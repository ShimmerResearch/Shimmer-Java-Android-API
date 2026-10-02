package com.shimmerresearch.pcDriver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

import com.shimmerresearch.bluetooth.BluetoothProgressReportPerCmd;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerDeviceCallbackAdapter;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driver.ThreadSafeByteFifoBuffer;
import com.shimmerresearch.driver.ble.nativeble.BleCentral;
import com.shimmerresearch.driver.ble.nativeble.BleConnectionListener;
import com.shimmerresearch.driver.ble.nativeble.BleUartProfile;
import com.shimmerresearch.driver.ble.nativeble.NativeBleDevice;
import com.shimmerresearch.driver.ble.nativeble.NativeBleException;
import com.shimmerresearch.driver.shimmer2r3.ConfigByteLayoutShimmer3;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.exceptions.ShimmerException;

/**
 * Shimmer3 and Shimmer3R over BLE through the in-process native library (ShimmerBLENativeLib/),
 * instead of the gRPC BLE server that {@link ShimmerGRPC} uses.
 * <p>
 * The device must have been seen by a {@link BleCentral} scan before {@link #connect} is called:
 * on Windows and macOS the native library cannot connect to an address it has not seen
 * advertising.
 */
public class ShimmerBLENative extends ShimmerBluetooth implements Serializable {

	private static final long serialVersionUID = -2735167920183245913L;

	public static final int CONNECT_TIMEOUT_MS = 20000;
	private static final int FIFO_CAPACITY = 1000000;
	/** How long to listen for a stream already running, and the step while waiting for one to stop. */
	private static final int STREAM_CHECK_MS = 300;
	private static final int STREAM_STOP_TIMEOUT_MS = 3000;

	/** What {@link BleCentral} connects with: the MAC address on Windows, a CoreBluetooth UUID on macOS. */
	protected String mDeviceId;
	/** The advertised name, e.g. "Shimmer3R-2F31-BLE". Selects the BLE profile. */
	protected String mDeviceName;

	protected transient BleCentral mCentral;
	/** Volatile: replaced on connect while the dispatcher thread may be writing to it. */
	protected transient volatile ThreadSafeByteFifoBuffer mBuffer;
	protected transient volatile long mHandle = 0;
	protected transient ShimmerDeviceCallbackAdapter mDeviceCallbackAdapter = new ShimmerDeviceCallbackAdapter(this);
	/** Makes the several ways a dropped link is noticed handle it once per connection. */
	private transient AtomicBoolean mLinkLostHandled = new AtomicBoolean(false);

	/**
	 * @param deviceId   the scan result's ID ({@link NativeBleDevice#getId()})
	 * @param deviceName the advertised name ({@link NativeBleDevice#getName()})
	 */
	public ShimmerBLENative(String deviceId, String deviceName) {
		super();
		mDeviceId = deviceId;
		mDeviceName = deviceName;
		mComPort = deviceName;
		mMyBluetoothAddress = "";
		mUseProcessingThread = true;
	}

	public ShimmerBLENative(NativeBleDevice device) {
		this(device.getId(), device.getName());
		if (!device.getMacId().isEmpty()) {
			mMyBluetoothAddress = device.getMacId();
			setMacIdFromUart(device.getMacId());
		}
	}

	public String getDeviceId() {
		return mDeviceId;
	}

	public String getDeviceName() {
		return mDeviceName;
	}

	/** The negotiated ATT MTU, or 0 when not connected. */
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

	/** Returns immediately; the connection is made on a background thread and reported through state changes. */
	@Override
	public void connect(String address, String bluetoothLibrary) {
		setBluetoothRadioState(BT_STATE.CONNECTING);
		Thread connectThread = new Thread(new Runnable() {
			@Override
			public void run() {
				connectBlocking();
			}
		}, "ShimmerBLENative-connect-" + mDeviceName);
		connectThread.setDaemon(true);
		connectThread.start();
	}

	private void connectBlocking() {
		BleUartProfile profile = BleUartProfile.fromDeviceName(mDeviceName);
		if (profile == null || !profile.isShimmer3Family()) {
			consolePrintLn("ShimmerBLENative: " + mDeviceName + " is not a Shimmer3 or Shimmer3R BLE name");
			setBluetoothRadioState(BT_STATE.DISCONNECTED);
			return;
		}
		try {
			mCentral = BleCentral.getDefault();
			mBuffer = new ThreadSafeByteFifoBuffer(FIFO_CAPACITY);
			mLinkLostHandled = new AtomicBoolean(false);
			mHandle = mCentral.connect(mDeviceId, profile, CONNECT_TIMEOUT_MS, new LinkListener());
			consolePrintLn("BLE connected to " + mDeviceName + " as " + profile.label + ", MTU " + getMtu());
			stopStreamLeftRunning();

			mIOThread = new IOThread();
			mIOThread.start();
			if (mUseProcessingThread) {
				mPThread = new ProcessingThread();
				mPThread.start();
			}
			initialize();
		} catch (NativeBleException e) {
			consolePrintLn("BLE connect to " + mDeviceName + " failed: " + e.getMessage());
			mHandle = 0;
			setBluetoothRadioState(BT_STATE.DISCONNECTED);
		}
	}

	/**
	 * If the device is already streaming, stops it before the driver's handshake, which cannot run
	 * over a stream. This happens after a crash: Windows keeps the BLE link open when the process
	 * that owned it is killed, and the device keeps streaming into it until another process takes
	 * the link over.
	 */
	private void stopStreamLeftRunning() throws NativeBleException {
		threadSleep(STREAM_CHECK_MS);
		if (mBuffer.size() == 0) {
			return;
		}
		consolePrintLn("BLE: " + mDeviceName + " is already streaming (link left open by an earlier session); stopping it");
		mCentral.write(mHandle, new byte[] { STOP_STREAMING_COMMAND });
		long deadline = System.currentTimeMillis() + STREAM_STOP_TIMEOUT_MS;
		int before;
		do {
			before = mBuffer.size();
			threadSleep(STREAM_CHECK_MS);
		} while (mBuffer.size() != before && System.currentTimeMillis() < deadline);
		// Discard the stream's tail and the stop command's ACK; the handshake starts clean.
		mBuffer = new ThreadSafeByteFifoBuffer(FIFO_CAPACITY);
	}

	private class LinkListener implements BleConnectionListener {
		@Override
		public void onBytes(byte[] data) {
			ThreadSafeByteFifoBuffer buffer = mBuffer;
			if (buffer == null) {
				return;
			}
			try {
				buffer.write(data);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		@Override
		public void onDisconnected(String reason) {
			consolePrintLn("BLE link to " + mDeviceName + " lost: " + reason);
			mHandle = 0;
			handleLinkLost();
		}
	}

	/**
	 * Runs {@link #connectionLost()} on its own thread. It joins the IO and processing threads, so it
	 * must not run on either of them (a failed write happens on the IO thread) or on the
	 * BleCentral dispatcher, which other devices share.
	 */
	private void handleLinkLost() {
		// Null after deserialisation (deepClone), as for every transient field here.
		AtomicBoolean handled = mLinkLostHandled;
		if (handled != null && !handled.compareAndSet(false, true)) {
			return;
		}
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				connectionLost();
			}
		}, "ShimmerBLENative-lost-" + mDeviceName);
		t.setDaemon(true);
		t.start();
	}

	@Override
	protected boolean bytesAvailableToBeRead() {
		ThreadSafeByteFifoBuffer buffer = mBuffer;
		return buffer != null && buffer.size() > 0;
	}

	@Override
	protected int availableBytes() {
		ThreadSafeByteFifoBuffer buffer = mBuffer;
		return buffer == null ? 0 : buffer.size();
	}

	@Override
	protected void writeBytes(byte[] data) {
		long handle = mHandle;
		if (handle == 0 || mCentral == null) {
			consolePrintLn("BLE write ignored: " + mDeviceName + " is not connected");
			return;
		}
		try {
			mCentral.write(handle, data);
		} catch (NativeBleException e) {
			consolePrintLn("BLE write to " + mDeviceName + " failed: " + e.getMessage());
			handleLinkLost();
		}
	}

	@Override
	protected byte[] readBytes(int numberofBytes) {
		try {
			return mBuffer.read(numberofBytes);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return null;
	}

	@Override
	protected byte readByte() {
		try {
			return mBuffer.read();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return -1;
	}

	@Override
	public void disconnect() throws ShimmerException {
		stopAllTimers();
		long handle = mHandle;
		mHandle = 0;
		if (handle != 0 && mCentral != null) {
			try {
				mCentral.disconnect(handle);
			} catch (NativeBleException e) {
				consolePrintLn("BLE disconnect from " + mDeviceName + ": " + e.getMessage());
			}
		}
		closeConnection();
		setBluetoothRadioState(BT_STATE.DISCONNECTED);
	}

	private void closeConnection() {
		try {
			if (mIOThread != null) {
				mIOThread.stop = true;
				mIOThread.interrupt();
				joinUnlessCurrent(mIOThread);
				mIOThread = null;
			}
			if (mPThread != null) {
				mPThread.stop = true;
				mPThread.interrupt();
				joinUnlessCurrent(mPThread);
				mPThread = null;
			}
			mIsStreaming = false;
			mIsInitialised = false;
		} catch (Exception ex) {
			consolePrintException(ex.getMessage(), ex.getStackTrace());
		}
	}

	private static void joinUnlessCurrent(Thread thread) throws InterruptedException {
		if (thread != Thread.currentThread()) {
			thread.join();
		}
	}

	@Override
	protected void stop() {
		try {
			disconnect();
		} catch (ShimmerException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void connectionLost() {
		try {
			disconnect();
		} catch (ShimmerException e) {
			e.printStackTrace();
		}
		setBluetoothRadioState(BT_STATE.CONNECTION_LOST);
	}

	@Override
	protected void sendProgressReport(BluetoothProgressReportPerCmd pr) {
		mDeviceCallbackAdapter.sendProgressReport(pr);
	}

	@Override
	protected void isReadyForStreaming() {
		mDeviceCallbackAdapter.isReadyForStreaming();
		restartTimersIfNull();
	}

	@Override
	protected void isNowStreaming() {
		mDeviceCallbackAdapter.isNowStreaming();
	}

	@Override
	protected void hasStopStreaming() {
		mDeviceCallbackAdapter.hasStopStreaming();
	}

	@Override
	protected void sendStatusMsgPacketLossDetected() {
	}

	@Override
	protected void inquiryDone() {
		mDeviceCallbackAdapter.inquiryDone();
		isReadyForStreaming();
	}

	@Override
	protected void sendStatusMSGtoUI(String msg) {
	}

	@Override
	protected void printLogDataForDebugging(String msg) {
		consolePrintLn(msg);
	}

	@Override
	public void startOperation(BT_STATE currentOperation) {
		this.startOperation(currentOperation, 1);
		consolePrintLn(currentOperation + " START");
	}

	@Override
	public void startOperation(BT_STATE currentOperation, int totalNumOfCmds) {
		mDeviceCallbackAdapter.startOperation(currentOperation, totalNumOfCmds);
	}

	@Override
	public void finishOperation(BT_STATE state) {
		mDeviceCallbackAdapter.finishOperation(state);
	}

	@Override
	public boolean setBluetoothRadioState(BT_STATE state) {
		boolean isChanged = super.setBluetoothRadioState(state);
		mDeviceCallbackAdapter.setBluetoothRadioState(state, isChanged);
		return isChanged;
	}

	@Override
	protected void eventLogAndStreamStatusChanged(byte currentCommand) {
		if (currentCommand == STOP_LOGGING_ONLY_COMMAND) {
			if (mIsStreaming) {
				setBluetoothRadioState(BT_STATE.STREAMING);
			} else if (isConnected()) {
				setBluetoothRadioState(BT_STATE.CONNECTED);
			} else {
				setBluetoothRadioState(BT_STATE.DISCONNECTED);
			}
		} else {
			if (mIsStreaming && isSDLogging()) {
				setBluetoothRadioState(BT_STATE.STREAMING_AND_SDLOGGING);
			} else if (mIsStreaming) {
				setBluetoothRadioState(BT_STATE.STREAMING);
			} else if (isSDLogging()) {
				setBluetoothRadioState(BT_STATE.SDLOGGING);
			} else {
				if (!mIsStreaming && !isSDLogging() && isConnected() && mBluetoothRadioState != BT_STATE.CONNECTED) {
					setBluetoothRadioState(BT_STATE.CONNECTED);
				}
				CallbackObject callBackObject = new CallbackObject(NOTIFICATION_SHIMMER_STATE_CHANGE, mBluetoothRadioState, getMacId(), getComPort());
				sendCallBackMsg(MSG_IDENTIFIER_STATE_CHANGE, callBackObject);
			}
		}
	}

	@Override
	protected void batteryStatusChanged() {
		mDeviceCallbackAdapter.batteryStatusChanged();
	}

	@Override
	protected void dockedStateChange() {
		mDeviceCallbackAdapter.dockedStateChange();
	}

	@Override
	public ShimmerDevice deepClone() {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(this);

			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (ShimmerDevice) ois.readObject();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	protected void interpretDataPacketFormat(Object object, COMMUNICATION_TYPE commType) {
	}

	@Override
	public void createConfigBytesLayout() {
		if (mShimmerVerObject.mHardwareVersion == HW_ID.UNKNOWN) {
			mConfigByteLayout = new ConfigByteLayoutShimmer3(getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal(), HW_ID.SHIMMER_3);
		} else {
			mConfigByteLayout = new ConfigByteLayoutShimmer3(getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal(), mShimmerVerObject.mHardwareVersion);
		}
	}

	@Override
	protected void dataHandler(ObjectCluster ojc) {
		mDeviceCallbackAdapter.dataHandler(ojc);
	}

	@Override
	protected void processMsgFromCallback(ShimmerMsg shimmerMSG) {
	}

	// Overridden because ShimmerDevice looks the label up in a different map, as in ShimmerGRPC.
	@Override
	public String getSensorLabel(int sensorKey) {
		super.getSensorLabel(sensorKey);
		SensorDetails sensor = mSensorMap.get(sensorKey);
		if (sensor != null) {
			return sensor.mSensorDetailsRef.mGuiFriendlyLabel;
		}
		return null;
	}
}
