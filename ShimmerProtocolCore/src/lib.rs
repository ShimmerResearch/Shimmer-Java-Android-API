//! The Shimmer LogAndStream protocol core (DEV-1134 spike).
//!
//! No I/O, no threads, no clock and no dependencies, so the same code builds for Windows, macOS,
//! Linux, Android, iOS and WebAssembly. Each platform supplies only its transport, as with the
//! Java `LogAndStreamProtocol` this follows.
//!
//! So far: the checksum, the stream framer, the protocol state machine, and the device model:
//! identity, firmware rules, configuration, calibration and decoding, for Shimmer3 and Shimmer3R.

pub mod calibration;
pub mod crc;
pub mod ffi;
pub mod framing;
pub mod model;
pub mod protocol;
