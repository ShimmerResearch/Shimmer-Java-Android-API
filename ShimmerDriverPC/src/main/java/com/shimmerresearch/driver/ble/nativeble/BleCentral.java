package com.shimmerresearch.driver.ble.nativeble;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Process-wide entry point for native BLE: scanning, connections, and routing of events.
 * <p>
 * One dispatcher thread pulls events from the {@link BleRadio} and delivers them, so each
 * connection's listener sees its bytes in order and never concurrently. Bytes that arrive before
 * {@link #connect} has registered the listener (the native side may deliver them first) are held
 * and replayed in order once it is registered.
 */
public class BleCentral {

	private static final int POLL_MS = 250;
	/** Pending events kept per handle while its listener is not yet registered. */
	private static final int MAX_PENDING_PER_HANDLE = 1000;

	private static BleCentral sDefault;

	private final BleRadio mRadio;
	private final Object mLock = new Object();
	private final Map<Long, BleConnectionListener> mListeners = new HashMap<Long, BleConnectionListener>();
	private final Map<Long, List<NativeBleEvent>> mPending = new HashMap<Long, List<NativeBleEvent>>();
	private final Map<String, NativeBleDevice> mDiscovered = new LinkedHashMap<String, NativeBleDevice>();
	private final List<BleScanListener> mScanListeners = new CopyOnWriteArrayList<BleScanListener>();
	private final Thread mDispatcher;
	private volatile boolean mRunning = true;

	/** The shared instance over the native library, created (and the library loaded) on first use. */
	public static synchronized BleCentral getDefault() throws NativeBleException {
		if (sDefault == null) {
			sDefault = new BleCentral(new NativeBleRadio());
		}
		return sDefault;
	}

	/**
	 * Creates the shared instance over {@code radio} - for instance one that logs traffic - instead
	 * of the plain native radio. Must be called before anything uses {@link #getDefault()}.
	 */
	public static synchronized BleCentral initDefault(BleRadio radio) {
		if (sDefault != null) {
			throw new IllegalStateException("The shared BleCentral already exists");
		}
		sDefault = new BleCentral(radio);
		return sDefault;
	}

	/** Uses the given radio. Production code should use {@link #getDefault()}. */
	public BleCentral(BleRadio radio) {
		mRadio = radio;
		mDispatcher = new Thread(new Runnable() {
			@Override
			public void run() {
				dispatchLoop();
			}
		}, "BleCentral-dispatcher");
		mDispatcher.setDaemon(true);
		mDispatcher.start();
	}

	public BleRadio getRadio() {
		return mRadio;
	}

	public void addScanListener(BleScanListener listener) {
		mScanListeners.add(listener);
	}

	public void removeScanListener(BleScanListener listener) {
		mScanListeners.remove(listener);
	}

	/**
	 * Starts scanning, and also reports Shimmer devices that are already connected to this
	 * machine, since those do not advertise. Windows, for one, keeps a link open after the
	 * process that owned it is killed, so without this a restarted app could not find the device.
	 */
	public void startScan() throws NativeBleException {
		mRadio.startScan();
		try {
			mRadio.retrieveConnected(allProfileServices());
		} catch (NativeBleException e) {
			// The scan itself is running; only already-connected devices are missed.
			System.err.println("BleCentral: could not list connected devices: " + e.getMessage());
		}
	}

	private static String[] allProfileServices() {
		BleUartProfile[] profiles = BleUartProfile.values();
		String[] services = new String[profiles.length];
		for (int i = 0; i < profiles.length; i++) {
			services[i] = profiles[i].serviceUuid;
		}
		return services;
	}

	public void stopScan() throws NativeBleException {
		mRadio.stopScan();
	}

	/** Every device seen since this instance was created, in the order first seen, with its latest details. */
	public Collection<NativeBleDevice> getDiscoveredDevices() {
		synchronized (mLock) {
			return new ArrayList<NativeBleDevice>(mDiscovered.values());
		}
	}

	/** The most recent scan result for {@code deviceId}, or null if it has not been seen. */
	public NativeBleDevice getDiscoveredDevice(String deviceId) {
		synchronized (mLock) {
			return mDiscovered.get(deviceId);
		}
	}

	/**
	 * Connects to a device seen by a scan and routes its data to {@code listener}. Blocks for up to
	 * {@code timeoutMs}, so call it off the UI thread.
	 *
	 * @return the handle for {@link #write}, {@link #mtu} and {@link #disconnect}
	 */
	public long connect(String deviceId, BleUartProfile profile, int timeoutMs, BleConnectionListener listener)
			throws NativeBleException {
		long handle = mRadio.connect(deviceId, profile, timeoutMs);
		synchronized (mLock) {
			mListeners.put(handle, listener);
		}
		return handle;
	}

	public void write(long handle, byte[] data) throws NativeBleException {
		mRadio.write(handle, data);
	}

	public int mtu(long handle) throws NativeBleException {
		return mRadio.mtu(handle);
	}

	/** Closes the link. The listener is dropped first, so it hears nothing more. */
	public void disconnect(long handle) throws NativeBleException {
		synchronized (mLock) {
			mListeners.remove(handle);
			mPending.remove(handle);
		}
		mRadio.disconnect(handle);
	}

	/** Stops the dispatcher. For tests; the shared instance lives as long as the JVM. */
	public void shutdown() {
		mRunning = false;
		mDispatcher.interrupt();
		try {
			mDispatcher.join(2000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void dispatchLoop() {
		while (mRunning) {
			NativeBleEvent event;
			try {
				event = mRadio.nextEvent(POLL_MS);
			} catch (NativeBleException e) {
				System.err.println("BleCentral: " + e.getMessage());
				sleepQuietly(POLL_MS);
				continue;
			} catch (RuntimeException e) {
				e.printStackTrace();
				sleepQuietly(POLL_MS);
				continue;
			}
			// Replay held events first, so a handle's events always arrive in order.
			flushPending();
			if (event != null) {
				dispatch(event);
			}
		}
	}

	void dispatch(NativeBleEvent event) {
		switch (event.type) {
		case NativeBleEvent.TYPE_DEVICE_FOUND:
			NativeBleDevice device = new NativeBleDevice(event.id, event.name, event.address, event.rssi);
			synchronized (mLock) {
				// Advertisements without a name must not wipe a name seen earlier.
				NativeBleDevice previous = mDiscovered.get(event.id);
				if (device.getName().isEmpty() && previous != null && !previous.getName().isEmpty()) {
					device = new NativeBleDevice(event.id, previous.getName(), event.address, event.rssi);
				}
				mDiscovered.put(event.id, device);
			}
			for (BleScanListener listener : mScanListeners) {
				try {
					listener.onDeviceFound(device);
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
			break;
		case NativeBleEvent.TYPE_BYTES:
		case NativeBleEvent.TYPE_DISCONNECTED:
			BleConnectionListener listener;
			boolean backlog;
			synchronized (mLock) {
				listener = mListeners.get(event.handle);
				// Queue behind any held events, even if the listener has just been registered.
				backlog = listener == null || mPending.containsKey(event.handle);
				if (backlog) {
					hold(event);
				} else if (event.type == NativeBleEvent.TYPE_DISCONNECTED) {
					mListeners.remove(event.handle);
				}
			}
			if (backlog) {
				if (listener != null) {
					flushPending();
				}
				return;
			}
			deliver(listener, event);
			break;
		default:
			System.err.println("BleCentral: ignoring unknown event type " + event.type);
		}
	}

	/** Called with mLock held. */
	private void hold(NativeBleEvent event) {
		List<NativeBleEvent> held = mPending.get(event.handle);
		if (held == null) {
			held = new ArrayList<NativeBleEvent>();
			mPending.put(event.handle, held);
		}
		if (held.size() < MAX_PENDING_PER_HANDLE) {
			held.add(event);
		}
	}

	void flushPending() {
		List<NativeBleEvent> ready = new ArrayList<NativeBleEvent>();
		Map<Long, BleConnectionListener> targets = new HashMap<Long, BleConnectionListener>();
		synchronized (mLock) {
			if (mPending.isEmpty()) {
				return;
			}
			for (Long handle : new ArrayList<Long>(mPending.keySet())) {
				BleConnectionListener listener = mListeners.get(handle);
				if (listener != null) {
					ready.addAll(mPending.remove(handle));
					targets.put(handle, listener);
				}
			}
		}
		for (NativeBleEvent event : ready) {
			BleConnectionListener listener = targets.get(event.handle);
			if (event.type == NativeBleEvent.TYPE_DISCONNECTED) {
				synchronized (mLock) {
					mListeners.remove(event.handle);
				}
			}
			deliver(listener, event);
		}
	}

	private static void deliver(BleConnectionListener listener, NativeBleEvent event) {
		try {
			if (event.type == NativeBleEvent.TYPE_BYTES) {
				listener.onBytes(event.data);
			} else {
				listener.onDisconnected(event.message);
			}
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
	}

	private static void sleepQuietly(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
