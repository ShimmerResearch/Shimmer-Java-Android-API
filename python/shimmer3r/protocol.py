"""The Shimmer3R Bluetooth protocol as an I/O-free state machine.

The host owns the transport and the clock. It calls ``connect()``, passes every received chunk to
``receive()``, and calls ``tick()`` when ``next_deadline()`` is due. Each call returns an
``Output``: the bytes to write (one entry per write) and the events to deliver. Nothing here
blocks, sleeps, starts a thread or calls back into the host, so the same object works over any
transport and can be driven by recorded bytes in a test.

Times are wall-clock milliseconds (``time.time() * 1000``): SET_RWC sends the clock to the device.

The handshake starts only once the link has been quiet for ``SETTLE_MS``. Windows keeps a BLE
link open after the process that owned it dies, so a new session can inherit a device that is
still streaming; if bytes arrive in that time it is told to stop, and its checksums are turned
off, first. One object serves one link: after ``link_lost()`` it stays DISCONNECTED.

A port of the Java com.shimmerresearch.protocol.Shimmer3RProtocol (DEV-1134). Keep them in step.
"""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field
from typing import Callable

from .crc import crc
from .events import (
    Discarded,
    Error,
    Event,
    Initialised,
    LinkLost,
    StateChanged,
    State,
)
from .model import Shimmer3RModel

ACK = 0xFF
DATA_PACKET = 0x00

INQUIRY_COMMAND = 0x01
INQUIRY_RESPONSE = 0x02
START_STREAMING_COMMAND = 0x07
STOP_STREAMING_COMMAND = 0x20
FW_VERSION_RESPONSE = 0x2F
GET_FW_VERSION_COMMAND = 0x2E
GET_SHIMMER_VERSION_RESPONSE = 0x25
GET_SHIMMER_VERSION_COMMAND_NEW = 0x3F
DAUGHTER_CARD_ID_RESPONSE = 0x65
GET_DAUGHTER_CARD_ID_COMMAND = 0x66
SET_CRC_COMMAND = 0x8B
INFOMEM_RESPONSE = 0x8D
GET_INFOMEM_COMMAND = 0x8E
SET_RWC_COMMAND = 0x8F
RSP_CALIB_DUMP_COMMAND = 0x99
GET_CALIB_DUMP_COMMAND = 0x9A
PRESSURE_CALIBRATION_COEFFICIENTS_RESPONSE = 0xA6
GET_PRESSURE_CALIBRATION_COEFFICIENTS_COMMAND = 0xA7

CRC_OFF = 0
CRC_ONE_BYTE = 1


@dataclass
class Output:
    """What one call produced: the bytes to write, one entry per write, and the events."""

    writes: list[bytes] = field(default_factory=list)
    events: list[Event] = field(default_factory=list)

    def __bool__(self) -> bool:
        return bool(self.writes or self.events)


# Given the received bytes and where the payload starts, the payload's length, or -1 if not known yet.
_Length = Callable[[bytearray, int], int]
_Handler = Callable[[bytes, int, Output], None]


@dataclass
class _Command:
    name: str
    data: bytes
    response_code: int | None = None  # None for a command that is only acknowledged
    length: _Length | None = None
    handle: _Handler | None = None
    timeout_ms: int = 2000
    optional: bool = False  # if it times out, carry on rather than fail


def _fixed(n: int) -> _Length:
    return lambda rx, start: n


def _length_prefixed(header_length: int) -> _Length:
    """A payload whose first byte is the length of the data after ``header_length`` bytes."""
    return lambda rx, start: -1 if len(rx) <= start else header_length + rx[start]


def _hex(b: int) -> str:
    return f"0x{b:02X}"


def rtc_bytes(now_ms: int) -> bytes:
    """The device clock for a wall-clock time: 32768 Hz ticks, 8 bytes, LSB first."""
    return int(now_ms * 32.768).to_bytes(8, "little", signed=True)


class Shimmer3RProtocol:
    DEFAULT_TIMEOUT_MS = 2000
    LONG_TIMEOUT_MS = 5000
    SETTLE_MS = 300
    SETTLE_GIVE_UP_MS = 3000
    MEM_CHUNK = 128
    MIN_FIRMWARE_VERSION_CODE = 7  # config bytes (6) and the calibration dump (7) over Bluetooth
    INQUIRY_SETTINGS_LENGTH = 11
    INQUIRY_CHANNEL_COUNT_INDEX = 9

    def __init__(self) -> None:
        self._model = Shimmer3RModel()
        self._queue: deque[_Command] = deque()
        self._rx = bytearray()
        self._state = State.DISCONNECTED
        self._in_flight: _Command | None = None
        self._ack_seen = False
        self._deadline: int | None = None
        self._crc_bytes = 0
        self._dropped = 0
        self._link_lost = False

        self._settling = False
        self._quiet_at: int | None = None
        self._settle_give_up_at: int | None = None
        self._stop_sent = False
        self._settle_bytes = 0

        self._config = bytearray()
        self._config_length = 0
        self._calib_dump = bytearray()
        self._calib_dump_length = -1

    # --- State ------------------------------------------------------------------------------

    @property
    def state(self) -> State:
        return self._state

    @property
    def sampling_rate(self) -> float:
        return self._model.sampling_rate

    @property
    def model(self) -> Shimmer3RModel:
        """The device as configured by the handshake; complete once the state is READY."""
        return self._model

    def next_deadline(self) -> int | None:
        """When the host should next call ``tick()``, or None if nothing is pending."""
        if self._settling:
            return min(self._quiet_at, self._settle_give_up_at)
        return self._deadline if self._in_flight is not None else None

    # --- Calls from the host ------------------------------------------------------------------

    def connect(self, now_ms: int) -> Output:
        """Call once the link is open. The handshake itself starts from ``tick()`` once the link
        has been quiet for ``SETTLE_MS``, so this returns no writes."""
        out = Output()
        if self._link_lost:
            out.events.append(
                Error("this link was lost; use a new Shimmer3RProtocol for a new connection")
            )
            return out
        if self._state is not State.DISCONNECTED:
            return self._failed(out, f"connect() called in state {self._state.value}")
        self._set_state(State.CONNECTING, out)
        self._settling = True
        self._quiet_at = now_ms + self.SETTLE_MS
        self._settle_give_up_at = now_ms + self.SETTLE_GIVE_UP_MS
        return out

    def link_lost(self, reason: str, now_ms: int) -> Output:
        """The host lost the link. DISCONNECTED for good: later bytes, ticks and stream requests
        are ignored, and ``connect()`` reports an error."""
        out = Output()
        if self._link_lost:
            return out
        self._link_lost = True
        self._settling = False
        self._in_flight = None
        self._deadline = None
        self._queue.clear()
        self._rx.clear()
        self._model.streaming_stopped()
        out.events.append(LinkLost(reason))
        self._set_state(State.DISCONNECTED, out)
        return out

    def start_streaming(self, now_ms: int) -> Output:
        out = Output()
        if self._link_lost:
            return out
        if self._state is not State.READY:
            return self._failed(out, f"start_streaming() called in state {self._state.value}")
        self._model.prepare_for_streaming()
        self._set_state(State.STARTING, out)
        self._queue.append(_Command("START_STREAMING", bytes([START_STREAMING_COMMAND])))
        self._send_next(now_ms, out)
        return out

    def stop_streaming(self, now_ms: int) -> Output:
        out = Output()
        if self._link_lost:
            return out
        if self._state is not State.STREAMING:
            return self._failed(out, f"stop_streaming() called in state {self._state.value}")
        self._set_state(State.STOPPING, out)
        self._queue.append(_Command("STOP_STREAMING", bytes([STOP_STREAMING_COMMAND])))
        self._send_next(now_ms, out)
        return out

    def receive(self, data: bytes, now_ms: int) -> Output:
        """Bytes received from the device, in the order they arrived."""
        out = Output()
        if self._state in (State.DISCONNECTED, State.FAILED):
            return out
        if self._settling:
            self._settle_on(data, now_ms, out)
            return out
        self._rx += data
        progressed = True
        while progressed and self._rx and self._state is not State.FAILED:
            if self._state in (State.STREAMING, State.STOPPING):
                progressed = self._receive_streaming(now_ms, out)
            else:
                progressed = self._receive_response(now_ms, out)
        return out

    def tick(self, now_ms: int) -> Output:
        """Starts the handshake once the link is quiet, and fires a command timeout if one is due."""
        out = Output()
        if self._settling:
            if now_ms >= self._quiet_at:
                self._start_handshake(now_ms, out)
            elif now_ms >= self._settle_give_up_at:
                self._failed(
                    out,
                    "the device was already streaming and did not stop within "
                    f"{self.SETTLE_GIVE_UP_MS} ms",
                )
            return out
        cmd = self._in_flight
        if cmd is not None and now_ms >= self._deadline:
            why = f"no {'response' if self._ack_seen else 'ACK'} to {cmd.name} within {cmd.timeout_ms} ms"
            if cmd.optional:
                out.events.append(Discarded(why + "; carrying on"))
                self._complete(now_ms, out)
            else:
                self._failed(out, why)
        return out

    # --- Settling: a device left streaming by an earlier session --------------------------------

    def _settle_on(self, data: bytes, now_ms: int, out: Output) -> None:
        """Bytes before the handshake can only be a stream left running: stop it, then wait for quiet."""
        self._settle_bytes += len(data)
        self._quiet_at = now_ms + self.SETTLE_MS
        if not self._stop_sent:
            self._stop_sent = True
            out.writes.append(bytes([STOP_STREAMING_COMMAND]))

    def _start_handshake(self, now_ms: int, out: Output) -> None:
        self._settling = False
        self._quiet_at = None
        self._settle_give_up_at = None
        if self._settle_bytes > 0:
            out.events.append(
                Discarded(
                    "the device was already streaming; stopped it and dropped "
                    f"{self._settle_bytes} byte(s)"
                )
            )
        if self._stop_sent:
            # The earlier session probably turned checksums on, and the device keeps them until
            # it is disconnected. Firmware too old for checksums does not ACK this: carry on.
            self._queue.append(
                _Command("SET_CRC_OFF", bytes([SET_CRC_COMMAND, CRC_OFF]), optional=True)
            )
        self._queue.append(
            _Command(
                "GET_SHIMMER_VERSION",
                bytes([GET_SHIMMER_VERSION_COMMAND_NEW]),
                GET_SHIMMER_VERSION_RESPONSE,
                _fixed(1),
                self._on_hardware_version,
            )
        )
        self._queue.append(
            _Command(
                "GET_FW_VERSION",
                bytes([GET_FW_VERSION_COMMAND]),
                FW_VERSION_RESPONSE,
                _fixed(6),
                self._on_firmware_version,
            )
        )
        self._send_next(now_ms, out)

    # --- Commands and responses -----------------------------------------------------------------

    def _send_next(self, now_ms: int, out: Output) -> None:
        if self._in_flight is not None or self._state is State.FAILED:
            return
        if not self._queue:
            self._on_queue_empty(out)
            return
        cmd = self._queue.popleft()
        self._in_flight = cmd
        self._ack_seen = False
        self._deadline = now_ms + cmd.timeout_ms
        data = cmd.data
        if data[0] == SET_RWC_COMMAND:
            data = bytes([SET_RWC_COMMAND]) + rtc_bytes(now_ms)  # stamped when sent, not queued
        elif data[0] == SET_CRC_COMMAND:
            # The device answers SET_CRC already in the new mode.
            self._model.apply_crc_mode(data[1])
            self._crc_bytes = data[1]
        out.writes.append(data)

    def _complete(self, now_ms: int, out: Output) -> None:
        done = self._in_flight
        self._in_flight = None
        self._deadline = None
        if done.data[0] == START_STREAMING_COMMAND:
            self._model.streaming_started()
            self._set_state(State.STREAMING, out)
        elif done.data[0] == STOP_STREAMING_COMMAND:
            self._model.streaming_stopped()
            self._set_state(State.READY, out)
        self._send_next(now_ms, out)

    def _on_queue_empty(self, out: Output) -> None:
        if self._state is State.CONNECTING:
            self._set_state(State.READY, out)
            m = self._model
            out.events.append(
                Initialised(
                    f"Shimmer3R {m.firmware_version_text}, {m.sampling_rate} Hz, "
                    f"packet size {m.packet_size}"
                )
            )

    def _receive_response(self, now_ms: int, out: Output) -> bool:
        """Not streaming: an ACK, then the expected response (if any), then the checksum."""
        cmd = self._in_flight
        if cmd is None:
            # Nothing was asked for. Unsolicited bytes are not handled yet; drop them.
            self._dropped += len(self._rx)
            self._rx.clear()
            self._report_dropped(out)
            return False
        if not self._ack_seen:
            ack = self._rx.find(ACK)
            if ack < 0:
                self._dropped += len(self._rx)
                self._rx.clear()
                return False
            self._dropped += ack
            del self._rx[:ack]
            if cmd.response_code is None:
                # ACK only: the ACK and its checksum.
                if len(self._rx) < 1 + self._crc_bytes:
                    return False
                self._check_response_crc(bytes(self._rx[: 1 + self._crc_bytes]), 1, out)
                del self._rx[: 1 + self._crc_bytes]
                self._report_dropped(out)
                self._complete(now_ms, out)
                return True
            del self._rx[:1]
            self._ack_seen = True
            self._report_dropped(out)
        if not self._rx:
            return False
        if self._rx[0] != cmd.response_code:
            self._failed(
                out,
                f"expected response {_hex(cmd.response_code)} to {cmd.name}, got {_hex(self._rx[0])}",
            )
            return False
        length = cmd.length(self._rx, 1)
        if length < 0 or len(self._rx) < 1 + length + self._crc_bytes:
            return False
        payload = bytes(self._rx[1 : 1 + length])
        # The checksum covers the ACK too, which is no longer in the buffer.
        framed = bytes([ACK]) + bytes(self._rx[: 1 + length + self._crc_bytes])
        self._check_response_crc(framed, 2 + length, out)
        del self._rx[: 1 + length + self._crc_bytes]
        cmd.handle(payload, now_ms, out)
        if self._state is not State.FAILED:
            self._complete(now_ms, out)
        return True

    def _receive_streaming(self, now_ms: int, out: Output) -> bool:
        """Streaming: data packets, plus the ACK of STOP_STREAMING."""
        first = self._rx[0]
        if first == DATA_PACKET:
            size = self._model.packet_size
            # With no checksum, the next packet's header is what confirms this packet's length.
            needed = 1 + size + (self._crc_bytes if self._crc_bytes > 0 else 1)
            if len(self._rx) < needed:
                return False
            if self._crc_bytes > 0:
                valid = self._packet_crc_matches(size)
            else:
                valid = self._rx[1 + size] in (DATA_PACKET, ACK)
            if not valid:
                del self._rx[:1]
                self._dropped += 1
                return True
            self._report_dropped(out)
            sample = self._model.decode(bytes(self._rx[1 : 1 + size]), now_ms)
            del self._rx[: 1 + size + self._crc_bytes]
            out.events.append(sample)
            return True
        cmd = self._in_flight
        if first == ACK and cmd is not None and not self._ack_seen and cmd.response_code is None:
            if len(self._rx) < 1 + self._crc_bytes:
                return False
            del self._rx[: 1 + self._crc_bytes]
            self._report_dropped(out)
            self._complete(now_ms, out)
            return True
        del self._rx[:1]
        self._dropped += 1
        return True

    def _packet_crc_matches(self, size: int) -> bool:
        frame = self._rx[: 1 + size + self._crc_bytes]
        expected = crc(bytes(frame[: 1 + size]))
        if frame[1 + size] != expected[0]:
            return False
        return self._crc_bytes < 2 or frame[2 + size] == expected[1]

    def _check_response_crc(self, framed: bytes, covered: int, out: Output) -> None:
        """Replies' checksums are reported, not enforced: the Java driver does not check them."""
        if self._crc_bytes == 0:
            return
        expected = crc(framed[:covered])
        ok = framed[covered] == expected[0] and (
            self._crc_bytes < 2 or framed[covered + 1] == expected[1]
        )
        if not ok:
            out.events.append(
                Discarded(f"CRC mismatch on the reply to {self._in_flight.name} (not enforced)")
            )

    def _report_dropped(self, out: Output) -> None:
        if self._dropped > 0:
            out.events.append(Discarded(f"{self._dropped} unexpected byte(s) dropped"))
            self._dropped = 0

    # --- Handshake steps ------------------------------------------------------------------------

    def _on_hardware_version(self, payload: bytes, now_ms: int, out: Output) -> None:
        self._model.apply_hardware_version(payload[0])
        if not self._model.is_shimmer3r:
            self._failed(out, f"device reports hardware version {payload[0]}, not a Shimmer3R")

    def _on_firmware_version(self, payload: bytes, now_ms: int, out: Output) -> None:
        m = self._model
        m.apply_firmware_version(payload)
        if m.firmware_version_code < self.MIN_FIRMWARE_VERSION_CODE:
            self._failed(
                out,
                f"firmware {m.firmware_version_text} cannot send its config and calibration "
                "over Bluetooth",
            )
            return
        # The rest of the handshake depends on the firmware, so it is queued only now.
        self._queue.append(
            _Command(
                "GET_DAUGHTER_CARD_ID",
                bytes([GET_DAUGHTER_CARD_ID_COMMAND, 0x03, 0x00]),
                DAUGHTER_CARD_ID_RESPONSE,
                _length_prefixed(1),
                self._on_expansion_board,
            )
        )
        if m.is_bt_crc_mode_supported:
            self._queue.append(_Command("SET_CRC", bytes([SET_CRC_COMMAND, CRC_ONE_BYTE])))
        self._config_length = m.config_byte_length
        self._config = bytearray()
        start = m.config_byte_start_address
        for offset in range(0, self._config_length, self.MEM_CHUNK):
            size = min(self.MEM_CHUNK, self._config_length - offset)
            self._queue.append(
                self._mem_read(
                    "GET_INFOMEM",
                    GET_INFOMEM_COMMAND,
                    start + offset,
                    size,
                    INFOMEM_RESPONSE,
                    _length_prefixed(1),
                    self._on_config_bytes,
                )
            )
        self._queue.append(
            _Command(
                "GET_PRESSURE_CALIBRATION_COEFFICIENTS",
                bytes([GET_PRESSURE_CALIBRATION_COEFFICIENTS_COMMAND]),
                PRESSURE_CALIBRATION_COEFFICIENTS_RESPONSE,
                _length_prefixed(1),
                self._on_pressure_coefficients,
            )
        )
        self._calib_dump = bytearray()
        self._calib_dump_length = -1
        self._queue.append(self._calib_dump_read(0, self.MEM_CHUNK))
        self._queue.append(
            _Command(
                "INQUIRY",
                bytes([INQUIRY_COMMAND]),
                INQUIRY_RESPONSE,
                self._inquiry_length,
                lambda payload, now, out: self._model.apply_inquiry(payload),
            )
        )
        self._queue.append(_Command("SET_RWC", bytes([SET_RWC_COMMAND])))

    def _on_expansion_board(self, payload: bytes, now_ms: int, out: Output) -> None:
        # [length, id, revision, special revision]
        self._model.apply_expansion_board(payload[1:4])

    def _on_config_bytes(self, payload: bytes, now_ms: int, out: Output) -> None:
        self._config += payload[1:]
        if len(self._config) >= self._config_length:
            self._model.apply_config_bytes(bytes(self._config))

    def _on_pressure_coefficients(self, payload: bytes, now_ms: int, out: Output) -> None:
        rejected = self._model.apply_pressure_coefficients(payload[1:])
        if rejected is not None:
            out.events.append(Discarded(f"pressure calibration coefficients rejected: {rejected}"))

    def _on_calib_dump(self, payload: bytes, now_ms: int, out: Output) -> None:
        # [length, address LSB, address MSB, data...]
        data = payload[3:]
        first = self._calib_dump_length < 0
        self._calib_dump += data
        if first:
            # The dump starts with its own length, which does not count those two bytes.
            self._calib_dump_length = (data[1] << 8 | data[0]) + 2
            rest = [
                self._calib_dump_read(
                    address, min(self.MEM_CHUNK, self._calib_dump_length - address)
                )
                for address in range(self.MEM_CHUNK, self._calib_dump_length, self.MEM_CHUNK)
            ]
            # Read the rest of the dump before anything else that is queued.
            self._queue.extendleft(reversed(rest))
        if len(self._calib_dump) >= self._calib_dump_length:
            self._model.apply_calibration_dump(bytes(self._calib_dump[: self._calib_dump_length]))

    def _calib_dump_read(self, address: int, size: int) -> _Command:
        return self._mem_read(
            "GET_CALIB_DUMP",
            GET_CALIB_DUMP_COMMAND,
            address,
            size,
            RSP_CALIB_DUMP_COMMAND,
            _length_prefixed(3),
            self._on_calib_dump,
        )

    def _mem_read(
        self,
        name: str,
        command: int,
        address: int,
        size: int,
        response_code: int,
        length: _Length,
        handle: _Handler,
    ) -> _Command:
        """[command, length, address LSB, address MSB]"""
        data = bytes([command, size, address & 0xFF, (address >> 8) & 0xFF])
        return _Command(
            f"{name}@{address}", data, response_code, length, handle, self.LONG_TIMEOUT_MS
        )

    def _inquiry_length(self, rx: bytearray, start: int) -> int:
        if len(rx) < start + self.INQUIRY_SETTINGS_LENGTH:
            return -1
        return self.INQUIRY_SETTINGS_LENGTH + rx[start + self.INQUIRY_CHANNEL_COUNT_INDEX]

    # --- Helpers --------------------------------------------------------------------------------

    def _set_state(self, state: State, out: Output) -> None:
        if self._state is not state:
            self._state = state
            out.events.append(StateChanged(state))

    def _failed(self, out: Output, why: str) -> Output:
        self._settling = False
        self._in_flight = None
        self._deadline = None
        self._queue.clear()
        out.events.append(Error(why))
        self._set_state(State.FAILED, out)
        return out
