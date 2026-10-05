"""Calibration the recorded device never exercises (its own is nominal): a crafted calibration dump
with real offsets, uneven sensitivities and off-diagonal alignment, a duplicate record, an all-0xFF
record and an unknown sensor, decoded from synthetic packets. The inputs and the Java decoder's
outputs come from tests/data/java_calibration_reference.txt, written by the Java
API_00030_PythonReferenceTest."""

from shimmer3r.model import RADIO_DUMP, SENSOR_ACCEL_LN, SENSOR_GYRO, SENSOR_MAG, Shimmer3RModel
from support import DATA

REFERENCE = DATA / "java_calibration_reference.txt"
START_MS = 1790960000000


def load():
    lines = REFERENCE.read_text(encoding="utf-8").splitlines()
    inputs = {name: bytes.fromhex(value) for name, value in (l.split(",") for l in lines[:3])}
    channels = lines[3].split(",")[1:]
    rows = [
        (bytes.fromhex(r.split(",")[0]), [float(v) for v in r.split(",")[1:]]) for r in lines[4:]
    ]
    return inputs, channels, rows


def model_from(inputs) -> Shimmer3RModel:
    """The same calls, in the same order, as the Java generator makes."""
    m = Shimmer3RModel()
    m.apply_hardware_version(10)
    m.apply_firmware_version(bytes([3, 0, 1, 0, 1, 16]))
    m.apply_expansion_board(bytes([0x30, 0x08, 0x01]))
    m.apply_config_bytes(inputs["config"])
    m.apply_calibration_dump(inputs["dump"])
    m.apply_inquiry(inputs["inquiry"])
    m.prepare_for_streaming()
    return m


def test_the_crafted_dump_is_what_is_applied():
    m = model_from(load()[0])

    assert not m.gyro_on_the_fly_enabled
    accel = m.calibrations[SENSOR_ACCEL_LN][0]
    assert accel.source == RADIO_DUMP
    assert accel.current_offset == [
        [12.0],
        [-34.0],
        [2047.0],
    ], "the later record replaces the earlier"
    assert [accel.current_sensitivity[i][i] for i in range(3)] == [1650.0, 1680.0, 1700.0]
    assert accel.current_alignment[0] == [-0.99, 0.03, -0.02]
    assert m.calibrations[SENSOR_ACCEL_LN][2].current_offset == [[5.0], [5.0], [5.0]]
    gyro = m.calibrations[SENSOR_GYRO][m.gyro_range]
    assert m.gyro_range == 1
    assert [gyro.current_sensitivity[i][i] for i in range(3)] == [112.5, 115.0, 113.99]
    mag = m.calibrations[SENSOR_MAG][0]
    assert mag.current_offset == [[-120.0], [300.0], [5.0]], "the all-0xFF record is skipped"


def test_calibrated_values_are_identical_to_the_java_decoder():
    inputs, channels, rows = load()
    m = model_from(inputs)

    for i, (packet, expected) in enumerate(rows):
        sample = m.decode(packet, START_MS + 20 * i)
        assert [sample[c] for c in channels] == expected, f"packet {i}"
