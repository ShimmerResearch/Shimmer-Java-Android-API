package com.shimmerresearch.driver.ble.nativeble;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** In-memory {@link BleRadio}: tests push events, and can see what was written or closed. */
class FakeBleRadio implements BleRadio {

	final BlockingQueue<NativeBleEvent> events = new LinkedBlockingQueue<NativeBleEvent>();
	final List<Long> disconnected = Collections.synchronizedList(new ArrayList<Long>());
	final List<byte[]> written = Collections.synchronizedList(new ArrayList<byte[]>());
	/** Events queued during connect(), before it returns: the native side may deliver data that early. */
	final List<NativeBleEvent> duringConnect = new ArrayList<NativeBleEvent>();
	private long mNextHandle = 1;

	void push(NativeBleEvent event) {
		events.add(event);
	}

	@Override
	public void startScan() {
	}

	@Override
	public void stopScan() {
	}

	@Override
	public int retrieveConnected(String[] serviceUuids) {
		return 0;
	}

	@Override
	public synchronized long connect(String deviceId, BleUartProfile profile, int timeoutMs) {
		long handle = mNextHandle++;
		for (NativeBleEvent e : duringConnect) {
			events.add(new NativeBleEvent(e.type, handle, e.id, e.name, e.address, e.rssi, e.data, e.message));
		}
		// Give the dispatcher time to see those events before the listener is registered.
		try {
			Thread.sleep(300);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return handle;
	}

	@Override
	public void write(long handle, byte[] data) {
		written.add(data);
	}

	@Override
	public int mtu(long handle) {
		return 247;
	}

	@Override
	public void disconnect(long handle) {
		disconnected.add(handle);
	}

	@Override
	public NativeBleEvent nextEvent(int timeoutMs) {
		try {
			return events.poll(timeoutMs, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}
}
