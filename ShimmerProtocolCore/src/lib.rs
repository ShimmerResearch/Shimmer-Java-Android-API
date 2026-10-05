//! The Shimmer LogAndStream protocol core (DEV-1134 spike).
//!
//! No I/O, no threads, no clock and no dependencies, so the same code builds for Windows, macOS,
//! Linux, Android, iOS and WebAssembly. Each platform supplies only its transport, as with the
//! Java `LogAndStreamProtocol` this follows.
//!
//! So far: the Shimmer UART checksum and the data-stream framer.

pub mod crc;
pub mod ffi;
pub mod framing;
