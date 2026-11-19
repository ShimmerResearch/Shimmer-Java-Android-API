package com.shimmerresearch.driver.ble.nativeble;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_CRC_MODE;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;

/**
 * A Shimmer3 or Shimmer3R over BLE through the in-process native library (shimmerble/), as the
 * {@link ByteCommunication} that ShimmerPC talks through in place of its COM port
 * (ByteCommunicationJSSC). The device object, its protocol and its state are the same whichever
 * way the bytes travel (DEV-1504).
 * <p>
 * Failures are reported as jssc exceptions, which is the interface's contract, so ShimmerPC
 * handles a dropped BLE link as it handles a dropped COM port: a {@link SerialPortException} from
 * {@link #getInputBufferBytesCount()}, {@link #readBytes(int, int)} or {@link #writeBytes(byte[])}
 * means the connection is lost.
 * <p>
 * The device must have been seen by a {@link BleCentral} scan before {@link #openPort()}: on
 * Windows and macOS the native library cannot connect to a device it has not seen advertising.
 */
public class ByteCommunicationNativeBle implements ByteCommunication {

	/** Kept well inside ShimmerPC's 20 s connecting timeout, which also covers the handshake. */
	public static final int CONNECT_TIMEOUT_MS = 15000;
	/** How long to listen for a stream already running, and the step while waiting for one to stop. */
	private static final int STREAM_CHECK_MS = 300;
	private static final int STREAM_STOP_TIMEOUT_MS = 3000;
	private static final int INITIAL_BUFFER_SIZE = 4096;

	private final String mDeviceId;
	private final String mDeviceName;
	/** Null for the shared {@link BleCentral#getDefault()}; tests pass one over a fake radio. */
	private final BleCentral mCentralToUse;
	private final UtilShimmer mUtil = new UtilShimmer(getClass().getSimpleName(), true);

	private BleCentral mCentral;
	private volatile long mHandle = 0;
	private volatile boolean mIsOpened = false;
	/** Set when the link drops while open; reads, writes and byte counts then fail. */
	private volatile String mLinkLostReason = null;

	/** Received bytes not yet read: a growable ring buffer, guarded by mLock. */
	private final Object mLock = new Object();
	private byte[] mBuffer = new byte[INITIAL_BUFFER_SIZE];
	private int mStart = 0;
	private int mCount = 0;

	/** @param device a scan result for the device */
	public ByteCommunicationNativeBle(BleScanResult device) {
		this(device.getId(), device.getName(), null);
	}

	/**
	 * @param deviceId   the scan result's ID ({@link BleScanResult#getId()})
	 * @param deviceName the advertised name ({@link BleScanResult#getName()}), which selects the BLE profile
	 * @param central    the central to connect through, or null for {@link BleCentral#getDefault()}
	 */
	public ByteCommunicationNativeBle(String deviceId, String deviceName, BleCentral central) {
		mDeviceId = deviceId;
		mDeviceName = deviceName;
		mCentralToUse = central;
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

	@Override
	public boolean openPort() throws SerialPortException {
		BleUartProfile profile = BleUartProfile.fromDeviceName(mDeviceName);
		if (profile == null || !profile.isShimmer3Family()) {
			throw new SerialPortException(mDeviceName, "openPort()", "not a Shimmer3 or Shimmer3R BLE name");
		}
		clearBuffer();
		mLinkLostReason = null;
		try {
			mCentral = mCentralToUse!=null ? mCentralToUse : BleCentral.getDefault();
			mHandle = mCentral.connect(mDeviceId, profile, CONNECT_TIMEOUT_MS, new LinkListener());
			mIsOpened = true;
			mUtil.consolePrintLn("BLE connected to " + mDeviceName + " as " + profile.label + ", MTU " + getMtu());
			stopStreamLeftRunning();
			return true;
		} catch (NativeBleException e) {
			closePort();
			throw new SerialPortException(mDeviceName, "openPort()", e.getMessage());
		}
	}

	/**
	 * If the device is already streaming, stops it before the driver's handshake, which cannot run
	 * over a stream. This happens after a crash: Windows keeps the BLE link open when the process
	 * that owned it is killed, and the device keeps streaming into it until another process takes
	 * the link over. A COM port has no such case: closing it ends the Bluetooth Classic link.
	 * <p>
	 * Such a device also keeps the checksum mode its earlier session set, until it is
	 * disconnected, while the handshake expects checksums off; so they are turned off here too.
	 * Firmware too old for checksums cannot have them on, and does not ACK the command.
	 */
	private void stopStreamLeftRunning() throws NativeBleException {
		sleep(STREAM_CHECK_MS);
		if (bufferedCount() == 0) {
			return;
		}
		mUtil.consolePrintLn("BLE: " + mDeviceName + " is already streaming (link left open by an earlier session); stopping it");
		mCentral.write(mHandle, new byte[] { ShimmerObject.STOP_STREAMING_COMMAND });
		long deadline = System.currentTimeMillis() + STREAM_STOP_TIMEOUT_MS;
		int before;
		do {
			before = bufferedCount();
			sleep(STREAM_CHECK_MS);
		} while (bufferedCount() != before && System.currentTimeMillis() < deadline);
		mCentral.write(mHandle, new byte[] { ShimmerObject.SET_CRC_COMMAND, (byte) BT_CRC_MODE.OFF.ordinal() });
		sleep(STREAM_CHECK_MS);
		// Discard the stream's tail and both commands' ACKs; the handshake starts clean.
		clearBuffer();
	}

	@Override
	public boolean isOpened() {
		return mIsOpened;
	}

	@Override
	public boolean closePort() {
		mIsOpened = false;
		long handle = mHandle;
		mHandle = 0;
		if (handle != 0 && mCentral != null) {
			try {
				mCentral.disconnect(handle);
			} catch (NativeBleException e) {
				mUtil.consolePrintLn("BLE disconnect from " + mDeviceName + ": " + e.getMessage());
			}
		}
		clearBuffer();
		return true;
	}

	@Override
	public int getInputBufferBytesCount() throws SerialPortException {
		throwIfLinkLost("getInputBufferBytesCount()");
		return bufferedCount();
	}

	/** Waits up to timeout ms for byteCount bytes, as jssc does; on a timeout, none are taken. */
	@Override
	public byte[] readBytes(int byteCount, int timeout) throws SerialPortTimeoutException, SerialPortException {
		long deadline = System.currentTimeMillis() + timeout;
		synchronized (mLock) {
			while (mCount < byteCount) {
				throwIfLinkLost("readBytes()");
				long wait = deadline - System.currentTimeMillis();
				if (wait <= 0) {
					throw new SerialPortTimeoutException(mDeviceName, "readBytes()", timeout);
				}
				try {
					mLock.wait(wait);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new SerialPortException(mDeviceName, "readBytes()", "interrupted");
				}
			}
			byte[] result = new byte[byteCount];
			for (int i = 0; i < byteCount; i++) {
				result[i] = mBuffer[(mStart + i) % mBuffer.length];
			}
			mStart = (mStart + byteCount) % mBuffer.length;
			mCount -= byteCount;
			return result;
		}
	}

	@Override
	public boolean writeBytes(byte[] buffer) throws SerialPortException {
		throwIfLinkLost("writeBytes()");
		long handle = mHandle;
		if (handle == 0 || mCentral == null) {
			throw new SerialPortException(mDeviceName, "writeBytes()", "port not opened");
		}
		try {
			mCentral.write(handle, buffer);
			return true;
		} catch (NativeBleException e) {
			throw new SerialPortException(mDeviceName, "writeBytes()", e.getMessage());
		}
	}

	/** Nothing to set over BLE. */
	@Override
	public boolean setParams(int i, int j, int k, int l) {
		return true;
	}

	/** Drops the received bytes not yet read. */
	@Override
	public boolean purgePort(int i) {
		clearBuffer();
		return true;
	}

	@Override
	public void setByteCommunicationListener(ByteCommunicationListener byteCommListener) {
	}

	@Override
	public void removeRadioListenerList() {
	}

	private void throwIfLinkLost(String methodName) throws SerialPortException {
		String reason = mLinkLostReason;
		if (reason != null) {
			throw new SerialPortException(mDeviceName, methodName, "BLE link lost: " + reason);
		}
	}

	private int bufferedCount() {
		synchronized (mLock) {
			return mCount;
		}
	}

	private void clearBuffer() {
		synchronized (mLock) {
			mStart = 0;
			mCount = 0;
		}
	}

	private void append(byte[] data) {
		synchronized (mLock) {
			if (mCount + data.length > mBuffer.length) {
				int newLength = mBuffer.length;
				while (newLength < mCount + data.length) {
					newLength *= 2;
				}
				byte[] bigger = new byte[newLength];
				for (int i = 0; i < mCount; i++) {
					bigger[i] = mBuffer[(mStart + i) % mBuffer.length];
				}
				mBuffer = bigger;
				mStart = 0;
			}
			int end = (mStart + mCount) % mBuffer.length;
			for (byte b : data) {
				mBuffer[end] = b;
				end = (end + 1) % mBuffer.length;
			}
			mCount += data.length;
			mLock.notifyAll();
		}
	}

	private static void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private class LinkListener implements BleConnectionListener {
		@Override
		public void onBytes(byte[] data) {
			append(data);
		}

		@Override
		public void onDisconnected(String reason) {
			mUtil.consolePrintLn("BLE link to " + mDeviceName + " lost: " + reason);
			mHandle = 0;
			if (mIsOpened) {
				mLinkLostReason = reason==null || reason.isEmpty() ? "disconnected" : reason;
			}
			synchronized (mLock) {
				mLock.notifyAll();
			}
		}
	}

}
