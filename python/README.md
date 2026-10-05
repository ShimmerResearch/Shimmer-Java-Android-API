# shimmer3r (Python): DEV-1134 prototype

A Shimmer-led Python API for the Shimmer3R over Bluetooth LE. It is a port of the Java
`LogAndStreamProtocol` design (DEV-1134), built to find out what porting the protocol to another
language costs.

**This folder is temporary.** It lives here, on the DEV-1134 branch only and never on `master`,
so that it sits beside the Java code and the recording it is checked against. It moves to its own
repository with `git subtree split --prefix=python`, which keeps its history. It has no licence
yet; that is Shimmer's choice for the new repository.

It is written from the Java driver and the protocol docs, **not from pyshimmer**, which is
third-party and GPL-3.0.

## Layers

```
Shimmer3R ──notification──► ble.py ──on_bytes()──► host.py ──receive()──► protocol.py ──► model.py
          ◄──write───────── ble.py ◄──write()────── host.py ◄──writes──────┘
                                                    host.py ──events──► your app
```

| Module | Does | Port of (Java) |
|---|---|---|
| `protocol.py` | The state machine: bytes in, writes and events out. No I/O, threads or clock. | `LogAndStreamProtocol` |
| `model.py` | Identity, config bytes, calibration, packet decoding | What `LogAndStreamModel` reuses from the driver |
| `host.py` | One asyncio loop: feeds bytes in, sends writes, runs timeouts, queues events | `ProtocolHost` |
| `ble.py` | bleak transport and the `Shimmer3R` class an app uses. The only module that knows Bluetooth. | `ProtocolCaptureBackend` |
| `crc.py`, `events.py` | Checksum; events and samples | `ShimmerCrc`; `ProtocolEvent` |

```python
async with Shimmer3R("Shimmer3R-2F31") as device:      # scan, connect, handshake
    await device.start_streaming()
    async for sample in device.samples():
        print(sample["Accel_LN_X"])
```

## Setup and tests

```
py -3.14 -m venv .venv
.venv\Scripts\python -m pip install -e .[ble,test,dev]
.venv\Scripts\python -m pytest
.venv\Scripts\python examples\stream.py Shimmer3R-2F31 10
```

Only `ble.py` needs bleak; the rest uses the standard library.

The tests need no device. `tests/data` holds a recorded Shimmer3R session and
`java_reference.csv`, the Java decoder's output for that recording, written by the Java test
`API_00030_PythonReferenceTest` (ShimmerDriverPC). `test_replay.py` replays the recording through
the Python protocol and requires every channel of every sample to equal the Java value exactly.
If the Java decoder changes, `API_00030` fails until the CSV is regenerated; it says how.

## Scope

Decoded: the timestamp, low-noise accel, gyro (including the on-the-fly offset calibration), mag,
battery, and the per-packet channels the Java adds. Other channels are sized correctly in the packet
layout but not decoded: wide-range accel, ADCs, pressure, GSR, ExG, bridge amp.

Not yet: writing configuration, and the TCXO sampling clock of the EXG unified board revision 1,
special revision 1.

## A device left connected (Windows)

Windows keeps a BLE link open when the process that owned it dies, so after a crash the device
stops advertising and a scan cannot find it, often while it is still streaming. `ble.py` handles
this:

- By name, it first asks Windows for connected devices with that name (opening only a match:
  Windows also lists connected devices that cannot be opened as BLE devices). By address, it opens
  the device directly, without scanning.
- For 8-20 s after such a process dies, Windows denies the Shimmer3R service to a new connection,
  and bleak's service discovery can fail outright. The transport retries for up to 30 s.
- The protocol then stops the stream left running and turns checksums off before its handshake.

Tested by killing a streaming process and reconnecting 2 s later, by name and by address: ready
in about 11 s, then streaming normally.
