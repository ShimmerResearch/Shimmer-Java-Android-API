package com.shimmerresearch.driver.ble.nativeble;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Event routing in {@link BleCentral}, against a fake radio: no native library is loaded, so this
 * runs on the Linux CI runner.
 */
public class API_00025_NativeBleCentralTest {

	private static final String ID = "E8:EB:1B:71:2F:31";
	private static final long WAIT_MS = 3000;

	private FakeBleRadio mRadio;
	private BleCentral mCentral;

	@Before
	public void setUp() {
		mRadio = new FakeBleRadio();
		mCentral = new BleCentral(mRadio);
	}

	@After
	public void tearDown() {
		mCentral.shutdown();
	}

	/** Records what a connection's listener receives. */
	private static class Recorder implements BleConnectionListener {
		final List<byte[]> bytes = Collections.synchronizedList(new ArrayList<byte[]>());
		final List<String> disconnects = Collections.synchronizedList(new ArrayList<String>());

		@Override
		public void onBytes(byte[] data) {
			bytes.add(data);
		}

		@Override
		public void onDisconnected(String reason) {
			disconnects.add(reason);
		}
	}

	private static void waitFor(String what, Condition condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + WAIT_MS;
		while (!condition.met()) {
			if (System.currentTimeMillis() > deadline) {
				throw new AssertionError("timed out waiting for " + what);
			}
			Thread.sleep(10);
		}
	}

	private interface Condition {
		boolean met();
	}

	@Test
	public void bytesSentBeforeTheListenerIsRegisteredArriveInOrder() throws Exception {
		mRadio.duringConnect.add(NativeBleEvent.bytes(0, new byte[] { (byte) 0xFF }));
		mRadio.duringConnect.add(NativeBleEvent.bytes(0, new byte[] { 0x02, 0x10 }));
		final Recorder recorder = new Recorder();

		long handle = mCentral.connect(ID, BleUartProfile.SHIMMER3R, 1000, recorder);
		mRadio.push(NativeBleEvent.bytes(handle, new byte[] { 0x03 }));

		waitFor("three notifications", new Condition() {
			public boolean met() {
				return recorder.bytes.size() == 3;
			}
		});
		assertArrayEquals(new byte[] { (byte) 0xFF }, recorder.bytes.get(0));
		assertArrayEquals(new byte[] { 0x02, 0x10 }, recorder.bytes.get(1));
		assertArrayEquals(new byte[] { 0x03 }, recorder.bytes.get(2));
	}

	@Test
	public void linkLossIsReportedOnceAndNothingFollows() throws Exception {
		final Recorder recorder = new Recorder();
		long handle = mCentral.connect(ID, BleUartProfile.SHIMMER3R, 1000, recorder);

		mRadio.push(NativeBleEvent.disconnected(handle, "link lost"));
		mRadio.push(NativeBleEvent.disconnected(handle, "notification stream ended"));
		mRadio.push(NativeBleEvent.bytes(handle, new byte[] { 0x01 }));

		waitFor("the link loss", new Condition() {
			public boolean met() {
				return recorder.disconnects.size() == 1;
			}
		});
		Thread.sleep(600);
		assertEquals(Collections.singletonList("link lost"), recorder.disconnects);
		assertTrue("no bytes after the link dropped", recorder.bytes.isEmpty());
	}

	@Test
	public void explicitDisconnectStopsDeliveryAndClosesTheRadio() throws Exception {
		final Recorder recorder = new Recorder();
		long handle = mCentral.connect(ID, BleUartProfile.SHIMMER3, 1000, recorder);

		mCentral.disconnect(handle);
		mRadio.push(NativeBleEvent.bytes(handle, new byte[] { 0x01 }));
		Thread.sleep(600);

		assertEquals(Collections.singletonList(handle), mRadio.disconnected);
		assertTrue(recorder.bytes.isEmpty());
		assertTrue(recorder.disconnects.isEmpty());
	}

	@Test
	public void aNamelessAdvertisementKeepsTheNameSeenEarlier() throws Exception {
		final List<NativeBleDevice> seen = Collections.synchronizedList(new ArrayList<NativeBleDevice>());
		mCentral.addScanListener(new BleScanListener() {
			public void onDeviceFound(NativeBleDevice device) {
				seen.add(device);
			}
		});

		mRadio.push(NativeBleEvent.deviceFound(ID, "Shimmer3R-2F31-BLE", ID, -60));
		mRadio.push(NativeBleEvent.deviceFound(ID, "", ID, -55));

		waitFor("two advertisements", new Condition() {
			public boolean met() {
				return seen.size() == 2;
			}
		});
		NativeBleDevice device = mCentral.getDiscoveredDevice(ID);
		assertEquals("Shimmer3R-2F31-BLE", device.getName());
		assertEquals(-55, device.getRssi());
		assertEquals("E8EB1B712F31", device.getMacId());
		assertEquals(BleUartProfile.SHIMMER3R, device.getProfile());
		assertEquals(1, mCentral.getDiscoveredDevices().size());
	}
}
