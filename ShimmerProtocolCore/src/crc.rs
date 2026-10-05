//! The Shimmer UART checksum, as the firmware computes it over replies and data packets.
//! A port of `com.shimmerresearch.comms.wiredProtocol.ShimmerCrc`.

const CRC_INIT: u16 = 0xB0CA;

fn crc_byte(crc: u16, b: u8) -> u16 {
    // The Java works on 32-bit ints and masks to 16 bits at the end; bits above 16 never reach
    // the low 16, so wrapping 16-bit arithmetic gives the same result.
    let mut crc = crc.rotate_left(8);
    crc ^= b as u16;
    crc ^= (crc & 0xFF) >> 4;
    crc ^= crc << 12;
    crc ^= (crc & 0xFF) << 5;
    crc
}

/// The two checksum bytes, LSB first, over all of `data` (which must not be empty).
pub fn crc(data: &[u8]) -> [u8; 2] {
    let mut value = crc_byte(CRC_INIT, data[0]);
    for &b in &data[1..] {
        value = crc_byte(value, b);
    }
    if data.len() % 2 == 1 {
        value = crc_byte(value, 0x00);
    }
    value.to_le_bytes()
}

#[cfg(test)]
mod tests {
    use super::crc;

    #[test]
    fn matches_the_device_and_the_java_driver() {
        // The device's own: its ACK to SET_CRC (one-byte mode) in the recordings is 0xFF 0xF4.
        assert_eq!(crc(&[0xFF])[0], 0xF4);
        // What the Java ShimmerCrc computes for the same input.
        assert_eq!(crc(&[0xFF, 0x02, 0x80, 0x02]), [0xFE, 0x0E]);
        let ramp: Vec<u8> = (1..=24).collect();
        assert_eq!(crc(&ramp), [0x32, 0xED]);
    }
}
