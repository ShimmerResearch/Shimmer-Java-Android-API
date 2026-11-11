package com.shimmerresearch.driver;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Shimmer3;
import com.shimmerresearch.driver.shimmer2r3.ConfigByteLayoutShimmer3;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilParseData;

/**
 * DEV-1451: a Shimmer3's alternative accelerometer (channel IDs 0x14-0x16) and
 * alternative magnetometer (0x17-0x19). The firmware sends 6 bytes for each
 * (shimmer3-firmware i2c.c, I2C_configureChannels): the MPU9x50/ICM-20948
 * accel as big-endian int16, from ACCEL_XOUT_H, and its AK8975/AK8963/AK09916
 * as little-endian int16, from HXL. Before DEV-1451 only a Shimmer3R gave
 * these IDs any bytes, so a Shimmer3 stream with either sensor on was 6 bytes
 * short per sensor and mis-framed from the first packet.
 * <p>
 * The packets here are laid out as the firmware packs them on an ICM-20948
 * board, where the alternative accel carries the same chip reading as the
 * wide-range accel (raw big-endian there, byte-swapped to little-endian with Y
 * negated for the wide-range channels) and the alternative mag the same
 * reading as the mag (Z negated there). So each pair checks the other's byte
 * order.
 * <p>
 * No hardware.
 *
 * @author Mark Nolan
 */
public class API_00042_Shimmer3AltAccelMagChannelsTest {

	private static final ShimmerVerObject S3 = new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 1, 9);
	private static final ShimmerVerObject S3R = new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 25);

	private static final byte[] LN_ACCEL = {0x00, 0x01, 0x02};
	private static final byte VBATT = 0x03;
	private static final byte[] WR_ACCEL = {0x04, 0x05, 0x06};
	private static final byte[] MAG = {0x07, 0x08, 0x09};
	private static final byte[] GYRO = {0x0A, 0x0B, 0x0C};
	private static final byte[] ALT_ACCEL = {0x14, 0x15, 0x16};
	private static final byte[] ALT_MAG = {0x17, 0x18, 0x19};
	private static final byte BMP_TEMPERATURE = 0x1A;
	private static final byte BMP_PRESSURE = 0x1B;

	private static final String[] ACCEL_MPU = {
			Shimmer3.ObjectClusterSensorName.ACCEL_MPU_X,
			Shimmer3.ObjectClusterSensorName.ACCEL_MPU_Y,
			Shimmer3.ObjectClusterSensorName.ACCEL_MPU_Z};
	private static final String[] MAG_MPU = {
			Shimmer3.ObjectClusterSensorName.MAG_MPU_X,
			Shimmer3.ObjectClusterSensorName.MAG_MPU_Y,
			Shimmer3.ObjectClusterSensorName.MAG_MPU_Z};

	/** One ICM-20948 accel reading, X Y Z, and one AK09916 reading */
	private static final int[] ACCEL_READING = {1234, -1234, 16384};
	private static final int[] MAG_READING = {291, -240, 512};

	/** The smallest concrete ShimmerObject: decoding, no connection */
	private static class TestShimmer extends ShimmerObject {
		private static final long serialVersionUID = 1L;

		TestShimmer(ShimmerVerObject svo) {
			setShimmerVersionObjectAndCreateSensorMap(svo);
			setSamplingRateShimmer(51.2);
			updateTimestampByteLength();
		}

		@Override
		public void createConfigBytesLayout() {
			mConfigByteLayout = new ConfigByteLayoutShimmer3(getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal(), mShimmerVerObject.mHardwareVersion);
		}

		@Override
		protected void checkBattery() {
		}

		@Override
		public ShimmerDevice deepClone() {
			return null;
		}

		@Override
		protected void interpretDataPacketFormat(Object object, COMMUNICATION_TYPE commType) {
		}

		@Override
		protected void dataHandler(ObjectCluster ojc) {
		}

		@Override
		protected void processMsgFromCallback(ShimmerMsg shimmerMSG) {
		}

		/** A Shimmer3 INQUIRY_RESPONSE, after its response byte: 51.2 Hz, then the channel list */
		void inquiry(byte... channelIds) {
			byte[] response = new byte[8 + channelIds.length];
			response[0] = (byte) 0x80; // 32768 / 640 = 51.2 Hz
			response[1] = (byte) 0x02;
			response[6] = (byte) channelIds.length;
			response[7] = 1;
			System.arraycopy(channelIds, 0, response, 8, channelIds.length);
			interpretInqResponse(response);
		}

		/** A channel list as a Shimmer3R inquiry response or SD header gives it */
		void channels(byte... channelIds) {
			mNChannels = channelIds.length;
			interpretDataPacketFormat(channelIds.length, channelIds);
		}

		ObjectCluster decode(byte[] packet) {
			return buildMsg(packet, COMMUNICATION_TYPE.BLUETOOTH, false, 0);
		}
	}

	private static byte[] concat(byte[]... parts) {
		int length = 0;
		for (byte[] part : parts) {
			length += part.length;
		}
		byte[] all = new byte[length];
		int index = 0;
		for (byte[] part : parts) {
			System.arraycopy(part, 0, all, index, part.length);
			index += part.length;
		}
		return all;
	}

	private static byte[] le16(int... values) {
		byte[] bytes = new byte[2 * values.length];
		for (int i = 0; i < values.length; i++) {
			bytes[2 * i] = (byte) values[i];
			bytes[2 * i + 1] = (byte) (values[i] >> 8);
		}
		return bytes;
	}

	private static byte[] be16(int... values) {
		byte[] bytes = new byte[2 * values.length];
		for (int i = 0; i < values.length; i++) {
			bytes[2 * i] = (byte) (values[i] >> 8);
			bytes[2 * i + 1] = (byte) values[i];
		}
		return bytes;
	}

	/** The 3-byte timestamp a Shimmer3 LogAndStream packet starts with, after its packet type */
	private static final byte[] TIMESTAMP = {0x10, 0x20, 0x30};

	private static final byte[] LN_ACCEL_BYTES = le16(2048, 2047, 2049);
	private static final byte[] VBATT_BYTES = le16(2700);
	private static final byte[] GYRO_BYTES = be16(100, -100, 0);
	/** The firmware's wide-range copy of the ICM-20948 accel: little-endian, Y negated */
	private static final byte[] WR_ACCEL_BYTES = le16(ACCEL_READING[0], -ACCEL_READING[1], ACCEL_READING[2]);
	/** And its mag copy: little-endian, Z negated */
	private static final byte[] MAG_BYTES = le16(MAG_READING[0], MAG_READING[1], -MAG_READING[2]);
	private static final byte[] ALT_ACCEL_BYTES = be16(ACCEL_READING);
	private static final byte[] ALT_MAG_BYTES = le16(MAG_READING);

	private static final byte[] FULL_CHANNELS = concat(LN_ACCEL, new byte[] {VBATT}, GYRO, WR_ACCEL, MAG, ALT_ACCEL, ALT_MAG);
	private static final byte[] FULL_PACKET = concat(TIMESTAMP, LN_ACCEL_BYTES, VBATT_BYTES, GYRO_BYTES, WR_ACCEL_BYTES, MAG_BYTES, ALT_ACCEL_BYTES, ALT_MAG_BYTES);

	private static double uncal(ObjectCluster objectCluster, String signalName) {
		FormatCluster formatCluster = objectCluster.getLastFormatCluster(signalName, CHANNEL_TYPE.UNCAL.toString());
		assertNotNull(signalName + " UNCAL missing", formatCluster);
		assertEquals(CHANNEL_UNITS.NO_UNITS, formatCluster.mUnits);
		return formatCluster.mData;
	}

	// ---------- Packet layout ----------

	/** 6 bytes each, as the firmware sends them: 38 bytes of channels here, not 26 */
	@Test
	public void packetSizeCountsBothSensors() {
		TestShimmer shimmer = new TestShimmer(S3);
		shimmer.inquiry(FULL_CHANNELS);
		assertEquals(3, shimmer.mTimeStampPacketByteSize);
		assertEquals(3 + 38, shimmer.mPacketSize);
		assertEquals(FULL_PACKET.length, shimmer.mPacketSize);
	}

	@Test
	public void eachSensorOnItsOwn() {
		TestShimmer shimmer = new TestShimmer(S3);
		shimmer.inquiry(concat(LN_ACCEL, ALT_ACCEL));
		assertEquals(3 + 12, shimmer.mPacketSize);

		shimmer = new TestShimmer(S3);
		shimmer.inquiry(concat(LN_ACCEL, ALT_MAG));
		assertEquals(3 + 12, shimmer.mPacketSize);
	}

	/** The names and types a Shimmer3 SD file already gives these sensors in ShimmerSDLog */
	@Test
	public void namesAndTypes() {
		TestShimmer shimmer = new TestShimmer(S3);
		shimmer.inquiry(FULL_CHANNELS);
		// mSignalNameArray[0] is the timestamp; the 13 channels before these sit at 1-13
		for (int axis = 0; axis < 3; axis++) {
			assertSame(ACCEL_MPU[axis], shimmer.mSignalNameArray[14 + axis]);
			assertEquals("i16r", shimmer.mSignalDataTypeArray[14 + axis]);
			assertSame(MAG_MPU[axis], shimmer.mSignalNameArray[17 + axis]);
			assertEquals("i16", shimmer.mSignalDataTypeArray[17 + axis]);
		}
	}

	/** A channel after the two sensors is still where the channel list says: the BMP280 pressure */
	@Test
	public void channelsAfterThemStayInPlace() {
		TestShimmer shimmer = new TestShimmer(S3);
		shimmer.inquiry(concat(FULL_CHANNELS, new byte[] {BMP_TEMPERATURE, BMP_PRESSURE}));
		assertEquals(3 + 38 + 5, shimmer.mPacketSize);

		byte[] packet = concat(FULL_PACKET, new byte[] {0x6A, (byte) 0xBC, 0x12, 0x34, 0x56});
		long[] parsed = UtilParseData.parseData(packet, shimmer.mSignalDataTypeArray);
		assertEquals(0x6ABC, parsed[20]);
		assertEquals(0x123456, parsed[21]);
	}

	// ---------- Decoding ----------

	@Test
	public void decodedUncalibrated() {
		TestShimmer shimmer = new TestShimmer(S3);
		shimmer.inquiry(FULL_CHANNELS);
		ObjectCluster objectCluster = shimmer.decode(FULL_PACKET);

		for (int axis = 0; axis < 3; axis++) {
			assertEquals(ACCEL_READING[axis], uncal(objectCluster, ACCEL_MPU[axis]), 0);
			assertEquals(MAG_READING[axis], uncal(objectCluster, MAG_MPU[axis]), 0);
			// Nothing calibrates them on a Shimmer3
			assertNull(objectCluster.getLastFormatCluster(ACCEL_MPU[axis], CHANNEL_TYPE.CAL.toString()));
			assertNull(objectCluster.getLastFormatCluster(MAG_MPU[axis], CHANNEL_TYPE.CAL.toString()));
		}
	}

	/** The same ICM-20948 reading through both of its byte orders, so each checks the other */
	@Test
	public void agreesWithTheWideRangeCopyOfTheSameReading() {
		TestShimmer shimmer = new TestShimmer(S3);
		shimmer.inquiry(FULL_CHANNELS);
		ObjectCluster objectCluster = shimmer.decode(FULL_PACKET);

		assertEquals(uncal(objectCluster, Shimmer3.ObjectClusterSensorName.ACCEL_WR_X), uncal(objectCluster, ACCEL_MPU[0]), 0);
		assertEquals(-uncal(objectCluster, Shimmer3.ObjectClusterSensorName.ACCEL_WR_Y), uncal(objectCluster, ACCEL_MPU[1]), 0);
		assertEquals(uncal(objectCluster, Shimmer3.ObjectClusterSensorName.ACCEL_WR_Z), uncal(objectCluster, ACCEL_MPU[2]), 0);
	}

	/** Wherever the channel list puts them: on their own with no other inertial sensor */
	@Test
	public void decodedOnTheirOwn() {
		TestShimmer shimmer = new TestShimmer(S3);
		shimmer.inquiry(concat(ALT_MAG, new byte[] {VBATT}));
		ObjectCluster objectCluster = shimmer.decode(concat(TIMESTAMP, ALT_MAG_BYTES, VBATT_BYTES));
		for (int axis = 0; axis < 3; axis++) {
			assertEquals(MAG_READING[axis], uncal(objectCluster, MAG_MPU[axis]), 0);
			assertNull(objectCluster.getLastFormatCluster(ACCEL_MPU[axis], CHANNEL_TYPE.UNCAL.toString()));
		}

		shimmer = new TestShimmer(S3);
		shimmer.inquiry(ALT_ACCEL);
		objectCluster = shimmer.decode(concat(TIMESTAMP, ALT_ACCEL_BYTES));
		for (int axis = 0; axis < 3; axis++) {
			assertEquals(ACCEL_READING[axis], uncal(objectCluster, ACCEL_MPU[axis]), 0);
		}
	}

	/**
	 * A stream of packets, each after its DATA_PACKET byte (0x00), comes apart at
	 * mPacketSize + 1 with a 0x00 at every boundary, which is the check
	 * ShimmerBluetooth makes before it takes a packet. Each packet carries a
	 * different alternative accel X, so a packet taken from the wrong place shows.
	 */
	@Test
	public void aStreamFramesAtThePacketSize() {
		TestShimmer shimmer = new TestShimmer(S3);
		shimmer.inquiry(FULL_CHANNELS);
		int frame = shimmer.mPacketSize + 1;

		byte[] stream = new byte[0];
		for (int sample = 0; sample < 4; sample++) {
			byte[] packet = FULL_PACKET.clone();
			packet[0] = (byte) (0x10 + sample);
			System.arraycopy(be16(ACCEL_READING[0] + 100 * sample), 0, packet, 3 + 26, 2);
			stream = concat(stream, new byte[] {0x00}, packet);
		}
		assertEquals(4 * frame, stream.length);

		for (int sample = 0; sample < 4; sample++) {
			assertEquals(0x00, stream[sample * frame]);
			byte[] packet = new byte[shimmer.mPacketSize];
			System.arraycopy(stream, sample * frame + 1, packet, 0, packet.length);
			ObjectCluster objectCluster = shimmer.decode(packet);
			assertEquals(ACCEL_READING[0] + 100 * sample, uncal(objectCluster, ACCEL_MPU[0]), 0);
			assertEquals(MAG_READING[2], uncal(objectCluster, MAG_MPU[2]), 0);
		}
	}

	// ---------- The Shimmer3R is unchanged ----------

	@Test
	public void shimmer3RStillHasItsHighGAccelAndAltMag() {
		TestShimmer shimmer = new TestShimmer(S3R);
		shimmer.channels(concat(ALT_ACCEL, ALT_MAG));
		for (int axis = 0; axis < 3; axis++) {
			assertEquals("i12*>", shimmer.mSignalDataTypeArray[1 + axis]);
			assertEquals("i16", shimmer.mSignalDataTypeArray[4 + axis]);
		}
		assertSame(Shimmer3.ObjectClusterSensorName.ACCEL_HIGHG_X, shimmer.mSignalNameArray[1]);
		assertSame(Shimmer3.ObjectClusterSensorName.ALT_MAG_X, shimmer.mSignalNameArray[4]);
		assertEquals(shimmer.mTimeStampPacketByteSize + 12, shimmer.mPacketSize);
	}
}
