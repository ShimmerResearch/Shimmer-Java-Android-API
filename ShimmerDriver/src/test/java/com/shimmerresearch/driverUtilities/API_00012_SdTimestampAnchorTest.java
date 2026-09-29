package com.shimmerresearch.driverUtilities;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Covers {@link SdTimestampAnchor}: an SD data file's records are placed at their
 * own counter time, not pinned to the header's file-creation time. The defect it
 * fixes (DEV-1095) was a permanent -158.8 ms step at the 000 to 001 file split of
 * a Shimmer3R recording.
 *
 * @author Mark Nolan
 */
public class API_00012_SdTimestampAnchorTest {

	private static final int MAX_3_BYTE = TimestampUnwrap.TICKS_MAX_3_BYTE; // 2^24
	private static final int MAX_2_BYTE = 65536;
	/** A full 40-bit counter value, well away from any 24-bit boundary. */
	private static final long INITIAL_TS = 0x12_3456_7000L;
	/** 32 ticks: one sample period at 1024 Hz. */
	private static final long PERIOD_TICKS = 32;

	/** Time the parser gives a record: header + unwrapped - offset. */
	private static double placed(long initialTs, double unwrapped, double offset) {
		return initialTs + unwrapped - offset;
	}

	private static long low24(long ticks) {
		return ticks & (MAX_3_BYTE - 1);
	}

	@Test
	public void firstRecordSampledAfterHeaderLandsOnItsOwnTime() {
		double raw = low24(INITIAL_TS) + 1000;
		double offset = SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(INITIAL_TS, raw, MAX_3_BYTE);
		assertEquals(INITIAL_TS + 1000, placed(INITIAL_TS, raw, offset), 0);
	}

	@Test
	public void firstRecordSampledBeforeHeaderLandsBeforeIt() {
		// File 000: records buffered during SD start-up predate the header by ~160 ms.
		double raw = low24(INITIAL_TS) - 5243;
		double offset = SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(INITIAL_TS, raw, MAX_3_BYTE);
		assertEquals(INITIAL_TS - 5243, placed(INITIAL_TS, raw, offset), 0);
	}

	@Test
	public void leadAcrossForwardWrapOfLowBits() {
		long initialTs = (0x42L << 24) + MAX_3_BYTE - 100; // low bits 100 below the top
		double raw = 200; // the counter wrapped 300 ticks after the header
		double offset = SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(initialTs, raw, MAX_3_BYTE);
		assertEquals(initialTs + 300, placed(initialTs, raw, offset), 0);
	}

	@Test
	public void leadAcrossBackwardWrapOfLowBits() {
		long initialTs = (0x42L << 24) + 50; // header just after a wrap
		double raw = MAX_3_BYTE - 100; // record sampled 150 ticks before it
		double offset = SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(initialTs, raw, MAX_3_BYTE);
		assertEquals(initialTs - 150, placed(initialTs, raw, offset), 0);
	}

	@Test
	public void laterRecordsKeepTheirSpacing() {
		double raw = low24(INITIAL_TS) - 5243;
		double offset = SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(INITIAL_TS, raw, MAX_3_BYTE);
		double first = placed(INITIAL_TS, raw, offset);
		double tenth = placed(INITIAL_TS, raw + 9 * PERIOD_TICKS, offset);
		assertEquals(9 * PERIOD_TICKS, tenth - first, 0);
	}

	/**
	 * Two consecutive files of one continuous recording. File 000's header is
	 * written 5800 ticks (177 ms) after its first record; file 001's 577 ticks
	 * (17.6 ms) after the split's first buffered record. Pinning each file to its
	 * header puts a step of the difference at the boundary; anchoring on the
	 * records' own counter keeps exactly one sample period.
	 */
	@Test
	public void noStepAtFileSplit() {
		long c0 = INITIAL_TS;
		long lastOf000 = c0 + 1000 * PERIOD_TICKS;
		long firstOf001 = lastOf000 + PERIOD_TICKS;
		long header000 = c0 + 5800;
		long header001 = firstOf001 + 577;

		double off000 = SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(header000, low24(c0), MAX_3_BYTE);
		double off001 = SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(header001, low24(firstOf001), MAX_3_BYTE);
		// Unwrapped values within a file: raw first + elapsed (no wrap inside this span).
		double end000 = placed(header000, low24(c0) + (lastOf000 - c0), off000);
		double start001 = placed(header001, low24(firstOf001), off001);

		assertEquals(lastOf000, end000, 0);
		assertEquals(firstOf001, start001, 0);
		assertEquals(PERIOD_TICKS, start001 - end000, 0);

		// The previous rule - subtract the raw first timestamp - stepped back here.
		double oldEnd000 = placed(header000, low24(c0) + (lastOf000 - c0), low24(c0));
		double oldStart001 = placed(header001, low24(firstOf001), low24(firstOf001));
		assertEquals(PERIOD_TICKS - (5800 - 577), oldStart001 - oldEnd000, 0);
	}

	/**
	 * Checks one real file split: each file's first packet lands on its own
	 * counter value, the split is one sample period, and pinning each file to its
	 * header - the previous rule - gives the step the Consensys export showed.
	 *
	 * @param oldStepTicks the previous rule's step beyond one sample period, i.e.
	 *            the difference between the two files' leads
	 */
	private static void assertRealSplit(long headerA, long firstRawA, long firstCounterA, long lastCounterA,
			long headerB, long firstRawB, long firstCounterB, long oldStepTicks) {
		double offA = SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(headerA, firstRawA, MAX_3_BYTE);
		double offB = SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(headerB, firstRawB, MAX_3_BYTE);
		assertEquals(firstCounterA, placed(headerA, firstRawA, offA), 0);
		assertEquals(firstCounterB, placed(headerB, firstRawB, offB), 0);

		// Unwrapped within file A: the first raw value plus the ticks elapsed.
		double unwrappedLastA = firstRawA + (lastCounterA - firstCounterA);
		double endA = placed(headerA, unwrappedLastA, offA);
		assertEquals(lastCounterA, endA, 0);
		assertEquals(PERIOD_TICKS, placed(headerB, firstRawB, offB) - endA, 0);

		// The previous rule reproduced the export's step.
		double oldStep = placed(headerB, firstRawB, firstRawB) - placed(headerA, unwrappedLastA, firstRawA);
		assertEquals(PERIOD_TICKS + oldStepTicks, oldStep, 0);
	}

	/**
	 * Header and packet values read from the raw 000 and 001 files of the
	 * Shimmer3R recording DEV-1095 was raised on (CE2F, 1024 Hz, 2026-09-25).
	 * File 000's first packet predates its header by 5332 ticks (162.72 ms), file
	 * 001's by 129 (3.94 ms); pinned to their headers, the files stepped back
	 * 5203 ticks (158.78 ms), exactly the step seen in the Consensys export.
	 * Anchored, the split is one sample period.
	 */
	@Test
	public void realShimmer3rRecordingSplitsByOnePeriod() {
		assertRealSplit(391630116578L, 16335374L, 391630111246L, 391748082222L, // 000: 3,675,280 records
				391748082383L, 88654L, 391748082254L, -5203);
	}

	/**
	 * The 041 and 042 files of the same recording: leads of 214 ticks (6.53 ms)
	 * and 131 (4.00 ms). The previous rule's step was only -83 ticks, which the
	 * export showed as timestamps going back 1.556 ms, about 27 ms before the
	 * post-split stall. Anchored, this split is one sample period too.
	 */
	@Test
	public void realShimmer3rSplitWithSmallLeadDifference() {
		assertRealSplit(396466711492L, 4319982L, 396466711278L, 396584676654L,
				396584676817L, 4844878L, 396584676686L, -83);
	}

	@Test
	public void strokareCorrectedHeaderStillPinsExactly() {
		// The old-StroKare workaround rewrites the header's low bits to the first
		// record's, so the lead is zero and the result is unchanged.
		double raw = 0x00_ABCDEF;
		long initialTs = (INITIAL_TS & 0xFFFF000000L) + (long) raw;
		assertEquals(raw, SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(initialTs, raw, MAX_3_BYTE), 0);
	}

	@Test
	public void twoByteCounterKeepsPreviousBehaviour() {
		assertEquals(1234.0, SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(INITIAL_TS, 1234, MAX_2_BYTE), 0);
	}

	@Test
	public void zeroInitialTimestampKeepsPreviousBehaviour() {
		double raw = 0x00_F00000;
		assertEquals(raw, SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(0, raw, MAX_3_BYTE), 0);
	}

	@Test
	public void implausibleLeadKeepsPreviousBehaviour() {
		double raw = low24(INITIAL_TS) + SdTimestampAnchor.MAX_LEAD_TICKS + 1;
		assertEquals(raw, SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(INITIAL_TS, raw, MAX_3_BYTE), 0);
		raw = low24(INITIAL_TS) + SdTimestampAnchor.MAX_LEAD_TICKS;
		assertEquals(raw - SdTimestampAnchor.MAX_LEAD_TICKS,
				SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(INITIAL_TS, raw, MAX_3_BYTE), 0);
	}

	@Test
	public void signedLeadIsFoldedIntoHalfRange() {
		assertEquals(0, SdTimestampAnchor.signedLeadTicks(INITIAL_TS, low24(INITIAL_TS), MAX_3_BYTE));
		assertEquals(-MAX_3_BYTE / 2,
				SdTimestampAnchor.signedLeadTicks(0, MAX_3_BYTE / 2, MAX_3_BYTE));
		assertEquals(MAX_3_BYTE / 2 - 1,
				SdTimestampAnchor.signedLeadTicks(0, MAX_3_BYTE / 2 - 1, MAX_3_BYTE));
	}
}
