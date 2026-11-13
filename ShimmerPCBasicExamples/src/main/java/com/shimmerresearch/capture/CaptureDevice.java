package com.shimmerresearch.capture;

import java.io.File;
import java.io.IOException;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driver.shimmer2r3.BluetoothModuleVersionDetails;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerBattStatusDetails;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.tools.bluetooth.BasicShimmerBluetoothManagerPc;

/**
 * One Shimmer3 or Shimmer3R over Bluetooth Classic, on the Java driver: its link (through the
 * driver's Bluetooth manager), the commands Capture sends it, and what it shows. No Swing.
 * <p>
 * The driver answers most commands by updating its own state rather than with an event, so the
 * window reads that state back on its refresh timer.
 */
public final class CaptureDevice {

	/** What the window is told; called on the driver's thread */
	public interface Listener {
		void log(String text);
		void stateChanged(String state);
		void sample(ObjectCluster sample, double deviceMs, double hostMs);
	}

	private static final String CAL = CHANNEL_TYPE.CAL.toString();

	private final BasicShimmerBluetoothManagerPc mManager;
	private final Listener mListener;
	private final ReceptionStats mStats = new ReceptionStats(System.nanoTime() / 1000000L);

	private volatile String mPort;
	private volatile String mState = "DISCONNECTED";
	private volatile int mLinkLost;
	private volatile int mErrors;
	private volatile String mLastError = "";
	private volatile double mPrr = Double.NaN;
	private volatile double mBattPercent = Double.NaN;
	private volatile long mClockReadAtHostMs = -1;
	private volatile long mClockValueSeen = 0;

	private final Object mRecordingLock = new Object();
	private CsvRecording mRecording;
	private volatile boolean mRecordRequested;
	private File mRecordFolder;

	public CaptureDevice(BasicShimmerBluetoothManagerPc manager, Listener listener) {
		mManager = manager;
		mListener = listener;
		new BasicProcessWithCallBack() {
			@Override
			protected void processMsgFromCallback(ShimmerMsg msg) {
				onMessage(msg);
			}
		}.setWaitForData(manager.callBackObject);
	}

	// ------------------------------------------------------------------
	// The link
	// ------------------------------------------------------------------

	/** Opens the paired unit's serial port (a COM port on Windows), which connects to it, and runs the driver's handshake */
	public void connect(String port) {
		mPort = port;
		mListener.log("Connecting on " + port);
		mManager.connectShimmerThroughCommPort(port);
	}

	/** Stops a stream, then closes the link */
	public void disconnect() {
		ShimmerBluetooth sb = shimmer();
		if (sb != null && sb.isStreaming()) {
			sb.stopStreaming();
		}
		stopRecording("the link was closed");
		if (mPort != null) {
			mManager.disconnectShimmer(mPort);
		}
	}

	public String getPort() {
		return mPort;
	}

	/** The connected driver device, or null */
	public ShimmerBluetooth shimmer() {
		if (mPort == null) {
			return null;
		}
		ShimmerDevice d = mManager.getShimmerDeviceBtConnected(mPort);
		return d instanceof ShimmerBluetooth ? (ShimmerBluetooth) d : null;
	}

	public String getState() {
		return mState;
	}

	public boolean isReady() {
		return "READY".equals(mState);
	}

	public boolean isStreaming() {
		return "STREAMING".equals(mState);
	}

	/** A connect has begun and the driver has not yet said how it ended */
	public boolean isConnecting() {
		return "CONNECTING".equals(mState);
	}

	public boolean isConnected() {
		return shimmer() != null && !"DISCONNECTED".equals(mState) && !"FAILED".equals(mState) && !"CONNECTING".equals(mState);
	}

	/** CAPTURE.md's states, from the driver's: CONNECTED is READY, a stream (with logging or not) is STREAMING */
	static String captureState(BT_STATE state) {
		switch (state) {
		case CONNECTING:
			return "CONNECTING";
		case CONNECTED:
		case SDLOGGING:
			return "READY";
		case STREAMING:
		case STREAMING_AND_SDLOGGING:
		case STREAMING_LOGGED_DATA:
			return "STREAMING";
		case CONFIGURING:
			return "CONFIGURING";
		case CONNECTION_FAILED:
			return "FAILED";
		case DISCONNECTED:
		case CONNECTION_LOST:
		default:
			return "DISCONNECTED";
		}
	}

	private void onMessage(ShimmerMsg msg) {
		int id = msg.mIdentifier;
		if (id == ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE) {
			CallbackObject cb = (CallbackObject) msg.mB;
			if (cb.mState == null) {
				return;
			}
			if (cb.mState == BT_STATE.CONNECTION_LOST) {
				mLinkLost++;
				mListener.log("Link lost");
				stopRecording("the link was lost");
			}
			if (cb.mState == BT_STATE.CONNECTION_FAILED) {
				error("Connection failed");
			}
			String state = captureState(cb.mState);
			if (!state.equals(mState)) {
				if ("STREAMING".equals(mState) && !"STREAMING".equals(state)) {
					stopRecording("the stream stopped");
				}
				mState = state;
				mListener.log("State " + state + " (driver " + cb.mState + ")");
				mListener.stateChanged(state);
			}
		}
		else if (id == ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE) {
			CallbackObject cb = (CallbackObject) msg.mB;
			if (cb.mIndicator == ShimmerBluetooth.NOTIFICATION_SHIMMER_FULLY_INITIALIZED) {
				mListener.log("Initialised: " + getSummary());
				mListener.stateChanged(mState);
			}
			else if (cb.mIndicator == ShimmerBluetooth.NOTIFICATION_SHIMMER_START_STREAMING) {
				ShimmerBluetooth sb = shimmer();
				mStats.reset(sb == null ? Double.NaN : sb.getSamplingRateShimmer(), monotonicMs());
				mListener.log("Streaming started");
			}
			else if (cb.mIndicator == ShimmerBluetooth.NOTIFICATION_SHIMMER_STOP_STREAMING) {
				mListener.log("Streaming stopped");
				stopRecording("the stream stopped");
			}
		}
		else if (id == ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET) {
			ObjectCluster sample = (ObjectCluster) msg.mB;
			double deviceMs = sample.getFormatClusterValue(Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP, CAL);
			double hostMs = sample.getFormatClusterValue(Configuration.Shimmer3.ObjectClusterSensorName.SYSTEM_TIMESTAMP, CAL);
			if (Double.isNaN(hostMs)) {
				hostMs = System.currentTimeMillis();
			}
			mStats.onSample(deviceMs, hostMs, monotonicMs());
			double prr = sample.getFormatClusterValue("Packet_Reception_Rate_Trial", CAL);
			if (!Double.isNaN(prr)) {
				mPrr = prr;
			}
			double batt = sample.getFormatClusterValue("Batt_Percentage", CAL);
			if (!Double.isNaN(batt)) {
				mBattPercent = batt;
			}
			record(sample, hostMs);
			mListener.sample(sample, deviceMs, hostMs);
		}
	}

	// ------------------------------------------------------------------
	// Commands
	// ------------------------------------------------------------------

	public void readBattery() {
		ShimmerBluetooth sb = require();
		if (sb != null) {
			mListener.log("Read battery");
			sb.readBattery();
		}
	}

	public void readStatus() {
		ShimmerBluetooth sb = require();
		if (sb != null) {
			mListener.log("Read status");
			sb.readStatusLogAndStream();
		}
	}

	public void startStreaming() {
		ShimmerBluetooth sb = require();
		if (sb == null) {
			return;
		}
		try {
			sb.startStreaming();
		} catch (ShimmerException e) {
			error("Start: " + e.getMessage());
		}
	}

	public void stopStreaming() {
		ShimmerBluetooth sb = require();
		if (sb != null) {
			sb.stopStreaming();
		}
	}

	/** Writes the sampling rate, as the driver writes it with its sensors' rates */
	public void writeSamplingRate(double hz) {
		ShimmerBluetooth sb = require();
		if (sb != null) {
			mListener.log("Write sampling rate " + hz + " Hz");
			sb.writeShimmerAndSensorsSamplingRate(hz);
		}
	}

	public void readClock() {
		ShimmerBluetooth sb = require();
		if (sb != null) {
			mListener.log("Read clock");
			mClockReadAtHostMs = System.currentTimeMillis();
			sb.readRealTimeClock();
		}
	}

	/** Sets the device's clock from this host's, then reads it back */
	public void setClock() {
		ShimmerBluetooth sb = require();
		if (sb != null) {
			mListener.log("Set clock from this host");
			sb.writeRealTimeClock();
			mClockReadAtHostMs = System.currentTimeMillis();
			sb.readRealTimeClock();
		}
	}

	/** Toggles the firmware's red LED override, then reads the status, whose flag says whether it is on */
	public void toggleRedLed() {
		ShimmerBluetooth sb = require();
		if (sb != null) {
			mListener.log("Toggle red LED");
			sb.toggleLed();
			sb.readStatusLogAndStream();
		}
	}

	public void readBluetoothModule() {
		ShimmerBluetooth sb = require();
		if (sb != null) {
			mListener.log("Read Bluetooth module");
			sb.readBtFwVersion();
		}
	}

	/** Sets the clock from this host's (unless it logs already), starts logging, then reads the status */
	public void startLogging() {
		ShimmerBluetooth sb = require();
		if (sb == null) {
			return;
		}
		if (!sb.isSDLogging()) {
			sb.writeRealTimeClock();
		}
		mListener.log("Start logging");
		sb.startSDLogging();
		sb.readStatusLogAndStream();
	}

	public void stopLogging() {
		ShimmerBluetooth sb = require();
		if (sb != null) {
			mListener.log("Stop logging");
			sb.stopSDLogging();
			sb.readStatusLogAndStream();
		}
	}

	private ShimmerBluetooth require() {
		ShimmerBluetooth sb = shimmer();
		if (sb == null) {
			error("No device connected");
		}
		return sb;
	}

	private void error(String text) {
		mErrors++;
		mLastError = text;
		mListener.log("ERROR " + text);
	}

	// ------------------------------------------------------------------
	// Recording
	// ------------------------------------------------------------------

	/** Starts a recording with the next sample, in {@code folder} */
	public void startRecording(File folder) {
		synchronized (mRecordingLock) {
			mRecordFolder = folder;
			mRecordRequested = true;
		}
		mListener.log("Recording starts with the next sample");
	}

	public void stopRecording(String why) {
		synchronized (mRecordingLock) {
			mRecordRequested = false;
			if (mRecording != null) {
				try {
					mRecording.close();
					mListener.log("Recording closed (" + why + "): " + mRecording.getRows() + " rows, " + mRecording.getFile());
				} catch (IOException e) {
					error("Recording close: " + e.getMessage());
				}
				mRecording = null;
			}
		}
	}

	public boolean isRecording() {
		return mRecordRequested;
	}

	public File getRecordingFile() {
		synchronized (mRecordingLock) {
			return mRecording == null ? null : mRecording.getFile();
		}
	}

	private void record(ObjectCluster sample, double hostMs) {
		synchronized (mRecordingLock) {
			if (!mRecordRequested) {
				return;
			}
			try {
				if (mRecording == null) {
					ShimmerBluetooth sb = shimmer();
					mRecording = new CsvRecording(mRecordFolder, getPlatform(), sb == null ? null : sb.getMacId(), sample,
							System.currentTimeMillis());
					mListener.log("Recording to " + mRecording.getFile());
				}
				mRecording.append(sample, hostMs);
			} catch (IOException e) {
				error("Recording: " + e.getMessage());
				mRecordRequested = false;
				if (mRecording != null) {
					try {
						mRecording.close();
					} catch (IOException closing) {
						// the write's error is the one reported
					}
					mRecording = null;
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// What it shows
	// ------------------------------------------------------------------

	public ReceptionStats.Snapshot snapshot(boolean endWindow) {
		return mStats.snapshot(monotonicMs(), endWindow);
	}

	public int getLinkLost() {
		return mLinkLost;
	}

	public int getErrors() {
		return mErrors;
	}

	public String getLastError() {
		return mLastError;
	}

	public double getPrr() {
		return mPrr;
	}

	/** {@code Batt_Percentage} from the stream, or the battery read's percent; NaN where neither is known */
	public double getBatteryPercent() {
		if (!Double.isNaN(mBattPercent)) {
			return mBattPercent;
		}
		ShimmerBluetooth sb = shimmer();
		ShimmerBattStatusDetails b = sb == null ? null : sb.getBattStatusDetails();
		if (b != null && b.getBattVoltageParsed() != null && !b.getBattVoltageParsed().isEmpty()) {
			return b.getEstimatedChargePercentage();
		}
		return Double.NaN;
	}

	/** {@code Shimmer3R} or {@code Shimmer3}, as a recording's name has it */
	public String getPlatform() {
		ShimmerBluetooth sb = shimmer();
		return sb != null && sb.isShimmerGen3R() ? "Shimmer3R" : "Shimmer3";
	}

	/** The INITIALISED summary: board, firmware, rate and packet size */
	public String getSummary() {
		ShimmerBluetooth sb = shimmer();
		if (sb == null) {
			return "-";
		}
		return sb.getHardwareVersionParsed() + " " + sb.getExpansionBoardParsedWithVer() + ", "
				+ sb.getFirmwareVersionParsed() + ", " + trim(sb.getSamplingRateShimmer()) + " Hz, packet size "
				+ sb.getPacketSize();
	}

	/** The battery as the last read gave it: volts, percent, the charger's state */
	public String getBatteryDetails() {
		ShimmerBluetooth sb = shimmer();
		ShimmerBattStatusDetails b = sb == null ? null : sb.getBattStatusDetails();
		if (b == null || b.getBattVoltageParsed() == null || b.getBattVoltageParsed().isEmpty()) {
			return "not read";
		}
		return b.getBattVoltageParsed() + ", " + b.getEstimatedChargePercentageParsed() + ", " + b.getChargingStatusParsed();
	}

	/** The status flags as the last status read left them */
	public String getStatusDetails() {
		ShimmerBluetooth sb = shimmer();
		if (sb == null) {
			return "-";
		}
		return "docked " + yes(sb.isDocked()) + ", sensing " + yes(sb.isSensing()) + ", RTC set " + yes(sb.mIsRtcSet)
				+ ", SD logging " + yes(sb.isSDLogging()) + ", streaming " + yes(sb.isStreaming())
				+ ", SD in " + yes(sb.isSDPresent()) + ", SD error " + yes(sb.isSDError())
				+ ", red LED " + yes(sb.mIsRedLedOn) + ", USB " + yes(sb.isUsbPluggedIn());
	}

	public String getMac() {
		ShimmerBluetooth sb = shimmer();
		return sb == null ? "-" : sb.getMacId();
	}

	public String getBluetoothModule() {
		ShimmerBluetooth sb = shimmer();
		BluetoothModuleVersionDetails d = sb == null ? null : sb.getBtFwVerDetails();
		if (d == null || d.mBtModuleVersionParsed == BluetoothModuleVersionDetails.BT_MODULE_VERSION.NOT_READ) {
			return "not read";
		}
		return d.mBtModuleVersionReceived + " (" + d.getUserFriendlyName() + ")";
	}

	/**
	 * The device's clock as last read, in this host's local time, and its difference from this
	 * host's clock when the read was sent (the driver does not report when the reply came).
	 */
	public String getClockDetails() {
		ShimmerBluetooth sb = shimmer();
		if (sb == null || sb.mShimmerLastReadRtcValueMilliSecs <= 0 || mClockReadAtHostMs < 0) {
			return "not read";
		}
		long device = sb.mShimmerLastReadRtcValueMilliSecs;
		if (device != mClockValueSeen) {
			mClockValueSeen = device;
		}
		return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date(device))
				+ String.format(" (device - host: %+d ms)", device - mClockReadAtHostMs);
	}

	private static String yes(boolean b) {
		return b ? "yes" : "no";
	}

	static String trim(double v) {
		return CsvRecording.number(v);
	}

	private static long monotonicMs() {
		return System.nanoTime() / 1000000L;
	}

}
