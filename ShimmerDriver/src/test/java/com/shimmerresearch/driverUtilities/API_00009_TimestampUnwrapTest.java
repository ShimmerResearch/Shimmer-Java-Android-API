package com.shimmerresearch.driverUtilities;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Covers {@link TimestampUnwrap}, and in particular the one input that made a
 * customer's 9 minute 30 second recording import as 43 minutes 38: a record
 * whose timestamp field is exactly zero, read as a 24-bit roll-over.
 *
 * @author Mark Nolan
 */
public class API_00009_TimestampUnwrapTest {

	private static final int MAX_3_BYTE = TimestampUnwrap.TICKS_MAX_3_BYTE; // 2^24
	private static final int MAX_2_BYTE = 65536;
	/** 32768 ticks per second; 65 ticks per sample at the customer's 504.123 Hz. */
	private static final double TICKS_PER_SECOND = 32768.0;
	private static final int PERIOD_TICKS = 65;
	/** Eight sample periods at 504.123 Hz - the reorder window at that rate. */
	private static final double WINDOW_504HZ = 8 * PERIOD_TICKS;

	/** Feeds a series of raw values through the unwrapper, as a caller would. */
	private static class Unwrapper {
		double lastUnwrapped = 0;
		double cycle = 0;
		boolean hasPrevious = false;
		boolean lastRejected = false;
		final double window;

		/** Reorder detection off, which is what a caller with no rate gets. */
		Unwrapper() {
			this(0.0);
		}

		Unwrapper(double window) {
			this.window = window;
		}

		double feed(double rawTicks, int maxTicks) {
			//The six-argument form: a stream knows whether it has seen a sample, and
			//(0, 0) cannot say so on its own once a reorder can land on the origin.
			TimestampUnwrap.Result r = TimestampUnwrap.unwrap(rawTicks, lastUnwrapped, cycle, maxTicks,
					window, hasPrevious);
			hasPrevious = true;
			lastRejected = r.rejected;
			cycle = r.cycle;
			lastUnwrapped = r.unwrapped;
			return r.unwrapped;
		}
	}

	@Test
	public void testMonotonicSamplesAreUnchanged() {
		Unwrapper u = new Unwrapper();
		assertEquals(1000, u.feed(1000, MAX_3_BYTE), 0.0);
		assertEquals(1065, u.feed(1065, MAX_3_BYTE), 0.0);
		assertEquals(1130, u.feed(1130, MAX_3_BYTE), 0.0);
		assertFalse("nothing here is invalid", u.lastRejected);
		assertEquals("no wrap was counted", 0.0, u.cycle, 0.0);
	}

	@Test
	public void testGenuineWrapIsCounted() {
		Unwrapper u = new Unwrapper();
		u.feed(MAX_3_BYTE - 16, MAX_3_BYTE);
		double afterWrap = u.feed(16, MAX_3_BYTE);

		assertFalse("a real wrap is not a rejection", u.lastRejected);
		assertEquals("one wrap counted", 1.0, u.cycle, 0.0);
		assertEquals("timeline advanced by 32 ticks, not by a modulo",
				MAX_3_BYTE + 16, afterWrap, 0.0);
	}

	/**
	 * A wrap can legitimately land on zero - the counter simply reached its last
	 * tick. What distinguishes it from a corrupt record is where it came from.
	 */
	@Test
	public void testWrapLandingExactlyOnZeroIsAccepted() {
		Unwrapper u = new Unwrapper();
		u.feed(MAX_3_BYTE - 100, MAX_3_BYTE);
		double afterWrap = u.feed(0, MAX_3_BYTE);

		assertFalse("predecessor was at the top of the range, so this is a wrap",
				u.lastRejected);
		assertEquals("one wrap counted", 1.0, u.cycle, 0.0);
		assertEquals(MAX_3_BYTE, afterWrap, 0.0);
	}

	/** The defect: mid-range, then exactly zero. */
	@Test
	public void testIsolatedZeroMidRangeIsRejected() {
		Unwrapper u = new Unwrapper();
		u.feed(7406506, MAX_3_BYTE);
		double afterZero = u.feed(0, MAX_3_BYTE);

		assertTrue("an exact zero from mid-range is an invalid record", u.lastRejected);
		assertEquals("no wrap may be counted for it", 0.0, u.cycle, 0.0);
		assertEquals("the timeline holds where it was", 7406506, afterZero, 0.0);
	}

	/** Rejecting one sample must not disturb the next. */
	@Test
	public void testRejectionDoesNotCascade() {
		Unwrapper u = new Unwrapper();
		u.feed(7406506, MAX_3_BYTE);
		u.feed(0, MAX_3_BYTE);
		double next = u.feed(7406571, MAX_3_BYTE);

		assertFalse("the following sample is ordinary", u.lastRejected);
		assertEquals("and lands one sample period on", 7406571, next, 0.0);
		assertEquals("still no wrap counted", 0.0, u.cycle, 0.0);
	}

	/**
	 * The exact sequence recovered from the customer's file, either side of one of
	 * its four bad records. Before the fix this spanned 512 seconds.
	 */
	@Test
	public void testCustomerSignatureSpansFourSamplePeriods() {
		double[] raw = { 7406116, 7406506, 0, 7406571 };
		Unwrapper u = new Unwrapper();
		double first = u.feed(raw[0], MAX_3_BYTE);
		double last = 0;
		int rejected = 0;
		for (int i = 1; i < raw.length; i++) {
			last = u.feed(raw[i], MAX_3_BYTE);
			if (u.lastRejected) {
				rejected++;
			}
		}

		assertEquals("exactly one record was invalid", 1, rejected);
		assertEquals("no modulo was added", 0.0, u.cycle, 0.0);
		assertEquals("the four records span 455 ticks", 455, last - first, 0.0);
		assertEquals("which is under 14 ms, not 512 seconds",
				0.0139, (last - first) / TICKS_PER_SECOND, 0.001);
	}

	/**
	 * Four bad records is what the customer's 9m30s file contained; read as wraps
	 * they added 2048 seconds and it imported as 43m38s.
	 */
	@Test
	public void testFourBadRecordsNoLongerInflateARecording() {
		Unwrapper u = new Unwrapper();
		double startTicks = 1000000;
		double t = startTicks;
		int samples = 0;

		// A short recording with a bad record every 200 samples.
		for (int i = 0; i < 800; i++) {
			if (i > 0 && i % 200 == 0) {
				u.feed(0, MAX_3_BYTE);
				assertTrue("planted record is rejected", u.lastRejected);
			}
			u.feed(t, MAX_3_BYTE);
			samples++;
			t += PERIOD_TICKS;
		}

		double spanTicks = u.lastUnwrapped - startTicks;
		assertEquals("no wraps counted across the whole recording", 0.0, u.cycle, 0.0);
		assertEquals("span is exactly the samples that were taken",
				(samples - 1) * PERIOD_TICKS, spanTicks, 0.0);
	}

	/** First sample of a recording may legitimately be zero. */
	@Test
	public void testFirstSampleZeroIsAccepted() {
		Unwrapper u = new Unwrapper();
		double first = u.feed(0, MAX_3_BYTE);

		assertFalse("nothing precedes it, so there is nothing to contradict", u.lastRejected);
		assertEquals(0.0, first, 0.0);
		assertEquals(0.0, u.cycle, 0.0);
	}

	/**
	 * Old firmware uses a 2-byte counter whose whole range is 2 seconds, so a
	 * stall really can cross it. The exemption must not apply there.
	 */
	@Test
	public void testTwoByteCounterZeroIsStillAWrap() {
		Unwrapper u = new Unwrapper();
		u.feed(30000, MAX_2_BYTE);
		double afterZero = u.feed(0, MAX_2_BYTE);

		assertFalse("2-byte counters keep their existing behaviour", u.lastRejected);
		assertEquals("one wrap counted", 1.0, u.cycle, 0.0);
		assertEquals(MAX_2_BYTE, afterZero, 0.0);
	}

	/** Only an exact zero is exempt; any other backward step is still a wrap. */
	@Test
	public void testNonZeroBackwardStepIsStillAWrap() {
		Unwrapper u = new Unwrapper();
		u.feed(7406506, MAX_3_BYTE);
		double after = u.feed(1, MAX_3_BYTE);

		assertFalse("a value of 1 is not the invalid marker", u.lastRejected);
		assertEquals("so it is read as a wrap, as before", 1.0, u.cycle, 0.0);
		assertEquals(MAX_3_BYTE + 1, after, 0.0);
	}

	/**
	 * Two adjacent packets delivered the wrong way round. Each is placed where it
	 * was taken, so the output is deliberately not monotonic - and, the point of
	 * this, no modulo is added.
	 */
	@Test
	public void testReorderedPacketIsPlacedWhereItWasTaken() {
		Unwrapper u = new Unwrapper(WINDOW_504HZ);
		u.feed(1000, MAX_3_BYTE);
		u.feed(1000 + 2 * PERIOD_TICKS, MAX_3_BYTE);
		double late = u.feed(1000 + PERIOD_TICKS, MAX_3_BYTE);

		assertFalse("a reordered packet is not an invalid one", u.lastRejected);
		assertEquals("no wrap counted", 0.0, u.cycle, 0.0);
		assertEquals("placed at its own sample time, below its predecessor",
				1000 + PERIOD_TICKS, late, 0.0);
		assertEquals("and the next packet carries on", 1000 + 3 * PERIOD_TICKS,
				u.feed(1000 + 3 * PERIOD_TICKS, MAX_3_BYTE), 0.0);
	}

	/** The same value twice: hold the timeline, do not count a wrap. */
	@Test
	public void testDuplicatePacketHoldsTheTimeline() {
		Unwrapper u = new Unwrapper(WINDOW_504HZ);
		u.feed(1000, MAX_3_BYTE);
		u.feed(1065, MAX_3_BYTE);
		double again = u.feed(1065, MAX_3_BYTE);

		assertFalse(u.lastRejected);
		assertEquals("held", 1065, again, 0.0);
		assertEquals("no wrap counted", 0.0, u.cycle, 0.0);
	}

	/**
	 * A packet arriving late from <em>before</em> a roll-over. This is the case a
	 * rule comparing unwrapped values rather than modular distances gets wrong: the
	 * late packet's candidate sits nearly a whole modulo ahead, so it is accepted,
	 * and the sample after it is then read as a second wrap. Two 512 s errors from
	 * one out-of-order packet.
	 */
	@Test
	public void testPacketArrivingLateFromBeforeAWrapIsNotASecondWrap() {
		Unwrapper u = new Unwrapper(WINDOW_504HZ);
		u.feed(MAX_3_BYTE - 10, MAX_3_BYTE);
		double afterWrap = u.feed(5, MAX_3_BYTE);
		double late = u.feed(MAX_3_BYTE - 10, MAX_3_BYTE);
		double next = u.feed(70, MAX_3_BYTE);

		assertEquals("the wrap itself", MAX_3_BYTE + 5, afterWrap, 0.0);
		assertEquals("the late packet belongs before the boundary", MAX_3_BYTE - 10, late, 0.0);
		assertEquals("and the stream resumes after it", MAX_3_BYTE + 70, next, 0.0);
		assertEquals("exactly one wrap across the whole sequence", 1.0, u.cycle, 0.0);
	}

	/**
	 * A roll-over preceded by a long dropout. Forward motion is the default, so
	 * however much was lost beforehand the wrap is still a wrap. A rule that treats
	 * an unexplained backward step as corruption instead loses it.
	 */
	@Test
	public void testWrapPrecededByHeavyLossIsStillAWrap() {
		Unwrapper u = new Unwrapper(WINDOW_504HZ);
		u.feed(16000000, MAX_3_BYTE);
		double afterWrap = u.feed(100, MAX_3_BYTE);

		assertFalse(u.lastRejected);
		assertEquals("one wrap counted", 1.0, u.cycle, 0.0);
		assertEquals(MAX_3_BYTE + 100, afterWrap, 0.0);
	}

	/**
	 * With no rate the window is zero, so a swapped pair reads as a roll-over -
	 * worse than knowing the rate, and identical to what older hosts did. What must
	 * not happen is an infinite window, which would make every backward step a
	 * reorder and lose every wrap.
	 */
	@Test
	public void testAnUnknownRateDisablesReorderDetectionRatherThanBreakingWraps() {
		assertEquals("unknown rate gives no window", 0.0,
				TimestampUnwrap.reorderWindowTicks(0.0, MAX_3_BYTE), 0.0);
		assertEquals("and a division that overflowed to infinity is still no window", 0.0,
				TimestampUnwrap.reorderWindowTicks(Double.POSITIVE_INFINITY, MAX_3_BYTE), 0.0);

		Unwrapper u = new Unwrapper();
		u.feed(1000, MAX_3_BYTE);
		u.feed(1130, MAX_3_BYTE);
		double swapped = u.feed(1065, MAX_3_BYTE);

		assertEquals("read as a wrap, as it always was", 1.0, u.cycle, 0.0);
		assertEquals(MAX_3_BYTE + 1065, swapped, 0.0);
	}

	/**
	 * The window is eight sample periods, clamped so it can never reach a fraction
	 * of the modulo at which no backward step could be a wrap.
	 */
	@Test
	public void testWindowIsEightSamplePeriodsAndClamped() {
		assertEquals("504.123 Hz on the 3-byte counter", WINDOW_504HZ,
				TimestampUnwrap.reorderWindowTicks(TICKS_PER_SECOND / PERIOD_TICKS, MAX_3_BYTE), 1e-9);
		assertEquals("51.2 Hz on the 2-byte counter", 5120.0,
				TimestampUnwrap.reorderWindowTicks(51.2, MAX_2_BYTE), 1e-9);
		assertEquals("1 Hz on the 2-byte counter is clamped to modulo/8", MAX_2_BYTE / 8.0,
				TimestampUnwrap.reorderWindowTicks(1.0, MAX_2_BYTE), 0.0);
	}

	/**
	 * A 1.8 second dropout across the 2 second counter - an ordinary Bluetooth gap.
	 * A window sized as a fraction of the modulo reads this as a reordered packet
	 * and silently loses the wrap; eight sample periods does not.
	 */
	@Test
	public void testWrapSpanningDropoutOnTheTwoByteCounterIsNotAReorder() {
		double window = TimestampUnwrap.reorderWindowTicks(51.2, MAX_2_BYTE);
		int lost = (int) (1.8 * TICKS_PER_SECOND);
		int before = 60000;
		int landing = (before + lost) % MAX_2_BYTE;

		Unwrapper u = new Unwrapper(window);
		u.feed(before, MAX_2_BYTE);
		double after = u.feed(landing, MAX_2_BYTE);

		assertEquals("one wrap counted", 1.0, u.cycle, 0.0);
		assertEquals("the gap is preserved at its true length", before + lost, after, 0.0);
	}

	/**
	 * A reorder can land exactly on the counter's origin, which puts a host that
	 * stores (unwrapped, cycle) back into the state it uses for "no sample yet".
	 * The next packet is then read as a first sample and passed through, so one
	 * arriving from just before the origin is placed a whole modulo late.
	 *
	 * Telling the unwrap outright is the fix. The sequence is also the shared
	 * vector reorder-onto-origin-then-earlier-packet-24bit, so the other host
	 * APIs are held to the same answer.
	 */
	@Test
	public void testAReorderOntoTheOriginDoesNotLookLikeAFreshStream() {
		double window = TimestampUnwrap.reorderWindowTicks(32768.0 / 65, MAX_3_BYTE);

		Unwrapper u = new Unwrapper(window);
		u.feed(520, MAX_3_BYTE);
		assertEquals("the reorder lands on the origin", 0.0, u.feed(0, MAX_3_BYTE), 0.0);
		assertEquals("and is not rejected - it is a reorder, not an unstamped record",
				false, u.lastRejected);

		double earlier = u.feed(MAX_3_BYTE - 16, MAX_3_BYTE);
		assertEquals("a packet from just before the origin sits 16 ticks below it",
				-16.0, earlier, 0.0);
		assertEquals("which is one cycle down, and that is a real state", -1.0, u.cycle, 0.0);
	}

	/**
	 * What the five-argument overload does with the same sequence, stated so the
	 * compatibility boundary is deliberate rather than discovered. It infers
	 * "no previous sample" from (0, 0) and therefore gets this one wrong; it is
	 * kept only for callers written before the distinction existed.
	 */
	@Test
	public void testTheFiveArgumentOverloadStillInfersItFromZeroZero() {
		double window = TimestampUnwrap.reorderWindowTicks(32768.0 / 65, MAX_3_BYTE);

		TimestampUnwrap.Result r = TimestampUnwrap.unwrap(MAX_3_BYTE - 16, 0.0, 0.0, MAX_3_BYTE,
				window);

		assertEquals("passed through as a first sample", MAX_3_BYTE - 16, r.unwrapped, 0.0);
		assertEquals("no cycle counted", 0.0, r.cycle, 0.0);
	}
}
