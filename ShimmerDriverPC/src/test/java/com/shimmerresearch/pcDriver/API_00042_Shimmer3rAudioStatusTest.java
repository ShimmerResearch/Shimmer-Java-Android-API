package com.shimmerresearch.pcDriver;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;

/**
 * DEV-1459: status byte 1 bits 1-2, audio recording and audio fault
 * (log-and-stream-common SHIMMER3R_AUDIO.md §5). No hardware.
 */
public class API_00042_Shimmer3rAudioStatusTest {

	/** Reaches the status parser, which the driver calls on each status frame */
	private static class StatusShimmer extends ShimmerPC {
		StatusShimmer(int major, int minor, int internal) {
			super("COM99");
			setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, major, minor, internal));
		}

		StatusShimmer parse(int statusByte1) {
			parseStatusByte(new byte[] {0, (byte) statusByte1});
			return this;
		}
	}

	@Test
	public void recordingAndFaultFromByte1() {
		StatusShimmer shimmer = new StatusShimmer(1, 1, 26);
		assertTrue(shimmer.parse(0x02).isAudioRecording());
		assertFalse(shimmer.isAudioFault());

		shimmer.parse(0x04);
		assertFalse(shimmer.isAudioRecording());
		assertTrue(shimmer.isAudioFault());

		shimmer.parse(0x01);
		assertTrue(shimmer.isUsbPluggedIn());
		assertFalse(shimmer.isAudioRecording());
		assertFalse(shimmer.isAudioFault());
	}

	@Test
	public void ignoredBeforeV1_01_026() {
		StatusShimmer shimmer = new StatusShimmer(1, 1, 25).parse(0x06);
		assertFalse(shimmer.isAudioRecording());
		assertFalse(shimmer.isAudioFault());
	}
}
