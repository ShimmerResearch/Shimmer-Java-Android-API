# Bundled Rust LogAndStream binding for macOS arm64

- Source: ShimmerResearch/shimmer-logandstream
- Source commit: `791f2ebbd38dd86bb7d8039cf850aeaab470f536`
- Library version: 0.1.0; C ABI: 2.
- Rust: 1.99.0; target: aarch64-apple-darwin; release build.
- Java binding: Java 8 bytecode, built using arm64 Corretto 17.
- JNA 5.17.0 is downloaded separately by Gradle.
- JAR includes `darwin-aarch64/libshimmer_logandstream.dylib`.
- JAR SHA-256: `66a148e6c42ab911be492252b4a6858cb8d5f534e6e4465dd322042ce6aaf187`

Rebuild after changing the Rust library or binding: run `cargo build --release` in that repository,
then `./gradlew clean test checkJar` in `bindings/java`. Copy the resulting JAR here as
`shimmer-logandstream-0.1.0-macos-arm64.jar`, update this provenance, and verify the capture app
without a sibling Rust checkout. Keep the binding and native library from the same revision.
The BLE transport is a separate library under ShimmerDriverPC/src/main/resources/native/macos-arm64.
