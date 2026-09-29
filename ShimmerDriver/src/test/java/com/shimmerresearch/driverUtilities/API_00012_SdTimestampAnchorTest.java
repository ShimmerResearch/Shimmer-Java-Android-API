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
	 * Header and packet values read from the raw 000 and 001 files of the
	 * Shimmer3R recording DEV-1095 was raised on (CE2F, 1024 Hz, 2026-09-25).
	 * File 000's first packet predates its header by 5332 ticks (162.72 ms), file
	 * 001's by 129 (3.94 ms); pinned to their headers, the files stepped back
	 * 5203 ticks (158.78 ms), exactly the step seen in the Consensys export.
	 * Anchored, the split is one sample period.
	 */
	@Test
	public void realShimmer3rRecordingSplitsByOnePeriod() {
		long header000 = 391630116578L;
		long firstRaw000 = 16335374L; // 5332 ticks below the header's low bits
		long firstCounter000 = 391630111246L;
		long lastCounter000 = 391748082222L; // 3,675,280 records later
		long header001 = 391748082383L;
		long firstRaw001 = 88654L;
		long firstCounter001 = 391748082254L;

		double off000 = SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(header000, firstRaw000, MAX_3_BYTE);
		double off001 = SdTimestampAnchor.firstTsOffsetFromInitialTsTicks(header001, firstRaw001, MAX_3_BYTE);
		assertEquals(firstCounter000, placed(header000, firstRaw000, off000), 0);
		assertEquals(firstCounter001, placed(header001, firstRaw001, off001), 0);

		// Unwrapped within file 000: the first raw value plus the ticks elapsed.
		double end000 = placed(header000, firstRaw000 + (lastCounter000 - firstCounter000), off000);
		assertEquals(lastCounter000, end000, 0);
		assertEquals(PERIOD_TICKS, placed(header001, firstRaw001, off001) - end000, 0);

		// The previous rule reproduced the export's step.
		double oldEnd000 = placed(header000, firstRaw000 + (lastCounter000 - firstCounter000), firstRaw000);
		double oldStart001 = placed(header001, firstRaw001, firstRaw001);
		assertEquals(PERIOD_TICKS - 5203, oldStart001 - oldEnd000, 0);
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
