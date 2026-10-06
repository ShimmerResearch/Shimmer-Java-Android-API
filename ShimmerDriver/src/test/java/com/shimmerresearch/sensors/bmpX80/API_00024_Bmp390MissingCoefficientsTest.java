package com.shimmerresearch.sensors.bmpX80;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;

import com.shimmerresearch.driver.calibration.CalibDetails.CALIB_READ_SOURCE;

/**
 * BMP390 calibration with no coefficients must report "not calibrated" (NaN)
 * rather than throw. {@link CalibDetailsBmp390#parseCalParamByteArray} skips an
 * all-0x00 or all-0xFF block - an unwritten SD header, for instance - and until
 * DEV-1125 the next calibration dereferenced the missing coefficients and threw
 * a NullPointerException, aborting the whole SD import.
 *
 * The coefficients live in a static map keyed by MAC, so each case uses its own
 * MAC to stay independent of the others.
 *
 * @author Mark Nolan
 */
public class API_00024_Bmp390MissingCoefficientsTest {

	/** Raw ADC values in a plausible indoor range. */
	private static final double UP = 6600000;
	private static final double UT = 8400000;

	/** A real BMP390 trim block (the vector CalibDetailsBmp390.main() uses). */
	private static final byte[] VALID_BLOCK = {
			(byte) 0xE7, (byte) 0x6B, (byte) 0xF0, (byte) 0x4A, (byte) 0xF9,
			(byte) 0xAB, (byte) 0x1C, (byte) 0x9B, (byte) 0x15, (byte) 0x06,
			(byte) 0x01, (byte) 0xD2, (byte) 0x49, (byte) 0x18, (byte) 0x5F,
			(byte) 0x03, (byte) 0xFA, (byte) 0x3A, (byte) 0x0F, (byte) 0x07,
			(byte) 0xF5 };

	private static double[] calibrateAfterParsing(String mac, byte[] block) {
		CalibDetailsBmp390 calib = new CalibDetailsBmp390(mac);
		if (block != null) {
			calib.parseCalParamByteArray(block, CALIB_READ_SOURCE.SD_HEADER);
		}
		return calib.calibratePressureSensorData(UP, UT);
	}

	private static void assertNotCalibrated(double[] caldata) {
		assertEquals(2, caldata.length);
		assertTrue("pressure should be NaN, was " + caldata[0], Double.isNaN(caldata[0]));
		assertTrue("temperature should be NaN, was " + caldata[1], Double.isNaN(caldata[1]));
	}

	@Test
	public void allZeroBlockIsNotCalibratedRatherThanThrowing() {
		assertNotCalibrated(calibrateAfterParsing("dev1125-all-zero", new byte[21]));
	}

	@Test
	public void allFfBlockIsNotCalibratedRatherThanThrowing() {
		byte[] blank = new byte[21];
		Arrays.fill(blank, (byte) 0xFF);
		assertNotCalibrated(calibrateAfterParsing("dev1125-all-ff", blank));
	}

	@Test
	public void calibratingBeforeAnyCoefficientsIsNotCalibrated() {
		// Streaming can sample before the 0xA7 coefficient reply arrives.
		assertNotCalibrated(calibrateAfterParsing("dev1125-never-parsed", null));
	}

	@Test
	public void validBlockStillCalibrates() {
		double[] caldata = calibrateAfterParsing("dev1125-valid", VALID_BLOCK);
		assertFalse(Double.isNaN(caldata[0]));
		assertFalse(Double.isNaN(caldata[1]));
		// Within the BMP390's own clamp range (Pa, degC).
		assertTrue(caldata[0] >= CalibDetailsBmp390.Bmp3Constants.BMP3_MIN_PRES_DOUBLE
				&& caldata[0] <= CalibDetailsBmp390.Bmp3Constants.BMP3_MAX_PRES_DOUBLE);
		assertTrue(caldata[1] >= CalibDetailsBmp390.Bmp3Constants.BMP3_MIN_TEMP_DOUBLE
				&& caldata[1] <= CalibDetailsBmp390.Bmp3Constants.BMP3_MAX_TEMP_DOUBLE);
	}

	@Test
	public void blankBlockDoesNotEraseEarlierCoefficients() {
		// A later blank block is skipped, so coefficients already parsed for that
		// sensor keep working - the existing behaviour, pinned here.
		String mac = "dev1125-valid-then-blank";
		CalibDetailsBmp390 calib = new CalibDetailsBmp390(mac);
		calib.parseCalParamByteArray(VALID_BLOCK, CALIB_READ_SOURCE.SD_HEADER);
		calib.parseCalParamByteArray(new byte[21], CALIB_READ_SOURCE.INFOMEM);
		assertFalse(Double.isNaN(calib.calibratePressureSensorData(UP, UT)[0]));
	}
}
