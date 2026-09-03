package com.shimmerresearch.verisense.payloaddesign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;
import com.shimmerresearch.verisense.VerisenseDevice;
import com.shimmerresearch.verisense.payloaddesign.DataBlockDetails.DATABLOCK_SENSOR_ID;
import com.shimmerresearch.verisense.sensors.SensorMLX90632;
import com.shimmerresearch.verisense.sensors.SensorVD6283;

/**
 * Unit tests for the DEV-979 slow-sensor timing refinement. These drive the REAL
 * package-private methods on {@link PayloadContentsDetailsV8orAbove} - one call
 * per synthetic payload, in sequence, with data blocks carrying end TICKS the
 * way the metadata parse leaves them - and judge the resulting CSV split
 * decisions through the real
 * {@link UtilCsvSplitting#isDataBlockContinuous(SENSORS, DataSegmentDetails, DataBlockDetails)}.
 * No binary test files, no hardware data and no reflection.
 * <p>
 * The bug: the VD6283 is NOT duty-cycled. The firmware samples it on a plain
 * repeated timer at one of {@code 0.5, 1, 2, 5, 10, 20} Hz
 * (hal_slowSensorSampler.c {@code slowSensorRateMs[]}), defaulting to 1 Hz, and
 * buffers 10 samples per block. That rate index lives in operational-config byte
 * 75 and is NOT stored in the payload, so the parser fell back to the
 * exposure-derived value - which only bounds the rate from ABOVE (10 Hz at the
 * default 100 ms exposure). Each 10-sample block was therefore laid out over
 * 0.9 s of the 10 s it really spans, and the 9.1 s remainder looked like a gap:
 * 129 one-block CSVs.
 * <p>
 * End-to-end coverage on the real recording is ASM_PC_00005_VerisenseFileParserPC
 * Test_066; the DEV-927 skin-temp equivalent is Test_065.
 */
public class API_00009_VerisenseSlowSensorGapWindow {

	private static final int LIGHT_SAMPLES_PER_BLOCK = SensorVD6283.NUM_SAMPLES_PER_BLOCK;
	private static final int SKIN_TEMP_SAMPLES_PER_BLOCK = SensorMLX90632.NUM_SAMPLES_PER_BLOCK;

	private static final double TICKS_PER_SECOND = AsmBinaryFileConstants.TICKS_PER_SECOND;
	private static final long TICKS_PER_MINUTE = (long) AsmBinaryFileConstants.TICKS_PER_MINUTE;

	/** The DEV-979 recording: 1 Hz light, so a 10-sample block every 10 s. */
	private static final double LIGHT_1HZ_BLOCK_SPACING_S = 10;
	/** Skin temp refresh code 6 = 32 Hz refresh -> 16 Hz medical output (DEV-927). */
	private static final int SKIN_TEMP_CONFIG_32HZ_REFRESH = 6<<1;

	@Before
	public void clearSplittingState() {
		UtilCsvSplitting.clearMapOfSamplingRateLimitsPerSensor();
	}

	private VerisenseDevice setupGen2Device(int skinTempConfigByte) {
		VerisenseDevice device = new VerisenseDevice(COMMUNICATION_TYPE.SD);

		byte[] configBytes = new byte[32];
		configBytes[0] = (byte) 0x10; // extended-config flag
		configBytes[2] = 2;  // FW major
		configBytes[4] = 9;  // FW internal LSB (v2.00.009)
		configBytes[6] = (byte) 0xFF; // reset reason
		configBytes[11] = HW_ID.VERISENSE_PULSE_PLUS; // SR68
		configBytes[12] = 9;  // SR68-9 (second generation)
		configBytes[25] = (byte) (0x02 | (1<<3) | (1<<4)); // GEN_CFG_3: LED + VD6283 + MLX90632
		configBytes[28] = (byte) skinTempConfigByte; // SKIN_TEMP_CONFIG
		device.configBytesParse(configBytes, COMMUNICATION_TYPE.SD);

		device.getOrCreateListOfSensorClassKeysForDataBlockId(DATABLOCK_SENSOR_ID.LIGHT);
		device.getOrCreateListOfSensorClassKeysForDataBlockId(DATABLOCK_SENSOR_ID.SKIN_TEMP);
		assertTrue("this fixture must exercise the v11+ (uC ticks) path", device.isPayloadDesignV11orAbove());
		return device;
	}

	private VerisenseDevice setupGen2Device() {
		return setupGen2Device(0);
	}

	private static int samplesPerBlock(DATABLOCK_SENSOR_ID slowSensorId) {
		return slowSensorId==DATABLOCK_SENSOR_ID.LIGHT? LIGHT_SAMPLES_PER_BLOCK:SKIN_TEMP_SAMPLES_PER_BLOCK;
	}

	private static SENSORS sensorClassKeyOf(DATABLOCK_SENSOR_ID slowSensorId) {
		return slowSensorId==DATABLOCK_SENSOR_ID.LIGHT? SENSORS.VD6283:SENSORS.MLX90632;
	}

	/** A block as the metadata parse leaves it: sized, timed with the header estimate, end TICKS set. */
	private DataBlockDetails newBlock(VerisenseDevice device, DATABLOCK_SENSOR_ID slowSensorId, long endTicks) {
		int bytesPerSample = slowSensorId==DATABLOCK_SENSOR_ID.LIGHT? SensorVD6283.BYTES_PER_SAMPLE:SensorMLX90632.BYTES_PER_SAMPLE;
		DataBlockDetails dataBlockDetails = new DataBlockDetails(slowSensorId, 0, 0,
				device.getOrCreateListOfSensorClassKeysForDataBlockId(slowSensorId), 0, 0);
		dataBlockDetails.setMetadata(samplesPerBlock(slowSensorId)*bytesPerSample, bytesPerSample,
				device.getSamplingRateForSensor(sensorClassKeyOf(slowSensorId)));
		// v11+ stores microcontroller-clock ticks per block; the sub-minute counter
		// is what the refinement differences.
		dataBlockDetails.getTimeDetailsUcClock().setEndTimeTicks(endTicks%TICKS_PER_MINUTE);
		// The absolute RWC ms the continuity check actually judges on. Kept in step
		// with the ticks so a boundary is judged on the same spacing that was measured.
		dataBlockDetails.getTimeDetailsRwc().setEndTimeMs(endTicks/TICKS_PER_SECOND*1000);
		return dataBlockDetails;
	}

	private static long ticks(double seconds) {
		return (long) Math.round(seconds*TICKS_PER_SECOND);
	}

	/**
	 * Run one payload through the real refinement: build a
	 * PayloadContentsDetailsV8orAbove, give it the blocks, and call the method the
	 * parse flow calls.
	 * 
	 * @return the payload's blocks, as the refinement left them
	 */
	private DataBlockDetails[] refinePayload(VerisenseDevice device, DATABLOCK_SENSOR_ID slowSensorId, DataBlockDetails... payloadBlocks) {
		PayloadContentsDetailsV8orAbove payloadContentsDetails = new PayloadContentsDetailsV8orAbove(device);
		payloadContentsDetails.listOfDataBlocksInOrder.addAll(Arrays.asList(payloadBlocks));
		payloadContentsDetails.refineSlowSensorSamplingRateFromBlockTicks(slowSensorId, samplesPerBlock(slowSensorId));
		return payloadBlocks;
	}

	/** One payload holding exactly one block of the sensor, at the given end ticks. */
	private DataBlockDetails refineOneBlockPayload(VerisenseDevice device, DATABLOCK_SENSOR_ID slowSensorId, long endTicks) {
		return refinePayload(device, slowSensorId, newBlock(device, slowSensorId, endTicks))[0];
	}

	private String continuityResult(DATABLOCK_SENSOR_ID slowSensorId, DataSegmentDetails previousSegment, DataBlockDetails next) {
		return UtilCsvSplitting.isDataBlockContinuous(sensorClassKeyOf(slowSensorId), previousSegment, next);
	}

	private DataSegmentDetails dataSegmentOf(DataBlockDetails... dataBlockDetails) {
		DataSegmentDetails dataSegmentDetails = new DataSegmentDetails();
		for (DataBlockDetails block : dataBlockDetails) {
			dataSegmentDetails.addDataBlock(block);
		}
		return dataSegmentDetails;
	}

	/**
	 * Walk a stream of one-block payloads through the real refinement, asserting
	 * each boundary's split decision.
	 * 
	 * @param spacingsS the spacing from each block to the next, in seconds
	 */
	private DataSegmentDetails walkStream(VerisenseDevice device, DATABLOCK_SENSOR_ID slowSensorId, double firstBlockEndS, double... spacingsS) {
		double endS = firstBlockEndS;
		DataBlockDetails previous = refineOneBlockPayload(device, slowSensorId, ticks(endS));
		DataSegmentDetails dataSegmentDetails = dataSegmentOf(previous);
		for (int i = 0; i < spacingsS.length; i++) {
			endS += spacingsS[i];
			DataBlockDetails next = refineOneBlockPayload(device, slowSensorId, ticks(endS));
			assertEquals("boundary " + i + " (spacing " + spacingsS[i] + " s) must be continuous",
					"", continuityResult(slowSensorId, dataSegmentDetails, next));
			dataSegmentDetails.addDataBlock(next);
		}
		return dataSegmentDetails;
	}

	private static double[] uniformSpacings(int count, double spacingS) {
		double[] spacingsS = new double[count];
		Arrays.fill(spacingsS, spacingS);
		return spacingsS;
	}

	// ---------------------------------------------------------------- VD6283

	/**
	 * The reported symptom, in the shape the firmware actually produces: 1 Hz
	 * light, a 10-sample block every 10 s. No boundary may split, so the whole
	 * stream lands in one CSV.
	 */
	@Test
	public void test001_lightAt1HzDoesNotSplit() {
		VerisenseDevice device = setupGen2Device();
		assertEquals("the header only yields the exposure-derived upper bound",
				10.0, device.getSamplingRateForSensor(SENSORS.VD6283), 1e-9);

		DataSegmentDetails dataSegmentDetails = walkStream(device, DATABLOCK_SENSOR_ID.LIGHT, 100, uniformSpacings(40, LIGHT_1HZ_BLOCK_SPACING_S));
		assertEquals(41, dataSegmentDetails.getDataBlockCount());
	}

	/**
	 * The refinement recovers the true 1 s period from the block spacing and
	 * applies it, so the blocks become CONTIGUOUS: each one's 10 samples span the
	 * 9 s from its first to its last, not the 0.9 s the exposure-derived estimate
	 * implied, and the next block starts one period after the previous one ends.
	 */
	@Test
	public void test002_refinedPeriodIsAppliedAndMakesBlocksContiguous() {
		VerisenseDevice device = setupGen2Device();

		DataBlockDetails first = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(100));
		// The first block of a CSV set has nothing to measure against, so it keeps
		// the header estimate - the documented residual.
		assertEquals(10.0, first.getSamplingRate(), 1e-9);
		assertEquals(0.1, first.getTimestampDiffInS(), 1e-9);

		DataBlockDetails second = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(100+LIGHT_1HZ_BLOCK_SPACING_S));
		assertEquals("10 samples over 10 s = 1 Hz", 1.0, second.getSamplingRate(), 1e-6);
		assertEquals(1.0, second.getTimestampDiffInS(), 1e-6);

		// Timed from the end tick, the block's samples now span 9 x 1 s...
		second.setUcClockEndTimeMinutesAndCalculateTimings(0);
		double blockSpanMs = second.getTimeDetailsUcClock().getEndTimeMs()-second.getTimeDetailsUcClock().getStartTimeMs();
		assertEquals(9000, blockSpanMs, 1);
	}

	/**
	 * A light configuration where the exposure-derived estimate happens to equal
	 * the truth (10 Hz: 10 samples 100 ms apart, a block every second) is refined
	 * to the same value, so nothing about it changes.
	 */
	@Test
	public void test003_lightWhereTheEstimateEqualsTheTruthIsUnchanged() {
		VerisenseDevice device = setupGen2Device();

		walkStream(device, DATABLOCK_SENSOR_ID.LIGHT, 100, uniformSpacings(20, 1.0));

		DataBlockDetails latest = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(121));
		assertEquals(10.0, latest.getSamplingRate(), 1e-6);
	}

	/**
	 * The ticks are a SUB-MINUTE counter, so a boundary that crosses a minute
	 * shows as a NEGATIVE delta and has to be re-based by one minute rather than
	 * wrapped at 2^24. Straddle the boundary and assert the period still comes out
	 * at 1 s.
	 */
	@Test
	public void test004_tickDeltaIsRebasedAcrossAMinuteBoundary() {
		VerisenseDevice device = setupGen2Device();

		// 55 s -> 65 s: the second block's sub-minute tick value is SMALLER
		DataBlockDetails first = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(55));
		DataBlockDetails second = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(65));
		assertTrue("the fixture must actually wrap",
				second.getTimeDetailsUcClock().getEndTimeTicks()<first.getTimeDetailsUcClock().getEndTimeTicks());

		assertEquals("a minute-crossing boundary must still measure 1 Hz", 1.0, second.getSamplingRate(), 1e-6);
	}

	/** A genuinely dropped block doubles the spacing and must still split. */
	@Test
	public void test005_droppedLightBlockSplitsOnceTheHistoryExists() {
		VerisenseDevice device = setupGen2Device();

		DataSegmentDetails dataSegmentDetails = walkStream(device, DATABLOCK_SENSOR_ID.LIGHT, 100, uniformSpacings(20, LIGHT_1HZ_BLOCK_SPACING_S));

		double afterDropoutS = 100+(20*LIGHT_1HZ_BLOCK_SPACING_S)+(LIGHT_1HZ_BLOCK_SPACING_S*2);
		DataBlockDetails afterDropout = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(afterDropoutS));

		assertFalse("a dropped light block must split the CSV",
				continuityResult(DATABLOCK_SENSOR_ID.LIGHT, dataSegmentDetails, afterDropout).isEmpty());
	}

	/**
	 * On the FIRST boundary of a CSV set the window is a-priori, bounded by the
	 * firmware's rate table, so the slowest rate the hardware offers (0.5 Hz, a
	 * 10-sample block every 20 s) must not split.
	 */
	@Test
	public void test006_firstBoundaryAtTheSlowestFirmwareRateDoesNotSplit() {
		VerisenseDevice device = setupGen2Device();

		double slowestSpacingS = LIGHT_SAMPLES_PER_BLOCK/SensorVD6283.MIN_SAMPLE_RATE_HZ; // 20 s
		DataBlockDetails first = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(10));
		DataSegmentDetails dataSegmentDetails = dataSegmentOf(first);
		DataBlockDetails second = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(10+slowestSpacingS));

		assertEquals("0.5 Hz is a legitimate firmware rate and must not split",
				"", continuityResult(DATABLOCK_SENSOR_ID.LIGHT, dataSegmentDetails, second));
	}

	/**
	 * ...but a gap beyond anything the hardware could produce must still split
	 * there. A 10-sample block 60 s after the previous one is 0.167 Hz, below the
	 * slowest firmware rate even after the standard ratio.
	 */
	@Test
	public void test007_firstBoundaryWithA60SecondGapSplits() {
		VerisenseDevice device = setupGen2Device();

		DataBlockDetails first = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(10));
		DataSegmentDetails dataSegmentDetails = dataSegmentOf(first);
		DataBlockDetails second = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(70));

		assertFalse("a 60 s gap must split even on the first boundary of a set",
				continuityResult(DATABLOCK_SENSOR_ID.LIGHT, dataSegmentDetails, second).isEmpty());
	}

	/**
	 * A failed I2C read makes ONE block take an extra sample period to fill
	 * without losing a sample slot (hal_slowSensorSampler.c only increments the
	 * count on a successful read), so that boundary measures 11 s. It must stay
	 * continuous and - because the median is applied, not the raw delta - must not
	 * stretch that block's or its neighbours' sample spacing.
	 */
	@Test
	public void test008_i2cDroppedSampleStaysContinuousAndDoesNotStretchTheBlocks() {
		VerisenseDevice device = setupGen2Device();

		double[] spacingsS = uniformSpacings(20, LIGHT_1HZ_BLOCK_SPACING_S);
		spacingsS[10] = LIGHT_1HZ_BLOCK_SPACING_S+1; // one sample dropped
		DataSegmentDetails dataSegmentDetails = walkStream(device, DATABLOCK_SENSOR_ID.LIGHT, 100, spacingsS);

		for (DataBlockDetails dataBlockDetails : dataSegmentDetails.getListOfDataBlocks()) {
			if(dataBlockDetails.getSamplingRate()!=10.0) { // skip the estimate-timed first block
				assertEquals("no block may be stretched by the dropped sample",
						1.0, dataBlockDetails.getSamplingRate(), 1e-6);
			}
		}
	}

	/**
	 * A gap of 60-70 s ALIASES through the sub-minute tick counter into an
	 * ordinary-looking period. Detection is unaffected (the continuity check works
	 * on absolute RWC ms), but the aliased value must not be recorded or applied -
	 * at the start of a CSV set it would be the whole history and would re-time
	 * every block.
	 */
	@Test
	public void test009_aliasedGapIsNeitherRecordedNorApplied() {
		VerisenseDevice device = setupGen2Device();

		// 65 s spacing: the tick delta aliases to 5 s -> 0.5 s per sample -> 2 Hz,
		// which is a legitimate firmware rate, so only the RWC ms reveals the gap.
		DataBlockDetails first = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(10));
		DataSegmentDetails dataSegmentDetails = dataSegmentOf(first);
		DataBlockDetails second = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(75));
		assertFalse("the real 65 s gap must still be reported",
				continuityResult(DATABLOCK_SENSOR_ID.LIGHT, dataSegmentDetails, second).isEmpty());

		// An implausible period is refused outright
		assertFalse(UtilCsvSplitting.isSlowSensorPeriodPlausible(device, DATABLOCK_SENSOR_ID.LIGHT, 10.0));
		assertFalse(UtilCsvSplitting.isSlowSensorPeriodPlausible(device, DATABLOCK_SENSOR_ID.LIGHT, 0.001));
		assertFalse(UtilCsvSplitting.isSlowSensorPeriodPlausible(device, DATABLOCK_SENSOR_ID.LIGHT, Double.NaN));
		assertFalse(UtilCsvSplitting.isSlowSensorPeriodPlausible(device, DATABLOCK_SENSOR_ID.LIGHT, -1.0));
		assertTrue(UtilCsvSplitting.isSlowSensorPeriodPlausible(device, DATABLOCK_SENSOR_ID.LIGHT, 1.0));
		assertTrue(UtilCsvSplitting.isSlowSensorPeriodPlausible(device, DATABLOCK_SENSOR_ID.LIGHT, 2.0));

		// ...and an implausible median is never applied, so the block keeps the estimate
		UtilCsvSplitting.clearMapOfSamplingRateLimitsPerSensor();
		UtilCsvSplitting.recordAndGetSlowSensorPeriodS(device, DATABLOCK_SENSOR_ID.LIGHT, 10.0);
		assertEquals(0, UtilCsvSplitting.getSlowSensorObservationCount(DATABLOCK_SENSOR_ID.LIGHT));
	}

	/**
	 * A block a midday/midnight transition cut in two must be measured as the ONE
	 * whole block it is. The halves are a fraction of a second apart and carry
	 * reduced sample counts, so measuring them would fabricate both a far too fast
	 * and a far too slow observation - and the continuity check never sees the
	 * halves either, it recombines them.
	 */
	@Test
	public void test010_middayMidnightSplitPartsAreMeasuredAsOneWholeBlock() {
		VerisenseDevice device = setupGen2Device();

		refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(10));

		// The next block straddles the transition and is cut in two
		DataBlockDetails firstPart = newBlock(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(20));
		DataBlockDetails secondPart = firstPart.deepClone();
		int splitAtSampleIndex = LIGHT_SAMPLES_PER_BLOCK/2;
		firstPart.splitAndEndBeforeSampleIndex(splitAtSampleIndex, firstPart.getEndTimeRwcMs()-500,
				firstPart.getTimeDetailsUcClock().getEndTimeMs());
		secondPart.splitAndStartAtSampleIndex(splitAtSampleIndex, secondPart.getEndTimeRwcMs()-400,
				secondPart.getTimeDetailsUcClock().getEndTimeMs());
		// The first part's end tick moves with its end time; the SECOND part keeps
		// the original block's end tick, which is what the refinement measures on.
		firstPart.getTimeDetailsUcClock().setEndTimeTicks(ticks(19.5));
		assertTrue(firstPart.isFirstPartOfSplitDataBlock() && secondPart.isSecondPartOfSplitDataBlock());
		assertEquals(splitAtSampleIndex, firstPart.getSampleCount());
		assertEquals(LIGHT_SAMPLES_PER_BLOCK-splitAtSampleIndex, secondPart.getSampleCount());
		assertEquals(ticks(20), secondPart.getTimeDetailsUcClock().getEndTimeTicks());

		refinePayload(device, DATABLOCK_SENSOR_ID.LIGHT, firstPart, secondPart);

		// One whole 10-sample block 10 s after the previous one = 1 Hz. Had the
		// halves been measured separately the 0.5 s gap between them would have
		// produced a wildly fast observation and a 5-sample slow one instead.
		assertEquals(1, UtilCsvSplitting.getSlowSensorObservationCount(DATABLOCK_SENSOR_ID.LIGHT));
		assertEquals(1.0, secondPart.getSamplingRate(), 1e-6);
		assertEquals("both halves are re-timed", 1.0, firstPart.getSamplingRate(), 1e-6);
	}

	/**
	 * A CSV set that OPENS on an anomalous boundary must recover: nothing is
	 * excluded at learn time, because the history is empty after every clear and a
	 * learn-time plausibility test against the history would let the first
	 * boundary define what counts as plausible.
	 */
	@Test
	public void test011_recoversFromAnomalousFirstObservation() {
		VerisenseDevice device = setupGen2Device();

		double[] spacingsS = uniformSpacings(20, LIGHT_1HZ_BLOCK_SPACING_S);
		spacingsS[0] = LIGHT_1HZ_BLOCK_SPACING_S*2; // opens on a dropped block
		walkStream(device, DATABLOCK_SENSOR_ID.LIGHT, 100, spacingsS);

		DataBlockDetails latest = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(100+(21*LIGHT_1HZ_BLOCK_SPACING_S)));
		assertEquals("the estimate must recover onto the healthy period", 1.0, latest.getSamplingRate(), 1e-6);
	}

	/**
	 * An overlapping (impossibly fast) boundary must be reported and must not
	 * define the fast side of the window, or it would widen it past itself and
	 * stop being reported.
	 */
	@Test
	public void test012_overlappingBoundarySplitsAndDoesNotDefineTheFastSide() {
		VerisenseDevice device = setupGen2Device();

		DataSegmentDetails dataSegmentDetails = walkStream(device, DATABLOCK_SENSOR_ID.LIGHT, 100, uniformSpacings(20, LIGHT_1HZ_BLOCK_SPACING_S));

		// A forward clock correction shrinks the spacing to 100 ms -> 100 Hz apparent
		DataBlockDetails overlapping = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(100+(20*LIGHT_1HZ_BLOCK_SPACING_S)+0.1));
		assertFalse("an overlapping boundary must split",
				continuityResult(DATABLOCK_SENSOR_ID.LIGHT, dataSegmentDetails, overlapping).isEmpty());
		assertTrue("the artefact must not define the fast side",
				UtilCsvSplitting.isSamplingRateOutsideOfLimits(UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.get(SENSORS.VD6283), 100.0));
	}

	/** Writing a CSV set out clears every piece of slow-sensor state. */
	@Test
	public void test013_clearResetsTheStateSoTheNextSetStartsFresh() {
		VerisenseDevice device = setupGen2Device();

		walkStream(device, DATABLOCK_SENSOR_ID.LIGHT, 100, uniformSpacings(5, LIGHT_1HZ_BLOCK_SPACING_S));
		assertTrue(UtilCsvSplitting.getSlowSensorObservationCount(DATABLOCK_SENSOR_ID.LIGHT)>0);

		UtilCsvSplitting.clearMapOfSamplingRateLimitsPerSensor();

		assertEquals(0, UtilCsvSplitting.getSlowSensorObservationCount(DATABLOCK_SENSOR_ID.LIGHT));
		// The first block of the new set is timed with the header estimate again -
		// proof that no stale end tick or period survived.
		DataBlockDetails afterClear = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(1000));
		assertEquals(10.0, afterClear.getSamplingRate(), 1e-9);
	}

	/** Nothing changes for fast sensors. */
	@Test
	public void test014_fastSensorLimitsAreUntouched() {
		VerisenseDevice device = setupGen2Device();

		double[] fastSensorLimits = UtilCsvSplitting.calculateSamplingRateLimits(960);
		UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.put(SENSORS.LSM6DSV, fastSensorLimits);

		walkStream(device, DATABLOCK_SENSOR_ID.LIGHT, 100, uniformSpacings(10, LIGHT_1HZ_BLOCK_SPACING_S));

		assertTrue("the fast sensor's band must be the same array, unmodified",
				fastSensorLimits==UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.get(SENSORS.LSM6DSV));
		assertEquals(960*UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.LOWER, fastSensorLimits[0], 1e-9);
		assertNull("only the sensors of this data block are touched",
				UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.get(SENSORS.MLX90632));
	}

	// ------------------------------------------------------------- MLX90632

	/**
	 * The cross-payload measurement rests on the block end time being a
	 * SUB-MINUTE tick counter, so it is only sound while the sensor's largest
	 * legitimate block span is under a minute. A 10-sample light block spans at
	 * most 20 s; a 16-sample skin-temp block at its slowest output spans over a
	 * minute and must be refused.
	 */
	@Test
	public void test015_crossPayloadMeasurementIsRefusedWhenTheTickDeltaIsAmbiguous() {
		assertTrue("a 10-sample light block spans at most 20 s",
				UtilCsvSplitting.isSlowSensorSpanUnambiguousAcrossPayloads(DATABLOCK_SENSOR_ID.LIGHT, LIGHT_SAMPLES_PER_BLOCK));
		assertFalse("a 16-sample skin-temp block can span more than a minute",
				UtilCsvSplitting.isSlowSensorSpanUnambiguousAcrossPayloads(DATABLOCK_SENSOR_ID.SKIN_TEMP, SKIN_TEMP_SAMPLES_PER_BLOCK));
		assertFalse("a fast sensor is not a slow sensor",
				UtilCsvSplitting.isSlowSensorSpanUnambiguousAcrossPayloads(DATABLOCK_SENSOR_ID.LSM6DSV, 100));
	}

	/**
	 * Because the MLX90632 fails that gate it takes the pre-DEV-979 per-payload
	 * path, so a payload holding a SINGLE temp block must be left completely
	 * alone - no cross-payload measurement, no rate change, no window put. This is
	 * what makes skin-temp byte-identity hold by construction (the DEV-927
	 * reference CSVs cannot be reached from here).
	 */
	@Test
	public void test016_skinTempSingleBlockPayloadsAreLeftAlone() {
		VerisenseDevice device = setupGen2Device(SKIN_TEMP_CONFIG_32HZ_REFRESH);
		assertEquals("DEV-927 configuration is 16 Hz output", 16.0, device.getSamplingRateForSensor(SENSORS.MLX90632), 1e-9);

		DataBlockDetails first = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.SKIN_TEMP, ticks(1));
		DataBlockDetails second = refineOneBlockPayload(device, DATABLOCK_SENSOR_ID.SKIN_TEMP, ticks(2));

		assertEquals("the header-derived rate must be left in place", 16.0, first.getSamplingRate(), 1e-9);
		assertEquals(16.0, second.getSamplingRate(), 1e-9);
		assertEquals("no cross-payload history may be accumulated",
				0, UtilCsvSplitting.getSlowSensorObservationCount(DATABLOCK_SENSOR_ID.SKIN_TEMP));
		assertNull("no window may be seeded from a single-block payload",
				UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.get(SENSORS.MLX90632));
		assertTrue(UtilCsvSplitting.SLOW_SENSOR_LAST_BLOCK_END_TICKS.isEmpty());
	}

	/**
	 * The DEV-927 shapes the reviewer measured against master, pinned here so the
	 * per-payload path cannot drift: 16 samples per block, 16 Hz output, 1000 ms
	 * nominal spacing, a +12.5% slip then a catch-up. The expected values are
	 * MASTER's - upper-middle median over this payload's periods only.
	 */
	@Test
	public void test017_skinTempPerPayloadPathMatchesMasterForTheDev927Shapes() {
		VerisenseDevice device = setupGen2Device(SKIN_TEMP_CONFIG_32HZ_REFRESH);

		// Two blocks in the payload: one boundary, the slip (1.125 s / 16 samples)
		DataBlockDetails[] twoBlockPayload = refinePayload(device, DATABLOCK_SENSOR_ID.SKIN_TEMP,
				newBlock(device, DATABLOCK_SENSOR_ID.SKIN_TEMP, ticks(10)),
				newBlock(device, DATABLOCK_SENSOR_ID.SKIN_TEMP, ticks(11.125)));
		double expectedTwoBlockRate = 1.0/(1.125/SKIN_TEMP_SAMPLES_PER_BLOCK);
		assertEquals("master applies this payload's own median", expectedTwoBlockRate, twoBlockPayload[0].getSamplingRate(), 1e-6);
		assertEquals(expectedTwoBlockRate, twoBlockPayload[1].getSamplingRate(), 1e-6);
		assertEquals("still no cross-payload history", 0, UtilCsvSplitting.getSlowSensorObservationCount(DATABLOCK_SENSOR_ID.SKIN_TEMP));

		// Three blocks: two boundaries, the slip then the catch-up. Master's
		// size()/2 UPPER-middle median of {0.875/16, 1.125/16} is the LARGER period.
		UtilCsvSplitting.clearMapOfSamplingRateLimitsPerSensor();
		DataBlockDetails[] threeBlockPayload = refinePayload(device, DATABLOCK_SENSOR_ID.SKIN_TEMP,
				newBlock(device, DATABLOCK_SENSOR_ID.SKIN_TEMP, ticks(10)),
				newBlock(device, DATABLOCK_SENSOR_ID.SKIN_TEMP, ticks(11.125)),
				newBlock(device, DATABLOCK_SENSOR_ID.SKIN_TEMP, ticks(12)));
		double upperMiddlePeriodS = 1.125/SKIN_TEMP_SAMPLES_PER_BLOCK;
		assertEquals("master takes the upper-middle median, not the mean of the two",
				1.0/upperMiddlePeriodS, threeBlockPayload[0].getSamplingRate(), 1e-6);

		// Master's window: gap side achievedRate/1.5, fast side (1/minPeriod)*1.1
		double[] samplingRateLimits = UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.get(SENSORS.MLX90632);
		assertEquals((1.0/upperMiddlePeriodS)/UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO,
				samplingRateLimits[0], 1e-6);
		assertEquals((1.0/(0.875/SKIN_TEMP_SAMPLES_PER_BLOCK))*UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.UPPER,
				samplingRateLimits[1], 1e-6);
	}

	/** The median helper averages the two middle values for an even-sized input. */
	@Test
	public void test018_medianIsTheMeanOfTheTwoMiddleValues() {
		assertTrue(Double.isNaN(UtilCsvSplitting.calculateMedian(Arrays.<Double>asList())));
		assertEquals(2.0, UtilCsvSplitting.calculateMedian(Arrays.asList(1.0, 2.0, 5.0)), 1e-9);
		assertEquals(1.5, UtilCsvSplitting.calculateMedian(Arrays.asList(1.0, 2.0)), 1e-9);
		// The upper-middle median the legacy path uses would have returned 4.0 here
		assertEquals(1.5, UtilCsvSplitting.calculateMedian(Arrays.asList(1.0, 1.0, 2.0, 4.0)), 1e-9);
	}
}
