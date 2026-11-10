package com.shimmerresearch.hil;

import java.util.List;

/**
 * Counts how often the unit's own timestamp counter wrapped during a stream, from the raw ticks the
 * driver passes on over Bluetooth (the TIMESTAMP channel's UNCAL format; CAL is the unwrapped
 * time). The counter is 3 bytes at 32768 Hz on Shimmer3 and Shimmer3R, so it wraps every 2^24
 * ticks, 512 s. Pure Java, so it is unit-tested on the JVM (TimestampWrapsTest, in the normal test
 * task). Kept apart from StreamAnalysis, which is the same class as the Android HIL's.
 */
final class TimestampWraps {

	/** The raw counter's range: 3 bytes. */
	static final double RANGE_TICKS = 1 << 24;

	private TimestampWraps() {
	}

	/**
	 * Steps where the raw counter fell by more than half its range: a wrap. A smaller step back is
	 * not a wrap but a fault the stream checks already count (StreamAnalysis.nonIncreasing). A packet
	 * without ticks (NaN) is skipped, so the step is taken from the last one that had them.
	 */
	static int count(List<Double> rawTicks) {
		int wraps = 0;
		double previous = Double.NaN;
		for (double ticks : rawTicks) {
			if (Double.isNaN(ticks)) {
				continue;
			}
			if (previous - ticks > RANGE_TICKS / 2) {
				wraps++;
			}
			previous = ticks;
		}
		return wraps;
	}
}
