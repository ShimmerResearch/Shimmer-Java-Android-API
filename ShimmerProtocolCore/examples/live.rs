//! The Rust protocol core driving a real Shimmer3 or Shimmer3R over the Windows BLE transport
//! (`shimmerble`, DEV-1132). This is the whole host: one thread that passes received bytes in,
//! sends the writes out, and ticks the protocol when its deadline is due.
//!
//!   cargo run --release --example live -- <device name> [seconds to stream] [device ID]
//!
//! A device ID (the Bluetooth address on Windows) skips the scan, for a device this PC still
//! holds connected, which does not advertise.

use std::collections::VecDeque;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use shimmer_protocol::protocol::{Event, LogAndStreamProtocol, Output, State};
use shimmerble::engine::{BleCore, Event as BleEvent};
use uuid::Uuid;

const TICK: Duration = Duration::from_millis(20);

/// (service, write, notify) for the device's BLE serial service, by its advertised name.
fn profile(name: &str) -> Option<(Uuid, Uuid, Uuid)> {
    let u = |s: &str| Uuid::parse_str(s).unwrap();
    if name.contains("Shimmer3R") {
        let ca102 = u("65333333-a115-11e2-9e9a-0800200ca102");
        Some((u("65333333-a115-11e2-9e9a-0800200ca100"), ca102, ca102))
    } else if name.contains("Shimmer") {
        Some((
            u("49535343-fe7d-4ae5-8fa9-9fafd205e455"),
            u("49535343-8841-43f4-a8d4-ecbe34729bb3"),
            u("49535343-1e4d-4bd9-ba61-23c647249616"),
        ))
    } else {
        None
    }
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as u64
}

#[derive(Default)]
struct Run {
    timestamps: Vec<u32>,
    discards: usize,
    error: Option<String>,
    /// (arrival, device ticks) of the first sample, and the worst backlog seen so far.
    first: Option<(Instant, u32)>,
    worst_lag_ms: f64,
    /// The last few notifications, printed when bytes are dropped, to show what they were.
    recent: VecDeque<(Instant, Vec<u8>)>,
}

/// Sends the protocol's writes to the device and handles its events.
fn apply(core: &BleCore, handle: i64, out: Output, run: &mut Run) {
    for w in out.writes {
        if let Err(e) = core.write(handle, &w) {
            run.error = Some(format!("write failed: {}", e));
        }
    }
    for e in out.events {
        match e {
            Event::Sample(s) => {
                // How far behind the device the samples arrive: wall time minus device time.
                let (t0, d0) = *run.first.get_or_insert((Instant::now(), s.timestamp_ticks));
                let device_ms = (s.timestamp_ticks.wrapping_sub(d0) & 0xFF_FFFF) as f64 / 32.768;
                let lag = t0.elapsed().as_secs_f64() * 1000.0 - device_ms;
                run.worst_lag_ms = run.worst_lag_ms.max(lag);
                run.timestamps.push(s.timestamp_ticks);
            }
            Event::Discarded(m) => {
                run.discards += 1;
                println!("  discarded: {}", m);
                for (t, data) in &run.recent {
                    let hex: Vec<String> = data.iter().map(|b| format!("{:02X}", b)).collect();
                    println!("    -{:4} ms: {}", t.elapsed().as_millis(), hex.join(" "));
                }
            }
            Event::Error(m) => {
                println!("  ERROR: {}", m);
                run.error = Some(m);
            }
            other => println!("  {:?}", other),
        }
    }
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let name = args.get(1).map(String::as_str).unwrap_or("Shimmer3R");
    let seconds: u64 = args.get(2).and_then(|s| s.parse().ok()).unwrap_or(10);
    let core = BleCore::new().expect("BLE adapter");

    // Find the device by name, or take the ID given.
    let (id, full_name) = match args.get(3) {
        Some(id) => (id.clone(), name.to_string()),
        None => {
            core.start_scan().expect("scan");
            let deadline = Instant::now() + Duration::from_secs(20);
            let found = loop {
                if Instant::now() > deadline {
                    panic!("no device named *{}* found (pass its ID if this PC still holds it connected)", name);
                }
                if let Some(BleEvent::DeviceFound { id, name: n, .. }) = core.next_event(TICK) {
                    if n.contains(name) {
                        break (id, n);
                    }
                }
            };
            core.stop_scan().ok();
            found
        }
    };
    let (service, write, notify) = profile(&full_name).expect("a Shimmer");
    let started = Instant::now();
    let handle = core
        .connect(&id, service, write, notify, Duration::from_secs(20))
        .expect("connect");
    println!(
        "connected to {} in {} ms, MTU {}",
        full_name,
        started.elapsed().as_millis(),
        core.mtu(handle)
    );

    let mut protocol = LogAndStreamProtocol::new();
    let mut run = Run::default();
    let handshake_started = Instant::now();
    apply(&core, handle, protocol.connect(now_ms()), &mut run);
    let mut streaming_since: Option<Instant> = None;
    let mut stop_requested: Option<Instant> = None;
    loop {
        match core.next_event(TICK) {
            Some(BleEvent::Bytes { handle: h, data }) if h == handle => {
                if let Some(stop) = stop_requested {
                    let tail: Vec<String> = data
                        .iter()
                        .rev()
                        .take(4)
                        .rev()
                        .map(|b| format!("{:02X}", b))
                        .collect();
                    println!(
                        "  +{:4} ms after STOP: {:3} bytes, ending {}",
                        stop.elapsed().as_millis(),
                        data.len(),
                        tail.join(" ")
                    );
                }
                run.recent.push_back((Instant::now(), data.clone()));
                if run.recent.len() > 8 {
                    run.recent.pop_front();
                }
                let out = protocol.receive(&data, now_ms());
                apply(&core, handle, out, &mut run);
            }
            Some(BleEvent::Disconnected { handle: h, reason }) if h == handle => {
                let out = protocol.link_lost(&reason, now_ms());
                apply(&core, handle, out, &mut run);
                break;
            }
            _ => {}
        }
        let now = now_ms();
        if protocol.next_deadline().is_some_and(|d| now >= d) {
            let out = protocol.tick(now);
            apply(&core, handle, out, &mut run);
        }
        match protocol.state() {
            State::Failed => break,
            _ if run.error.is_some() => break,
            State::Ready if streaming_since.is_none() => {
                println!(
                    "handshake done in {} ms",
                    handshake_started.elapsed().as_millis()
                );
                streaming_since = Some(Instant::now()); // replaced once STREAMING is reached
                let out = protocol.start_streaming(now_ms());
                apply(&core, handle, out, &mut run);
            }
            State::Streaming if stop_requested.is_none() => {
                if run.timestamps.is_empty() {
                    streaming_since = Some(Instant::now());
                } else if streaming_since
                    .is_some_and(|s| s.elapsed() >= Duration::from_secs(seconds))
                {
                    stop_requested = Some(Instant::now());
                    println!(
                        "  backlog when stopping: worst {:.0} ms so far",
                        run.worst_lag_ms
                    );
                    let out = protocol.stop_streaming(now_ms());
                    apply(&core, handle, out, &mut run);
                }
            }
            State::Ready if stop_requested.is_some() => break,
            _ => {}
        }
    }
    core.disconnect(handle);

    // Report: samples, and gaps in the device timestamps (each packet is 32768 / rate ticks).
    let streamed = match (streaming_since, stop_requested) {
        (Some(a), Some(b)) => (b - a).as_secs_f64(),
        _ => 0.0,
    };
    let per_packet = (32768.0 / protocol.sampling_rate()).round() as u32;
    let missing: u32 = run
        .timestamps
        .windows(2)
        .map(|w| w[1].wrapping_sub(w[0]) & 0xFF_FFFF)
        .filter(|&step| step > per_packet)
        .map(|step| step / per_packet - 1)
        .sum();
    let received = run.timestamps.len() as f64;
    println!(
        "{} samples in {:.1} s = {:.1}/s at {} Hz; {} missing by device timestamp ({:.1}% received); {} discard event(s)",
        run.timestamps.len(),
        streamed,
        if streamed > 0.0 { received / streamed } else { 0.0 },
        protocol.sampling_rate(),
        missing,
        if received > 0.0 { 100.0 * received / (received + missing as f64) } else { 0.0 },
        run.discards
    );
    match run.error {
        Some(e) => {
            println!("FAILED: {}", e);
            std::process::exit(1);
        }
        None if protocol.state() == State::Ready && !run.timestamps.is_empty() => println!("PASS"),
        None => {
            println!("FAILED: ended in state {:?}", protocol.state());
            std::process::exit(1);
        }
    }
}
