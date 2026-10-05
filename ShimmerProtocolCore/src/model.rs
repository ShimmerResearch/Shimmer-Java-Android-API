//! The device behind the protocol: identity and firmware rules, where Shimmer3 and Shimmer3R
//! differ, configuration, calibration and packet decoding. The protocol feeds it the parsed
//! handshake replies and asks it to decode data packets; nothing here does I/O.
//!
//! Ported from the Java `LogAndStreamModel` and the driver code it reuses: ShimmerVerObject,
//! ShimmerObject (configuration, inquiry, packet layout, buildMsg), ShimmerDevice (calibration
//! dump), TimestampUnwrap, SystemTimestampPlot, and the sensor classes (LSM6DSV and LIS2MDL on a
//! Shimmer3R; KXRB5-2042 or KXTC9-2050, MPU9150 or MPU9250, LSM303DLHC or LSM303AHTR on a
//! Shimmer3). The arithmetic follows the Java expression for expression, so the doubles come out
//! identical.
//!
//! Decoded: the timestamp, low-noise accel, gyro, mag, battery, and the per-packet channels the
//! Java adds (battery %, reception rates, system timestamps, event marker). Other channels are
//! parsed for their size, so the packet layout stays right, but not decoded.

use crate::calibration::{GyroOnTheFly, KinematicCalibration, INFOMEM, RADIO_DUMP};

pub const SHIMMER_3: u8 = 3; // ShimmerVerDetails.HW_ID
pub const SHIMMER_3R: u8 = 10;
pub const LOGANDSTREAM: u16 = 3; // ShimmerVerDetails.FW_ID

const RTC_HZ: f64 = 32768.0; // the packet timestamp's clock
const CLOCK_HZ: f64 = 32768.0; // the sampling clock, unless the TCXO is in use
const TCXO_HZ: f64 = 255765.625;
const CONFIG_BYTE_LENGTH: usize = 384;
const CONFIG_BYTE_START_ADDRESS: usize = 0;

// Expansion board SR numbers (ShimmerVerDetails.HW_ID_SR_CODES).
const SR_SHIMMER3: u8 = 31;
const SR_PROTO3_MINI: u8 = 36;
const SR_PROTO3_DELUXE: u8 = 38;
const SR_EXG_UNIFIED: u8 = 47;
const SR_GSR_UNIFIED: u8 = 48;
const SR_BR_AMP_UNIFIED: u8 = 49;

// Calibration sensor IDs (Configuration.Shimmer3.SENSOR_ID), as the calibration dump names them.
pub const SENSOR_ANALOG_ACCEL: u16 = 2; // Shimmer3: KXRB5-2042 or KXTC9-2050
pub const SENSOR_MPU9X50_GYRO: u16 = 30;
pub const SENSOR_LSM303_MAG: u16 = 32;
pub const SENSOR_LSM6DSV_ACCEL_LN: u16 = 37; // Shimmer3R
pub const SENSOR_LSM6DSV_GYRO: u16 = 38;
pub const SENSOR_LIS2MDL_MAG: u16 = 42;

/// Which inertial sensors the device carries, from its hardware and expansion board.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum Imu {
    /// Not known yet.
    #[default]
    Unknown,
    /// LSM6DSV (accel, gyro) and LIS2MDL (mag).
    Shimmer3R,
    /// KXTC9-2050, MPU9250 and LSM303AHTR (ShimmerObject.isSupportedNewImuSensors).
    Shimmer3NewImu,
    /// KXRB5-2042, MPU9150 and LSM303DLHC: earlier boards, and the driver's default.
    Shimmer3,
}

/// One channel of one sample: the calibrated value and its units, and the raw value.
#[derive(Debug, Clone, PartialEq)]
pub struct Reading {
    pub name: &'static str,
    pub cal: f64,
    pub units: &'static str,
    pub uncal: Option<f64>,
}

/// One decoded data packet: its device timestamp, the PC time it arrived, its raw bytes (without
/// header and checksum), and its channels.
#[derive(Debug, Clone, PartialEq)]
pub struct Sample {
    pub timestamp_ticks: u32,
    pub pc_time_ms: u64,
    pub packet: Vec<u8>,
    pub readings: Vec<Reading>,
}

impl Sample {
    pub fn reading(&self, name: &str) -> Option<&Reading> {
        self.readings.iter().find(|r| r.name == name)
    }

    /// The calibrated value of a channel, e.g. `sample.get("Accel_LN_X")`.
    pub fn get(&self, name: &str) -> Option<f64> {
        self.reading(name).map(|r| r.cal)
    }
}

// --- Packet layout (ShimmerObject.interpretDataPacketFormat, UtilParseData.parseData) ----------

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Kind {
    U8,
    I16,
    I16R,
    U12,
    U14,
    U16,
    U16R,
    U24,
    U24R,
    I24R,
    /// "i12*>": MSB << 4 | LSB >> 4, as a 12-bit two's complement.
    I12Shifted,
}

impl Kind {
    fn size(self) -> usize {
        match self {
            Kind::U8 => 1,
            Kind::U24 | Kind::U24R | Kind::I24R => 3,
            _ => 2,
        }
    }

    /// Unsigned types are not masked to their width, as in the Java.
    fn parse(self, b: &[u8]) -> i64 {
        let le = |n: usize| (0..n).fold(0i64, |v, i| v | (b[i] as i64) << (8 * i));
        let be = |n: usize| (0..n).fold(0i64, |v, i| v << 8 | b[i] as i64);
        let signed = |v: i64, bits: u32| {
            if v >= 1 << (bits - 1) {
                v - (1 << bits)
            } else {
                v
            }
        };
        match self {
            Kind::U8 => b[0] as i64,
            Kind::U12 | Kind::U14 | Kind::U16 => le(2),
            Kind::U16R => be(2),
            Kind::I16 => signed(le(2), 16),
            Kind::I16R => signed(be(2), 16),
            Kind::U24 => le(3),
            Kind::U24R => be(3),
            Kind::I24R => signed(be(3), 24),
            Kind::I12Shifted => signed((b[0] as i64) << 4 | (b[1] as i64) >> 4, 12),
        }
    }
}

/// A channel's type by its ID, or None for an ID that takes no bytes on this hardware (the Java
/// gives Shimmer3 no branch for the Shimmer3R-only high-g accel and alternative mag).
fn channel_kind(imu: Imu, id: u8) -> Option<Kind> {
    if imu == Imu::Shimmer3R {
        return Some(match id {
            0x00..=0x0C => Kind::I16,        // accel LN, battery, accel WR, mag, gyro
            0x0D..=0x13 => Kind::U14,        // ADCs
            0x14..=0x16 => Kind::I12Shifted, // high-g accel
            0x17..=0x19 => Kind::I16,        // alternative mag
            0x1A | 0x1B => Kind::U24,        // temperature and pressure
            0x1C => Kind::U16,               // GSR
            0x1D | 0x20 => Kind::U8,         // ExG status
            0x1E | 0x1F | 0x21 | 0x22 => Kind::I24R, // ExG 24-bit
            0x23..=0x26 => Kind::I16R,       // ExG 16-bit
            _ => Kind::U12,                  // bridge amp, and any unknown ID
        });
    }
    Some(match id {
        0x00..=0x06 => Kind::I16, // accel LN, battery, accel WR
        0x07..=0x09 if imu == Imu::Shimmer3NewImu => Kind::I16, // LSM303AHTR mag
        0x07..=0x09 => Kind::I16R, // LSM303DLHC mag
        0x0A..=0x0C => Kind::I16R, // gyro
        0x0D..=0x13 => Kind::U12, // ADCs
        0x14..=0x19 => return None,
        0x1A => Kind::U16R, // BMP180/BMP280 temperature
        0x1B => Kind::U24R, // and pressure
        0x1C => Kind::U16,
        0x1D | 0x20 => Kind::U8,
        0x1E | 0x1F | 0x21 | 0x22 => Kind::I24R,
        0x23..=0x26 => Kind::I16R,
        _ => Kind::U12,
    })
}

// Channel IDs that are decoded.
const ACCEL_LN: [u8; 3] = [0x00, 0x01, 0x02];
const BATTERY: u8 = 0x03;
const MAG: [u8; 3] = [0x07, 0x08, 0x09];
const GYRO: [u8; 3] = [0x0A, 0x0B, 0x0C];

// --- Calibration defaults (the sensor classes) -------------------------------------------------

const LSM6DSV_ALIGNMENT: [[i8; 3]; 3] = [[-1, 0, 0], [0, 1, 0], [0, 0, -1]];
const LIS2MDL_ALIGNMENT: [[i8; 3]; 3] = [[-1, 0, 0], [0, -1, 0], [0, 0, -1]];
const KIONIX_ALIGNMENT: [[i8; 3]; 3] = [[0, -1, 0], [-1, 0, 0], [0, 0, -1]];
const MPU9X50_ALIGNMENT: [[i8; 3]; 3] = [[0, -1, 0], [-1, 0, 0], [0, 0, -1]];
const LSM303AH_ALIGNMENT: [[i8; 3]; 3] = [[0, -1, 0], [1, 0, 0], [0, 0, -1]];
const LSM303DLHC_ALIGNMENT: [[i8; 3]; 3] = [[-1, 0, 0], [0, 1, 0], [0, 0, -1]];

/// Every (sensor ID, range) the device has a calibration for, at its defaults.
fn default_calibrations(imu: Imu) -> Vec<((u16, u8), KinematicCalibration)> {
    let same = |s: f64| [s, s, s];
    let zero = [0.0; 3];
    let mut c = Vec::new();
    match imu {
        Imu::Unknown => {}
        Imu::Shimmer3R => {
            for (r, s) in [1672.0, 836.0, 418.0, 209.0].into_iter().enumerate() {
                let k = KinematicCalibration::new(zero, same(s), LSM6DSV_ALIGNMENT, 1.0);
                c.push(((SENSOR_LSM6DSV_ACCEL_LN, r as u8), k)); // ±2/4/8/16 g
            }
            for (r, s) in [229.0, 114.0, 57.0, 29.0, 14.0, 7.0]
                .into_iter()
                .enumerate()
            {
                let k = KinematicCalibration::new(zero, same(s), LSM6DSV_ALIGNMENT, 100.0);
                c.push(((SENSOR_LSM6DSV_GYRO, r as u8), k)); // 125..4000 dps
            }
            let k = KinematicCalibration::new(zero, same(667.0), LIS2MDL_ALIGNMENT, 1.0);
            c.push(((SENSOR_LIS2MDL_MAG, 0), k));
        }
        Imu::Shimmer3NewImu | Imu::Shimmer3 => {
            let new = imu == Imu::Shimmer3NewImu;
            // KXTC9-2050: 1.65 V zero-g offset, 660 mV/g; KXRB5-2042: 1.5 V, 600 mV/g. ±2 g.
            let (offset, s) = if new { (2253.0, 92.0) } else { (2047.0, 83.0) };
            let k = KinematicCalibration::new(same(offset), same(s), KIONIX_ALIGNMENT, 1.0);
            c.push(((SENSOR_ANALOG_ACCEL, 0), k));
            for (r, s) in [131.0, 65.5, 32.8, 16.4].into_iter().enumerate() {
                let k = KinematicCalibration::new(zero, same(s), MPU9X50_ALIGNMENT, 100.0);
                c.push(((SENSOR_MPU9X50_GYRO, r as u8), k)); // 250..2000 dps
            }
            if new {
                let k = KinematicCalibration::new(zero, same(667.0), LSM303AH_ALIGNMENT, 1.0);
                c.push(((SENSOR_LSM303_MAG, 0), k));
            } else {
                let dlhc = [
                    [1100.0, 1100.0, 980.0], // ±1.3 Ga (range 1)
                    [855.0, 855.0, 760.0],
                    [670.0, 670.0, 600.0],
                    [450.0, 450.0, 400.0],
                    [400.0, 400.0, 355.0],
                    [330.0, 330.0, 295.0],
                    [230.0, 230.0, 205.0], // ±8.1 Ga (range 7)
                ];
                for (r, s) in dlhc.into_iter().enumerate() {
                    let k = KinematicCalibration::new(zero, s, LSM303DLHC_ALIGNMENT, 1.0);
                    c.push(((SENSOR_LSM303_MAG, r as u8 + 1), k));
                }
            }
        }
    }
    c
}

// --- Timestamps (TimestampUnwrap) ---------------------------------------------------------------

const TICKS_MAX_3_BYTE: f64 = (1 << 24) as f64;
const WRAP_WINDOW_TICKS: f64 = 32768.0;
const REORDER_PERIODS: f64 = 8.0;
const MAX_WINDOW_DIVISOR: f64 = 8.0;

fn reorder_window_ticks(sampling_rate: f64, max_ticks: f64) -> f64 {
    if sampling_rate.is_nan() || sampling_rate.is_infinite() || sampling_rate <= 0.0 {
        return 0.0;
    }
    let window = REORDER_PERIODS * RTC_HZ / sampling_rate;
    window.min(max_ticks / MAX_WINDOW_DIVISOR)
}

/// TimestampUnwrap.unwrap: (unwrapped, cycle, rejected).
fn unwrap(
    raw: f64,
    last: f64,
    cycle: f64,
    max_ticks: f64,
    window: f64,
    has_previous: bool,
) -> (f64, f64, bool) {
    if !has_previous {
        return (raw, 0.0, false);
    }
    let last_raw = last - (max_ticks * cycle);
    let mut forward = raw - last_raw;
    if forward < 0.0 {
        forward += max_ticks;
    }
    let backwards = max_ticks - forward;
    let candidate = if forward == 0.0 {
        last // a duplicate
    } else if backwards <= window {
        last - backwards // reordered
    } else if max_ticks == TICKS_MAX_3_BYTE
        && raw == 0.0
        && last_raw < (max_ticks - WRAP_WINDOW_TICKS)
    {
        return (last, cycle, true); // a record the firmware never stamped
    } else {
        last + forward
    };
    (candidate, (candidate / max_ticks).floor(), false)
}

/// Math.pow(x, n) for n = 2, 3, 4, rounded once, as Math.pow is. The fit below cancels terms of
/// some 10^5 down to a percentage, so a power rounded twice, as `powf` or repeated multiplication
/// may give, moves the result by about 1e-10. The square is kept exactly as a double-double
/// (hi + lo) and multiplied out with fused multiply-adds.
fn pow(x: f64, n: u32) -> f64 {
    let hi = x * x;
    let lo = x.mul_add(x, -hi);
    match n {
        2 => hi,
        3 => {
            let p = hi * x;
            p + (hi.mul_add(x, -p) + lo * x)
        }
        4 => {
            let p = hi * hi;
            p + (hi.mul_add(hi, -p) + 2.0 * hi * lo)
        }
        _ => unreachable!("only the battery fit's powers"),
    }
}

/// ShimmerBattStatusDetails.calculateBattPercentage: a 4th-order fit, clamped to 0..100.
fn battery_percentage(mut volts: f64) -> f64 {
    if volts > (4.167 + 0.2) {
        volts = 4.167;
    } else if volts < (3.2 - 0.2) {
        volts = 3.2;
    }
    let pct = (1109.739792 * pow(volts, 4)) - (17167.12674 * pow(volts, 3))
        + (99232.71686 * pow(volts, 2))
        - (253825.397 * volts)
        + 242266.0527;
    // As Java's ifs: NaN passes through.
    pct.clamp(0.0, 100.0)
}

#[derive(Debug, Default)]
struct StreamState {
    last_unwrapped: f64,
    cycle: f64,
    has_previous: bool,
    rejected: bool,
    start_ms_saved: bool,
    start_ms: f64,
    received_overall: u64,
    reception_trial: f64,
    first_offset: Option<f64>, // SystemTimestampPlot
    first_plot: Option<f64>,
}

#[derive(Debug, Default)]
pub struct DeviceModel {
    pub hardware_version: u8,
    pub firmware_identifier: u16,
    pub firmware_version: (u16, u8, u8),
    pub firmware_version_code: i32,
    pub expansion_board: [u8; 3],
    pub imu: Imu,
    pub config: Vec<u8>,
    pub pressure_coefficients: Vec<u8>,
    pub crc_mode: u8,
    pub sampling_rate: f64,
    pub channel_ids: Vec<u8>,
    pub packet_size: usize,
    pub streaming: bool,

    pub accel_range: u8,
    pub gyro_range: u8,
    calibrations: Vec<((u16, u8), KinematicCalibration)>,
    gyro_on_the_fly: GyroOnTheFly,
    /// (channel ID, type, offset) of each channel after the 3-byte timestamp.
    layout: Vec<(u8, Kind, usize)>,
    battery_percentage: f64,
    stream: StreamState,
}

impl DeviceModel {
    pub fn new() -> Self {
        DeviceModel {
            sampling_rate: f64::NAN,
            firmware_version_code: -1,
            // The default configuration's; InfoMem and the inquiry set the real one.
            gyro_range: 1,
            battery_percentage: -1.0,
            stream: StreamState {
                start_ms: -1.0,
                ..Default::default()
            },
            ..Default::default()
        }
    }

    // --- Identity and firmware ---------------------------------------------------------------

    pub fn apply_hardware_version(&mut self, version: u8) {
        self.hardware_version = version;
        self.update_imu();
    }

    /// FW_VERSION_RESPONSE: identifier (2 bytes LE), major (2 bytes LE), minor, internal.
    pub fn apply_firmware_version(&mut self, six: &[u8]) {
        self.firmware_identifier = u16::from_le_bytes([six[0], six[1]]);
        self.firmware_version = (u16::from_le_bytes([six[2], six[3]]), six[4], six[5]);
        self.firmware_version_code = self.version_code();
    }

    /// ShimmerVerObject's version-code table, for LogAndStream on Shimmer3 and Shimmer3R.
    fn version_code(&self) -> i32 {
        if self.firmware_identifier != LOGANDSTREAM {
            return -1;
        }
        let v = self.firmware_version;
        match self.hardware_version {
            SHIMMER_3R if v >= (0, 0, 1) => 8,
            SHIMMER_3 if v >= (0, 16, 6) => 9,
            SHIMMER_3 if v >= (0, 13, 7) => 8,
            SHIMMER_3 if v >= (0, 6, 5) => 7,
            _ => -1,
        }
    }

    fn firmware_at_least(&self, version: (u16, u8, u8)) -> bool {
        self.firmware_identifier == LOGANDSTREAM && self.firmware_version >= version
    }

    pub fn is_supported_hardware(&self) -> bool {
        self.hardware_version == SHIMMER_3 || self.hardware_version == SHIMMER_3R
    }

    pub fn device_name(&self) -> &'static str {
        if self.hardware_version == SHIMMER_3R {
            "Shimmer3R"
        } else {
            "Shimmer3"
        }
    }

    pub fn firmware_version_text(&self) -> String {
        let (major, minor, internal) = self.firmware_version;
        let label = if self.firmware_identifier == LOGANDSTREAM {
            "LogAndStream".to_string()
        } else {
            format!("firmware ID {}", self.firmware_identifier)
        };
        format!("{} v{}.{}.{}", label, major, minor, internal)
    }

    /// None if supported, otherwise why not. Shimmer3 needs LogAndStream v1.1.3 or later; every
    /// Shimmer3R LogAndStream release is supported.
    pub fn unsupported_firmware(&self) -> Option<String> {
        if self.firmware_identifier != LOGANDSTREAM {
            return Some(format!(
                "firmware {} is not LogAndStream",
                self.firmware_version_text()
            ));
        }
        if self.hardware_version == SHIMMER_3 && !self.firmware_at_least((1, 1, 3)) {
            return Some(format!(
                "Shimmer3 firmware {} is older than LogAndStream v1.1.3; update the firmware",
                self.firmware_version_text()
            ));
        }
        None
    }

    /// ShimmerBluetooth.isBtCrcModeSupported.
    pub fn is_bt_crc_mode_supported(&self) -> bool {
        self.firmware_version_code >= 8
    }

    /// DAUGHTER_CARD_ID_RESPONSE: the expansion board, which decides a Shimmer3's sensors.
    pub fn apply_expansion_board(&mut self, three: &[u8]) {
        self.expansion_board = [three[0], three[1], three[2]];
        self.update_imu();
    }

    pub fn apply_crc_mode(&mut self, mode: u8) {
        self.crc_mode = mode;
    }

    /// As the Java recreates its sensor classes when the hardware or expansion board changes:
    /// the sensors, at their default calibrations.
    fn update_imu(&mut self) {
        let imu = match self.hardware_version {
            SHIMMER_3R => Imu::Shimmer3R,
            SHIMMER_3 if self.is_new_imu_per_sr_number() => Imu::Shimmer3NewImu,
            SHIMMER_3 => Imu::Shimmer3,
            _ => Imu::Unknown,
        };
        self.imu = imu;
        self.calibrations = default_calibrations(imu);
    }

    /// ShimmerObject.isSupportedNewImuSensors, for a Shimmer3.
    fn is_new_imu_per_sr_number(&self) -> bool {
        let [id, rev, special] = self.expansion_board;
        (id == SR_EXG_UNIFIED && rev >= 3)
            || (id == SR_GSR_UNIFIED && rev >= 3)
            || (id == SR_BR_AMP_UNIFIED && rev >= 3)
            || (id == SR_SHIMMER3 && rev >= 6)
            || special == 171 // any expansion board on a new-IMU base board
            || (id == SR_PROTO3_DELUXE && rev >= 3)
            || (id == SR_PROTO3_MINI && rev >= 3)
    }

    // --- Where the handshake differs ---------------------------------------------------------

    pub fn config_byte_length(&self) -> usize {
        CONFIG_BYTE_LENGTH
    }

    pub fn config_byte_start_address(&self) -> usize {
        CONFIG_BYTE_START_ADDRESS
    }

    /// INQUIRY_RESPONSE: the settings bytes before the channel list.
    pub fn inquiry_settings_length(&self) -> usize {
        if self.hardware_version == SHIMMER_3R {
            11
        } else {
            8
        }
    }

    /// INQUIRY_RESPONSE: which settings byte holds the channel count.
    pub fn inquiry_channel_count_index(&self) -> usize {
        if self.hardware_version == SHIMMER_3R {
            9
        } else {
            6
        }
    }

    /// As ShimmerBluetooth.readPressureCalibrationCoefficients decides: every supported Shimmer3
    /// (code 9); a Shimmer3R except on v1.01.006 with a BMP581 by SR number, which NACKs it.
    pub fn reads_pressure_coefficients(&self) -> bool {
        if self.hardware_version == SHIMMER_3R {
            return !self.is_bmp581_per_sr_number() || self.firmware_at_least((1, 1, 7));
        }
        self.firmware_version_code >= 9
    }

    /// ShimmerObject.isSupportedBmp581(svo, ebd): the SR-number rule and its firmware guard.
    fn is_bmp581_per_sr_number(&self) -> bool {
        if self.hardware_version != SHIMMER_3R {
            return false;
        }
        let gte = |sr: u8, rev: u8, special: u8| {
            let [id, r, s] = self.expansion_board;
            id == sr && if r != rev { r > rev } else { s >= special }
        };
        let board = gte(SR_SHIMMER3, 11, 2)
            || gte(SR_PROTO3_DELUXE, 4, 2)
            || gte(SR_EXG_UNIFIED, 8, 2)
            || (gte(SR_GSR_UNIFIED, 7, 2) && !gte(SR_GSR_UNIFIED, 8, 0))
            || gte(SR_GSR_UNIFIED, 8, 2)
            || gte(SR_BR_AMP_UNIFIED, 4, 2);
        board && self.firmware_at_least((1, 1, 6))
    }

    // --- Configuration and calibration -------------------------------------------------------

    /// ConfigByteLayout: config bytes starting with six 0xFF were never written.
    fn config_valid(&self) -> bool {
        self.config.len() >= CONFIG_BYTE_LENGTH && self.config[..6] != [0xFF; 6]
    }

    /// Derived sensor bit 29 (GYRO_ON_THE_FLY_CAL): derived-sensors byte 3 (byte 118), bit 5. The
    /// derived bytes count only if the first two are not 0xFF (parseEnabledDerivedSensorsForMaps).
    pub fn gyro_on_the_fly_enabled(&self) -> bool {
        self.config_valid()
            && self.config[31] != 0xFF
            && self.config[32] != 0xFF
            && self.config[118] & 0x20 != 0
    }

    /// Experiment config byte 1 (byte 218), bit 4.
    fn uses_tcxo(&self) -> bool {
        self.config_valid() && self.config[218] & 0x10 != 0
    }

    /// The parts of ShimmerObject.configBytesParse and the sensor classes' parsers that the decode
    /// depends on: the ranges, then each sensor's calibration at its current range.
    pub fn apply_config_bytes(&mut self, config: &[u8]) {
        self.config = config.to_vec();
        if !self.config_valid() {
            return; // the Java falls back to its default configuration
        }
        let (accel, gyro, mag) = match self.imu {
            Imu::Unknown => return,
            Imu::Shimmer3R => {
                self.accel_range = (config[9] >> 6) & 0x03;
                self.gyro_range = ((config[130] >> 2) & 0x01) << 2 | (config[8] & 0x03);
                (
                    SENSOR_LSM6DSV_ACCEL_LN,
                    SENSOR_LSM6DSV_GYRO,
                    SENSOR_LIS2MDL_MAG,
                )
            }
            Imu::Shimmer3NewImu | Imu::Shimmer3 => {
                self.gyro_range = config[8] & 0x03;
                (SENSOR_ANALOG_ACCEL, SENSOR_MPU9X50_GYRO, SENSOR_LSM303_MAG)
            }
        };
        let (accel_range, gyro_range, mag_range) = (
            self.accel_range_in_use(),
            self.gyro_range,
            self.mag_range_in_use(),
        );
        for (sensor_id, range, record) in [
            (accel, accel_range, 34..55),
            (gyro, gyro_range, 55..76),
            (mag, mag_range, 76..97),
        ] {
            if let Some(c) = self.calibration_mut(sensor_id, range) {
                c.parse(&config[record], INFOMEM);
            }
        }
    }

    /// A Shimmer3's analog accel has one range.
    fn accel_range_in_use(&self) -> u8 {
        if self.imu == Imu::Shimmer3R {
            self.accel_range
        } else {
            0
        }
    }

    /// The LSM303AHTR and LIS2MDL have one range. For the LSM303DLHC the driver always uses its
    /// first range's calibration (1, ±1.3 Ga), whatever range is configured:
    /// SensorLSM303DLHC.setLSM303MagRange updates the wide-range accel's calibration in use, not
    /// the mag's. Kept, so that the values match the driver's.
    fn mag_range_in_use(&self) -> u8 {
        if self.imu == Imu::Shimmer3 {
            1
        } else {
            0
        }
    }

    fn calibration_mut(&mut self, sensor_id: u16, range: u8) -> Option<&mut KinematicCalibration> {
        self.calibrations
            .iter_mut()
            .find(|(key, _)| *key == (sensor_id, range))
            .map(|(_, c)| c)
    }

    /// The calibration of a (sensor ID, range), for inspection.
    pub fn calibration(&self, sensor_id: u16, range: u8) -> Option<&KinematicCalibration> {
        self.calibrations
            .iter()
            .find(|(key, _)| *key == (sensor_id, range))
            .map(|(_, c)| c)
    }

    /// Kept for when pressure is decoded; only an empty reply is rejected.
    pub fn apply_pressure_coefficients(&mut self, payload: &[u8]) -> Option<String> {
        if payload.is_empty() {
            return Some("zero length, no sensor ID".to_string());
        }
        self.pressure_coefficients = payload.to_vec();
        None
    }

    /// ShimmerDevice.calibByteDumpParse: after the length (2 bytes) and version (8 bytes),
    /// records of [sensor ID u16 LE, range, length, 8-byte time, calibration bytes]. A later
    /// record for the same sensor and range replaces an earlier one; a truncated one ends it.
    pub fn apply_calibration_dump(&mut self, dump: &[u8]) {
        if dump.iter().all(|&b| b == 0) || dump.len() <= 2 {
            return;
        }
        let mut i = 10;
        while dump.len().saturating_sub(i) > 12 {
            // As the Java computes it, sign extension and all.
            let sensor_id =
                (((dump[i + 1] as i8 as i32) << 8 | dump[i] as i8 as i32) & 0xFFFF) as u16;
            let range = dump[i + 2];
            let end = i + 12 + dump[i + 3] as usize;
            if end > dump.len() {
                break;
            }
            let record = dump[i + 12..end].to_vec();
            if let Some(c) = self.calibration_mut(sensor_id, range) {
                c.parse(&record, RADIO_DUMP);
            }
            i = end;
        }
    }

    /// INQUIRY_RESPONSE payload: the sampling rate and ranges, then the channel list, which sets
    /// the packet layout (ShimmerObject.interpretInqResponse and interpretDataPacketFormat).
    pub fn apply_inquiry(&mut self, inquiry: &[u8]) {
        let clock = if self.uses_tcxo() { TCXO_HZ } else { CLOCK_HZ };
        self.sampling_rate = clock / (inquiry[0] as u32 + ((inquiry[1] as u32) << 8)) as f64;
        let settings = u64::from_le_bytes({
            let mut b = [0u8; 8];
            let n = if self.hardware_version == SHIMMER_3R {
                7
            } else {
                4
            };
            b[..n].copy_from_slice(&inquiry[2..2 + n]);
            b
        });
        self.gyro_range = ((settings >> 16) & 0x03) as u8;
        if self.hardware_version == SHIMMER_3R {
            self.gyro_range += (((settings >> 34) & 0x01) as u8) << 2;
        }
        let count = inquiry[self.inquiry_channel_count_index()] as usize;
        let start = self.inquiry_settings_length();
        self.channel_ids = inquiry[start..start + count].to_vec();
        self.layout.clear();
        let mut offset = 3;
        for &id in &self.channel_ids {
            if let Some(kind) = channel_kind(self.imu, id) {
                self.layout.push((id, kind, offset));
                offset += kind.size();
            }
        }
        self.packet_size = offset;
    }

    // --- Streaming -----------------------------------------------------------------------------

    /// What ShimmerBluetooth.startStreaming resets: packet counters and the timestamp; and what
    /// initializeAlgorithms sets: the gyro on-the-fly window.
    pub fn prepare_for_streaming(&mut self) {
        self.stream = StreamState {
            start_ms: -1.0,
            ..Default::default()
        };
        if self.gyro_on_the_fly_enabled() {
            self.gyro_on_the_fly.set_window(self.sampling_rate);
        }
    }

    pub fn streaming_started(&mut self) {
        self.streaming = true;
    }

    pub fn streaming_stopped(&mut self) {
        self.streaming = false;
    }

    fn raw(&self, packet: &[u8], id: u8) -> Option<f64> {
        let &(_, kind, offset) = self.layout.iter().find(|(i, _, _)| *i == id)?;
        Some(kind.parse(&packet[offset..offset + kind.size()]) as f64)
    }

    fn raw3(&self, packet: &[u8], ids: [u8; 3]) -> Option<[f64; 3]> {
        Some([
            self.raw(packet, ids[0])?,
            self.raw(packet, ids[1])?,
            self.raw(packet, ids[2])?,
        ])
    }

    /// ShimmerObject.buildMsg (Bluetooth), then SystemTimestampPlot.
    pub fn decode(&mut self, packet: &[u8], pc_time_ms: u64) -> Sample {
        let timestamp_ticks = packet[0] as u32 | (packet[1] as u32) << 8 | (packet[2] as u32) << 16;
        let mut r = Vec::new();
        let mut push = |name, cal, units, uncal| {
            r.push(Reading {
                name,
                cal,
                units,
                uncal,
            })
        };

        let ticks = timestamp_ticks as f64;
        push("Clock 3_LSB", ticks, "Ticks", Some(ticks));
        let unwrapped = self.unwrap(ticks);
        let ms = unwrapped / RTC_HZ * 1000.0;
        self.stream.received_overall += 1;
        if !self.stream.rejected {
            self.trial_packet_loss(ms);
        }
        push("Timestamp", ms, "ms", Some(ticks));

        let (accel, gyro, mag) = match self.imu {
            Imu::Shimmer3R => (
                SENSOR_LSM6DSV_ACCEL_LN,
                SENSOR_LSM6DSV_GYRO,
                SENSOR_LIS2MDL_MAG,
            ),
            _ => (SENSOR_ANALOG_ACCEL, SENSOR_MPU9X50_GYRO, SENSOR_LSM303_MAG),
        };
        let inertial = [
            (
                ACCEL_LN,
                ["Accel_LN_X", "Accel_LN_Y", "Accel_LN_Z"],
                "m/(s^2)",
                accel,
                self.accel_range_in_use(),
            ),
            (
                GYRO,
                ["Gyro_X", "Gyro_Y", "Gyro_Z"],
                "deg/s",
                gyro,
                self.gyro_range,
            ),
            (
                MAG,
                ["Mag_X", "Mag_Y", "Mag_Z"],
                "local_flux",
                mag,
                self.mag_range_in_use(),
            ),
        ];
        let mut gyro_values = None;
        for (ids, names, units, sensor_id, range) in inertial {
            let (Some(u), Some(calibration)) =
                (self.raw3(packet, ids), self.calibration(sensor_id, range))
            else {
                continue;
            };
            let cal = calibration.apply(u);
            for axis in 0..3 {
                push(names[axis], cal[axis], units, Some(u[axis]));
            }
            if ids == GYRO {
                gyro_values = Some((cal, u));
            }
        }
        if let Some(battery) = self.raw(packet, BATTERY) {
            // SensorADC.calibrateU12AdcValueToMillivolts: 3 V reference, then the x2 divider.
            let mv = (battery - 0.0) * ((3000.0 / 1.0) / 4095.0) * 2.0;
            push("Battery", mv, "mV", Some(battery));
            self.battery_percentage = battery_percentage(mv / 1000.0);
        }

        let s = &mut self.stream;
        push("Batt_Percentage", self.battery_percentage, "%", None);
        push("Packet_Reception_Rate_Current", 0.0, "%", None); // set only by a manager's timer
        push("Packet_Reception_Rate_Trial", s.reception_trial, "%", None);
        push("System_Timestamp", pc_time_ms as f64, "ms", None);
        push("Event_Marker", -1.0, "no_units", None);
        let offset = *s.first_offset.get_or_insert(pc_time_ms as f64 - ms);
        let plot = ms + offset;
        let first_plot = *s.first_plot.get_or_insert(plot);
        push("System_Timestamp_Plot", plot, "ms", None);
        push(
            "System_Timestamp_Plot_Zeroed",
            plot - first_plot,
            "ms",
            None,
        );

        // ShimmerDevice.processData, which buildMsg ends with: the enabled algorithms.
        if let (true, Some((cal, uncal))) = (self.gyro_on_the_fly_enabled(), gyro_values) {
            let range = self.gyro_range;
            let mut otf = std::mem::take(&mut self.gyro_on_the_fly);
            if let Some(calibration) = self.calibration_mut(gyro, range) {
                otf.update(calibration, cal, uncal);
            }
            self.gyro_on_the_fly = otf;
        }
        Sample {
            timestamp_ticks,
            pc_time_ms,
            packet: packet.to_vec(),
            readings: r,
        }
    }

    fn unwrap(&mut self, ticks: f64) -> f64 {
        let window = reorder_window_ticks(self.sampling_rate, TICKS_MAX_3_BYTE);
        let s = &mut self.stream;
        let (unwrapped, cycle, rejected) = unwrap(
            ticks,
            s.last_unwrapped,
            s.cycle,
            TICKS_MAX_3_BYTE,
            window,
            s.has_previous,
        );
        s.last_unwrapped = unwrapped;
        s.cycle = cycle;
        s.rejected = rejected;
        s.has_previous = true;
        unwrapped
    }

    /// ShimmerObject.calculateTrialPacketLoss.
    fn trial_packet_loss(&mut self, ms: f64) {
        let s = &mut self.stream;
        if !s.start_ms_saved {
            s.start_ms_saved = true;
            s.start_ms = ms;
        }
        if s.start_ms > 0.0 {
            let expected = ((ms - s.start_ms) / ((1.0 / self.sampling_rate) * 1000.0)) as i64;
            let rate = if expected == 0 {
                f64::INFINITY
            } else {
                (s.received_overall as f64 / expected as f64) * 100.0
            };
            // UtilShimmer.nudgeDouble: Math.max and Math.min, which, as clamp, pass NaN through.
            s.reception_trial = rate.clamp(0.0, 100.0);
        }
    }
}
