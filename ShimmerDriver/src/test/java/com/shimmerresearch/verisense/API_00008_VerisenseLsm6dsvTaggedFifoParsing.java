package com.shimmerresearch.verisense;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.Test;

import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Verisense;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.verisense.VerisenseDevice.FW_CHANGES;
import com.shimmerresearch.verisense.payloaddesign.DataBlockDetails;
import com.shimmerresearch.verisense.sensors.SensorLSM6DSV;

/**
 * Unit tests for the second-generation LSM6DSV variable-length tagged-FIFO data
 * block parsing in {@link VerisenseDevice} using small synthetic FIFO blocks
 * (no binary test files needed). The block layout under test is
 * {@code [SENSOR_ID=6][3-byte ticks][2-byte NUM_ENTRIES][N x 7-byte entries]}
 * where each entry is {@code [TAG_CNT][X lsb][X msb][Y lsb][Y msb][Z lsb][Z msb]}
 * and the 5-bit tag (TAG_CNT bits 7:3) identifies the stream.
 * <p>
 * End-to-end coverage against real SR68-9 recordings lives in
 * ASM_PC_00005_VerisenseFileParserPC (Test_028-047, ASM_PC repository).
 *
 * @author Mark Nolan
 */
public class API_00008_VerisenseLsm6dsvTaggedFifoParsing {

	private static final int TAG_GYRO = 0x01;
	private static final int TAG_ACCEL = 0x02;
	private static final int TAG_TIMESTAMP = 0x04;
	private static final int TAG_MAG = 0x0E;

	/**
	 * Set up the device exactly as the binary-file parse flow does: by parsing a
	 * synthetic second-generation (payload design v13, 32-byte) payload config
	 * header. This also exercises the gen-2 enabled-sensor mapping (PAYLOAD_CONFIG0
	 * bits 6/5 -> LSM6DSV accel/gyro, GEN_CFG_3 bit 2 -> mag).
	 */
	private VerisenseDevice setupGen2Device(boolean accelEn, boolean gyroEn, boolean magEn) {
		VerisenseDevice device = new VerisenseDevice(COMMUNICATION_TYPE.SD);

		byte[] configBytes = new byte[32];
		configBytes[0] = (byte) (0x10 | (accelEn ? 0x40 : 0) | (gyroEn ? 0x20 : 0)); // extended-config flag + enables
		configBytes[2] = 2;  // FW major
		configBytes[3] = 0;  // FW minor
		configBytes[4] = 9;  // FW internal LSB (v2.00.009)
		configBytes[5] = 0;  // FW internal MSB
		configBytes[6] = (byte) 0xFF; // reset reason
		configBytes[11] = HW_ID.VERISENSE_PULSE_PLUS; // HW major = 68 (SR68)
		configBytes[12] = 9;  // HW minor (SR68-9 = second-generation)
		configBytes[14] = (byte) (accelEn ? 0x15 : 0x10); // LSM6DSV_CFG_0: accel ODR 60 Hz (or off), FS +-4g
		configBytes[15] = (byte) (gyroEn ? 0x25 : 0x20);  // LSM6DSV_CFG_1: gyro ODR 60 Hz (or off), FS +-500dps
		configBytes[16] = 0x01; // LSM6DSV_CFG_2: mag rate 30 Hz
		configBytes[25] = (byte) (0x02 | (magEn ? 0x04 : 0)); // GEN_CFG_3: LED mode + MAG_EN bit 2
		device.configBytesParse(configBytes, COMMUNICATION_TYPE.SD);

		return device;
	}

	private static byte[] entry(int tag, int x, int y, int z) {
		return new byte[] {
				(byte) ((tag & 0x1F) << 3),
				(byte) (x & 0xFF), (byte) ((x >> 8) & 0xFF),
				(byte) (y & 0xFF), (byte) ((y >> 8) & 0xFF),
				(byte) (z & 0xFF), (byte) ((z >> 8) & 0xFF) };
	}

	/** [id=6][ticks(3)][numEntries(2)][entries] */
	private static byte[] buildDataBlock(byte[]... entries) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(6);
		baos.write(0x10); baos.write(0x20); baos.write(0x30); // arbitrary ticks
		baos.write(entries.length & 0xFF);
		baos.write((entries.length >> 8) & 0xFF);
		for (byte[] e : entries) {
			baos.write(e, 0, e.length);
		}
		return baos.toByteArray();
	}

	private DataBlockDetails parseBlock(VerisenseDevice device, byte[] block) throws Exception {
		DataBlockDetails dataBlockDetails = device.parseDataBlockMetaData(block, 0, 0, 0, 0);
		// In the production flow parseDataBlockData is called with the index pointing
		// just past [SENSOR_ID][ticks] (see PayloadContentsDetailsV8orAbove)
		device.parseDataBlockData(dataBlockDetails, block, 4, COMMUNICATION_TYPE.SD);
		return dataBlockDetails;
	}

	private static double uncal(ObjectCluster ojc, String channelName) {
		return ojc.getFormatClusterValue(channelName, CHANNEL_TYPE.UNCAL.toString());
	}

	/** Accel + gyro interleaved 1:1 with a mag sample and a timestamp entry mixed in. */
	@Test
	public void test001_accelGyroMagInterleaved() throws Exception {
		VerisenseDevice device = setupGen2Device(true, true, true);
		byte[] block = buildDataBlock(
				entry(TAG_TIMESTAMP, 0, 0, 0),     // must be skipped
				entry(TAG_GYRO, 10, -20, 30),
				entry(TAG_ACCEL, 100, -200, 300),
				entry(TAG_MAG, -150, 5, 150),
				entry(TAG_GYRO, 11, -21, 31),
				entry(TAG_ACCEL, 101, -201, 301));
		DataBlockDetails dataBlockDetails = parseBlock(device, block);

		// sample count follows the aligned accel stream; OJC array = aligned + mag
		assertEquals(2, dataBlockDetails.getSampleCount());
		assertEquals(3, dataBlockDetails.getOjcArray().length);

		// aligned OJCs carry accel+gyro but NO mag channels (no cross-pollution)
		ObjectCluster aligned0 = dataBlockDetails.getOjcArray()[0];
		assertEquals(100, uncal(aligned0, SensorLSM6DSV.ObjectClusterSensorName.LSM6DSV_ACC_X), 0.001);
		assertEquals(-200, uncal(aligned0, SensorLSM6DSV.ObjectClusterSensorName.LSM6DSV_ACC_Y), 0.001);
		assertEquals(300, uncal(aligned0, SensorLSM6DSV.ObjectClusterSensorName.LSM6DSV_ACC_Z), 0.001);
		assertEquals(10, uncal(aligned0, SensorLSM6DSV.ObjectClusterSensorName.LSM6DSV_GYRO_X), 0.001);
		assertTrue(Double.isNaN(uncal(aligned0, SensorLSM6DSV.ObjectClusterSensorName.LIS2MDL_MAG_X)));

		ObjectCluster aligned1 = dataBlockDetails.getOjcArray()[1];
		assertEquals(101, uncal(aligned1, SensorLSM6DSV.ObjectClusterSensorName.LSM6DSV_ACC_X), 0.001);
		assertEquals(11, uncal(aligned1, SensorLSM6DSV.ObjectClusterSensorName.LSM6DSV_GYRO_X), 0.001);

		// the mag OJC carries mag but NO accel/gyro channels
		ObjectCluster magOjc = dataBlockDetails.getOjcArray()[2];
		assertEquals(-150, uncal(magOjc, SensorLSM6DSV.ObjectClusterSensorName.LIS2MDL_MAG_X), 0.001);
		assertEquals(5, uncal(magOjc, SensorLSM6DSV.ObjectClusterSensorName.LIS2MDL_MAG_Y), 0.001);
		assertEquals(150, uncal(magOjc, SensorLSM6DSV.ObjectClusterSensorName.LIS2MDL_MAG_Z), 0.001);
		assertTrue(Double.isNaN(uncal(magOjc, SensorLSM6DSV.ObjectClusterSensorName.LSM6DSV_ACC_X)));
		assertTrue(Double.isNaN(uncal(magOjc, SensorLSM6DSV.ObjectClusterSensorName.LSM6DSV_GYRO_X)));
	}

	/** Gyro-only: gyro acts as the aligned reference stream. */
	@Test
	public void test002_gyroOnly() throws Exception {
		VerisenseDevice device = setupGen2Device(false, true, false);
		byte[] block = buildDataBlock(
				entry(TAG_GYRO, 1, 2, 3),
				entry(TAG_TIMESTAMP, 0, 0, 0),
				entry(TAG_GYRO, 4, 5, 6),
				entry(TAG_GYRO, 7, 8, 9));
		DataBlockDetails dataBlockDetails = parseBlock(device, block);

		assertEquals(3, dataBlockDetails.getSampleCount());
		assertEquals(3, dataBlockDetails.getOjcArray().length);
		assertEquals(4, uncal(dataBlockDetails.getOjcArray()[1], SensorLSM6DSV.ObjectClusterSensorName.LSM6DSV_GYRO_X), 0.001);
	}

	/**
	 * Mag-only enable configuration. Not currently producible by the firmware (the
	 * LSM6DSV sensor hub requires accel and/or gyro to be running) but expressible
	 * in the payload header, so the parser handles it defensively: the mag stream
	 * acts as the reference for the sample count and all samples are emitted.
	 */
	@Test
	public void test003_magOnly() throws Exception {
		VerisenseDevice device = setupGen2Device(false, false, true);
		byte[] block = buildDataBlock(
				entry(TAG_MAG, -100, 0, 100),
				entry(TAG_MAG, -101, 1, 101));
		DataBlockDetails dataBlockDetails = parseBlock(device, block);

		assertEquals(2, dataBlockDetails.getSampleCount());
		assertEquals(2, dataBlockDetails.getOjcArray().length);
		assertEquals(-100, uncal(dataBlockDetails.getOjcArray()[0], SensorLSM6DSV.ObjectClusterSensorName.LIS2MDL_MAG_X), 0.001);
		assertEquals(-101, uncal(dataBlockDetails.getOjcArray()[1], SensorLSM6DSV.ObjectClusterSensorName.LIS2MDL_MAG_X), 0.001);
	}

	/** The per-stream enable toggling inside the parser must be restored afterwards. */
	@Test
	public void test004_enableStateRestoredAfterParse() throws Exception {
		VerisenseDevice device = setupGen2Device(true, true, true);
		byte[] block = buildDataBlock(
				entry(TAG_ACCEL, 1, 2, 3),
				entry(TAG_GYRO, 4, 5, 6),
				entry(TAG_MAG, 7, 8, 9));
		parseBlock(device, block);

		assertTrue(device.isSensorEnabled(Verisense.SENSOR_ID.LSM6DSV_ACCEL));
		assertTrue(device.isSensorEnabled(Verisense.SENSOR_ID.LSM6DSV_GYRO));
		assertTrue(device.isSensorEnabled(Verisense.SENSOR_ID.LSM6DSV_MAG));

		// and a disabled stream stays disabled
		VerisenseDevice deviceAccelOnly = setupGen2Device(true, false, false);
		parseBlock(deviceAccelOnly, buildDataBlock(entry(TAG_ACCEL, 1, 2, 3)));
		assertTrue(deviceAccelOnly.isSensorEnabled(Verisense.SENSOR_ID.LSM6DSV_ACCEL));
		assertFalse(deviceAccelOnly.isSensorEnabled(Verisense.SENSOR_ID.LSM6DSV_GYRO));
		assertFalse(deviceAccelOnly.isSensorEnabled(Verisense.SENSOR_ID.LSM6DSV_MAG));
	}

}
