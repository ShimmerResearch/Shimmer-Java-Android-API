//! The whole state machine replayed against the two recorded sessions, on a simulated clock: each
//! write is answered as recorded, delayed as recorded. Ports of the Java SessionResponder and
//! ProtocolReplay, and of the API_00027 tests: the handshake completes against the real device's
//! replies, and the samples streamed are exactly those the Java decoder produced.

mod common;

use std::cmp::Reverse;
use std::collections::BinaryHeap;

use common::*;
use shimmer_protocol::protocol::{Event, LogAndStreamProtocol, Output, State, SET_RWC_COMMAND};

const START_MS: u64 = 1790960000000; // the Java replay's origin

/// Answers each write with the RX entries recorded after the same command.
struct SessionResponder {
    entries: Vec<Entry>,
    cursor: usize,
    unanswered: Vec<Vec<u8>>,
}

impl SessionResponder {
    fn same_command(recorded: &[u8], written: &[u8]) -> bool {
        // The clock in SET_RWC always differs.
        !recorded.is_empty()
            && !written.is_empty()
            && recorded[0] == written[0]
            && (written[0] == SET_RWC_COMMAND || recorded == written)
    }

    fn answer_at(&self, tx: usize) -> (u64, Vec<Entry>) {
        let rx = self.entries[tx + 1..]
            .iter()
            .take_while(|e| !e.tx)
            .cloned()
            .collect();
        (self.entries[tx].time_ms, rx)
    }

    fn answer(&mut self, written: &[u8]) -> Option<(u64, Vec<Entry>)> {
        if let Some(next) = (self.cursor..self.entries.len()).find(|&i| self.entries[i].tx) {
            if Self::same_command(&self.entries[next].data, written) {
                let answer = self.answer_at(next);
                self.cursor = next + 1 + answer.1.len();
                return Some(answer);
            }
        }
        if let Some(i) = (0..self.entries.len())
            .find(|&i| self.entries[i].tx && Self::same_command(&self.entries[i].data, written))
        {
            return Some(self.answer_at(i));
        }
        self.unanswered.push(written.to_vec());
        None
    }
}

struct Replay {
    responder: SessionResponder,
    now: u64,
    order: u64,
    pending: BinaryHeap<Reverse<(u64, u64, Vec<u8>)>>,
    events: Vec<Event>,
    timestamps: Vec<u32>,
}

impl Replay {
    /// Connects, starts streaming once ready, and runs until the recording is exhausted.
    fn run(entries: Vec<Entry>) -> (Replay, LogAndStreamProtocol) {
        let mut p = LogAndStreamProtocol::new();
        let mut r = Replay {
            responder: SessionResponder {
                entries,
                cursor: 0,
                unanswered: Vec::new(),
            },
            now: START_MS,
            order: 0,
            pending: BinaryHeap::new(),
            events: Vec::new(),
            timestamps: Vec::new(),
        };
        let out = p.connect(r.now);
        r.handle(out);
        let mut started = false;
        while p.state() != State::Failed {
            if !started && p.state() == State::Ready {
                started = true;
                let out = p.start_streaming(r.now);
                r.handle(out);
                continue;
            }
            let next_delivery = r.pending.peek().map(|Reverse((t, _, _))| *t);
            match (next_delivery, p.next_deadline()) {
                (None, None) => break,
                (Some(t), d) if d.map_or(true, |d| t <= d) => {
                    let Reverse((when, _, data)) = r.pending.pop().unwrap();
                    r.now = r.now.max(when);
                    let out = p.receive(&data, r.now);
                    r.handle(out);
                }
                (_, Some(d)) => {
                    r.now = d;
                    let out = p.tick(r.now);
                    r.handle(out);
                }
                _ => unreachable!(),
            }
        }
        (r, p)
    }

    fn handle(&mut self, out: Output) {
        for e in out.events {
            if let Event::Sample(s) = &e {
                self.timestamps.push(s.timestamp_ticks);
            }
            self.events.push(e);
        }
        for w in out.writes {
            if let Some((tx_time, rx)) = self.responder.answer(&w) {
                for e in rx {
                    let delay = e.time_ms.saturating_sub(tx_time);
                    self.order += 1;
                    self.pending
                        .push(Reverse((self.now + delay, self.order, e.data)));
                }
            }
        }
    }

    fn errors(&self) -> Vec<String> {
        self.events
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
}

fn check_session(session: &str, reference: &str, expected_samples: usize, initialised: &str) {
    let (r, p) = Replay::run(load_session(session));

    assert_eq!(r.errors(), Vec::<String>::new());
    assert_eq!(p.state(), State::Streaming);
    assert_eq!(p.sampling_rate(), 51.2);
    assert!(
        r.responder.unanswered.is_empty(),
        "unanswered: {:02X?}",
        r.responder.unanswered
    );
    assert!(r.events.contains(&Event::Initialised(initialised.into())));
    assert_eq!(r.timestamps, java_timestamps(reference));
    assert_eq!(r.timestamps.len(), expected_samples);
}

#[test]
fn shimmer3r_handshake_completes_and_streams_the_same_samples_as_java() {
    check_session(
        SHIMMER3R_SESSION,
        SHIMMER3R_REFERENCE,
        493,
        "Shimmer3R LogAndStream v1.1.16, 51.2 Hz, packet size 23",
    );
}

#[test]
fn shimmer3_handshake_completes_and_streams_the_same_samples_as_java() {
    check_session(
        SHIMMER3_SESSION,
        SHIMMER3_REFERENCE,
        414,
        "Shimmer3 LogAndStream v1.1.3, 51.2 Hz, packet size 23",
    );
}

#[test]
fn how_the_bytes_are_split_makes_no_difference() {
    let entries = load_session(SHIMMER3R_SESSION);
    let split: Vec<Entry> = entries
        .iter()
        .flat_map(|e| {
            if e.tx {
                vec![e.clone()]
            } else {
                e.data
                    .iter()
                    .map(|b| Entry {
                        time_ms: e.time_ms,
                        tx: false,
                        data: vec![*b],
                    })
                    .collect()
            }
        })
        .collect();
    let (whole, _) = Replay::run(entries);
    let (one_byte_at_a_time, _) = Replay::run(split);

    assert_eq!(one_byte_at_a_time.errors(), Vec::<String>::new());
    assert_eq!(one_byte_at_a_time.timestamps, whole.timestamps);
}

#[test]
fn a_corrupted_packet_is_dropped_and_decoding_resumes() {
    let entries = load_session(SHIMMER3R_SESSION);
    let start = entries
        .iter()
        .position(|e| e.tx && e.data.first() == Some(&0x07))
        .unwrap();
    // The 3rd notification after START_STREAMING; byte 5 is inside its first packet's data.
    let mut corrupted_entries = entries.clone();
    let third_rx = (start + 1..entries.len())
        .filter(|&i| !entries[i].tx)
        .nth(2)
        .unwrap();
    corrupted_entries[third_rx].data[5] ^= 0xFF;

    let (clean, _) = Replay::run(entries);
    let (corrupted, _) = Replay::run(corrupted_entries);

    assert_eq!(corrupted.errors(), Vec::<String>::new());
    assert_eq!(
        corrupted.timestamps.len(),
        clean.timestamps.len() - 1,
        "exactly the corrupted packet is lost"
    );
    assert!(
        corrupted
            .events
            .iter()
            .any(|e| matches!(e, Event::Discarded(_))),
        "the dropped bytes are reported"
    );
    assert_eq!(corrupted.timestamps.last(), clean.timestamps.last());
}
