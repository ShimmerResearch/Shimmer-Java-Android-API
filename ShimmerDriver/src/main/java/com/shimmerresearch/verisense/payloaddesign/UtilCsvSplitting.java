package com.shimmerresearch.verisense.payloaddesign;

import java.util.HashMap;
import java.util.List;

import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;
import com.shimmerresearch.verisense.UtilVerisenseDriver;
import com.shimmerresearch.verisense.VerisenseDevice;
import com.shimmerresearch.verisense.payloaddesign.DataBlockDetails.DATABLOCK_SENSOR_ID;

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
		 * spacing on the DEV-927 validation recording with no samples lost), and the
		 * window is seeded from the first payload that carries >= 2 blocks - often a
		 * single inter-block gap, i.e. no spread information - so the standard
		 * LOWER (-10%) band is routinely violated by healthy data. A genuinely
		 * dropped block doubles the spacing (2x), so 1.5x keeps comfortable margin
		 * on both sides.
		 */
		public static final double SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO = 1.5;
	}
	
	protected static HashMap<SENSORS, double[]> SAMPLING_RATE_LIMITS_PER_SENSOR = new HashMap<SENSORS, double[]>(); 

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

	public static void clearMapOfSamplingRateLimitsPerSensor() {
		SAMPLING_RATE_LIMITS_PER_SENSOR.clear();
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
