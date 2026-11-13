package com.shimmerresearch.driver;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;

import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.shimmer2r3.ConfigByteLayoutShimmer3;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilParseData;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;
import com.shimmerresearch.sensors.soundlevel.SensorSoundLevel;

/**
 * DEV-1413: the Shimmer3R sound level channel, 0x38. Its decoding from a data
 * packet, wherever the channel list puts it, and configuration byte 233,
 * whose bit 4 turns it on. A full configuration write builds its image from
 * zeros, so before DEV-1413 it wrote 0 to byte 233 and turned off a
 * Shimmer3R's audio recording (DEV-99) as well; it now gives back bits 0-3 as
 * read. A Shimmer3 has no microphone: it never sends 0x38 and its byte 233
 * stays 0.
 * <p>
 * No hardware.
 *
 * @author Mark Nolan
 */
public class API_00040_SoundLevelChannelTest {

	private static final int IDX_AUDIO_CONFIG = 233;

	/** Shimmer3R LogAndStream v1.01.026, the first release with the channel: every Shimmer3R has the microphone */
	private static final ShimmerVerObject S3R = new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 26);
	/** The release before it, which stores byte 233 untouched */
	private static final ShimmerVerObject S3R_BEFORE_CHANNEL = new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 25);
	private static final ShimmerVerObject S3 = new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 0, 17);

	private static final byte LN_ACCEL_X = 0x00;
	private static final byte LN_ACCEL_Y = 0x01;
	private static final byte LN_ACCEL_Z = 0x02;
	private static final byte VBATT = 0x03;
	private static final byte GSR = 0x1C;
	private static final byte SOUND_LEVEL = 0x38;

	/** The smallest concrete ShimmerObject: decoding and configuration, no connection */
	private static class TestShimmer extends ShimmerObject {
		private static final long serialVersionUID = 1L;

		TestShimmer(ShimmerVerObject svo) {
			setShimmerVersionObjectAndCreateSensorMap(svo);
			setSamplingRateShimmer(128);
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

		byte[] written() {
			return generateConfigBytesForWritingToShimmer().clone();
		}

		boolean isSoundLevelEnabled() {
			return isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL);
		}

		void setSoundLevelEnabled(boolean state) {
			assertTrue(setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL, state));
		}

		/** Takes the channel list as an inquiry response or an SD header gives it */
		void channels(byte... channelIds) {
			mNChannels = channelIds.length;
			interpretDataPacketFormat(channelIds.length, channelIds);
		}
	}

	/** A configuration as a Shimmer on that firmware would answer it, with the given byte 233 */
	private static byte[] configFromShimmer(ShimmerVerObject svo, int audioConfig) {
		byte[] config = new TestShimmer(svo).written();
		config[IDX_AUDIO_CONFIG] = (byte) audioConfig;
		return config;
	}

	private static TestShimmer readFrom(ShimmerVerObject svo, int audioConfig) {
		TestShimmer shimmer = new TestShimmer(svo);
		shimmer.configBytesParse(configFromShimmer(svo, audioConfig), COMMUNICATION_TYPE.BLUETOOTH);
		return shimmer;
	}

	private static int writtenAudioConfig(TestShimmer shimmer) {
		return shimmer.written()[IDX_AUDIO_CONFIG] & 0xFF;
	}

	private static String hex(int value) {
		return String.format("0x%02X", value);
	}

	/** A data packet as buildMsg gets it: the timestamp, then two bytes little-endian per channel */
	private static byte[] packet(TestShimmer shimmer, int... channelValues) {
		byte[] packet = new byte[shimmer.mTimeStampPacketByteSize + 2*channelValues.length];
		packet[0] = 0x10;
		int index = shimmer.mTimeStampPacketByteSize;
		for(int value:channelValues){
			packet[index++] = (byte) (value & 0xFF);
			packet[index++] = (byte) ((value >> 8) & 0xFF);
		}
		return packet;
	}

	private static FormatCluster soundLevel(ObjectCluster objectCluster, CHANNEL_TYPE channelType) {
		return objectCluster.getLastFormatCluster(SensorSoundLevel.ObjectClusterSensorName.SOUND_LEVEL, channelType.toString());
	}

	// ---------- Decoding ----------

	/** Just before the ADS7028 block (GSR, then the battery last), as the firmware sends it */
	@Test
	public void decodedAtTheSpecPosition() {
		TestShimmer shimmer = new TestShimmer(S3R);
		shimmer.channels(LN_ACCEL_X, LN_ACCEL_Y, LN_ACCEL_Z, SOUND_LEVEL, GSR, VBATT);

		assertEquals(shimmer.mTimeStampPacketByteSize + 12, shimmer.mPacketSize);
		assertSame(SensorSoundLevel.ObjectClusterSensorName.SOUND_LEVEL, shimmer.mSignalNameArray[4]);
		assertEquals("i16", shimmer.mSignalDataTypeArray[4]);

		byte[] packet = packet(shimmer, 0x0101, 0x0202, 0x0303, 6543, 0x0456, 0x0123);
		ObjectCluster objectCluster = shimmer.buildMsg(packet, COMMUNICATION_TYPE.BLUETOOTH, false, 0);

		assertEquals(6543, soundLevel(objectCluster, CHANNEL_TYPE.UNCAL).mData, 0);
		FormatCluster cal = soundLevel(objectCluster, CHANNEL_TYPE.CAL);
		assertEquals(65.43, cal.mData, 1e-9);
		assertEquals(CHANNEL_UNITS.DECIBELS_A, cal.mUnits);

		// The channels after it are still where the channel list says
		long[] parsed = UtilParseData.parseData(packet, shimmer.mSignalDataTypeArray);
		assertEquals(0x0303, parsed[3]);
		assertEquals(6543, parsed[4]);
		assertEquals(0x0456, parsed[5]);
		assertEquals(0x0123, parsed[6]);
	}

	/** The decoder goes by the channel list, not by a fixed position */
	@Test
	public void decodedWhereverTheChannelListPutsIt() {
		TestShimmer shimmer = new TestShimmer(S3R);
		shimmer.channels(SOUND_LEVEL, VBATT);
		ObjectCluster objectCluster = shimmer.buildMsg(packet(shimmer, 4012, 0x0123), COMMUNICATION_TYPE.BLUETOOTH, false, 0);
		assertEquals(40.12, soundLevel(objectCluster, CHANNEL_TYPE.CAL).mData, 1e-9);

		shimmer = new TestShimmer(S3R);
		shimmer.channels(VBATT, SOUND_LEVEL);
		objectCluster = shimmer.buildMsg(packet(shimmer, 0x0123, 4012), COMMUNICATION_TYPE.BLUETOOTH, false, 0);
		assertEquals(40.12, soundLevel(objectCluster, CHANNEL_TYPE.CAL).mData, 1e-9);
	}

	/** 0x8000 says there is no level: NaN, never -327.68 */
	@Test
	public void noLevelIsNaN() {
		TestShimmer shimmer = new TestShimmer(S3R);
		shimmer.channels(LN_ACCEL_X, SOUND_LEVEL, VBATT);
		ObjectCluster objectCluster = shimmer.buildMsg(packet(shimmer, 0, 0x8000, 0x0123), COMMUNICATION_TYPE.BLUETOOTH, false, 0);

		assertEquals(SensorSoundLevel.NO_LEVEL, soundLevel(objectCluster, CHANNEL_TYPE.UNCAL).mData, 0);
		assertTrue(Double.isNaN(soundLevel(objectCluster, CHANNEL_TYPE.CAL).mData));
	}

	/** Signed: a level below 0 dB(A) is a level, not a wrap */
	@Test
	public void signedLittleEndian() {
		TestShimmer shimmer = new TestShimmer(S3R);
		shimmer.channels(SOUND_LEVEL);
		ObjectCluster objectCluster = shimmer.buildMsg(packet(shimmer, 0xFF9C), COMMUNICATION_TYPE.BLUETOOTH, false, 0);
		assertEquals(-100, soundLevel(objectCluster, CHANNEL_TYPE.UNCAL).mData, 0);
		assertEquals(-1.0, soundLevel(objectCluster, CHANNEL_TYPE.CAL).mData, 1e-9);

		objectCluster = shimmer.buildMsg(packet(shimmer, 0x7FFF), COMMUNICATION_TYPE.BLUETOOTH, false, 0);
		assertEquals(327.67, soundLevel(objectCluster, CHANNEL_TYPE.CAL).mData, 1e-9);
	}

	/** Shimmer3R SD files carry the channel list in their header, decoded the same way */
	@Test
	public void decodedFromAnSdFile() {
		TestShimmer shimmer = new TestShimmer(S3R);
		shimmer.channels(LN_ACCEL_X, SOUND_LEVEL, GSR, VBATT);
		ObjectCluster objectCluster = shimmer.buildMsg(packet(shimmer, 0, 7210, 0x0456, 0x0123), COMMUNICATION_TYPE.SD, false, 0);
		assertEquals(72.10, soundLevel(objectCluster, CHANNEL_TYPE.CAL).mData, 1e-9);
	}

	@Test
	public void calibration() {
		assertEquals(65.43, SensorSoundLevel.calibrateSoundLevel(6543), 1e-12);
		assertEquals(0.0, SensorSoundLevel.calibrateSoundLevel(0), 0);
		assertEquals(-327.67, SensorSoundLevel.calibrateSoundLevel(-32767), 1e-12);
		assertTrue(Double.isNaN(SensorSoundLevel.calibrateSoundLevel(-32768)));
		assertTrue(Double.isNaN(SensorSoundLevel.calibrateSoundLevel(32768)));
		assertTrue(Double.isNaN(SensorSoundLevel.calibrateSoundLevel(Double.NaN)));
	}

	/** A Shimmer3 never sends 0x38: if one did, it would stay an unknown channel, as before */
	@Test
	public void shimmer3KeepsTheUnknownChannelHandling() {
		TestShimmer shimmer = new TestShimmer(S3);
		shimmer.channels(LN_ACCEL_X, SOUND_LEVEL, VBATT);
		assertEquals(Byte.toString(SOUND_LEVEL), shimmer.mSignalNameArray[2]);
		assertEquals("u12", shimmer.mSignalDataTypeArray[2]);

		ObjectCluster objectCluster = shimmer.buildMsg(packet(shimmer, 0, 6543, 0x0123), COMMUNICATION_TYPE.BLUETOOTH, false, 0);
		assertNull(soundLevel(objectCluster, CHANNEL_TYPE.CAL));
		assertNull(soundLevel(objectCluster, CHANNEL_TYPE.UNCAL));
	}

	// ---------- Configuration byte 233 ----------

	@Test
	public void onlyAShimmer3RHasTheSensor() {
		TestShimmer s3r = new TestShimmer(S3R);
		assertNotNull(s3r.getSensorClass(SENSORS.SOUND_LEVEL));
		assertNotNull(s3r.getSensorDetails(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL));
		assertFalse("off until the Shimmer says otherwise", s3r.isSoundLevelEnabled());

		TestShimmer s3 = new TestShimmer(S3);
		assertNull(s3.getSensorClass(SENSORS.SOUND_LEVEL));
		assertNull(s3.getSensorDetails(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL));
	}

	/** The tile is offered from v1.01.026, the first release with the channel, and not before */
	@Test
	public void theTileIsOfferedFromV1_01_026() {
		int tile = Configuration.Shimmer3.LABEL_SENSOR_TILE.SOUND_LEVEL.ordinal();
		assertTrue(new TestShimmer(S3R).getSensorGroupingMap().containsKey(tile));
		assertTrue(new TestShimmer(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 2, 0)).getSensorGroupingMap().containsKey(tile));
		assertFalse(new TestShimmer(S3R_BEFORE_CHANNEL).getSensorGroupingMap().containsKey(tile));
	}

	/**
	 * Before v1.01.026 the firmware stores byte 233 untouched, so a bit 4 read back
	 * there has no channel behind it: it reads as off, and the next write clears it,
	 * keeping the audio mode, so a later firmware update does not turn the channel on
	 */
	@Test
	public void beforeTheChannelBit4ReadsOffAndIsClearedOnWrite() {
		TestShimmer shimmer = readFrom(S3R_BEFORE_CHANNEL, 0x12);
		assertFalse(shimmer.isSoundLevelEnabled());
		assertEquals("the audio mode kept, bit 4 cleared", 0x02, writtenAudioConfig(shimmer));
	}

	/** Before v1.01.026 enabling it writes nothing: the firmware would only store the bit */
	@Test
	public void beforeTheChannelEnablingDoesNotSetBit4() {
		TestShimmer shimmer = readFrom(S3R_BEFORE_CHANNEL, 0x01);
		shimmer.setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL, true);
		assertEquals("the audio mode kept, bit 4 not set", 0x01, writtenAudioConfig(shimmer));
	}

	/** The DEV-99 audio mode went to 0 on every full write before DEV-1413 */
	@Test
	public void theAudioModeReadGoesBackOnAFullWrite() {
		for(int audioMode=0;audioMode<4;audioMode++){
			TestShimmer shimmer = readFrom(S3R, audioMode);
			assertFalse(shimmer.isSoundLevelEnabled());
			assertEquals("mode " + audioMode, audioMode, writtenAudioConfig(shimmer));
		}
	}

	/**
	 * Every value of the byte: bits 0-4 go back as read, bits 5-6 are cleared
	 * as the firmware clears them, and bit 7 set (erased flash) reads as 0
	 */
	@Test
	public void everyByteGoesBackAsTheFirmwareKeepsIt() {
		for(int value=0;value<256;value++){
			TestShimmer shimmer = readFrom(S3R, value);
			boolean valid = (value & 0x80)==0;
			assertEquals(hex(value), valid && (value & 0x10)!=0, shimmer.isSoundLevelEnabled());
			assertEquals(hex(value), valid ? value & 0x1F : 0, writtenAudioConfig(shimmer));
		}
	}

	/** Enabling the sensor sets byte 233 bit 4 and changes nothing else in the image */
	@Test
	public void enablingSetsBit4AndNothingElse() {
		TestShimmer shimmer = readFrom(S3R, 0x02);
		byte[] off = shimmer.written();

		shimmer.setSoundLevelEnabled(true);
		byte[] on = shimmer.written();
		assertEquals(0x12, on[IDX_AUDIO_CONFIG] & 0xFF);
		byte[] onWithByte233Off = on.clone();
		onWithByte233Off[IDX_AUDIO_CONFIG] = off[IDX_AUDIO_CONFIG];
		assertArrayEquals("no sensor bitmap bit, so no other byte moves", off, onWithByte233Off);

		shimmer.setSoundLevelEnabled(false);
		assertTrue(Arrays.equals(off, shimmer.written()));
	}

	/** A Shimmer3R never read writes 0, as the driver always did */
	@Test
	public void unreadShimmer3RWritesZero() {
		TestShimmer shimmer = new TestShimmer(S3R);
		assertEquals(0, writtenAudioConfig(shimmer));
		shimmer.setSoundLevelEnabled(true);
		assertEquals(0x10, writtenAudioConfig(shimmer));
	}

	/** The bitmap has no bit for it, so updating the sensor map from the bitmap must leave it alone */
	@Test
	public void theSensorBitmapDoesNotTurnItOff() {
		TestShimmer shimmer = readFrom(S3R, 0x11);
		assertTrue(shimmer.isSoundLevelEnabled());
		long enabledSensors = shimmer.getEnabledSensors();

		shimmer.setEnabledAndDerivedSensorsAndUpdateMaps(shimmer.getEnabledSensors(), shimmer.getDerivedSensors());
		assertTrue(shimmer.isSoundLevelEnabled());

		shimmer.refreshEnabledSensorsFromSensorMap();
		assertEquals("adds no bitmap bit (0x4000 would read as ECG-to-HR on SD)", enabledSensors, shimmer.getEnabledSensors());
		assertEquals(0x11, writtenAudioConfig(shimmer));
	}

	/** A Shimmer3 has no microphone: byte 233 stays 0 whatever was read */
	@Test
	public void shimmer3WritesZero() {
		TestShimmer shimmer = readFrom(S3, 0x13);
		assertFalse(shimmer.isSoundLevelEnabled());
		assertEquals(0, writtenAudioConfig(shimmer));
		assertFalse(shimmer.setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL, true));
		assertEquals(0, writtenAudioConfig(shimmer));
	}
}
