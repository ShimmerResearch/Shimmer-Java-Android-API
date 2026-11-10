package com.shimmerresearch.hil;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** The rollover scenario's wrap count, checked without a unit. */
public class TimestampWrapsTest {

	/** Raw 3-byte ticks at 256 Hz (128 ticks a sample) from startTicks, as the counter gives them. */
	private static List<Double> rawTicks(double startTicks, int count) {
		List<Double> ticks = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ticks.add((startTicks + i * 128.0) % TimestampWraps.RANGE_TICKS);
		}
		return ticks;
	}

	@Test
	public void countsNoWrapInAShortStream() {
		assertEquals(0, TimestampWraps.count(rawTicks(1000, 2560)));
	}

	@Test
	public void countsTheWrapInTenMinutesWhereverTheStreamStarts() {
		// 600 s at 256 Hz is 153,600 samples: one wrap from any start, two from one just before a wrap
		assertEquals(1, TimestampWraps.count(rawTicks(0, 153600)));
		assertEquals(1, TimestampWraps.count(rawTicks(TimestampWraps.RANGE_TICKS / 2, 153600)));
		assertEquals(2, TimestampWraps.count(rawTicks(TimestampWraps.RANGE_TICKS - 1000, 153600)));
	}

	@Test
	public void aSmallStepBackIsNotAWrap() {
		List<Double> ticks = rawTicks(5000000, 100);
		ticks.set(50, ticks.get(49) - 1000);
		assertEquals(0, TimestampWraps.count(ticks));
	}

	@Test
	public void ignoresTicksTheDriverDidNotGive() {
		List<Double> ticks = rawTicks(TimestampWraps.RANGE_TICKS - 640, 10);
		ticks.set(4, Double.NaN);
		assertEquals(1, TimestampWraps.count(ticks));
	}
}
