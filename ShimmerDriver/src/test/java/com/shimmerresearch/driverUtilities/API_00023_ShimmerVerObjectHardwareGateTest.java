package com.shimmerresearch.driverUtilities;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;

/**
 * The hardware-taking {@link ShimmerVerObject#compareVersions} overloads must
 * check the hardware. Shimmer3 and Shimmer3R LogAndStream version numbers
 * overlap (both FW_ID 3), so a "Shimmer3R LogAndStream >= x" gate built on a
 * version comparison alone would also match a Shimmer3 - which is what these
 * overloads did until DEV-1124.
 *
 * @author Mark Nolan
 */
public class API_00023_ShimmerVerObjectHardwareGateTest {

	private static final int ANY = ShimmerVerDetails.ANY_VERSION;

	private static ShimmerVerObject shimmer3(int major, int minor, int internal) {
		return new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, major, minor, internal);
	}

	private static ShimmerVerObject shimmer3r(int major, int minor, int internal) {
		return new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, major, minor, internal);
	}

	@Test
	public void staticOverloadRejectsOtherHardware() {
		ShimmerVerObject s3 = shimmer3(1, 1, 5);
		ShimmerVerObject s3r = shimmer3r(1, 1, 17);

		assertTrue(ShimmerVerObject.compareVersions(s3, HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 1, 5));
		assertFalse("a Shimmer3 must not pass a Shimmer3R gate",
				ShimmerVerObject.compareVersions(s3, HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 5));

		assertTrue(ShimmerVerObject.compareVersions(s3r, HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 17));
		assertFalse("a Shimmer3R must not pass a Shimmer3 gate",
				ShimmerVerObject.compareVersions(s3r, HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 1, 17));
	}

	@Test
	public void instanceOverloadRejectsOtherHardware() {
		assertTrue(shimmer3(1, 1, 5).compareVersions(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 0, 4));
		assertFalse(shimmer3(1, 1, 5).compareVersions(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 0, 40));
		assertTrue(shimmer3r(1, 1, 17).compareVersions(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 0, 40));
		assertFalse(shimmer3r(1, 1, 17).compareVersions(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 0, 4));
	}

	@Test
	public void anyVersionHardwareMatchesBoth() {
		assertTrue(ShimmerVerObject.compareVersions(shimmer3(1, 1, 5), ANY, FW_ID.LOGANDSTREAM, 1, 1, 5));
		assertTrue(ShimmerVerObject.compareVersions(shimmer3r(1, 1, 17), ANY, FW_ID.LOGANDSTREAM, 1, 1, 17));
	}

	@Test
	public void versionAndFirmwareStillDecide() {
		// The right hardware is necessary, not sufficient.
		assertFalse("older than the gate",
				ShimmerVerObject.compareVersions(shimmer3(1, 1, 5), HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 1, 6));
		assertFalse("different firmware",
				ShimmerVerObject.compareVersions(shimmer3(1, 1, 5), HW_ID.SHIMMER_3, FW_ID.BTSTREAM, 0, 7, 0));
	}

	@Test
	public void existingDerivedSensorsGateUnchanged() {
		// isSupportedDerivedSensors is the one existing gate that reaches the
		// static overload with a hardware it relied on not being checked; it
		// also ORs isShimmerGen3R(), so both platforms must still pass.
		assertTrue(ShimmerDevice.isSupportedDerivedSensors(shimmer3(0, 3, 17)));
		assertTrue(ShimmerDevice.isSupportedDerivedSensors(shimmer3(1, 1, 5)));
		assertTrue(ShimmerDevice.isSupportedDerivedSensors(shimmer3r(1, 0, 0)));
		assertFalse(ShimmerDevice.isSupportedDerivedSensors(shimmer3(0, 3, 16)));
	}
}
