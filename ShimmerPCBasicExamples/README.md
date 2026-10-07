# ShimmerPCBasicExamples

There are a number of examples
- ECGToHRExample
- PPGToHRExample
- SensorMapsExample
- ShimmerBLECaptureExample - Shimmer3/Shimmer3R over BLE through the native library (no gRPC server); see ../ShimmerBLENativeLib/README.md.
  Two modes: the ShimmerBluetooth driver (ShimmerBLENative), and the LogAndStream protocol from
  the Rust core (shimmer-protocol-core). The core's mode appears only when a checkout of shimmer-protocol-core
  sits beside this repository (`C:\dev\shimmer-protocol-core`), with its library built there
  (`cargo build --release`); `-PnoProtocolCore` builds without it.
  `./gradlew runCaptureSmoke --args="Shimmer3R-2F31 5"` runs both modes headless and compares them.
- bletest.BleHardwareTestApp - BLE hardware test matrix and report (`./gradlew runBleTest --args="--help"`)
