package com.shimmerresearch.simpleexamples;

import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ble.nativeble.BleCentral;
import com.shimmerresearch.driver.ble.nativeble.BleConnectionListener;
import com.shimmerresearch.driver.ble.nativeble.BleUartProfile;
import com.shimmerresearch.driver.ble.nativeble.NativeBleDevice;
import com.shimmerresearch.driver.ble.nativeble.NativeBleException;
import com.shimmerresearch.protocol.ProtocolEvent;
import com.shimmerresearch.protocol.ProtocolHost;
import com.shimmerresearch.protocol.LogAndStreamProtocol;
import com.shimmerresearch.protocol.LogAndStreamProtocol.State;

/**
 * The DEV-1134 protocol state machine ({@link LogAndStreamProtocol}) over native BLE. The
 * {@link ProtocolHost} runs the protocol; all this class adds is the BLE link and the mapping of
 * events onto the capture app. Shimmer3 (LogAndStream v1.1.3 onwards) and Shimmer3R; it cannot
 * change the device's configuration yet.
 */
class ProtocolCaptureBackend implements CaptureBackend {

	private static final int CONNECT_TIMEOUT_MS = 20000;

	private BleCentral mCentral;
	private Listener mListener;
	private volatile ProtocolHost mHost;
	private volatile long mHandle = 0;
	private volatile double mPacketReceptionRate = Double.NaN;

	@Override
	public String label() {
		return "statemachine";
	}

	@Override
	public void connect(final NativeBleDevice device, final Listener listener) {
		mListener = listener;
		if (device.getProfile() != BleUartProfile.SHIMMER3R && device.getProfile() != BleUartProfile.SHIMMER3) {
			listener.onError("The DEV-1134 state machine supports Shimmer3 and Shimmer3R; " + device.getName() + " is neither.");
			return;
		}
		Thread t = new Thread(() -> connectBlocking(device), "ProtocolCapture-connect");
		t.setDaemon(true);
		t.start();
	}

	private void connectBlocking(NativeBleDevice device) {
		try {
			mCentral = BleCentral.getDefault();
			mPacketReceptionRate = Double.NaN;
			ProtocolHost host = new ProtocolHost(bytes -> mCentral.write(mHandle, bytes), this::onEvent);
			mHost = host;
			mListener.onState("CONNECTING (BLE)");
			mHandle = mCentral.connect(device.getId(), device.getProfile(), CONNECT_TIMEOUT_MS,
					new BleConnectionListener() {
						@Override
						public void onBytes(byte[] data) {
							host.onBytes(data);
						}

						@Override
						public void onDisconnected(String reason) {
							mHandle = 0;
							host.onLinkLost(reason);
						}
					});
			host.connect();
		} catch (Exception e) {
			mListener.onError("Connect failed: " + e.getMessage());
			disconnect();
		}
	}

	/** On the host's event thread. */
	private void onEvent(ProtocolEvent e) {
		switch (e.type) {
		case STATE_CHANGED:
			mListener.onState(e.state.toString());
			if (e.state == State.FAILED) {
				disconnect();
			}
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
		case LINK_LOST:
			mListener.onState("CONNECTION_LOST (" + e.message + ")");
			break;
		case DISCARDED:
			System.out.println("ProtocolCapture: " + e.message);
			break;
		default:
			break;
		}
	}

	@Override
	public void disconnect() {
		// Close the host first, so the transport's own disconnect is not reported as a lost link.
		ProtocolHost host = mHost;
		if (host != null) {
			host.close();
		}
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

	@Override
	public void startStreaming() {
		ProtocolHost host = mHost;
		if (host != null) {
			host.startStreaming();
		}
	}

	@Override
	public void stopStreaming() {
		ProtocolHost host = mHost;
		if (host != null) {
			host.stopStreaming();
		}
	}

	private State state() {
		ProtocolHost host = mHost;
		return host == null ? State.DISCONNECTED : host.getState();
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
		ProtocolHost host = mHost;
		return host == null ? Double.NaN : host.getSamplingRate();
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
		ProtocolHost host = mHost;
		return host != null && isConnected() ? host.getDeviceModel() : null;
	}

	@Override
	public boolean canConfigure() {
		return false;
	}
}
