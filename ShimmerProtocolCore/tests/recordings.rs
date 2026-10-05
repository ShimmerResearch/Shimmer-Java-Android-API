//! The framer on the two recorded sessions, against the Java decoder: the packets framed from the
//! stream must be the same packets, in the same order, as the Java state machine decoded
//! (compared by each packet's raw device timestamp, from the Java reference exports).

use std::fs;
use std::path::PathBuf;

use shimmer_protocol::framing::{Frame, StreamFramer};

/// Both devices' default configuration: 3-byte timestamp + 10 channels x 2 bytes; checksums on.
const PACKET_SIZE: usize = 23;
const CRC_BYTES: usize = 1;
const START_STREAMING: u8 = 0x07;

fn repo(path: &str) -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join(path)
}

/// The notifications that answer START_STREAMING: every RX line after its TX, up to the next TX.
fn stream_notifications(recording: &str) -> Vec<Vec<u8>> {
    let text = fs::read_to_string(repo(recording)).expect("recording");
    let mut after_start = false;
    let mut out = Vec::new();
    for line in text.lines() {
        let (Some(open), Some(close)) = (line.find('['), line.rfind(']')) else {
            continue;
        };
        let is_tx = line.contains(" TX ");
        if !is_tx && !line.contains(" RX ") {
            continue;
        }
        let bytes: Vec<u8> = line[open + 1..close]
            .split_whitespace()
            .map(|h| u8::from_str_radix(h.trim_start_matches("0x"), 16).unwrap())
            .collect();
        if is_tx {
            if after_start {
                break;
            }
            after_start = bytes.first() == Some(&START_STREAMING);
        } else if after_start {
            out.push(bytes);
        }
    }
    out
}

/// The raw timestamps (Timestamp, UNCAL) of every sample the Java decoder produced.
fn java_timestamps(reference: &str) -> Vec<u32> {
    let text = fs::read_to_string(repo(reference)).expect("Java reference");
    let mut lines = text.lines();
    let header: Vec<&str> = lines.next().unwrap().split(',').collect();
    let column = header
        .iter()
        .position(|h| *h == "Timestamp|UNCAL|Ticks")
        .expect("timestamp column");
    lines
        .map(|l| l.split(',').nth(column).unwrap().parse::<f64>().unwrap() as u32)
        .collect()
}

fn frame(notifications: &[Vec<u8>]) -> (Vec<u32>, usize) {
    let mut framer = StreamFramer::new(PACKET_SIZE, CRC_BYTES);
    framer.expect_ack(); // START_STREAMING's ACK comes first
    let mut timestamps = Vec::new();
    let mut dropped = 0;
    for n in notifications {
        for f in framer.push(n) {
            match f {
                Frame::Packet(p) => {
                    timestamps.push(p[0] as u32 | (p[1] as u32) << 8 | (p[2] as u32) << 16)
                }
                Frame::Dropped(d) => dropped += d,
                Frame::Ack => {}
            }
        }
    }
    (timestamps, dropped)
}

#[test]
fn shimmer3r_stream_frames_into_the_same_packets_as_java() {
    let notifications = stream_notifications(
        "ShimmerDriverPC/src/test/resources/protocol/shimmer3r_2f31_handshake_stream10s.bytes.log",
    );
    let (timestamps, dropped) = frame(&notifications);
    assert_eq!(dropped, 0);
    assert_eq!(
        timestamps,
        java_timestamps("python/tests/data/java_reference.csv")
    );
    assert_eq!(timestamps.len(), 493);
}

#[test]
fn shimmer3_stream_frames_into_the_same_packets_as_java() {
    // This stream has gaps (the device dropped packets over its link); framing must not care.
    let notifications = stream_notifications(
        "ShimmerDriverPC/src/test/resources/protocol/shimmer3_3e36_handshake_stream10s.bytes.log",
    );
    let (timestamps, dropped) = frame(&notifications);
    assert_eq!(dropped, 0);
    assert_eq!(
        timestamps,
        java_timestamps("python/tests/data/java_reference_shimmer3.csv")
    );
    assert_eq!(timestamps.len(), 414);
}

#[test]
fn how_the_bytes_are_split_makes_no_difference() {
    let notifications = stream_notifications(
        "ShimmerDriverPC/src/test/resources/protocol/shimmer3r_2f31_handshake_stream10s.bytes.log",
    );
    let one_byte_at_a_time: Vec<Vec<u8>> =
        notifications.iter().flatten().map(|b| vec![*b]).collect();
    assert_eq!(frame(&one_byte_at_a_time), frame(&notifications));
}

#[test]
fn a_corrupted_packet_is_dropped_and_framing_resumes() {
    let mut notifications = stream_notifications(
        "ShimmerDriverPC/src/test/resources/protocol/shimmer3r_2f31_handshake_stream10s.bytes.log",
    );
    let (clean, _) = frame(&notifications);
    // As in the Java test: the 3rd notification after START_STREAMING, byte 5 (in a packet's data).
    notifications[2][5] ^= 0xFF;
    let (corrupted, dropped) = frame(&notifications);

    assert_eq!(
        corrupted.len(),
        clean.len() - 1,
        "exactly the corrupted packet is lost"
    );
    assert!(dropped > 0, "the dropped bytes are reported");
    assert_eq!(
        corrupted.last(),
        clean.last(),
        "everything after it still frames"
    );
}
