package com.shimmerresearch.simpleexamples.bletest;

import java.util.function.BooleanSupplier;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ble.nativeble.BleCentral;
import com.shimmerresearch.driver.ble.nativeble.BleConnectionListener;
import com.shimmerresearch.driver.ble.nativeble.BleUartProfile;
import com.shimmerresearch.driver.ble.nativeble.NativeBleDevice;
import com.shimmerresearch.driver.ble.nativeble.NativeBleEvent;
import com.shimmerresearch.protocol.ProtocolEvent;
import com.shimmerresearch.protocol.ProtocolHost;
import com.shimmerresearch.protocol.Shimmer3RProtocol;
import com.shimmerresearch.protocol.Shimmer3RProtocol.State;

/**
 * DEV-1134: the Shimmer3R protocol state machine driving a real device over the native BLE
 * transport (DEV-1132). The {@link ProtocolHost} runs the protocol; this class only opens the
 * link and passes bytes to it.
 *
 * <pre>
 * Shimmer3RProtocolLiveTest &lt;device name&gt; [seconds to stream] [device ID if not advertising]
 * </pre>
 */
public class Shimmer3RProtocolLiveTest {

	private static final long HANDSHAKE_TIMEOUT_MS = 30000;
	private static final long STATE_TIMEOUT_MS = 10000;

	private volatile int mSamples = 0;
	private volatile int mDiscardedEvents = 0;
	private volatile ObjectCluster mFirstSample;
	private volatile String mError;

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.err.println("usage: Shimmer3RProtocolLiveTest <device name> [seconds] [device ID]");
			System.exit(2);
		}
		int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 10;
		String knownId = args.length > 2 ? args[2] : null;
		boolean ok = new Shimmer3RProtocolLiveTest().run(args[0], seconds, knownId);
		System.exit(ok ? 0 : 1);
	}

	boolean run(String name, int seconds, String knownId) throws Exception {
		BleCentral central = BleCentral.getDefault();
		NativeBleDevice device;
		try {
			device = BleTransport.scanFor(central, name, BleTransport.FIND_TIMEOUT_MS);
		} catch (Exception notAdvertising) {
			if (knownId == null) {
				throw notAdvertising;
			}
			System.out.println(name + " is not advertising; trying " + knownId + " as already connected");
			device = new NativeBleDevice(knownId, name, knownId.contains(":") ? knownId : "", NativeBleEvent.RSSI_UNKNOWN);
		}
		System.out.println("connecting to " + device);

		long[] handle = { 0 };
		ProtocolHost host = new ProtocolHost(bytes -> central.write(handle[0], bytes), this::onEvent);
		long started = System.currentTimeMillis();
		handle[0] = central.connect(device.getId(), BleUartProfile.SHIMMER3R, 20000, new BleConnectionListener() {
			@Override
			public void onBytes(byte[] data) {
				host.onBytes(data);
			}

			@Override
			public void onDisconnected(String reason) {
				host.onLinkLost(reason);
			}
		});
		System.out.println("BLE connected in " + (System.currentTimeMillis() - started) + " ms, MTU " + central.mtu(handle[0]));

		long handshakeStarted = System.currentTimeMillis();
		long streamingSince = -1;
		long stopRequested = -1;
		try {
			host.connect();
			if (!waitFor(host, State.READY, HANDSHAKE_TIMEOUT_MS)) {
				return report(host, 0);
			}
			System.out.println("handshake done in " + (System.currentTimeMillis() - handshakeStarted) + " ms (including "
					+ Shimmer3RProtocol.SETTLE_MS + " ms waiting for a quiet link)");
			host.startStreaming();
			if (!waitFor(host, State.STREAMING, STATE_TIMEOUT_MS)) {
				return report(host, 0);
			}
			streamingSince = System.currentTimeMillis();
			waitUntil(() -> mError != null, seconds * 1000L);
			stopRequested = System.currentTimeMillis();
			host.stopStreaming();
			waitFor(host, State.READY, STATE_TIMEOUT_MS);
		} finally {
			host.close();
			central.disconnect(handle[0]);
		}
		double streamedSeconds = streamingSince < 0 ? 0 : (stopRequested - streamingSince) / 1000.0;
		return report(host, streamedSeconds);
	}

	/** On the host's event thread. */
	private void onEvent(ProtocolEvent e) {
		switch (e.type) {
		case SAMPLE:
			if (mFirstSample == null) {
				mFirstSample = e.sample;
			}
			mSamples++;
			break;
		case ERROR:
			mError = e.message;
			break;
		case LINK_LOST:
			mError = "link lost: " + e.message;
			break;
		case DISCARDED:
			mDiscardedEvents++;
			System.out.println("  discarded: " + e.message);
			break;
		default:
			System.out.println("  " + e);
		}
	}

	private boolean report(ProtocolHost host, double streamedSeconds) {
		System.out.println(String.format("%d samples in %.1f s = %.1f /s at %.1f Hz configured; %d discard event(s)",
				mSamples, streamedSeconds, streamedSeconds > 0 ? mSamples / streamedSeconds : 0, host.getSamplingRate(),
				mDiscardedEvents));
		ObjectCluster first = mFirstSample;
		if (first != null) {
			for (String channel : first.getChannelNamesByInsertionOrder()) {
				if (!channel.startsWith("System_Timestamp")) {
					System.out.println("  " + channel + " = " + first.getFormatClusterValue(channel, "CAL"));
				}
			}
		}
		if (mError != null) {
			System.out.println("FAILED: " + mError);
			return false;
		}
		if (host.getState() != State.READY) {
			System.out.println("FAILED: ended in state " + host.getState());
			return false;
		}
		return mSamples > 0;
	}

	/** Waits for a state; false if the protocol failed, the link dropped or the wait timed out. */
	private boolean waitFor(ProtocolHost host, State state, long timeoutMs) throws InterruptedException {
		waitUntil(() -> host.getState() == state || host.getState() == State.FAILED || mError != null, timeoutMs);
		return host.getState() == state && mError == null;
	}

	private static void waitUntil(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
	}
}
