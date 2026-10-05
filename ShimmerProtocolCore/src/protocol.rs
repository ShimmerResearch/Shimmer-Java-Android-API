//! The LogAndStream firmware's Bluetooth protocol as an I/O-free state machine, for Shimmer3
//! (LogAndStream v1.1.3 onwards) and Shimmer3R. A port of the Java `LogAndStreamProtocol`
//! (DEV-1134); keep them in step.
//!
//! The host owns the transport and the clock. It calls `connect`, passes every received chunk to
//! `receive`, and calls `tick` when `next_deadline` is due. Each call returns an `Output`: the
//! bytes to write (one entry per write) and the events to deliver. Nothing here blocks, sleeps,
//! starts a thread or calls back into the host.
//!
//! Times are wall-clock milliseconds: SET_RWC sends the clock to the device. The handshake starts
//! only once the link has been quiet for `SETTLE_MS`, so that a device an earlier session left
//! streaming can be stopped, and its checksums turned off, first. One object serves one link:
//! after `link_lost` it stays DISCONNECTED.

use std::collections::VecDeque;

use crate::crc::crc;
use crate::framing::{Frame, StreamFramer};
use crate::model::{DeviceModel, Sample};

pub const ACK: u8 = 0xFF;
pub const INQUIRY_COMMAND: u8 = 0x01;
pub const INQUIRY_RESPONSE: u8 = 0x02;
pub const START_STREAMING_COMMAND: u8 = 0x07;
pub const STOP_STREAMING_COMMAND: u8 = 0x20;
pub const GET_SHIMMER_VERSION_RESPONSE: u8 = 0x25;
pub const GET_FW_VERSION_COMMAND: u8 = 0x2E;
pub const FW_VERSION_RESPONSE: u8 = 0x2F;
pub const GET_SHIMMER_VERSION_COMMAND_NEW: u8 = 0x3F;
pub const DAUGHTER_CARD_ID_RESPONSE: u8 = 0x65;
pub const GET_DAUGHTER_CARD_ID_COMMAND: u8 = 0x66;
pub const SET_CRC_COMMAND: u8 = 0x8B;
pub const INFOMEM_RESPONSE: u8 = 0x8D;
pub const GET_INFOMEM_COMMAND: u8 = 0x8E;
pub const SET_RWC_COMMAND: u8 = 0x8F;
pub const RSP_CALIB_DUMP_COMMAND: u8 = 0x99;
pub const GET_CALIB_DUMP_COMMAND: u8 = 0x9A;
pub const PRESSURE_CALIBRATION_COEFFICIENTS_RESPONSE: u8 = 0xA6;
pub const GET_PRESSURE_CALIBRATION_COEFFICIENTS_COMMAND: u8 = 0xA7;

const CRC_OFF: u8 = 0;
const CRC_ONE_BYTE: u8 = 1;

pub const DEFAULT_TIMEOUT_MS: u64 = 2000;
pub const LONG_TIMEOUT_MS: u64 = 5000;
pub const SETTLE_MS: u64 = 300;
pub const SETTLE_GIVE_UP_MS: u64 = 3000;
const MEM_CHUNK: usize = 128;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum State {
    Disconnected,
    Connecting,
    Ready,
    Starting,
    Streaming,
    Stopping,
    Failed,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Event {
    StateChanged(State),
    /// The handshake finished: the device is configured and ready to stream.
    Initialised(String),
    Sample(Sample),
    /// The protocol failed, for instance a command timed out.
    Error(String),
    /// Bytes were dropped, or something was ignored; for information.
    Discarded(String),
    /// The host reported that the link to the device was lost.
    LinkLost(String),
}

/// What one call produced: the bytes to write, one entry per write, and the events.
#[derive(Debug, Default)]
pub struct Output {
    pub writes: Vec<Vec<u8>>,
    pub events: Vec<Event>,
}

impl Output {
    pub fn is_empty(&self) -> bool {
        self.writes.is_empty() && self.events.is_empty()
    }
}

/// How a command's reply is read and applied.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Reply {
    /// Only acknowledged.
    Ack,
    HardwareVersion,
    FirmwareVersion,
    ExpansionBoard,
    ConfigBytes,
    PressureCoefficients,
    CalibDump,
    Inquiry,
}

#[derive(Debug, Clone)]
struct Command {
    name: String,
    data: Vec<u8>,
    response_code: Option<u8>,
    reply: Reply,
    timeout_ms: u64,
    /// If it times out, carry on rather than fail.
    optional: bool,
}

impl Command {
    fn ack(name: &str, data: Vec<u8>) -> Self {
        Command {
            name: name.into(),
            data,
            response_code: None,
            reply: Reply::Ack,
            timeout_ms: DEFAULT_TIMEOUT_MS,
            optional: false,
        }
    }

    fn reply(name: &str, data: Vec<u8>, response_code: u8, reply: Reply) -> Self {
        Command {
            name: name.into(),
            data,
            response_code: Some(response_code),
            reply,
            timeout_ms: DEFAULT_TIMEOUT_MS,
            optional: false,
        }
    }

    /// [command, length, address LSB, address MSB]
    fn mem_read(
        name: &str,
        command: u8,
        address: usize,
        size: usize,
        response_code: u8,
        reply: Reply,
    ) -> Self {
        let data = vec![
            command,
            size as u8,
            (address & 0xFF) as u8,
            ((address >> 8) & 0xFF) as u8,
        ];
        Command {
            name: format!("{}@{}", name, address),
            data,
            response_code: Some(response_code),
            reply,
            timeout_ms: LONG_TIMEOUT_MS,
            optional: false,
        }
    }
}

/// The device clock for a wall-clock time: 32768 Hz ticks, 8 bytes, LSB first.
pub fn rtc_bytes(now_ms: u64) -> [u8; 8] {
    // As UtilShimmer: (long) (milliseconds * 32.768), so truncated.
    ((now_ms as f64 * 32.768) as i64).to_le_bytes()
}

pub struct LogAndStreamProtocol {
    model: DeviceModel,
    queue: VecDeque<Command>,
    rx: Vec<u8>,
    framer: Option<StreamFramer>,
    state: State,
    in_flight: Option<Command>,
    ack_seen: bool,
    deadline: Option<u64>,
    crc_bytes: usize,
    dropped: usize,
    link_lost: bool,

    settling: bool,
    quiet_at: u64,
    settle_give_up_at: u64,
    stop_sent: bool,
    settle_bytes: usize,

    config: Vec<u8>,
    config_length: usize,
    calib_dump: Vec<u8>,
    calib_dump_length: Option<usize>,
}

impl Default for LogAndStreamProtocol {
    fn default() -> Self {
        Self::new()
    }
}

impl LogAndStreamProtocol {
    pub fn new() -> Self {
        LogAndStreamProtocol {
            model: DeviceModel::new(),
            queue: VecDeque::new(),
            rx: Vec::new(),
            framer: None,
            state: State::Disconnected,
            in_flight: None,
            ack_seen: false,
            deadline: None,
            crc_bytes: 0,
            dropped: 0,
            link_lost: false,
            settling: false,
            quiet_at: 0,
            settle_give_up_at: 0,
            stop_sent: false,
            settle_bytes: 0,
            config: Vec::new(),
            config_length: 0,
            calib_dump: Vec::new(),
            calib_dump_length: None,
        }
    }

    pub fn state(&self) -> State {
        self.state
    }

    pub fn sampling_rate(&self) -> f64 {
        self.model.sampling_rate
    }

    /// The device as configured by the handshake; complete once the state is READY.
    pub fn model(&self) -> &DeviceModel {
        &self.model
    }

    /// When the host should next call `tick`, or None if nothing is pending.
    pub fn next_deadline(&self) -> Option<u64> {
        if self.settling {
            return Some(self.quiet_at.min(self.settle_give_up_at));
        }
        self.in_flight.as_ref().and(self.deadline)
    }

    // --- Calls from the host -------------------------------------------------------------------

    /// Call once the link is open. The handshake itself starts from `tick` once the link has been
    /// quiet for `SETTLE_MS`, so this returns no writes.
    pub fn connect(&mut self, now_ms: u64) -> Output {
        let mut out = Output::default();
        if self.link_lost {
            out.events.push(Event::Error(
                "this link was lost; use a new LogAndStreamProtocol for a new connection".into(),
            ));
            return out;
        }
        if self.state != State::Disconnected {
            return self.failed(out, format!("connect() called in state {:?}", self.state));
        }
        self.set_state(State::Connecting, &mut out);
        self.settling = true;
        self.quiet_at = now_ms + SETTLE_MS;
        self.settle_give_up_at = now_ms + SETTLE_GIVE_UP_MS;
        out
    }

    /// The host lost the link. DISCONNECTED for good: later bytes, ticks and stream requests are
    /// ignored, and `connect` reports an error.
    pub fn link_lost(&mut self, reason: &str, _now_ms: u64) -> Output {
        let mut out = Output::default();
        if self.link_lost {
            return out;
        }
        self.link_lost = true;
        self.settling = false;
        self.in_flight = None;
        self.deadline = None;
        self.queue.clear();
        self.rx.clear();
        self.framer = None;
        self.model.streaming_stopped();
        out.events.push(Event::LinkLost(reason.into()));
        self.set_state(State::Disconnected, &mut out);
        out
    }

    pub fn start_streaming(&mut self, now_ms: u64) -> Output {
        let out = Output::default();
        if self.link_lost {
            return out;
        }
        if self.state != State::Ready {
            let why = format!("start_streaming() called in state {:?}", self.state);
            return self.failed(out, why);
        }
        let mut out = out;
        self.model.prepare_for_streaming();
        self.set_state(State::Starting, &mut out);
        self.queue.push_back(Command::ack(
            "START_STREAMING",
            vec![START_STREAMING_COMMAND],
        ));
        self.send_next(now_ms, &mut out);
        out
    }

    pub fn stop_streaming(&mut self, now_ms: u64) -> Output {
        let out = Output::default();
        if self.link_lost {
            return out;
        }
        if self.state != State::Streaming {
            let why = format!("stop_streaming() called in state {:?}", self.state);
            return self.failed(out, why);
        }
        let mut out = out;
        self.set_state(State::Stopping, &mut out);
        self.queue
            .push_back(Command::ack("STOP_STREAMING", vec![STOP_STREAMING_COMMAND]));
        self.send_next(now_ms, &mut out);
        out
    }

    /// Bytes received from the device, in the order they arrived.
    pub fn receive(&mut self, data: &[u8], now_ms: u64) -> Output {
        let mut out = Output::default();
        if matches!(self.state, State::Disconnected | State::Failed) {
            return out;
        }
        if self.settling {
            self.settle_on(data, now_ms, &mut out);
            return out;
        }
        self.rx.extend_from_slice(data);
        loop {
            if self.rx.is_empty() || self.state == State::Failed {
                break;
            }
            let progressed = if matches!(self.state, State::Streaming | State::Stopping) {
                self.receive_streaming(now_ms, &mut out)
            } else {
                self.receive_response(now_ms, &mut out)
            };
            if !progressed {
                break;
            }
        }
        out
    }

    /// Starts the handshake once the link is quiet, and fires a command timeout if one is due.
    pub fn tick(&mut self, now_ms: u64) -> Output {
        let mut out = Output::default();
        if self.settling {
            if now_ms >= self.quiet_at {
                self.start_handshake(now_ms, &mut out);
            } else if now_ms >= self.settle_give_up_at {
                return self.failed(
                    out,
                    format!(
                        "the device was already streaming and did not stop within {} ms",
                        SETTLE_GIVE_UP_MS
                    ),
                );
            }
            return out;
        }
        let due = match (&self.in_flight, self.deadline) {
            (Some(cmd), Some(deadline)) if now_ms >= deadline => {
                Some((cmd.name.clone(), cmd.timeout_ms, cmd.optional))
            }
            _ => None,
        };
        if let Some((name, timeout_ms, optional)) = due {
            let waited_for = if self.ack_seen { "response" } else { "ACK" };
            let why = format!("no {} to {} within {} ms", waited_for, name, timeout_ms);
            if optional {
                out.events
                    .push(Event::Discarded(format!("{}; carrying on", why)));
                self.complete(now_ms, &mut out);
            } else {
                return self.failed(out, why);
            }
        }
        out
    }

    // --- Settling: a device left streaming by an earlier session -------------------------------

    /// Bytes before the handshake can only be a stream left running: stop it, then wait for quiet.
    fn settle_on(&mut self, data: &[u8], now_ms: u64, out: &mut Output) {
        self.settle_bytes += data.len();
        self.quiet_at = now_ms + SETTLE_MS;
        if !self.stop_sent {
            self.stop_sent = true;
            out.writes.push(vec![STOP_STREAMING_COMMAND]);
        }
    }

    fn start_handshake(&mut self, now_ms: u64, out: &mut Output) {
        self.settling = false;
        if self.settle_bytes > 0 {
            out.events.push(Event::Discarded(format!(
                "the device was already streaming; stopped it and dropped {} byte(s)",
                self.settle_bytes
            )));
        }
        if self.stop_sent {
            // The earlier session probably turned checksums on, and the device keeps them until
            // it is disconnected. Firmware too old for checksums does not ACK this: carry on.
            let mut off = Command::ack("SET_CRC_OFF", vec![SET_CRC_COMMAND, CRC_OFF]);
            off.optional = true;
            self.queue.push_back(off);
        }
        self.queue.push_back(Command::reply(
            "GET_SHIMMER_VERSION",
            vec![GET_SHIMMER_VERSION_COMMAND_NEW],
            GET_SHIMMER_VERSION_RESPONSE,
            Reply::HardwareVersion,
        ));
        self.queue.push_back(Command::reply(
            "GET_FW_VERSION",
            vec![GET_FW_VERSION_COMMAND],
            FW_VERSION_RESPONSE,
            Reply::FirmwareVersion,
        ));
        self.send_next(now_ms, out);
    }

    // --- Commands and responses -----------------------------------------------------------------

    fn send_next(&mut self, now_ms: u64, out: &mut Output) {
        if self.in_flight.is_some() || self.state == State::Failed {
            return;
        }
        let Some(cmd) = self.queue.pop_front() else {
            self.on_queue_empty(out);
            return;
        };
        let mut data = cmd.data.clone();
        if data[0] == SET_RWC_COMMAND {
            // Stamped when sent, not when queued.
            data = std::iter::once(SET_RWC_COMMAND)
                .chain(rtc_bytes(now_ms))
                .collect();
        } else if data[0] == SET_CRC_COMMAND {
            // The device answers SET_CRC already in the new mode.
            self.model.apply_crc_mode(data[1]);
            self.crc_bytes = data[1] as usize;
        } else if data[0] == STOP_STREAMING_COMMAND {
            if let Some(framer) = self.framer.as_mut() {
                framer.expect_ack();
            }
        }
        self.deadline = Some(now_ms + cmd.timeout_ms);
        self.ack_seen = false;
        self.in_flight = Some(cmd);
        out.writes.push(data);
    }

    fn complete(&mut self, now_ms: u64, out: &mut Output) {
        let done = self.in_flight.take().expect("a command in flight");
        self.deadline = None;
        if done.data[0] == START_STREAMING_COMMAND {
            self.model.streaming_started();
            self.framer = Some(StreamFramer::new(self.model.packet_size, self.crc_bytes));
            self.set_state(State::Streaming, out);
        } else if done.data[0] == STOP_STREAMING_COMMAND {
            self.model.streaming_stopped();
            // What the framer had not framed is no longer stream: back to the reply buffer.
            if let Some(mut framer) = self.framer.take() {
                let rest = framer.take_buffered();
                self.rx.splice(0..0, rest);
            }
            self.set_state(State::Ready, out);
        }
        self.send_next(now_ms, out);
    }

    fn on_queue_empty(&mut self, out: &mut Output) {
        if self.state == State::Connecting {
            self.set_state(State::Ready, out);
            let m = &self.model;
            out.events.push(Event::Initialised(format!(
                "{} {}, {} Hz, packet size {}",
                m.device_name(),
                m.firmware_version_text(),
                m.sampling_rate,
                m.packet_size
            )));
        }
    }

    /// The length of a reply's payload given the bytes received from its start, or None if not
    /// known yet.
    fn reply_length(&self, reply: Reply, start: usize) -> Option<usize> {
        let length_prefixed = |header: usize| self.rx.get(start).map(|&n| header + n as usize);
        match reply {
            Reply::Ack => Some(0),
            Reply::HardwareVersion => Some(1),
            Reply::FirmwareVersion => Some(6),
            Reply::ExpansionBoard | Reply::ConfigBytes | Reply::PressureCoefficients => {
                length_prefixed(1)
            }
            Reply::CalibDump => length_prefixed(3),
            Reply::Inquiry => {
                let settings = self.model.inquiry_settings_length();
                if self.rx.len() < start + settings {
                    return None;
                }
                Some(settings + self.rx[start + self.model.inquiry_channel_count_index()] as usize)
            }
        }
    }

    /// Not streaming: an ACK, then the expected response (if any), then the checksum.
    fn receive_response(&mut self, now_ms: u64, out: &mut Output) -> bool {
        let Some(cmd) = self.in_flight.clone() else {
            // Nothing was asked for. Unsolicited bytes are not handled yet; drop them.
            self.dropped += self.rx.len();
            self.rx.clear();
            self.report_dropped(out);
            return false;
        };
        if !self.ack_seen {
            let Some(ack) = self.rx.iter().position(|&b| b == ACK) else {
                self.dropped += self.rx.len();
                self.rx.clear();
                return false;
            };
            self.dropped += ack;
            self.rx.drain(..ack);
            if cmd.response_code.is_none() {
                // ACK only: the ACK and its checksum.
                if self.rx.len() < 1 + self.crc_bytes {
                    return false;
                }
                let framed: Vec<u8> = self.rx[..1 + self.crc_bytes].to_vec();
                self.check_response_crc(&framed, 1, &cmd.name, out);
                self.rx.drain(..1 + self.crc_bytes);
                self.report_dropped(out);
                self.complete(now_ms, out);
                return true;
            }
            self.rx.drain(..1);
            self.ack_seen = true;
            self.report_dropped(out);
        }
        if self.rx.is_empty() {
            return false;
        }
        let code = cmd.response_code.unwrap();
        if self.rx[0] != code {
            let why = format!(
                "expected response 0x{:02X} to {}, got 0x{:02X}",
                code, cmd.name, self.rx[0]
            );
            self.fail_in_place(out, why);
            return false;
        }
        let Some(length) = self.reply_length(cmd.reply, 1) else {
            return false;
        };
        if self.rx.len() < 1 + length + self.crc_bytes {
            return false;
        }
        let payload = self.rx[1..1 + length].to_vec();
        // The checksum covers the ACK too, which is no longer in the buffer.
        let framed: Vec<u8> = std::iter::once(ACK)
            .chain(self.rx[..1 + length + self.crc_bytes].iter().copied())
            .collect();
        self.check_response_crc(&framed, 2 + length, &cmd.name, out);
        self.rx.drain(..1 + length + self.crc_bytes);
        self.handle_reply(cmd.reply, &payload, now_ms, out);
        if self.state != State::Failed {
            self.complete(now_ms, out);
        }
        true
    }

    /// Streaming: data packets through the framer, plus the ACK of STOP_STREAMING.
    fn receive_streaming(&mut self, now_ms: u64, out: &mut Output) -> bool {
        let bytes = std::mem::take(&mut self.rx);
        let Some(framer) = self.framer.as_mut() else {
            return false;
        };
        let frames = framer.push(&bytes);
        for frame in frames {
            match frame {
                Frame::Packet(packet) => out
                    .events
                    .push(Event::Sample(self.model.decode(&packet, now_ms))),
                Frame::Dropped(n) => out.events.push(Event::Discarded(format!(
                    "{} unexpected byte(s) dropped",
                    n
                ))),
                Frame::Ack => {
                    // STOP_STREAMING's: complete it, which hands the bytes after it back to rx.
                    self.complete(now_ms, out);
                    return true;
                }
            }
        }
        false
    }

    fn check_response_crc(&self, framed: &[u8], covered: usize, name: &str, out: &mut Output) {
        // Replies' checksums are reported, not enforced: the Java driver does not check them.
        if self.crc_bytes == 0 {
            return;
        }
        let expected = crc(&framed[..covered]);
        let ok = framed[covered] == expected[0]
            && (self.crc_bytes < 2 || framed[covered + 1] == expected[1]);
        if !ok {
            out.events.push(Event::Discarded(format!(
                "CRC mismatch on the reply to {} (not enforced)",
                name
            )));
        }
    }

    fn report_dropped(&mut self, out: &mut Output) {
        if self.dropped > 0 {
            out.events.push(Event::Discarded(format!(
                "{} unexpected byte(s) dropped",
                self.dropped
            )));
            self.dropped = 0;
        }
    }

    // --- Handshake steps ------------------------------------------------------------------------

    fn handle_reply(&mut self, reply: Reply, payload: &[u8], _now_ms: u64, out: &mut Output) {
        match reply {
            Reply::Ack => {}
            Reply::HardwareVersion => {
                self.model.apply_hardware_version(payload[0]);
                if !self.model.is_supported_hardware() {
                    let why = format!(
                        "device reports hardware version {}, not a Shimmer3 or Shimmer3R",
                        payload[0]
                    );
                    self.fail_in_place(out, why);
                }
            }
            Reply::FirmwareVersion => {
                self.model.apply_firmware_version(payload);
                if let Some(why) = self.model.unsupported_firmware() {
                    self.fail_in_place(out, why);
                    return;
                }
                self.queue.push_back(Command::reply(
                    "GET_DAUGHTER_CARD_ID",
                    vec![GET_DAUGHTER_CARD_ID_COMMAND, 0x03, 0x00],
                    DAUGHTER_CARD_ID_RESPONSE,
                    Reply::ExpansionBoard,
                ));
            }
            Reply::ExpansionBoard => {
                // [length, id, revision, special revision]
                self.model.apply_expansion_board(&payload[1..4]);
                self.queue_rest_of_handshake();
            }
            Reply::ConfigBytes => {
                self.config.extend_from_slice(&payload[1..]);
                if self.config.len() >= self.config_length {
                    self.model.apply_config_bytes(&self.config);
                }
            }
            Reply::PressureCoefficients => {
                if let Some(why) = self.model.apply_pressure_coefficients(&payload[1..]) {
                    out.events.push(Event::Discarded(format!(
                        "pressure calibration coefficients rejected: {}",
                        why
                    )));
                }
            }
            Reply::CalibDump => self.on_calib_dump(payload),
            Reply::Inquiry => self.model.apply_inquiry(payload),
        }
    }

    /// The rest of the handshake depends on the firmware and, for the pressure sensor, on the
    /// expansion board, so it is queued only once both are known.
    fn queue_rest_of_handshake(&mut self) {
        if self.model.is_bt_crc_mode_supported() {
            self.queue
                .push_back(Command::ack("SET_CRC", vec![SET_CRC_COMMAND, CRC_ONE_BYTE]));
        }
        self.config_length = self.model.config_byte_length();
        self.config.clear();
        let start = self.model.config_byte_start_address();
        let mut offset = 0;
        while offset < self.config_length {
            let size = MEM_CHUNK.min(self.config_length - offset);
            self.queue.push_back(Command::mem_read(
                "GET_INFOMEM",
                GET_INFOMEM_COMMAND,
                start + offset,
                size,
                INFOMEM_RESPONSE,
                Reply::ConfigBytes,
            ));
            offset += MEM_CHUNK;
        }
        if self.model.reads_pressure_coefficients() {
            self.queue.push_back(Command::reply(
                "GET_PRESSURE_CALIBRATION_COEFFICIENTS",
                vec![GET_PRESSURE_CALIBRATION_COEFFICIENTS_COMMAND],
                PRESSURE_CALIBRATION_COEFFICIENTS_RESPONSE,
                Reply::PressureCoefficients,
            ));
        }
        self.calib_dump.clear();
        self.calib_dump_length = None;
        self.queue.push_back(Self::calib_dump_read(0, MEM_CHUNK));
        self.queue.push_back(Command::reply(
            "INQUIRY",
            vec![INQUIRY_COMMAND],
            INQUIRY_RESPONSE,
            Reply::Inquiry,
        ));
        self.queue
            .push_back(Command::ack("SET_RWC", vec![SET_RWC_COMMAND]));
    }

    fn calib_dump_read(address: usize, size: usize) -> Command {
        Command::mem_read(
            "GET_CALIB_DUMP",
            GET_CALIB_DUMP_COMMAND,
            address,
            size,
            RSP_CALIB_DUMP_COMMAND,
            Reply::CalibDump,
        )
    }

    fn on_calib_dump(&mut self, payload: &[u8]) {
        // [length, address LSB, address MSB, data...]
        let data = &payload[3..];
        let first = self.calib_dump_length.is_none();
        self.calib_dump.extend_from_slice(data);
        if first {
            // The dump starts with its own length, which does not count those two bytes.
            let length = u16::from_le_bytes([data[0], data[1]]) as usize + 2;
            self.calib_dump_length = Some(length);
            // Read the rest of the dump before anything else that is queued.
            let mut address = MEM_CHUNK;
            let mut rest = Vec::new();
            while address < length {
                rest.push(Self::calib_dump_read(
                    address,
                    MEM_CHUNK.min(length - address),
                ));
                address += MEM_CHUNK;
            }
            for cmd in rest.into_iter().rev() {
                self.queue.push_front(cmd);
            }
        }
        let length = self.calib_dump_length.unwrap();
        if self.calib_dump.len() >= length {
            let dump = self.calib_dump[..length].to_vec();
            self.model.apply_calibration_dump(&dump);
        }
    }

    // --- Helpers --------------------------------------------------------------------------------

    fn set_state(&mut self, state: State, out: &mut Output) {
        if self.state != state {
            self.state = state;
            out.events.push(Event::StateChanged(state));
        }
    }

    fn fail_in_place(&mut self, out: &mut Output, why: String) {
        let mut done = Output::default();
        std::mem::swap(out, &mut done);
        *out = self.failed(done, why);
    }

    fn failed(&mut self, mut out: Output, why: String) -> Output {
        self.settling = false;
        self.in_flight = None;
        self.deadline = None;
        self.queue.clear();
        out.events.push(Event::Error(why));
        self.set_state(State::Failed, &mut out);
        out
    }
}
