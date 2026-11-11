package com.shimmerresearch.pcDriver;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.sensors.soundlevel.SensorSoundLevel;

/**
 * DEV-1413: the Shimmer3R sound level channel through ShimmerPC, the class
 * Consensys configures. Consensys edits a copy made by serialisation
 * (deepClone()) and writes the configuration from it, so byte 233 as read has
 * to survive the copy. API_00040 in ShimmerDriver covers the decoding and the
 * byte itself.
 * <p>
 * No hardware.
 *
 * @author Mark Nolan
 */
public class API_00041_SoundLevelShimmerPCTest {

	private static final int IDX_AUDIO_CONFIG = 233;
	private static final ShimmerVerObject S3R = new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 25);

	private static ShimmerPC shimmer() {
		ShimmerPC shimmer = new ShimmerPC("COM99");
		shimmer.setShimmerVersionObjectAndCreateSensorMap(S3R);
		return shimmer;
	}

	private static ShimmerPC readFrom(int audioConfig) {
		byte[] config = shimmer().generateConfigBytesForWritingToShimmer().clone();
		config[IDX_AUDIO_CONFIG] = (byte) audioConfig;
		ShimmerPC shimmer = shimmer();
		shimmer.configBytesParse(config, COMMUNICATION_TYPE.BLUETOOTH);
		return shimmer;
	}

	private static int writtenAudioConfig(ShimmerPC shimmer) {
		return shimmer.generateConfigBytesForWritingToShimmer()[IDX_AUDIO_CONFIG] & 0xFF;
	}

	@Test
	public void byte233SurvivesTheCopyConsensysWritesFrom() {
		ShimmerPC shimmer = readFrom(0x13);
		assertTrue(shimmer.isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL));

		ShimmerPC clone = shimmer.deepClone();
		assertNotNull(clone);
		assertTrue(clone.isSensorEnabled(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL));
		assertEquals(0x13, writtenAudioConfig(clone));

		assertTrue(clone.setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL, false));
		assertEquals("the audio mode stays", 0x03, writtenAudioConfig(clone));
		assertEquals("the original is untouched", 0x13, writtenAudioConfig(shimmer));
	}

	/** Consensys streams with the arrays data structure on */
	@Test
	public void decodedWithTheArraysDataStructure() {
		ShimmerPC shimmer = shimmer();
		shimmer.mNChannels = 2;
		shimmer.interpretDataPacketFormat(2, new byte[] {SensorSoundLevel.CHANNEL_ID, 0x03});
		shimmer.enableArraysDataStructure(true);

		byte[] packet = new byte[shimmer.getPacketSize()];
		int iSoundLevel = packet.length - 4;
		packet[iSoundLevel] = (byte) 0x8F;		// 6543 = 0x198F
		packet[iSoundLevel + 1] = (byte) 0x19;
		ObjectCluster objectCluster = shimmer.buildMsg(packet, COMMUNICATION_TYPE.BLUETOOTH, false, 0);
		assertEquals(65.43, objectCluster.getFormatClusterValue(SensorSoundLevel.ObjectClusterSensorName.SOUND_LEVEL, CHANNEL_TYPE.CAL.toString()), 1e-9);
		assertEquals(6543, objectCluster.getFormatClusterValue(SensorSoundLevel.ObjectClusterSensorName.SOUND_LEVEL, CHANNEL_TYPE.UNCAL.toString()), 0);

		packet[iSoundLevel] = 0x00;				// 0x8000, no level
		packet[iSoundLevel + 1] = (byte) 0x80;
		objectCluster = shimmer.buildMsg(packet, COMMUNICATION_TYPE.BLUETOOTH, false, 0);
		assertTrue(Double.isNaN(objectCluster.getFormatClusterValue(SensorSoundLevel.ObjectClusterSensorName.SOUND_LEVEL, CHANNEL_TYPE.CAL.toString())));
		assertEquals(-32768, objectCluster.getFormatClusterValue(SensorSoundLevel.ObjectClusterSensorName.SOUND_LEVEL, CHANNEL_TYPE.UNCAL.toString()), 0);
	}
}
