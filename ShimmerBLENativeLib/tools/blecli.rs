//! Bench tool for the native BLE engine, independent of Java.
//!
//!   blecli scan [seconds]              list advertising devices
//!   blecli probe <name> [seconds]      connect, send inquiry (0x01), print the reply
//!   blecli stream <name> [seconds]     connect, start streaming (0x07), report bytes/s, stop (0x20)
//!
//! <name> is matched as a substring of the advertised name, e.g. "Shimmer3R-2F31". Add
//! `--id <id>` to probe or stream to connect to a known device without finding it by scan, e.g. one
//! that is already connected and so not advertising. <name> then only picks the BLE service.

use std::collections::HashSet;
use std::env;
use std::process::ExitCode;
use std::time::{Duration, Instant};

use shimmerble::engine::{BleCore, BleError, Event, Result};
use uuid::Uuid;

const INQUIRY_COMMAND: u8 = 0x01;
const INQUIRY_RESPONSE: u8 = 0x02;
const START_STREAMING_COMMAND: u8 = 0x07;
const STOP_STREAMING_COMMAND: u8 = 0x20;
const ACK_COMMAND_PROCESSED: u8 = 0xFF;

const FIND_TIMEOUT: Duration = Duration::from_secs(15);
const CONNECT_TIMEOUT: Duration = Duration::from_secs(20);
const POLL: Duration = Duration::from_millis(100);

/// The BLE UART service each device family exposes. Mirrors BleUartProfile.java.
struct Profile {
    label: &'static str,
    service: Uuid,
    write: Uuid,
    notify: Uuid,
}

fn uuid(s: &str) -> Uuid {
    Uuid::parse_str(s).expect("valid UUID literal")
}

fn profile_for(name: &str) -> Option<Profile> {
    // "Shimmer3R" must be tested before "Shimmer", which it contains.
    if name.contains("Shimmer3R") {
        Some(Profile {
            label: "Shimmer3R",
            service: uuid("65333333-a115-11e2-9e9a-0800200ca100"),
            write: uuid("65333333-a115-11e2-9e9a-0800200ca102"),
            notify: uuid("65333333-a115-11e2-9e9a-0800200ca102"),
        })
    } else if name.contains("Verisense") {
        Some(Profile {
            label: "Verisense",
            service: uuid("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
            write: uuid("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),
            notify: uuid("6e400003-b5a3-f393-e0a9-e50e24dcca9e"),
        })
    } else if name.contains("Shimmer") {
        Some(Profile {
            label: "Shimmer3",
            service: uuid("49535343-fe7d-4ae5-8fa9-9fafd205e455"),
            write: uuid("49535343-8841-43f4-a8d4-ecbe34729bb3"),
            notify: uuid("49535343-1e4d-4bd9-ba61-23c647249616"),
        })
    } else {
        None
    }
}

fn main() -> ExitCode {
    let mut args: Vec<String> = env::args().collect();
    let id = match args.iter().position(|a| a == "--id") {
        Some(i) if i + 1 < args.len() => {
            let id = args.remove(i + 1);
            args.remove(i);
            Some(id)
        }
        _ => None,
    };
    let id = id.as_deref();
    let seconds = |i: usize, default: u64| {
        Duration::from_secs(args.get(i).and_then(|s| s.parse().ok()).unwrap_or(default))
    };
    let result = match (args.get(1).map(String::as_str), args.get(2)) {
        (Some("scan"), _) => scan(seconds(2, 8)),
        (Some("probe"), Some(name)) => probe(name, seconds(3, 3), id),
        (Some("stream"), Some(name)) => stream(name, seconds(3, 10), id),
        _ => {
            eprintln!(
                "usage: blecli scan [seconds] | probe <name> [seconds] [--id <id>] | stream <name> [seconds] [--id <id>]"
            );
            return ExitCode::from(2);
        }
    };
    match result {
        Ok(()) => ExitCode::SUCCESS,
        Err(e) => {
            eprintln!("error: {}", e);
            ExitCode::FAILURE
        }
    }
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{:02X}", b)).collect::<Vec<_>>().join(" ")
}

fn open() -> Result<BleCore> {
    let core = BleCore::new()?;
    println!("adapter state: {}", core.adapter_state()?);
    Ok(core)
}

/// Starts a scan and also reports Shimmer devices already connected to this machine, which do
/// not advertise. Mirrors BleCentral.startScan().
fn start_scan(core: &BleCore) -> Result<()> {
    core.start_scan()?;
    let services = ["Shimmer3R", "Verisense", "Shimmer"]
        .iter()
        .filter_map(|name| profile_for(name))
        .map(|p| p.service)
        .collect();
    // Not fatal: the scan itself is running.
    match core.retrieve_connected(services) {
        Ok(0) => {}
        Ok(n) => println!("{} Shimmer device(s) already connected to this machine", n),
        Err(e) => println!("warning: could not list connected devices: {}", e),
    }
    Ok(())
}

fn scan(duration: Duration) -> Result<()> {
    let core = open()?;
    start_scan(&core)?;
    let deadline = Instant::now() + duration;
    let mut seen = HashSet::new();
    let mut named = HashSet::new();
    while Instant::now() < deadline {
        if let Some(Event::DeviceFound { id, name, address, rssi }) = core.next_event(POLL) {
            // A name often arrives in a later scan response, so print again when it first appears.
            let first_sight = seen.insert(id.clone());
            let first_name = !name.is_empty() && named.insert(id.clone());
            if first_sight || first_name {
                println!("{:<40} {:<24} addr={:<17} rssi={:?}", id, name, address, rssi);
            }
        }
    }
    core.stop_scan()?;
    println!("{} device(s)", seen.len());
    Ok(())
}

/// Scans until a device whose name contains `name_part` appears.
fn find(core: &BleCore, name_part: &str) -> Result<(String, String)> {
    start_scan(core)?;
    let deadline = Instant::now() + FIND_TIMEOUT;
    while Instant::now() < deadline {
        if let Some(Event::DeviceFound { id, name, address, rssi }) = core.next_event(POLL) {
            if name.contains(name_part) {
                core.stop_scan()?;
                println!("found {} id={} addr={} rssi={:?}", name, id, address, rssi);
                return Ok((id, name));
            }
        }
    }
    core.stop_scan()?;
    Err(BleError(format!("no device named *{}* within {:?}", name_part, FIND_TIMEOUT)))
}

fn connect(core: &BleCore, name_part: &str, known_id: Option<&str>) -> Result<i64> {
    let (id, name) = match known_id {
        Some(id) => (id.to_string(), name_part.to_string()),
        None => find(core, name_part)?,
    };
    let profile = profile_for(&name)
        .ok_or_else(|| BleError(format!("no BLE profile for device name {:?}", name)))?;
    let started = Instant::now();
    let handle = core.connect(&id, profile.service, profile.write, profile.notify, CONNECT_TIMEOUT)?;
    println!(
        "connected as {} in {} ms, MTU {}",
        profile.label,
        started.elapsed().as_millis(),
        core.mtu(handle)
    );
    Ok(handle)
}

fn probe(name_part: &str, listen: Duration, id: Option<&str>) -> Result<()> {
    let core = open()?;
    let handle = connect(&core, name_part, id)?;

    core.write(handle, &[INQUIRY_COMMAND])?;
    let sent = Instant::now();
    let mut reply = Vec::new();
    while sent.elapsed() < listen {
        match core.next_event(POLL) {
            Some(Event::Bytes { data, .. }) => {
                println!("+{:>5} ms  {:>3} B  {}", sent.elapsed().as_millis(), data.len(), hex(&data));
                reply.extend_from_slice(&data);
            }
            Some(Event::Disconnected { reason, .. }) => return Err(BleError(format!("link lost: {}", reason))),
            _ => {}
        }
    }
    core.disconnect(handle);

    let acked = reply.first() == Some(&ACK_COMMAND_PROCESSED);
    let inquiry = reply.get(1) == Some(&INQUIRY_RESPONSE);
    println!("{} bytes back; ACK 0xFF: {}; inquiry response 0x02: {}", reply.len(), acked, inquiry);
    if acked && inquiry {
        Ok(())
    } else {
        Err(BleError("unexpected inquiry reply".into()))
    }
}

fn stream(name_part: &str, duration: Duration, id: Option<&str>) -> Result<()> {
    let core = open()?;
    let handle = connect(&core, name_part, id)?;

    core.write(handle, &[START_STREAMING_COMMAND])?;
    let started = Instant::now();
    let mut window = Instant::now();
    let (mut total, mut in_window, mut notifications) = (0usize, 0usize, 0usize);
    while started.elapsed() < duration {
        match core.next_event(POLL) {
            Some(Event::Bytes { data, .. }) => {
                if total == 0 {
                    println!("first bytes: {}", hex(&data[..data.len().min(32)]));
                }
                total += data.len();
                in_window += data.len();
                notifications += 1;
            }
            Some(Event::Disconnected { reason, .. }) => return Err(BleError(format!("link lost: {}", reason))),
            _ => {}
        }
        if window.elapsed() >= Duration::from_secs(1) {
            println!("{:>4} s  {:>7} B/s", started.elapsed().as_secs(), in_window);
            in_window = 0;
            window = Instant::now();
        }
    }
    core.write(handle, &[STOP_STREAMING_COMMAND])?;
    std::thread::sleep(Duration::from_millis(500));
    core.disconnect(handle);

    let secs = started.elapsed().as_secs_f64();
    println!(
        "{} bytes in {} notifications over {:.1} s = {:.0} B/s",
        total,
        notifications,
        secs,
        total as f64 / secs
    );
    Ok(())
}
