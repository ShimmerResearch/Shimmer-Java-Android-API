//! Calibration the recorded devices never exercise (theirs is nominal): a crafted calibration dump
//! with real offsets, uneven sensitivities and off-diagonal alignment, a duplicate record, an
//! all-0xFF record and an unknown sensor, decoded from synthetic packets. The inputs and the Java
//! decoder's outputs come from python/tests/data/java_calibration_reference.txt, written by the
//! Java API_00030_PythonReferenceTest. A port of python/tests/test_calibration.py.
//!
//! And for a Shimmer3, its default calibrations, its older sensors and a calibration dump, in four
//! cases written by the Java API_00031_Shimmer3DecodeReferenceTest.

mod common;

use common::*;
use shimmer_protocol::calibration::RADIO_DUMP;
use shimmer_protocol::model::{
    DeviceModel, SENSOR_LIS2MDL_MAG, SENSOR_LSM6DSV_ACCEL_LN, SENSOR_LSM6DSV_GYRO,
};

const REFERENCE: &str = "python/tests/data/java_calibration_reference.txt";
const START_MS: u64 = 1790960000000;

fn hex(text: &str) -> Vec<u8> {
    (0..text.len())
        .step_by(2)
        .map(|i| u8::from_str_radix(&text[i..i + 2], 16).unwrap())
        .collect()
}

struct Crafted {
    config: Vec<u8>,
    dump: Vec<u8>,
    inquiry: Vec<u8>,
    channels: Vec<String>,
    rows: Vec<(Vec<u8>, Vec<f64>)>,
}

fn load() -> Crafted {
    let text = repo_text(REFERENCE);
    let lines: Vec<&str> = text.lines().collect();
    let input = |name: &str| {
        let line = lines
            .iter()
            .find(|l| l.starts_with(&format!("{},", name)))
            .unwrap();
        hex(line.split(',').nth(1).unwrap())
    };
    Crafted {
        config: input("config"),
        dump: input("dump"),
        inquiry: input("inquiry"),
        channels: lines[3].split(',').skip(1).map(String::from).collect(),
        rows: lines[4..]
            .iter()
            .map(|l| {
                let mut fields = l.split(',');
                let packet = hex(fields.next().unwrap());
                (packet, fields.map(java_double).collect())
            })
            .collect(),
    }
}

/// The same calls, in the same order, as the Java generator makes.
fn model_from(c: &Crafted) -> DeviceModel {
    let mut m = DeviceModel::new();
    m.apply_hardware_version(10);
    m.apply_firmware_version(&[3, 0, 1, 0, 1, 16]);
    m.apply_expansion_board(&[0x30, 0x08, 0x01]);
    m.apply_config_bytes(&c.config);
    m.apply_calibration_dump(&c.dump);
    m.apply_inquiry(&c.inquiry);
    m.prepare_for_streaming();
    m
}

fn diagonal(m: [[f64; 3]; 3]) -> [f64; 3] {
    [m[0][0], m[1][1], m[2][2]]
}

#[test]
fn the_crafted_dump_is_what_is_applied() {
    let m = model_from(&load());

    assert!(!m.gyro_on_the_fly_enabled());
    let accel = m.calibration(SENSOR_LSM6DSV_ACCEL_LN, 0).unwrap();
    assert_eq!(accel.source, RADIO_DUMP);
    assert_eq!(
        accel.current_offset,
        Some([12.0, -34.0, 2047.0]),
        "the later record replaces the earlier"
    );
    assert_eq!(
        diagonal(accel.current_sensitivity.unwrap()),
        [1650.0, 1680.0, 1700.0]
    );
    assert_eq!(accel.current_alignment.unwrap()[0], [-0.99, 0.03, -0.02]);
    let accel_8g = m.calibration(SENSOR_LSM6DSV_ACCEL_LN, 2).unwrap();
    assert_eq!(accel_8g.current_offset, Some([5.0, 5.0, 5.0]));
    assert_eq!(m.gyro_range, 1);
    let gyro = m.calibration(SENSOR_LSM6DSV_GYRO, 1).unwrap();
    assert_eq!(
        diagonal(gyro.current_sensitivity.unwrap()),
        [112.5, 115.0, 113.99]
    );
    let mag = m.calibration(SENSOR_LIS2MDL_MAG, 0).unwrap();
    assert_eq!(
        mag.current_offset,
        Some([-120.0, 300.0, 5.0]),
        "the all-0xFF record is skipped"
    );
}

#[test]
fn calibrated_values_are_identical_to_the_java_decoder() {
    let c = load();
    let mut m = model_from(&c);

    for (i, (packet, expected)) in c.rows.iter().enumerate() {
        let sample = m.decode(packet, START_MS + 20 * i as u64);
        for (channel, &want) in c.channels.iter().zip(expected) {
            let got = sample.get(channel).unwrap();
            assert!(
                same(got, want),
                "packet {}, {}: {} != {}",
                i,
                channel,
                got,
                want
            );
        }
    }
}

/// What the Java decodes that this model does not yet (it sizes them, but does not decode them).
fn not_decoded(channel: &str) -> bool {
    channel.starts_with("Pressure_BMP") || channel.starts_with("Temperature_BMP")
}

/// A Shimmer3 case from tests/data, written by the Java API_00031_Shimmer3DecodeReferenceTest:
/// a Shimmer3 on LogAndStream v1.1.3 with the recorded device's config bytes (InfoMem
/// calibration blanked), an expansion board that picks its sensors, and a calibration dump (or
/// none); then synthetic packets carrying temperature and pressure before the gyro and mag, so
/// that the Shimmer3's channel sizes decide where those are read. Every channel compared.
fn check_shimmer3(case: &str) {
    let text = repo_text(&format!("ShimmerProtocolCore/tests/data/java_{}.txt", case));
    let lines: Vec<&str> = text.lines().collect();
    let input = |i: usize| hex(lines[i].split(',').nth(1).unwrap());
    let mut m = DeviceModel::new();
    m.apply_hardware_version(3);
    m.apply_firmware_version(&[3, 0, 1, 0, 1, 3]);
    m.apply_expansion_board(&input(0));
    m.apply_config_bytes(&input(1));
    m.apply_calibration_dump(&input(2));
    m.apply_inquiry(&input(3));
    m.prepare_for_streaming();
    assert_eq!(m.packet_size, 28);

    let columns: Vec<Vec<&str>> = lines[4]
        .split(',')
        .skip(1)
        .map(|c| c.split('|').collect())
        .collect();
    let decoded: Vec<&Vec<&str>> = columns.iter().filter(|c| !not_decoded(c[0])).collect();
    assert_eq!(decoded.len(), 31);
    for (i, row) in lines[5..].iter().enumerate() {
        let mut fields = row.split(',');
        let sample = m.decode(&hex(fields.next().unwrap()), START_MS + 20 * i as u64);
        let mut names: Vec<&str> = sample.readings.iter().map(|r| r.name).collect();
        let mut java_names: Vec<&str> = decoded.iter().map(|c| c[0]).collect();
        names.sort();
        java_names.sort();
        java_names.dedup();
        assert_eq!(names, java_names, "the same channels");
        for (column, value) in columns.iter().zip(fields) {
            if not_decoded(column[0]) {
                continue;
            }
            let reading = sample.reading(column[0]).unwrap();
            let got = if column[1] == "CAL" {
                reading.cal
            } else {
                reading.uncal.unwrap()
            };
            let want = java_double(value);
            assert!(
                same(got, want),
                "{} packet {}, {} {}: {} != {}",
                case,
                i,
                column[0],
                column[1],
                got,
                want
            );
            if column[1] == "CAL" {
                assert_eq!(reading.units, column[2], "{}", column[0]);
            }
        }
    }
}

#[test]
fn shimmer3_older_sensors_at_their_defaults_decode_as_java() {
    check_shimmer3("shimmer3_older_sensors_defaults");
}

#[test]
fn shimmer3_newer_sensors_at_their_defaults_decode_as_java() {
    check_shimmer3("shimmer3_newer_sensors_defaults");
}

#[test]
fn shimmer3_older_sensors_with_a_calibration_dump_decode_as_java() {
    check_shimmer3("shimmer3_older_sensors_dump");
}

#[test]
fn shimmer3_newer_sensors_with_a_calibration_dump_decode_as_java() {
    check_shimmer3("shimmer3_newer_sensors_dump");
}
