# ShimmerProtocolCore: the Shimmer protocol in Rust (DEV-1134 spike)

One implementation of the Shimmer LogAndStream protocol, to be shared by every platform instead
of a hand port per language. It has no I/O, no threads, no clock and no dependencies, so the same
code builds for Windows, macOS, Linux, Android, iOS and WebAssembly; each platform supplies only
its transport, as with the Java `LogAndStreamProtocol` it follows.

**Status.** It holds the protocol and the parts of the device model the protocol needs:

- `crc.rs`: the Shimmer UART checksum.
- `framing.rs`: the data-stream framer: packets out of a stream, checksums, resync after corruption.
- `protocol.rs`: the whole `LogAndStreamProtocol` state machine (handshake, commands, timeouts,
  recovery of a stream left running, link loss), ported from the Java.
- `model.rs`: identity, firmware rules, where Shimmer3 and Shimmer3R differ, and the packet layout
  from the inquiry. Calibrated decoding is next: until then a sample is the raw packet.
- `ffi.rs`: a plain C ABI over the checksum and the framer, for non-Rust hosts.

Tests (Windows; `cargo test --release`, 27): the 15 Java protocol tests, ported; and the two
recorded sessions (a Shimmer3R, and a Shimmer3 whose stream has gaps) replayed through the whole
state machine, whose handshake must complete against the real replies and whose samples must be
exactly those the Java decoder produced (493 and 414), split one byte at a time or corrupted.

Live on Windows (`examples/live.rs`, over the `shimmerble` transport): Shimmer3R-2F31, handshake
749 ms, 1029 samples in 20 s, none missing. Shimmer3-3E36 streamed with none missing but did not act
on STOP_STREAMING, and kept ignoring commands from any host until reset; under investigation.

The WebAssembly build of the framer runs in Node, identical to Java on both recordings
(`wasm/run_framing.mjs`); Android and iOS static libraries compile.

Like `python/`, this folder is temporary: it sits beside the Java code and the recordings it is
checked against, and moves to its own repository later.

## Next

1. Port the model (configuration, calibration, decoding) for Shimmer3 and Shimmer3R, checked value
   by value against the Java reference exports in `python/tests/data/`.
2. Bindings: Java through JNI (as `ShimmerBLENativeLib` does), Python through PyO3 (replacing the
   hand port in `python/`), the web SDK through WebAssembly, Kotlin and Swift through UniFFI.

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
