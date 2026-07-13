package com.shimmerresearch.verisense.payloaddesign;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;

/**
 * This class represents a single block of data in the payload that has
 * originated from a single sensor (e.g. the FIFO buffer from any of the
 * sensors). It has an associated sensor ID and a RTC ticks value included.
 * 
 * @author Mark Nolan
 *
 */
public class DataBlockDetails implements Serializable {
	
	private static final long serialVersionUID = -3695586952435188960L;
	
	/** This is the ENUM of the DataBlock ID as set in FW. The ordinal of each
	 * entry MUST match the firmware {@code SENSOR_IDS} enum
	 * (verisense-firmware: Includes/ASM_common_source/SensorDataManagement/recordBuffers.h)
	 * because the parser maps the data-block sensor-id byte straight to
	 * {@code DATABLOCK_SENSOR_ID.values()[sensorId]}. */
	public enum DATABLOCK_SENSOR_ID {
		NONE,			// 0
		ADC,			// 1
		ACCEL_1,		// 2  - LIS2DW12
		GYRO_ACCEL2,	// 3  - LSM6DS3
		PPG,			// 4  - MAX86150/MAX86916 (gen1) or MAX86176 via the MAX32674 hub (gen2)
		BIOZ,			// 5  - MAX30001/2 (reserved, not currently emitted)
		// --- Second-generation hardware (SR68-9/10, SR61-5/6) ---
		LSM6DSV,		// 6  - LSM6DSV accel/gyro + LIS2MDL mag (via the LSM6DSV sensor hub)
		LIGHT,			// 7  - VD6283TX45 ambient light
		ALGO_HUB,		// 8  - MAX32674 algorithm hub (HR/SpO2/activity/skin-contact)
		SKIN_TEMP,		// 9  - MLX90632 skin temperature
		FLICKER			// 10 - VD6283TX45 flicker (RESERVED, not yet emitted by firmware)
	}

	public DATABLOCK_SENSOR_ID datablockSensorId;
	public List<SENSORS> listOfSensorClassKeys;
	public int dataBlockStartByteIndexInPayload;
	public int dataBlockStartByteIndexInFile;
	
	private VerisenseTimeDetails timeDetailsRwc = new VerisenseTimeDetails();
	private VerisenseTimeDetails timeDetailsUcClock = new VerisenseTimeDetails();
	
	public int qtySensorDataBytesInDatablock;
	/**
	 * The raw on-disk sensor-data byte length as parsed from the payload. Unlike
	 * {@link #qtySensorDataBytesInDatablock} this is never recomputed by the
	 * midday/midnight CSV-split logic ({@link #setSampleCountAndUpdateDataBlockSize(int)})
	 * - essential for variable-length blocks (e.g. the LSM6DSV tagged FIFO, whose raw
	 * length includes timestamp-tag and magnetometer entries that don't map 1:1 to
	 * aligned samples). Always use this for advancing byte offsets through the payload.
	 */
	private int qtySensorDataBytesInDatablockRaw;
	public int dataPacketSize;
	private double samplingRate;
	
	private int sampleCount;
	private double timestampDiffInS;
	
	/** Useful for console prints */
	private int dataBlockIndexInPayload = Integer.MIN_VALUE;
	private int payloadIndex = Integer.MIN_VALUE;
	
	private ObjectCluster[] ojcArray = null;
	
	/** If a midday/midnight transition is detected within a data block, the data
	 * block will be split in two on a sample-by-sample basis. */
	public DATA_BLOCK_SPLIT_PART splitDataBlockPart = DATA_BLOCK_SPLIT_PART.NOT_SPLIT;
	private boolean firstDataBlockAfterSplitBySampleDueToTimeGapOrOverlap = false;
	/** Is true for is the first data block after a midday/midnight transition
	 * in-which the datablock didn't need to be split sample-by-sample */
	private boolean firstUnsplitDataBlockAfterMiddayMidnightTransition = false;
	
	public enum DATA_BLOCK_SPLIT_PART {
		NOT_SPLIT,
		FIRST_PART_OF_SPLIT_DATA_BLOCK,
		SECOND_PART_OF_SPLIT_DATA_BLOCK,
	}

	public DataBlockDetails(DATABLOCK_SENSOR_ID datablockSensorId, int payloadIndex, int dataBlockIndexInPayload, List<SENSORS> listOfSensorClassKeys, 
			int dataBlockStartByteIndexInFile, int dataBlockStartByteIndexInPayload) {
		this.datablockSensorId = datablockSensorId;
		this.listOfSensorClassKeys = listOfSensorClassKeys;
		
		setDataBlockIndexInPayload(dataBlockIndexInPayload);
		this.payloadIndex = payloadIndex;
		
		this.dataBlockStartByteIndexInFile = dataBlockStartByteIndexInFile;
		this.dataBlockStartByteIndexInPayload = dataBlockStartByteIndexInPayload;
	}

	public DataBlockDetails(DATABLOCK_SENSOR_ID datablockSensorId, int payloadIndex, int dataBlockIndexInPayload, List<SENSORS> listOfSensorClassKeys, 
			int dataBlockStartByteIndexInFile, int dataBlockStartByteIndexInPayload, 
			long endTimeTicks, boolean isEndTimeTicksFromUcClock) {
		this(datablockSensorId, payloadIndex, dataBlockIndexInPayload, listOfSensorClassKeys, dataBlockStartByteIndexInFile, dataBlockStartByteIndexInPayload);
		
		VerisenseTimeDetails verisenseTimeDetails = isEndTimeTicksFromUcClock? getTimeDetailsUcClock():getTimeDetailsRwc();
		verisenseTimeDetails.setEndTimeTicks(endTimeTicks);
	}
	
	public void setMetadata(int dataBlockSizeSensorData, int dataPacketSize, double samplingRate) {
		this.qtySensorDataBytesInDatablock = dataBlockSizeSensorData;
		this.qtySensorDataBytesInDatablockRaw = dataBlockSizeSensorData;
		this.dataPacketSize = dataPacketSize;
		setSamplingRate(samplingRate);
		calculateSampleCount();
	}

	public int getQtySensorDataBytesInDatablockRaw() {
		return qtySensorDataBytesInDatablockRaw;
	}
	
	public void setRwcEndTimeMinutesAndCalculateTimings(long rtcEndTimeMinutes) {
		setUcClockOrRwcClockEndTimeMinutesAndCalculateTimings(rtcEndTimeMinutes, false);
	}

	public void setUcClockEndTimeMinutesAndCalculateTimings(long ucClockEndTimeMinutes) {
		setUcClockOrRwcClockEndTimeMinutesAndCalculateTimings(ucClockEndTimeMinutes, true);
	}

	public void setUcClockOrRwcClockEndTimeMinutesAndCalculateTimings(long endTimeMinutes, boolean setUcClock) {
		VerisenseTimeDetails verisenseTimeDetails = setUcClock? getTimeDetailsUcClock():getTimeDetailsRwc();
		verisenseTimeDetails.setEndTimeMinutes(endTimeMinutes);
		verisenseTimeDetails.calculateEndTimeMs();
		verisenseTimeDetails.calculateAndSetStartTimeMs(getSampleCount(), getTimestampDiffInS());
	}

	public void calculateSampleCount() {
		int sampleCount = (int) (qtySensorDataBytesInDatablock/dataPacketSize);
		setSampleCount(sampleCount);
	}

	public void setSampleCount(int sampleCount) {
		this.sampleCount = sampleCount;
		calculateTimestampDiffInS();
		setupOjcArray(sampleCount);
	}
	
	public void setSampleCountAndUpdateDataBlockSize(int sampleCount) {
		setSampleCount(ojcArray.length);
		qtySensorDataBytesInDatablock = dataPacketSize*sampleCount;
	}

	public int getSampleCount() {
		return sampleCount;
	}

	public double getEndTimeRwcMs() {
		return timeDetailsRwc.getEndTimeMs();
	}

	public void calculateTimestampDiffInS() {
		timestampDiffInS = 1/samplingRate;
	}

	public double getTimestampDiffInS() {
		return timestampDiffInS;
	}

	public double getStartTimeRwcMs() {
		return timeDetailsRwc.getStartTimeMs();
	}

	public void setSamplingRate(double samplingRate) {
		this.samplingRate = samplingRate;
	}

	public double getSamplingRate() {
		return this.samplingRate;
	}

	public String generateDebugStr() {
		return (listOfSensorClassKeys
				+ " -> " + "PayloadIndex=" + getPayloadIndex()
				+ ", " + "DataBlockIndexInPayload=" + getDataBlockIndexInPayload()
				+ ", Time [Start=" + timeDetailsRwc.getStartTimeStr()
				+ ", End=" + timeDetailsRwc.getEndTimeStr()
				+ "], Samples=" + getSampleCount()
				+ " @ " + samplingRate + CHANNEL_UNITS.FREQUENCY
//				+ ", EndTime [Minutes=" + rtcEndTimeMinutes
//				+ ", Ticks=" + rtcEndTimeTicks
				+ "]");
	}

	public void setUcClockOrRwcEndTimeMinutesFromSubsequentDataBlock(DataBlockDetails subsequentDataBlock, boolean setUcClock) {
		VerisenseTimeDetails verisenseTimeDetailsSubSequentBlock = setUcClock? subsequentDataBlock.getTimeDetailsUcClock():subsequentDataBlock.getTimeDetailsRwc();
		VerisenseTimeDetails verisenseTimeDetailsCurrentBlock = setUcClock? getTimeDetailsUcClock():getTimeDetailsRwc();
		
		long endTimeMinutes = verisenseTimeDetailsSubSequentBlock.getEndTimeMinutes();
		if(verisenseTimeDetailsCurrentBlock.getEndTimeTicks()>verisenseTimeDetailsSubSequentBlock.getEndTimeTicks()) {
			endTimeMinutes--;
		}
		
		setUcClockOrRwcClockEndTimeMinutesAndCalculateTimings(endTimeMinutes, setUcClock);
	}

	public void setupOjcArray(int sampleCount) {
		ojcArray = new ObjectCluster[sampleCount];
	}

	public void setOjcArrayAtIndex(int sampleIndex, ObjectCluster ojc) {
		ojcArray[sampleIndex] = ojc;
	}

	public ObjectCluster[] getOjcArray() {
		return ojcArray;
	}
	
	public DataBlockDetails deepClone() {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(this);

			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (DataBlockDetails) ois.readObject();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	public void splitAndEndBeforeSampleIndex(int sampleIndex, double newEndTimestampMsRwc, double newEndTimestampMsUcClock) {
		ObjectCluster[] ojcArrayBuf = new ObjectCluster[sampleIndex]; 
		System.arraycopy(ojcArray, 0, ojcArrayBuf, 0, sampleIndex);
		ojcArray = ojcArrayBuf;
		
		getTimeDetailsRwc().setEndTimeMs(newEndTimestampMsRwc);
		// Value is NaN for payload designs <v10 as the microcontroller time was not supported
		if(!Double.isNaN(newEndTimestampMsUcClock)) {
			getTimeDetailsUcClock().setEndTimeMs(newEndTimestampMsUcClock);
		}
		
		setSampleCountAndUpdateDataBlockSize(ojcArray.length);
		
		setSplitDataBlockPart(DATA_BLOCK_SPLIT_PART.FIRST_PART_OF_SPLIT_DATA_BLOCK);
	}
	
	public void splitAndStartAtSampleIndex(int sampleIndex, double newStartTimestampMsRwc, double newStartTimestampMsUcClock) {
		ObjectCluster[] ojcArrayBuf = new ObjectCluster[ojcArray.length-sampleIndex]; 
		System.arraycopy(ojcArray, sampleIndex, ojcArrayBuf, 0, ojcArrayBuf.length);
		ojcArray = ojcArrayBuf;
		
		getTimeDetailsRwc().setStartTimeMs(newStartTimestampMsRwc);
		// Value is NaN for payload designs <v10 as the microcontroller time was not supported
		if(!Double.isNaN(newStartTimestampMsUcClock)) {
			getTimeDetailsUcClock().setStartTimeMs(newStartTimestampMsUcClock);
		}

		setSampleCountAndUpdateDataBlockSize(ojcArray.length);

		setSplitDataBlockPart(DATA_BLOCK_SPLIT_PART.SECOND_PART_OF_SPLIT_DATA_BLOCK);
	}

	public static DataBlockDetails recombineDataBlockDetailsForContinuityCheck(DataBlockDetails dataBlockDetails1, DataBlockDetails dataBlockDetails2) {
		DataBlockDetails dataBlockDetailsCombined = new DataBlockDetails(dataBlockDetails1.datablockSensorId, dataBlockDetails1.getPayloadIndex(), dataBlockDetails1.getDataBlockIndexInPayload(), dataBlockDetails1.getListOfSensorClassKeys(), 
				dataBlockDetails1.dataBlockStartByteIndexInFile, dataBlockDetails1.dataBlockIndexInPayload);

		dataBlockDetailsCombined.setMetadata(dataBlockDetails1.qtySensorDataBytesInDatablock+dataBlockDetails2.qtySensorDataBytesInDatablock, 
				dataBlockDetails1.dataPacketSize, 
				dataBlockDetails1.samplingRate);

		VerisenseTimeDetails timeDetailsRwcCombined = dataBlockDetailsCombined.getTimeDetailsRwc();
		timeDetailsRwcCombined.setStartTimeMs(dataBlockDetails1.getTimeDetailsRwc().getStartTimeMs());
		timeDetailsRwcCombined.setEndTimeMs(dataBlockDetails2.getTimeDetailsRwc().getEndTimeMs());

		return dataBlockDetailsCombined;
	}

	public List<SENSORS> getListOfSensorClassKeys() {
		return listOfSensorClassKeys;
	}

	public void setSplitDataBlockPart(DATA_BLOCK_SPLIT_PART dataBlockSplitIndex) {
		splitDataBlockPart = dataBlockSplitIndex;
	}

	public boolean isFirstPartOfSplitDataBlock() {
		return splitDataBlockPart == DATA_BLOCK_SPLIT_PART.FIRST_PART_OF_SPLIT_DATA_BLOCK;
	}

	public boolean isSecondPartOfSplitDataBlock() {
		return splitDataBlockPart == DATA_BLOCK_SPLIT_PART.SECOND_PART_OF_SPLIT_DATA_BLOCK;
	}

	public void setFirstDataBlockAfterSplitBySampleDueToTimeGapOrOverlap() {
		firstDataBlockAfterSplitBySampleDueToTimeGapOrOverlap = true;
	}

	public boolean isFirstDataBlockAfterSplitBySampleDueToTimeGapOrOverlap() {
		return firstDataBlockAfterSplitBySampleDueToTimeGapOrOverlap;
	}

	public void setFirstUnsplitDataBlockAfterMiddayMidnightTransition() {
		firstUnsplitDataBlockAfterMiddayMidnightTransition = true;
	}

	public boolean isFirstUnsplitDataBlockAfterMiddayMidnightTransition() {
		return firstUnsplitDataBlockAfterMiddayMidnightTransition;
	}

	public VerisenseTimeDetails getTimeDetailsRwc() {
		return timeDetailsRwc;
	}

	public VerisenseTimeDetails getTimeDetailsUcClock() {
		return timeDetailsUcClock;
	}

	public void setDataBlockIndexInPayload(int dataBlockIndexInPayload) {
		this.dataBlockIndexInPayload = dataBlockIndexInPayload;
	}
	
	public int getDataBlockIndexInPayload() {
		return dataBlockIndexInPayload;
	}

	public int getPayloadIndex() {
		return payloadIndex;
	}

	public boolean isResultOfSplitAtMiddayOrMidnight() {
		return isSecondPartOfSplitDataBlock() || isFirstUnsplitDataBlockAfterMiddayMidnightTransition();
	}

}