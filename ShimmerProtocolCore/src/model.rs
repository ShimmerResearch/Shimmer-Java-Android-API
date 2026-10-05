//! What the protocol needs to know about the device: its identity and firmware, where Shimmer3 and
//! Shimmer3R differ, and the packet layout from the inquiry reply. Ported from the Java
//! `LogAndStreamModel` and the driver code it reuses (ShimmerVerObject, ShimmerObject).
//!
//! Calibrated decoding (sensors, calibration) is the next step; until then a sample is the raw
//! packet and its device timestamp.

pub const SHIMMER_3: u8 = 3; // ShimmerVerDetails.HW_ID
pub const SHIMMER_3R: u8 = 10;
pub const LOGANDSTREAM: u16 = 3; // ShimmerVerDetails.FW_ID

const CLOCK_HZ: f64 = 32768.0;
const TCXO_HZ: f64 = 255765.625;
const CONFIG_BYTE_LENGTH: usize = 384;
const CONFIG_BYTE_START_ADDRESS: usize = 0;

// Expansion board SR numbers (ShimmerVerDetails.HW_ID_SR_CODES).
const SR_SHIMMER3: u8 = 31;
const SR_PROTO3_DELUXE: u8 = 38;
const SR_EXG_UNIFIED: u8 = 47;
const SR_GSR_UNIFIED: u8 = 48;
const SR_BR_AMP_UNIFIED: u8 = 49;

/// One data packet: its raw bytes (without header and checksum) and its device timestamp.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RawSample {
    pub timestamp_ticks: u32,
    pub packet: Vec<u8>,
    pub pc_time_ms: u64,
}

#[derive(Debug, Default)]
pub struct DeviceModel {
    pub hardware_version: u8,
    pub firmware_identifier: u16,
    pub firmware_version: (u16, u8, u8),
    pub firmware_version_code: i32,
    pub expansion_board: [u8; 3],
    pub config: Vec<u8>,
    pub pressure_coefficients: Vec<u8>,
    pub calibration_dump: Vec<u8>,
    pub crc_mode: u8,
    pub sampling_rate: f64,
    pub channel_ids: Vec<u8>,
    pub packet_size: usize,
    pub streaming: bool,
}

impl DeviceModel {
    pub fn new() -> Self {
        DeviceModel {
            sampling_rate: f64::NAN,
            firmware_version_code: -1,
            ..Default::default()
        }
    }

    // --- Identity and firmware ---------------------------------------------------------------

    pub fn apply_hardware_version(&mut self, version: u8) {
        self.hardware_version = version;
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

    pub fn apply_expansion_board(&mut self, three: &[u8]) {
        self.expansion_board = [three[0], three[1], three[2]];
    }

    pub fn apply_crc_mode(&mut self, mode: u8) {
        self.crc_mode = mode;
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

    // --- Configuration and calibration (stored for the model to come) -------------------------

    pub fn apply_config_bytes(&mut self, config: &[u8]) {
        self.config = config.to_vec();
    }

    fn uses_tcxo(&self) -> bool {
        let valid = self.config.len() > 218 && self.config[..6] != [0xFF; 6];
        valid && self.config[218] & 0x10 != 0
    }

    /// Kept for when pressure is decoded; only an empty reply is rejected.
    pub fn apply_pressure_coefficients(&mut self, payload: &[u8]) -> Option<String> {
        if payload.is_empty() {
            return Some("zero length, no sensor ID".to_string());
        }
        self.pressure_coefficients = payload.to_vec();
        None
    }

    pub fn apply_calibration_dump(&mut self, dump: &[u8]) {
        self.calibration_dump = dump.to_vec();
    }

    /// INQUIRY_RESPONSE payload: the sampling rate, then the channel list, which sets the packet
    /// layout (ShimmerObject.interpretInqResponse and interpretDataPacketFormat).
    pub fn apply_inquiry(&mut self, inquiry: &[u8]) {
        let clock = if self.uses_tcxo() { TCXO_HZ } else { CLOCK_HZ };
        self.sampling_rate = clock / (u16::from_le_bytes([inquiry[0], inquiry[1]]) as f64);
        let count = inquiry[self.inquiry_channel_count_index()] as usize;
        let start = self.inquiry_settings_length();
        self.channel_ids = inquiry[start..start + count].to_vec();
        self.packet_size = 3 + self
            .channel_ids
            .iter()
            .map(|&id| channel_size(id))
            .sum::<usize>();
    }

    // --- Streaming -----------------------------------------------------------------------------

    pub fn prepare_for_streaming(&mut self) {}

    pub fn streaming_started(&mut self) {
        self.streaming = true;
    }

    pub fn streaming_stopped(&mut self) {
        self.streaming = false;
    }

    pub fn decode(&self, packet: &[u8], pc_time_ms: u64) -> RawSample {
        let timestamp_ticks = packet[0] as u32 | (packet[1] as u32) << 8 | (packet[2] as u32) << 16;
        RawSample {
            timestamp_ticks,
            packet: packet.to_vec(),
            pc_time_ms,
        }
    }
}

/// Bytes per channel in a data packet, by channel ID (ShimmerObject.interpretDataPacketFormat).
fn channel_size(id: u8) -> usize {
    match id {
        0x1A | 0x1B => 3,               // pressure and temperature (u24)
        0x1E | 0x1F | 0x21 | 0x22 => 3, // ExG 24-bit
        0x1D | 0x20 => 1,               // ExG status
        _ => 2,                         // everything else, including unknown IDs (u12)
    }
}
