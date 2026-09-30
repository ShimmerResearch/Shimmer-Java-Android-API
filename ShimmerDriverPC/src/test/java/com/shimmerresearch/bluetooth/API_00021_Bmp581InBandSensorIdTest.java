package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import org.junit.Test;

import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driver.calibration.CalibDetails.CALIB_READ_SOURCE;
import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID_SR_CODES;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.pcDriver.ShimmerPC;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;
import com.shimmerresearch.sensors.bmpX80.SensorBMP390;
import com.shimmerresearch.sensors.bmpX80.SensorBMPX80;
import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortTimeoutException;

/**
 * DEV-1110: the Shimmer3R's answer to GET_PRESSURE_CALIBRATION_COEFFICIENTS_COMMAND
 * (0xA7) names the fitted pressure sensor, [0xA6][len = 1+n][sensorId][n bytes],
 * and that answer overrides the SR-number rule in either direction. Before
 * DEV-1110 the driver read a fixed length chosen from the sensor it had assumed,
 * so a BMP581's 3-byte answer was read as a 23-byte BMP390 one, and it never
 * sent 0xA7 to a board the SR-number rule marked as a BMP581.
 * <p>
 * No hardware: the response handler reads from an in-memory byte queue. It lives
 * in this package because the handler and the instruction stack are protected
 * members of ShimmerBluetooth.
 *
 * @author Mark Nolan
 */
public class API_00021_Bmp581InBandSensorIdTest {

	/** Coefficients of a real BMP390 (ByteCommunicationSimulatorS3R.getPressureResoTest()) */
	private static final byte[] BMP390_COEFFICIENTS = {
			(byte) 0xE7, (byte) 0x6B, (byte) 0xF0, (byte) 0x4A, (byte) 0xF9,
			(byte) 0xAB, (byte) 0x1C, (byte) 0x9B, (byte) 0x15, (byte) 0x06,
			(byte) 0x01, (byte) 0xD2, (byte) 0x49, (byte) 0x18, (byte) 0x5F,
			(byte) 0x03, (byte) 0xFA, (byte) 0x3A, (byte) 0x0F, (byte) 0x07,
			(byte) 0xF5
	};

	/** Bytes following the 0xA6 response byte */
	private static byte[] response(int sensorId, byte[] coefficients) {
		byte[] bytes = new byte[2 + coefficients.length];
		bytes[0] = (byte) (1 + coefficients.length);
		bytes[1] = (byte) sensorId;
		System.arraycopy(coefficients, 0, bytes, 2, coefficients.length);
		return bytes;
	}

	private static class QueueRadio implements ByteCommunication {
		final Deque<Byte> rx = new ArrayDeque<Byte>();

		void queue(byte... bytes) {
			for(byte b:bytes){
				rx.add(b);
			}
		}

		@Override
		public byte[] readBytes(int byteCount, int timeout) throws SerialPortTimeoutException {
			if(rx.size()<byteCount){
				throw new SerialPortTimeoutException("TEST", "readBytes", timeout);
			}
			byte[] out = new byte[byteCount];
			for(int i=0;i<byteCount;i++){
				out[i] = rx.poll();
			}
			return out;
		}

		@Override public int getInputBufferBytesCount() { return rx.size(); }
		@Override public boolean isOpened() { return true; }
		@Override public boolean closePort() { return true; }
		@Override public boolean openPort() { return true; }
		@Override public boolean writeBytes(byte[] buffer) { return true; }
		@Override public boolean setParams(int i, int j, int k, int l) { return true; }
		@Override public boolean purgePort(int i) { return true; }
		@Override public void setByteCommunicationListener(ByteCommunicationListener byteCommListener) { }
		@Override public void removeRadioListenerList() { }
	}

	private QueueRadio mRadio;

	private ShimmerPC shimmer3r(int fwMajor, int fwMinor, int fwInternal, int srId, int rev, int revSpecial) {
		ShimmerPC device = new ShimmerPC("COM99");
		mRadio = new QueueRadio();
		device.setTestRadio(mRadio);
		device.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, fwMajor, fwMinor, fwInternal));
		device.setExpansionBoardDetailsAndCreateSensorMap(new ExpansionBoardDetails(srId, rev, revSpecial));
		return device;
	}

	/** SR48-8-0 is a BMP390 board by the SR-number rule */
	private ShimmerPC sr48_8_0() {
		return shimmer3r(1, 1, 7, HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 0);
	}

	/** SR48-8-2 is a BMP581 board by the SR-number rule */
	private ShimmerPC sr48_8_2() {
		return shimmer3r(1, 1, 7, HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 2);
	}

	private void receive(ShimmerPC device, byte[] bytes) {
		mRadio.queue(bytes);
		device.processPressureCalibCoefficientsResponse();
		assertEquals("every byte of the response is consumed, and no more", 0, mRadio.rx.size());
	}

	@Test
	public void bmp581ReportedOnBmp390BoardSwitchesToBmp581() {
		ShimmerPC device = sr48_8_0();
		assertEquals(SENSORS.BMP390, device.mSensorBMPX80.mSensorType);

		receive(device, new byte[] {0x01, 0x03}); // [A6][01][03]

		assertEquals(Integer.valueOf(ShimmerObject.PRESSURE_SENSOR_ID.BMP581), device.getPressureSensorIdInBand());
		assertTrue(device.isSupportedBmp581());
		assertEquals(SENSORS.BMP581, device.mSensorBMPX80.mSensorType);
		assertSame(device.mSensorBMPX80, device.getSensorClass(SENSORS.BMP581));
		assertNull(device.getSensorClass(SENSORS.BMP390));
	}

	@Test
	public void bmp390ReportedOnBmp581BoardSwitchesToBmp390AndAppliesCoefficients() {
		ShimmerPC device = sr48_8_2();
		assertEquals(SENSORS.BMP581, device.mSensorBMPX80.mSensorType);

		receive(device, response(ShimmerObject.PRESSURE_SENSOR_ID.BMP390, BMP390_COEFFICIENTS));

		assertFalse(device.isSupportedBmp581());
		assertEquals(SENSORS.BMP390, device.mSensorBMPX80.mSensorType);
		assertBmp390CoefficientsApplied(device.mSensorBMPX80);
	}

	@Test
	public void bmp390ReportedOnBmp390BoardIsUnchanged() {
		// The pre-DEV-1110 path: same sensor class, same coefficient bytes
		ShimmerPC device = sr48_8_0();
		SensorBMPX80 before = device.mSensorBMPX80;

		receive(device, response(ShimmerObject.PRESSURE_SENSOR_ID.BMP390, BMP390_COEFFICIENTS));

		assertSame("no rebuild when the rule was right", before, device.mSensorBMPX80);
		assertBmp390CoefficientsApplied(device.mSensorBMPX80);
	}

	@Test
	public void bmp581ReportedOnBmp581BoardIsUnchanged() {
		ShimmerPC device = sr48_8_2();
		SensorBMPX80 before = device.mSensorBMPX80;

		receive(device, new byte[] {0x01, 0x03});

		assertSame("no rebuild when the rule was right", before, device.mSensorBMPX80);
		assertTrue(device.isSupportedBmp581());
	}

	private static void assertBmp390CoefficientsApplied(SensorBMPX80 sensor) {
		assertArrayEquals(BMP390_COEFFICIENTS, sensor.mCalibDetailsBmpX80.getPressureRawCoefficients());

		// What the pre-DEV-1110 handler did: the bytes after [len][id], straight into a BMP390
		ShimmerPC referenceDevice = new ShimmerPC("COM98");
		referenceDevice.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 7));
		referenceDevice.setExpansionBoardDetailsAndCreateSensorMap(new ExpansionBoardDetails(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 0));
		SensorBMPX80 reference = referenceDevice.mSensorBMPX80;
		assertTrue(reference instanceof SensorBMP390);
		reference.parseCalParamByteArray(BMP390_COEFFICIENTS, CALIB_READ_SOURCE.LEGACY_BT_COMMAND);
		// Raw values and expected output from API_0000X_ByteCommunicationShimmer3R test002
		double uP = 0x641700, uT = 0x7FCF00;
		double[] calibrated = sensor.calibratePressureSensorData(uP, uT);
		assertArrayEquals(reference.calibratePressureSensorData(uP, uT), calibrated, 0);
		assertEquals(100912.8176, calibrated[0], 0.0001);
		assertEquals(23.2659, calibrated[1], 0.0001);
	}

	@Test
	public void lengthThatDoesNotMatchTheSensorIdIsRejected() {
		// [A6 04 03 xx xx xx]: a BMP581 has no coefficients
		ShimmerPC device = sr48_8_0();
		SensorBMPX80 before = device.mSensorBMPX80;
		receive(device, new byte[] {0x04, 0x03, 0x11, 0x22, 0x33});
		assertNull(device.getPressureSensorIdInBand());
		assertSame(before, device.mSensorBMPX80);
		assertFalse(device.isSupportedBmp581());

		// A BMP390 without its 21 coefficient bytes
		device = sr48_8_2();
		receive(device, new byte[] {0x01, 0x02});
		assertNull(device.getPressureSensorIdInBand());
		assertTrue("the SR-number rule stands", device.isSupportedBmp581());

		// An unknown sensor ID
		device = sr48_8_2();
		receive(device, new byte[] {0x01, 0x07});
		assertNull(device.getPressureSensorIdInBand());

		// A zero length carries no sensor ID
		device = sr48_8_2();
		receive(device, new byte[] {0x00});
		assertNull(device.getPressureSensorIdInBand());
	}

	@Test
	public void shimmer3rRejectsAShimmer3OnlySensor() {
		// A well-formed BMP280 answer (ID 1, 24 bytes) cannot come from a Shimmer3R
		ShimmerPC device = sr48_8_0();
		SensorBMPX80 before = device.mSensorBMPX80;
		receive(device, response(ShimmerObject.PRESSURE_SENSOR_ID.BMP280, new byte[24]));
		assertNull(device.getPressureSensorIdInBand());
		assertSame(before, device.mSensorBMPX80);
	}

	@Test
	public void newExpansionBoardClearsTheInBandId() {
		ShimmerPC device = sr48_8_0();
		receive(device, new byte[] {0x01, 0x03});
		assertTrue(device.isSupportedBmp581());

		device.setExpansionBoardDetails(new ExpansionBoardDetails(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 0));
		assertNull(device.getPressureSensorIdInBand());
		assertFalse("back to the SR-number rule", device.isSupportedBmp581());
	}

	@Test
	public void switchKeepsSettingsFromTheConfigBytes() {
		// The config bytes arrive before 0xA6 while connecting, so they were
		// parsed into the BMP390 class; the BMP581 class must pick them up too.
		// Oversampling 7 exists only on the BMP581 (3 bits, BMP390 is 0..5).
		ShimmerPC source = sr48_8_2();
		source.setPressureResolution(7);
		assertEquals(7, source.getPressureResolution());
		byte[] configBytes = source.configBytesGenerate(false);

		ShimmerPC device = sr48_8_0();
		device.configBytesParse(configBytes);
		receive(device, new byte[] {0x01, 0x03});

		assertEquals(SENSORS.BMP581, device.mSensorBMPX80.mSensorType);
		assertEquals(7, device.getPressureResolution());
	}

	private static boolean isGetPressureCalibQueued(ShimmerPC device) {
		device.readPressureCalibrationCoefficients();
		for(byte[] instruction:device.getListofInstructions()){
			if(instruction!=null && instruction[0]==ShimmerObject.GET_PRESSURE_CALIBRATION_COEFFICIENTS_COMMAND){
				return true;
			}
		}
		return false;
	}

	@Test
	public void getPressureCalibIsSentExceptToV1_01_006OnABmp581Board() {
		// v1.01.006 NACKs 0xA7 on a BMP581 and the driver has no NACK handling,
		// so the SR-number rule is left to stand there
		assertFalse(isGetPressureCalibQueued(shimmer3r(1, 1, 6, HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 2)));
		assertTrue(isGetPressureCalibQueued(shimmer3r(1, 1, 6, HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 0)));
		// v1.01.007+ answers in-band, whatever the rule says
		assertTrue(isGetPressureCalibQueued(shimmer3r(1, 1, 7, HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 2)));
		assertTrue(isGetPressureCalibQueued(shimmer3r(1, 2, 0, HW_ID_SR_CODES.EXP_BRD_PROTO3_DELUXE, 4, 2)));
		assertTrue(isGetPressureCalibQueued(shimmer3r(1, 1, 7, HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 0)));
		// Before v1.01.006 there is no BMP581 support, and 0xA7 was always sent
		assertTrue(isGetPressureCalibQueued(shimmer3r(1, 1, 5, HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 2)));
	}
}
