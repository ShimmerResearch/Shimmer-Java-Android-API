package com.shimmerresearch.driver.ble.nativeble;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Device-name to BLE profile mapping, and the loader's choice of native library folder. */
public class API_00026_NativeBleProfileAndPlatformTest {

	@Test
	public void profileIsChosenFromTheAdvertisedName() {
		// Shimmer3R must win over Shimmer3: the name contains both.
		assertEquals(BleUartProfile.SHIMMER3R, BleUartProfile.fromDeviceName("Shimmer3R-2F31-BLE"));
		assertEquals(BleUartProfile.SHIMMER3, BleUartProfile.fromDeviceName("Shimmer3-6813"));
		assertEquals(BleUartProfile.VERISENSE, BleUartProfile.fromDeviceName("Verisense-19092501A2BB"));
		assertNull(BleUartProfile.fromDeviceName("Some Headphones"));
		assertNull(BleUartProfile.fromDeviceName(null));
	}

	@Test
	public void platformFolderFollowsTheJvmNotTheMachine() throws Exception {
		assertEquals("windows-x64", NativeBleLoader.platformDirectory("Windows 11", "amd64"));
		assertEquals("windows-arm64", NativeBleLoader.platformDirectory("Windows 11", "aarch64"));
		assertEquals("macos-arm64", NativeBleLoader.platformDirectory("Mac OS X", "aarch64"));
		// An Intel JVM under Rosetta on Apple silicon reports x86_64 and needs the Intel library.
		assertEquals("macos-x64", NativeBleLoader.platformDirectory("Mac OS X", "x86_64"));
		assertEquals("linux-x64", NativeBleLoader.platformDirectory("Linux", "amd64"));
	}

	@Test
	public void a32BitJvmIsRejectedWithAClearMessage() {
		try {
			NativeBleLoader.platformDirectory("Windows 10", "x86");
			fail("expected NativeBleException");
		} catch (NativeBleException e) {
			assertEquals("BLE native library is not available for Windows 10 on x86 (needs a 64-bit JVM)",
					e.getMessage());
		}
	}
}
