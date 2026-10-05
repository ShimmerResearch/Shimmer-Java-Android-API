//! The protocol state machine on hand-written bytes: framing, failure paths and the hardware
//! differences a good recording never exercises. A port of the Java API_00028_LogAndStreamProtocolTest.

use shimmer_protocol::protocol::{
    Event, LogAndStreamProtocol, Output, State, DEFAULT_TIMEOUT_MS, SETTLE_GIVE_UP_MS, SETTLE_MS,
};

const T0: u64 = 1790960000000;
const T1: u64 = T0 + SETTLE_MS; // when the handshake starts on a link that stays quiet
const GET_SHIMMER_VERSION: &[u8] = &[0x3F];
const GET_FW_VERSION: &[u8] = &[0x2E];
const STOP_STREAMING: &[u8] = &[0x20];
const SET_CRC_OFF: &[u8] = &[0x8B, 0x00];
const SHIMMER3R_VERSION_REPLY: &[u8] = &[0xFF, 0x25, 0x0A]; // ACK, response, hardware version 10
const STREAM_BYTES: &[u8] = &[0x00, 0x12, 0x34, 0x56, 0xFF, 0x00, 0x78]; // a stream left running

fn errors(out: &Output) -> Vec<String> {
    out.events
        .iter()
        .filter_map(|e| {
            if let Event::Error(m) = e {
                Some(m.clone())
            } else {
                None
            }
        })
        .collect()
}

fn discarded(out: &Output) -> Vec<String> {
    out.events
        .iter()
        .filter_map(|e| {
            if let Event::Discarded(m) = e {
                Some(m.clone())
            } else {
                None
            }
        })
        .collect()
}

/// Connects on a quiet link and starts the handshake, as the host's first due tick does.
fn connect(p: &mut LogAndStreamProtocol) -> Output {
    assert!(
        p.connect(T0).writes.is_empty(),
        "nothing is written until the link is quiet"
    );
    p.tick(T1)
}

/// ACK, FW_VERSION_RESPONSE, LogAndStream (ID 3) at major.minor.internal.
fn version_reply(major: u8, minor: u8, internal: u8) -> Vec<u8> {
    vec![0xFF, 0x2F, 0x03, 0x00, major, 0x00, minor, internal]
}

/// Takes a device through the handshake to the command after the config bytes and returns it:
/// the pressure coefficients (0xA7) or the calibration dump (0x9A). Replies after SET_CRC carry a
/// checksum byte; replies' checksums are not enforced, so its value does not matter.
fn command_after_the_config_bytes(
    hardware: u8,
    minor: u8,
    internal: u8,
    board: [u8; 3],
) -> Vec<u8> {
    let mut p = LogAndStreamProtocol::new();
    connect(&mut p);
    let mut t = T1;
    t += 10;
    p.receive(&[0xFF, 0x25, hardware], t);
    t += 10;
    p.receive(&version_reply(1, minor, internal), t);
    t += 10;
    let out = p.receive(&[0xFF, 0x65, 0x03, board[0], board[1], board[2]], t);
    assert_eq!(out.writes[0], vec![0x8B, 0x01], "SET_CRC");
    t += 10;
    let mut out = p.receive(&[0xFF, 0x00], t);
    for chunk in 0..3 {
        assert_eq!(out.writes[0][0], 0x8E, "INFOMEM read {}", chunk);
        let mut reply = vec![0xFF, 0x8D, 0x80];
        reply.extend(std::iter::repeat_n(0xFF, 128)); // blank config bytes
        reply.push(0x00);
        t += 10;
        out = p.receive(&reply, t);
    }
    assert!(errors(&out).is_empty());
    out.writes[0].clone()
}

#[test]
fn connect_waits_for_a_quiet_link_then_asks_for_the_hardware_version() {
    let mut p = LogAndStreamProtocol::new();
    assert!(p.connect(T0).writes.is_empty());
    assert_eq!(p.next_deadline(), Some(T1));
    assert!(p.tick(T1 - 1).is_empty());

    let out = p.tick(T1);

    assert_eq!(out.writes, vec![GET_SHIMMER_VERSION.to_vec()]);
    assert!(discarded(&out).is_empty(), "a quiet link reports nothing");
    assert_eq!(p.state(), State::Connecting);
    assert_eq!(p.next_deadline(), Some(T1 + DEFAULT_TIMEOUT_MS));
}

#[test]
fn a_device_left_streaming_is_stopped_before_the_handshake() {
    let mut p = LogAndStreamProtocol::new();
    p.connect(T0);

    assert_eq!(
        p.receive(STREAM_BYTES, T0 + 10).writes,
        vec![STOP_STREAMING.to_vec()],
        "stopped once"
    );
    // More stream, and the STOP_STREAMING ACK, push the start of the handshake back.
    assert!(p.receive(STREAM_BYTES, T0 + 100).writes.is_empty());
    let quiet = T0 + 100 + SETTLE_MS;
    assert_eq!(p.next_deadline(), Some(quiet));
    assert!(p.tick(quiet - 1).is_empty());

    let out = p.tick(quiet);

    assert!(discarded(&out)[0].contains(&format!("dropped {} byte(s)", 2 * STREAM_BYTES.len())));
    // The earlier session's checksums go first, then the handshake starts as usual.
    assert_eq!(out.writes, vec![SET_CRC_OFF.to_vec()]);
    assert_eq!(
        p.receive(&[0xFF], quiet + 10).writes,
        vec![GET_SHIMMER_VERSION.to_vec()]
    );
    // None of the dropped bytes is mistaken for the reply.
    assert_eq!(
        p.receive(SHIMMER3R_VERSION_REPLY, quiet + 20).writes,
        vec![GET_FW_VERSION.to_vec()]
    );
}

#[test]
fn firmware_that_does_not_ack_checksums_off_is_not_failed() {
    let mut p = LogAndStreamProtocol::new();
    p.connect(T0);
    p.receive(STREAM_BYTES, T0 + 10);
    let quiet = T0 + 10 + SETTLE_MS;
    assert_eq!(p.tick(quiet).writes, vec![SET_CRC_OFF.to_vec()]);

    let out = p.tick(quiet + DEFAULT_TIMEOUT_MS);

    assert!(errors(&out).is_empty());
    assert!(discarded(&out)[0].contains("no ACK to SET_CRC_OFF"));
    assert_eq!(out.writes, vec![GET_SHIMMER_VERSION.to_vec()]);
    assert_eq!(p.state(), State::Connecting);
}

#[test]
fn a_device_that_does_not_stop_streaming_fails_the_connection() {
    let mut p = LogAndStreamProtocol::new();
    p.connect(T0);
    let give_up = T0 + SETTLE_GIVE_UP_MS;
    let mut t = T0 + 10;
    while t < give_up {
        p.receive(STREAM_BYTES, t);
        assert!(p.tick(t).is_empty());
        t += 100;
    }

    let out = p.tick(give_up);

    assert!(errors(&out)[0].contains("did not stop"));
    assert_eq!(p.state(), State::Failed);
    assert_eq!(p.next_deadline(), None);
}

#[test]
fn a_lost_link_ends_the_protocol() {
    let mut p = LogAndStreamProtocol::new();
    connect(&mut p);

    let out = p.link_lost("device switched off", T1 + 10);

    assert_eq!(out.events[0], Event::LinkLost("device switched off".into()));
    assert_eq!(p.state(), State::Disconnected);
    assert_eq!(p.next_deadline(), None, "no timeout is pending");
    assert!(
        p.receive(SHIMMER3R_VERSION_REPLY, T1 + 20).is_empty(),
        "late bytes are ignored"
    );
    assert!(p.link_lost("again", T1 + 30).is_empty(), "reported once");
    assert!(p.start_streaming(T1 + 40).is_empty());
    assert!(errors(&p.connect(T1 + 50))[0].contains("use a new LogAndStreamProtocol"));
    assert_eq!(
        p.state(),
        State::Disconnected,
        "still disconnected, not failed"
    );
}

#[test]
fn a_shimmer3_is_accepted() {
    let mut p = LogAndStreamProtocol::new();
    connect(&mut p);

    let out = p.receive(&[0xFF, 0x25, 0x03], T1 + 10);

    assert_eq!(out.writes, vec![GET_FW_VERSION.to_vec()]);
}

#[test]
fn shimmer3_firmware_older_than_v1_1_3_is_refused() {
    let mut p = LogAndStreamProtocol::new();
    connect(&mut p);
    p.receive(&[0xFF, 0x25, 0x03], T1 + 10);

    let out = p.receive(&version_reply(1, 1, 2), T1 + 20);

    assert!(errors(&out)[0].contains("older than LogAndStream v1.1.3"));
    assert_eq!(p.state(), State::Failed);
}

#[test]
fn shimmer3_firmware_v1_1_3_is_accepted() {
    let mut p = LogAndStreamProtocol::new();
    connect(&mut p);
    p.receive(&[0xFF, 0x25, 0x03], T1 + 10);

    let out = p.receive(&version_reply(1, 1, 3), T1 + 20);

    assert_eq!(
        out.writes,
        vec![vec![0x66, 0x03, 0x00]],
        "GET_DAUGHTER_CARD_ID"
    );
}

#[test]
fn a_shimmer3r_with_a_bmp581_on_v1_1_6_is_not_asked_for_pressure_coefficients() {
    // SR31-11.2 carries a BMP581 by SR number; LogAndStream_Shimmer3R v1.01.006 NACKs the command there.
    let bmp581_board = [31, 11, 2];
    let bmp390_board = [31, 11, 1];

    assert_eq!(
        command_after_the_config_bytes(10, 1, 6, bmp581_board)[0],
        0x9A,
        "v1.1.6, BMP581: skipped"
    );
    assert_eq!(
        command_after_the_config_bytes(10, 1, 7, bmp581_board)[0],
        0xA7,
        "v1.1.7, BMP581: asked"
    );
    assert_eq!(
        command_after_the_config_bytes(10, 1, 6, bmp390_board)[0],
        0xA7,
        "v1.1.6, BMP390: asked"
    );
    assert_eq!(
        command_after_the_config_bytes(3, 1, 3, bmp390_board)[0],
        0xA7,
        "Shimmer3 v1.1.3: asked"
    );
}

#[test]
fn an_ack_and_its_response_may_arrive_separately() {
    let mut p = LogAndStreamProtocol::new();
    connect(&mut p);

    assert!(p.receive(&[0xFF], T1 + 10).writes.is_empty());
    let out = p.receive(&[0x25, 0x0A], T1 + 20);

    assert_eq!(out.writes, vec![GET_FW_VERSION.to_vec()]);
}

#[test]
fn bytes_before_the_ack_are_dropped_and_reported() {
    let mut p = LogAndStreamProtocol::new();
    connect(&mut p);

    let out = p.receive(&[0x12, 0x34, 0xFF, 0x25, 0x0A], T1 + 10);

    assert_eq!(discarded(&out).len(), 1);
    assert_eq!(out.writes, vec![GET_FW_VERSION.to_vec()]);
}

#[test]
fn a_command_with_no_reply_times_out() {
    let mut p = LogAndStreamProtocol::new();
    connect(&mut p);

    assert!(p.tick(T1 + DEFAULT_TIMEOUT_MS - 1).is_empty());
    let out = p.tick(T1 + DEFAULT_TIMEOUT_MS);

    assert_eq!(errors(&out).len(), 1);
    assert!(errors(&out)[0].contains("GET_SHIMMER_VERSION"));
    assert_eq!(p.state(), State::Failed);
    assert_eq!(p.next_deadline(), None);
}

#[test]
fn a_device_that_is_neither_a_shimmer3_nor_a_shimmer3r_is_refused() {
    let mut p = LogAndStreamProtocol::new();
    connect(&mut p);

    let out = p.receive(&[0xFF, 0x25, 0x01], T1 + 10); // hardware version 1 is a Shimmer2R

    assert!(errors(&out)[0].contains("not a Shimmer3 or Shimmer3R"));
    assert!(out.writes.is_empty(), "nothing more is sent");
    assert_eq!(p.state(), State::Failed);
}

#[test]
fn an_unexpected_response_fails_the_handshake() {
    let mut p = LogAndStreamProtocol::new();
    connect(&mut p);

    // A firmware version response where the hardware version was asked for.
    let out = p.receive(&[0xFF, 0x2F, 3, 0, 1, 0, 1, 16], T1 + 10);

    assert!(errors(&out)[0].contains("expected response 0x25"));
    assert_eq!(p.state(), State::Failed);
}

#[test]
fn starting_to_stream_before_the_handshake_is_refused() {
    let mut p = LogAndStreamProtocol::new();
    connect(&mut p);
    p.receive(SHIMMER3R_VERSION_REPLY, T1 + 10);

    let out = p.start_streaming(T1 + 20);

    assert!(errors(&out)[0].contains("start_streaming() called in state Connecting"));
}
