package com.shimmerresearch.simpleexamples.bletest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.shimmerresearch.driver.ble.nativeble.BleRadio;
import com.shimmerresearch.driver.ble.nativeble.BleUartProfile;
import com.shimmerresearch.driver.ble.nativeble.NativeBleEvent;
import com.shimmerresearch.driver.ble.nativeble.NativeBleException;
import com.shimmerresearch.driverUtilities.UtilShimmer;

/**
 * Wraps a {@link BleRadio} and logs every connect, write, notification and disconnect with a
 * timestamp - the record to send back when a run on another machine misbehaves.
 */
public class LoggingBleRadio implements BleRadio {

	private final BleRadio mInner;
	private final PrintWriter mOut;
	private final SimpleDateFormat mTime = new SimpleDateFormat("HH:mm:ss.SSS");

	public LoggingBleRadio(BleRadio inner, File logFile) throws IOException {
		mInner = inner;
		mOut = new PrintWriter(new BufferedWriter(new FileWriter(logFile)), true);
	}

	private synchronized void log(String line) {
		mOut.println(mTime.format(new Date()) + "  " + line);
	}

	@Override
	public void startScan() throws NativeBleException {
		log("scan start");
		mInner.startScan();
	}

	@Override
	public void stopScan() throws NativeBleException {
		log("scan stop");
		mInner.stopScan();
	}

	@Override
	public int retrieveConnected(String[] serviceUuids) throws NativeBleException {
		int found = mInner.retrieveConnected(serviceUuids);
		log("already connected: " + found);
		return found;
	}

	@Override
	public long connect(String deviceId, BleUartProfile profile, int timeoutMs) throws NativeBleException {
		log("connect " + deviceId + " as " + profile.label);
		try {
			long handle = mInner.connect(deviceId, profile, timeoutMs);
			log("connected handle=" + handle + " mtu=" + mInner.mtu(handle));
			return handle;
		} catch (NativeBleException e) {
			log("connect failed: " + e.getMessage());
			throw e;
		}
	}

	@Override
	public void write(long handle, byte[] data) throws NativeBleException {
		log("TX h" + handle + " " + UtilShimmer.bytesToHexStringWithSpacesFormatted(data));
		try {
			mInner.write(handle, data);
		} catch (NativeBleException e) {
			log("TX failed: " + e.getMessage());
			throw e;
		}
	}

	@Override
	public int mtu(long handle) throws NativeBleException {
		return mInner.mtu(handle);
	}

	@Override
	public void disconnect(long handle) throws NativeBleException {
		log("disconnect h" + handle);
		mInner.disconnect(handle);
	}

	@Override
	public NativeBleEvent nextEvent(int timeoutMs) throws NativeBleException {
		NativeBleEvent event = mInner.nextEvent(timeoutMs);
		if (event != null && event.type == NativeBleEvent.TYPE_BYTES) {
			log("RX h" + event.handle + " " + UtilShimmer.bytesToHexStringWithSpacesFormatted(event.data));
		} else if (event != null && event.type == NativeBleEvent.TYPE_DISCONNECTED) {
			log("link lost h" + event.handle + ": " + event.message);
		}
		return event;
	}
}
