package com.shimmerresearch.verisense.payloaddesign;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import com.shimmerresearch.verisense.sensors.SensorMLX90632;
import com.shimmerresearch.verisense.sensors.SensorVD6283;
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

		// The slow sensors' achieved sample rates are not what the payload header
		// can tell us: the VD6283's configured rate index is not stored at all (the
		// header only yields the exposure-derived upper bound, up to 10x the truth)
		// and the MLX90632's output cadence is refresh-code derived and approximate.
		// Refine them from the data itself - across payloads - before the block
		// timings are back-filled below, and derive the CSV gap window with them.
		refineSlowSensorSamplingRateFromBlockTicks(DATABLOCK_SENSOR_ID.LIGHT, SensorVD6283.NUM_SAMPLES_PER_BLOCK);
		refineSlowSensorSamplingRateFromBlockTicks(DATABLOCK_SENSOR_ID.SKIN_TEMP, SensorMLX90632.NUM_SAMPLES_PER_BLOCK);

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

	/**
	 * Refine a slow sensor's achieved per-sample period from the data and
	 * (re)derive its CSV gap-splitting window, choosing between the cross-payload
	 * treatment DEV-979 added and the per-payload one that came before it.
	 * <p>
	 * The choice is made by
	 * {@link UtilCsvSplitting#isSlowSensorSpanUnambiguousAcrossPayloads(DATABLOCK_SENSOR_ID, int)}
	 * and it is not a preference: a block's stored end time is a counter that
	 * wraps every minute, so measuring across a payload boundary is only
	 * recoverable while the sensor's largest legitimate block span is under a
	 * minute. The VD6283 qualifies (a 10-sample block spans at most 20 s) and
	 * needs it, because its configured rate is not in the payload at all. The
	 * MLX90632 does not qualify (a 16-sample block spans up to 64 s) and does not
	 * need it, because its refresh code is in the payload - so it keeps the
	 * pre-DEV-979 code path verbatim.
	 * 
	 * @param slowSensorId the slow sensor's data block id
	 * @param samplesPerBlock the sensor's fixed samples per block
	 */
	void refineSlowSensorSamplingRateFromBlockTicks(DATABLOCK_SENSOR_ID slowSensorId, int samplesPerBlock) {
		if(UtilCsvSplitting.isSlowSensorSpanUnambiguousAcrossPayloads(slowSensorId, samplesPerBlock)) {
			refineSlowSensorSamplingRateAcrossPayloads(slowSensorId, samplesPerBlock);
		} else {
			refineSlowSensorSamplingRatePerPayload(slowSensorId);
		}
	}

	/**
	 * Refine a slow sensor's achieved
	 * per-sample period from the spacing of consecutive same-sensor block end
	 * ticks, apply it to those blocks before their timings are back-filled, and
	 * (re)derive the sensor's CSV gap-splitting window from the same measurements.
	 * <p>
	 * Each block holds a fixed number of samples and is stamped with the time of
	 * its LAST sample, so {@code inter-block ticks / samples-per-block} is the
	 * achieved per-sample period - the technique the storage-format spec
	 * prescribes for the LSM6DSV. It is the only way to recover the VD6283's rate:
	 * the configured rate index is operational-config byte 75 and is NOT copied
	 * into the payload header, so the header-derived value the blocks are created
	 * with is merely the exposure-limited UPPER BOUND (see
	 * {@link com.shimmerresearch.verisense.sensors.SensorVD6283#getRateFreq()}).
	 * With the firmware's default 1 Hz rate and the default 100 ms exposure that
	 * bound is ten times the truth, which compressed every 10-sample block into
	 * 0.9 s of the 10 s it actually spans and left the remaining 9.1 s looking
	 * like a gap - splitting the CSV on every single block (DEV-979).
	 * <p>
	 * DEV-979 also made the measurement work ACROSS payloads. A 1 Hz light block
	 * spans 10 s while a payload spans ~2 s, so a payload carries at most one
	 * light block and there is no inter-block gap inside it to measure; the
	 * previous payload's last block end ticks are therefore carried forward in
	 * {@link UtilCsvSplitting#SLOW_SENSOR_LAST_BLOCK_END_TICKS} (cleared with the
	 * rest of the splitting state whenever a CSV set is written out). That is only
	 * sound while the largest span the sensor could legitimately have is under the
	 * one-minute wrap of the sub-minute tick counter, which
	 * {@code isSlowSensorSpanUnambiguousAcrossPayloads} checks: a 10-sample light
	 * block spans at most 20 s (the firmware's slowest rate is 0.5 Hz) and is
	 * always safe, whereas a 16-sample skin-temp block at 0.25 Hz spans 64 s and
	 * would be ambiguous - so the skin temp keeps measuring within a payload only,
	 * which costs it nothing because its refresh code IS stored in the payload and
	 * its header-derived rate is already correct.
	 * <p>
	 * RESIDUAL, not fixed here: the FIRST block of each CSV set is timed before
	 * any spacing has been observed, so it keeps the header-derived estimate. Its
	 * samples are laid out over {@code (N-1) x estimatedPeriod} instead of
	 * {@code (N-1) x truePeriod}, which for a 10-sample light block at the default
	 * exposure puts the block's - and therefore the CSV header's - reported start
	 * time 8.1 s late at 1 Hz. Re-timing it would mean revisiting the block after
	 * the next one arrives, by which point the file parser has deep-cloned it into
	 * the CSV dataset it is accumulating, so the fix does not belong in the driver.
	 * The sample VALUES and the block end times are unaffected.
	 * 
	 * @param slowSensorId the slow sensor's data block id
	 * @param samplesPerBlock the sensor's fixed samples per block
	 */
	void refineSlowSensorSamplingRateAcrossPayloads(DATABLOCK_SENSOR_ID slowSensorId, int samplesPerBlock) {
		// Only WHOLE blocks may be measured. splitDataBlocksAtMiddayMidnight runs
		// after this method, so nothing is split yet in the normal flow, but a half
		// block would read as an extra boundary a fraction of a second wide carrying
		// a reduced sample count. A split block is therefore measured on its SECOND
		// part - which keeps the original block's end ticks, splitAndStartAtSampleIndex
		// only moves the start - with the two parts' sample counts added back
		// together. (Measuring a recombined block instead would lose the ticks:
		// recombineDataBlockDetailsForContinuityCheck only carries the RWC
		// millisecond times across, which is all a continuity check needs.)
		List<DataBlockDetails> slowSensorBlocks = new ArrayList<DataBlockDetails>();
		List<Integer> wholeBlockSampleCounts = new ArrayList<Integer>();
		int pendingFirstPartSampleCount = 0;
		for(DataBlockDetails dataBlockDetails:listOfDataBlocksInOrder) {
			if(dataBlockDetails.datablockSensorId!=slowSensorId) {
				continue;
			}
			if(dataBlockDetails.isFirstPartOfSplitDataBlock()) {
				pendingFirstPartSampleCount = dataBlockDetails.getSampleCount();
				continue;
			}
			slowSensorBlocks.add(dataBlockDetails);
			wholeBlockSampleCounts.add(Integer.valueOf(dataBlockDetails.getSampleCount()+pendingFirstPartSampleCount));
			pendingFirstPartSampleCount = 0;
		}
		if(slowSensorBlocks.isEmpty()) {
			return;
		}

		// v11+ payloads store microcontroller-clock ticks per block, earlier designs
		// store real-world-clock ticks; either works as only deltas are used.
		boolean useUcClockTicks = verisenseDevice.isPayloadDesignV11orAbove();

		// This method runs before PayloadContentsDetails sorts the payload by
		// continuity, so listOfDataBlocksInOrder is still in file - i.e. temporal -
		// order and the last block of the sensor is genuinely its latest.
		Long previousBlockEndTicks = UtilCsvSplitting.SLOW_SENSOR_LAST_BLOCK_END_TICKS.get(slowSensorId);
		double medianPeriodS = Double.NaN;
		for(int i=0;i<slowSensorBlocks.size();i++) {
			DataBlockDetails dataBlockDetails = slowSensorBlocks.get(i);
			VerisenseTimeDetails blockTimeDetails = useUcClockTicks? dataBlockDetails.getTimeDetailsUcClock():dataBlockDetails.getTimeDetailsRwc();
			long blockEndTicks = blockTimeDetails.getEndTimeTicks();
			int sampleCount = wholeBlockSampleCounts.get(i).intValue();
			if(previousBlockEndTicks!=null && sampleCount>0) {
				// The per-block ticks are a SUB-MINUTE counter (resets at
				// TICKS_PER_MINUTE, 32768 Hz x 60 s - the same semantics the
				// minute-rollover logic in backfillDataBlockUcClockOrRwcTimestamps
				// depends on), so a minute-boundary crossing shows as a negative delta
				// that must be re-based by one minute - NOT wrapped at 2^24.
				long deltaTicks = blockEndTicks - previousBlockEndTicks.longValue();
				if(deltaTicks<0) {
					deltaTicks += (long) AsmBinaryFileConstants.TICKS_PER_MINUTE;
				}
				if(deltaTicks>0) {
					medianPeriodS = UtilCsvSplitting.recordAndGetSlowSensorPeriodS(verisenseDevice, slowSensorId, (deltaTicks/AsmBinaryFileConstants.TICKS_PER_SECOND)/sampleCount);
				}
			}
			previousBlockEndTicks = Long.valueOf(blockEndTicks);
		}
		UtilCsvSplitting.SLOW_SENSOR_LAST_BLOCK_END_TICKS.put(slowSensorId, previousBlockEndTicks);

		if(Double.isNaN(medianPeriodS)) {
			// Nothing measured yet, so re-read the running estimate: a payload that
			// carries a block but completes no boundary (the first of a CSV set, or a
			// skin-temp payload with a single block) must still be timed with whatever
			// has been learned so far rather than falling back to the header estimate.
			medianPeriodS = UtilCsvSplitting.recordAndGetSlowSensorPeriodS(verisenseDevice, slowSensorId, Double.NaN);
		}
		// Refuse to APPLY an implausible median as well as to record one: a gap of
		// 60-70 s aliases through the sub-minute tick counter into an ordinary-looking
		// period, and at the start of a CSV set one such value can be the whole
		// history. Leaving the header estimate in place is the safer failure.
		if(UtilCsvSplitting.isSlowSensorPeriodPlausible(verisenseDevice, slowSensorId, medianPeriodS)) {
			double achievedRateHz = 1.0/medianPeriodS;
			for(DataBlockDetails dataBlockDetails:listOfDataBlocksInOrder) {
				if(dataBlockDetails.datablockSensorId==slowSensorId) {
					dataBlockDetails.setSamplingRate(achievedRateHz);
					dataBlockDetails.calculateTimestampDiffInS();
				}
			}
		}

		UtilCsvSplitting.refineSlowSensorGapWindow(verisenseDevice, slowSensorId, samplesPerBlock);
	}

	/**
	 * The PER-PAYLOAD refinement as it stood before DEV-979, kept VERBATIM (body
	 * unchanged from master 6d27fb2, including the {@code size()/2} upper-middle
	 * median and the early return below two blocks) for every slow sensor that
	 * fails
	 * {@link UtilCsvSplitting#isSlowSensorSpanUnambiguousAcrossPayloads(DATABLOCK_SENSOR_ID, int)}
	 * - i.e. the MLX90632, whose 16-sample block can span 64 s at its slowest
	 * output rate and whose sub-minute tick delta across a payload boundary would
	 * therefore be ambiguous.
	 * <p>
	 * Byte-identity for the skin temp holds BY CONSTRUCTION this way: the applied
	 * period is still this payload's own median, not a whole-file one, and the
	 * window is still seeded with the old formula. The DEV-927 reference CSVs
	 * (ASM_PC Test_065) cannot be reached from this environment, so nothing about
	 * that sensor's timing is changed on trust.
	 * <p>
	 * The MLX90632 also does not need the cross-payload treatment: its refresh
	 * code IS stored in the payload header, so its header-derived rate is already
	 * correct, which is exactly what the VD6283's is not.
	 * 
	 * @param slowSensorId the slow sensor's data block id
	 */
	void refineSlowSensorSamplingRatePerPayload(DATABLOCK_SENSOR_ID slowSensorId) {
		List<DataBlockDetails> slowSensorBlocks = new ArrayList<DataBlockDetails>();
		for(DataBlockDetails dataBlockDetails:listOfDataBlocksInOrder) {
			if(dataBlockDetails.datablockSensorId==slowSensorId) {
				slowSensorBlocks.add(dataBlockDetails);
			}
		}
		if(slowSensorBlocks.size()<2) {
			return;
		}

		// v11+ payloads store microcontroller-clock ticks per block, earlier designs
		// store real-world-clock ticks; either works as only deltas are used.
		boolean useUcClockTicks = verisenseDevice.isPayloadDesignV11orAbove();
		List<Double> perSamplePeriodsS = new ArrayList<Double>();
		for(int i=1;i<slowSensorBlocks.size();i++) {
			VerisenseTimeDetails prev = useUcClockTicks? slowSensorBlocks.get(i-1).getTimeDetailsUcClock():slowSensorBlocks.get(i-1).getTimeDetailsRwc();
			VerisenseTimeDetails curr = useUcClockTicks? slowSensorBlocks.get(i).getTimeDetailsUcClock():slowSensorBlocks.get(i).getTimeDetailsRwc();
			// The per-block ticks are a SUB-MINUTE counter (resets at
			// TICKS_PER_MINUTE, 32768 Hz x 60 s - the same semantics the
			// minute-rollover logic in backfillDataBlockUcClockOrRwcTimestamps
			// depends on), so a minute-boundary crossing shows as a negative
			// delta that must be re-based by one minute - NOT wrapped at 2^24.
			long deltaTicks = curr.getEndTimeTicks() - prev.getEndTimeTicks();
			if(deltaTicks<0) {
				deltaTicks += (long) AsmBinaryFileConstants.TICKS_PER_MINUTE;
			}
			int sampleCount = slowSensorBlocks.get(i).getSampleCount();
			if(deltaTicks>0 && sampleCount>0) {
				perSamplePeriodsS.add((deltaTicks/32768.0)/sampleCount);
			}
		}
		if(perSamplePeriodsS.isEmpty()) {
			return;
		}
		// Median so a dropped block (a 2x gap) can't skew the period.
		Collections.sort(perSamplePeriodsS);
		double medianPeriodS = perSamplePeriodsS.get(perSamplePeriodsS.size()/2);
		if(!(medianPeriodS>0)) {
			return;
		}

		double achievedRateHz = 1.0/medianPeriodS;
		for(DataBlockDetails dataBlockDetails:slowSensorBlocks) {
			dataBlockDetails.setSamplingRate(achievedRateHz);
			dataBlockDetails.calculateTimestampDiffInS();
		}

		// Seed the CSV gap-splitting window from the OBSERVED cadence rather than a
		// single-rate +/-10% band. The header-derived estimate can sit within ~1% of
		// the band edge (VD6283: 10 Hz estimated vs ~9.09 Hz achieved), and the slow
		// sensors' cadence is inherently jittery: the light's is bimodal (exposure vs
		// exposure + dead time: ~100 vs ~110 ms at the default exposure) and the
		// MLX90632's conversions can slip by several refresh periods and then catch
		// up (observed +12.5% block spacing with no samples lost - DEV-927
		// validation data). A single payload carries only 2-3 slow-sensor blocks,
		// i.e. one or two inter-block gaps - no spread information - so the gap
		// side of the window cannot rely on observed spread at all: it is set to
		// tolerate anything up to SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO x the
		// achieved median spacing, which keeps healthy jitter continuous while a
		// genuinely dropped block (2x spacing) still splits. The fast side keeps
		// the observed-minimum-period basis with the standard tolerance.
		// The put is deliberately UNCONDITIONAL: the limits map is global across
		// payloads, and a payload with fewer than two blocks of this sensor (early
		// return above - e.g. the very first payload of a recording) leaves
		// populateExpectedPayloadTsDiffLimitMapIfNeeded to seed a configured-rate
		// +/-10% band first. A containsKey guard here would then lock that too-tight
		// estimate in for the whole file (observed: 25-min DEV-927 skin-temp
		// recording fragmented into 7 CSVs); the measured window must win as soon as
		// it exists, and re-measuring on every payload keeps it tracking the sensor.
		double minPeriodS = perSamplePeriodsS.get(0);
		double[] samplingRateLimits = new double[] {
				achievedRateHz/UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.SLOW_SENSOR_MAX_INTER_BLOCK_GAP_RATIO,
				(1.0/minPeriodS)*UtilCsvSplitting.FILE_GAP_TOLERANCE_MULTIPLIER.UPPER};
		for(SENSORS sensorClassKey:verisenseDevice.getOrCreateListOfSensorClassKeysForDataBlockId(slowSensorId)) {
			if(sensorClassKey!=SENSORS.CLOCK) {
				UtilCsvSplitting.SAMPLING_RATE_LIMITS_PER_SENSOR.put(sensorClassKey, samplingRateLimits);
			}
		}
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
				// Advance by the on-disk bytes THIS list entry occupies. A midday/midnight-split
				// block appears as two consecutive entries whose recomputed sizes
				// (dataPacketSize*sampleCount) sum to the on-disk block size, so each part must
				// advance by its own share - advancing by the raw size for each part would count
				// the block twice and misalign every block after it. Unsplit blocks advance by
				// the raw size, which for the variable-length LSM6DSV tagged FIFO is the only
				// valid measure (its raw length includes tag/mag/timestamp entries that a
				// dataPacketSize*sampleCount recomputation cannot reproduce).
				if(dataBlockDetails.isFirstPartOfSplitDataBlock() || dataBlockDetails.isSecondPartOfSplitDataBlock()) {
					if(dataBlockDetails.datablockSensorId==DATABLOCK_SENSOR_ID.LSM6DSV) {
						// A split tagged-FIFO block cannot be byte-walked from recomputed
						// sample counts (the raw layout interleaves tag/mag/timestamp
						// entries). Instead BOTH halves are handed the same raw block bytes
						// and the LSM6DSV entry parser selects each half's aligned-sample
						// range - so the first half advances by nothing and the second half
						// advances by the raw on-disk size, keeping subsequent blocks aligned.
						if(dataBlockDetails.isSecondPartOfSplitDataBlock()) {
							currentByteIndex += dataBlockDetails.getQtySensorDataBytesInDatablockRaw();
						}
					} else {
						currentByteIndex += dataBlockDetails.qtySensorDataBytesInDatablock;
					}
				} else {
					currentByteIndex += dataBlockDetails.getQtySensorDataBytesInDatablockRaw();
				}
				
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
