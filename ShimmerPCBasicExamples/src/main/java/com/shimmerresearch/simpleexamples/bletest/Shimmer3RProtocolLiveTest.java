package com.shimmerresearch.simpleexamples.bletest;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ble.nativeble.BleCentral;
import com.shimmerresearch.driver.ble.nativeble.BleConnectionListener;
import com.shimmerresearch.driver.ble.nativeble.BleUartProfile;
import com.shimmerresearch.driver.ble.nativeble.NativeBleDevice;
import com.shimmerresearch.driver.ble.nativeble.NativeBleEvent;
import com.shimmerresearch.driver.ble.nativeble.NativeBleException;
import com.shimmerresearch.protocol.ProtocolEvent;
import com.shimmerresearch.protocol.ProtocolOutput;
import com.shimmerresearch.protocol.Shimmer3RProtocol;

/**
 * DEV-1134: the Shimmer3R protocol state machine driving a real device over the native BLE
 * transport (DEV-1132). This is the whole host side: a transport, a clock and a lock.
 *
 * <pre>
 * Shimmer3RProtocolLiveTest &lt;device name&gt; [seconds to stream] [device ID if not advertising]
 * </pre>
 */
public class Shimmer3RProtocolLiveTest {

	private final Shimmer3RProtocol mProtocol = new Shimmer3RProtocol();
	private final Object mLock = new Object();
	private BleCentral mCentral;
	private long mHandle;
	private int mSamples = 0;
	private int mDiscardedEvents = 0;
	private ObjectCluster mFirstSample;
	private String mError;

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
		mCentral = BleCentral.getDefault();
		NativeBleDevice device;
		try {
			device = BleTransport.scanFor(mCentral, name, BleTransport.FIND_TIMEOUT_MS);
		} catch (Exception notAdvertising) {
			if (knownId == null) {
				throw notAdvertising;
			}
			System.out.println(name + " is not advertising; trying " + knownId + " as already connected");
			device = new NativeBleDevice(knownId, name, knownId.contains(":") ? knownId : "", NativeBleEvent.RSSI_UNKNOWN);
		}
		System.out.println("connecting to " + device);

		long started = System.currentTimeMillis();
		mHandle = mCentral.connect(device.getId(), BleUartProfile.SHIMMER3R, 20000, new BleConnectionListener() {
			@Override
			public void onBytes(byte[] data) {
				synchronized (mLock) {
					handle(mProtocol.receive(data, System.currentTimeMillis()));
				}
			}

			@Override
			public void onDisconnected(String reason) {
				synchronized (mLock) {
					mError = "link lost: " + reason;
				}
			}
		});
		System.out.println("BLE connected in " + (System.currentTimeMillis() - started) + " ms, MTU " + mCentral.mtu(mHandle));

		long handshakeStarted = System.currentTimeMillis();
		long streamingSince = -1;
		long stopRequested = -1;
		synchronized (mLock) {
			handle(mProtocol.connect(handshakeStarted));
		}
		try {
			while (true) {
				Thread.sleep(20);
				long now = System.currentTimeMillis();
				synchronized (mLock) {
					if (mError != null || mProtocol.getState() == Shimmer3RProtocol.State.FAILED) {
						break;
					}
					if (now >= mProtocol.nextDeadline()) {
						handle(mProtocol.tick(now));
					}
					Shimmer3RProtocol.State state = mProtocol.getState();
					if (state == Shimmer3RProtocol.State.READY && streamingSince < 0) {
						System.out.println("handshake done in " + (now - handshakeStarted) + " ms");
						handle(mProtocol.startStreaming(now));
					} else if (state == Shimmer3RProtocol.State.STREAMING && streamingSince < 0) {
						streamingSince = now;
					} else if (state == Shimmer3RProtocol.State.STREAMING && now - streamingSince >= seconds * 1000L
							&& stopRequested < 0) {
						stopRequested = now;
						handle(mProtocol.stopStreaming(now));
					} else if (state == Shimmer3RProtocol.State.READY && stopRequested > 0) {
						break;
					}
				}
			}
		} finally {
			mCentral.disconnect(mHandle);
		}

		synchronized (mLock) {
			double streamedSeconds = streamingSince < 0 ? 0 : ((stopRequested > 0 ? stopRequested : System.currentTimeMillis()) - streamingSince) / 1000.0;
			System.out.println(String.format("%d samples in %.1f s = %.1f /s at %.1f Hz configured; %d discard event(s)",
					mSamples, streamedSeconds, streamedSeconds > 0 ? mSamples / streamedSeconds : 0, mProtocol.getSamplingRate(),
					mDiscardedEvents));
			if (mFirstSample != null) {
				for (String channel : mFirstSample.getChannelNamesByInsertionOrder()) {
					if (!channel.startsWith("System_Timestamp")) {
						System.out.println("  " + channel + " = " + mFirstSample.getFormatClusterValue(channel, "CAL"));
					}
				}
			}
			if (mError != null) {
				System.out.println("FAILED: " + mError);
				return false;
			}
			return mSamples > 0 && mProtocol.getState() == Shimmer3RProtocol.State.READY;
		}
	}

	/** Called with mLock held. */
	private void handle(ProtocolOutput out) {
		for (byte[] write : out.getWrites()) {
			try {
				mCentral.write(mHandle, write);
			} catch (NativeBleException e) {
				mError = "write failed: " + e.getMessage();
			}
		}
		for (ProtocolEvent e : out.getEvents()) {
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
			case DISCARDED:
				mDiscardedEvents++;
				System.out.println("  discarded: " + e.message);
				break;
			default:
				System.out.println("  " + e);
			}
		}
	}
}
