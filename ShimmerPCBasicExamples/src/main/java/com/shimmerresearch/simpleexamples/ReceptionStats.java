package com.shimmerresearch.simpleexamples;

/**
 * How well one device's samples are arriving, counted the same way whichever driver delivers them,
 * so that the two can be compared on the same device:
 * <ul>
 * <li><b>missing</b>: from the device's own timestamps (the Timestamp channel, in ms). A gap of
 * more than one sampling period between consecutive samples counts the samples that should have
 * filled it. It cannot see samples lost after the last one received;</li>
 * <li><b>delay</b>: each sample's PC arrival time (System_Timestamp) minus its device timestamp,
 * relative to the least-delayed sample so far. It grows when the link falls behind the device and
 * data queues up; it also drifts slowly with the device's crystal, by tens of ms an hour.</li>
 * </ul>
 * Samples arrive on a driver's thread and the UI reads on its own, so every method is synchronized.
 */
final class ReceptionStats {

	/** The figures at one moment, and over the window since the previous snapshot. */
	static final class Snapshot {
		final long samples;
		final long missing;
		/** Received over received plus missing, in percent; NaN before any sample. */
		final double receptionPercent;
		/** The latest sample's delay; NaN before any sample. */
		final double delayMs;
		final double maxDelayMs;
		/** From the first sample's arrival to the last's. */
		final double spanSeconds;
		/** Since the last sample arrived; -1 if none has. */
		final long msSinceLastSample;
		final long windowSamples;
		final long windowMissing;
		final double windowSeconds;
		final double windowMaxDelayMs;

		private Snapshot(ReceptionStats s, long now, double windowSeconds) {
			samples = s.mSamples;
			missing = s.mMissing;
			receptionPercent = percent(s.mSamples, s.mMissing);
			delayMs = s.mLastOffsetMs - s.mMinOffsetMs;
			maxDelayMs = s.mMaxDelayMs;
			spanSeconds = s.mSamples < 2 ? 0 : (s.mLastArrivalMs - s.mFirstArrivalMs) / 1000.0;
			msSinceLastSample = s.mSamples == 0 ? -1 : now - s.mLastReceivedAtMs;
			windowSamples = s.mWindowSamples;
			windowMissing = s.mWindowMissing;
			this.windowSeconds = windowSeconds;
			windowMaxDelayMs = s.mWindowMaxDelayMs;
		}

		/** Samples per second over the window. */
		double windowRate() {
			return windowSeconds <= 0 ? 0 : windowSamples / windowSeconds;
		}

		/** Reception over the window, in percent; NaN if nothing arrived and nothing is known missing. */
		double windowReceptionPercent() {
			return percent(windowSamples, windowMissing);
		}
	}

	private double mPeriodMs = Double.NaN;
	private long mSamples;
	private long mMissing;
	private double mLastDeviceMs = Double.NaN;
	private double mFirstArrivalMs;
	private double mLastArrivalMs;
	private long mLastReceivedAtMs;
	private double mMinOffsetMs = Double.NaN;
	private double mLastOffsetMs = Double.NaN;
	private double mMaxDelayMs = Double.NaN;
	private long mWindowSamples;
	private long mWindowMissing;
	private double mWindowMaxDelayMs = Double.NaN;
	private long mWindowStartedMs = System.currentTimeMillis();

	/** Starts counting again, for a stream at this sampling rate (NaN if unknown: nothing counts as missing). */
	synchronized void reset(double samplingRateHz) {
		mPeriodMs = samplingRateHz > 0 ? 1000.0 / samplingRateHz : Double.NaN;
		mSamples = 0;
		mMissing = 0;
		mLastDeviceMs = Double.NaN;
		mMinOffsetMs = Double.NaN;
		mLastOffsetMs = Double.NaN;
		mMaxDelayMs = Double.NaN;
		mWindowSamples = 0;
		mWindowMissing = 0;
		mWindowMaxDelayMs = Double.NaN;
		mWindowStartedMs = System.currentTimeMillis();
	}

	/**
	 * One sample: its device timestamp in ms (NaN if it has none) and the PC time it arrived, in
	 * ms since the epoch.
	 */
	synchronized void onSample(double deviceMs, double arrivalMs) {
		long now = System.currentTimeMillis();
		if (mSamples == 0) {
			mFirstArrivalMs = arrivalMs;
		}
		mSamples++;
		mWindowSamples++;
		mLastArrivalMs = arrivalMs;
		mLastReceivedAtMs = now;
		if (Double.isNaN(deviceMs)) {
			return;
		}
		if (!Double.isNaN(mLastDeviceMs) && !Double.isNaN(mPeriodMs)) {
			long gap = Math.round((deviceMs - mLastDeviceMs) / mPeriodMs) - 1;
			if (gap > 0) {
				mMissing += gap;
				mWindowMissing += gap;
			}
		}
		mLastDeviceMs = deviceMs;

		double offset = arrivalMs - deviceMs;
		if (Double.isNaN(mMinOffsetMs) || offset < mMinOffsetMs) {
			mMinOffsetMs = offset;
		}
		mLastOffsetMs = offset;
		double delay = offset - mMinOffsetMs;
		mMaxDelayMs = Double.isNaN(mMaxDelayMs) ? delay : Math.max(mMaxDelayMs, delay);
		mWindowMaxDelayMs = Double.isNaN(mWindowMaxDelayMs) ? delay : Math.max(mWindowMaxDelayMs, delay);
	}

	/** The figures now; with {@code endWindow}, also starts the next window. */
	synchronized Snapshot snapshot(boolean endWindow) {
		long now = System.currentTimeMillis();
		Snapshot s = new Snapshot(this, now, (now - mWindowStartedMs) / 1000.0);
		if (endWindow) {
			mWindowSamples = 0;
			mWindowMissing = 0;
			mWindowMaxDelayMs = Double.NaN;
			mWindowStartedMs = now;
		}
		return s;
	}

	private static double percent(long received, long missing) {
		return received + missing <= 0 ? Double.NaN : 100.0 * received / (received + missing);
	}
}
