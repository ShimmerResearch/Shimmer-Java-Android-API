package com.shimmerresearch.sensors.bmpX80;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * The BMP581's compensated temperature is a signed 24-bit value over 65536, and
 * the driver receives it as UINT24. Before DEV-1102 it was used unsigned, so a
 * fridge test that took two Shimmer3Rs below freezing exported 254.33 to 256.00
 * deg C for every sample under 0 deg C.
 *
 * @author Mark Nolan
 */
public class API_00013_Bmp581TemperatureSignTest {

	private static final double LSB_C = 1.0 / 65536.0;

	private static double calTemp(long raw) {
		return new CalibDetailsBmp581("test").calibratePressureSensorData(0, raw)[1];
	}

	@Test
	public void positiveTemperaturesAreUnchanged() {
		assertEquals(0.0, calTemp(0x000000), 0);
		assertEquals(1600000 * LSB_C, calTemp(1600000), 0); // 24.41 deg C
		assertEquals(0x7FFFFF * LSB_C, calTemp(0x7FFFFF), 0); // top of the range, ~128 deg C
	}

	@Test
	public void subZeroTemperaturesAreNegative() {
		assertEquals(-LSB_C, calTemp(0xFFFFFF), 0); // previously 255.99998
		// CE2F's coldest reading in the fridge test: -1.667 deg C, previously 254.333
		assertEquals(-109226 * LSB_C, calTemp(0xFE5556), 0);
		assertEquals(-128.0, calTemp(0x800000), 0); // bottom of the range
	}

	@Test
	public void noSubZeroReadingLandsAboveOneHundredTwentyEightDegrees() {
		// The whole upper half of the unsigned range is sub-zero.
		for (long raw = 0x800000; raw <= 0xFFFFFF; raw += 0x1111) {
			double t = calTemp(raw);
			assertTrue("raw 0x" + Long.toHexString(raw) + " gave " + t, t < 0 && t >= -128.0);
		}
	}

	@Test
	public void signExtendIsIdempotent() {
		assertEquals(-1.0, CalibDetailsBmp581.signExtend24(-1), 0);
		assertEquals(-1.0, CalibDetailsBmp581.signExtend24(0xFFFFFF), 0);
		assertEquals(0x7FFFFF, CalibDetailsBmp581.signExtend24(0x7FFFFF), 0);
	}

	@Test
	public void pressureStaysUnsigned() {
		// 6,400,000 / 64 = 100,000 Pa; the top bit of pressure is magnitude, not sign.
		CalibDetailsBmp581 c = new CalibDetailsBmp581("test");
		assertEquals(100000.0, c.calibratePressureSensorData(6400000, 0)[0], 0);
		assertEquals(0xFFFFFF / 64.0, c.calibratePressureSensorData(0xFFFFFF, 0)[0], 0);
	}
}
