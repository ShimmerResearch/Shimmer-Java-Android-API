package com.shimmerresearch.driverUtilities;

/**
 * Places the first record of an SD data file at the time it was actually
 * sampled.
 * <p>
 * An SD data file's absolute timeline is its header's initial timestamp plus the
 * elapsed ticks of its records. The driver used to anchor that timeline by
 * subtracting the first record's raw timestamp, which pins the first record to
 * the header time. That is only right if the header holds the first record's
 * time, and LogAndStream does not write that: the header carries the RTC at the
 * moment the file was <em>created</em> (<code>sdFileSyncTs</code>, set in
 * <code>ShimSdDataFile_fileInit</code> and at each hourly split). Records
 * already buffered when the file is opened were sampled before that moment, so
 * pinning erases a lead time that differs from file to file:
 * <ul>
 * <li><b>File 000</b> is created after sampling has started - SD power-up,
 * directory creation, header build - so its first records predate the header by
 * roughly 160 ms more than at a mid-stream split.</li>
 * <li><b>Later files</b> are opened mid-stream, behind only the records still in
 * the SD write buffer.</li>
 * </ul>
 * The difference surfaces as a permanent step at each file boundary: -158.8 ms
 * at the 000 to 001 split of a 66.8 hour Shimmer3R recording (DEV-1095).
 * <p>
 * Nothing is lost to fix it. A record's 3-byte timestamp is the low 24 bits of
 * the same 32768 Hz counter the header's initial timestamp reads, so the first
 * record's full counter value is the header value moved by the signed, wrap-
 * corrected distance between the two low parts. That is exact whenever the
 * header lies within half a counter period (256 s) of the first record, and it
 * does not depend on which moment the firmware chose for the header - so it
 * stays right if the firmware later writes the first record's time instead.
 * <p>
 * The reconstruction is in the device's counter domain. It must be applied to
 * the header's initial timestamp <em>before</em> the RTC difference is added: on
 * Shimmer3 that difference is the offset from the free-running counter to real
 * time, and it is not a multiple of the counter period.
 * <p>
 * Pure and static so that both of the driver's timestamp paths -
 * <code>ShimmerObject</code> and <code>SensorShimmerClock</code> - share one rule,
 * as with {@link TimestampUnwrap}.
 *
 * @author Mark Nolan
 */
public final class SdTimestampAnchor {

	/**
	 * Largest distance between the header's initial timestamp and the first
	 * record that is read as a lead time: 10 s at 32768 Hz. The real lead is well
	 * under a second - the buffered records, plus SD start-up for file 000 - so a
	 * larger distance means the header does not describe this counter, and the
	 * first record keeps the header time as it always has.
	 */
	public static final long MAX_LEAD_TICKS = 10L * 32768L;

	private SdTimestampAnchor() {
	}

	/**
	 * The value to subtract from <code>unwrappedTicks + initialTsTicks</code> so
	 * that each record of an SD data file lands on its own counter time. For the
	 * first record that is <code>initialTsTicks + d</code>, where <code>d</code> is
	 * the signed modular distance from the header's low bits to the record's raw
	 * timestamp.
	 * <p>
	 * Falls back to the first record's raw timestamp - pinning the first record
	 * to the header time, the previous behaviour - when there is nothing to
	 * reconstruct from:
	 * <ul>
	 * <li>a 2-byte counter, whose firmware predates the current header and whose
	 * 2 s period leaves no useful margin;</li>
	 * <li>an initial timestamp of zero, which some log formats write in place of a
	 * time;</li>
	 * <li>a distance larger than {@link #MAX_LEAD_TICKS}.</li>
	 * </ul>
	 *
	 * @param initialTsTicks the header's initial timestamp, in counter ticks,
	 *            without any RTC difference added
	 * @param firstRawTicks the first record's raw (wrapped) timestamp
	 * @param ticksMaxValue the counter's modulo: 2^24 for a 3-byte timestamp
	 * @return the offset to subtract, in ticks
	 */
	public static double firstTsOffsetFromInitialTsTicks(long initialTsTicks, double firstRawTicks,
			int ticksMaxValue) {
		if (ticksMaxValue != TimestampUnwrap.TICKS_MAX_3_BYTE || initialTsTicks == 0) {
			return firstRawTicks;
		}
		long lead = signedLeadTicks(initialTsTicks, (long) firstRawTicks, ticksMaxValue);
		if (Math.abs(lead) > MAX_LEAD_TICKS) {
			return firstRawTicks;
		}
		return firstRawTicks - lead;
	}

	/**
	 * Signed distance from the header's low bits to the first record's raw
	 * timestamp, folded into <code>[-ticksMaxValue/2, ticksMaxValue/2)</code>:
	 * positive when the record was sampled after the header was written, negative
	 * when it was sampled before.
	 */
	static long signedLeadTicks(long initialTsTicks, long firstRawTicks, int ticksMaxValue) {
		long half = ticksMaxValue / 2;
		long low = Math.floorMod(initialTsTicks, (long) ticksMaxValue);
		return Math.floorMod(firstRawTicks - low + half, (long) ticksMaxValue) - half;
	}
}
