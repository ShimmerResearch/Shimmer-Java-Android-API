package com.shimmerresearch.simpleexamples.bletest;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.ShimmerMsg;

/**
 * One device as the tests drive it: blocking lifecycle calls built on the driver's asynchronous
 * callbacks, plus counters of what those callbacks reported.
 * <p>
 * Subclasses supply the transport-specific parts (see {@link BleTransport}).
 */
public abstract class DeviceUnderTest extends BasicProcessWithCallBack {

	public static final Set<BT_STATE> NOT_CONNECTED = EnumSet.of(BT_STATE.DISCONNECTED, BT_STATE.CONNECTION_LOST);

	protected final ShimmerBluetooth mShimmer;
	private final Object mLock = new Object();
	private BT_STATE mState = BT_STATE.DISCONNECTED;
	private boolean mInitialised = false;
	private final AtomicLong mPackets = new AtomicLong();
	private volatile double mPacketReceptionRate = Double.NaN;

	protected DeviceUnderTest(ShimmerBluetooth shimmer) {
		mShimmer = shimmer;
		setWaitForData(shimmer);
	}

	/** e.g. "native Shimmer3R-2F31-BLE". */
	public abstract String label();

	/** The ID the transport connects with (native: BLE device ID; gRPC: MAC), or null. */
	public abstract String deviceId();

	/** Makes the device connectable; for native BLE, a scan must have seen it recently. */
	protected void prepare(int timeoutMs) throws Exception {
	}

	/** Starts an asynchronous connect; the outcome arrives through callbacks. */
	protected abstract void startConnect();

	public ShimmerBluetooth getShimmer() {
		return mShimmer;
	}

	public BT_STATE getState() {
		synchronized (mLock) {
			return mState;
		}
	}

	public long getPackets() {
		return mPackets.get();
	}

	public void resetPackets() {
		mPackets.set(0);
	}

	/** Packet reception rate in percent, as the driver last reported it. */
	public double getPacketReceptionRate() {
		double reported = mPacketReceptionRate;
		return Double.isNaN(reported) ? mShimmer.getPacketReceptionRateOverall() : reported;
	}

	/** Connects and waits until the driver reports the device fully initialised. Returns the connect time in ms. */
	public long connect(int timeoutMs) throws Exception {
		prepare(timeoutMs);
		synchronized (mLock) {
			mInitialised = false;
		}
		long started = System.nanoTime();
		startConnect();
		waitUntil("fully initialised", timeoutMs, () -> mInitialised);
		long connectedMs = elapsedMs(started);
		waitForIdle(5000);
		return connectedMs;
	}

	/**
	 * Waits until the driver has sent every queued command. It reports "fully initialised" while
	 * some (such as setting the clock) are still queued, and disconnecting then interrupts them.
	 */
	public void waitForIdle(long timeoutMs) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		long idleSince = -1;
		while (System.currentTimeMillis() < deadline) {
			if (mShimmer.isInstructionStackLock()) {
				idleSince = -1;
			} else if (idleSince < 0) {
				idleSince = System.currentTimeMillis();
			} else if (System.currentTimeMillis() - idleSince >= 500) {
				return;
			}
			Thread.sleep(50);
		}
	}

	public long disconnect(int timeoutMs) throws Exception {
		long started = System.nanoTime();
		mShimmer.disconnect();
		waitForState(NOT_CONNECTED, timeoutMs);
		return elapsedMs(started);
	}

	public void startStreaming(int timeoutMs) throws Exception {
		resetPackets();
		mShimmer.startStreaming();
		waitUntil("streaming", timeoutMs, () -> mShimmer.isStreaming());
	}

	public void stopStreaming(int timeoutMs) throws Exception {
		mShimmer.stopStreaming();
		waitUntil("streaming stopped", timeoutMs, () -> !mShimmer.isStreaming());
	}

	/** Waits until the radio state is one of {@code states}; returns how long that took. */
	public long waitForState(Set<BT_STATE> states, int timeoutMs) throws Exception {
		long started = System.nanoTime();
		waitUntil("state " + states, timeoutMs, () -> states.contains(mState));
		return elapsedMs(started);
	}

	public long waitForState(BT_STATE state, int timeoutMs) throws Exception {
		return waitForState(EnumSet.copyOf(Arrays.asList(state)), timeoutMs);
	}

	/** Disconnects if connected, ignoring errors. For clean-up between tests. */
	public void closeQuietly() {
		try {
			if (!NOT_CONNECTED.contains(getState()) || mShimmer.isConnected()) {
				mShimmer.disconnect();
			}
		} catch (Exception e) {
			System.err.println("clean-up disconnect of " + label() + ": " + e.getMessage());
		}
	}

	private void waitUntil(String what, long timeoutMs, BooleanSupplier condition) throws Exception {
		long deadline = System.currentTimeMillis() + timeoutMs;
		synchronized (mLock) {
			while (!condition.getAsBoolean()) {
				long remaining = deadline - System.currentTimeMillis();
				if (remaining <= 0) {
					throw new TimeoutException(label() + ": not " + what + " within " + timeoutMs + " ms (state " + mState + ")");
				}
				mLock.wait(Math.min(remaining, 200));
			}
		}
	}

	@Override
	protected void processMsgFromCallback(ShimmerMsg msg) {
		int id = msg.mIdentifier;
		if (id == ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE) {
			synchronized (mLock) {
				mState = ((CallbackObject) msg.mB).mState;
				mLock.notifyAll();
			}
		} else if (id == ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE) {
			if (((CallbackObject) msg.mB).mIndicator == ShimmerBluetooth.NOTIFICATION_SHIMMER_FULLY_INITIALIZED) {
				synchronized (mLock) {
					mInitialised = true;
					mLock.notifyAll();
				}
			}
		} else if (id == ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET) {
			mPackets.incrementAndGet();
		} else if (id == ShimmerBluetooth.MSG_IDENTIFIER_PACKET_RECEPTION_RATE_OVERALL) {
			mPacketReceptionRate = ((CallbackObject) msg.mB).mPacketReceptionRate;
		}
	}

	static long elapsedMs(long startedNanos) {
		return (System.nanoTime() - startedNanos) / 1000000L;
	}
}
