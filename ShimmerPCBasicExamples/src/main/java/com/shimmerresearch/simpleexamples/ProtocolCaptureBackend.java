package com.shimmerresearch.simpleexamples;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ble.nativeble.BleCentral;
import com.shimmerresearch.driver.ble.nativeble.BleConnectionListener;
import com.shimmerresearch.driver.ble.nativeble.BleUartProfile;
import com.shimmerresearch.driver.ble.nativeble.NativeBleDevice;
import com.shimmerresearch.driver.ble.nativeble.NativeBleException;
import com.shimmerresearch.protocol.ProtocolEvent;
import com.shimmerresearch.protocol.ProtocolOutput;
import com.shimmerresearch.protocol.Shimmer3RProtocol;
import com.shimmerresearch.protocol.Shimmer3RProtocol.State;

/**
 * The DEV-1134 protocol state machine ({@link Shimmer3RProtocol}) over native BLE: the whole
 * host side is the transport, a clock (a 20 ms ticker for timeouts) and one lock.
 * Shimmer3R only, and it cannot change the device's configuration yet.
 */
class ProtocolCaptureBackend implements CaptureBackend {

	private static final int CONNECT_TIMEOUT_MS = 20000;
	private static final int TICK_MS = 20;
	/** How long to listen for a stream left running, and the step while waiting for it to stop. */
	private static final int STREAM_CHECK_MS = 300;
	private static final int STREAM_STOP_TIMEOUT_MS = 3000;
	private static final byte STOP_STREAMING_COMMAND = 0x20;

	private final Object mLock = new Object();
	private BleCentral mCentral;
	private Shimmer3RProtocol mProtocol;
	private Listener mListener;
	private ScheduledExecutorService mTicker;
	private volatile long mHandle = 0;
	private volatile boolean mHandshakeStarted = false;
	private volatile int mBytesBeforeHandshake = 0;
	private volatile double mPacketReceptionRate = Double.NaN;

	@Override
	public String label() {
		return "statemachine";
	}

	@Override
	public void connect(final NativeBleDevice device, final Listener listener) {
		mListener = listener;
		if (device.getProfile() != BleUartProfile.SHIMMER3R) {
			listener.onError("The DEV-1134 state machine supports Shimmer3R only; " + device.getName() + " is not one.");
			return;
		}
		Thread t = new Thread(() -> connectBlocking(device), "ProtocolCapture-connect");
		t.setDaemon(true);
		t.start();
	}

	private void connectBlocking(NativeBleDevice device) {
		try {
			mCentral = BleCentral.getDefault();
			synchronized (mLock) {
				mProtocol = new Shimmer3RProtocol();
				mHandshakeStarted = false;
				mBytesBeforeHandshake = 0;
				mPacketReceptionRate = Double.NaN;
			}
			mListener.onState("CONNECTING (BLE)");
			mHandle = mCentral.connect(device.getId(), BleUartProfile.SHIMMER3R, CONNECT_TIMEOUT_MS, new LinkListener());
			stopStreamLeftRunning();
			synchronized (mLock) {
				mHandshakeStarted = true;
				handle(mProtocol.connect(System.currentTimeMillis()));
			}
			mTicker = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread th = new Thread(r, "ProtocolCapture-tick");
				th.setDaemon(true);
				return th;
			});
			mTicker.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			mListener.onError("Connect failed: " + e.getMessage());
			disconnect();
		}
	}

	/**
	 * A link left open by a crashed earlier session (Windows keeps it) can still be streaming,
	 * and the prototype's handshake does not handle that, so stop it first. Bytes received here
	 * are not passed to the state machine.
	 */
	private void stopStreamLeftRunning() throws NativeBleException, InterruptedException {
		Thread.sleep(STREAM_CHECK_MS);
		if (mBytesBeforeHandshake == 0) {
			return;
		}
		System.out.println("ProtocolCapture: device is already streaming; stopping it before the handshake");
		mCentral.write(mHandle, new byte[] { STOP_STREAMING_COMMAND });
		long deadline = System.currentTimeMillis() + STREAM_STOP_TIMEOUT_MS;
		int before;
		do {
			before = mBytesBeforeHandshake;
			Thread.sleep(STREAM_CHECK_MS);
		} while (mBytesBeforeHandshake != before && System.currentTimeMillis() < deadline);
	}

	private class LinkListener implements BleConnectionListener {
		@Override
		public void onBytes(byte[] data) {
			if (!mHandshakeStarted) {
				mBytesBeforeHandshake += data.length;
				return;
			}
			synchronized (mLock) {
				handle(mProtocol.receive(data, System.currentTimeMillis()));
			}
		}

		@Override
		public void onDisconnected(String reason) {
			mHandle = 0;
			stopTicker();
			mListener.onState("CONNECTION_LOST (" + reason + ")");
		}
	}

	private void tick() {
		boolean failed;
		synchronized (mLock) {
			long now = System.currentTimeMillis();
			if (now >= mProtocol.nextDeadline()) {
				handle(mProtocol.tick(now));
			}
			failed = mProtocol.getState() == State.FAILED;
		}
		if (failed) {
			disconnect();
		}
	}

	/** Called with mLock held. Listener calls must not call back into this backend synchronously. */
	private void handle(ProtocolOutput out) {
		for (byte[] write : out.getWrites()) {
			try {
				mCentral.write(mHandle, write);
			} catch (NativeBleException e) {
				mListener.onError("Write failed: " + e.getMessage());
			}
		}
		for (ProtocolEvent e : out.getEvents()) {
			switch (e.type) {
			case STATE_CHANGED:
				mListener.onState(e.state.toString());
				break;
			case INITIALISED:
				System.out.println("ProtocolCapture: " + e.message);
				mListener.onReady();
				break;
			case SAMPLE:
				double prr = e.sample.getFormatClusterValue("Packet_Reception_Rate_Trial", "CAL");
				if (!Double.isNaN(prr)) {
					mPacketReceptionRate = prr;
				}
				mListener.onSample(e.sample);
				break;
			case ERROR:
				mListener.onError(e.message);
				break;
			case DISCARDED:
				System.out.println("ProtocolCapture: " + e.message);
				break;
			default:
				break;
			}
		}
	}

	@Override
	public void disconnect() {
		stopTicker();
		long handle = mHandle;
		mHandle = 0;
		if (handle != 0 && mCentral != null) {
			try {
				mCentral.disconnect(handle);
			} catch (NativeBleException e) {
				System.out.println("ProtocolCapture: disconnect: " + e.getMessage());
			}
		}
		if (mListener != null) {
			mListener.onState(State.DISCONNECTED.toString());
		}
	}

	private void stopTicker() {
		ScheduledExecutorService ticker = mTicker;
		mTicker = null;
		if (ticker != null) {
			ticker.shutdownNow();
		}
	}

	@Override
	public void startStreaming() {
		synchronized (mLock) {
			handle(mProtocol.startStreaming(System.currentTimeMillis()));
		}
	}

	@Override
	public void stopStreaming() {
		synchronized (mLock) {
			handle(mProtocol.stopStreaming(System.currentTimeMillis()));
		}
	}

	private State state() {
		synchronized (mLock) {
			return mProtocol == null ? State.DISCONNECTED : mProtocol.getState();
		}
	}

	@Override
	public boolean isConnected() {
		State s = state();
		return mHandle != 0 && (s == State.READY || s == State.STARTING || s == State.STREAMING || s == State.STOPPING);
	}

	@Override
	public boolean isStreaming() {
		return mHandle != 0 && state() == State.STREAMING;
	}

	@Override
	public double getSamplingRate() {
		synchronized (mLock) {
			return mProtocol == null ? Double.NaN : mProtocol.getSamplingRate();
		}
	}

	@Override
	public int getMtu() {
		long handle = mHandle;
		if (handle == 0 || mCentral == null) {
			return 0;
		}
		try {
			return mCentral.mtu(handle);
		} catch (NativeBleException e) {
			return 0;
		}
	}

	@Override
	public double getPacketReceptionRate() {
		return mPacketReceptionRate;
	}

	@Override
	public ShimmerDevice getDeviceForPlot() {
		synchronized (mLock) {
			return mProtocol != null && isConnected() ? mProtocol.getDeviceModel() : null;
		}
	}

	@Override
	public boolean canConfigure() {
		return false;
	}
}
