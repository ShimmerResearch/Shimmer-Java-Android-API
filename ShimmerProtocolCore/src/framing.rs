//! The data-stream framer: splits the bytes a streaming Shimmer sends into data packets.
//!
//! A port of the streaming half of the Java `LogAndStreamProtocol.receive`. Bytes may arrive in
//! any chunks. A packet is `[0x00][packet_size bytes][checksum]`; with checksums on, a packet
//! whose checksum does not match is skipped one byte at a time until the stream lines up again,
//! and with them off, the byte after a packet must start the next one (0x00 or an ACK).

use crate::crc::crc;

pub const DATA_PACKET: u8 = 0x00;
pub const ACK: u8 = 0xFF;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Frame {
    /// One data packet, without its header byte and checksum.
    Packet(Vec<u8>),
    /// The acknowledgement the caller said to expect, such as STOP_STREAMING's.
    Ack,
    /// Bytes dropped to resynchronise, reported before the frame that followed them.
    Dropped(usize),
}

pub struct StreamFramer {
    packet_size: usize,
    crc_bytes: usize,
    rx: Vec<u8>,
    dropped: usize,
    ack_expected: bool,
}

impl StreamFramer {
    /// `packet_size` excludes the header byte and checksum; `crc_bytes` is 0, 1 or 2.
    pub fn new(packet_size: usize, crc_bytes: usize) -> Self {
        StreamFramer {
            packet_size,
            crc_bytes,
            rx: Vec::new(),
            dropped: 0,
            ack_expected: false,
        }
    }

    /// The next ACK in the stream answers a command (START_ or STOP_STREAMING), not noise.
    pub fn expect_ack(&mut self) {
        self.ack_expected = true;
    }

    /// Bytes received from the device, in the order they arrived.
    pub fn push(&mut self, bytes: &[u8]) -> Vec<Frame> {
        self.rx.extend_from_slice(bytes);
        let mut out = Vec::new();
        let mut at = 0; // consumed so far; drained once at the end
        while at < self.rx.len() {
            let rest = &self.rx[at..];
            if rest[0] == DATA_PACKET {
                let size = self.packet_size;
                // With no checksum, the next packet's header is what confirms this one's length.
                let needed = 1
                    + size
                    + if self.crc_bytes > 0 {
                        self.crc_bytes
                    } else {
                        1
                    };
                if rest.len() < needed {
                    break;
                }
                let valid = if self.crc_bytes > 0 {
                    let expected = crc(&rest[..1 + size]);
                    rest[1 + size] == expected[0]
                        && (self.crc_bytes < 2 || rest[2 + size] == expected[1])
                } else {
                    rest[1 + size] == DATA_PACKET || rest[1 + size] == ACK
                };
                if !valid {
                    at += 1;
                    self.dropped += 1;
                    continue;
                }
                let payload = rest[1..1 + size].to_vec();
                self.report_dropped(&mut out);
                out.push(Frame::Packet(payload));
                at += 1 + size + self.crc_bytes;
            } else if rest[0] == ACK && self.ack_expected {
                if rest.len() < 1 + self.crc_bytes {
                    break;
                }
                at += 1 + self.crc_bytes;
                self.ack_expected = false;
                self.report_dropped(&mut out);
                out.push(Frame::Ack);
                // An ACK changes what the bytes after it mean (after STOP_STREAMING's, they are
                // no longer stream), so stop: the caller continues with `push(&[])` or takes them
                // back with `take_buffered`.
                break;
            } else {
                at += 1;
                self.dropped += 1;
            }
        }
        self.rx.drain(..at);
        out
    }

    /// The bytes received but not yet framed, which the framer gives up.
    pub fn take_buffered(&mut self) -> Vec<u8> {
        std::mem::take(&mut self.rx)
    }

    fn report_dropped(&mut self, out: &mut Vec<Frame>) {
        if self.dropped > 0 {
            out.push(Frame::Dropped(self.dropped));
            self.dropped = 0;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn packet(size: usize, fill: u8, with_crc: bool) -> Vec<u8> {
        let mut p = vec![DATA_PACKET];
        p.extend(std::iter::repeat_n(fill, size));
        if with_crc {
            let c = crc(&p);
            p.push(c[0]);
        }
        p
    }

    #[test]
    fn a_packet_split_anywhere_comes_out_whole() {
        let bytes: Vec<u8> = [packet(4, 0x11, true), packet(4, 0x22, true)].concat();
        let mut f = StreamFramer::new(4, 1);
        let frames: Vec<Frame> = bytes.iter().flat_map(|b| f.push(&[*b])).collect();
        assert_eq!(
            frames,
            vec![Frame::Packet(vec![0x11; 4]), Frame::Packet(vec![0x22; 4])]
        );
    }

    #[test]
    fn a_bad_checksum_is_skipped_and_reported() {
        let mut bad = packet(4, 0x11, true);
        bad[2] ^= 0xFF;
        let bytes: Vec<u8> = [bad, packet(4, 0x22, true)].concat();
        let frames = StreamFramer::new(4, 1).push(&bytes);
        assert_eq!(
            frames,
            vec![Frame::Dropped(6), Frame::Packet(vec![0x22; 4])]
        );
    }

    #[test]
    fn without_checksums_the_next_header_confirms_a_packet() {
        let bytes: Vec<u8> = [packet(4, 0x11, false), packet(4, 0x22, false), vec![ACK]].concat();
        let mut f = StreamFramer::new(4, 0);
        f.expect_ack();
        let frames = f.push(&bytes);
        assert_eq!(
            frames,
            vec![
                Frame::Packet(vec![0x11; 4]),
                Frame::Packet(vec![0x22; 4]),
                Frame::Ack
            ]
        );
    }
}
