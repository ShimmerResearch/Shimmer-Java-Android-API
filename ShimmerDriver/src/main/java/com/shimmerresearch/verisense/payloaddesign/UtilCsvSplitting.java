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

public class UtilCsvSplitting {

	public static class FILE_GAP_TOLERANCE_MULTIPLIER {
		// +/- 10%
		public static final double UPPER = 1.1;
		public static final double LOWER = 0.9;
		/**
		 * Slow sensors only (VD6283 light / MLX90632 skin temp): the largest
		 * inter-block gap, as a multiple of the achieved median block spacing, that
		 * is still treated as continuous. It sets the SLOW (gap) side of the
		 * sampling-rate window only - the fast side stays on the standard UPPER
		 * (+10%) tolerance, see {@link
		 * UtilCsvSplitting#calculateSlowSensorSamplingRateLimits(double)}.
		 * <p>
		 * The median it is applied to is accumulated across every payload parsed so
		 * far in the current parse run (see
		 * {@link UtilCsvSplitting#refineSlowSensorSamplingRateLimits(SENSORS, List)}),
		 * not re-derived from the handful of inter-block gaps in the payload
		 * currently being judged - a payload only carries 2-3 slow-sensor blocks, so
		 * a per-payload estimate would absorb a dropped block into its own window and
		 * never report it. Against the accumulated median a single 2x outlier barely
		 * moves the centre, so the gap stays outside the window.
		 * <p>
		 * The band still has to be wide because the slow sensors' cadence is
		 * inherently jittery even when no samples are lost: the light's is bimodal
		 * (exposure vs exposure + dead time) and the MLX90632's conversions can slip
		 * by several refresh periods and then catch up (observed up to +12.5% block
		 * spacing on the DEV-927 validation recording with no samples lost), which
		 * routinely violates the standard LOWER (-10%) band. A genuinely dropped
		 * block doubles the spacing (2x), so 1.5x sits comfortably between healthy
		 * jitter and a real gap.
		 */
		public static final double SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO = 1.5;
	}

	/**
	 * The maximum number of slow-sensor per-sample periods kept per sensor in
	 * {@link #SLOW_SENSOR_OBSERVED_PERIODS_PER_SENSOR}. Once full, the oldest
	 * measurements are dropped so that the median follows any genuine long-term
	 * drift in the sensor's cadence while staying deep enough (hundreds of
	 * payloads' worth of inter-block gaps) that individual dropped blocks cannot
	 * shift it.
	 */
	protected static final int SLOW_SENSOR_PERIOD_HISTORY_MAX = 1024;

	protected static HashMap<SENSORS, double[]> SAMPLING_RATE_LIMITS_PER_SENSOR = new HashMap<SENSORS, double[]>();

	/**
	 * Slow-sensor (VD6283 light / MLX90632 skin temp) per-sample periods, in
	 * seconds, as measured from the inter-block tick spacing of every payload
	 * parsed so far in the current parse run. Shares its lifecycle with
	 * {@link #SAMPLING_RATE_LIMITS_PER_SENSOR}: both are cleared together by
	 * {@link #clearMapOfSamplingRateLimitsPerSensor()}, which the file parser calls
	 * on each CSV-set boundary so that measurements never leak from one recording
	 * into the next.
	 */
	protected static HashMap<SENSORS, List<Double>> SLOW_SENSOR_OBSERVED_PERIODS_PER_SENSOR = new HashMap<SENSORS, List<Double>>();

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
	 * Slow-sensor (VD6283 light / MLX90632 skin temp) window either side of the
	 * achieved median rate. Both sides are derived from the SAME robust median so
	 * that neither edge can be dragged around by a single extreme inter-block
	 * spacing: the slow (gap) side tolerates up to
	 * {@link FILE_GAP_TOLERANCE_MULTIPLIER#SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO}
	 * times the median spacing, the fast side the standard
	 * {@link FILE_GAP_TOLERANCE_MULTIPLIER#UPPER} tolerance.
	 *
	 * @param medianRateHz the achieved median sampling rate, in Hz
	 * @return {min, max} sampling rate, in Hz, still treated as continuous
	 */
	public static double[] calculateSlowSensorSamplingRateLimits(double medianRateHz) {
		return new double[] {
				medianRateHz/FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO,
				medianRateHz*FILE_GAP_TOLERANCE_MULTIPLIER.UPPER};
	}

	/**
	 * Median of the supplied values. Unlike a bare {@code get(size/2)} this
	 * averages the two middle values for an even-sized input, and it sorts a copy
	 * so the caller's list ordering is left alone.
	 *
	 * @param values the values to take the median of
	 * @return the median, or {@link Double#NaN} if there are no values
	 */
	public static double calculateMedian(List<Double> values) {
		if(values==null || values.isEmpty()) {
			return Double.NaN;
		}
		List<Double> sortedValues = new ArrayList<Double>(values);
		Collections.sort(sortedValues);
		int size = sortedValues.size();
		if(size%2==0) {
			return (sortedValues.get((size/2)-1) + sortedValues.get(size/2))/2.0;
		}
		return sortedValues.get(size/2);
	}

	/**
	 * Add the per-sample periods measured in the payload just parsed to this
	 * sensor's running history and return the median over EVERYTHING accumulated so
	 * far in the current parse run (not just the latest payload's values).
	 *
	 * @param sensorClassKey the sensor the periods were measured for
	 * @param newlyObservedPeriodsS the per-sample periods, in seconds, measured in
	 *            the payload just parsed (may be empty/null to just read the
	 *            current median back)
	 * @return the accumulated median per-sample period, in seconds, or
	 *         {@link Double#NaN} if nothing has been measured for this sensor yet
	 */
	public static double accumulateSlowSensorPeriodsAndGetMedianPeriodS(SENSORS sensorClassKey, List<Double> newlyObservedPeriodsS) {
		List<Double> accumulatedPeriodsS = SLOW_SENSOR_OBSERVED_PERIODS_PER_SENSOR.get(sensorClassKey);
		if(accumulatedPeriodsS==null) {
			accumulatedPeriodsS = new ArrayList<Double>();
			SLOW_SENSOR_OBSERVED_PERIODS_PER_SENSOR.put(sensorClassKey, accumulatedPeriodsS);
		}
		if(newlyObservedPeriodsS!=null) {
			for(Double periodS:newlyObservedPeriodsS) {
				if(periodS!=null && periodS>0) {
					accumulatedPeriodsS.add(periodS);
				}
			}
		}
		// Bounded history: drop the oldest measurements rather than growing without
		// limit over a multi-day recording.
		int excess = accumulatedPeriodsS.size()-SLOW_SENSOR_PERIOD_HISTORY_MAX;
		if(excess>0) {
			accumulatedPeriodsS.subList(0, excess).clear();
		}
		return calculateMedian(accumulatedPeriodsS);
	}

	/**
	 * Accumulate the slow-sensor per-sample periods measured in the payload just
	 * parsed and (re)apply the resulting CSV gap-splitting window for that sensor.
	 * <p>
	 * The put into {@link #SAMPLING_RATE_LIMITS_PER_SENSOR} is deliberately
	 * UNCONDITIONAL. A payload that carries fewer than two blocks of this sensor
	 * (e.g. the very first payload of a recording) leaves
	 * {@link #populateExpectedPayloadTsDiffLimitMapIfNeeded(VerisenseDevice, HashMap)}
	 * to seed a configured-rate +/-10% band first; the header-derived rates for the
	 * slow sensors are only estimates (the light rate isn't stored at all), so that
	 * band can be far too tight (observed: a 25-min DEV-927 skin-temp recording
	 * fragmented into 7 CSVs). A containsKey guard here would lock that estimate in
	 * for the whole file, so the measured window must win as soon as it exists.
	 *
	 * @param sensorClassKey the sensor the periods were measured for
	 * @param newlyObservedPeriodsS the per-sample periods, in seconds, measured in
	 *            the payload just parsed
	 * @return the accumulated median sampling rate, in Hz, or {@link Double#NaN} if
	 *         nothing has been measured for this sensor yet (in which case the
	 *         limits map is left untouched)
	 */
	public static double refineSlowSensorSamplingRateLimits(SENSORS sensorClassKey, List<Double> newlyObservedPeriodsS) {
		double medianPeriodS = accumulateSlowSensorPeriodsAndGetMedianPeriodS(sensorClassKey, newlyObservedPeriodsS);
		if(!(medianPeriodS>0)) {
			return Double.NaN;
		}
		double medianRateHz = 1.0/medianPeriodS;
		SAMPLING_RATE_LIMITS_PER_SENSOR.put(sensorClassKey, calculateSlowSensorSamplingRateLimits(medianRateHz));
		return medianRateHz;
	}

	public static void clearMapOfSamplingRateLimitsPerSensor() {
		SAMPLING_RATE_LIMITS_PER_SENSOR.clear();
		// Same lifecycle as the limits map itself - the accumulated slow-sensor
		// measurements that the limits are derived from must not survive a CSV-set
		// boundary either.
		SLOW_SENSOR_OBSERVED_PERIODS_PER_SENSOR.clear();
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
