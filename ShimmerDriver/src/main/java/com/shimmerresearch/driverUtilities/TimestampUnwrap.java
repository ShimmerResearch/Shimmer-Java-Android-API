package com.shimmerresearch.driverUtilities;

/**
 * Turns a Shimmer's wrapping packet tick counter into a monotonic one.
 * <p>
 * The counter is 3 bytes at 32768 Hz on current firmware, so it returns to zero
 * every 512 seconds exactly, and a host has to add a whole modulo back each time
 * it does. The obvious rule - if this sample reads lower than the last one, a
 * wrap happened - is what every Shimmer host API implemented, and it is wrong
 * three times over: on an out-of-order packet, on a record the firmware never
 * stamped, and on a packet arriving late from before a wrap boundary.
 * <p>
 * Each sample is classified by its <em>modular forward distance</em> from the
 * previous one, with forward motion as the default:
 * <ol>
 * <li><b>Duplicate</b> - the same raw value again. The timeline holds.</li>
 * <li><b>Reordered</b> - no further back than
 * {@link #reorderWindowTicks(double, int)}. Placed where it was actually taken,
 * which is below its predecessor. The output is deliberately not monotonic: a
 * late packet belongs at the time it was sampled, not at the time it arrived.</li>
 * <li><b>Invalid</b> - the 3-byte counter, a raw value of exactly zero, and a
 * predecessor more than {@link #WRAP_WINDOW_TICKS} below the top of the range.
 * Firmware stamps a packet when the sample tick starts it and does not publish a
 * packet it never stamped, so <code>0x000000</code> means the record is invalid,
 * not that the counter reached its origin. LogAndStream v1.00.x-v1.01.003 could
 * emit one under SD write back-pressure. Read as a wrap, a single such record
 * makes every later sample in the recording 512 seconds late - which is how a 9
 * minute 30 second trial came back as 43 minutes 38. The timeline holds, the
 * wrap count is left alone and {@link Result#rejected} is set.</li>
 * <li><b>Forward</b> - everything else, which is a wrap when the raw value fell.
 * This is the <em>default</em>, and that matters: a wrap preceded by a long
 * dropout is still a wrap, however much was lost before it.</li>
 * </ol>
 * <p>
 * Two details are easy to get wrong and are load-bearing here.
 * <p>
 * <b>The comparison is on modular distance, not on unwrapped values.</b> Asking
 * whether the new unwrapped candidate is below the last one misses a packet that
 * arrives late from <em>before</em> a wrap boundary: its candidate sits nearly a
 * whole modulo ahead, so it is accepted, and the next real sample is then read as
 * a second wrap. The sequence 16777206, 5, 16777206, 70 is the smallest case, and
 * it costs 512 seconds twice over.
 * <p>
 * <b>The reorder window is sized in sample periods</b>, not as a fraction of the
 * modulo - see {@link #reorderWindowTicks(double, int)}.
 * <p>
 * Pure and static so that both of the driver's timestamp paths -
 * <code>ShimmerObject</code> and <code>SensorShimmerClock</code> - share one rule
 * rather than two copies of it, and so that it can be tested without a device.
 * The rule and its conformance vectors are specified in <code>log-and-stream-common</code>,
 * <code>docs/SHIMMER3_STREAMING_DATA_FORMAT.md</code> section 2.1; the vectors are
 * run against this class by API_00010_TimestampUnwrapVectorsTest.
 *
 * @author Mark Nolan
 * @author Mas Azalya Yusof (reordered and duplicated packet detection)
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

	/** Sample periods a packet may lag its predecessor and still be a reorder. */
	public static final int REORDER_PERIODS = 8;

	/**
	 * The clock the packet tick counter runs on. Not the sampling clock, which is
	 * 312500 or 255765.625 Hz on a TCXO board - see
	 * {@link #reorderWindowTicks(double, int)}.
	 */
	public static final double RTC_TICKS_PER_SECOND = 32768.0;

	/** The reorder window is never allowed past this fraction of the modulo. */
	public static final int MAX_WINDOW_DIVISOR = 8;

	/** Outcome of unwrapping one sample. */
	public static final class Result {
		/** The unwrapped tick count to use. On a rejected sample, the previous one. */
		public final double unwrapped;
		/**
		 * Wrap count after this sample: floor(unwrapped / maxTicks). Unchanged when
		 * the sample was rejected, and one lower than its predecessor's for a packet
		 * that arrived late from before a wrap boundary.
		 */
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
	 * How far back a sample may be from its predecessor and still be treated as a
	 * reordered packet rather than as a wrap.
	 * <p>
	 * Sized in sample periods, because that is what distinguishes the two cases: a
	 * reorder swaps packets that are adjacent in time - a handful of periods -
	 * whereas a dropout that spans the counter's wrap point is most of a modulo.
	 * Sizing the window as a fraction of the modulo confuses them. At
	 * <code>modulo / 8</code> on the 2-byte counter, every dropout between 1.75 and
	 * 2.0 seconds reads as a reorder and the wrap is silently lost - and a 1.75
	 * second Bluetooth gap is ordinary. Eight periods shrinks the band in which
	 * that can happen to about 16 milliseconds.
	 * <p>
	 * The rate must be in the {@link #RTC_TICKS_PER_SECOND} domain, which is what
	 * <code>getSamplingRateShimmer()</code> returns. Do not derive it from
	 * <code>getSamplingClockFreq()</code>: that is the sampling clock, and on a
	 * TCXO board it is 312500 or 255765.625 Hz, which would widen the window by
	 * roughly nine and a half times.
	 * <p>
	 * <b>Returns zero - the branch disabled - when the rate is not known.</b> Never
	 * guess: <code>32768 / 0</code> is {@link Double#POSITIVE_INFINITY} in Java, and
	 * an infinite window classifies every backward step as a reorder and loses every
	 * wrap, which is worse than the naive rule this replaces. Rejecting an
	 * unstamped record needs no rate, so that still works with the window at zero.
	 *
	 * @param samplingRateHz the configured rate, in Hz; zero, negative, NaN or
	 *        infinite all mean "not known"
	 * @param maxTicks the counter's modulo
	 * @return the window in ticks, clamped so that it can never reach the modulo
	 *         and leave no backward step large enough to be read as a wrap
	 */
	public static double reorderWindowTicks(double samplingRateHz, int maxTicks) {
		if (Double.isNaN(samplingRateHz) || Double.isInfinite(samplingRateHz) || samplingRateHz <= 0.0) {
			return 0.0;
		}
		double window = REORDER_PERIODS * RTC_TICKS_PER_SECOND / samplingRateHz;
		return Math.min(window, (double) maxTicks / MAX_WINDOW_DIVISOR);
	}

	/**
	 * Unwraps one sample with the reorder branch disabled. Equivalent to passing a
	 * window of zero; kept so that a caller with no rate to hand still compiles.
	 *
	 * @see #unwrap(double, double, double, int, double)
	 */
	public static Result unwrap(double rawTicks, double lastUnwrapped, double cycle, int maxTicks) {
		return unwrap(rawTicks, lastUnwrapped, cycle, maxTicks, 0.0);
	}

	/**
	 * @param rawTicks the packet's raw tick value
	 * @param lastUnwrapped the unwrapped value returned for the previous sample
	 * @param cycle how many wraps have been counted so far
	 * @param maxTicks the counter's modulo - 2^24, or 2^16 on old firmware
	 * @param reorderWindowTicks from {@link #reorderWindowTicks(double, int)}; zero
	 *        disables reorder detection
	 * @return the unwrapped value, the new wrap count, and whether the sample was
	 *         rejected as invalid
	 */
	public static Result unwrap(double rawTicks, double lastUnwrapped, double cycle, int maxTicks,
			double reorderWindowTicks) {
		// (0, 0) is the reset state AND a state the rule can reach, so this
		// overload cannot always tell them apart - see the six-argument form.
		// Kept for callers that predate the distinction and behaves as before.
		return unwrap(rawTicks, lastUnwrapped, cycle, maxTicks, reorderWindowTicks,
				!(lastUnwrapped == 0.0 && cycle == 0.0));
	}

	/**
	 * As above, but told outright whether a previous sample exists.
	 *
	 * The five-argument form infers it from the state being (0, 0). That is the
	 * reset state, and it is also a state the rule can produce: a reorder that
	 * lands exactly on the counter's origin leaves lastUnwrapped = 0 and
	 * cycle = 0 in the middle of a stream. The next packet is then read as a
	 * first sample and passed through, so one arriving from just before the
	 * origin is placed a whole modulo late rather than a few ticks behind. The
	 * conformance vector {@code reorder-onto-origin-then-earlier-packet-24bit}
	 * is exactly that sequence.
	 *
	 * Hosts that keep the previous RAW value instead of a cycle count - the web
	 * SDK and pyshimmer - never had the ambiguity, which is why this is stated
	 * as a separate flag rather than fixed by choosing a different reset value:
	 * {@link #getLastReceivedTimeStampTicksUnwrapped()} is public and some
	 * callers seed it across files.
	 *
	 * @param hasPreviousSample false only before the first sample of a stream
	 */
	public static Result unwrap(double rawTicks, double lastUnwrapped, double cycle, int maxTicks,
			double reorderWindowTicks, boolean hasPreviousSample) {
		if (!hasPreviousSample) {
			// No predecessor to measure against. Taking the reset state as a real
			// sample at zero would let a first raw value near the top of the range
			// read as a packet reordered across a boundary, placing a whole
			// recording one modulo early.
			return new Result(rawTicks, 0.0, false);
		}

		double lastRaw = lastUnwrapped - (maxTicks * cycle);
		double forward = rawTicks - lastRaw;
		if (forward < 0) {
			forward += maxTicks;
		}
		double backwards = maxTicks - forward;

		double candidate;
		if (forward == 0.0) {
			// The same value again: a duplicate. Hold the timeline where it is.
			candidate = lastUnwrapped;
		} else if (backwards <= reorderWindowTicks) {
			// Reordered, on either side of a wrap boundary.
			candidate = lastUnwrapped - backwards;
		} else if (maxTicks == TICKS_MAX_3_BYTE
				&& rawTicks == 0.0
				&& lastRaw < (maxTicks - WRAP_WINDOW_TICKS)) {
			// Mid-range and then exactly zero: a record the firmware never stamped.
			// Hold the timeline where it was and say so. Nothing about this sample
			// moves the state, so the next real one is an ordinary step forward and
			// the rejection cannot cascade.
			return new Result(lastUnwrapped, cycle, true);
		} else {
			// Forward motion, which is a wrap when the raw value fell.
			candidate = lastUnwrapped + forward;
		}

		return new Result(candidate, Math.floor(candidate / maxTicks), false);
	}
}
