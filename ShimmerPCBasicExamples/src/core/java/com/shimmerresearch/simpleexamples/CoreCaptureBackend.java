package com.shimmerresearch.simpleexamples;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ble.nativeble.BleCentral;
import com.shimmerresearch.driver.ble.nativeble.BleConnectionListener;
import com.shimmerresearch.driver.ble.nativeble.BleUartProfile;
import com.shimmerresearch.driver.ble.nativeble.NativeBleDevice;
import com.shimmerresearch.driver.ble.nativeble.NativeBleException;
import com.shimmerresearch.protocolcore.CoreChannel;
import com.shimmerresearch.protocolcore.CoreEvent;
import com.shimmerresearch.protocolcore.CoreHost;
import com.shimmerresearch.protocolcore.LogAndStreamProtocolCore.State;

/**
 * The Rust protocol core (shimmer-protocol-core, through its Java binding) over native BLE. The
 * binding's {@link CoreHost} runs the protocol; all this class adds is the BLE link and the mapping
 * of events onto the capture app, samples becoming ObjectClusters. Shimmer3 (LogAndStream v1.1.3
 * onwards) and Shimmer3R; it cannot change the device's configuration yet.
 * <p>
 * Built only when a checkout of shimmer-protocol-core sits beside this repository (see
 * build.gradle), so the app finds it by name.
 */
class CoreCaptureBackend implements CaptureBackend {

	private static final int CONNECT_TIMEOUT_MS = 20000;
	private static final String PACKET_RECEPTION_RATE = "Packet_Reception_Rate_Trial";

	private BleCentral mCentral;
	private Listener mListener;
	private volatile CoreHost mHost;
	private volatile long mHandle = 0;
	private volatile String mDeviceName = "";
	private volatile double mPacketReceptionRate = Double.NaN;

	@Override
	public String label() {
		return "core";
	}

	@Override
	public void connect(final NativeBleDevice device, final Listener listener) {
		mListener = listener;
		if (device.getProfile() != BleUartProfile.SHIMMER3R && device.getProfile() != BleUartProfile.SHIMMER3) {
			listener.onError("The protocol core supports Shimmer3 and Shimmer3R; " + device.getName() + " is neither.");
			return;
		}
		Thread t = new Thread(() -> connectBlocking(device), "CoreCapture-connect");
		t.setDaemon(true);
		t.start();
	}

	private void connectBlocking(NativeBleDevice device) {
		try {
			mCentral = BleCentral.getDefault();
			mDeviceName = device.getName();
			mPacketReceptionRate = Double.NaN;
			CoreHost host = new CoreHost(bytes -> mCentral.write(mHandle, bytes), this::onEvent);
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
		} catch (Exception | UnsatisfiedLinkError e) {
			mListener.onError("Connect failed: " + e.getMessage());
			disconnect();
		}
	}

	/** On the host's event thread. */
	private void onEvent(CoreEvent e) {
		switch (e.type) {
		case STATE_CHANGED:
			mListener.onState(e.state.toString());
			if (e.state == State.FAILED) {
				disconnect();
			}
			break;
		case INITIALISED:
			System.out.println("CoreCapture: " + e.message);
			mListener.onReady();
			break;
		case SAMPLE:
			List<CoreChannel> channels = channels();
			ObjectCluster sample = CoreSamples.toObjectCluster(channels, e.values, e.packet, mDeviceName);
			double prr = sample.getFormatClusterValue(PACKET_RECEPTION_RATE, "CAL");
			if (!Double.isNaN(prr)) {
				mPacketReceptionRate = prr;
			}
			mListener.onSample(sample);
			break;
		case ERROR:
			mListener.onError(e.message);
			break;
		case LINK_LOST:
			mListener.onState("CONNECTION_LOST (" + e.message + ")");
			break;
		case DISCARDED:
			System.out.println("CoreCapture: " + e.message);
			break;
		default:
			break;
		}
	}

	private List<CoreChannel> channels() {
		CoreHost host = mHost;
		return host == null ? Collections.<CoreChannel>emptyList() : host.getChannels();
	}

	@Override
	public void disconnect() {
		// Close the host first, so the transport's own disconnect is not reported as a lost link.
		CoreHost host = mHost;
		if (host != null) {
			host.close();
		}
		long handle = mHandle;
		mHandle = 0;
		if (handle != 0 && mCentral != null) {
			try {
				mCentral.disconnect(handle);
			} catch (NativeBleException e) {
				System.out.println("CoreCapture: disconnect: " + e.getMessage());
			}
		}
		if (mListener != null) {
			mListener.onState(State.DISCONNECTED.toString());
		}
	}

	@Override
	public void startStreaming() {
		CoreHost host = mHost;
		if (host != null) {
			host.startStreaming();
		}
	}

	@Override
	public void stopStreaming() {
		CoreHost host = mHost;
		if (host != null) {
			host.stopStreaming();
		}
	}

	private State state() {
		CoreHost host = mHost;
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
		CoreHost host = mHost;
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

	/** None: the core describes its channels itself (see {@link #getSignalsForPlot()}). */
	@Override
	public ShimmerDevice getDeviceForPlot() {
		return null;
	}

	@Override
	public List<String[]> getSignalsForPlot() {
		if (!isConnected()) {
			return null;
		}
		List<String[]> signals = new ArrayList<String[]>();
		for (CoreChannel c : channels()) {
			signals.add(new String[] { mDeviceName, c.name, CoreSamples.format(c), c.units });
		}
		return signals;
	}

	@Override
	public boolean canConfigure() {
		return false;
	}
}
