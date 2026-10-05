//! A plain C ABI over the core, for hosts that are not Rust: WebAssembly (JavaScript), and later
//! JNI, Python, Swift or Kotlin bindings. Spike scope: the checksum and the stream framer.
//!
//! Memory: the host allocates buffers in the core's memory with `shimmer_alloc`, fills them, and
//! frees them with `shimmer_free`. Framers are created and freed with their own pair.

use crate::crc::crc;
use crate::framing::{Frame, StreamFramer};

#[no_mangle]
pub extern "C" fn shimmer_alloc(len: usize) -> *mut u8 {
    let mut buf = Vec::<u8>::with_capacity(len.max(1));
    let ptr = buf.as_mut_ptr();
    std::mem::forget(buf);
    ptr
}

/// # Safety
/// `ptr` and `len` must come from one `shimmer_alloc` call.
#[no_mangle]
pub unsafe extern "C" fn shimmer_free(ptr: *mut u8, len: usize) {
    drop(Vec::from_raw_parts(ptr, 0, len.max(1)));
}

/// The checksum of `len` bytes, LSB in bits 0-7 and MSB in bits 8-15.
///
/// # Safety
/// `ptr` must point at `len` readable bytes, and `len` must not be 0.
#[no_mangle]
pub unsafe extern "C" fn shimmer_crc(ptr: *const u8, len: usize) -> u32 {
    let c = crc(std::slice::from_raw_parts(ptr, len));
    c[0] as u32 | (c[1] as u32) << 8
}

#[no_mangle]
pub extern "C" fn shimmer_framer_new(packet_size: u32, crc_bytes: u32) -> *mut StreamFramer {
    Box::into_raw(Box::new(StreamFramer::new(
        packet_size as usize,
        crc_bytes as usize,
    )))
}

/// # Safety
/// `framer` must come from `shimmer_framer_new` and not be used afterwards.
#[no_mangle]
pub unsafe extern "C" fn shimmer_framer_free(framer: *mut StreamFramer) {
    drop(Box::from_raw(framer));
}

/// # Safety
/// `framer` must come from `shimmer_framer_new`.
#[no_mangle]
pub unsafe extern "C" fn shimmer_framer_expect_ack(framer: *mut StreamFramer) {
    (*framer).expect_ack();
}

/// Pushes `len` received bytes. Each complete packet's payload (packet_size bytes) is copied to
/// `out`, up to `out_packets` of them, and the number of packets framed is returned; acks and
/// dropped bytes are not reported through this call.
///
/// # Safety
/// `framer` must come from `shimmer_framer_new`; `bytes` must point at `len` readable bytes and
/// `out` at `out_packets * packet_size` writable ones.
#[no_mangle]
pub unsafe extern "C" fn shimmer_framer_push(
    framer: *mut StreamFramer,
    bytes: *const u8,
    len: usize,
    out: *mut u8,
    out_packets: usize,
) -> usize {
    let frames = (*framer).push(std::slice::from_raw_parts(bytes, len));
    let mut n = 0;
    for frame in frames {
        if let Frame::Packet(payload) = frame {
            if n < out_packets {
                std::ptr::copy_nonoverlapping(
                    payload.as_ptr(),
                    out.add(n * payload.len()),
                    payload.len(),
                );
            }
            n += 1;
        }
    }
    n
}
