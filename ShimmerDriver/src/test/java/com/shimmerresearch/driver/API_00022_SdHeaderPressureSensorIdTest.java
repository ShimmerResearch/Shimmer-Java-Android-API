package com.shimmerresearch.driver;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shimmerresearch.driver.ShimmerObject.PRESSURE_SENSOR_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.sensors.bmpX80.SdHeaderPressureSensorId;
import com.shimmerresearch.sensors.bmpX80.SdHeaderPressureSensorId.STATE;

/**
 * Decoding of the pressure sensor ID LogAndStream writes at SD header offset
 * 224 (DEV-1123), and the firmware-version gate that decides whether it is
 * trusted. The gate has to check the hardware version, because Shimmer3 and
 * Shimmer3R firmware version numbers overlap. Choosing the sensor class from
 * it, against the SR-number rule, is covered end to end through ShimmerSDLog
 * in Shimmer-Advance-API.
 *
 * @author Mark Nolan
 */
public class API_00022_SdHeaderPressureSensorIdTest {

	private static ShimmerVerObject s3r(int major, int minor, int internal) {
		return new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, major, minor, internal);
	}

	private static ShimmerVerObject s3(int major, int minor, int internal) {
		return new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, major, minor, internal);
	}

	private static final ShimmerVerObject S3R_GATE = s3r(1, 1, 18);
	private static final ShimmerVerObject S3_GATE = s3(1, 1, 6);

	private static void assertKnown(SdHeaderPressureSensorId id, int sensorId, boolean inferred) {
		assertEquals(id.toString(), STATE.KNOWN, id.getState());
		assertTrue(id.isPresent());
		assertTrue(id.isSensor(sensorId));
		assertEquals(sensorId, id.getSensorId());
		assertEquals(inferred, id.isInferred());
		assertFalse(id.isUncalibrated());
	}

	private static void assertAbsent(SdHeaderPressureSensorId id) {
		assertEquals(id.toString(), STATE.ABSENT, id.getState());
		assertFalse(id.isPresent());
		assertFalse(id.isUncalibrated());
		assertFalse(id.isSensor(PRESSURE_SENSOR_ID.BMP581));
	}

	private static void assertUnknown(SdHeaderPressureSensorId id) {
		assertEquals(id.toString(), STATE.UNKNOWN, id.getState());
		assertTrue(id.isPresent());
		assertTrue(id.isUncalibrated());
		for (int sensorId = PRESSURE_SENSOR_ID.BMP180; sensorId <= PRESSURE_SENSOR_ID.BMP581; sensorId++) {
			assertFalse("never treated as sensor " + sensorId, id.isSensor(sensorId));
		}
	}

	@Test
	public void shimmer3rKnownIds() {
		assertKnown(SdHeaderPressureSensorId.parse(S3R_GATE, 0x02), PRESSURE_SENSOR_ID.BMP390, false);
		assertKnown(SdHeaderPressureSensorId.parse(S3R_GATE, 0x03), PRESSURE_SENSOR_ID.BMP581, false);
	}

	@Test
	public void inferredFlag() {
		assertKnown(SdHeaderPressureSensorId.parse(S3R_GATE, 0x83), PRESSURE_SENSOR_ID.BMP581, true);
		assertKnown(SdHeaderPressureSensorId.parse(S3R_GATE, 0x82), PRESSURE_SENSOR_ID.BMP390, true);
	}

	@Test
	public void notRecordedFallsBackToSrRule() {
		assertAbsent(SdHeaderPressureSensorId.parse(S3R_GATE, 0xFF));
		assertAbsent(SdHeaderPressureSensorId.parse(S3_GATE, 0xFF));
	}

	@Test
	public void unknownIdsAreUncalibrated() {
		assertUnknown(SdHeaderPressureSensorId.parse(S3R_GATE, 0x04));
		assertUnknown(SdHeaderPressureSensorId.parse(S3R_GATE, 0x7D));
		// 0x7E-0x7F were never allocated
		assertUnknown(SdHeaderPressureSensorId.parse(S3R_GATE, 0x7E));
		assertUnknown(SdHeaderPressureSensorId.parse(S3R_GATE, 0x7F));

		SdHeaderPressureSensorId inferredUnknown = SdHeaderPressureSensorId.parse(S3R_GATE, 0x84);
		assertUnknown(inferredUnknown);
		assertTrue(inferredUnknown.isInferred());
	}

	/** The parser has no Shimmer3R path for a BMP180/280 or Shimmer3 path for a BMP390/581. */
	@Test
	public void sensorsTheOtherPlatformCarriesAreUnknown() {
		assertUnknown(SdHeaderPressureSensorId.parse(S3R_GATE, 0x00));
		assertUnknown(SdHeaderPressureSensorId.parse(S3R_GATE, 0x01));
		assertUnknown(SdHeaderPressureSensorId.parse(S3_GATE, 0x02));
		assertUnknown(SdHeaderPressureSensorId.parse(S3_GATE, 0x03));
	}

	@Test
	public void shimmer3Ids() {
		assertKnown(SdHeaderPressureSensorId.parse(S3_GATE, 0x00), PRESSURE_SENSOR_ID.BMP180, false);
		assertKnown(SdHeaderPressureSensorId.parse(S3_GATE, 0x01), PRESSURE_SENSOR_ID.BMP280, false);

		SdHeaderPressureSensorId none = SdHeaderPressureSensorId.parse(S3_GATE, 0xFE);
		assertEquals(STATE.NOT_FITTED, none.getState());
		assertTrue(none.isPresent());
		assertTrue("any pressure channels are emitted uncalibrated", none.isUncalibrated());
		assertFalse(none.isSensor(PRESSURE_SENSOR_ID.BMP180));
		assertFalse(none.isSensor(PRESSURE_SENSOR_ID.BMP280));
	}

	@Test
	public void oldFirmwareIsIgnored() {
		assertAbsent(SdHeaderPressureSensorId.parse(s3r(1, 1, 17), 0x03));
		assertAbsent(SdHeaderPressureSensorId.parse(s3(1, 1, 5), 0x01));
		assertAbsent(SdHeaderPressureSensorId.parse(s3(1, 1, 5), 0xFE));
	}

	@Test
	public void gateChecksHardware() {
		// Shimmer3 v1.01.018 is gated on the Shimmer3 threshold, so it passes
		assertTrue(SdHeaderPressureSensorId.isSupported(s3(1, 1, 18)));
		assertKnown(SdHeaderPressureSensorId.parse(s3(1, 1, 18), 0x01), PRESSURE_SENSOR_ID.BMP280, false);

		// Shimmer3R v1.01.006-v1.01.017 would pass the Shimmer3 threshold but must not
		for (int internal = 6; internal <= 17; internal++) {
			assertFalse("S3R v1.01." + internal, SdHeaderPressureSensorId.isSupported(s3r(1, 1, internal)));
			assertAbsent(SdHeaderPressureSensorId.parse(s3r(1, 1, internal), 0x03));
		}
	}

	@Test
	public void gateBoundaries() {
		assertTrue(SdHeaderPressureSensorId.isSupported(s3r(1, 1, 18)));
		assertTrue(SdHeaderPressureSensorId.isSupported(s3r(1, 2, 0)));
		assertTrue(SdHeaderPressureSensorId.isSupported(s3r(2, 0, 0)));
		assertFalse(SdHeaderPressureSensorId.isSupported(s3r(1, 0, 99)));

		assertTrue(SdHeaderPressureSensorId.isSupported(s3(1, 1, 6)));
		assertTrue(SdHeaderPressureSensorId.isSupported(s3(1, 2, 0)));
		assertFalse(SdHeaderPressureSensorId.isSupported(s3(1, 1, 5)));
		assertFalse(SdHeaderPressureSensorId.isSupported(s3(0, 16, 99)));

		// Only LogAndStream writes it; SDLog used 224 for MPL gyro calibration
		assertFalse(SdHeaderPressureSensorId.isSupported(new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.SDLOG, 1, 1, 6)));
		assertAbsent(SdHeaderPressureSensorId.parse(new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.SDLOG, 1, 1, 6), 0x01));
		// Other hardware
		assertFalse(SdHeaderPressureSensorId.isSupported(new ShimmerVerObject(HW_ID.SHIMMER_4_SDK, FW_ID.LOGANDSTREAM, 1, 1, 18)));
		assertFalse(SdHeaderPressureSensorId.isSupported(null));
	}
}
