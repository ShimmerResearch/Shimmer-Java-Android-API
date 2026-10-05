# ShimmerProtocolCore: the Shimmer protocol in Rust (DEV-1134 spike)

One implementation of the Shimmer LogAndStream protocol, to be shared by every platform instead
of a hand port per language. It has no I/O, no threads, no clock and no dependencies, so the same
code builds for Windows, macOS, Linux, Android, iOS and WebAssembly; each platform supplies only
its transport, as with the Java `LogAndStreamProtocol` it follows.

**Spike status.** It holds the first two pieces, to prove the targets before the real work:

- `crc.rs`: the Shimmer UART checksum.
- `framing.rs`: the data-stream framer, which splits a streaming device's bytes into packets,
  checks their checksums and resynchronises after corruption (the streaming half of
  `LogAndStreamProtocol.receive`).
- `ffi.rs`: a plain C ABI over both, for hosts that are not Rust (used by the WebAssembly check).

It is checked against the Java decoder on the two recorded sessions (a Shimmer3R, and a Shimmer3
whose stream has gaps): the packets it frames must be exactly the packets Java decoded, in order.

| Target | Result |
|---|---|
| Windows x64 | `cargo test`: 8 tests pass, including both recordings (493 and 414 packets, identical to Java) |
| WebAssembly (Node) | 24.7 KB module; identical to Java on both recordings |
| Android arm64 | compiles (static library; linking a `.so` needs the Android NDK) |
| iOS arm64 | compiles (static library) |
| macOS, Linux | in CI (`.github/workflows/protocol-core.yml`) |

Like `python/`, this folder is temporary: it sits beside the Java code and the recordings it is
checked against, and moves to its own repository later.

## Next

1. Port the rest of `LogAndStreamProtocol` (handshake, commands, timeouts, stream recovery).
2. Port the model (configuration, calibration, decoding) for Shimmer3 and Shimmer3R, checked value
   by value against the Java reference exports in `python/tests/data/`.
3. Bindings: Java through JNI (as `ShimmerBLENativeLib` does), Python through PyO3 (replacing the
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
