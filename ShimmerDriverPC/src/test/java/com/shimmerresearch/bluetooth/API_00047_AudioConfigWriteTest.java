package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.pcDriver.ShimmerPC;

/**
 * Configuration byte 233 on a Shimmer3R (DEV-99 audio mode and gain in bits
 * 0-3, DEV-1413 sound level in bit 4) through a Bluetooth configuration write.
 * The host can set bit 4 but never bits 0-3, which it only gives back as read.
 * So before a read, the write leaves the byte out, as it does byte 232
 * (API_00046), and the Shimmer keeps its own. The one exception is the sound
 * level turned on before a read: the byte is then written with bits 0-3 at 0,
 * since a SET_INFOMEM cannot write one bit.
 * <p>
 * No hardware. It lives in this package because the instruction list is a
 * protected member of ShimmerBluetooth.
 */
public class API_00047_AudioConfigWriteTest {

	private static final int IDX_BUTTON_LOG_MODE = 232;
	private static final int IDX_AUDIO_CONFIG = 233;
	private static final int SOUND_LEVEL_BIT = 0x10;

	/** v1.01.026, the first release with the sound level */
	private static final ShimmerVerObject S3R = new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 26);

	private static ShimmerPC shimmer() {
		ShimmerPC shimmer = new ShimmerPC("COM99");
		shimmer.setShimmerVersionObjectAndCreateSensorMap(S3R);
		return shimmer;
	}

	private static ShimmerPC readFrom(int buttonLogMode, int audioConfig) {
		byte[] config = shimmer().generateConfigBytesForWritingToShimmer().clone();
		config[IDX_BUTTON_LOG_MODE] = (byte) buttonLogMode;
		config[IDX_AUDIO_CONFIG] = (byte) audioConfig;
		ShimmerPC shimmer = shimmer();
		shimmer.configBytesParse(config, COMMUNICATION_TYPE.BLUETOOTH);
		return shimmer;
	}

	/** Each SET_INFOMEM queued: [0x8C, length, address LSB, address MSB, data...] */
	private static List<byte[]> infoMemWrites(ShimmerBluetooth shimmer) {
		List<byte[]> writes = new ArrayList<byte[]>();
		for(byte[] instruction:shimmer.getListofInstructions()){
			if(instruction!=null && instruction[0]==ShimmerObject.SET_INFOMEM_COMMAND){
				writes.add(instruction);
			}
		}
		return writes;
	}

	private static int address(byte[] write) {
		return (write[2]&0xFF) | ((write[3]&0xFF)<<8);
	}

	private static int length(byte[] write) {
		return write[1]&0xFF;
	}

	/** The value written at idx, or -1 if no SET_INFOMEM covers it */
	private static int writtenAt(ShimmerBluetooth shimmer, int idx) {
		for(byte[] write:infoMemWrites(shimmer)){
			if(idx>=address(write) && idx<address(write)+length(write)){
				return write[4 + idx - address(write)] & 0xFF;
			}
		}
		return -1;
	}

	private static List<String> chunks(ShimmerBluetooth shimmer) {
		List<String> chunks = new ArrayList<String>();
		for(byte[] write:infoMemWrites(shimmer)){
			chunks.add(Arrays.toString(new int[] {address(write), length(write)}));
		}
		return chunks;
	}

	@Test
	public void beforeAReadTheBluetoothWriteLeavesTheShimmersAudioByte() {
		ShimmerPC shimmer = shimmer();
		shimmer.writeConfigBytes(false);
		assertEquals("a configuration written on connect, before the read, would turn the Shimmer's audio mode and sound level off",
				-1, writtenAt(shimmer, IDX_AUDIO_CONFIG));
	}

	@Test
	public void beforeAReadBytes232And233AreBothLeftOut() {
		ShimmerPC shimmer = shimmer();
		shimmer.writeConfigBytes(false);
		int length = shimmer.generateConfigBytesForWritingToShimmer().length;
		List<String> wanted = new ArrayList<String>();
		for(int address=0;address<length;address+=128){
			if(address==128){
				wanted.add(Arrays.toString(new int[] {128, 104}));
				wanted.add(Arrays.toString(new int[] {234, 22}));
			}
			else {
				wanted.add(Arrays.toString(new int[] {address, 128}));
			}
		}
		assertEquals(wanted, chunks(shimmer));

		// What is sent is the image's
		byte[] image = shimmer.generateConfigBytesForWritingToShimmer();
		for(byte[] write:infoMemWrites(shimmer)){
			assertArrayEquals("chunk at " + address(write), Arrays.copyOfRange(image, address(write), address(write)+length(write)),
					Arrays.copyOfRange(write, 4, 4+length(write)));
		}
	}

	@Test
	public void onceReadTheBluetoothWriteGivesTheByteBack() {
		ShimmerPC shimmer = readFrom(0x11, 0x03 | SOUND_LEVEL_BIT);
		shimmer.writeConfigBytes(false);
		assertEquals(0x13, writtenAt(shimmer, IDX_AUDIO_CONFIG));
		assertEquals(0x11, writtenAt(shimmer, IDX_BUTTON_LOG_MODE));
	}

	@Test
	public void theSoundLevelTurnedOnBeforeAReadIsWritten() {
		ShimmerPC shimmer = shimmer();
		assertTrue(shimmer.setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_SOUND_LEVEL, true));
		shimmer.writeConfigBytes(false);
		assertEquals("bit 4, with bits 0-3 at 0", SOUND_LEVEL_BIT, writtenAt(shimmer, IDX_AUDIO_CONFIG));
		assertEquals("byte 232 is still left out", -1, writtenAt(shimmer, IDX_BUTTON_LOG_MODE));
	}

	@Test
	public void byte232SetBeforeAReadIsWrittenWithout233() {
		ShimmerPC shimmer = shimmer();
		shimmer.setButtonMode(com.shimmerresearch.driver.shimmer2r3.ButtonLogMode.BUTTON_MODE_DISABLED);
		shimmer.writeConfigBytes(false);
		assertEquals(0x01, writtenAt(shimmer, IDX_BUTTON_LOG_MODE));
		assertEquals(-1, writtenAt(shimmer, IDX_AUDIO_CONFIG));
	}
}
