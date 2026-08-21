package com.shimmerresearch.verisense.payloaddesign;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;
import com.shimmerresearch.verisense.VerisenseDevice;
import com.shimmerresearch.verisense.payloaddesign.DataBlockDetails.DATABLOCK_SENSOR_ID;
import com.shimmerresearch.verisense.payloaddesign.UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER;

/**
 * Unit tests for the slow-sensor (VD6283 light / MLX90632 skin temp) CSV
 * gap-splitting window in {@link UtilCsvSplitting} - the accumulated-median
 * estimate that
 * {@code PayloadContentsDetailsV8orAbove.refineSlowSensorSamplingRateFromBlockTicks}
 * feeds on every payload.
 * <p>
 * The tests drive the window through per-sample periods, in seconds, exactly as
 * the payload parser measures them ({@code inter-block ticks / samples-per-block}),
 * so no binary test files or hardware recordings are needed. The boundary rate
 * the CSV splitter then judges is simply {@code 1/period} - see
 * {@link UtilCsvSplitting#isSamplingRateOutsideOfLimits(double[], DataBlockDetails, DataBlockDetails, SENSORS)},
 * which computes samples/second between two consecutive block end times.
 * <p>
 * End-to-end coverage against real recordings lives in
 * ASM_PC_00005_VerisenseFileParserPC (ASM_PC repository).
 */
public class API_00009_UtilCsvSplittingSlowSensorGapWindow {

	/** ~9.09 Hz - the achieved VD6283 cadence at the default exposure. */
	private static final double NOMINAL_PERIOD_S = 0.11;
	private static final double NOMINAL_RATE_HZ = 1.0/NOMINAL_PERIOD_S;
	/** Number of healthy payloads used to build up a history before the payload under test. */
	private static final int HEALTHY_PAYLOAD_COUNT = 20;
	/** Slow-sensor blocks per payload is 2-3 in the field, i.e. 1-2 inter-block gaps. */
	private static final int GAPS_PER_PAYLOAD = 2;

	private static final SENSORS SENSOR_UNDER_TEST = SENSORS.VD6283;

	private static final double DELTA = 1e-9;

	@Before
	public void resetStaticState() {
		// The limits map and the period history are process-wide statics, cleared by
		// the file parser at each CSV-set boundary - do the same between tests so
		// that they cannot leak into one another.
		UtilCsvSplitting.clearMapOfSamplingRateLimitsPerSensor();
	}

	// ---------------------------------------------------------------- helpers

	/** Feed one payload's worth of measurements in, as the parser does per payload. */
	private double feedPayload(double... perSamplePeriodsS) {
		List<Double> periodsS = new ArrayList<Double>();
		for(double periodS:perSamplePeriodsS) {
			periodsS.add(periodS);
		}
		return UtilCsvSplitting.refineSlowSensorSamplingRateLimits(SENSOR_UNDER_TEST, periodsS);
	}

	/** Build up a history of healthy payloads at the nominal cadence. */
	private void feedHealthyHistory() {
		for(int i=0;i<HEALTHY_PAYLOAD_COUNT;i++) {
			double[] periodsS = new double[GAPS_PER_PAYLOAD];
			Arrays.fill(periodsS, NOMINAL_PERIOD_S);
			feedPayload(periodsS);
		}
	}

	private double[] currentLimits() {
		return UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.get(SENSOR_UNDER_TEST);
	}

	/** The rate the CSV splitter derives for a block boundary of this per-sample period. */
	private static double boundaryRateHz(double perSamplePeriodS) {
		return 1.0/perSamplePeriodS;
	}

	// ------------------------------------------------- (a) even-count median

	@Test
	public void testMedianAveragesTheTwoMiddleValuesForAnEvenCount() {
		// Upper-median (the old get(size/2)) would return 3.0 here.
		assertEquals(2.5, UtilCsvSplitting.calculateMedian(Arrays.asList(1.0, 2.0, 3.0, 4.0)), DELTA);
		// Two elements: the average of both.
		assertEquals(0.15, UtilCsvSplitting.calculateMedian(Arrays.asList(0.1, 0.2)), DELTA);
		// Odd counts are unchanged.
		assertEquals(2.0, UtilCsvSplitting.calculateMedian(Arrays.asList(1.0, 2.0, 3.0)), DELTA);
		// Unsorted input is handled...
		assertEquals(2.5, UtilCsvSplitting.calculateMedian(Arrays.asList(4.0, 1.0, 3.0, 2.0)), DELTA);
		// ...without re-ordering the caller's list.
		List<Double> callersList = new ArrayList<Double>(Arrays.asList(4.0, 1.0, 3.0, 2.0));
		UtilCsvSplitting.calculateMedian(callersList);
		assertEquals(Arrays.asList(4.0, 1.0, 3.0, 2.0), callersList);
		// Nothing measured yet.
		assertTrue(Double.isNaN(UtilCsvSplitting.calculateMedian(new ArrayList<Double>())));
		assertTrue(Double.isNaN(UtilCsvSplitting.calculateMedian(null)));
	}

	@Test
	public void testAccumulatedMedianAveragesTheTwoMiddleValuesForAnEvenCount() {
		// Four measurements across two payloads -> the mean of the middle two.
		feedPayload(0.10, 0.12);
		double medianPeriodS = UtilCsvSplitting.accumulateSlowSensorPeriodsAndGetMedianPeriodS(SENSOR_UNDER_TEST, Arrays.asList(0.14, 0.16));
		assertEquals(0.13, medianPeriodS, DELTA);
	}

	// ------------------------------------------------------- window geometry

	@Test
	public void testBothLimitsAreDerivedFromTheSameMedian() {
		double[] limits = UtilCsvSplitting.calculateSlowSensorSamplingRateLimits(NOMINAL_RATE_HZ);
		assertEquals(NOMINAL_RATE_HZ/FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO, limits[0], DELTA);
		assertEquals(NOMINAL_RATE_HZ*FILE_GAP_TOLERANCE_MULTIPLIER.UPPER, limits[1], DELTA);

		// A single fast outlier in the history must not push the fast side out with
		// it - the old limits[1] used 1/minObservedPeriod, i.e. that extremum.
		feedHealthyHistory();
		double fastOutlierPeriodS = NOMINAL_PERIOD_S/2.0;
		feedPayload(NOMINAL_PERIOD_S, fastOutlierPeriodS);
		assertArrayEquals(UtilCsvSplitting.calculateSlowSensorSamplingRateLimits(NOMINAL_RATE_HZ), currentLimits(), 1e-6);
		assertTrue(UtilCsvSplitting.isSamplingRateOutsideOfLimits(currentLimits(), boundaryRateHz(fastOutlierPeriodS)));
	}

	// ------------------------------------------------------ (b) healthy jitter

	@Test
	public void testHealthyJitterStaysInsideTheWindow() {
		feedHealthyHistory();

		// +12.5% block spacing with no samples lost - observed on the DEV-927
		// MLX90632 validation recording. This payload contributes to the estimate.
		double jitteredPeriodS = NOMINAL_PERIOD_S*1.125;
		feedPayload(NOMINAL_PERIOD_S, jitteredPeriodS);

		double[] limits = currentLimits();
		assertFalse("+12.5% block spacing must still be judged continuous",
				UtilCsvSplitting.isSamplingRateOutsideOfLimits(limits, boundaryRateHz(jitteredPeriodS)));
		assertFalse("the nominal cadence must obviously be judged continuous",
				UtilCsvSplitting.isSamplingRateOutsideOfLimits(limits, boundaryRateHz(NOMINAL_PERIOD_S)));
		// A single +12.5% sample barely moves the median off the nominal cadence.
		assertEquals(NOMINAL_RATE_HZ, 1.0/UtilCsvSplitting.accumulateSlowSensorPeriodsAndGetMedianPeriodS(SENSOR_UNDER_TEST, null), 1e-6);
	}

	@Test
	public void testHealthyJitterStaysInsideTheWindowFromTheVeryFirstPayload() {
		// No history at all yet: the first payload with >= 2 blocks is all there is,
		// and the window must already be wide enough for the jitter it contains.
		double jitteredPeriodS = NOMINAL_PERIOD_S*1.125;
		feedPayload(NOMINAL_PERIOD_S, jitteredPeriodS);
		assertFalse(UtilCsvSplitting.isSamplingRateOutsideOfLimits(currentLimits(), boundaryRateHz(jitteredPeriodS)));
		assertFalse(UtilCsvSplitting.isSamplingRateOutsideOfLimits(currentLimits(), boundaryRateHz(NOMINAL_PERIOD_S)));
	}

	// ------------------------------------------------------- (c) dropped block

	@Test
	public void testDroppedBlockIsDetectedEvenThoughItsPayloadFedTheEstimate() {
		feedHealthyHistory();

		// A dropped block doubles the spacing. This payload is fed into the estimate
		// BEFORE the boundary it contains is judged - exactly the self-referential
		// case that a per-payload window could not detect.
		double droppedBlockPeriodS = NOMINAL_PERIOD_S*2.0;
		feedPayload(NOMINAL_PERIOD_S, droppedBlockPeriodS);

		assertTrue("a 2x inter-block gap must fall outside the window",
				UtilCsvSplitting.isSamplingRateOutsideOfLimits(currentLimits(), boundaryRateHz(droppedBlockPeriodS)));
		// The healthy boundary in the same payload must NOT be flagged instead.
		assertFalse(UtilCsvSplitting.isSamplingRateOutsideOfLimits(currentLimits(), boundaryRateHz(NOMINAL_PERIOD_S)));
		// One 2x outlier in ~40 measurements leaves the median where it was.
		assertEquals(NOMINAL_RATE_HZ, 1.0/UtilCsvSplitting.accumulateSlowSensorPeriodsAndGetMedianPeriodS(SENSOR_UNDER_TEST, null), 1e-6);
	}

	@Test
	public void testPerPayloadWindowWouldHaveAbsorbedTheDroppedBlock() {
		// Regression guard for the finding this change addresses: a window rebuilt
		// from ONLY the payload being judged (2 blocks -> 1 or 2 gaps) swallows the
		// dropped block into its own centre and reports nothing.
		double droppedBlockPeriodS = NOMINAL_PERIOD_S*2.0;
		double payloadLocalMedianPeriodS = UtilCsvSplitting.calculateMedian(Arrays.asList(NOMINAL_PERIOD_S, droppedBlockPeriodS));
		double[] payloadLocalLimits = UtilCsvSplitting.calculateSlowSensorSamplingRateLimits(1.0/payloadLocalMedianPeriodS);
		assertFalse("baseline: a payload-local window does NOT see the dropped block",
				UtilCsvSplitting.isSamplingRateOutsideOfLimits(payloadLocalLimits, boundaryRateHz(droppedBlockPeriodS)));

		// The accumulated window does.
		feedHealthyHistory();
		feedPayload(NOMINAL_PERIOD_S, droppedBlockPeriodS);
		assertTrue(UtilCsvSplitting.isSamplingRateOutsideOfLimits(currentLimits(), boundaryRateHz(droppedBlockPeriodS)));
	}

	// ------------------------------------------- (d) fallback seeding interplay

	@Test
	public void testMeasuredWindowWinsOverAFallbackSeededBand() {
		// populateExpectedPayloadTsDiffLimitMapIfNeeded seeds a configured-rate
		// +/-10% band for any sensor not yet measured - e.g. after a first payload
		// that carried fewer than two blocks of this sensor.
		double configuredRateHz = 10.0;
		UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.put(SENSOR_UNDER_TEST, UtilCsvSplitting.calculateSamplingRateLimits(configuredRateHz));

		// The header-derived 10 Hz is an estimate; the achieved cadence is ~9.09 Hz,
		// which the +/-10% band already calls a gap on every single boundary (this
		// fragmented a 25-min DEV-927 recording into 7 CSVs).
		assertTrue("baseline: the fallback band is too tight for the achieved cadence",
				UtilCsvSplitting.isSamplingRateOutsideOfLimits(currentLimits(), boundaryRateHz(NOMINAL_PERIOD_S*1.125)));

		// First measurement must take over immediately - no containsKey guard.
		feedPayload(NOMINAL_PERIOD_S, NOMINAL_PERIOD_S);
		assertArrayEquals(UtilCsvSplitting.calculateSlowSensorSamplingRateLimits(NOMINAL_RATE_HZ), currentLimits(), 1e-6);
		assertFalse(UtilCsvSplitting.isSamplingRateOutsideOfLimits(currentLimits(), boundaryRateHz(NOMINAL_PERIOD_S*1.125)));
	}

	@Test
	public void testFallbackSeedingDoesNotClobberAnExistingMeasuredWindow() {
		feedHealthyHistory();
		double[] measuredLimits = currentLimits().clone();

		// populateExpectedPayloadTsDiffLimitMapIfNeeded runs after the refinement on
		// every payload; its containsKey guard must leave the measurement alone.
		HashMap<DATABLOCK_SENSOR_ID, List<SENSORS>> mapOfSensorIdsPerDataBlock = new HashMap<DATABLOCK_SENSOR_ID, List<SENSORS>>();
		mapOfSensorIdsPerDataBlock.put(DATABLOCK_SENSOR_ID.LIGHT, Arrays.asList(SENSOR_UNDER_TEST));
		UtilCsvSplitting.populateExpectedPayloadTsDiffLimitMapIfNeeded(new VerisenseDevice(COMMUNICATION_TYPE.SD), mapOfSensorIdsPerDataBlock);

		assertArrayEquals(measuredLimits, currentLimits(), DELTA);
	}

	// -------------------------------------------------------- lifecycle contract

	@Test
	public void testClearingTheLimitsMapAlsoClearsTheAccumulatedPeriods() {
		feedHealthyHistory();
		assertEquals(NOMINAL_PERIOD_S, UtilCsvSplitting.accumulateSlowSensorPeriodsAndGetMedianPeriodS(SENSOR_UNDER_TEST, null), DELTA);

		// The file parser calls this on each CSV-set boundary: measurements from one
		// recording must not survive into the next.
		UtilCsvSplitting.clearMapOfSamplingRateLimitsPerSensor();

		assertTrue(UtilCsvSplitting.SLOW_SENSOR_OBSERVED_PERIODS_PER_SENSOR.isEmpty());
		assertTrue(Double.isNaN(UtilCsvSplitting.accumulateSlowSensorPeriodsAndGetMedianPeriodS(SENSOR_UNDER_TEST, null)));
		assertTrue(currentLimits()==null || currentLimits().length==0);
	}

	@Test
	public void testPeriodHistoryIsBounded() {
		int payloadsToOverflowHistory = (UtilCsvSplitting.SLOW_SENSOR_PERIOD_HISTORY_MAX/GAPS_PER_PAYLOAD)+10;
		for(int i=0;i<payloadsToOverflowHistory;i++) {
			feedPayload(NOMINAL_PERIOD_S, NOMINAL_PERIOD_S);
		}
		assertEquals(UtilCsvSplitting.SLOW_SENSOR_PERIOD_HISTORY_MAX,
				UtilCsvSplitting.SLOW_SENSOR_OBSERVED_PERIODS_PER_SENSOR.get(SENSOR_UNDER_TEST).size());
	}

	@Test
	public void testNonPositiveAndMissingMeasurementsAreIgnored() {
		// Nothing measured for this sensor yet -> NaN and no limits written.
		assertTrue(Double.isNaN(UtilCsvSplitting.refineSlowSensorSamplingRateLimits(SENSOR_UNDER_TEST, new ArrayList<Double>())));
		assertTrue(currentLimits()==null);

		// Zero/negative periods (a same-tick or out-of-order block pair) are dropped
		// rather than dragging the median to zero.
		assertEquals(NOMINAL_RATE_HZ, feedPayload(0.0, -1.0, NOMINAL_PERIOD_S), 1e-6);
	}
}
