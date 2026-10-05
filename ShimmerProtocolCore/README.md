# ShimmerProtocolCore: the Shimmer protocol in Rust (DEV-1134 spike)

One implementation of the Shimmer LogAndStream protocol, to be shared by every platform instead
of a hand port per language. It has no I/O, no threads, no clock and no dependencies, so the same
code builds for Windows, macOS, Linux, Android, iOS and WebAssembly; each platform supplies only
its transport, as with the Java `LogAndStreamProtocol` it follows.

**Status.** It holds the protocol and the device model:

- `crc.rs`: the Shimmer UART checksum.
- `framing.rs`: the data-stream framer: packets out of a stream, checksums, resync after corruption.
- `protocol.rs`: the whole `LogAndStreamProtocol` state machine (handshake, commands, timeouts,
  recovery of a stream left running, link loss), ported from the Java.
- `model.rs`: identity, firmware rules, where Shimmer3 and Shimmer3R differ, configuration, the
  packet layout, and decoding: timestamp, low-noise accel, gyro, mag and battery, with the
  channels the Java adds per packet (battery %, reception rate, system timestamps). Shimmer3R
  (LSM6DSV, LIS2MDL) and both Shimmer3 generations (KXTC9-2050, MPU9250, LSM303AHTR; and
  KXRB5-2042, MPU9150, LSM303DLHC). Other channels are sized, so the packet layout is right, but
  not decoded yet.
- `calibration.rs`: inertial calibration as the Java computes it (defaults, InfoMem, the
  calibration dump, BigDecimal rounding, the gyro's on-the-fly offset).
- `ffi.rs`: a plain C ABI over the checksum and the framer, for non-Rust hosts.

Tests (Windows; `cargo test --release`, 36), all against the Java driver, to the last bit:

- the 15 Java protocol tests, ported;
- the two recorded sessions (a Shimmer3R, and a Shimmer3 whose stream has gaps) replayed through
  the whole state machine: the handshake completes against the real replies, and every channel of
  every sample, raw and calibrated, equals the Java decoder's (493 and 414 samples), including the
  gyro after its on-the-fly recalibration; also split one byte at a time, or corrupted;
- calibration the recordings never exercise, from Java exports: a crafted Shimmer3R calibration
  dump, and four Shimmer3 cases (`tests/data/`, from `API_00031_Shimmer3DecodeReferenceTest`):
  each sensor generation at its defaults and with a dump, with temperature and pressure in the
  packet, where a Shimmer3's channel sizes differ from a Shimmer3R's.

Two things the Java does that the model keeps, so that the values match: Math.pow's single
rounding in the battery fit (whose cancellation turns a twice-rounded power into a 1e-10 error),
and the LSM303DLHC quirk: the driver always decodes that mag with its range-1 calibration,
whatever range is configured, because `SensorLSM303DLHC.setLSM303MagRange` updates the
wide-range accel's calibration in use instead of the mag's.

Live on Windows (`examples/live.rs`, over the `shimmerble` transport): Shimmer3R-2F31, handshake
749 ms, 1029 samples in 20 s, none missing. Shimmer3-3E36 streamed with none missing but did not act
on STOP_STREAMING, and kept ignoring commands from any host until reset; under investigation.

The WebAssembly build of the framer runs in Node, identical to Java on both recordings
(`wasm/run_framing.mjs`); Android and iOS static libraries compile.

Like `python/`, this folder is temporary: it sits beside the Java code and the recordings it is
checked against, and moves to its own repository later.

## Next

1. Bindings: Java through JNI (as `ShimmerBLENativeLib` does), Python through PyO3 (replacing the
   hand port in `python/`), the web SDK through WebAssembly, Kotlin and Swift through UniFFI.
2. Decode the remaining channels (wide-range accel, ADCs, GSR, ExG, pressure), each checked against
   a Java export.

## Build and test

Windows needs the MSVC environment for linking (see `ShimmerBLENativeLib/README.md`).

```
cargo test --release

rustup target add wasm32-unknown-unknown
cargo rustc --lib --release --target wasm32-unknown-unknown --crate-type cdylib
node wasm/run_framing.mjs

rustup target add aarch64-linux-android aarch64-apple-ios
cargo rustc --lib --release --target aarch64-linux-android --crate-type staticlib
cargo rustc --lib --release --target aarch64-apple-ios --crate-type staticlib
```
