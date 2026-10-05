"""The Shimmer3R device model behind the protocol: identity, configuration, calibration and packet
decoding. The protocol feeds it the parsed handshake replies and asks it to decode data packets;
nothing here does I/O.

Ported from the Java driver code that LogAndStreamModel reuses: ShimmerObject (configuration,
inquiry, buildMsg), ShimmerDevice (calibration dump, packet counters), ShimmerVerObject,
UtilCalibration, CalibDetailsKinematic, TimestampUnwrap, SensorLSM6DSV and SensorLIS2MDL. The
arithmetic follows the Java expression for expression, so the doubles come out identical.

Decoded so far: the timestamp, low-noise accel, gyro, mag, battery, and the per-packet channels
the Java adds (battery %, reception rates, system timestamps, event marker). Other channels are
parsed for their size, so the packet layout stays right, but not decoded.
"""

from __future__ import annotations

import math
from collections import deque
from decimal import ROUND_HALF_UP, Decimal

from .events import Reading, Sample

SHIMMER_3R = 10  # ShimmerVerDetails.HW_ID.SHIMMER_3R
LOGANDSTREAM = 3  # ShimmerVerDetails.FW_ID.LOGANDSTREAM
_FIRMWARE_LABELS = {LOGANDSTREAM: "LogAndStream"}

RTC_HZ = 32768.0  # the packet timestamp's clock
CLOCK_HZ = 32768.0  # the sampling clock, unless the TCXO is in use
TCXO_HZ = 255765.625

CONFIG_BYTE_LENGTH = 384  # ConfigByteLayoutShimmer3.calculateConfigByteLength for Shimmer3R
CONFIG_BYTE_START_ADDRESS = 0

# Calibration read sources, lowest priority first (CalibDetails.CALIB_READ_SOURCE).
UNKNOWN, SD_HEADER, LEGACY_BT_COMMAND, INFOMEM, RADIO_DUMP = range(5)

# Calibration sensor IDs (Configuration).
SENSOR_ACCEL_LN = 37  # LSM6DSV accel
SENSOR_GYRO = 38  # LSM6DSV gyro
SENSOR_MAG = 42  # LIS2MDL

# --- Packet layout (ShimmerObject.interpretDataPacketFormat, Shimmer3R branches) ----------------

_TYPE_SIZES = {
    "u8": 1,
    "i16": 2,
    "i16r": 2,
    "u12": 2,
    "u14": 2,
    "u16": 2,
    "u24": 3,
    "i24r": 3,
    "i12*>": 2,
}
_CHANNELS = {
    0x00: ("Accel_LN_X", "i16"),
    0x01: ("Accel_LN_Y", "i16"),
    0x02: ("Accel_LN_Z", "i16"),
    0x03: ("Battery", "i16"),
    0x04: ("Accel_WR_X", "i16"),
    0x05: ("Accel_WR_Y", "i16"),
    0x06: ("Accel_WR_Z", "i16"),
    0x07: ("Mag_X", "i16"),
    0x08: ("Mag_Y", "i16"),
    0x09: ("Mag_Z", "i16"),
    0x0A: ("Gyro_X", "i16"),
    0x0B: ("Gyro_Y", "i16"),
    0x0C: ("Gyro_Z", "i16"),
    0x0D: ("Ext_ADC_0", "u14"),
    0x0E: ("Ext_ADC_1", "u14"),
    0x0F: ("Ext_ADC_2", "u14"),
    0x10: ("Int_ADC_3", "u14"),
    0x11: ("Int_ADC_0", "u14"),
    0x12: ("Int_ADC_1", "u14"),
    0x13: ("Int_ADC_2", "u14"),
    0x14: ("Accel_HighG_X", "i12*>"),
    0x15: ("Accel_HighG_Y", "i12*>"),
    0x16: ("Accel_HighG_Z", "i12*>"),
    0x17: ("Alt_Mag_X", "i16"),
    0x18: ("Alt_Mag_Y", "i16"),
    0x19: ("Alt_Mag_Z", "i16"),
    0x1A: ("Temperature", "u24"),
    0x1B: ("Pressure", "u24"),
    0x1C: ("GSR", "u16"),
    0x1D: ("EXG1_Status", "u8"),
    0x1E: ("EXG1_CH1_24BIT", "i24r"),
    0x1F: ("EXG1_CH2_24BIT", "i24r"),
    0x20: ("EXG2_Status", "u8"),
    0x21: ("EXG2_CH1_24BIT", "i24r"),
    0x22: ("EXG2_CH2_24BIT", "i24r"),
    0x23: ("EXG1_CH1_16BIT", "i16r"),
    0x24: ("EXG1_CH2_16BIT", "i16r"),
    0x25: ("EXG2_CH1_16BIT", "i16r"),
    0x26: ("EXG2_CH2_16BIT", "i16r"),
    0x27: ("Bridge_Amp_High", "u12"),
    0x28: ("Bridge_Amp_Low", "u12"),
}


def _parse_field(data: bytes, offset: int, kind: str) -> int:
    """UtilParseData.parseData for one field. Unsigned types are not masked, as in the Java."""
    size = _TYPE_SIZES[kind]
    raw = data[offset : offset + size]
    if kind in ("i16r", "i24r"):
        return int.from_bytes(raw, "big", signed=True)
    if kind == "i16":
        return int.from_bytes(raw, "little", signed=True)
    return int.from_bytes(raw, "little", signed=False)


# --- Calibration (UtilCalibration, CalibArraysKinematic, CalibDetailsKinematic) ----------------


def _inverse3x3(m):
    """UtilCalibration.matrixInverse3x3, term for term."""
    (a, b, c), (d, e, f), (g, h, i) = m
    deter = a * e * i + b * f * g + c * d * h - c * e * g - b * d * i - a * f * h
    return [
        [
            (1 / deter) * (e * i - f * h),
            (1 / deter) * (c * h - b * i),
            (1 / deter) * (b * f - c * e),
        ],
        [
            (1 / deter) * (f * g - d * i),
            (1 / deter) * (a * i - c * g),
            (1 / deter) * (c * d - a * f),
        ],
        [
            (1 / deter) * (d * h - e * g),
            (1 / deter) * (g * b - a * h),
            (1 / deter) * (a * e - b * d),
        ],
    ]


def _multiply(a, b):
    """UtilCalibration.matrixMultiplication: accumulates from 0.0 in k order."""
    result = [[0.0] * len(b[0]) for _ in a]
    for i in range(len(a)):
        for j in range(len(b[0])):
            for k in range(len(b)):
                result[i][j] += a[i][k] * b[k][j]
    return result


def _precision_correction(value: float, places: int) -> float:
    """UtilShimmer.applyPrecisionCorrection: BigDecimal(value).setScale(places, HALF_UP)."""
    return float(Decimal(value).quantize(Decimal(1).scaleb(-places), rounding=ROUND_HALF_UP))


def _nudge(m, lo: float, hi: float, places: int):
    """UtilShimmer.nudgeDoubleArray: clamp into range, then round."""
    return [[_precision_correction(max(lo, min(hi, v)), places) for v in row] for row in m]


def _precision(scale: float) -> int:
    """CalibDetailsKinematic.calculatePrecision."""
    return int(math.log10(scale) + 1) - 1


def _premultiplied(alignment, sensitivity):
    """inverse(alignment) x inverse(sensitivity), as CalibArraysKinematic precomputes it."""
    return _multiply(_inverse3x3(alignment), _inverse3x3(sensitivity))


class KinematicCalibration:
    """One inertial sensor at one range: its defaults, and the current values read from the
    device. Each current array falls back to its default on its own, as in the Java ("valid")."""

    ALIGNMENT_SCALE = 100.0

    def __init__(self, sensitivity: float, alignment, sensitivity_scale: float = 1.0) -> None:
        self.default_offset = [[0.0], [0.0], [0.0]]
        self.default_sensitivity = [
            [sensitivity, 0.0, 0.0],
            [0.0, sensitivity, 0.0],
            [0.0, 0.0, sensitivity],
        ]
        self.default_alignment = [list(map(float, row)) for row in alignment]
        self._default_matrix = _premultiplied(self.default_alignment, self.default_sensitivity)
        self.current_offset = None
        self.current_sensitivity = None
        self.current_alignment = None
        self._current_matrix = None
        self.source = UNKNOWN
        self._sensitivity_scale = sensitivity_scale

    def parse(self, record: bytes, source: int) -> None:
        """CalibDetailsKinematic.parseCalParamByteArray: a 21-byte record, offsets and
        sensitivities as big-endian i16, then the alignment matrix as nine i8 / 100."""
        if source < self.source or record == b"\xff" * len(record) or not any(record):
            return
        self.source = source
        f = [int.from_bytes(record[i : i + 2], "big", signed=True) for i in range(0, 12, 2)]
        f += [int.from_bytes(record[i : i + 1], "big", signed=True) for i in range(12, 21)]
        am = [f[6 + i] / self.ALIGNMENT_SCALE for i in range(9)]
        alignment = [am[0:3], am[3:6], am[6:9]]
        sensitivity = [[float(f[3]), 0.0, 0.0], [0.0, float(f[4]), 0.0], [0.0, 0.0, float(f[5])]]
        for i in range(3):
            sensitivity[i][i] = sensitivity[i][i] / self._sensitivity_scale
        offset = [[float(f[0])], [float(f[1])], [float(f[2])]]

        def bounds(scale: float):
            places = _precision(scale)
            return (
                _precision_correction(-32768 / scale, places),
                _precision_correction(32767 / scale, places),
                places,
            )

        a_lo, a_hi = _precision_correction(-128 / 100.0, 2), _precision_correction(127 / 100.0, 2)
        s_lo, s_hi, s_places = bounds(self._sensitivity_scale)
        o_lo, o_hi, o_places = bounds(1.0)
        self.current_alignment = _nudge(alignment, a_lo, a_hi, 2)
        self.current_sensitivity = _nudge(sensitivity, s_lo, s_hi, s_places)
        self.current_offset = _nudge(offset, o_lo, o_hi, o_places)
        self._current_matrix = _premultiplied(self.current_alignment, self.current_sensitivity)

    def update_offset(self, x: float, y: float, z: float) -> None:
        """CalibArraysKinematic.updateOffsetVector: replaces the offset as is, not rounded."""
        self.current_offset = [[x], [y], [z]]

    def apply(self, u: list[float]) -> list[float]:
        """UtilCalibration.calibrateInertialSensorData: inv(R) inv(K) (U - b)."""
        b = self.current_offset or self.default_offset
        d = [[u[0] - b[0][0]], [u[1] - b[1][0]], [u[2] - b[2][0]]]
        r = _multiply(self._current_matrix or self._default_matrix, d)
        return [r[0][0], r[1][0], r[2][0]]


# --- Gyro on-the-fly offset calibration (GyroOnTheFlyCalModule, OnTheFlyGyroOffsetCal) ---------


def _mean(values) -> float:
    """Apache Commons Math 2.2 Mean.evaluate: the sum's mean, then a correction pass."""
    n = float(len(values))
    total = 0.0
    for v in values:
        total += v
    xbar = total / n
    correction = 0.0
    for v in values:
        correction += v - xbar
    return xbar + (correction / n)


def _standard_deviation(values) -> float:
    """Commons Math 2.2 DescriptiveStatistics.getStandardDeviation (bias-corrected Variance)."""
    if len(values) == 1:
        return 0.0
    m = _mean(values)
    accum = 0.0
    accum2 = 0.0
    for v in values:
        dev = v - m
        accum += dev * dev
        accum2 += dev
    n = float(len(values))
    return math.sqrt((accum - (accum2 * accum2 / n)) / (n - 1.0))


class GyroOnTheFlyCalibration:
    """While the device lies still, re-estimates the gyro offset: once a window of round(fs)
    samples is full, if every calibrated axis varies by less than 1.2 deg/s (standard deviation),
    the mean of the raw values becomes the offset, from the next packet on. Enabled by derived
    sensor bit 29 in the config bytes."""

    THRESHOLD = 1.2

    def __init__(self) -> None:
        self.window = 0
        self._cal = [deque() for _ in range(3)]
        self._uncal = [deque() for _ in range(3)]

    def set_window(self, sampling_rate: float) -> None:
        """setBufferSizeFromSamplingRate: Java's Math.round, so halves round up. Values already
        in the window are kept, as DescriptiveStatistics keeps them."""
        self.window = int(math.floor(sampling_rate + 0.5))
        self._cal = [deque(d, maxlen=self.window) for d in self._cal]
        self._uncal = [deque(d, maxlen=self.window) for d in self._uncal]

    def update(
        self, calibration: KinematicCalibration, cal: list[float], uncal: list[float]
    ) -> None:
        for axis in range(3):
            self._cal[axis].append(cal[axis])
            self._uncal[axis].append(uncal[axis])
        if len(self._cal[1]) == self.window:
            if all(_standard_deviation(d) < self.THRESHOLD for d in self._cal):
                calibration.update_offset(*(_mean(d) for d in self._uncal))


# Defaults: SensorLSM6DSV and SensorLIS2MDL.
_LSM6DSV_ALIGNMENT = [[-1, 0, 0], [0, 1, 0], [0, 0, -1]]
_LIS2MDL_ALIGNMENT = [[-1, 0, 0], [0, -1, 0], [0, 0, -1]]
_ACCEL_SENSITIVITY = {0: 1672, 1: 836, 2: 418, 3: 209}  # ±2/4/8/16 g
_GYRO_SENSITIVITY = {0: 229, 1: 114, 2: 57, 3: 29, 4: 14, 5: 7}  # 125..4000 dps


def _default_calibrations() -> dict[int, dict[int, KinematicCalibration]]:
    return {
        SENSOR_ACCEL_LN: {
            r: KinematicCalibration(s, _LSM6DSV_ALIGNMENT) for r, s in _ACCEL_SENSITIVITY.items()
        },
        SENSOR_GYRO: {
            r: KinematicCalibration(s, _LSM6DSV_ALIGNMENT, sensitivity_scale=100.0)
            for r, s in _GYRO_SENSITIVITY.items()
        },
        SENSOR_MAG: {0: KinematicCalibration(667, _LIS2MDL_ALIGNMENT)},
    }


# --- Timestamps (TimestampUnwrap) ---------------------------------------------------------------

TICKS_MAX_3_BYTE = 1 << 24
WRAP_WINDOW_TICKS = 32768
REORDER_PERIODS = 8
MAX_WINDOW_DIVISOR = 8


def _reorder_window_ticks(sampling_rate: float, max_ticks: int) -> float:
    if math.isnan(sampling_rate) or math.isinf(sampling_rate) or sampling_rate <= 0.0:
        return 0.0
    window = REORDER_PERIODS * RTC_HZ / sampling_rate
    return min(window, max_ticks / MAX_WINDOW_DIVISOR)


def _unwrap(raw, last, cycle, max_ticks, window, has_previous):
    """TimestampUnwrap.unwrap: (unwrapped, cycle, rejected)."""
    if not has_previous:
        return raw, 0.0, False
    last_raw = last - (max_ticks * cycle)
    forward = raw - last_raw
    if forward < 0:
        forward += max_ticks
    backwards = max_ticks - forward
    if forward == 0.0:
        candidate = last  # a duplicate
    elif backwards <= window:
        candidate = last - backwards  # reordered
    elif (
        max_ticks == TICKS_MAX_3_BYTE and raw == 0.0 and last_raw < (max_ticks - WRAP_WINDOW_TICKS)
    ):
        return last, cycle, True  # a record the firmware never stamped
    else:
        candidate = last + forward
    return candidate, float(math.floor(candidate / max_ticks)), False


def battery_percentage(volts: float) -> float:
    """ShimmerBattStatusDetails.calculateBattPercentage: a 4th-order fit, clamped to 0..100."""
    if volts > (4.167 + 0.2):
        volts = 4.167
    elif volts < (3.2 - 0.2):
        volts = 3.2
    pct = (
        (1109.739792 * math.pow(volts, 4))
        - (17167.12674 * math.pow(volts, 3))
        + (99232.71686 * math.pow(volts, 2))
        - (253825.397 * volts)
        + 242266.0527
    )
    return 100.0 if pct > 100 else 0.0 if pct < 0 else pct


class Shimmer3RModel:
    def __init__(self) -> None:
        self.hardware_version = -1
        self.firmware_identifier = -1
        self.firmware_version = (0, 0, 0)
        self.firmware_version_code = -1
        self.expansion_board = b""
        self.crc_mode = 0
        self.config = b""
        self.sampling_rate = float("nan")
        self.channel_ids: list[int] = []
        self.packet_size = 0
        self.streaming = False

        self.accel_range = 0
        self.gyro_range = 1  # the default configuration's; InfoMem and the inquiry set the real one
        self.calibrations = _default_calibrations()
        self.gyro_on_the_fly = GyroOnTheFlyCalibration()
        self.pressure_coefficients = b""

        self._layout: list[tuple[str, str, int]] = []  # (name, type, offset)
        self._battery_percentage = -1.0
        self._reset_stream_state()

    # --- Identity -------------------------------------------------------------------------------

    @property
    def is_shimmer3r(self) -> bool:
        return self.hardware_version == SHIMMER_3R

    def apply_hardware_version(self, version: int) -> None:
        self.hardware_version = version

    def apply_firmware_version(self, six: bytes) -> None:
        """FW_VERSION_RESPONSE: identifier (2 bytes LE), major (2 bytes LE), minor, internal."""
        self.firmware_identifier = six[0] | six[1] << 8
        self.firmware_version = (six[2] | six[3] << 8, six[4], six[5])
        self.firmware_version_code = _firmware_version_code(
            self.hardware_version, self.firmware_identifier, self.firmware_version
        )

    @property
    def firmware_version_text(self) -> str:
        label = _FIRMWARE_LABELS.get(
            self.firmware_identifier, f"firmware ID {self.firmware_identifier}"
        )
        return f"{label} v{'.'.join(str(v) for v in self.firmware_version)}"

    @property
    def is_bt_crc_mode_supported(self) -> bool:
        """ShimmerBluetooth.isBtCrcModeSupported."""
        return self.firmware_version_code >= 8

    def apply_expansion_board(self, three: bytes) -> None:
        self.expansion_board = bytes(three)

    def apply_crc_mode(self, mode: int) -> None:
        self.crc_mode = mode

    # --- Configuration and calibration ----------------------------------------------------------

    @property
    def config_byte_length(self) -> int:
        return CONFIG_BYTE_LENGTH

    @property
    def config_byte_start_address(self) -> int:
        return CONFIG_BYTE_START_ADDRESS

    @property
    def config_valid(self) -> bool:
        """ConfigByteLayout: config bytes starting with six 0xFF were never written."""
        return len(self.config) >= 6 and self.config[:6] != b"\xff" * 6

    @property
    def gyro_on_the_fly_enabled(self) -> bool:
        """Derived sensor bit 29 (GYRO_ON_THE_FLY_CAL): derived-sensors byte 3 (byte 118), bit 5."""
        return self.config_valid and bool(self.config[118] & 0x20)

    @property
    def uses_tcxo(self) -> bool:
        """Experiment config byte 1 (byte 218), bit 4."""
        return self.config_valid and bool(self.config[218] & 0x10)

    def apply_config_bytes(self, config: bytes) -> None:
        """The parts of ShimmerObject.configBytesParse and the LSM6DSV/LIS2MDL parsers that the
        decode depends on: the ranges, then each sensor's calibration at its current range."""
        self.config = bytes(config)
        if not self.config_valid:
            return  # the Java falls back to its default configuration
        self.accel_range = (config[9] >> 6) & 0x03
        self.gyro_range = ((config[130] >> 2) & 0x01) << 2 | (config[8] & 0x03)
        self._calibration(SENSOR_ACCEL_LN, self.accel_range).parse(config[34:55], INFOMEM)
        self._calibration(SENSOR_GYRO, self.gyro_range).parse(config[55:76], INFOMEM)
        self._calibration(SENSOR_MAG, 0).parse(config[76:97], INFOMEM)

    def apply_pressure_coefficients(self, payload: bytes) -> str | None:
        """Kept for when pressure is decoded; nothing here uses it yet."""
        if not payload:
            return "zero length, no sensor ID"
        self.pressure_coefficients = bytes(payload)
        return None

    def apply_calibration_dump(self, dump: bytes) -> None:
        """ShimmerDevice.calibByteDumpParse: after the length (2 bytes) and version (8 bytes),
        records of [sensor ID u16 LE, range, length, 8-byte time, calibration bytes]. A later
        record for the same sensor and range replaces an earlier one."""
        i = 10
        while len(dump) - i > 12:
            sensor_id = dump[i] | dump[i + 1] << 8
            rng = dump[i + 2]
            length = dump[i + 3]
            record = dump[i + 12 : i + 12 + length]
            calibration = self.calibrations.get(sensor_id, {}).get(rng)
            if calibration is not None:
                calibration.parse(record, RADIO_DUMP)
            i += 12 + length

    def apply_inquiry(self, inquiry: bytes) -> None:
        """ShimmerObject.interpretInqResponse, Shimmer3R branch, and interpretDataPacketFormat."""
        clock = TCXO_HZ if self.uses_tcxo else CLOCK_HZ
        self.sampling_rate = clock / (inquiry[0] + (inquiry[1] << 8))
        config = int.from_bytes(inquiry[2:9], "little")
        self.gyro_range = ((config >> 16) & 0x03) + (((config >> 34) & 0x01) << 2)
        n = inquiry[9]
        self.channel_ids = list(inquiry[11 : 11 + n])
        self._layout = [("Timestamp", "u24", 0)]
        offset = 3
        for cid in self.channel_ids:
            name, kind = _CHANNELS.get(cid, (str(cid), "u12"))
            self._layout.append((name, kind, offset))
            offset += _TYPE_SIZES[kind]
        self.packet_size = offset

    def _calibration(self, sensor_id: int, rng: int) -> KinematicCalibration:
        return self.calibrations[sensor_id][rng]

    # --- Streaming ------------------------------------------------------------------------------

    def prepare_for_streaming(self) -> None:
        """What ShimmerBluetooth.startStreaming resets: packet counters and the timestamp; and
        what initializeAlgorithms sets: the gyro on-the-fly window."""
        self._reset_stream_state()
        if self.gyro_on_the_fly_enabled:
            self.gyro_on_the_fly.set_window(self.sampling_rate)

    def streaming_started(self) -> None:
        self.streaming = True

    def streaming_stopped(self) -> None:
        self.streaming = False

    def _reset_stream_state(self) -> None:
        self._last_unwrapped = 0.0
        self._cycle = 0.0
        self._has_previous = False
        self._rejected = False
        self._start_ms_saved = False
        self._start_ms = -1.0
        self._received_overall = 0
        self._reception_trial = 0.0
        self._first_offset: float | None = None  # SystemTimestampPlot
        self._first_plot: float | None = None

    def decode(self, packet: bytes, pc_time_ms: int) -> Sample:
        """ShimmerObject.buildMsg (Bluetooth), then SystemTimestampPlot."""
        raw = {name: _parse_field(packet, offset, kind) for name, kind, offset in self._layout}
        r: dict[str, Reading] = {}

        ticks = float(raw["Timestamp"])
        r["Clock 3_LSB"] = Reading(ticks, "Ticks", ticks)
        unwrapped = self._unwrap(ticks)
        ms = unwrapped / RTC_HZ * 1000
        self._received_overall += 1
        if not self._rejected:
            self._trial_packet_loss(ms)
        r["Timestamp"] = Reading(ms, "ms", ticks)

        self._inertial(
            r,
            raw,
            ("Accel_LN_X", "Accel_LN_Y", "Accel_LN_Z"),
            "m/(s^2)",
            self._calibration(SENSOR_ACCEL_LN, self.accel_range),
        )
        self._inertial(
            r,
            raw,
            ("Gyro_X", "Gyro_Y", "Gyro_Z"),
            "deg/s",
            self._calibration(SENSOR_GYRO, self.gyro_range),
        )
        self._inertial(
            r, raw, ("Mag_X", "Mag_Y", "Mag_Z"), "local_flux", self._calibration(SENSOR_MAG, 0)
        )
        if "Battery" in raw:
            battery = float(raw["Battery"])
            mv = (
                (battery - 0.0) * ((3000.0 / 1.0) / 4095) * 2
            )  # SensorADC, 3 V reference, x2 divider
            r["Battery"] = Reading(mv, "mV", battery)
            self._battery_percentage = battery_percentage(mv / 1000)

        r["Batt_Percentage"] = Reading(self._battery_percentage, "%")
        r["Packet_Reception_Rate_Current"] = Reading(0.0, "%")  # set only by a manager's timer
        r["Packet_Reception_Rate_Trial"] = Reading(self._reception_trial, "%")
        r["System_Timestamp"] = Reading(float(pc_time_ms), "ms")
        r["Event_Marker"] = Reading(-1.0, "no_units")

        if self._first_offset is None:
            self._first_offset = float(pc_time_ms) - ms
        plot = ms + self._first_offset
        if self._first_plot is None:
            self._first_plot = plot
        r["System_Timestamp_Plot"] = Reading(plot, "ms")
        r["System_Timestamp_Plot_Zeroed"] = Reading(plot - self._first_plot, "ms")

        # ShimmerDevice.processData, which buildMsg ends with: the enabled algorithms.
        if self.gyro_on_the_fly_enabled and "Gyro_X" in r:
            names = ("Gyro_X", "Gyro_Y", "Gyro_Z")
            self.gyro_on_the_fly.update(
                self._calibration(SENSOR_GYRO, self.gyro_range),
                [r[n].cal for n in names],
                [r[n].uncal for n in names],
            )
        return Sample(r)

    @staticmethod
    def _inertial(r, raw, names, units, calibration: KinematicCalibration) -> None:
        if names[0] not in raw:
            return
        u = [float(raw[n]) for n in names]
        cal = calibration.apply(u)
        for name, uncal, value in zip(names, u, cal):
            r[name] = Reading(value, units, uncal)

    def _unwrap(self, ticks: float) -> float:
        window = _reorder_window_ticks(self.sampling_rate, TICKS_MAX_3_BYTE)
        unwrapped, self._cycle, self._rejected = _unwrap(
            ticks, self._last_unwrapped, self._cycle, TICKS_MAX_3_BYTE, window, self._has_previous
        )
        self._last_unwrapped = unwrapped
        self._has_previous = True
        return unwrapped

    def _trial_packet_loss(self, ms: float) -> None:
        """ShimmerObject.calculateTrialPacketLoss."""
        if not self._start_ms_saved:
            self._start_ms_saved = True
            self._start_ms = ms
        if self._start_ms > 0:
            expected = int((ms - self._start_ms) / ((1 / self.sampling_rate) * 1000))
            rate = math.inf if expected == 0 else (self._received_overall / expected) * 100
            self._reception_trial = max(0.0, min(100.0, rate))


def _firmware_version_code(hardware: int, identifier: int, version: tuple[int, int, int]) -> int:
    """The Shimmer3R branch of ShimmerVerObject's version-code table: every LogAndStream release
    from 0.0.1 is code 8, which reads config and calibration over Bluetooth and supports checksums.
    """
    if hardware == SHIMMER_3R and identifier == LOGANDSTREAM and version >= (0, 0, 1):
        return 8
    return -1
