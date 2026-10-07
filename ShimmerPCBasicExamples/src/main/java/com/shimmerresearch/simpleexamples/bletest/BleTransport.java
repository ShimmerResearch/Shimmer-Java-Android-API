package com.shimmerresearch.simpleexamples.bletest;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.shimmerresearch.driver.ble.nativeble.BleCentral;
import com.shimmerresearch.driver.ble.nativeble.BleScanListener;
import com.shimmerresearch.driver.ble.nativeble.BleScanResult;
import com.shimmerresearch.driver.ble.nativeble.NativeBleEvent;
import com.shimmerresearch.driver.ble.nativeble.NativeBleLoader;
import com.shimmerresearch.driver.ble.nativeble.NativeBleRadio;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.grpc.GrpcBLERadioByteTools;
import com.shimmerresearch.pcDriver.ShimmerBLENative;
import com.shimmerresearch.pcDriver.ShimmerGRPC;

/**
 * How the tests reach a device: the in-process native library, or the gRPC BLE server as the
 * baseline it replaces. The same tests run unchanged over either.
 */
public abstract class BleTransport {

	public static final int FIND_TIMEOUT_MS = 20000;

	public abstract String name();

	/**
	 * Finds a device whose advertised name contains {@code nameFilter}, ready to connect.
	 * {@code knownId} is the native device ID or, for gRPC, the MAC; native uses it when the
	 * device is not advertising because it is already connected.
	 */
	public abstract DeviceUnderTest open(String nameFilter, String knownId) throws Exception;

	/** Details for the report header, e.g. library version and adapter state. */
	public abstract Map<String, String> describe();

	public void close() {
	}

	public static BleTransport create(TestOptions options, File byteLog) throws Exception {
		if (options.transport.equals("grpc")) {
			return new Grpc(options.grpcServer);
		}
		return new Native(options.logBytes ? byteLog : null);
	}

	/** {@link ShimmerBLENative} over the native library. */
	public static class Native extends BleTransport {
		private final BleCentral mCentral;
		private final NativeBleRadio mRadio;

		Native(File byteLog) throws Exception {
			mRadio = new NativeBleRadio();
			mCentral = byteLog == null ? BleCentral.getDefault() : BleCentral.initDefault(new LoggingBleRadio(mRadio, byteLog));
		}

		@Override
		public String name() {
			return "native";
		}

		@Override
		public Map<String, String> describe() {
			Map<String, String> d = new LinkedHashMap<String, String>();
			d.put("native library", NativeBleLoader.EXPECTED_NATIVE_VERSION);
			try {
				d.put("adapter state", mRadio.getAdapterState());
			} catch (Exception e) {
				d.put("adapter state", "unknown (" + e.getMessage() + ")");
			}
			return d;
		}

		@Override
		public DeviceUnderTest open(String nameFilter, String knownId) throws Exception {
			try {
				return new NativeDevice(mCentral, scanFor(mCentral, nameFilter, FIND_TIMEOUT_MS));
			} catch (Exception notAdvertising) {
				if (knownId == null) {
					throw notAdvertising;
				}
				// A connected device does not advertise; connect falls back to looking it up by ID.
				System.out.println(nameFilter + " is not advertising; trying " + knownId + " as an already-connected device");
				String address = knownId.contains(":") ? knownId : "";
				return new NativeDevice(mCentral, new BleScanResult(knownId, nameFilter, address, NativeBleEvent.RSSI_UNKNOWN));
			}
		}
	}

	/** Scans until a Shimmer3-family device whose name contains {@code nameFilter} advertises. */
	public static BleScanResult scanFor(BleCentral central, final String nameFilter, int timeoutMs) throws Exception {
		final BleScanResult[] found = new BleScanResult[1];
		final CountDownLatch latch = new CountDownLatch(1);
		BleScanListener listener = new BleScanListener() {
			public void onDeviceFound(BleScanResult device) {
				if (device.getName().contains(nameFilter) && device.getProfile() != null
						&& device.getProfile().isShimmer3Family()) {
					found[0] = device;
					latch.countDown();
				}
			}
		};
		central.addScanListener(listener);
		try {
			central.startScan();
			if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
				throw new Exception("no Shimmer3/Shimmer3R named *" + nameFilter + "* advertised within " + timeoutMs + " ms");
			}
			return found[0];
		} finally {
			central.removeScanListener(listener);
			central.stopScan();
		}
	}

	static class NativeDevice extends DeviceUnderTest {
		private final BleCentral mCentral;
		private final String mName;

		NativeDevice(BleCentral central, BleScanResult device) {
			super(new ShimmerBLENative(device));
			mCentral = central;
			mName = device.getName();
		}

		@Override
		public String label() {
			return "native " + mName;
		}

		@Override
		public String deviceId() {
			return ((ShimmerBLENative) mShimmer).getDeviceId();
		}

		/**
		 * A device that was switched off must advertise again before it can be reconnected. One
		 * that never shows up may be connected already (and so silent); connect then looks it up
		 * by ID.
		 */
		@Override
		protected void prepare(int timeoutMs) throws Exception {
			try {
				scanFor(mCentral, mName, Math.max(timeoutMs, FIND_TIMEOUT_MS));
			} catch (Exception notAdvertising) {
				System.out.println(mName + " is not advertising; connecting by ID " + deviceId());
			}
		}

		@Override
		protected void startConnect() {
			((ShimmerBLENative) mShimmer).connect("", "");
		}
	}

	/** {@link ShimmerGRPC} through the ShimmerBLEGrpc server, started for the run. */
	public static class Grpc extends BleTransport {
		private final GrpcBLERadioByteTools mTools;
		private final int mPort;

		Grpc(String serverPath) throws Exception {
			if (serverPath == null) {
				throw new IllegalArgumentException("--grpc-server <path to ShimmerBLEGrpc.exe> is required for --transport grpc");
			}
			String exeName = new File(serverPath).getName();
			mTools = new GrpcBLERadioByteTools(exeName, serverPath);
			mPort = mTools.startServer();
			// startServer() does not wait for the server to listen.
			Thread.sleep(3000);
		}

		@Override
		public String name() {
			return "grpc";
		}

		@Override
		public Map<String, String> describe() {
			Map<String, String> d = new LinkedHashMap<String, String>();
			d.put("gRPC port", Integer.toString(mPort));
			return d;
		}

		@Override
		public DeviceUnderTest open(String nameFilter, String mac) throws Exception {
			if (UtilShimmer.isOsMac()) {
				return new GrpcDevice(new ShimmerGRPC(mac == null ? "" : mac, nameFilter, "localhost", mPort), nameFilter);
			}
			if (mac == null) {
				throw new IllegalArgumentException("--mac is required for --transport grpc on Windows");
			}
			return new GrpcDevice(new ShimmerGRPC(mac.replace(":", "").toUpperCase(), "localhost", mPort), mac);
		}

		@Override
		public void close() {
			mTools.stopServer();
		}
	}

	static class GrpcDevice extends DeviceUnderTest {
		private final String mWhat;

		GrpcDevice(ShimmerGRPC shimmer, String what) {
			super(shimmer);
			mWhat = what;
		}

		@Override
		public String label() {
			return "grpc " + mWhat;
		}

		@Override
		public String deviceId() {
			return mWhat;
		}

		@Override
		protected void startConnect() {
			((ShimmerGRPC) mShimmer).connect("", "");
		}
	}
}
