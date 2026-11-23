package com.shimmerresearch.driver.ble.nativeble;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;

/**
 * The native BLE transport that ShimmerPC talks through in place of its COM port (DEV-1504),
 * against a fake radio: no native library is loaded, so this runs on the Linux CI runner.
 */
public class API_00053_ByteCommunicationNativeBleTest {

	private static final String ID = "E7:47:AA:7D:2F:31";
	private static final String NAME = "Shimmer3R-2F31-BLE";
	/** The fake radio's first connection */
	private static final long HANDLE = 1;
	private static final long WAIT_MS = 3000;

	private FakeBleRadio mRadio;
	private BleCentral mCentral;
	private ByteCommunicationNativeBle mTransport;

	@Before
	public void setUp() {
		mRadio = new FakeBleRadio();
		mCentral = new BleCentral(mRadio);
		mTransport = new ByteCommunicationNativeBle(ID, NAME, mCentral);
	}

	@After
	public void tearDown() {
		mCentral.shutdown();
	}

	private interface Condition {
		boolean met() throws Exception;
	}

	private static void waitFor(String what, Condition condition) throws Exception {
		long deadline = System.currentTimeMillis() + WAIT_MS;
		while (!condition.met()) {
			if (System.currentTimeMillis() > deadline) {
				throw new AssertionError("timed out waiting for " + what);
			}
			Thread.sleep(10);
		}
	}

	private void receive(final int count, byte... data) throws Exception {
		mRadio.push(NativeBleEvent.bytes(HANDLE, data));
		waitFor(count + " bytes buffered", new Condition() {
			public boolean met() throws Exception {
				return mTransport.getInputBufferBytesCount() == count;
			}
		});
	}

	@Test
	public void receivedBytesAreReadInOrder() throws Exception {
		assertTrue(mTransport.openPort());
		assertTrue(mTransport.isOpened());

		receive(3, (byte) 0x01, (byte) 0x02, (byte) 0x03);
		receive(5, (byte) 0x04, (byte) 0x05);

		assertArrayEquals(new byte[] { 0x01, 0x02 }, mTransport.readBytes(2, 1000));
		assertArrayEquals(new byte[] { 0x03, 0x04, 0x05 }, mTransport.readBytes(3, 1000));
		assertEquals(0, mTransport.getInputBufferBytesCount());
	}

	@Test
	public void writesGoToTheRadio() throws Exception {
		mTransport.openPort();
		assertTrue(mTransport.writeBytes(new byte[] { 0x01 }));
		assertEquals(1, mRadio.written.size());
		assertArrayEquals(new byte[] { 0x01 }, mRadio.written.get(0));
	}

	@Test
	public void aReadTimesOutWithoutTakingWhatHasArrived() throws Exception {
		mTransport.openPort();
		receive(2, (byte) 0x0A, (byte) 0x0B);
		try {
			mTransport.readBytes(3, 200);
			fail("expected a timeout");
		} catch (SerialPortTimeoutException expected) {
		}
		assertEquals(2, mTransport.getInputBufferBytesCount());
		assertArrayEquals(new byte[] { 0x0A, 0x0B }, mTransport.readBytes(2, 1000));
	}

	@Test
	public void aLostLinkFailsByteCountsReadsAndWrites() throws Exception {
		mTransport.openPort();
		mRadio.push(NativeBleEvent.disconnected(HANDLE, "timeout"));
		waitFor("the byte count to fail", new Condition() {
			public boolean met() {
				try {
					mTransport.getInputBufferBytesCount();
					return false;
				} catch (SerialPortException e) {
					return true;
				}
			}
		});
		assertTrue("still open until ShimmerPC closes it, so it purges and closes as for a COM port", mTransport.isOpened());
		try {
			mTransport.writeBytes(new byte[] { 0x01 });
			fail("expected the write to fail");
		} catch (SerialPortException expected) {
		}
		try {
			mTransport.readBytes(1, 200);
			fail("expected the read to fail");
		} catch (SerialPortException expected) {
		}
	}

	@Test
	public void aWaitingReadEndsWhenTheLinkDrops() throws Exception {
		mTransport.openPort();
		final AtomicReference<Exception> thrown = new AtomicReference<Exception>();
		Thread reader = new Thread(new Runnable() {
			public void run() {
				try {
					mTransport.readBytes(5, 10000);
				} catch (Exception e) {
					thrown.set(e);
				}
			}
		});
		reader.start();
		Thread.sleep(100);
		long start = System.currentTimeMillis();
		mRadio.push(NativeBleEvent.disconnected(HANDLE, "timeout"));
		reader.join(WAIT_MS);
		assertFalse("the read is still waiting", reader.isAlive());
		assertTrue("ended in " + (System.currentTimeMillis() - start) + " ms", System.currentTimeMillis() - start < 2000);
		assertTrue(String.valueOf(thrown.get()), thrown.get() instanceof SerialPortException);
	}

	@Test
	public void closingDisconnectsAndDropsWhatIsBuffered() throws Exception {
		mTransport.openPort();
		receive(1, (byte) 0x01);
		assertTrue(mTransport.closePort());
		assertFalse(mTransport.isOpened());
		assertEquals(1, mRadio.disconnected.size());
		assertEquals(Long.valueOf(HANDLE), mRadio.disconnected.get(0));
		assertEquals(0, mTransport.getInputBufferBytesCount());
	}

	@Test
	public void purgingDropsWhatIsBuffered() throws Exception {
		mTransport.openPort();
		receive(2, (byte) 0x01, (byte) 0x02);
		assertTrue(mTransport.purgePort(1));
		assertEquals(0, mTransport.getInputBufferBytesCount());
	}

	@Test
	public void theBufferGrowsForALargeBurst() throws Exception {
		mTransport.openPort();
		byte[] burst = new byte[20000];
		for (int i = 0; i < burst.length; i++) {
			burst[i] = (byte) i;
		}
		receive(burst.length, burst);
		assertArrayEquals(burst, mTransport.readBytes(burst.length, 1000));
	}

	/** A link left open by an earlier session that crashed: the device is still streaming into it */
	@Test
	public void aStreamLeftRunningIsStoppedBeforeTheHandshake() throws Exception {
		mRadio.duringConnect.add(NativeBleEvent.bytes(0, new byte[] { 0x00, 0x11, 0x22 }));
		mTransport.openPort();

		assertEquals("stop streaming, then checksums off", 2, mRadio.written.size());
		assertArrayEquals(new byte[] { 0x20 }, mRadio.written.get(0));
		assertArrayEquals(new byte[] { (byte) 0x8B, 0x00 }, mRadio.written.get(1));
		assertEquals("the stream's tail is dropped", 0, mTransport.getInputBufferBytesCount());
	}

	@Test
	public void aDeviceNotStreamingGetsNoCommands() throws Exception {
		mTransport.openPort();
		assertTrue(mRadio.written.isEmpty());
	}

	@Test
	public void aNameThatIsNotAShimmerIsRefused() throws Exception {
		ByteCommunicationNativeBle verisense = new ByteCommunicationNativeBle(ID, "Verisense-190226016F14", mCentral);
		try {
			verisense.openPort();
			fail("expected a refusal");
		} catch (SerialPortException expected) {
		}
		assertFalse(verisense.isOpened());
	}

}
