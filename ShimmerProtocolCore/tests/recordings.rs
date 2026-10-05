//! The framer on the two recorded sessions, against the Java decoder: the packets framed from the
//! stream must be the same packets, in the same order, as the Java state machine decoded
//! (compared by each packet's raw device timestamp, from the Java reference exports).

mod common;

use common::*;
use shimmer_protocol::framing::{Frame, StreamFramer};

/// Both devices' default configuration: 3-byte timestamp + 10 channels x 2 bytes; checksums on.
const PACKET_SIZE: usize = 23;
const CRC_BYTES: usize = 1;

fn frame(notifications: &[Vec<u8>]) -> (Vec<u32>, usize) {
    let mut framer = StreamFramer::new(PACKET_SIZE, CRC_BYTES);
    framer.expect_ack(); // START_STREAMING's ACK comes first
    let mut timestamps = Vec::new();
    let mut dropped = 0;
    for n in notifications {
        let mut frames = framer.push(n);
        // Framing pauses at an ACK; carry on with the rest of the bytes.
        let mut paused = frames.last() == Some(&Frame::Ack);
        while paused {
            let more = framer.push(&[]);
            paused = more.last() == Some(&Frame::Ack);
            frames.extend(more);
        }
        for f in frames {
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
    let notifications = stream_notifications(&load_session(SHIMMER3R_SESSION));
    let (timestamps, dropped) = frame(&notifications);
    assert_eq!(dropped, 0);
    assert_eq!(timestamps, java_timestamps(SHIMMER3R_REFERENCE));
    assert_eq!(timestamps.len(), 493);
}

#[test]
fn shimmer3_stream_frames_into_the_same_packets_as_java() {
    // This stream has gaps (the device dropped packets over its link); framing must not care.
    let notifications = stream_notifications(&load_session(SHIMMER3_SESSION));
    let (timestamps, dropped) = frame(&notifications);
    assert_eq!(dropped, 0);
    assert_eq!(timestamps, java_timestamps(SHIMMER3_REFERENCE));
    assert_eq!(timestamps.len(), 414);
}

#[test]
fn how_the_bytes_are_split_makes_no_difference() {
    let notifications = stream_notifications(&load_session(SHIMMER3R_SESSION));
    let one_byte_at_a_time: Vec<Vec<u8>> =
        notifications.iter().flatten().map(|b| vec![*b]).collect();
    assert_eq!(frame(&one_byte_at_a_time), frame(&notifications));
}

#[test]
fn a_corrupted_packet_is_dropped_and_framing_resumes() {
    let mut notifications = stream_notifications(&load_session(SHIMMER3R_SESSION));
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
