package com.shimmerresearch.verisense.payloaddesign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
import com.shimmerresearch.verisense.sensors.SensorVD6283.VD6283_RATE;

/**
 * Tests for deriving the slow sensors' CSV gap-splitting window from the rate
 * the PAYLOAD HEADER states, rather than measuring it from the data.
 * <p>
 * The window is built by
 * {@link UtilCsvSplitting#seedSlowSensorGapWindow(VerisenseDevice, DATABLOCK_SENSOR_ID)}
 * and every split decision below is taken through the real
 * {@link UtilCsvSplitting#isDataBlockContinuous(SENSORS, DataSegmentDetails, DataBlockDetails)},
 * on synthetic payloads built the way the metadata parse leaves them. No binary
 * test files, no hardware data and no reflection.
 * <p>
 * Background. The VD6283 is sampled on a plain repeated timer at one of
 * {@code 0.5, 1, 2, 5, 10, 20} Hz (hal_slowSensorSampler.c
 * {@code slowSensorRateMs[]}), defaulting to 1 Hz, and 10 samples are buffered
 * per block. That rate lived only in operational-config byte 75 and was not
 * stored in the payload, so the parser fell back to the exposure-derived value,
 * which bounds the rate from ABOVE and is ten times too fast at the default. A
 * 10-sample block covering 10 s was laid out over 0.9 s and the remaining 9.1 s
 * read as a gap: 129 one-block CSVs from one recording (DEV-979). From FW
 * v2.02.000 the firmware stores the effective rate index in header byte 30 bits
 * 6:3 (DEV-1011) and this suite pins the parser side of that.
 * <p>
 * Several cases below are physical scenarios carried over from the earlier
 * measurement-based work so the coverage is not lost: a dropped block, a failed
 * I2C read costing one period, the VD6283's bimodal cadence, and an overlapping
 * boundary. They are re-pointed at the header-derived window. The reason a
 * measured window was abandoned is asserted directly by
 * {@link #test006_droppedBlockSplitsOnTheVeryFirstBoundary()}: a window built
 * from one or two observed gaps could not report a dropped block that was
 * itself one of those gaps (DEV-974 Bug A), whereas a configured-rate window
 * reports it from the first boundary onwards.
 */
public class API_00009_VerisenseSlowSensorGapWindow {

	private static final int LIGHT_SAMPLES_PER_BLOCK = SensorVD6283.NUM_SAMPLES_PER_BLOCK;
	private static final int SKIN_TEMP_SAMPLES_PER_BLOCK = SensorMLX90632.NUM_SAMPLES_PER_BLOCK;

	private static final double TICKS_PER_SECOND = AsmBinaryFileConstants.TICKS_PER_SECOND;
	private static final long TICKS_PER_MINUTE = (long) AsmBinaryFileConstants.TICKS_PER_MINUTE;

	/** Skin temp refresh code 6 = 32 Hz refresh -> 16 Hz medical output (DEV-927). */
	private static final int SKIN_TEMP_CONFIG_32HZ_REFRESH = 6<<1;
	/** Refresh code 0 = 0.5 Hz refresh -> 0.25 Hz medical output (slowest). */
	private static final int SKIN_TEMP_CONFIG_0HZ5_REFRESH = 0<<1;

	@Before
	public void clearSplittingState() {
		UtilCsvSplitting.clearMapOfSamplingRateLimitsPerSensor();
	}

	/**
	 * A gen-2 device as the payload-header parse leaves it. The config array is
	 * the 32-byte payload header, so writing a byte here is exactly what the
	 * firmware writing that byte would produce.
	 *
	 * @param lightGainAndDarkByte header byte 30: gain 2:0, rate index 6:3, dark 7
	 * @param skinTempConfigByte header byte 32: measType bit 0, refresh code 3:1
	 */
	private VerisenseDevice setupGen2Device(int lightGainAndDarkByte, int skinTempConfigByte) {
		return setupGen2Device(lightGainAndDarkByte, skinTempConfigByte, 2, 0, 9);
	}

	private VerisenseDevice setupGen2Device(int lightGainAndDarkByte, int skinTempConfigByte,
			int fwMajor, int fwMinor, int fwInternal) {
		VerisenseDevice device = new VerisenseDevice(COMMUNICATION_TYPE.SD);

		byte[] configBytes = new byte[32];
		configBytes[0] = (byte) 0x10; // extended-config flag
		configBytes[2] = (byte) fwMajor;
		configBytes[3] = (byte) fwMinor;
		configBytes[4] = (byte) (fwInternal & 0xFF);
		configBytes[5] = (byte) ((fwInternal>>8) & 0xFF);
		configBytes[6] = (byte) 0xFF; // reset reason
		configBytes[11] = HW_ID.VERISENSE_PULSE_PLUS; // SR68
		configBytes[12] = 9;  // SR68-9 (second generation)
		configBytes[25] = (byte) (0x02 | (1<<3) | (1<<4)); // GEN_CFG_3: LED + VD6283 + MLX90632
		configBytes[26] = (byte) lightGainAndDarkByte;
		configBytes[28] = (byte) skinTempConfigByte;
		device.configBytesParse(configBytes, COMMUNICATION_TYPE.SD);

		device.getOrCreateListOfSensorClassKeysForDataBlockId(DATABLOCK_SENSOR_ID.LIGHT);
		device.getOrCreateListOfSensorClassKeysForDataBlockId(DATABLOCK_SENSOR_ID.SKIN_TEMP);
		assertTrue("this fixture must exercise the v11+ (uC ticks) path", device.isPayloadDesignV11orAbove());
		return device;
	}

	/** Header byte 30 as the firmware writes it: gain 2.5x, given rate index, no dark channel. */
	private static int lightHeaderByte(int rateIndex) {
		return 0x02 | (rateIndex << SensorVD6283.LIGHT_RATE_INDEX_BIT_SHIFT);
	}

	/** A light device at the given configured rate index (0 = as earlier firmware left it). */
	private VerisenseDevice setupLightDevice(int rateIndex) {
		return setupGen2Device(lightHeaderByte(rateIndex), SKIN_TEMP_CONFIG_32HZ_REFRESH);
	}

	private static int samplesPerBlock(DATABLOCK_SENSOR_ID slowSensorId) {
		return slowSensorId==DATABLOCK_SENSOR_ID.LIGHT? LIGHT_SAMPLES_PER_BLOCK:SKIN_TEMP_SAMPLES_PER_BLOCK;
	}

	private static SENSORS sensorClassKeyOf(DATABLOCK_SENSOR_ID slowSensorId) {
		return slowSensorId==DATABLOCK_SENSOR_ID.LIGHT? SENSORS.VD6283:SENSORS.MLX90632;
	}

	/** A block as the metadata parse leaves it: sized, timed with the header rate, end ticks set. */
	private DataBlockDetails newBlock(VerisenseDevice device, DATABLOCK_SENSOR_ID slowSensorId, long endTicks) {
		int bytesPerSample = slowSensorId==DATABLOCK_SENSOR_ID.LIGHT? SensorVD6283.BYTES_PER_SAMPLE:SensorMLX90632.BYTES_PER_SAMPLE;
		DataBlockDetails dataBlockDetails = new DataBlockDetails(slowSensorId, 0, 0,
				device.getOrCreateListOfSensorClassKeysForDataBlockId(slowSensorId), 0, 0);
		dataBlockDetails.setMetadata(samplesPerBlock(slowSensorId)*bytesPerSample, bytesPerSample,
				device.getSamplingRateForSensor(sensorClassKeyOf(slowSensorId)));
		// v11+ stores a SUB-MINUTE microcontroller tick counter per block...
		dataBlockDetails.getTimeDetailsUcClock().setEndTimeTicks(endTicks%TICKS_PER_MINUTE);
		// ...while the continuity check works on absolute real-world-clock ms.
		dataBlockDetails.getTimeDetailsRwc().setEndTimeMs(endTicks/TICKS_PER_SECOND*1000);
		return dataBlockDetails;
	}

	private static long ticks(double seconds) {
		return (long) Math.round(seconds*TICKS_PER_SECOND);
	}

	/** Run the parse-flow step under test: derive the window from the header. */
	private void seedWindow(VerisenseDevice device, DATABLOCK_SENSOR_ID slowSensorId) {
		UtilCsvSplitting.seedSlowSensorGapWindow(device, slowSensorId);
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
	 * Walk a stream of one-block payloads through the real continuity check,
	 * asserting every boundary is judged continuous.
	 *
	 * @param spacingsS the spacing from each block to the next, in seconds
	 */
	private DataSegmentDetails walkStream(VerisenseDevice device, DATABLOCK_SENSOR_ID slowSensorId, double firstBlockEndS, double... spacingsS) {
		seedWindow(device, slowSensorId);
		double endS = firstBlockEndS;
		DataSegmentDetails dataSegmentDetails = dataSegmentOf(newBlock(device, slowSensorId, ticks(endS)));
		for (int i = 0; i < spacingsS.length; i++) {
			endS += spacingsS[i];
			DataBlockDetails next = newBlock(device, slowSensorId, ticks(endS));
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

	private double[] windowFor(DATABLOCK_SENSOR_ID slowSensorId) {
		return UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.get(sensorClassKeyOf(slowSensorId));
	}

	// ------------------------------------------------- header rate decoding

	/** Every index in the firmware rate table must decode to its rate. */
	@Test
	public void test001_everyConfiguredRateIndexIsDecoded() {
		// hal_slowSensorSampler.c: slowSensorRateMs[] = {0, 2000, 1000, 500, 200, 100, 50}.
		// The table is a STORED-DATA CONTRACT shared with the firmware across two
		// repositories with no build-time link between them, so pin the conversion
		// here: an index renumbered on either side silently re-times recordings.
		double[] firmwarePeriodMs = {2000, 1000, 500, 200, 100, 50};
		double[] expectedHz = {0.5, 1.0, 2.0, 5.0, 10.0, 20.0};
		for(int i=0;i<expectedHz.length;i++) {
			assertEquals("index " + (i+1) + " must be 1000/" + firmwarePeriodMs[i] + " Hz",
					1000.0/firmwarePeriodMs[i], expectedHz[i], 1e-9);
		}
		for (int rateIndex = 1; rateIndex <= 6; rateIndex++) {
			VerisenseDevice device = setupLightDevice(rateIndex);
			SensorVD6283 sensor = device.getSensorVD6283();
			assertTrue("index " + rateIndex + " must be recognised", sensor.isConfiguredRateKnown());
			// Index 6 (20 Hz) is exposure-limited by the fixture's 100 ms exposure,
			// so compare the enum rather than getRateFreq() here.
			assertEquals("index " + rateIndex, expectedHz[rateIndex-1], sensor.getRate().freqHz, 1e-9);
			assertEquals("gain must still parse from bits 2:0", 2.5, sensor.getGain(), 1e-9);
			assertFalse("dark channel must still parse from bit 7", sensor.isDarkChannelEnabled());
		}
	}

	/**
	 * Zero means "the rate was not recorded", not "index 0". Earlier firmware
	 * left those bits clear, and the firmware that does write them stores the
	 * EFFECTIVE index, which is never zero while light blocks exist.
	 */
	@Test
	public void test002_zeroRateFieldMeansNotStored() {
		VerisenseDevice device = setupLightDevice(0);
		SensorVD6283 sensor = device.getSensorVD6283();
		assertFalse(sensor.isConfiguredRateKnown());
		assertEquals(VD6283_RATE.NOT_STORED, sensor.getRate());
		assertEquals("falls back to the exposure bound", 10.0, sensor.getRateFreq(), 1e-9);
	}

	/**
	 * A reserved index (7..15) must read as unknown rather than being clamped to
	 * the nearest rate: guessing 20 Hz would mis-time every sample in the block.
	 * <p>
	 * It must also stay DISTINCT from "not stored". Both get the wide fallback
	 * window, but they mean opposite things - one is an old recording behaving as
	 * designed, the other is a payload holding a rate nobody can decode - and the
	 * diagnostic has to be able to say which.
	 */
	@Test
	public void test003_reservedRateIndicesReadAsReservedNotAsNotStored() {
		for (int rateIndex = 7; rateIndex <= 15; rateIndex++) {
			VerisenseDevice device = setupLightDevice(rateIndex);
			SensorVD6283 sensor = device.getSensorVD6283();
			assertEquals("reserved index " + rateIndex, VD6283_RATE.RESERVED_INDEX, sensor.getRate());
			assertTrue("reserved index " + rateIndex, sensor.isRateIndexReserved());
			assertFalse(sensor.isConfiguredRateKnown());
			assertEquals("the raw nibble must survive for the diagnostic",
					rateIndex, sensor.getRateIndexRaw());
		}
		// A clear field is the other state, and must not be confused with it.
		SensorVD6283 notStored = setupLightDevice(0).getSensorVD6283();
		assertEquals(VD6283_RATE.NOT_STORED, notStored.getRate());
		assertFalse(notStored.isRateIndexReserved());
		// ...and the field cannot spill into gain or the dark bit.
		VerisenseDevice device = setupLightDevice(15);
		assertEquals(2.5, device.getSensorVD6283().getGain(), 1e-9);
		assertFalse(device.getSensorVD6283().isDarkChannelEnabled());
	}

	/** All three fields of header byte 30 decode independently. */
	@Test
	public void test004_gainRateAndDarkChannelShareByte30Cleanly() {
		// gain index 3 (5.0x), rate index 2 (1 Hz), dark channel on = 0x93
		VerisenseDevice device = setupGen2Device(0x93, SKIN_TEMP_CONFIG_32HZ_REFRESH);
		SensorVD6283 sensor = device.getSensorVD6283();
		assertEquals(VD6283_RATE.RATE_1_HZ, sensor.getRate());
		assertEquals(5.0, sensor.getGain(), 1e-9);
		assertTrue(sensor.isDarkChannelEnabled());
	}

	/**
	 * The configured rate is clamped by the exposure bound, because the chip
	 * cannot measure faster than it integrates. 20 Hz configured with the
	 * fixture's 100 ms exposure is unattainable, so 10 Hz is used.
	 */
	@Test
	public void test005_configuredRateIsClampedByTheExposureBound() {
		VerisenseDevice device = setupLightDevice(6); // 20 Hz configured
		assertEquals(20.0, device.getSensorVD6283().getRate().freqHz, 1e-9);
		assertEquals("exposure-limited to 10 Hz", 10.0, device.getSensorVD6283().getRateFreq(), 1e-9);
		// A rate the exposure can sustain is returned unchanged.
		assertEquals(1.0, setupLightDevice(2).getSensorVD6283().getRateFreq(), 1e-9);
	}

	// ------------------------------------------------------- light: splitting

	/**
	 * DEV-974 Bug A, the reason a measured window was abandoned. A window built
	 * from the one or two inter-block gaps a payload carries could not report a
	 * dropped block that was itself one of those gaps. Derived from the
	 * configured rate, the very FIRST boundary of a CSV set reports it.
	 */
	@Test
	public void test006_droppedBlockSplitsOnTheVeryFirstBoundary() {
		VerisenseDevice device = setupLightDevice(2); // 1 Hz -> a block every 10 s
		seedWindow(device, DATABLOCK_SENSOR_ID.LIGHT);

		DataSegmentDetails dataSegmentDetails = dataSegmentOf(newBlock(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(10)));
		DataBlockDetails afterDropout = newBlock(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(30)); // 2x spacing

		assertFalse("a dropped block must split, with no history needed",
				continuityResult(DATABLOCK_SENSOR_ID.LIGHT, dataSegmentDetails, afterDropout).isEmpty());
	}

	/** The reported symptom: 1 Hz light, a 10-sample block every 10 s, one CSV. */
	@Test
	public void test007_lightAt1HzDoesNotSplit() {
		VerisenseDevice device = setupLightDevice(2);
		DataSegmentDetails dataSegmentDetails = walkStream(device, DATABLOCK_SENSOR_ID.LIGHT, 100, uniformSpacings(40, 10.0));
		assertEquals(41, dataSegmentDetails.getDataBlockCount());
	}

	/** The window is exactly the documented [cfg/1.5, cfg*1.1] band. */
	@Test
	public void test008_windowIsDerivedFromTheConfiguredRate() {
		VerisenseDevice device = setupLightDevice(2); // 1 Hz
		seedWindow(device, DATABLOCK_SENSOR_ID.LIGHT);

		double[] window = windowFor(DATABLOCK_SENSOR_ID.LIGHT);
		assertNotNull(window);
		assertEquals(1.0/UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO, window[0], 1e-9);
		assertEquals(1.0*UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.UPPER, window[1], 1e-9);
		// A dropped block halves the apparent rate and a healthy boundary is at 1.0
		assertTrue(UtilCsvSplitting.isSamplingRateOutsideOfLimits(window, 0.5));
		assertFalse(UtilCsvSplitting.isSamplingRateOutsideOfLimits(window, 1.0));
	}

	/**
	 * A failed I2C read does not consume a sample slot, so one block takes an
	 * extra period to fill and that boundary measures 11 s. It must stay
	 * continuous: no samples were lost.
	 */
	@Test
	public void test009_failedI2cReadStaysContinuous() {
		VerisenseDevice device = setupLightDevice(2);
		double[] spacingsS = uniformSpacings(20, 10.0);
		spacingsS[10] = 11.0;
		walkStream(device, DATABLOCK_SENSOR_ID.LIGHT, 100, spacingsS);
	}

	/**
	 * The VD6283's cadence is bimodal - exposure versus exposure plus dead time,
	 * about 100 and 110 ms at the default exposure - so at 10 Hz configured the
	 * boundaries alternate around 1.0 and 1.1 s. Both must stay continuous. This
	 * is the case that made a median-based fast edge split healthy files
	 * (DEV-974 Bug B); a fixed multiple of the configured rate cannot.
	 */
	@Test
	public void test010_bimodalCadenceAt10HzDoesNotSplit() {
		VerisenseDevice device = setupLightDevice(5); // 10 Hz
		double[] spacingsS = new double[20];
		for (int i = 0; i < spacingsS.length; i++) {
			spacingsS[i] = (i%2==0)? 1.0:1.1;
		}
		walkStream(device, DATABLOCK_SENSOR_ID.LIGHT, 100, spacingsS);
	}

	/** The slowest configurable rate, 0.5 Hz, gives a 20 s block spacing. */
	@Test
	public void test011_slowestConfiguredRateDoesNotSplit() {
		VerisenseDevice device = setupLightDevice(1); // 0.5 Hz
		walkStream(device, DATABLOCK_SENSOR_ID.LIGHT, 100, uniformSpacings(10, 20.0));
	}

	/** An overlapping block, or a backwards clock step, must be reported. */
	@Test
	public void test012_overlappingBoundarySplits() {
		VerisenseDevice device = setupLightDevice(2);
		seedWindow(device, DATABLOCK_SENSOR_ID.LIGHT);

		DataSegmentDetails dataSegmentDetails = dataSegmentOf(newBlock(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(100)));
		DataBlockDetails overlapping = newBlock(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(100.1));

		assertFalse("an overlapping boundary must split",
				continuityResult(DATABLOCK_SENSOR_ID.LIGHT, dataSegmentDetails, overlapping).isEmpty());
	}

	/**
	 * A boundary that crosses a minute is a non-event: the continuity check works
	 * on absolute real-world-clock milliseconds, not the sub-minute tick counter
	 * that the block also carries.
	 */
	@Test
	public void test013_minuteCrossingBoundaryIsANonEvent() {
		VerisenseDevice device = setupLightDevice(2);
		seedWindow(device, DATABLOCK_SENSOR_ID.LIGHT);

		DataBlockDetails first = newBlock(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(55));
		DataBlockDetails second = newBlock(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(65));
		assertTrue("the fixture must actually wrap the tick counter",
				second.getTimeDetailsUcClock().getEndTimeTicks()<first.getTimeDetailsUcClock().getEndTimeTicks());

		assertEquals("", continuityResult(DATABLOCK_SENSOR_ID.LIGHT, dataSegmentOf(first), second));
	}

	// -------------------------------------------- light: rate not in the header

	/**
	 * A recording from firmware that did not store the rate gets a wide window
	 * spanning the whole firmware rate table. It is deliberately generous: it
	 * keeps such a recording in ONE CSV instead of one per block, which is the
	 * DEV-979 symptom, at the cost of not detecting loss. Crucially it is
	 * stateless, so no amount of unhealthy data can move it.
	 */
	@Test
	public void test014_unknownRateGetsTheWideRateTableWindow() {
		VerisenseDevice device = setupLightDevice(0);
		seedWindow(device, DATABLOCK_SENSOR_ID.LIGHT);

		double[] window = windowFor(DATABLOCK_SENSOR_ID.LIGHT);
		assertNotNull(window);
		assertEquals(SensorVD6283.MIN_SAMPLE_RATE_HZ/UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO, window[0], 1e-9);
		assertEquals(SensorVD6283.MAX_SAMPLE_RATE_HZ*UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.UPPER, window[1], 1e-9);

		// The 1 Hz stream that used to fragment into one CSV per block does not.
		walkStream(device, DATABLOCK_SENSOR_ID.LIGHT, 100, uniformSpacings(10, 10.0));
	}

	/** The blind spot of that wide window, stated so it cannot be forgotten. */
	@Test
	public void test015_unknownRateCannotDetectModerateLoss() {
		VerisenseDevice device = setupLightDevice(0);
		seedWindow(device, DATABLOCK_SENSOR_ID.LIGHT);
		double[] window = windowFor(DATABLOCK_SENSOR_ID.LIGHT);

		// 10 samples over 30 s = 0.33 Hz is inside the window, so up to 20 s of
		// lost light data at 1 Hz goes unreported when the rate is unknown.
		assertFalse(UtilCsvSplitting.isSamplingRateOutsideOfLimits(window, LIGHT_SAMPLES_PER_BLOCK/30.0));
		// A 60 s spacing does still split.
		assertTrue(UtilCsvSplitting.isSamplingRateOutsideOfLimits(window, LIGHT_SAMPLES_PER_BLOCK/60.0));
	}

	// -------------------------------------------------------------- skin temp

	/**
	 * The MLX90632's refresh code has always been in the payload header, so its
	 * window comes from the configured output rate too, widened for the chip's
	 * documented conversion slip. At the DEV-927 16 Hz configuration a 16-sample
	 * block spans about 1 s.
	 */
	@Test
	public void test016_skinTempAt16HzUsesTheHeaderRate() {
		VerisenseDevice device = setupGen2Device(lightHeaderByte(2), SKIN_TEMP_CONFIG_32HZ_REFRESH);
		assertEquals("DEV-927 configuration is 16 Hz output", 16.0, device.getSamplingRateForSensor(SENSORS.MLX90632), 1e-9);
		seedWindow(device, DATABLOCK_SENSOR_ID.SKIN_TEMP);

		double slip = UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_CONVERSION_SLIP_TOLERANCE;
		double[] window = windowFor(DATABLOCK_SENSOR_ID.SKIN_TEMP);
		assertNotNull(window);
		assertEquals((16.0/slip)/UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO, window[0], 1e-9);
		assertEquals((16.0*slip)*UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.UPPER, window[1], 1e-9);

		// The observed slip-then-catch-up pair stays continuous...
		walkStream(device, DATABLOCK_SENSOR_ID.SKIN_TEMP, 10, 1.125, 0.875, 1.0, 1.0);
		// ...and a dropped block still splits.
		assertTrue(UtilCsvSplitting.isSamplingRateOutsideOfLimits(window, 8.0));
	}

	/**
	 * The skin-temp gap edge is tight, and this states exactly how tight so that
	 * nobody has to rediscover it.
	 * <p>
	 * A dropped block normally doubles the spacing. Landing on a boundary that had
	 * already caught up by the chip's documented 12.5% makes the spacing
	 * {@code 2 x 0.875} nominal periods instead, so the block presents itself as
	 * {@code cfg/1.75} rather than {@code cfg/2} - much healthier than it is. The
	 * gap edge is {@code cfg/1.725}, so it is still reported, by 1.4%.
	 * <p>
	 * The obvious repair is to narrow the slow side to {@code cfg/1.5}, as the
	 * VD6283 uses. That was tried and reverted: Test_065 contains a HEALTHY
	 * skin-temp boundary at 1.63x nominal spacing during start-up settling, and
	 * narrowing split it. This sensor's healthy behaviour genuinely reaches into
	 * the region a dropped block occupies, so the two assertions below are the
	 * real constraint from both sides, 1.63x must not split and 1.75x must.
	 */
	@Test
	public void test017_skinTempGapEdgeSeparatesRealLossFromStartUpSettling() {
		VerisenseDevice device = setupGen2Device(lightHeaderByte(2), SKIN_TEMP_CONFIG_32HZ_REFRESH);
		seedWindow(device, DATABLOCK_SENSOR_ID.SKIN_TEMP);
		double[] window = windowFor(DATABLOCK_SENSOR_ID.SKIN_TEMP);

		double configuredHz = 16.0;

		// Measured on Test_065: a 16-sample block boundary 1531 ms after the
		// previous block ended, against a nominal 937.5 ms. No samples were lost.
		assertFalse("the 1.63x start-up boundary observed on Test_065 must NOT split",
				UtilCsvSplitting.isSamplingRateOutsideOfLimits(window, configuredHz/1.633));

		// A dropped block, even hidden by a 12.5% catch-up, must still split.
		assertTrue("a dropped block landing on a catch-up must split",
				UtilCsvSplitting.isSamplingRateOutsideOfLimits(window, configuredHz/(2.0*0.875)));

		// A clean dropped block has proper margin; it is only the catch-up case
		// that is close.
		assertTrue(UtilCsvSplitting.isSamplingRateOutsideOfLimits(window, configuredHz/2.0));
		assertTrue("the catch-up case is deliberately close - if this margin ever grows"
				+ " past 10% somebody has widened the window without saying so",
				(window[0]-configuredHz/(2.0*0.875))/window[0] < 0.10);
	}

	/**
	 * The slowest skin-temp configuration, 0.25 Hz output, where a 16-sample
	 * block spans about 64 s. Two such blocks can land in one payload, and
	 * differencing their SUB-MINUTE end ticks re-bases a real 64 s gap to about
	 * 4 s, i.e. an apparent 4 Hz. That is what fragmented a 3-day recording into
	 * thousands of CSVs and, through the algorithm-buffer reset on every split,
	 * stopped non-wear detection producing any output. Taking the rate from the
	 * header removes the tick arithmetic entirely.
	 */
	@Test
	public void test018_skinTempAtSlowestRateUsesTheHeaderRateNotWrappedTicks() {
		VerisenseDevice device = setupGen2Device(lightHeaderByte(2), SKIN_TEMP_CONFIG_0HZ5_REFRESH);
		assertEquals("slowest configuration is 0.25 Hz output", 0.25, device.getSamplingRateForSensor(SENSORS.MLX90632), 1e-9);
		seedWindow(device, DATABLOCK_SENSOR_ID.SKIN_TEMP);

		double[] window = windowFor(DATABLOCK_SENSOR_ID.SKIN_TEMP);
		assertNotNull(window);
		assertTrue("the window must sit around 0.25 Hz, not the wrapped ~4 Hz", window[1] < 1.0);

		// A genuine 64 s boundary between 0.25 Hz blocks must NOT split.
		walkStream(device, DATABLOCK_SENSOR_ID.SKIN_TEMP, 10, 64.0, 64.0, 64.0);
		// The aliased ~4 Hz reading that the tick path produced would split.
		assertTrue(UtilCsvSplitting.isSamplingRateOutsideOfLimits(window, 4.0));
	}

	// ------------------------------------------------------------- other

	// ------------------------------------------- the firmware-fault diagnostic

	/**
	 * Captures whatever the parser prints while {@code runnable} runs.
	 * <p>
	 * Worth the awkwardness: the warning below is the ONLY signal that would
	 * exist if the firmware shipped with the rate field broken, so "does it
	 * actually print" is the whole point of it.
	 */
	private String captureConsole(Runnable runnable) {
		PrintStream original = System.out;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		try {
			System.setOut(new PrintStream(captured));
			runnable.run();
		} finally {
			System.setOut(original);
		}
		return captured.toString();
	}

	/**
	 * A recording from firmware that predates the field is the ordinary case and
	 * must stay silent. Warning on every old recording would train people to
	 * ignore the line that matters.
	 */
	@Test
	public void test023_oldFirmwareWithNoStoredRateIsNotReported() {
		final VerisenseDevice device = setupGen2Device(lightHeaderByte(0), SKIN_TEMP_CONFIG_32HZ_REFRESH, 2, 0, 9);
		assertFalse("fixture must have no stored rate", device.getSensorVD6283().isConfiguredRateKnown());
		assertFalse("and must predate the field", device.isPayloadDesignV14orAbove());

		String printed = captureConsole(new Runnable() {
			@Override
			public void run() {
				UtilCsvSplitting.warnIfLightRateFieldUnusable(device);
			}
		});
		assertEquals("an old recording must print nothing", "", printed.trim());
	}

	/**
	 * The case this exists for: firmware new enough to store the rate, carrying
	 * light blocks, with the field clear. That is a firmware fault, and without
	 * this line it would be indistinguishable from an old recording - the file
	 * would parse, the light data would come out, and the near-blind fallback
	 * window would never be mentioned.
	 */
	@Test
	public void test024_newFirmwareWithNoStoredRateIsReportedOncePerFile() {
		final VerisenseDevice device = setupGen2Device(lightHeaderByte(0), SKIN_TEMP_CONFIG_32HZ_REFRESH, 2, 2, 0);
		assertTrue("fixture must claim firmware that stores the field", device.isPayloadDesignV14orAbove());
		assertFalse("but carry no rate", device.getSensorVD6283().isConfiguredRateKnown());

		String printed = captureConsole(new Runnable() {
			@Override
			public void run() {
				// Three payloads of the same file: the warning is per recording.
				UtilCsvSplitting.warnIfLightRateFieldUnusable(device);
				UtilCsvSplitting.warnIfLightRateFieldUnusable(device);
				UtilCsvSplitting.warnIfLightRateFieldUnusable(device);
			}
		});
		assertTrue("it must say the firmware is at fault: " + printed, printed.contains("firmware fault"));
		assertEquals("exactly one line per file", 1, printed.split("WARNING", -1).length-1);

		// A new file clears the state, so the next recording is reported too.
		UtilCsvSplitting.clearMapOfSamplingRateLimitsPerSensor();
		String nextFile = captureConsole(new Runnable() {
			@Override
			public void run() {
				UtilCsvSplitting.warnIfLightRateFieldUnusable(device);
			}
		});
		assertTrue("the next file must warn again", nextFile.contains("WARNING"));
	}

	/** New firmware that DID store the rate is the healthy case: silent. */
	@Test
	public void test025_newFirmwareWithAStoredRateIsNotReported() {
		final VerisenseDevice device = setupGen2Device(lightHeaderByte(2), SKIN_TEMP_CONFIG_32HZ_REFRESH, 2, 2, 0);
		assertTrue(device.getSensorVD6283().isConfiguredRateKnown());

		String printed = captureConsole(new Runnable() {
			@Override
			public void run() {
				UtilCsvSplitting.warnIfLightRateFieldUnusable(device);
			}
		});
		assertEquals("", printed.trim());
	}

	/**
	 * A reserved index is reported whatever the payload design says.
	 * <p>
	 * Two reasons it cannot be folded into the previous case. It is a different
	 * fault, so saying the field is "clear" would send a reader after the wrong
	 * firmware bug. And on an OLD recording those bits should be clear by
	 * construction, so a non-zero one is an anomaly in its own right - gating the
	 * report on the firmware version would have said nothing at all.
	 */
	@Test
	public void test026_reservedRateIndexIsReportedWhateverTheFirmwareVersion() {
		for(final int[] fw:new int[][] {{2, 0, 9}, {2, 2, 0}}) {
			UtilCsvSplitting.clearMapOfSamplingRateLimitsPerSensor();
			final VerisenseDevice device = setupGen2Device(
					lightHeaderByte(9), SKIN_TEMP_CONFIG_32HZ_REFRESH, fw[0], fw[1], fw[2]);
			assertTrue(device.getSensorVD6283().isRateIndexReserved());

			String printed = captureConsole(new Runnable() {
				@Override
				public void run() {
					UtilCsvSplitting.warnIfLightRateFieldUnusable(device);
				}
			});
			String where = "firmware " + fw[0] + "." + fw[1] + "." + fw[2] + ": " + printed;
			assertTrue("a reserved index must be reported, " + where, printed.contains("WARNING"));
			assertTrue("and named, " + where, printed.contains("reserved index 9"));
			assertFalse("it must not be described as clear, " + where, printed.contains("field clear"));
		}
	}

	// ------------------------------------- the layer above the window seeder

	/**
	 * Everything else here drives {@link UtilCsvSplitting} directly. This drives
	 * the layer that decides WHETHER the seeder runs during a real payload parse.
	 * <p>
	 * That layer matters for a reason that is invisible from the seeder itself:
	 * asking the device for the sensor-class keys behind a data block id CREATES
	 * and caches them, and the lookup returns the second-generation slow sensors
	 * unconditionally. Seeding for a sensor the payload has no blocks for would
	 * therefore populate mappings and rate limits for hardware the recording does
	 * not have - visible on first-generation files, which have neither slow sensor.
	 */
	@Test
	public void test027_windowIsSeededOnlyForSensorsThePayloadActuallyCarries() {
		VerisenseDevice device = setupLightDevice(2);
		PayloadContentsDetailsV8orAbove payload = new PayloadContentsDetailsV8orAbove(device);

		assertFalse("an empty payload carries neither slow sensor",
				payload.containsDataBlockForSensor(DATABLOCK_SENSOR_ID.LIGHT));
		payload.seedSlowSensorGapWindow(DATABLOCK_SENSOR_ID.LIGHT);
		assertNull("no blocks means no window", windowFor(DATABLOCK_SENSOR_ID.LIGHT));
		assertNull(windowFor(DATABLOCK_SENSOR_ID.SKIN_TEMP));

		// Give it a light block and the light window appears - and only that one.
		payload.listOfDataBlocksInOrder.add(newBlock(device, DATABLOCK_SENSOR_ID.LIGHT, ticks(10)));
		assertTrue(payload.containsDataBlockForSensor(DATABLOCK_SENSOR_ID.LIGHT));
		assertFalse(payload.containsDataBlockForSensor(DATABLOCK_SENSOR_ID.SKIN_TEMP));

		payload.seedSlowSensorGapWindow(DATABLOCK_SENSOR_ID.LIGHT);
		payload.seedSlowSensorGapWindow(DATABLOCK_SENSOR_ID.SKIN_TEMP);

		double[] lightWindow = windowFor(DATABLOCK_SENSOR_ID.LIGHT);
		assertNotNull("a payload carrying light blocks must get its window", lightWindow);
		assertEquals(1.0/UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO,
				lightWindow[0], 1e-9);
		assertNull("and the sensor with no blocks must get nothing",
				windowFor(DATABLOCK_SENSOR_ID.SKIN_TEMP));
	}

	/** Nothing here may disturb a fast sensor's own band. */
	@Test
	public void test019_fastSensorLimitsAreUntouched() {
		VerisenseDevice device = setupLightDevice(2);
		double[] fastSensorLimits = UtilCsvSplitting.calculateSamplingRateLimits(960);
		UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.put(SENSORS.LSM6DSV, fastSensorLimits);

		seedWindow(device, DATABLOCK_SENSOR_ID.LIGHT);

		assertTrue("the fast sensor's band must be the same array, unmodified",
				fastSensorLimits==UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.get(SENSORS.LSM6DSV));
		assertEquals(960*UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.LOWER, fastSensorLimits[0], 1e-9);
	}

	/**
	 * The window is a pure function of the header, so re-seeding it - which
	 * happens on every payload - always yields the same band, and clearing the
	 * state at a CSV-set boundary loses nothing that cannot be recomputed.
	 */
	@Test
	public void test020_windowIsStatelessAndReproducible() {
		VerisenseDevice device = setupLightDevice(2);
		seedWindow(device, DATABLOCK_SENSOR_ID.LIGHT);
		double[] first = windowFor(DATABLOCK_SENSOR_ID.LIGHT).clone();

		for (int i = 0; i < 50; i++) {
			seedWindow(device, DATABLOCK_SENSOR_ID.LIGHT);
		}
		assertArrayEqualsExact(first, windowFor(DATABLOCK_SENSOR_ID.LIGHT));

		UtilCsvSplitting.clearMapOfSamplingRateLimitsPerSensor();
		seedWindow(device, DATABLOCK_SENSOR_ID.LIGHT);
		assertArrayEqualsExact(first, windowFor(DATABLOCK_SENSOR_ID.LIGHT));
	}

	private static void assertArrayEqualsExact(double[] expected, double[] actual) {
		assertNotNull(actual);
		assertEquals(expected.length, actual.length);
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], actual[i], 0.0);
		}
	}

	/**
	 * Pins the regression this change exists to fix, without needing the old
	 * implementation to hand.
	 * <p>
	 * Before the rate was stored, the light window could only come from
	 * {@code getRateFreq()}, which was the exposure bound - 10 Hz at the default
	 * 100 ms exposure - widened by the standard +/-10%. A 1 Hz recording presents
	 * 10 samples per 10 s, i.e. 1 Hz, which sits far outside that band, so EVERY
	 * boundary was reported as a gap and every block became its own CSV: 129 of
	 * them from one 22-minute recording (DEV-979). The header-derived window
	 * accepts the same boundary.
	 */
	@Test
	public void test021_theExposureDerivedBandIsWhatUsedToSplitEveryBlock() {
		double exposureBoundHz = setupLightDevice(0).getSensorVD6283().getRateFreq();
		assertEquals("the old band was built from this", 10.0, exposureBoundHz, 1e-9);
		double[] oldBand = UtilCsvSplitting.calculateSamplingRateLimits(exposureBoundHz);
	
		double apparentRateOf1HzBoundaryHz = LIGHT_SAMPLES_PER_BLOCK/10.0;
		assertTrue("a healthy 1 Hz boundary was OUTSIDE the exposure-derived band",
			UtilCsvSplitting.isSamplingRateOutsideOfLimits(oldBand, apparentRateOf1HzBoundaryHz));
	
		VerisenseDevice device = setupLightDevice(2);
		seedWindow(device, DATABLOCK_SENSOR_ID.LIGHT);
		assertFalse("and INSIDE the header-derived window",
			UtilCsvSplitting.isSamplingRateOutsideOfLimits(windowFor(DATABLOCK_SENSOR_ID.LIGHT), apparentRateOf1HzBoundaryHz));
	}

	/**
	 * The CSV sensor-config line reports {@code Configured} only when the header
	 * actually carried the rate, so a reader can tell a known rate from an
	 * exposure-derived guess.
	 */
	@Test
	public void test022_csvConfigLineReportsConfiguredOnlyWhenKnown() {
		String known = setupLightDevice(2).generateSensorConfigStrSingleSensor(SENSORS.VD6283, 0.993);
		assertTrue("known rate must report Configured: " + known, known.contains("Configured = 1.0 Hz"));
		assertTrue(known.contains("Calculated = 0.993 Hz"));
		assertTrue("the rest of the line must be preserved: " + known,
				known.contains("Gain = 2.50x") && known.contains("Slot1 = Visible"));

		String unknown = setupLightDevice(0).generateSensorConfigStrSingleSensor(SENSORS.VD6283, 0.993);
		assertFalse("an unknown rate must NOT be reported as configured: " + unknown,
				unknown.contains("Configured"));
		assertTrue(unknown.contains("Calculated = 0.993 Hz"));
		assertTrue(unknown.contains("Gain = 2.50x") && unknown.contains("Slot1 = Visible"));
	}
}
