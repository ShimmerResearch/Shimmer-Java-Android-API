package com.shimmerresearch.verisense.payloaddesign;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;
import com.shimmerresearch.verisense.UtilVerisenseDriver;
import com.shimmerresearch.verisense.VerisenseDevice;
import com.shimmerresearch.verisense.payloaddesign.AsmBinaryFileConstants.BYTE_COUNT;
import com.shimmerresearch.verisense.payloaddesign.DataBlockDetails.DATABLOCK_SENSOR_ID;
import com.shimmerresearch.verisense.sensors.SensorVerisenseClock;

public class PayloadContentsDetailsV8orAbove extends PayloadContentsDetails {
	
	private static final long serialVersionUID = -3062638445721582576L;
	
	private static final boolean RESET_GYRO_ON_THE_FLY_CALIB_DURING_TIME_GAPS = false;
	
	/**
	 * @param verisenseDevice
	 */
	public PayloadContentsDetailsV8orAbove(VerisenseDevice verisenseDevice) {
		super(verisenseDevice);
	}

	@Override
	public void parsePayloadContentsMetaData(int binFileByteIndex) throws IOException {
		int currentByteIndexInPayload = 0;
		int dataBlockIndexInPayload = 0;
		
		while(true) {
			int dataBlockStartByteIndexInFile = binFileByteIndex+currentByteIndexInPayload;

			DataBlockDetails dataBlockDetails;
			try {
				dataBlockDetails = verisenseDevice.parseDataBlockMetaData(byteBuffer, currentByteIndexInPayload, dataBlockStartByteIndexInFile, dataBlockIndexInPayload, getPayloadIndex());
				dataBlockIndexInPayload++;
			} catch (Exception e) {
				printDataBlockMetadataReport();
				throw(e);
			}

//			if (DEBUG_DATA_BLOCKS) {
//				System.out.println(dataBlockDetails.generateDebugStr());
//			}
			
			listOfDataBlocksInOrder.add(dataBlockDetails);
			setOfPayloadSensorIds.add(dataBlockDetails.datablockSensorId);

			// Update byte offset as it's passed by value into "parseDataBlockMetaData"
			// Use the RAW block size for byte-offset arithmetic - qtySensorDataBytesInDatablock
			// can be recomputed by the midday/midnight split logic (wrongly for
			// variable-length blocks such as the LSM6DSV tagged FIFO).
			int dataBlockTotalSize = BYTE_COUNT.PAYLOAD_CONTENTS_GEN8_SENSOR_ID + BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES_TICKS + dataBlockDetails.getQtySensorDataBytesInDatablockRaw();
			currentByteIndexInPayload += dataBlockTotalSize;

			if(isParserAtEndOfBuffer(byteBuffer.length, currentByteIndexInPayload)) {
				break;
			}
		}
		
		long rwcTimeMinutes = VerisenseTimeDetails.parseTimeMinutesAtIndex(byteBuffer, currentByteIndexInPayload);
		currentByteIndexInPayload += BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES_MINUTES;

		long rwcTimeTicks = 0;
		if(verisenseDevice.isPayloadDesignV9orAbove()) {
			rwcTimeTicks = VerisenseTimeDetails.parseTimeTicksAtIndex(byteBuffer, currentByteIndexInPayload);
			currentByteIndexInPayload += BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES_TICKS;
		} else {
			// Use the ticks value from the last data block in the payload. This is
			// appropriate if the payload is full but not if the payload was packaged early
			// and hence the reason why the ticks were added back into the footer in FW
			// v1.02.074.
			rwcTimeTicks = listOfDataBlocksInOrder.get(listOfDataBlocksInOrder.size()-1).getTimeDetailsRwc().getEndTimeTicks();
		}
		
		currentByteIndexInPayload = parseTemperatureBytes(currentByteIndexInPayload);
		currentByteIndexInPayload = parseBatteryVoltageBytes(currentByteIndexInPayload);

		if(verisenseDevice.isPayloadDesignV10orAbove()) {
			currentByteIndexInPayload = parseMicrocontrollerClockBytes(currentByteIndexInPayload);
		}
		
		// --------- End of parsing ------------------

		// Up to, and including, payload design v10, the real-world clock time that was
		// stored in the payload footer was the real-world time at the end of the
		// payload. From payload design v11 onwards, the real-world clock offset is
		// stored instead. Additionally, the microcontroller ticks is stored per
		// datablock instead of the real-world clock ticks/
		if(verisenseDevice.isPayloadDesignV11orAbove()) {
			// 1) back fill microcontroller time values
			backfillDataBlockUcClockTimestamps();
			// 2) Calculate the payload start time based on the microcontroller clock values stored in the earliest recorded data block 
			calculatePayloadStartTimeMsUcClock();
			
			// 3) apply real-world clock offset to all microcontroller clock values to set real-world clock times
			double rwcOffsetMs = SensorVerisenseClock.convertRtcMinutesAndTicksToMs(rwcTimeMinutes, rwcTimeTicks);
			getTimeDetailsRwc().setEndTimeMs(getTimeDetailsUcClock().getEndTimeMs() + rwcOffsetMs);
			applyRwcOffsetToDataBlockRwcClockTimestamps(rwcOffsetMs);
			
			// 4) Calculate the payload start time based on the real-world clock values stored in the earliest recorded data block 
			calculatePayloadStartTimeMsRwc();
		} else {
			// 1) Set the payload real-world clock end time from minutes and ticks stored in the payload footer
			getTimeDetailsRwc().setEndTimeAndCalculateMs(rwcTimeMinutes, rwcTimeTicks);
			// 2) Backfill the data block real-world clock start and end times
			backfillDataBlockRwcTimestamps();
			// 3) Calculate the payload start time based on the real-world clock values stored in the earliest recorded data block 
			calculatePayloadStartTimeMsRwc();
			
			// The microcontroller time (a.k.a. time since boot or uC time) was added in
			// payload design v10 onwards to help with RTC recovery. This section handles
			// back-filling the start and end times for the microcontoller clock values
			// stored in each of the datablocks
			if(verisenseDevice.isPayloadDesignV10orAbove()) {
				// Use the payload duration (as previously calculated from the RWC time) to calculate the microcontroller start time.
				VerisenseTimeDetails timeDetailsUcClock = getTimeDetailsUcClock();
				timeDetailsUcClock.setStartTimeMs(timeDetailsUcClock.getEndTimeMs() - calculatePayloadDurationMs());
	
				// Calculate the RWC offset that would be stored in the sensor when it's RTC has been set
				double rwcOffsetMs = getTimeDetailsRwc().getEndTimeMs() - getTimeDetailsUcClock().getEndTimeMs();
				applyRwcOffsetToDataBlockUcClockTimestamps(rwcOffsetMs);
			}
		}

		// If midday/midnight transition detected within a payload, dive down deeper to find out where it is
		if(SPLIT_CSVS_AT_MIDDAY_AND_MIDNIGHT && UtilVerisenseDriver.isTransitionMidDayOrMidnight(getStartTimeRwcMs(), getEndTimeRwcMs())) {
			splitDataBlocksAtMiddayMidnight(listOfDataBlocksInOrder, verisenseDevice.getMapOfSensorIdsPerDataBlock().keySet());
		}

		UtilCsvSplitting.populateExpectedPayloadTsDiffLimitMapIfNeeded(verisenseDevice, verisenseDevice.getMapOfSensorIdsPerDataBlock());
		calculateAndSetPayloadPackagingDelayMs();
	}

	private void printDataBlockMetadataReport() {
		System.err.println("\nDataBlockDetails = null while parsing Metadata, stopping parsing early");
		
		System.err.println("Parsing History for payload index " + getPayloadIndex() + "\n");

		System.err.println("dataBlockStartByteIndexInPayload, dataBlockStartByteIndexInFile, dataBlockIndexInPayload, datablockSensorId, dataBlockSize, endTimeTicks");
		for (DataBlockDetails dataBlockDetails : listOfDataBlocksInOrder) {
			long dataBlockEndTimeTicks = verisenseDevice.isPayloadDesignV11orAbove()? dataBlockDetails.getTimeDetailsUcClock().getEndTimeTicks():dataBlockDetails.getTimeDetailsRwc().getEndTimeTicks();
					
			System.err.println(dataBlockDetails.dataBlockStartByteIndexInPayload + "/" + byteBuffer.length
					+ ",\t" + UtilShimmer.intToHexStringFormatted(dataBlockDetails.dataBlockStartByteIndexInFile, 4, true)
					+ ",\t" + dataBlockDetails.getDataBlockIndexInPayload()
					+ ",\t" + dataBlockDetails.datablockSensorId + " " + UtilShimmer.byteToHexStringFormatted((byte) dataBlockDetails.datablockSensorId.ordinal())
					+ ",\t" + dataBlockDetails.qtySensorDataBytesInDatablock
					+ ",\t" + dataBlockEndTimeTicks);
		}
		System.err.println("End of report");
	}

	protected int parseMicrocontrollerClockBytes(int currentByteIndex) {
		long ucEndTimeMinutes = VerisenseTimeDetails.parseTimeMinutesAtIndex(byteBuffer, currentByteIndex);
		currentByteIndex += BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES_MINUTES;
		long ucEndTimeTicks = VerisenseTimeDetails.parseTimeTicksAtIndex(byteBuffer, currentByteIndex);
		currentByteIndex += BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES_TICKS;
		
		getTimeDetailsUcClock().setEndTimeAndCalculateMs(ucEndTimeMinutes, ucEndTimeTicks);
		
		return currentByteIndex;
	}

	private void splitDataBlocksAtMiddayMidnight(List<DataBlockDetails> listOfDataBlocks, Set<DATABLOCK_SENSOR_ID> setOfSensorIds) {
		boolean aDataBlockWasSplitSampleBySample = false;
		for(DATABLOCK_SENSOR_ID datablockSensorId:setOfSensorIds) {
			int dataBlockIndex = 0;
			
			// Need to loop through all data blocks because there could be a midday/midnight transition for each sensor
			ListIterator<DataBlockDetails> iterator = listOfDataBlocks.listIterator();
			while(iterator.hasNext()) {
				DataBlockDetails dataBlockDetails = iterator.next();
				if(dataBlockDetails.datablockSensorId==datablockSensorId) {
					double startTimeMs = dataBlockDetails.getStartTimeRwcMs();
					double endTimeMsToCheck;

					DataBlockDetails dataBlockDetailsNext = searchForNextDatablockForDataBlockId(listOfDataBlocks, dataBlockIndex+1, datablockSensorId);
					
					if(dataBlockDetailsNext==null) {
						// check for sample-by-sample transition points within the last datablock
						endTimeMsToCheck = dataBlockDetails.getEndTimeRwcMs();
					} else {
						// Detect if a transition point between datablocks and then focus in on sample-by-sample transition point within the datablock
						endTimeMsToCheck = dataBlockDetailsNext.getStartTimeRwcMs();
					}

					if(UtilVerisenseDriver.isTransitionMidDayOrMidnight(startTimeMs, endTimeMsToCheck)) {
						DataBlockDetails dataBlockDetailsSplit = checkAndSplitIndividualDataBlock(dataBlockDetails, dataBlockIndex);
						if(dataBlockDetailsSplit!=null) {
							iterator.add(dataBlockDetailsSplit);
							aDataBlockWasSplitSampleBySample = true;
						} else {
							// Midday/Midnight transition was not detected within a datablock, therefore it must be between two datablocks themselves.
							if(dataBlockDetailsNext!=null) {
								dataBlockDetailsNext.setFirstUnsplitDataBlockAfterMiddayMidnightTransition();
							}
						}
					}
				}
				dataBlockIndex++;
			}
			
		}
		
		//Update the data block index number (purely for console prints)
		if(aDataBlockWasSplitSampleBySample) {
			for(int i=0;i<listOfDataBlocks.size();i++) {
				listOfDataBlocks.get(i).setDataBlockIndexInPayload(i);
			}
		}
		
	}

	private DataBlockDetails searchForNextDatablockForDataBlockId(List<DataBlockDetails> listOfDataBlocks,
			int startSearchIndex, DATABLOCK_SENSOR_ID datablockSensorId) {
		for(int i=startSearchIndex;i<listOfDataBlocks.size();i++) {
			DataBlockDetails nextDataBlock = listOfDataBlocks.get(i);
			if(nextDataBlock.datablockSensorId==datablockSensorId) {
				return nextDataBlock;
			}
		}
		return null;
	}

	/**
	 * The purpose of this method is to split data blocks when a midday/midnight
	 * transition is detected within them. This is the cleanest way to do it so that
	 * later on in the processing, when the data blocks are sorted into to
	 * DataSegments, a split data block with midday/midnight transition between them
	 * will result in two separate datasegments being created.
	 * 
	 * @param listOfDataBlocks
	 * @param dataBlockIndex
	 */
	private DataBlockDetails checkAndSplitIndividualDataBlock(DataBlockDetails dataBlockDetailsOriginal, int dataBlockIndex) {
		
		// Note, we have to calculate the timestamps here because the ObjectCluster
		// arrays haven't been populated yet in this stage of the file parser flow -
		// otherwise using those calculated values would be more efficient.
		double timestampDiffMs = dataBlockDetailsOriginal.getTimestampDiffInS()*1000;
		
		double timestampMsCurrentRwc = dataBlockDetailsOriginal.getTimeDetailsRwc().getStartTimeMs();
		double timestampMsNextRwc = timestampMsCurrentRwc + timestampDiffMs;

		double timestampMsCurrentUcClock = Double.NaN, timestampMsNextUcClock = Double.NaN;
		if(verisenseDevice.isPayloadDesignV10orAbove()) {
			timestampMsCurrentUcClock = dataBlockDetailsOriginal.getTimeDetailsUcClock().getStartTimeMs();
			timestampMsNextUcClock = timestampMsCurrentUcClock + timestampDiffMs;
		}

		for(int sampleIndex=0;sampleIndex<dataBlockDetailsOriginal.getSampleCount()-1;sampleIndex++) {
			
			System.out.println("Checking..." + UtilVerisenseDriver.convertMilliSecondsToCsvHeaderFormat((long) timestampMsCurrentRwc) + "\tvs.\t" + UtilVerisenseDriver.convertMilliSecondsToCsvHeaderFormat((long) timestampMsNextRwc));
			
			if(UtilVerisenseDriver.isTransitionMidDayOrMidnight(timestampMsCurrentRwc, timestampMsNextRwc)) {
				System.out.println("Midday/Midnight transition detected within data block for Sensor=" + dataBlockDetailsOriginal.listOfSensorClassKeys + ", DataBlockIndex = " + dataBlockIndex + ", SampleIndex=" + sampleIndex
						+ ", Timing [CurrentSample=" + UtilVerisenseDriver.convertMilliSecondsToCsvHeaderFormat((long) timestampMsCurrentRwc)
						+ ", NextSample=" + UtilVerisenseDriver.convertMilliSecondsToCsvHeaderFormat((long) timestampMsNextRwc) + "]");
				System.out.println("  |_Splitting DataBlock:");
				
				DataBlockDetails dataBlockDetailsSplit = dataBlockDetailsOriginal.deepClone();

				System.out.println("    |_Original=" + dataBlockDetailsOriginal.generateDebugStr());

				dataBlockDetailsOriginal.splitAndEndBeforeSampleIndex(sampleIndex+1, timestampMsCurrentRwc, timestampMsCurrentUcClock);
				dataBlockDetailsSplit.splitAndStartAtSampleIndex(sampleIndex+1, timestampMsNextRwc, timestampMsNextUcClock);
				
				System.out.println("    |_Split1=" + dataBlockDetailsOriginal.generateDebugStr());
				System.out.println("    |_Split2=" + dataBlockDetailsSplit.generateDebugStr());
				
				// Safe to assume one midday/midnight transition per data block so return
				return dataBlockDetailsSplit;
			}
			
			// Increment for next loop
			timestampMsCurrentRwc += timestampDiffMs;
			timestampMsNextRwc += timestampDiffMs;
			if(verisenseDevice.isPayloadDesignV10orAbove()) {
				timestampMsCurrentUcClock += timestampDiffMs;
				timestampMsNextUcClock += timestampDiffMs;
			}
		}
		return null;
	}

	private boolean isParserAtEndOfBuffer(int bufferLength, int currentByteIndex) {
		int footerLength = 0;
		if(verisenseDevice.isPayloadDesignV10orAbove()) {
			footerLength = BYTE_COUNT.PAYLOAD_CONTENTS_FOOTER_GEN10_OR_ABOVE;
		} else  if(verisenseDevice.isPayloadDesignV9orAbove()) {
			footerLength = BYTE_COUNT.PAYLOAD_CONTENTS_FOOTER_GEN1_TO_GEN7_AND_GEN9;
		} else {
			footerLength = BYTE_COUNT.PAYLOAD_CONTENTS_FOOTER_GEN8_ONLY;
		}
		return (bufferLength-currentByteIndex)<(footerLength+BYTE_COUNT.PAYLOAD_CRC);
	}

	private void backfillDataBlockRwcTimestamps() {
		backfillDataBlockUcClockOrRwcTimestamps(false);
	}

	private void backfillDataBlockUcClockTimestamps() {
		backfillDataBlockUcClockOrRwcTimestamps(true);
	}

	/**
	 * Each data block only contains 3 byte clock ticks value (i.e. the timestamp of
	 * the last sample in the datablock). At the end of the payload there is 3 bytes
	 * clock ticks value as well as a 4 byte minute counter (i.e., the timestamp
	 * when the payload was packaged). Once the payload timestamp has been parsed,
	 * we need to back fill through the datablocks within the payload to calculate
	 * what their minute values would have been so that we can calculate a
	 * real-world-clock time in milliseconds for each data block.
	 */
	private void backfillDataBlockUcClockOrRwcTimestamps(boolean backfilUcClock) {
		VerisenseTimeDetails payloadTimeDetails = backfilUcClock? getTimeDetailsUcClock():getTimeDetailsRwc();
		long payloadEndTimeMinutes = payloadTimeDetails.getEndTimeMinutes();
		long payloadEndTimeTicks = payloadTimeDetails.getEndTimeTicks();

		//Set minutes for last data block
		DataBlockDetails lastDataBlock = listOfDataBlocksInOrder.get(listOfDataBlocksInOrder.size()-1);
		if(verisenseDevice.isPayloadDesignV9orAbove()) {
			long lastDataBlockMinutes = payloadEndTimeMinutes;
			VerisenseTimeDetails dataBlockTimeDetails = backfilUcClock? lastDataBlock.getTimeDetailsUcClock():lastDataBlock.getTimeDetailsRwc();
			
			if(payloadEndTimeTicks<dataBlockTimeDetails.getEndTimeTicks()) {
				lastDataBlockMinutes--;
			}
			if(backfilUcClock) {
				lastDataBlock.setUcClockEndTimeMinutesAndCalculateTimings(lastDataBlockMinutes);
			} else {
				lastDataBlock.setRwcEndTimeMinutesAndCalculateTimings(lastDataBlockMinutes);
			}
		} else {
			// no need to check for UcClock vs. RWC here as payload designs <v9 don't have the capability of storing UcClock
			lastDataBlock.setRwcEndTimeMinutesAndCalculateTimings(payloadEndTimeMinutes);
		}

		// Back-fill minute values for all data blocks
		if(listOfDataBlocksInOrder.size()>1) {
			for (int i = listOfDataBlocksInOrder.size() - 2; i >= 0; i--) {
				DataBlockDetails currentDataBlock = listOfDataBlocksInOrder.get(i);
				DataBlockDetails subsequentDataBlock = listOfDataBlocksInOrder.get(i+1);
				currentDataBlock.setUcClockOrRwcEndTimeMinutesFromSubsequentDataBlock(subsequentDataBlock, backfilUcClock);
			}
		}
	}
	
	private void applyRwcOffsetToDataBlockUcClockTimestamps(double rwcOffsetMs) {
		// Back-fill milliseconds values for all data blocks 
		for(DataBlockDetails dataBlockDetails : listOfDataBlocksInOrder) {
			VerisenseTimeDetails dataBlockUcTimeDetailsUcClock = dataBlockDetails.getTimeDetailsUcClock();
			VerisenseTimeDetails dataBlockUcTimeDetailsRwcClock = dataBlockDetails.getTimeDetailsRwc();
			
			dataBlockUcTimeDetailsUcClock.setStartTimeMs(dataBlockUcTimeDetailsRwcClock.getStartTimeMs()-rwcOffsetMs);
			dataBlockUcTimeDetailsUcClock.setEndTimeMs(dataBlockUcTimeDetailsRwcClock.getEndTimeMs()-rwcOffsetMs);
		}
	}

	private void applyRwcOffsetToDataBlockRwcClockTimestamps(double rwcOffsetMs) {
		// Back-fill milliseconds values for all data blocks 
		for(DataBlockDetails dataBlockDetails : listOfDataBlocksInOrder) {
			VerisenseTimeDetails dataBlockUcTimeDetailsUcClock = dataBlockDetails.getTimeDetailsUcClock();
			VerisenseTimeDetails dataBlockUcTimeDetailsRwcClock = dataBlockDetails.getTimeDetailsRwc();
			
			dataBlockUcTimeDetailsRwcClock.setStartTimeMs(dataBlockUcTimeDetailsUcClock.getStartTimeMs()+rwcOffsetMs);
			dataBlockUcTimeDetailsRwcClock.setEndTimeMs(dataBlockUcTimeDetailsUcClock.getEndTimeMs()+rwcOffsetMs);
		}
	}

	@Override
	public void parsePayloadSensorData() {
		for (SENSORS sensorClassKey : datasetToSave.getMapOfDataSegmentsPerSensor().keySet()) {
			int dataBlockIndex = 0;
			int currentByteIndex = 0;
			while(true) {
				if(dataBlockIndex>=listOfDataBlocksInOrder.size() || listOfDataBlocksInOrder.get(dataBlockIndex)==null) {
					System.err.println("DataBlockDetails = null while parsing sensor data, stopping parsing early");
					break;
				}
				DataBlockDetails dataBlockDetails = listOfDataBlocksInOrder.get(dataBlockIndex); 
				
				//Reset algorithms associated with the sensor class key if a time gap/overlap is detected
				if(dataBlockDetails.isFirstDataBlockAfterSplitBySampleDueToTimeGapOrOverlap()) {
					// We've chosen not to reset the gyro-on-the-fly for time gaps/overlaps in order
					// to carry the calibration parameters forward. This is effectively the same
					// thing that would be done when the file parser starts, the last previous gyro
					// calibration is loaded.
					if(sensorClassKey!=SENSORS.LSM6DS3 || RESET_GYRO_ON_THE_FLY_CALIB_DURING_TIME_GAPS) {
						verisenseDevice.resetAlgorithmBuffersAssociatedWithSensor(sensorClassKey);
					}
				}
				
				// Added offset for sensor ID byte and 3 bytes RTC ticks (as long as it's not the second half of a split datablock)
				if(!dataBlockDetails.isSecondPartOfSplitDataBlock()) {
					currentByteIndex += BYTE_COUNT.PAYLOAD_CONTENTS_GEN8_SENSOR_ID + BYTE_COUNT.PAYLOAD_CONTENTS_RTC_BYTES_TICKS;
				}

				if(dataBlockDetails.listOfSensorClassKeys.contains(sensorClassKey)) {
					verisenseDevice.parseDataBlockData(dataBlockDetails, byteBuffer, currentByteIndex, COMMUNICATION_TYPE.SD);
				}
				// RAW size: immune to midday/midnight split recomputation (see above)
				currentByteIndex += dataBlockDetails.getQtySensorDataBytesInDatablockRaw();
				
				dataBlockIndex++;
				
				if(isParserAtEndOfBuffer(byteBuffer.length, currentByteIndex)) {
					break;
				}
			}
		}
		
	}

	public static List<SENSORS> getListOfSensorsNotInNewPayload(TreeMap<SENSORS, List<DataSegmentDetails>> previousMapOfDataBlocks,
			TreeMap<SENSORS, List<DataSegmentDetails>> currentMapOfDataBlocks) {
		List<SENSORS> listOfSensorsNotInPayload = new ArrayList<SENSORS>();
		for (SENSORS sensorClassKey : previousMapOfDataBlocks.keySet()) {
			if(!currentMapOfDataBlocks.containsKey(sensorClassKey)) {
				listOfSensorsNotInPayload.add(sensorClassKey);
			}
		}
		return listOfSensorsNotInPayload;
	}

	public static List<SENSORS> getListOfSensorsWithTimeGapBetweenPayloads(TreeMap<SENSORS, List<DataSegmentDetails>> previousMapOfDataBlocks, TreeMap<SENSORS, List<DataSegmentDetails>> currentMapOfDataBlocks) {
		List<SENSORS> listOfSensorsWithTimeGaps = new ArrayList<SENSORS>();
		for (SENSORS sensorClassKey : currentMapOfDataBlocks.keySet()) {
			if(!isContinuityBetweenPrevAndCurrentPayloadsForSensor(sensorClassKey, previousMapOfDataBlocks, currentMapOfDataBlocks)) {
				listOfSensorsWithTimeGaps.add(sensorClassKey);
			}
		}
		return listOfSensorsWithTimeGaps;
	}

	public static boolean isContinuityBetweenPrevAndCurrentPayloadsForSensor(SENSORS sensorClassKey,
			TreeMap<SENSORS, List<DataSegmentDetails>> previousMapOfDataSegments, 
			TreeMap<SENSORS, List<DataSegmentDetails>> currentMapOfDataSegments) {
		
		List<DataSegmentDetails> previousDataSegmentList = previousMapOfDataSegments.get(sensorClassKey);
		if(previousDataSegmentList==null) {
			System.out.println("CSV Split logic - sensor not present in previous payload = " + sensorClassKey);
			return false;
		}

		List<DataSegmentDetails> currentDataSegment = currentMapOfDataSegments.get(sensorClassKey);
		if(currentDataSegment==null) {
			System.out.println("CSV Split logic - sensor not present in new payload = " + sensorClassKey);
			return false;
		}
		
		//Get last data segment from existing dataset
		DataSegmentDetails dataSegmentDetailsPrevious = previousDataSegmentList.get(previousDataSegmentList.size()-1);
		
		//Get first data block from new dataset
		List<DataBlockDetails> currentListOfDataBlocks = currentDataSegment.get(0).getListOfDataBlocks();
		DataBlockDetails nextDataBlockDetails = currentListOfDataBlocks.get(0);
		
		String result = UtilCsvSplitting.isDataBlockContinuous(sensorClassKey, dataSegmentDetailsPrevious, nextDataBlockDetails);
		if(!result.isEmpty()) {
			System.out.println("CSV Split logic - unexpected time gap between payloads for sensor = " + sensorClassKey);
			System.out.println(result);
			return false;
		}
		return true;
	}
	
	public List<SENSORS> getListOfSensorsWithTimeGapsWithinThePayload() {
		List<SENSORS> listOfSensorsWithTimeGaps = new ArrayList<SENSORS>();
		for (Entry<SENSORS, List<DataSegmentDetails>> entry : getMapOfDataSegmentsPerSensor().entrySet()) {
			if(entry.getValue().size()>1) {
				listOfSensorsWithTimeGaps.add(entry.getKey());
			}
		}
		return listOfSensorsWithTimeGaps;
	}

}
