"""Shimmer3R over Bluetooth LE with bleak: the only module that knows about Bluetooth.

async with Shimmer3R("Shimmer3R-2F31") as device:      # scan, connect, handshake
    await device.start_streaming()
    async for sample in device.samples():
        print(sample["Accel_LN_X"])
"""

from __future__ import annotations

import asyncio
import logging
import sys
from typing import AsyncIterator

from bleak import BleakClient, BleakScanner
from bleak.exc import BleakDeviceNotFoundError
from bleak.backends.device import BLEDevice

from .events import Discarded, Error, Initialised, LinkLost, Sample, State, StateChanged
from .host import ProtocolHost

log = logging.getLogger(__name__)

# The Shimmer3R's serial service. Writes go to CA102 and notifications come from CA101, as the
# native BLE transport (DEV-1132) uses them; the Android and Swift APIs use the opposite pair, and
# both have worked against devices, so the firmware may accept either.
SERVICE_UUID = "65333333-a115-11e2-9e9a-0800200ca100"
WRITE_UUID = "65333333-a115-11e2-9e9a-0800200ca102"
NOTIFY_UUID = "65333333-a115-11e2-9e9a-0800200ca101"


class ShimmerError(Exception):
    """The protocol failed or the link was lost."""


async def _windows_connected_device(name: str) -> BLEDevice | None:
    """A device Windows holds connected, by name. Such a device does not advertise, so a scan
    cannot find it; typically its link was left open by an earlier process, which Windows keeps
    when that process dies. The protocol then stops whatever it is still streaming.

    Only a device whose name matches is opened: Windows also lists connected devices that cannot
    be opened as BLE devices, and opening those fails ("not a valid BluetoothLEDevice")."""
    from winrt.windows.devices.bluetooth import BluetoothConnectionStatus, BluetoothLEDevice
    from winrt.windows.devices.enumeration import DeviceInformation

    selector = BluetoothLEDevice.get_device_selector_from_connection_status(
        BluetoothConnectionStatus.CONNECTED
    )
    for info in await DeviceInformation.find_all_async_aqs_filter(selector):
        if not (info.name or "").startswith(name):
            continue
        try:
            device = await BluetoothLEDevice.from_id_async(info.id)
        except OSError as e:
            log.info("cannot open %s, which Windows lists as connected: %s", info.name, e)
            continue
        if device is None:
            continue
        hex12 = f"{device.bluetooth_address:012X}"
        device.close()
        return BLEDevice(":".join(hex12[i : i + 2] for i in range(0, 12, 2)), info.name, None)
    return None


class BleTransport:
    """Moves bytes between a bleak connection and a ProtocolHost."""

    RETRY_S = 2.0

    def __init__(self, device: BLEDevice | str) -> None:
        self._device = device
        self._client: BleakClient | None = None
        self._open = False
        self._closing = False

    async def open(
        self, host: ProtocolHost, timeout: float = 20.0, release_s: float = 30.0
    ) -> None:
        """Connects and subscribes.

        For about 20 s after a process that had the device open dies, Windows denies a new
        connection the Shimmer3R service ("access denied", so the service looks absent), and
        bleak's service discovery can itself fail in that time. Either way this disconnects and
        tries again, for up to ``release_s``."""

        def disconnected(_: BleakClient) -> None:
            if self._open and not self._closing:
                host.on_link_lost("disconnected")

        loop = asyncio.get_running_loop()
        give_up = loop.time() + release_s
        while True:
            self._client = BleakClient(self._device, disconnected, timeout=timeout)
            try:
                await self._client.connect()
                why = "service not available"
                if self._client.services.get_characteristic(NOTIFY_UUID) is not None:
                    break
            except BleakDeviceNotFoundError:
                raise
            except Exception as e:  # bleak's discovery can fail while Windows releases the service
                why = f"{type(e).__name__}: {e}"
            try:
                await self._client.disconnect()
            except Exception:
                pass
            if loop.time() >= give_up:
                raise ShimmerError(
                    f"cannot open the Shimmer3R service after {release_s:.0f} s: {why}"
                )
            log.info(
                "Shimmer3R service not ready (%s; held by a process that ended?); retrying", why
            )
            await asyncio.sleep(self.RETRY_S)
        await self._client.start_notify(NOTIFY_UUID, lambda _, data: host.on_bytes(data))
        self._open = True

    @property
    def mtu(self) -> int:
        return self._client.mtu_size if self._client else 0

    async def write(self, data: bytes) -> None:
        await self._client.write_gatt_char(WRITE_UUID, data, response=False)

    async def close(self) -> None:
        self._closing = True
        if self._client is not None and self._client.is_connected:
            await self._client.disconnect()


class Shimmer3R:
    """A Shimmer3R over BLE, by advertised name (e.g. "Shimmer3R-2F31-BLE") or address."""

    def __init__(self, name_or_address: str, scan_timeout: float = 20.0) -> None:
        self._target = name_or_address
        self._scan_timeout = scan_timeout
        self._transport: BleTransport | None = None
        self._host: ProtocolHost | None = None
        self._pump: asyncio.Task | None = None
        self._changed = asyncio.Condition()
        self._samples: asyncio.Queue[Sample | None] = asyncio.Queue()
        self._failure: str | None = None
        self._streaming = False
        self.summary = ""

    async def __aenter__(self) -> "Shimmer3R":
        await self.connect()
        return self

    async def __aexit__(self, *exc) -> None:
        await self.disconnect()

    @property
    def state(self) -> State:
        return self._host.state if self._host else State.DISCONNECTED

    @property
    def sampling_rate(self) -> float:
        return self._host.sampling_rate if self._host else float("nan")

    @property
    def mtu(self) -> int:
        return self._transport.mtu if self._transport else 0

    async def connect(self, timeout: float = 30.0) -> None:
        """Finds the device, opens the link and runs the handshake."""
        device = await self._find()
        self._transport = BleTransport(device)
        self._host = ProtocolHost(self._transport)
        self._pump = asyncio.create_task(self._dispatch())
        await self._transport.open(self._host)
        self._host.connect()
        await self._wait_for(State.READY, timeout)

    async def start_streaming(self, timeout: float = 10.0) -> None:
        self._host.start_streaming()
        await self._wait_for(State.STREAMING, timeout)

    async def stop_streaming(self, timeout: float = 10.0) -> None:
        self._host.stop_streaming()
        await self._wait_for(State.READY, timeout)

    async def samples(self) -> AsyncIterator[Sample]:
        """Samples as they arrive, until streaming stops; raises ShimmerError if the link fails."""
        while (sample := await self._samples.get()) is not None:
            yield sample
        if self._failure:
            raise ShimmerError(self._failure)

    async def disconnect(self) -> None:
        if self._host:
            await self._host.close()
        if self._transport:
            await self._transport.close()
        if self._pump:
            self._pump.cancel()

    async def _find(self) -> BLEDevice | str:
        target = self._target
        is_mac = ":" in target
        is_apple_uuid = len(target) == 36 and target.count("-") == 4  # macOS has no MAC addresses
        if is_mac and sys.platform == "win32":
            # Opened by address without a scan, so this works while Windows holds it connected.
            return BLEDevice(target.upper(), None, None)
        if is_mac or is_apple_uuid:
            return target
        if sys.platform == "win32":
            held = await _windows_connected_device(target)
            if held is not None:
                log.info("%s is already connected to this PC; using that link", held.name)
                return held
        device = await BleakScanner.find_device_by_filter(
            lambda d, ad: bool(ad.local_name) and ad.local_name.startswith(target),
            timeout=self._scan_timeout,
        )
        if device is None:
            raise ShimmerError(f"{target} not found within {self._scan_timeout:.0f} s")
        return device

    async def _dispatch(self) -> None:
        """Routes the host's events: samples to samples(), the rest to the waits."""
        while True:
            event = await self._host.events.get()
            if isinstance(event, Sample):
                self._samples.put_nowait(event)
                continue
            if isinstance(event, (Error, LinkLost)):
                self._failure = getattr(event, "message", None) or f"link lost: {event.reason}"
                self._samples.put_nowait(None)
            elif isinstance(event, StateChanged):
                if event.state is State.STREAMING:
                    self._streaming = True
                elif event.state is State.READY and self._streaming:
                    self._streaming = False
                    self._samples.put_nowait(None)  # a stream that stops ends samples()
            elif isinstance(event, Initialised):
                self.summary = event.summary
            elif isinstance(event, Discarded):
                log.info("%s", event.message)
            async with self._changed:
                self._changed.notify_all()

    async def _wait_for(self, state: State, timeout: float) -> None:
        async with self._changed:
            await asyncio.wait_for(
                self._changed.wait_for(lambda: self._failure or self.state is state), timeout
            )
        if self._failure:
            raise ShimmerError(self._failure)
