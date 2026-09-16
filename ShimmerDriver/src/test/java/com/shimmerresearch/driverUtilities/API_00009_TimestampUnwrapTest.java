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

	/** Feeds a series of raw values through the unwrapper, as a caller would. */
	private static class Unwrapper {
		double lastUnwrapped = 0;
		double cycle = 0;
		boolean lastRejected = false;

		double feed(double rawTicks, int maxTicks) {
			TimestampUnwrap.Result r = TimestampUnwrap.unwrap(rawTicks, lastUnwrapped, cycle, maxTicks);
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
}
