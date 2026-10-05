//! The recorded sessions and the Java reference exports, shared by the integration tests.
#![allow(dead_code)]

use std::fs;
use std::path::PathBuf;

pub const SHIMMER3R_SESSION: &str =
    "ShimmerDriverPC/src/test/resources/protocol/shimmer3r_2f31_handshake_stream10s.bytes.log";
pub const SHIMMER3_SESSION: &str =
    "ShimmerDriverPC/src/test/resources/protocol/shimmer3_3e36_handshake_stream10s.bytes.log";
pub const SHIMMER3R_REFERENCE: &str = "python/tests/data/java_reference.csv";
pub const SHIMMER3_REFERENCE: &str = "python/tests/data/java_reference_shimmer3.csv";

fn repo(path: &str) -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join(path)
}

#[derive(Debug, Clone, PartialEq)]
pub struct Entry {
    /// Milliseconds since the first TX or RX line.
    pub time_ms: u64,
    pub tx: bool,
    pub data: Vec<u8>,
}

/// One entry per BLE write (TX) or notification (RX); other lines are skipped.
pub fn load_session(path: &str) -> Vec<Entry> {
    let text = fs::read_to_string(repo(path)).expect("recording");
    let mut entries = Vec::new();
    let mut first = None;
    for line in text.lines() {
        let tx = line.contains(" TX ");
        if !tx && !line.contains(" RX ") {
            continue;
        }
        let (Some(open), Some(close)) = (line.find('['), line.rfind(']')) else {
            continue;
        };
        let t: Vec<u64> = line[..12]
            .split([':', '.'])
            .map(|n| n.parse().unwrap())
            .collect();
        let ms = ((t[0] * 60 + t[1]) * 60 + t[2]) * 1000 + t[3];
        let first_ms = *first.get_or_insert(ms);
        let data = line[open + 1..close]
            .split_whitespace()
            .map(|h| u8::from_str_radix(h.trim_start_matches("0x"), 16).unwrap())
            .collect();
        entries.push(Entry {
            time_ms: ms - first_ms,
            tx,
            data,
        });
    }
    entries
}

/// The notifications that answer START_STREAMING: every RX after its TX, up to the next TX.
pub fn stream_notifications(entries: &[Entry]) -> Vec<Vec<u8>> {
    let start = entries
        .iter()
        .position(|e| e.tx && e.data.first() == Some(&0x07))
        .expect("START_STREAMING");
    entries[start + 1..]
        .iter()
        .take_while(|e| !e.tx)
        .map(|e| e.data.clone())
        .collect()
}

/// The raw timestamps (Timestamp, UNCAL) of every sample the Java decoder produced.
pub fn java_timestamps(reference: &str) -> Vec<u32> {
    let text = fs::read_to_string(repo(reference)).expect("Java reference");
    let mut lines = text.lines();
    let header: Vec<&str> = lines.next().unwrap().split(',').collect();
    let column = header
        .iter()
        .position(|h| *h == "Timestamp|UNCAL|Ticks")
        .expect("timestamp column");
    lines
        .map(|l| l.split(',').nth(column).unwrap().parse::<f64>().unwrap() as u32)
        .collect()
}

/// A Java reference export: one column per channel and format ("Accel_LN_X|CAL|m/(s^2)"), one
/// row per sample, as API_00030_PythonReferenceTest writes it.
pub struct JavaReference {
    /// (channel, "CAL" or "UNCAL", units)
    pub columns: Vec<(String, String, String)>,
    pub rows: Vec<Vec<f64>>,
}

pub fn java_reference(reference: &str) -> JavaReference {
    let text = fs::read_to_string(repo(reference)).expect("Java reference");
    let mut lines = text.lines();
    let columns = lines.next().unwrap().split(',').skip(1).map(|h| {
        let parts: Vec<&str> = h.split('|').collect();
        (
            parts[0].to_string(),
            parts[1].to_string(),
            parts[2].to_string(),
        )
    });
    JavaReference {
        columns: columns.collect(),
        rows: lines
            .map(|l| l.split(',').skip(1).map(java_double).collect())
            .collect(),
    }
}

/// Double.toString's output: "1.0519714E7", "NaN", "Infinity".
pub fn java_double(text: &str) -> f64 {
    match text {
        "NaN" => f64::NAN,
        "Infinity" => f64::INFINITY,
        "-Infinity" => f64::NEG_INFINITY,
        _ => text
            .parse()
            .unwrap_or_else(|_| panic!("not a double: {}", text)),
    }
}

/// Equal to the last bit, sign of zero included (any NaN equal to any NaN).
pub fn same(a: f64, b: f64) -> bool {
    a.to_bits() == b.to_bits() || (a.is_nan() && b.is_nan())
}

pub fn repo_text(path: &str) -> String {
    fs::read_to_string(repo(path)).expect(path)
}
