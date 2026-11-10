package com.shimmerresearch.hil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driverUtilities.AssembleShimmerConfig;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails;
import com.shimmerresearch.shimmer3.communication.ByteCommunicationTestUtils;

/**
 * One unit on one serial port, driven through ShimmerPC as an app drives it. Every API call runs
 * under a watchdog: if one does not return, the session records which, dumps every thread (with
 * the locks each holds and waits on) and refuses further calls, so a deadlock ends the scenario
 * with evidence instead of hanging the run.
 */
class HilSession extends BasicProcessWithCallBack implements AutoCloseable {

	interface Call {
		void run() throws Exception;
	}

	interface Condition {
		boolean holds();
	}

	final String port;
	final String mac;
	final String type;
	final HilShimmerPC device;
	private final long t0 = System.nanoTime();
	private final List<String> events = new ArrayList<>();
	private final List<StreamAnalysis.Sample> samples = new ArrayList<>();
	/** Each recorded packet's raw device ticks (TIMESTAMP, UNCAL), since startRecording, across stretches. */
	private final List<Double> rawTicks = new ArrayList<>();
	private final Set<Integer> otherMessagesSeen = new HashSet<>();
	private final ExecutorService calls = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "HilCall");
		t.setDaemon(true);
		return t;
	});
	private volatile boolean recording;
	private volatile BT_STATE lastState;
	/** State changes reported so far, so a connect can tell its own CONNECTION_FAILED from the last one's. */
	private volatile int stateChanges;
	/** The call that never returned, if one did not. */
	volatile String hung;
	/** Connects made in this session, for the report: each is a chance for the connect-time race. */
	int connects;
	/** Why the last connect failed, as a sentence for the report; null after one that succeeded. */
	volatile String connectFailure;
	/** jssc's message when Windows refuses to open a COM port that another program already has open. */
	private static final Pattern PORT_BUSY = Pattern.compile("Port busy");
	/**
	 * Per connect: whether the driver has sent NOTIFICATION_SHIMMER_FULLY_INITIALIZED (the end of
	 * its connect sequence), and whether the first CONNECTED state is still to come.
	 */
	private volatile boolean fullyInitialisedSinceConnect;
	private volatile boolean awaitingFirstConnected;
	/**
	 * Connects whose first CONNECTED came before the end of the connect sequence: the app was told
	 * the unit was ready while the sequence was still queued (DEV-1275).
	 */
	volatile int connectedBeforeReady;

	HilSession(String port, String mac, String type) {
		this.port = port;
		this.mac = mac;
		this.type = type;
		this.device = new HilShimmerPC(port, t0);
		setWaitForData(device);
	}

	double nowMs() {
		return (System.nanoTime() - t0) / 1e6;
	}

	void event(String text) {
		String line = String.format(Locale.ROOT, "%9.0f %s", nowMs(), text);
		synchronized (events) {
			events.add(line);
		}
		System.out.println(port + " " + line);
	}

	List<String> events() {
		synchronized (events) {
			return new ArrayList<>(events);
		}
	}

	@Override
	protected void processMsgFromCallback(ShimmerMsg msg) {
		if (msg.mIdentifier == HilShimmerPC.MSG_IDENTIFIER_STATE_CHANGE && msg.mB instanceof CallbackObject) {
			BT_STATE state = ((CallbackObject) msg.mB).mState;
			if (state != null) {
				lastState = state;
				stateChanges++;
				event("state " + state);
				if (state == BT_STATE.CONNECTED && awaitingFirstConnected) {
					awaitingFirstConnected = false;
					if (!fullyInitialisedSinceConnect) {
						connectedBeforeReady++;
						event("CONNECTED reported before the connect sequence ended (no FULLY_INITIALIZED yet; DEV-1275)");
					}
				}
			}
		} else if (msg.mIdentifier == HilShimmerPC.MSG_IDENTIFIER_NOTIFICATION_MESSAGE && msg.mB instanceof CallbackObject
				&& ((CallbackObject) msg.mB).mIndicator == HilShimmerPC.NOTIFICATION_SHIMMER_FULLY_INITIALIZED) {
			fullyInitialisedSinceConnect = true;
			event("fully initialised");
		} else if (msg.mIdentifier == HilShimmerPC.MSG_IDENTIFIER_DATA_PACKET && msg.mB instanceof ObjectCluster) {
			if (recording) {
				recordPacket((ObjectCluster) msg.mB);
			}
		} else if (otherMessagesSeen.add(msg.mIdentifier)) {
			event("message " + msg.mIdentifier + (msg.mB == null ? "" : " " + msg.mB.getClass().getSimpleName()));
		}
	}

	private void recordPacket(ObjectCluster oc) {
		StreamAnalysis.Sample s = new StreamAnalysis.Sample(nowMs(),
				cal(oc, Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP),
				cal(oc, Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_X),
				cal(oc, Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_Y),
				cal(oc, Configuration.Shimmer3.ObjectClusterSensorName.ACCEL_LN_Z));
		double ticks = value(oc, Configuration.Shimmer3.ObjectClusterSensorName.TIMESTAMP, "UNCAL");
		synchronized (samples) {
			samples.add(s);
			rawTicks.add(ticks);
		}
	}

	private static double cal(ObjectCluster oc, String channel) {
		return value(oc, channel, "CAL");
	}

	private static double value(ObjectCluster oc, String channel, String format) {
		Collection<FormatCluster> formats = oc.getCollectionOfFormatClusters(channel);
		FormatCluster c = (FormatCluster) ObjectCluster.returnFormatCluster(formats, format);
		return c == null ? Double.NaN : c.mData;
	}

	BT_STATE state() {
		BT_STATE s = device.getBluetoothRadioState();
		return s != null ? s : lastState;
	}

	boolean waitFor(Condition condition, long timeoutMs) throws InterruptedException {
		long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
		while (System.nanoTime() < deadline) {
			if (condition.holds()) {
				return true;
			}
			Thread.sleep(20);
		}
		return condition.holds();
	}

	/**
	 * Runs an API call under the watchdog. After a hung call nothing more is run: the driver is in
	 * an unknown state, and queuing calls behind the stuck one only adds stuck threads.
	 */
	boolean call(String what, long timeoutMs, Call call) {
		if (hung != null) {
			event("skipped " + what + ": " + hung + " never returned");
			return false;
		}
		event(what);
		Future<?> f = calls.submit(() -> {
			call.run();
			return null;
		});
		try {
			f.get(timeoutMs, TimeUnit.MILLISECONDS);
			return true;
		} catch (TimeoutException e) {
			hung = what;
			event(what + " did not return within " + timeoutMs + " ms; every thread:\n"
					+ ByteCommunicationTestUtils.dumpAllThreads());
			return false;
		} catch (java.util.concurrent.ExecutionException e) {
			event(what + " threw " + e.getCause());
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Connects and waits until the driver has read the unit's configuration. A connect is never
	 * retried. When the port can't be opened, connectFailure says why. jssc's "Port busy" means
	 * another program on this PC has the port open (another HIL, Consensys, a serial terminal), so
	 * the unit is in use. Results taken while another host shares the unit mean nothing, and that is
	 * how DEV-1276 was misread.
	 */
	boolean connect(long timeoutMs) throws InterruptedException {
		connects++;
		connectFailure = null;
		fullyInitialisedSinceConnect = false;
		awaitingFirstConnected = true;
		int mark = stateChanges;
		int logMark = device.logSize();
		Condition failed = () -> stateChanges > mark && device.getBluetoothRadioState() == BT_STATE.CONNECTION_FAILED;
		if (!call("connect on " + port, 10000, () -> device.connect(port, ""))) {
			connectFailure = "connect on " + port + " did not return (see the events)";
			return false;
		}
		boolean ready = waitFor(() -> (device.isInitialised() && device.getBluetoothRadioState() == BT_STATE.CONNECTED)
				|| failed.holds(), timeoutMs) && !failed.holds();
		if (ready) {
			event("connected: timers " + device.timers());
		} else {
			if (device.countSince(logMark, PORT_BUSY) > 0) {
				connectFailure = port + " is open in another program (another HIL, Consensys, a serial terminal?): "
						+ "jssc reports \"Port busy\". Nothing else may use the unit during a run.";
			} else if (failed.holds()) {
				connectFailure = "the Bluetooth link on " + port + " could not be opened (CONNECTION_FAILED): "
						+ "is the unit on, in range, and not connected to another host?";
			} else {
				connectFailure = "did not connect and initialise within " + timeoutMs / 1000 + " s (state " + state() + ")";
			}
			event(connectFailure);
		}
		return ready;
	}

	/**
	 * Accel only (the low-noise accel on Shimmer3, the LSM6DSV's on Shimmer3R) at rateHz, written the
	 * way Consensys's Bluetooth manager does it: a clone, AssembleShimmerConfig, then
	 * operationPrepare, configureFromClone and operationStart(CONFIGURING) on the connected device,
	 * except that the calibration is not written back.
	 */
	boolean configureAccelOnly(double rateHz, long timeoutMs) throws InterruptedException {
		ShimmerDevice clone = device.deepClone();
		clone.disableAllSensors();
		if (clone.getHardwareVersion() == ShimmerVerDetails.HW_ID.SHIMMER_3) {
			clone.setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_ANALOG_ACCEL, true);
		} else {
			clone.setSensorEnabledState(Configuration.Shimmer3.SENSOR_ID.SHIMMER_LSM6DSV_ACCEL_LN, true);
		}
		clone.setShimmerAndSensorsSamplingRate(rateHz);
		AssembleShimmerConfig.generateSingleShimmerConfig(clone, Configuration.COMMUNICATION_TYPE.BLUETOOTH);
		// Leave the unit's stored calibration alone. configureFromClone otherwise writes the clone's
		// calibration dump back (AA-246), which would rewrite the bench units' calibration on every run.
		((ShimmerBluetooth) clone).setWriteCalibrationDumpWhenConfiguringForClone(false);
		int logMark = device.logSize();
		boolean sent = call(String.format(Locale.ROOT, "configure accel only at %.1f Hz", rateHz), 10000, () -> {
			device.operationPrepare();
			device.configureFromClone(clone);
			device.operationStart(BT_STATE.CONFIGURING);
		});
		if (!sent) {
			return false;
		}
		// The operation ends by reading the configuration back, finishing with an INQUIRY. Wait for that
		// reply (logged after this configure began), an empty queue and the requested rate read back:
		// state and rate alone can already hold before the write starts, if the unit kept them.
		boolean done = waitFor(() -> device.countSince(logMark, "Inquiry Response Received") > 0
				&& device.queuedInstructions() == 0 && state() == BT_STATE.CONNECTED
				&& Math.abs(device.getSamplingRateShimmer() - rateHz) < 0.5, timeoutMs);
		event(String.format(Locale.ROOT, "configure %s: rate on device %.2f Hz, timers %s", done ? "finished" : "timed out",
				device.getSamplingRateShimmer(), device.timers()));
		return done;
	}

	void startRecording() {
		synchronized (samples) {
			samples.clear();
			rawTicks.clear();
		}
		recording = true;
	}

	/**
	 * The raw device ticks of every packet recorded since startRecording, in arrival order: the
	 * unit's own 3-byte counter, before the driver unwraps it (TimestampWraps counts its wraps).
	 */
	List<Double> rawTicksRecorded() {
		synchronized (samples) {
			return new ArrayList<>(rawTicks);
		}
	}

	List<StreamAnalysis.Sample> stopRecording() {
		recording = false;
		synchronized (samples) {
			return new ArrayList<>(samples);
		}
	}

	/**
	 * Ends one stretch of the recording and starts the next, for when the test stops and restarts
	 * streaming on purpose: each stretch is judged on its own, so the pause is not taken for loss.
	 */
	List<StreamAnalysis.Sample> nextSegment() {
		synchronized (samples) {
			List<StreamAnalysis.Sample> done = new ArrayList<>(samples);
			samples.clear();
			return done;
		}
	}

	/**
	 * Starts SD logging on a streaming unit the way Consensys does (PanelLiveData, DEV-436): stop
	 * streaming, start logging on the quiet link (SET_RWC, then START_LOGGING_ONLY), then stream
	 * again. The RTC is therefore never written mid-stream. The stretch recorded before the stop is
	 * returned, and recording carries on into a new one.
	 */
	List<StreamAnalysis.Sample> startSdLoggingLikeConsensys(String what, HilReport report) throws InterruptedException {
		report.check(stopStreaming(10000), what + ": streaming did not stop before starting SD logging");
		List<StreamAnalysis.Sample> before = nextSegment();
		boolean started = call("startSDLogging", 10000, device::startSDLogging);
		report.check(started, what + ": startSDLogging did not complete: it threw, hung or was skipped (see the events)");
		if (started) {
			report.check(waitFor(device::isSDLogging, 5000), what + ": SD logging did not start (state " + state() + "; is there an SD card?)");
		}
		report.check(startStreaming(10000), what + ": streaming did not restart after starting SD logging");
		report.check(waitFor(() -> state() == BT_STATE.STREAMING_AND_SDLOGGING, 5000),
				what + ": not streaming and logging after the restart (state " + state() + ")");
		return before;
	}

	boolean startStreaming(long timeoutMs) throws InterruptedException {
		return call("startStreaming", 10000, device::startStreaming) && waitFor(device::isStreaming, timeoutMs);
	}

	boolean stopStreaming(long timeoutMs) throws InterruptedException {
		event("while streaming: timers " + device.timers());
		return call("stopStreaming", 10000, device::stopStreaming) && waitFor(() -> !device.isStreaming(), timeoutMs);
	}

	boolean disconnect(long timeoutMs) throws InterruptedException {
		return call("disconnect", 10000, device::disconnect) && waitFor(() -> {
			BT_STATE s = state();
			return s == BT_STATE.DISCONNECTED || s == BT_STATE.CONNECTION_LOST;
		}, timeoutMs);
	}

	/** Instructions waiting to be sent, read as the driver's own accessors read them. */
	int queuedInstructions() {
		return device.queuedInstructions();
	}

	@Override
	public void close() {
		try {
			// After a hung call leave the unit alone; the runner's timeout ends the JVM.
			if (hung == null) {
				BT_STATE s = state();
				if (s != null && s != BT_STATE.DISCONNECTED && s != BT_STATE.CONNECTION_LOST) {
					if (device.isStreaming()) {
						stopStreaming(5000);
					}
					disconnect(10000);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			removeSetWaitForData(device);
			calls.shutdownNow();
		}
	}
}
