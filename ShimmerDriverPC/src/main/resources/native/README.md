# Native BLE library binaries

`NativeBleLoader` loads `shimmerble` from `native/<platform>/` on the classpath, for example
`native/windows-x64/shimmerble.dll` or `native/macos-arm64/libshimmerble.dylib`.

The Windows x64 and macOS arm64 binaries are committed, so every build of the driver carries them,
Consensys's release build included. Both come from one run of the **Native BLE library** GitHub
Actions workflow, built from `shimmerble/` at the same commit. When `shimmerble/` changes:

1. Bump its version: `version` in `Cargo.toml` and `NativeBleLoader.EXPECTED_NATIVE_VERSION`.
2. Run the workflow, and unzip both platforms' artifacts here, replacing these files.
3. Commit the binaries with the source change.

`./gradlew buildNative` in `ShimmerDriverPC/` builds the library for this machine into the same
folder, which is useful while working on it. Don't commit that build: the committed binaries are
the workflow's, so that both platforms come from the same source. Other platforms' folders are
ignored by git.

This file is committed so that `src/main/resources` always exists. Eclipse (Buildship) adds a
resource folder to the build path only if it exists at import, and without it Eclipse never copies
the library to `bin/main`. It is excluded from the jar.
