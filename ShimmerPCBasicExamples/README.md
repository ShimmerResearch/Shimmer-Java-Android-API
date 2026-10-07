# ShimmerPCBasicExamples

There are a number of examples
- ECGToHRExample
- PPGToHRExample
- SensorMapsExample
- ShimmerBLECaptureExample - Shimmer3/Shimmer3R over BLE through the native library (no gRPC server); see ../shimmerble/README.md.
  Two drivers: ShimmerBluetooth (ShimmerBLENative), and the Rust LogAndStream library
  (shimmer-logandstream). The Rust one appears only when a checkout of shimmer-logandstream sits
  beside this repository (`C:\dev\shimmer-logandstream`), with its library built there
  (`cargo build --release`); `-PnoShimmerLogAndStream` builds without it.
  `./gradlew runCaptureSmoke --args="Shimmer3R-2F31 5"` runs both modes headless and compares them.
- bletest.BleHardwareTestApp - BLE hardware test matrix and report (`./gradlew runBleTest --args="--help"`)
