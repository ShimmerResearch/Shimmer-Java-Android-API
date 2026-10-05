"""The protocol state machine on hand-written bytes: framing and failure paths that a good
recorded session never exercises. A port of the Java API_00028_LogAndStreamProtocolTest."""

from shimmer3r.crc import crc
from shimmer3r.events import Discarded, Error, LinkLost, State
from shimmer3r.protocol import Output, LogAndStreamProtocol

T0 = 1790960000000
T1 = T0 + LogAndStreamProtocol.SETTLE_MS  # when the handshake starts on a link that stays quiet
GET_SHIMMER_VERSION = bytes([0x3F])
GET_FW_VERSION = bytes([0x2E])
STOP_STREAMING = bytes([0x20])
SET_CRC_OFF = bytes([0x8B, 0x00])
SHIMMER3R_VERSION_REPLY = bytes([0xFF, 0x25, 0x0A])  # ACK, response, hardware version 10
STREAM_BYTES = bytes([0x00, 0x12, 0x34, 0x56, 0xFF, 0x00, 0x78])  # a stream left running


def messages(out: Output, kind: type) -> list[str]:
    return [
        getattr(e, "message", getattr(e, "reason", None)) for e in out.events if isinstance(e, kind)
    ]


def connect(p: LogAndStreamProtocol) -> Output:
    """Connects on a quiet link and starts the handshake, as the host's first due tick does."""
    assert not p.connect(T0).writes, "nothing is written until the link is quiet"
    return p.tick(T1)


def test_crc_matches_the_device_and_the_java_driver():
    # The device's own: its ACK to SET_CRC (one-byte mode) in the recording is 0xFF 0xF4.
    assert crc(b"\xff")[0] == 0xF4
    # What the Java ShimmerCrc computes for the same input. (The vector in ShimmerCrc.main is
    # not a valid one: Java computes 8a93 for it too, not the 9ab0 written there.)
    assert crc(bytes.fromhex("ff028002")) == bytes.fromhex("fe0e")
    assert crc(bytes(range(1, 25))) == bytes.fromhex("32ed")


def test_connect_waits_for_a_quiet_link_then_asks_for_the_hardware_version():
    p = LogAndStreamProtocol()
    assert not p.connect(T0).writes
    assert p.next_deadline() == T1
    assert not p.tick(T1 - 1)

    out = p.tick(T1)

    assert out.writes == [GET_SHIMMER_VERSION]
    assert not messages(out, Discarded), "a quiet link reports nothing"
    assert p.state is State.CONNECTING
    assert p.next_deadline() == T1 + LogAndStreamProtocol.DEFAULT_TIMEOUT_MS


def test_a_device_left_streaming_is_stopped_before_the_handshake():
    p = LogAndStreamProtocol()
    p.connect(T0)

    assert p.receive(STREAM_BYTES, T0 + 10).writes == [STOP_STREAMING], "stopped once"
    # More stream, and the STOP_STREAMING ACK, push the start of the handshake back.
    assert not p.receive(STREAM_BYTES, T0 + 100).writes
    quiet = T0 + 100 + LogAndStreamProtocol.SETTLE_MS
    assert p.next_deadline() == quiet
    assert not p.tick(quiet - 1)

    out = p.tick(quiet)

    assert f"dropped {2 * len(STREAM_BYTES)} byte(s)" in messages(out, Discarded)[0]
    # The earlier session's checksums go first, then the handshake starts as usual.
    assert out.writes == [SET_CRC_OFF]
    assert p.receive(bytes([0xFF]), quiet + 10).writes == [GET_SHIMMER_VERSION]
    # None of the dropped bytes is mistaken for the reply.
    assert p.receive(SHIMMER3R_VERSION_REPLY, quiet + 20).writes == [GET_FW_VERSION]


def test_firmware_that_does_not_ack_checksums_off_is_not_failed():
    p = LogAndStreamProtocol()
    p.connect(T0)
    p.receive(STREAM_BYTES, T0 + 10)
    quiet = T0 + 10 + LogAndStreamProtocol.SETTLE_MS
    assert p.tick(quiet).writes == [SET_CRC_OFF]

    out = p.tick(quiet + LogAndStreamProtocol.DEFAULT_TIMEOUT_MS)

    assert not messages(out, Error)
    assert "no ACK to SET_CRC_OFF" in messages(out, Discarded)[0]
    assert out.writes == [GET_SHIMMER_VERSION]
    assert p.state is State.CONNECTING


def test_a_device_that_does_not_stop_streaming_fails_the_connection():
    p = LogAndStreamProtocol()
    p.connect(T0)
    give_up = T0 + LogAndStreamProtocol.SETTLE_GIVE_UP_MS
    for t in range(T0 + 10, give_up, 100):
        p.receive(STREAM_BYTES, t)
        assert not p.tick(t)

    out = p.tick(give_up)

    assert "did not stop" in messages(out, Error)[0]
    assert p.state is State.FAILED
    assert p.next_deadline() is None


def test_a_lost_link_ends_the_protocol():
    p = LogAndStreamProtocol()
    connect(p)

    out = p.link_lost("device switched off", T1 + 10)

    assert messages(out, LinkLost) == ["device switched off"]
    assert p.state is State.DISCONNECTED
    assert p.next_deadline() is None, "no timeout is pending"
    assert not p.receive(SHIMMER3R_VERSION_REPLY, T1 + 20), "late bytes are ignored"
    assert not p.link_lost("again", T1 + 30), "reported once"
    assert not p.start_streaming(T1 + 40)
    assert "use a new LogAndStreamProtocol" in messages(p.connect(T1 + 50), Error)[0]
    assert p.state is State.DISCONNECTED, "still disconnected, not failed"


def test_an_ack_and_its_response_may_arrive_separately():
    p = LogAndStreamProtocol()
    connect(p)

    assert not p.receive(bytes([0xFF]), T1 + 10).writes
    out = p.receive(bytes([0x25, 0x0A]), T1 + 20)

    assert out.writes == [GET_FW_VERSION]


def test_bytes_before_the_ack_are_dropped_and_reported():
    p = LogAndStreamProtocol()
    connect(p)

    out = p.receive(bytes([0x12, 0x34, 0xFF, 0x25, 0x0A]), T1 + 10)

    assert len(messages(out, Discarded)) == 1
    assert out.writes == [GET_FW_VERSION]


def test_a_command_with_no_reply_times_out():
    p = LogAndStreamProtocol()
    connect(p)

    assert not p.tick(T1 + LogAndStreamProtocol.DEFAULT_TIMEOUT_MS - 1)
    out = p.tick(T1 + LogAndStreamProtocol.DEFAULT_TIMEOUT_MS)

    assert len(messages(out, Error)) == 1
    assert "GET_SHIMMER_VERSION" in messages(out, Error)[0]
    assert p.state is State.FAILED
    assert p.next_deadline() is None


def test_a_device_that_is_not_a_shimmer3r_is_refused():
    p = LogAndStreamProtocol()
    connect(p)

    out = p.receive(bytes([0xFF, 0x25, 0x03]), T1 + 10)  # hardware version 3 is a Shimmer3

    assert "not a Shimmer3R" in messages(out, Error)[0]
    assert not out.writes, "nothing more is sent"
    assert p.state is State.FAILED


def test_an_unexpected_response_fails_the_handshake():
    p = LogAndStreamProtocol()
    connect(p)

    # A firmware version response where the hardware version was asked for.
    out = p.receive(bytes([0xFF, 0x2F, 3, 0, 1, 0, 1, 16]), T1 + 10)

    assert "expected response 0x25" in messages(out, Error)[0]
    assert p.state is State.FAILED


def test_starting_to_stream_before_the_handshake_is_refused():
    p = LogAndStreamProtocol()
    connect(p)
    p.receive(SHIMMER3R_VERSION_REPLY, T1 + 10)

    out = p.start_streaming(T1 + 20)

    assert "start_streaming() called in state CONNECTING" in messages(out, Error)[0]
