package com.shimmerresearch.capture;

/**
 * How well the samples are arriving, counted as shimmer-host-core's bindings/CAPTURE.md defines
 * its figures, so that this app's figures compare with every other Capture app's:
 * <ul>
 * <li><b>Missing:</b> a step of more than one sampling period between consecutive device
 * timestamps ({@code Timestamp}, calibrated, ms) counts the samples that should have filled it,
 * {@code round(step / period) - 1};</li>
 * <li><b>Reception:</b> {@code received / (received + missing)}, in percent;</li>
 * <li><b>Delay:</b> each sample's arrival ({@code System_Timestamp}) minus its device time, less
 * the least such difference so far, now and worst;</li>
 * <li><b>Rx /s</b> over the last second, and how long ago the last sample came.</li>
 * </ul>
 * Times are passed in, so the arithmetic is tested without a clock. Thread-safe: samples come on
 * the driver's thread, snapshots are taken on the window's.
 */
public final class ReceptionStats {

	/** The figures at one moment, and over the window since the snapshot before */
	public static final class Snapshot {
		public final long samples;
		public final long missing;
		/** Percent; NaN before any sample */
		public final double reception;
		/** The latest sample's delay and the worst so far, ms; NaN before any timed sample */
		public final double delayMs;
		public final double maxDelayMs;
		/** Since the last sample arrived, ms; -1 before any */
		public final long sinceLastMs;
		public final long windowSamples;
		public final double windowSeconds;

		Snapshot(long samples, long missing, double delayMs, double maxDelayMs, long sinceLastMs, long windowSamples,
				double windowSeconds) {
			this.samples = samples;
			this.missing = missing;
			this.reception = percent(samples, missing);
			this.delayMs = delayMs;
			this.maxDelayMs = maxDelayMs;
			this.sinceLastMs = sinceLastMs;
			this.windowSamples = windowSamples;
			this.windowSeconds = windowSeconds;
		}

		/** Samples received per second over the window: the Rx /s figure */
		public double rxPerSecond() {
			return windowSeconds > 0 ? windowSamples / windowSeconds : 0;
		}
	}

	private double periodMs = Double.NaN;
	private long samples;
	private long missing;
	private double lastDeviceMs = Double.NaN;
	private double minOffsetMs = Double.NaN;
	private double lastOffsetMs = Double.NaN;
	private double maxDelayMs = Double.NaN;
	private long lastArrivedAt = -1;
	private long windowSamples;
	private long windowStart;

	public ReceptionStats(long nowMs) {
		reset(Double.NaN, nowMs);
	}

	/** Starts counting afresh, for a stream at {@code samplingRateHz} */
	public synchronized void reset(double samplingRateHz, long nowMs) {
		periodMs = samplingRateHz > 0 ? 1000.0 / samplingRateHz : Double.NaN;
		samples = 0;
		missing = 0;
		lastDeviceMs = Double.NaN;
		minOffsetMs = Double.NaN;
		lastOffsetMs = Double.NaN;
		maxDelayMs = Double.NaN;
		lastArrivedAt = -1;
		windowSamples = 0;
		windowStart = nowMs;
	}

	/** One sample: its device time (ms, NaN if none), its arrival on the wall clock (ms), and now on a monotonic clock */
	public synchronized void onSample(double deviceMs, double arrivalMs, long nowMs) {
		samples++;
		windowSamples++;
		lastArrivedAt = nowMs;
		if (Double.isNaN(deviceMs)) {
			return;
		}
		if (!Double.isNaN(lastDeviceMs) && !Double.isNaN(periodMs)) {
			long gap = Math.round((deviceMs - lastDeviceMs) / periodMs) - 1;
			if (gap > 0) {
				missing += gap;
			}
		}
		lastDeviceMs = deviceMs;
		if (Double.isNaN(arrivalMs)) {
			return;
		}
		double offset = arrivalMs - deviceMs;
		if (Double.isNaN(minOffsetMs) || offset < minOffsetMs) {
			minOffsetMs = offset;
		}
		lastOffsetMs = offset;
		double delay = offset - minOffsetMs;
		maxDelayMs = Double.isNaN(maxDelayMs) ? delay : Math.max(maxDelayMs, delay);
	}

	/** The figures now; with {@code endWindow} the window starts again (once a second, for Rx /s) */
	public synchronized Snapshot snapshot(long nowMs, boolean endWindow) {
		Snapshot s = new Snapshot(samples, missing, lastOffsetMs - minOffsetMs, maxDelayMs,
				lastArrivedAt < 0 ? -1 : nowMs - lastArrivedAt, windowSamples, (nowMs - windowStart) / 1000.0);
		if (endWindow) {
			windowSamples = 0;
			windowStart = nowMs;
		}
		return s;
	}

	static double percent(long received, long missing) {
		return received + missing > 0 ? 100.0 * received / (received + missing) : Double.NaN;
	}

}
