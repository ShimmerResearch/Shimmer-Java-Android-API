package com.shimmerresearch.simpleexamples;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ble.nativeble.BleCentral;
import com.shimmerresearch.driver.ble.nativeble.BleConnectionListener;
import com.shimmerresearch.driver.ble.nativeble.BleScanResult;
import com.shimmerresearch.driver.ble.nativeble.BleUartProfile;
import com.shimmerresearch.driver.ble.nativeble.NativeBleException;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.logandstream.Channel;
import com.shimmerresearch.logandstream.Event;
import com.shimmerresearch.logandstream.LogAndStreamProtocol.State;
import com.shimmerresearch.logandstream.LogAndStreamSession;

/**
 * Drives a Shimmer3 (LogAndStream v1.01.003 onwards) or Shimmer3R through shimmer-logandstream,
 * the Rust LogAndStream library, over native BLE. The binding's {@link LogAndStreamSession} runs
 * the protocol; all this class adds is the BLE link and the mapping of events onto the capture app,
 * samples becoming ObjectClusters. It runs the firmware's data-rate test too, but cannot change the
 * device's configuration yet.
 * <p>
 * Built only when a checkout of shimmer-logandstream sits beside this repository (see
 * build.gradle), so the app finds it by name.
 */
class RustLogAndStreamCaptureDriver implements CaptureDriver {

	private static final int CONNECT_TIMEOUT_MS = 20000;
	private static final String PACKET_RECEPTION_RATE = "Packet_Reception_Rate_Trial";
	private static final String SYSTEM_TIMESTAMP = "System_Timestamp";

	private BleCentral mCentral;
	private Listener mListener;
	private volatile LogAndStreamSession mSession;
	private volatile long mHandle = 0;
	private volatile String mDeviceName = "";
	private volatile double mPacketReceptionRate = Double.NaN;
	private volatile String mDeviceSummary;

	@Override
	public String label() {
		return "rust-logandstream";
	}

	@Override
	public void connect(final BleScanResult device, final Listener listener) {
		mListener = listener;
		if (device.getProfile() != BleUartProfile.SHIMMER3R && device.getProfile() != BleUartProfile.SHIMMER3) {
			listener.onError("The Rust LogAndStream library supports Shimmer3 and Shimmer3R; " + device.getName() + " is neither.");
			return;
		}
		Thread t = new Thread(() -> connectBlocking(device), "RustLogAndStream-connect");
		t.setDaemon(true);
		t.start();
	}

	private void connectBlocking(BleScanResult device) {
		try {
			mCentral = BleCentral.getDefault();
			mDeviceName = device.getName();
			mPacketReceptionRate = Double.NaN;
			mDeviceSummary = null;
			LogAndStreamSession session = new LogAndStreamSession(bytes -> mCentral.write(mHandle, bytes), this::onEvent);
			mSession = session;
			mListener.onState("CONNECTING (BLE)");
			mHandle = mCentral.connect(device.getId(), device.getProfile(), CONNECT_TIMEOUT_MS,
					new BleConnectionListener() {
						@Override
						public void onBytes(byte[] data) {
							session.onBytes(data);
						}

						@Override
						public void onDisconnected(String reason) {
							mHandle = 0;
							session.onLinkLost(reason);
						}
					});
			session.connect();
		} catch (Exception | LinkageError e) {
			// LinkageError: the native library not found (UnsatisfiedLinkError), or the binding unusable.
			mListener.onError("Connect failed: " + e.getMessage());
			disconnect();
		}
	}

	/** On the session's event thread. */
	private void onEvent(Event e) {
		switch (e.type) {
		case STATE_CHANGED:
			mListener.onState(e.state.toString());
			if (e.state == State.FAILED) {
				disconnect();
			}
			break;
		case INITIALISED:
			mDeviceSummary = e.message;
			mListener.onReady();
			break;
		case SAMPLE:
			List<Channel> channels = channels();
			ObjectCluster sample = toObjectCluster(channels, e.values, e.packet, mDeviceName);
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
			mListener.onInfo(e.message);
			break;
		case DATA_RATE_PROGRESS:
			mListener.onDataRate(false, e.dataRate.kibPerSecond, e.dataRate.missingPackets, e.dataRate.toString());
			break;
		case DATA_RATE_RESULT:
			mListener.onDataRate(true, e.dataRate.kibPerSecond, e.dataRate.missingPackets, e.message);
			break;
		default:
			break;
		}
	}

	private List<Channel> channels() {
		LogAndStreamSession session = mSession;
		return session == null ? Collections.<Channel>emptyList() : session.getChannels();
	}

	@Override
	public void disconnect() {
		// Close the session first, so the transport's own disconnect is not reported as a lost link.
		LogAndStreamSession session = mSession;
		if (session != null) {
			session.close();
		}
		long handle = mHandle;
		mHandle = 0;
		if (handle != 0 && mCentral != null) {
			try {
				mCentral.disconnect(handle);
			} catch (NativeBleException e) {
				System.out.println("RustLogAndStream: disconnect: " + e.getMessage());
			}
		}
		if (mListener != null) {
			mListener.onState(State.DISCONNECTED.toString());
		}
	}

	@Override
	public void startStreaming() {
		LogAndStreamSession session = mSession;
		if (session != null) {
			session.startStreaming();
		}
	}

	@Override
	public void stopStreaming() {
		LogAndStreamSession session = mSession;
		if (session != null) {
			session.stopStreaming();
		}
	}

	private State state() {
		LogAndStreamSession session = mSession;
		return session == null ? State.DISCONNECTED : session.getState();
	}

	@Override
	public boolean isConnected() {
		State s = state();
		return mHandle != 0 && (s == State.READY || s == State.STARTING || s == State.STREAMING || s == State.STOPPING
				|| s == State.TESTING);
	}

	@Override
	public boolean isStreaming() {
		return mHandle != 0 && state() == State.STREAMING;
	}

	@Override
	public boolean isIdle() {
		return mHandle != 0 && state() == State.READY;
	}

	@Override
	public double getSamplingRate() {
		LogAndStreamSession session = mSession;
		return session == null ? Double.NaN : session.getSamplingRate();
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
	public String getDeviceSummary() {
		return mDeviceSummary;
	}

	@Override
	public List<String[]> getSignalsForPlot() {
		if (!isConnected()) {
			return null;
		}
		List<String[]> signals = new ArrayList<String[]>();
		for (Channel c : channels()) {
			signals.add(new String[] { mDeviceName, c.name, format(c), c.units });
		}
		return signals;
	}

	@Override
	public boolean canConfigure() {
		return false;
	}

	@Override
	public boolean canTestDataRate() {
		return true;
	}

	@Override
	public void startDataRateTest(int durationMs) {
		LogAndStreamSession session = mSession;
		if (session != null) {
			session.startDataRateTest(durationMs);
		}
	}

	private static String format(Channel channel) {
		return channel.format == Channel.Format.CALIBRATED ? CHANNEL_TYPE.CAL.toString() : CHANNEL_TYPE.UNCAL.toString();
	}

	/**
	 * A sample as the Java driver's ObjectCluster, for code written against those (plots, the CSV
	 * log); filled as buildMsg fills one: the device's name, the raw packet, the PC time, then every
	 * channel. The library's channels already are the driver's: the same names, units and order,
	 * each calibrated (CAL) and raw (UNCAL).
	 */
	private static ObjectCluster toObjectCluster(List<Channel> channels, double[] values, byte[] packet,
			String deviceName) {
		ObjectCluster oc = new ObjectCluster();
		oc.setShimmerName(deviceName);
		oc.mRawData = packet;
		for (int i = 0; i < channels.size() && i < values.length; i++) {
			Channel c = channels.get(i);
			oc.addDataToMap(c.name, format(c), c.units, values[i]);
			if (c.name.equals(SYSTEM_TIMESTAMP) && c.format == Channel.Format.CALIBRATED) {
				oc.setSystemTimeStamp(values[i]);
			}
		}
		return oc;
	}
}
