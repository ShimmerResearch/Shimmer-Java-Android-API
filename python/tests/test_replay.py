"""The protocol replayed against the recorded session, and its samples compared value by value
with the Java decoder's for the same recording (tests/data/java_reference.csv, kept current by
the Java API_00030_PythonReferenceTest). Ports of the Java API_00027 tests where they apply."""

from shimmer3r.events import Discarded, State
from shimmer3r.protocol import START_STREAMING_COMMAND, LogAndStreamProtocol
from support import (
    Replay,
    corrupt_rx,
    first_rx_after,
    load_java_reference,
    load_session,
    split_into_single_bytes,
)


def test_the_handshake_completes_and_streams():
    p = LogAndStreamProtocol()
    r = Replay.run(load_session(), p)

    assert r.errors() == []
    assert p.state is State.STREAMING
    assert p.sampling_rate == 51.2
    assert r.responder.unanswered == []
    _, java = load_java_reference()
    assert len(r.samples) == len(java) == 493


def test_samples_are_identical_to_the_java_decoder():
    """Every channel, raw and calibrated, of every sample: equal to the last bit, not just close.
    That includes the gyro after its on-the-fly offset recalibration (from sample 51 on), and the
    PC-clock channels, whose equality shows the replay itself runs exactly as the Java one."""
    r = Replay.run(load_session())
    header, java = load_java_reference()
    assert {c[0] for c in header} == set(r.samples[0].readings), "the same channels"

    for i, (sample, expected) in enumerate(zip(r.samples, java, strict=True)):
        for channel, fmt, units in header:
            reading = sample.readings[channel]
            value = reading.cal if fmt == "CAL" else reading.uncal
            assert value == expected[(channel, fmt, units)], f"sample {i}, {channel} {fmt}"
            if fmt == "CAL":
                assert reading.units == units, channel


def test_how_the_bytes_are_split_makes_no_difference():
    session = load_session()
    whole = Replay.run(session)
    one_byte_at_a_time = Replay.run(split_into_single_bytes(session))

    assert one_byte_at_a_time.errors() == []
    assert len(one_byte_at_a_time.samples) == len(whole.samples)
    for a, b in zip(whole.samples, one_byte_at_a_time.samples):
        assert a.readings["Timestamp"] == b.readings["Timestamp"]
        assert a["Accel_LN_X"] == b["Accel_LN_X"]


def test_a_corrupted_packet_is_dropped_and_decoding_resumes():
    session = load_session()
    # The 3rd notification after START_STREAMING; byte 5 is inside its first packet's data.
    notification = first_rx_after(session, START_STREAMING_COMMAND) + 2
    clean = Replay.run(session)
    corrupted = Replay.run(corrupt_rx(session, notification, 5))

    assert corrupted.errors() == []
    assert len(corrupted.samples) == len(clean.samples) - 1, "exactly the corrupted packet is lost"
    assert any(isinstance(e, Discarded) for e in corrupted.events), "the dropped bytes are reported"
    # Everything after the corrupted packet still decodes to the same values.
    assert corrupted.samples[-1].readings["Timestamp"] == clean.samples[-1].readings["Timestamp"]
