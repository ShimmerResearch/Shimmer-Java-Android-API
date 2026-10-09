# Native BLE library binaries

`NativeBleLoader` loads `shimmerble` from `native/<platform>/` on the classpath, for example
`native/windows-x64/shimmerble.dll` or `native/macos-arm64/libshimmerble.dylib`.

The macOS arm64 BLE binary is committed for Apple Silicon testers. Rebuild it or provide binaries
for other platforms by either:
- running `./gradlew buildNative` in `ShimmerDriverPC/`, which builds the library for this machine
- unzipping the artifact of the **Native BLE library** GitHub Actions workflow here, for the other
  platforms

This file is committed so that `src/main/resources` always exists. Eclipse (Buildship) adds a
resource folder to the build path only if it exists at import, and without it Eclipse never copies
the library to `bin/main`. It is excluded from the jar.
