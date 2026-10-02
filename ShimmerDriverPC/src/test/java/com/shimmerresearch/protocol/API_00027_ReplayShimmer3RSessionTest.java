package com.shimmerresearch.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.pcDriver.ShimmerPC;

/**
 * Today's driver (ShimmerPC) replaying a recorded Shimmer3R session, with no hardware. Its output
 * is the reference the DEV-1134 protocol state machine has to reproduce.
 */
public class API_00027_ReplayShimmer3RSessionTest extends BasicProcessWithCallBack {

	static final String SESSION = "/protocol/shimmer3r_2f31_handshake_stream10s.bytes.log";

	private final List<ObjectCluster> mPackets = Collections.synchronizedList(new ArrayList<ObjectCluster>());
	private volatile boolean mInitialised = false;
	private ShimmerPC mDevice;
	private ReplayByteCommunication mReplay;

	@Before
	public void setUp() throws Exception {
		mReplay = new ReplayByteCommunication(RecordedSession.load(SESSION));
		mDevice = new ShimmerPC("REPLAY");
		mDevice.setTestRadio(mReplay);
		setWaitForData(mDevice);
	}

	@After
	public void tearDown() {
		try {
			mDevice.disconnect();
		} catch (Exception e) {
			// Clean-up only.
		}
		mReplay.shutdown();
	}

	@Override
	protected void processMsgFromCallback(ShimmerMsg msg) {
		if (msg.mIdentifier == ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE
				&& ((CallbackObject) msg.mB).mIndicator == ShimmerBluetooth.NOTIFICATION_SHIMMER_FULLY_INITIALIZED) {
			mInitialised = true;
		} else if (msg.mIdentifier == ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET) {
			mPackets.add((ObjectCluster) msg.mB);
		}
	}

	@Test
	public void todaysDriverDecodesTheRecordedSession() throws Exception {
		mDevice.connect("REPLAY", "");
		waitUntil("fully initialised", 30000, () -> mInitialised);
		assertEquals(HW_ID.SHIMMER_3R, mDevice.getHardwareVersion());

		mDevice.startStreaming();
		waitUntil("streaming", 15000, () -> mDevice.isStreaming());
		// The recording is finite: wait until packets have started and then stopped arriving.
		waitUntil("first packet", 15000, () -> !mPackets.isEmpty());
		int last = -1;
		long deadline = System.currentTimeMillis() + 30000;
		while (mPackets.size() != last && System.currentTimeMillis() < deadline) {
			last = mPackets.size();
			Thread.sleep(1000);
		}

		System.out.println("commands written: " + mReplay.getWritten().size());
		for (byte[] c : mReplay.getWritten()) {
			System.out.println("written: " + UtilShimmer.bytesToHexStringWithSpacesFormatted(c));
		}
		for (byte[] c : mReplay.getUnanswered()) {
			System.out.println("unanswered: " + UtilShimmer.bytesToHexStringWithSpacesFormatted(c));
		}
		System.out.println("sampling rate " + mDevice.getSamplingRateShimmer() + " Hz, packets " + mPackets.size());
		if (!mPackets.isEmpty()) {
			ObjectCluster first = mPackets.get(0);
			for (String channel : first.getChannelNamesByInsertionOrder()) {
				System.out.println("  " + channel + " = " + first.getFormatClusterValue(channel, CHANNEL_TYPE.CAL.toString()));
			}
		}
		assertTrue("expected roughly 10 s of 51.2 Hz packets, got " + mPackets.size(), mPackets.size() > 400);
	}

	private static void waitUntil(String what, long timeoutMs, BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > deadline) {
				throw new AssertionError("timed out waiting for " + what);
			}
			Thread.sleep(50);
		}
	}
}
