package com.shimmerresearch.verisense.payloaddesign;

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
		 * inter-block gap, as a multiple of the CONFIGURED block spacing taken from
		 * the payload header, that is still treated as continuous. A genuinely
		 * dropped block doubles the spacing, so 1.5x leaves 25% of margin against
		 * it while absorbing the jitter that the standard LOWER (-10%) band is far
		 * too tight for.
		 * <p>
		 * <b>This also sets the MLX90632 gap edge, and that edge is pinned against
		 * real data.</b> For skin temp the window is widened again by
		 * {@link #SLOW_SENSOR_CONVERSION_SLIP_TOLERANCE}, so the edge lands at
		 * {@code cfg/1.725} - and Test_065 contains a HEALTHY skin-temp boundary at
		 * 1.63x nominal spacing during start-up settling. Retuning this constant for
		 * the light sensor alone moves the skin-temp edge with it: 1.4 would put it
		 * at 1.61x and re-split that recording. See
		 * {@link UtilCsvSplitting#getSlowSensorPlausibleRateRangeHz} and change it
		 * only against a measurement on both sensors.
		 */
		public static final double SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO = 1.5;
		/**
		 * MLX90632 only: how far a SINGLE block boundary apparent rate may sit
		 * either side of the configured output rate and still be plausible.
		 * <p>
		 * The chip conversions slip by several refresh periods and then catch up
		 * (+12.5% block spacing observed on the DEV-927 validation recording, with
		 * no samples lost), so one boundary reads ~12.5% slow and the next
		 * correspondingly fast. Only the AVERAGE rate is bounded by the configured
		 * one, which is why the configured rate alone is too tight a bound. 1.15
		 * covers the observed slip with a little margin.
		 * <p>
		 * Not used for the VD6283, whose sampling is a plain periodic timer with no
		 * slip-and-catch-up behaviour: a failed read there costs a whole period, it
		 * never shortens one.
		 */
		public static final double SLOW_SENSOR_CONVERSION_SLIP_TOLERANCE = 1.15;
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

	/**
	 * Whether the payload header itself carries this slow sensor configured
	 * sample rate, so that nothing has to be inferred from the data.
	 * <p>
	 * VD6283: true once the firmware stores the rate index in header byte 30
	 * bits 6:3 (FW v2.02.000+). MLX90632: always true for an enabled gen-2
	 * device, because the refresh code has always been in header byte 32.
	 *
	 * @param verisenseDevice the device being parsed
	 * @param slowSensorId the slow sensor data block id
	 * @return true when the configured rate is known from the header alone
	 */
	public static boolean isSlowSensorConfiguredRateKnown(VerisenseDevice verisenseDevice, DATABLOCK_SENSOR_ID slowSensorId) {
		if(slowSensorId==DATABLOCK_SENSOR_ID.LIGHT) {
			SensorVD6283 sensorVd6283 = verisenseDevice.getSensorVD6283();
			return sensorVd6283!=null && sensorVd6283.isConfiguredRateKnown();
		}
		if(slowSensorId==DATABLOCK_SENSOR_ID.SKIN_TEMP) {
			return isRateUsable(verisenseDevice.getSamplingRateForSensor(SENSORS.MLX90632));
		}
		return false;
	}

	private static boolean isRateUsable(double rateHz) {
		return rateHz>0 && !Double.isNaN(rateHz) && !Double.isInfinite(rateHz);
	}

	/**
	 * The {min, max} per-sample rate a slow sensor could legitimately be running
	 * at, which the CSV gap window is then built from.
	 * <p>
	 * When the configured rate is known from the header the range collapses onto
	 * it - exactly for the VD6283, whose timer has no slip behaviour, and widened
	 * by {@link FILE_GAP_TOLERANCE_MULTIPLIER#SLOW_SENSOR_CONVERSION_SLIP_TOLERANCE}
	 * for the MLX90632, whose conversions slip and catch up.
	 * <p>
	 * When it is not known - a light recording from FW earlier than v2.02.000 -
	 * the range is the whole firmware rate table. That is deliberately wide:
	 * [0.5, 20] Hz becomes a [0.33, 22] Hz window, so a 10-sample light block
	 * boundary is accepted anywhere up to 30 s and genuine loss goes unreported.
	 * It is the best that can be done without the rate, it keeps such recordings
	 * in ONE CSV rather than one per block, and it is stateless: nothing is
	 * learned from the data, so no amount of unhealthy data can move it.
	 *
	 * @param verisenseDevice the device being parsed
	 * @param slowSensorId the slow sensor data block id
	 * @return the {min, max} plausible rate in Hz, or null if it cannot be bounded
	 */
	public static double[] getSlowSensorPlausibleRateRangeHz(VerisenseDevice verisenseDevice, DATABLOCK_SENSOR_ID slowSensorId) {
		if(slowSensorId==DATABLOCK_SENSOR_ID.LIGHT) {
			if(isSlowSensorConfiguredRateKnown(verisenseDevice, slowSensorId)) {
				double configuredRateHz = verisenseDevice.getSensorVD6283().getRateFreq();
				if(isRateUsable(configuredRateHz)) {
					return new double[] {configuredRateHz, configuredRateHz};
				}
			}
			return new double[] {SensorVD6283.MIN_SAMPLE_RATE_HZ, SensorVD6283.MAX_SAMPLE_RATE_HZ};
		}
		if(slowSensorId==DATABLOCK_SENSOR_ID.SKIN_TEMP) {
			double configuredRateHz = verisenseDevice.getSamplingRateForSensor(SENSORS.MLX90632);
			if(isSlowSensorConfiguredRateKnown(verisenseDevice, slowSensorId)) {
				// Both sides are widened, and the slow side is the uncomfortable one.
				// It puts the gap edge at cfg/1.725, while a dropped block landing on a
				// 12.5% catch-up presents cfg/1.75 - so that case is reported, but by
				// 1.4%, and it stops being reported at all once a catch-up reaches
				// 13.75%.
				//
				// Narrowing the slow side to cfg/1.5, as the VD6283 uses, was tried and
				// reverted: the Test_065 recording contains a healthy skin-temp boundary
				// at 1.63x nominal spacing during start-up settling, which then split. So
				// this sensor's healthy behaviour genuinely overlaps the region a dropped
				// block would land in, and no single threshold separates them cleanly.
				// 1.725 is the midpoint that was chosen with that data in hand: 6% above
				// the worst healthy boundary observed, 14% below a clean dropped block.
				// Moving it in either direction trades false splits against missed loss,
				// so change it only against a measurement, not an intuition.
				return new double[] {
					configuredRateHz/FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_CONVERSION_SLIP_TOLERANCE,
					configuredRateHz*FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_CONVERSION_SLIP_TOLERANCE};
			}
			// Unreachable today: getRateFreq() is refresh/sub-measurements, always
			// within [0.167, 32], and the sensor class exists whenever skin-temp
			// blocks do. Kept because the caller no longer guarantees either.
			return new double[] {SensorMLX90632.MIN_OUTPUT_RATE_HZ, SensorMLX90632.MAX_OUTPUT_RATE_HZ};
		}
		return null;
	}

	/**
	 * Sets a slow sensor CSV gap-splitting window from the rate the payload
	 * header states, replacing the configured-rate +/-10% band that
	 * {@link #populateExpectedPayloadTsDiffLimitMapIfNeeded} would otherwise
	 * install.
	 * <p>
	 * The gap side is {@code min / SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO} so
	 * healthy jitter stays continuous while a dropped block, which doubles the
	 * spacing and so halves the apparent rate, is reported. The fast side is
	 * {@code max * UPPER}, which catches an overlapping block or a backwards
	 * clock step. Worked through at 1 Hz the window is [0.667, 1.1] Hz: a dropped
	 * block presents 0.5 Hz and splits, a block that took one extra period after
	 * a failed I2C read presents 0.909 Hz and does not.
	 * <p>
	 * The VD6283 gets a clean 25% margin on a dropped block. The MLX90632 does
	 * not, and cannot: its window is {@code [f/1.725, f*1.265]}, so a dropped
	 * block presents {@code 0.5f} and splits comfortably, but a dropped block
	 * landing on the chip's documented 12.5% catch-up presents {@code 0.571f}
	 * against an edge of {@code 0.580f} and splits by 1.4%. That is not slack
	 * left lying around - see {@link #getSlowSensorPlausibleRateRangeHz} for the
	 * measurement that pins the edge where it is.
	 * <p>
	 * Nothing here depends on previously seen data, so unlike a measured window
	 * this cannot be pulled onto a wrong cadence by the very loss it is meant to
	 * detect, and it is correct from the FIRST boundary of a CSV set rather than
	 * after several. The put is unconditional because
	 * populateExpectedPayloadTsDiffLimitMapIfNeeded is containsKey-guarded and
	 * runs later in the parse: this window must win.
	 * <p>
	 * <b>The MLX90632 gap edge is coupled to
	 * {@link FILE_GAP_TOLERANCE_MULTIPLIER#SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO}
	 * and it is pinned against real data.</b> That edge is
	 * {@code cfg / (1.15 x 1.5) = cfg/1.725}, an emergent product of the slip
	 * tolerance and that ratio, and Test_065 contains a healthy skin-temp boundary
	 * at 1.63x nominal spacing. Retuning the ratio for the light sensor alone
	 * moves the skin-temp edge with it: 1.4 would put it at 1.61x and re-split
	 * that recording. Check both sensors, against a measurement.
	 *
	 * @param verisenseDevice the device being parsed
	 * @param slowSensorId the slow sensor data block id
	 */
	public static void seedSlowSensorGapWindow(VerisenseDevice verisenseDevice, DATABLOCK_SENSOR_ID slowSensorId) {
		double[] plausibleRateRangeHz = getSlowSensorPlausibleRateRangeHz(verisenseDevice, slowSensorId);
		if(plausibleRateRangeHz==null) {
			return;
		}
		double[] samplingRateLimits = new double[] {
			plausibleRateRangeHz[0]/FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO,
			plausibleRateRangeHz[1]*FILE_GAP_TOLERANCE_MULTIPLIER.UPPER};
		for(SENSORS sensorClassKey:verisenseDevice.getOrCreateListOfSensorClassKeysForDataBlockId(slowSensorId)) {
			if(sensorClassKey!=SENSORS.CLOCK) {
				SAMPLING_RATE_LIMITS_PER_SENSOR.put(sensorClassKey, samplingRateLimits);
			}
		}
	}

	/**
	 * Set once a file has reported a missing light rate field, so the warning is
	 * one line per recording rather than one per payload. Cleared with the limits
	 * map, which {@code AsmBinaryFileParse} does at the start and end of a file.
	 */
	private static boolean hasWarnedLightRateFieldMissing = false;

	public static void clearMapOfSamplingRateLimitsPerSensor() {
		SAMPLING_RATE_LIMITS_PER_SENSOR.clear();
		hasWarnedLightRateFieldMissing = false;
	}

	/**
	 * Reports, once per file, an ambient-light rate field this parser cannot use:
	 * either a payload claiming firmware new enough to store the index yet leaving
	 * it clear, or any payload holding a reserved index 7..15.
	 * <p>
	 * This is the one failure the header-driven design cannot otherwise see. A
	 * zero field is indistinguishable from an old recording, so the parser falls
	 * back to the wide rate-table window, the light data still comes out, and
	 * nothing anywhere says that the gap detection for this file is nearly blind
	 * - see {@link #getSlowSensorPlausibleRateRangeHz} for how wide that fallback
	 * is. If the firmware ever ships with the field broken, this line is the only
	 * thing that will say so.
	 * <p>
	 * Nothing about parsing keys on the firmware version: the field is
	 * self-describing and {@link SensorVD6283#isConfiguredRateKnown()} alone
	 * decides behaviour. The version is read HERE and nowhere else, purely to tell
	 * "old recording, as expected" apart from "new recording, firmware bug". A
	 * wrong value in {@link VerisenseDevice.FW_CHANGES#CCF_GEN2_LIGHT_RATE} can
	 * therefore only make this warning fire at the wrong boundary; it cannot
	 * change a parse.
	 *
	 * @param verisenseDevice the device being parsed
	 */
	public static void warnIfLightRateFieldUnusable(VerisenseDevice verisenseDevice) {
		if(hasWarnedLightRateFieldMissing) {
			return;
		}
		SensorVD6283 sensorVd6283 = verisenseDevice.getSensorVD6283();
		if(sensorVd6283==null || sensorVd6283.isConfiguredRateKnown()) {
			return;
		}

		// Two different faults, and they must not be reported as one.
		String fault;
		if(sensorVd6283.isRateIndexReserved()) {
			// A value no firmware rate table defines. Report this whatever the
			// payload design claims: on an old recording those bits should be clear,
			// so a non-zero one is an anomaly in its own right and the version tells
			// us nothing useful about it.
			fault = "carries ambient light blocks whose rate field holds the reserved index "
					+ sensorVd6283.getRateIndexRaw() + ", which no firmware rate table defines";
		} else if(verisenseDevice.isPayloadDesignV14orAbove()) {
			fault = "stores the VD6283 sample rate in payload header byte 30 bits 6:3, but this"
					+ " recording carries ambient light blocks with that field clear. This is a"
					+ " firmware fault, not an old recording";
		} else {
			// Old recording, field clear: exactly as expected, and not worth a line.
			return;
		}

		hasWarnedLightRateFieldMissing = true;
		double[] fallbackRangeHz = getSlowSensorPlausibleRateRangeHz(verisenseDevice, DATABLOCK_SENSOR_ID.LIGHT);
		System.out.println("WARNING!!! Firmware " + verisenseDevice.getFirmwareVersionParsed() + " "
				+ fault + ". Falling back to the whole rate table, " + fallbackRangeHz[0] + " to "
				+ fallbackRangeHz[1] + " Hz, so light blocks are timed from the exposure bound"
				+ " and gap detection for this file is close to blind.");
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
