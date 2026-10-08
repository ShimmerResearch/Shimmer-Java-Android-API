package com.shimmerresearch.simpleexamples;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ble.nativeble.BleScanResult;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;

/**
 * One device in {@link ShimmerBLECaptureExample}: the driver connected to it (either kind), its
 * {@link ReceptionStats}, its CSV log, and the latest of everything it reported, for the device
 * table. Receives its driver's events on the driver's threads and tells the app through an
 * {@link Observer}.
 */
final class ConnectedShimmer implements CaptureDriver.Listener {

	interface Observer {
		/** A line for the app's log. Any thread. */
		void onLog(ConnectedShimmer shimmer, String message);

		/** On the driver's thread. */
		void onSample(ConnectedShimmer shimmer, ObjectCluster sample);

		/** Its state changed: refresh what depends on it. Any thread. */
		void onChanged(ConnectedShimmer shimmer);
	}

	private static final String CAL = CHANNEL_TYPE.CAL.toString();
	private static final String TIMESTAMP = Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP;
	private static final String SYSTEM_TIMESTAMP = Configuration.Shimmer3.ObjectClusterSensorName.SYSTEM_TIMESTAMP;

	private final BleScanResult mDevice;
	private final CaptureDriver mDriver;
	private final Observer mObserver;
	private final ReceptionStats mStats = new ReceptionStats();
	private final CsvLog mCsvLog = new CsvLog();
	private final CountDownLatch mSettled = new CountDownLatch(1);
	/** Counts streaming runs, so a timed stop stops only the run it was set for. */
	private final AtomicInteger mStreamRun = new AtomicInteger();

	private volatile String mState = "QUEUED";
	private volatile boolean mConnecting = false;
	private volatile boolean mReady = false;
	private volatile boolean mClosed = false;
	private volatile int mLinkLosses;
	private volatile int mErrors;
	private volatile String mLastError;
	private volatile double mStreamSamplingRate = Double.NaN;
	private volatile String mDataRate;
	private volatile String mDataRateText;

	// On the Swing thread only.
	private ReceptionStats.Snapshot mSnapshot = new ReceptionStats().snapshot(false);
	private boolean mWasStreaming = false;
	private boolean mStreamEnded = false;

	ConnectedShimmer(BleScanResult device, CaptureDriver driver, Observer observer) {
		mDevice = device;
		mDriver = driver;
		mObserver = observer;
	}

	// --- Actions; each may block, so not on the Swing thread ----------------------------------

	/** Connects and waits until the device is ready, the connection failed, or the time is up. */
	void connectAndWait(long timeoutMs) {
		if (mClosed) {
			return;
		}
		mConnecting = true;
		int errorsBefore = mErrors;
		setState("CONNECTING");
		log("connecting with " + mDriver.label());
		mDriver.connect(mDevice, this);
		boolean settled = false;
		try {
			settled = mSettled.await(timeoutMs, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		mConnecting = false;
		// A driver that gave up without saying why (ShimmerBluetooth only prints it) still failed.
		if (!mReady && !mClosed && mErrors == errorsBefore) {
			error(settled ? "did not connect: " + mState + " (the driver's console output may say why)"
					: "not ready after " + timeoutMs / 1000 + " s");
		}
		mObserver.onChanged(this);
	}

	/** Starts streaming, logging to a new CSV file in {@code csvFolder} unless it is null. Returns the run. */
	int startStreaming(File csvFolder) {
		mStreamSamplingRate = mDriver.getSamplingRate();
		mStats.reset(mStreamSamplingRate);
		if (csvFolder != null) {
			String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
			File file = new File(csvFolder, "shimmer_ble_" + safe(name()) + "_" + mDriver.label() + "_" + stamp + ".csv");
			try {
				mCsvLog.open(file);
				log("logging to " + file.getAbsolutePath());
			} catch (IOException e) {
				error("could not open " + file + ": " + e.getMessage());
			}
		}
		int run = mStreamRun.incrementAndGet();
		log(String.format("start streaming at %s Hz", hz(mStreamSamplingRate)));
		try {
			mDriver.startStreaming();
		} catch (Exception e) {
			error("start streaming failed: " + e.getMessage());
		}
		return run;
	}

	void stopStreaming() {
		log("stop streaming");
		mDriver.stopStreaming();
	}

	/** Stops streaming if run {@code run} is still the one streaming. */
	void stopStreamingRun(int run) {
		if (run == mStreamRun.get() && mDriver.isStreaming()) {
			stopStreaming();
		}
	}

	void startDataRateTest(int seconds) {
		mDataRate = "starting";
		mDataRateText = null;
		log("data-rate test for " + seconds + " s");
		mDriver.startDataRateTest(seconds * 1000);
		mObserver.onChanged(this);
	}

	void disconnect() {
		mClosed = true;
		mSettled.countDown();
		log("disconnecting");
		mDriver.disconnect();
		closeCsv();
		mObserver.onChanged(this);
	}

	// --- On the Swing thread ------------------------------------------------------------------

	/**
	 * Once a second: takes the figures for the table and charts, and reports a stream that ended,
	 * a second after it did, so that samples still arriving after the stop are counted (and logged).
	 */
	ReceptionStats.Snapshot tick() {
		mSnapshot = mStats.snapshot(true);
		if (mStreamEnded) {
			mStreamEnded = false;
			log("streamed " + receptionSummary());
			closeCsv();
		}
		boolean streaming = mDriver.isStreaming();
		mStreamEnded = mWasStreaming && !streaming;
		mWasStreaming = streaming;
		return mSnapshot;
	}

	ReceptionStats.Snapshot getSnapshot() {
		return mSnapshot;
	}

	/** The last stream's figures, as one line. */
	String receptionSummary() {
		ReceptionStats.Snapshot s = mSnapshot;
		double rate = s.spanSeconds > 0 ? (s.samples - 1) / s.spanSeconds : Double.NaN;
		return String.format("%d samples in %.1f s (%s/s at %s Hz), %d missing, reception %s, driver PRR %s, delay max %s ms",
				s.samples, s.spanSeconds, number(rate, 1), hz(mStreamSamplingRate), s.missing,
				percent(s.receptionPercent), percent(mDriver.getPacketReceptionRate()), number(s.maxDelayMs, 0));
	}

	// --- CaptureDriver.Listener, on the driver's threads ---------------------------------------

	@Override
	public void onState(String state) {
		// The ShimmerBluetooth driver reports some states again as its operations progress.
		if (!state.equals(mState)) {
			log("state " + state);
		}
		setState(state);
		if (isLinkLost(state)) {
			mLinkLosses++;
		}
		if (hasEnded(state)) {
			mReady = false;
			mSettled.countDown();
			closeCsv();
		}
	}

	/** Lost, as either driver spells it: CONNECTION_LOST (...) or "Lost connection". */
	static boolean isLinkLost(String state) {
		return state.toLowerCase().contains("lost");
	}

	/** Disconnected, lost or failed, as either driver spells it. */
	static boolean hasEnded(String state) {
		String s = state.toLowerCase();
		return isLinkLost(state) || s.startsWith("disconnected") || s.contains("failed");
	}

	@Override
	public void onReady() {
		mReady = true;
		log("ready: " + mDriver.getDeviceSummary() + ", MTU " + mDriver.getMtu());
		mSettled.countDown();
		mObserver.onChanged(this);
	}

	@Override
	public void onSample(ObjectCluster sample) {
		double arrival = sample.getFormatClusterValue(SYSTEM_TIMESTAMP, CAL);
		mStats.onSample(sample.getFormatClusterValue(TIMESTAMP, CAL),
				Double.isNaN(arrival) ? System.currentTimeMillis() : arrival);
		mCsvLog.write(sample);
		mObserver.onSample(this, sample);
	}

	@Override
	public void onError(String message) {
		error(message);
		mSettled.countDown();
	}

	@Override
	public void onInfo(String message) {
		log(message);
	}

	@Override
	public void onDataRate(boolean finished, double kibPerSecond, long missingPackets, String text) {
		String figures = String.format("%.1f KiB/s", kibPerSecond) + (missingPackets > 0 ? ", " + missingPackets + " missing" : "");
		mDataRate = finished ? figures : figures + " ...";
		mDataRateText = text;
		if (finished) {
			log("data-rate test: " + text);
			mObserver.onChanged(this);
		}
	}

	// --- For the table and the buttons ----------------------------------------------------------

	String name() {
		return mDevice.getName();
	}

	BleScanResult getDevice() {
		return mDevice;
	}

	CaptureDriver getDriver() {
		return mDriver;
	}

	String getState() {
		return mState;
	}

	/** Connecting, or connected and not disconnected since. */
	boolean isActive() {
		return !mClosed && (mConnecting || mDriver.isConnected());
	}

	boolean isReady() {
		return mReady && !mClosed && mDriver.isConnected();
	}

	/** Ready for a command: see {@link CaptureDriver#isIdle()}. */
	boolean isIdle() {
		return mReady && !mClosed && mDriver.isIdle();
	}

	boolean isStreaming() {
		return !mClosed && mDriver.isStreaming();
	}

	int getLinkLosses() {
		return mLinkLosses;
	}

	int getErrors() {
		return mErrors;
	}

	String getLastError() {
		return mLastError;
	}

	String getDataRate() {
		return mDataRate;
	}

	String getDataRateText() {
		return mDataRateText;
	}

	// --- Helpers ----------------------------------------------------------------------------------

	private void setState(String state) {
		mState = state;
		mObserver.onChanged(this);
	}

	private void log(String message) {
		mObserver.onLog(this, message);
	}

	private void error(String message) {
		mErrors++;
		mLastError = message;
		log("ERROR " + message);
		mObserver.onChanged(this);
	}

	private void closeCsv() {
		File file = mCsvLog.close();
		if (file != null) {
			log("logged to " + file.getAbsolutePath());
		}
	}

	static String hz(double rate) {
		return number(rate, 1);
	}

	static String percent(double value) {
		return Double.isNaN(value) ? "-" : String.format("%.1f%%", value);
	}

	static String number(double value, int decimals) {
		return Double.isNaN(value) ? "-" : String.format("%." + decimals + "f", value);
	}

	private static String safe(String name) {
		return name.replaceAll("[^A-Za-z0-9._-]", "_");
	}
}
