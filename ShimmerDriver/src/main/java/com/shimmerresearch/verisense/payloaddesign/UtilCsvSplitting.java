package com.shimmerresearch.verisense.payloaddesign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;
import com.shimmerresearch.verisense.UtilVerisenseDriver;
import com.shimmerresearch.verisense.VerisenseDevice;
import com.shimmerresearch.verisense.payloaddesign.DataBlockDetails.DATABLOCK_SENSOR_ID;
import com.shimmerresearch.verisense.sensors.SensorMLX90632;
import com.shimmerresearch.verisense.sensors.SensorVD6283;

public class UtilCsvSplitting {

	public class FILE_GAP_TOLERANCE_MULTIPLIER {
		// +/- 10%
		public static final double UPPER = 1.1;
		public static final double LOWER = 0.9;
		/**
		 * Slow sensors only (VD6283 light / MLX90632 skin temp): the largest
		 * inter-block gap, as a multiple of the achieved median block spacing, that
		 * is still treated as continuous. The MLX90632's conversions can slip by
		 * several refresh periods and then catch up (observed up to +12.5% block
		 * spacing on the DEV-927 validation recording with no samples lost) and the
		 * VD6283's cadence is bimodal (exposure vs exposure + dead time), so the
		 * standard LOWER (-10%) band is routinely violated by healthy data. A
		 * genuinely dropped block doubles the spacing (2x), so 1.5x keeps
		 * comfortable margin on both sides.
		 */
		public static final double SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO = 1.5;
		/**
		 * MLX90632 only: how far a SINGLE block boundary's apparent rate may sit
		 * either side of the configured output rate and still be plausible, used to
		 * widen that rate into the a-priori window applied before any boundary has
		 * been measured.
		 * <p>
		 * The chip's conversions slip by several refresh periods and then catch up
		 * (+12.5% block spacing observed on the DEV-927 validation recording, with
		 * no samples lost), so a boundary reads ~12.5% slow and the one after it
		 * correspondingly fast. Only the AVERAGE rate is bounded by the configured
		 * one; a single boundary is not, which is why the configured rate alone is
		 * too tight a bound. 1.15 covers the observed slip with a little margin.
		 * <p>
		 * Not used for the VD6283, whose a-priori bounds come from the firmware's
		 * rate table instead - its configured rate is not in the payload at all,
		 * and its sampling is a plain periodic timer with no slip-and-catch-up
		 * behaviour (a failed read costs a whole period, it never shortens one).
		 */
		public static final double SLOW_SENSOR_CONVERSION_SLIP_TOLERANCE = 1.15;
	}

	/**
	 * Slow sensors only: how many block boundaries must have been observed before
	 * the MEASURED window replaces the provisional one. Below this the median is
	 * not an estimate of anything - the boundary about to be judged is itself one
	 * of the one or two values it would be built from, so it would always be found
	 * continuous, and the first boundary of every CSV set would be unreportable no
	 * matter how large its gap.
	 */
	public static final int SLOW_SENSOR_MIN_OBSERVATIONS_FOR_MEASURED_WINDOW = 3;

	/**
	 * Slow sensors only: how many of the most recently observed per-sample periods
	 * {@link #recordAndGetSlowSensorPeriodS(VerisenseDevice, DATABLOCK_SENSOR_ID, double)} keeps
	 * per sensor. Bounded so the estimate follows genuine long-term drift instead
	 * of averaging a multi-day recording, and so the median stays cheap to re-take
	 * on every payload. Large enough that occasional dropped blocks cannot move
	 * the median once the history is full - but see the design note on
	 * {@link #refineSlowSensorGapWindow(VerisenseDevice, DATABLOCK_SENSOR_ID, int)}
	 * for what SUSTAINED loss does.
	 */
	public static final int SLOW_SENSOR_OBSERVED_RATE_HISTORY_MAX = 256;
	
	protected static HashMap<SENSORS, double[]> SAMPLING_RATE_LIMITS_PER_SENSOR = new HashMap<SENSORS, double[]>(); 

	/**
	 * Slow sensors only: the ABSOLUTE real-world-clock end time (ms) of the last
	 * block seen for each slow-sensor data block id, carried from one payload to
	 * the next so the achieved per-sample period can be measured across payload
	 * boundaries. A 1 Hz light block spans 10 s while a payload spans ~2 s, so a
	 * payload carries at most one light block and there is no inter-block gap
	 * inside it to measure.
	 * <p>
	 * Absolute milliseconds, not the per-block sub-minute tick counter, because a
	 * tick delta cannot tell a 5 s spacing from a 65 s one - a real 65 s gap
	 * between 10-sample light blocks aliases to a perfectly legitimate 2 Hz.
	 * Populated only once the block timings have been back-filled.
	 */
	protected static HashMap<DATABLOCK_SENSOR_ID, Double> SLOW_SENSOR_LAST_BLOCK_END_TIME_RWC_MS = new HashMap<DATABLOCK_SENSOR_ID, Double>();

	protected static HashMap<DATABLOCK_SENSOR_ID, List<Double>> SLOW_SENSOR_OBSERVED_BLOCK_PERIODS_S = new HashMap<DATABLOCK_SENSOR_ID, List<Double>>();

	public static boolean isTsDifferenceOutsideOfLimits(double expectedPayloadTsDiffLimits[], double unixTimeInMs_1, double unixTimeInMs_2) {
		double differenceInMillisec = Math.abs(unixTimeInMs_1 - unixTimeInMs_2);
		if(differenceInMillisec < expectedPayloadTsDiffLimits[0] || differenceInMillisec > expectedPayloadTsDiffLimits[1]) {
			return true;
		}
		return false;
	}

	public static String isSamplingRateOutsideOfLimits(double[] samplingRateLimits, DataBlockDetails previousBlockDetails, DataBlockDetails nextBlockDetails, SENSORS sensorClassKey) {
		// The end time in the payload and data blocks comes from the sensor whereas the
		// start time is calculated by the file parser from the end time and the
		// configured sampling rate. As the sampling rate in the Verisense chips can
		// drift, it's better to check that we are getting the correct the number of
		// samples between the end time of the one datablock and the end time of
		// the next block (i.e., the average sampling rate is within a reasonable
		// tolerance) rather than checking the time diff between the end of one
		// datablock and the start of the next datablock.
		double calculatedSamplingRate = UtilVerisenseDriver.calcSamplingRate(previousBlockDetails.getEndTimeRwcMs(), nextBlockDetails.getEndTimeRwcMs(), nextBlockDetails.getSampleCount());
		if(Double.isNaN(calculatedSamplingRate)) {
			return ("WARNING!!! Unable to calculate sampling rate");
		}
		
		if(isSamplingRateOutsideOfLimits(samplingRateLimits, calculatedSamplingRate)) {
			//UtilShimmer.consolePrintCurrentStackTrace();
			
			double timeGapS = Math.abs((nextBlockDetails.getStartTimeRwcMs()-previousBlockDetails.getEndTimeRwcMs())/1000);
			
			String timeGapLocation = (previousBlockDetails.getPayloadIndex()==nextBlockDetails.getPayloadIndex()? "datablocks":"payloads");
			return("WARNING!!! Unexpected sampling rate or time-gap detected for sensor " + sensorClassKey + " in between " + timeGapLocation + ": " 
					+ "\n  |_1) " + previousBlockDetails.generateDebugStr() 
					+ "\n  |_2) " + nextBlockDetails.generateDebugStr() 

//					+ "\n    |_Time between datablocks=" + timeToStr(nextBlockDetails.getStartTimeMs()-previousBlockDetails.getEndTimeMs())
					+ "\n    |_Time between datablocks=" + UtilVerisenseDriver.convertSecondsToHHmmssSSS(timeGapS) + " (HH:mm:ss.SSS)"
					+ "\n    |_Detected=" + freqToStr(calculatedSamplingRate) //+ " (" + timeToStr(1/calculatedSamplingRate) + ")"
					+ ", Limits: Min=" + freqToStr(samplingRateLimits[0]) + " (" + timeToStr(1/samplingRateLimits[0]) + ")"
					+ ", Max=" + freqToStr(samplingRateLimits[1]) + " (" + timeToStr(1/samplingRateLimits[1]) + ")"
//					+ "\n    |_Payload index " + nextBlockDetails.payloadIndex + " EndTime [Minutes=" + nextBlockDetails.rtcEndTimeMinutes + ", Ticks=" + nextBlockDetails.rtcEndTimeTicks + "]"
					);
		}
		return "";
	}

	public static boolean isSamplingRateOutsideOfLimits(double[] samplingRateLimits, double samplingRate) {
		if(samplingRate < samplingRateLimits[0] || samplingRate > samplingRateLimits[1]) {
			return true;
		}
		return false;
	}

	public static void populateExpectedPayloadTsDiffLimitMapIfNeeded(VerisenseDevice verisenseDevice, HashMap<DATABLOCK_SENSOR_ID, List<SENSORS>> mapOfSensorIdsPerDataBlock) {
		for (List<SENSORS> listOfSensorClassKeys : mapOfSensorIdsPerDataBlock.values()) {
			for (SENSORS sensorClassKey : listOfSensorClassKeys) {
				if(!UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.containsKey(sensorClassKey)) {
					double configuredSamplingRate = verisenseDevice.getSamplingRateForSensor(sensorClassKey);
					double[] samplingRateLimits = calculateSamplingRateLimits(configuredSamplingRate);
					UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.put(sensorClassKey, samplingRateLimits);
				}
			}
		}
	}

	public static double[] calculateSamplingRateLimits(double configuredSamplingRate) {
		// +/- of configured sampling rate
		return new double[] {configuredSamplingRate*FILE_GAP_TOLERANCE_MULTIPLIER.LOWER, configuredSamplingRate*FILE_GAP_TOLERANCE_MULTIPLIER.UPPER};
	}

	/**
	 * Clears everything the CSV-splitting windows are derived from. Called
	 * whenever a whole CSV set is written out (end of file, config change or
	 * device reset) so that no measurement leaks across a CSV-set boundary - the
	 * timing regime either side of a reset is unrelated.
	 */
	public static void clearMapOfSamplingRateLimitsPerSensor() {
		SAMPLING_RATE_LIMITS_PER_SENSOR.clear();
		SLOW_SENSOR_LAST_BLOCK_END_TIME_RWC_MS.clear();
		SLOW_SENSOR_OBSERVED_BLOCK_PERIODS_S.clear();
	}

	/**
	 * Records one observed slow-sensor per-sample period and returns the sensor's
	 * best current estimate of it.
	 * <p>
	 * The slow sensors (VD6283 ambient light, MLX90632 skin temp) buffer a fixed
	 * number of samples and emit the block only once it is full, stamping it with
	 * the time of its LAST sample. The spacing between two consecutive blocks'
	 * end times divided by the samples per block is therefore the achieved
	 * per-sample period - the same technique the storage-format spec prescribes for
	 * the LSM6DSV, and the only way to recover the VD6283's rate at all, because
	 * the configured rate index is not stored in the payload (see
	 * {@link com.shimmerresearch.verisense.sensors.SensorVD6283#getRateFreq()}).
	 * <p>
	 * The MEDIAN over the accumulated history is returned rather than the latest
	 * delta. A single failed I2C read makes one block take an extra period to fill
	 * without losing a sample slot (hal_slowSensorSampler.c increments the count
	 * only on a successful read), and a dropped block doubles the delta - taking
	 * the raw delta would stretch that block's samples by 10% or 100%, whereas the
	 * median keeps every block on the true period, which is where the samples
	 * actually are. The history is bounded to
	 * {@link #SLOW_SENSOR_OBSERVED_RATE_HISTORY_MAX} so the estimate still follows
	 * genuine long-term drift.
	 * 
	 * @param slowSensorId the slow sensor's data block id
	 * @param observedPeriodS the period just measured, or NaN to only read the estimate back
	 * @return the median observed period in seconds, or NaN if nothing has been observed
	 */
	public static double recordAndGetSlowSensorPeriodS(VerisenseDevice verisenseDevice, DATABLOCK_SENSOR_ID slowSensorId, double observedPeriodS) {
		List<Double> observedPeriodsS = SLOW_SENSOR_OBSERVED_BLOCK_PERIODS_S.get(slowSensorId);
		if(observedPeriodsS==null) {
			observedPeriodsS = new ArrayList<Double>();
			SLOW_SENSOR_OBSERVED_BLOCK_PERIODS_S.put(slowSensorId, observedPeriodsS);
		}
		// Every finite positive observation is learned from, anomalies included.
		// Filtering here cannot be done safely: the history is empty after every
		// clear, so the first observation would define what counts as plausible and a
		// CSV set opening on a dropped block would lock the estimate onto the wrong
		// period for good. Anomalies are handled by the median, and by the window
		// builder rejecting implausible values when it picks the fast side.
		if(isSlowSensorPeriodPlausible(verisenseDevice, slowSensorId, observedPeriodS)) {
			observedPeriodsS.add(Double.valueOf(observedPeriodS));
			if(observedPeriodsS.size()>SLOW_SENSOR_OBSERVED_RATE_HISTORY_MAX) {
				// Keep the NEWEST observations - the estimate tracks the sensor.
				observedPeriodsS.subList(0, observedPeriodsS.size()-SLOW_SENSOR_OBSERVED_RATE_HISTORY_MAX).clear();
			}
		}
		return calculateMedian(observedPeriodsS);
	}

	/**
	 * Whether an observed per-sample period is one the sensor could actually have
	 * produced, i.e. finite, positive and inside
	 * {@code getSlowSensorPlausibleRateRangeHz}.
	 * <p>
	 * This catches a period no configuration could have produced - a dropped block
	 * or a clock correction stretching a boundary well past the slowest rate, for
	 * instance - so that neither the running estimate nor the timing of a block
	 * can be built from one. It does NOT and cannot catch tick aliasing: a real
	 * 65 s gap between 10-sample light blocks aliases to 0.5 s per sample, i.e.
	 * 2 Hz, which IS a legitimate firmware rate. That is why the cross-payload
	 * measurement differences absolute real-world-clock milliseconds instead of
	 * ticks (see
	 * {@code PayloadContentsDetailsV8orAbove.observeSlowSensorBlockSpacing}) - the
	 * ambiguity is removed at the source rather than filtered here.
	 * 
	 * @param verisenseDevice the device being parsed
	 * @param slowSensorId the slow sensor's data block id
	 * @param observedPeriodS the candidate per-sample period in seconds
	 * @return true when the period is one the sensor could have produced
	 */
	public static boolean isSlowSensorPeriodPlausible(VerisenseDevice verisenseDevice, DATABLOCK_SENSOR_ID slowSensorId, double observedPeriodS) {
		if(Double.isNaN(observedPeriodS) || Double.isInfinite(observedPeriodS) || !(observedPeriodS>0)) {
			return false;
		}
		double[] plausibleRateRangeHz = getSlowSensorPlausibleRateRangeHz(verisenseDevice, slowSensorId);
		if(plausibleRateRangeHz==null) {
			return true;
		}
		double observedRateHz = 1.0/observedPeriodS;
		return observedRateHz>=plausibleRateRangeHz[0] && observedRateHz<=plausibleRateRangeHz[1];
	}

	public static int getSlowSensorObservationCount(DATABLOCK_SENSOR_ID slowSensorId) {
		List<Double> observedPeriodsS = SLOW_SENSOR_OBSERVED_BLOCK_PERIODS_S.get(slowSensorId);
		return observedPeriodsS==null? 0:observedPeriodsS.size();
	}

	/**
	 * (Re)derives a slow sensor's CSV gap-splitting window.
	 * <p>
	 * Once the achieved per-sample period is known the window is the #285 formula
	 * over the measured history: gap side
	 * {@code median / SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO} so healthy jitter
	 * stays continuous while a dropped block (2x spacing) is reported, fast side
	 * the quickest boundary that is still plausible against the median, with the
	 * standard tolerance. The fast side has to be an extremum rather than the
	 * median because the MLX90632's conversions slip and then catch up (+12.5%
	 * observed on the DEV-927 recording, so the catch-up boundary reads
	 * correspondingly fast); excluding the implausible ones here rather than when
	 * they were learned stops an overlap artefact widening the fast side past
	 * itself while still letting the median recover from an early anomaly.
	 * <p>
	 * Below {@link #SLOW_SENSOR_MIN_OBSERVATIONS_FOR_MEASURED_WINDOW}
	 * observations - which includes the first block of every CSV set, when there
	 * are none - an A-PRIORI window is used instead, because the boundary being
	 * judged is itself one of the one or two values a measured window would be
	 * built from, so that window would simply re-centre on whatever it was about
	 * to judge and the first boundary of every CSV set would be continuous no
	 * matter how large its gap. The a-priori bounds come from what the hardware
	 * can actually do, not from an arbitrary multiple: see
	 * {@code getSlowSensorPlausibleRateRangeHz}.
	 * <p>
	 * DESIGN NOTE: the window follows the data, so SUSTAINED block loss is
	 * eventually learned as the cadence. If every other block went missing for
	 * more than {@link #SLOW_SENSOR_OBSERVED_RATE_HISTORY_MAX} boundaries the
	 * median would move onto the halved rate and the loss would stop being
	 * reported; the return to the true cadence is then reported once, as a single
	 * split. That is the price of tracking a rate the payload does not carry.
	 * 
	 * @param verisenseDevice the device being parsed
	 * @param slowSensorId the slow sensor's data block id
	 * @param samplesPerBlock the sensor's fixed samples per block
	 */
	public static void refineSlowSensorGapWindow(VerisenseDevice verisenseDevice, DATABLOCK_SENSOR_ID slowSensorId, int samplesPerBlock) {
		List<Double> observedPeriodsS = SLOW_SENSOR_OBSERVED_BLOCK_PERIODS_S.get(slowSensorId);
		double[] samplingRateLimits = null;
		if(observedPeriodsS!=null && observedPeriodsS.size()>=SLOW_SENSOR_MIN_OBSERVATIONS_FOR_MEASURED_WINDOW) {
			double medianRateHz = 1.0/calculateMedian(observedPeriodsS);
			if(medianRateHz>0 && !Double.isInfinite(medianRateHz)) {
				double plausibleRateCeilingHz = medianRateHz*FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO;
				double fastestPlausibleRateHz = medianRateHz;
				for(Double observedPeriodS:observedPeriodsS) {
					double observedRateHz = 1.0/observedPeriodS.doubleValue();
					if(observedRateHz>fastestPlausibleRateHz && observedRateHz<=plausibleRateCeilingHz) {
						fastestPlausibleRateHz = observedRateHz;
					}
				}
				samplingRateLimits = new double[] {
						medianRateHz/FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO,
						fastestPlausibleRateHz*FILE_GAP_TOLERANCE_MULTIPLIER.UPPER};
			}
		}
		if(samplingRateLimits==null) {
			double[] plausibleRateRangeHz = getSlowSensorPlausibleRateRangeHz(verisenseDevice, slowSensorId);
			if(plausibleRateRangeHz==null) {
				return;
			}
			samplingRateLimits = new double[] {
					plausibleRateRangeHz[0]/FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO,
					plausibleRateRangeHz[1]*FILE_GAP_TOLERANCE_MULTIPLIER.UPPER};
		}
		// Deliberately unconditional: populateExpectedPayloadTsDiffLimitMapIfNeeded
		// would otherwise leave a band built from the configured rate in place, and
		// for the VD6283 that "configured rate" is only an exposure-derived upper
		// bound which can be 10x the truth.
		for(SENSORS sensorClassKey:verisenseDevice.getOrCreateListOfSensorClassKeysForDataBlockId(slowSensorId)) {
			if(sensorClassKey!=SENSORS.CLOCK) {
				SAMPLING_RATE_LIMITS_PER_SENSOR.put(sensorClassKey, samplingRateLimits);
			}
		}
	}

	/**
	 * Whether a slow sensor's block spacing can be measured ACROSS payloads from
	 * the sub-minute tick counter without ambiguity.
	 * <p>
	 * A block's stored end time is a counter that wraps every minute, so a delta
	 * between two blocks is only recoverable (by re-basing a negative delta by one
	 * minute) while the true spacing is under a minute. Inside one payload that is
	 * guaranteed by the payload's own duration, but across payloads it has to be
	 * bounded by what the sensor could be configured to do: the slowest rate the
	 * hardware offers times the samples per block.
	 * <p>
	 * A 10-sample VD6283 block spans at most 20 s (slowest firmware rate 0.5 Hz)
	 * and is always safe. A 16-sample MLX90632 block spans 64 s in the common
	 * medical-mode worst case (0.5 Hz refresh / 2 = 0.25 Hz output) and 96 s in
	 * the extended-mode worst case
	 * ({@link com.shimmerresearch.verisense.sensors.SensorMLX90632#MIN_OUTPUT_RATE_HZ},
	 * 0.5 Hz refresh / 3 = 0.167 Hz), and this method uses that worst case - both
	 * exceed the 60 s unambiguous span, so the skin temp never qualifies. It costs
	 * nothing: the MLX90632's refresh code IS stored in the payload, so its
	 * header-derived rate is already correct and it only needs the within-payload
	 * refinement it has always had.
	 * 
	 * @param slowSensorId the slow sensor's data block id
	 * @param samplesPerBlock the sensor's fixed samples per block
	 * @return true when a cross-payload tick delta is unambiguous
	 */
	public static boolean isSlowSensorSpanUnambiguousAcrossPayloads(DATABLOCK_SENSOR_ID slowSensorId, int samplesPerBlock) {
		double[] plausibleRateRangeHz = null;
		if(slowSensorId==DATABLOCK_SENSOR_ID.LIGHT) {
			plausibleRateRangeHz = new double[] {SensorVD6283.MIN_SAMPLE_RATE_HZ, SensorVD6283.MAX_SAMPLE_RATE_HZ};
		} else if(slowSensorId==DATABLOCK_SENSOR_ID.SKIN_TEMP) {
			plausibleRateRangeHz = new double[] {SensorMLX90632.MIN_OUTPUT_RATE_HZ, SensorMLX90632.MAX_OUTPUT_RATE_HZ};
		}
		if(plausibleRateRangeHz==null || !(plausibleRateRangeHz[0]>0)) {
			return false;
		}
		double maximumBlockSpanS = samplesPerBlock/plausibleRateRangeHz[0];
		return maximumBlockSpanS<(AsmBinaryFileConstants.TICKS_PER_MINUTE/AsmBinaryFileConstants.TICKS_PER_SECOND);
	}

	/**
	 * The {min, max} per-sample rate a slow sensor could legitimately be running
	 * at, used for the a-priori gap window before anything has been measured.
	 * <p>
	 * VD6283: the firmware's whole rate table
	 * ({@link com.shimmerresearch.verisense.sensors.SensorVD6283#MIN_SAMPLE_RATE_HZ}
	 * ..{@link com.shimmerresearch.verisense.sensors.SensorVD6283#MAX_SAMPLE_RATE_HZ}
	 * = 0.5..20 Hz), because the configured index is not in the payload and the
	 * exposure only bounds the rate from above. A 10-sample block may therefore
	 * legitimately span anything from 0.5 s to 20 s.
	 * <p>
	 * BLIND SPOT, quantified: with the standard tolerances that window is
	 * [0.33, 22] Hz, and a boundary presents {@code 10 / deltaS}, so on the first
	 * two boundaries of a CSV set any spacing up to 30 s is accepted. At the
	 * firmware's default 1 Hz that means up to 20 s of genuinely lost light data
	 * goes unreported there, permanently - those two boundaries are never
	 * re-judged. The fast side likewise accepts a backwards clock jump of up to
	 * ~9.5 s. A 60 s spacing does split. This is the price of not knowing the
	 * configured rate: the alternative, centring the window on one or two
	 * observations, cannot report anything at all (the boundary being judged is
	 * the estimate). From the third boundary on the measured window applies and
	 * the tolerance is 1.5x the achieved period.
	 * <p>
	 * MLX90632: the refresh code IS stored in the payload, so the configured
	 * output rate is known; it is widened by
	 * {@link FILE_GAP_TOLERANCE_MULTIPLIER#SLOW_SENSOR_CONVERSION_SLIP_TOLERANCE}
	 * to cover the documented conversion slip and catch-up, and falls back to the
	 * refresh table's full span if the configured rate is unusable.
	 * 
	 * @param verisenseDevice the device being parsed
	 * @param slowSensorId the slow sensor's data block id
	 * @return the {min, max} plausible rate in Hz, or null if it cannot be bounded
	 */
	public static double[] getSlowSensorPlausibleRateRangeHz(VerisenseDevice verisenseDevice, DATABLOCK_SENSOR_ID slowSensorId) {
		if(slowSensorId==DATABLOCK_SENSOR_ID.LIGHT) {
			return new double[] {SensorVD6283.MIN_SAMPLE_RATE_HZ, SensorVD6283.MAX_SAMPLE_RATE_HZ};
		}
		if(slowSensorId==DATABLOCK_SENSOR_ID.SKIN_TEMP) {
			double configuredSamplingRate = verisenseDevice.getSamplingRateForSensor(SENSORS.MLX90632);
			if(configuredSamplingRate>0 && !Double.isNaN(configuredSamplingRate) && !Double.isInfinite(configuredSamplingRate)) {
				return new double[] {
						configuredSamplingRate/FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_CONVERSION_SLIP_TOLERANCE,
						configuredSamplingRate*FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_CONVERSION_SLIP_TOLERANCE};
			}
			return new double[] {SensorMLX90632.MIN_OUTPUT_RATE_HZ, SensorMLX90632.MAX_OUTPUT_RATE_HZ};
		}
		return null;
	}

	/**
	 * Median of a list of observations - the mean of the two middle values for an
	 * even-sized input, so that neither of a pair straddling the middle can hand
	 * the estimate to an outlier on its own. Sorts a copy; the caller's list is
	 * kept in arrival order so its oldest entries can be trimmed.
	 * 
	 * @param values the observations, in arrival order
	 * @return the median, or NaN when there are none
	 */
	public static double calculateMedian(List<Double> values) {
		if(values==null || values.isEmpty()) {
			return Double.NaN;
		}
		List<Double> sortedValues = new ArrayList<Double>(values);
		Collections.sort(sortedValues);
		int middleIndex = sortedValues.size()/2;
		if(sortedValues.size()%2==0) {
			return (sortedValues.get(middleIndex-1).doubleValue()+sortedValues.get(middleIndex).doubleValue())/2.0;
		}
		return sortedValues.get(middleIndex).doubleValue();
	}

	public static String isDataBlockContinuous(SENSORS sensorClassKey, DataSegmentDetails dataSegmentDetailsPrevious, DataBlockDetails nextDataBlockDetails) {
		//Get last data block from existing dataset
		DataBlockDetails previousDataBlockDetails = dataSegmentDetailsPrevious.getListOfDataBlocks().get(dataSegmentDetailsPrevious.getDataBlockCount()-1);
		
		double[] samplingRateLimits = UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.get(sensorClassKey);
		if(samplingRateLimits==null) {
			return ("WARNING!!! Sampling Rate Limits not set for sensor = " + sensorClassKey);
		}
		return UtilCsvSplitting.isSamplingRateOutsideOfLimits(samplingRateLimits, previousDataBlockDetails, nextDataBlockDetails, sensorClassKey);
	}
	
	private static String freqToStr(double freq) {
		return UtilVerisenseDriver.formatDoubleToNdecimalPlaces(freq, 2) + " " + CHANNEL_UNITS.FREQUENCY;
	}

	private static String timeToStr(double ts) {
		return UtilVerisenseDriver.formatDoubleToNdecimalPlaces(ts, 3) + " " + CHANNEL_UNITS.SECONDS;
	}

}
