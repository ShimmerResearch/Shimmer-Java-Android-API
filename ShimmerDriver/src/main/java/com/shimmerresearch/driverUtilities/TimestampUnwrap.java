package com.shimmerresearch.driverUtilities;

/**
 * Turns a Shimmer's wrapping packet tick counter into a monotonic one.
 * <p>
 * The counter is 3 bytes at 32768 Hz on current firmware, so it returns to zero
 * every 512 seconds exactly, and a host has to add a whole modulo back each time
 * it does. The obvious rule - if this sample reads lower than the last one, a
 * wrap happened - is what every Shimmer host API has implemented, and it is
 * wrong for one input: a record whose timestamp field is exactly zero.
 * <p>
 * Firmware stamps a packet when the sample tick starts it and does not publish a
 * packet it never stamped, so <code>0x000000</code> in that field means the
 * record is invalid, not that the counter reached its origin. LogAndStream
 * v1.00.x-v1.01.003 could emit one under SD write back-pressure. Read as a wrap,
 * a single such record makes every later sample in the recording 512 seconds
 * late - which is how a 9 minute 30 second trial came back as 43 minutes 38.
 * <p>
 * So: a backward step is a roll-over <em>unless</em> the counter is the 3-byte
 * one, the new value is exactly zero, and the previous value was more than
 * {@link #WRAP_WINDOW_TICKS} below the top of the range. In that case the sample
 * is rejected - the caller is handed the previous timestamp back, the wrap count
 * is left alone, and {@link Result#rejected} is set.
 * <p>
 * The exemption is deliberately narrow. A genuine wrap that happens to land on
 * zero still has a predecessor near the top of the range, so it is accepted. A
 * 2-byte counter (older firmware, 2 second range, where a stall really can
 * exceed a second) is never rejected and keeps its existing behaviour. And
 * rejection cannot cascade: the following sample reads above the retained
 * previous value, so it is accepted normally and the timeline carries on at its
 * true spacing.
 * <p>
 * Pure and static so that both of the driver's timestamp paths -
 * <code>ShimmerObject</code> and <code>SensorShimmerClock</code> - share one
 * rule rather than two copies of it, and so that it can be tested without a
 * device.
 *
 * @author Mark Nolan
 */
public final class TimestampUnwrap {

	/** Maximum value of the 3-byte tick counter, exclusive: 2^24 ticks = 512 s. */
	public static final int TICKS_MAX_3_BYTE = 1 << 24;

	/**
	 * How close to the top of the range the previous sample must have been for a
	 * drop to exactly zero to be believed as a wrap. One second at 32768 Hz: a
	 * legitimate wrap onto zero means the counter advanced to its very last tick,
	 * so its predecessor is within a sample or two of the maximum, and a second is
	 * a generous allowance for a gap in the data.
	 */
	public static final int WRAP_WINDOW_TICKS = 32768;

	/** Outcome of unwrapping one sample. */
	public static final class Result {
		/** The unwrapped tick count to use. On a rejected sample, the previous one. */
		public final double unwrapped;
		/** Wrap count after this sample. Unchanged when the sample was rejected. */
		public final double cycle;
		/** True when the raw value was an invalid zero rather than a real wrap. */
		public final boolean rejected;

		public Result(double unwrapped, double cycle, boolean rejected) {
			this.unwrapped = unwrapped;
			this.cycle = cycle;
			this.rejected = rejected;
		}
	}

	private TimestampUnwrap() {
	}

	/**
	 * @param rawTicks the packet's raw tick value
	 * @param lastUnwrapped the unwrapped value returned for the previous sample
	 * @param cycle how many wraps have been counted so far
	 * @param maxTicks the counter's modulo - 2^24, or 2^16 on old firmware
	 * @return the unwrapped value, the new wrap count, and whether the sample was
	 *         rejected as invalid
	 */
	public static Result unwrap(double rawTicks, double lastUnwrapped, double cycle, int maxTicks) {
		double candidate = rawTicks + (maxTicks * cycle);

		if (lastUnwrapped > candidate) {
			// The counter went backwards. Either it wrapped, or this record is bad.
			double lastRaw = lastUnwrapped - (maxTicks * cycle);

			if (maxTicks == TICKS_MAX_3_BYTE
					&& rawTicks == 0.0
					&& lastRaw < (maxTicks - WRAP_WINDOW_TICKS)) {
				// Mid-range and then exactly zero: a record the firmware never
				// stamped. Hold the timeline where it was and say so.
				return new Result(lastUnwrapped, cycle, true);
			}

			cycle += 1;
			candidate = rawTicks + (maxTicks * cycle);
		}

		return new Result(candidate, cycle, false);
	}
}
