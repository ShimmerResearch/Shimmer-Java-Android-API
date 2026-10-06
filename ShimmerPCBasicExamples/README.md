# ShimmerPCBasicExamples

There are a number of examples
- ECGToHRExample
- PPGToHRExample
- SensorMapsExample
- ShimmerBLECaptureExample - Shimmer3/Shimmer3R over BLE through the native library (no gRPC server); see ../ShimmerBLENativeLib/README.md.
  Three modes: today's driver, the DEV-1134 state machine, and the Rust protocol core
  (shimmer-protocol-core). The core's mode appears only when a checkout of shimmer-protocol-core
  sits beside this repository (`C:\dev\shimmer-protocol-core`), with its library built there
  (`cargo build --release`); `-PnoProtocolCore` builds without it.
  `./gradlew runCaptureSmoke --args="Shimmer3R-2F31 5"` runs every mode headless and compares them.
- bletest.BleHardwareTestApp - BLE hardware test matrix and report (`./gradlew runBleTest --args="--help"`)
