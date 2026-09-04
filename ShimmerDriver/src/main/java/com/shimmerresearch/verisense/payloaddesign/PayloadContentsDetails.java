package com.shimmerresearch.verisense.payloaddesign;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import com.shimmerresearch.driver.Configuration.CHANNEL_UNITS;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilParseData;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_ENDIAN;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_TYPE;
import com.shimmerresearch.sensors.AbstractSensor.SENSORS;
import com.shimmerresearch.verisense.UtilVerisenseDriver;
import com.shimmerresearch.verisense.VerisenseDevice;
import com.shimmerresearch.verisense.payloaddesign.AsmBinaryFileConstants.BYTE_COUNT;
import com.shimmerresearch.verisense.payloaddesign.AsmBinaryFileConstants.DATA_COMPRESSION_MODE;
import com.shimmerresearch.verisense.payloaddesign.DataBlockDetails.DATABLOCK_SENSOR_ID;

public abstract class PayloadContentsDetails implements Serializable {
	
	private static final long serialVersionUID = -7481013763683220629L;
	
	public static final List<SENSORS> SENSORS_TO_EXCLUDE_FROM_SENSOR_CSV_GENERATION = Arrays.asList(new SENSORS[] {
			SENSORS.CLOCK});

	public static boolean DEBUG_ACCEL_ARRAYS = false;
	private static boolean DEBUG_DATA_BLOCKS = false;
	public static boolean SPLIT_CSVS_AT_MIDDAY_AND_MIDNIGHT = true;

	protected transient List<DataBlockDetails> listOfDataBlocksInOrder = new ArrayList<DataBlockDetails>();
	public transient DatasetToSave datasetToSave = new DatasetToSave();

	/** Real-world clock time (if set in the sensor, otherwise this is the same as the microcontroller time).*/
	private VerisenseTimeDetails timeDetailsRwc = new VerisenseTimeDetails();
	/** Microcontroller Clock. Supported in FW >=v1.02.085. */
	private VerisenseTimeDetails timeDetailsUcClock = new VerisenseTimeDetails();

	private long temperatureUncal = 0; 
	private double temperatureCal = 0; 

	private long batteryVoltageCal = -1; 

	private int payloadIndex = 0; 
	
	protected transient byte[] byteBuffer = null;

	public transient VerisenseDevice verisenseDevice;
	
	protected Set<DATABLOCK_SENSOR_ID> setOfPayloadSensorIds = new HashSet<DATABLOCK_SENSOR_ID>();

	private int payloadSplitIndex = 0;

	private double payloadPackagingDelayMs = Double.NaN;

	abstract public void parsePayloadContentsMetaData(int binFileByteIndex) throws IOException;
	abstract public void parsePayloadSensorData();

	public PayloadContentsDetails(VerisenseDevice verisenseDevice) {
		this.verisenseDevice = verisenseDevice;
	}

	/** Returns the end time as stored in the payload footer
	 * @return Payload end time in a milliseconds
	 */
	public double getEndTimeRwcMs() {
		return timeDetailsRwc.getEndTimeMs();
	}
	
	/** Returns the end time as stored in the payload footer
	 * @return Payload end time in a formatted String
	 */
	public String getEndTimeRwcStr() {
		return timeDetailsRwc.getEndTimeStr();
	}

	/** Returns the end time of the last data sample within the payload
	 * @return Payload data end time in a milliseconds
	 */
	public double getDataEndTimeRwcMs() {
		return listOfDataBlocksInOrder.get(listOfDataBlocksInOrder.size()-1).getEndTimeRwcMs();
	}
	
	/** Returns the end time of the last data sample within the payload
	 * @return Payload data end time in a formatted String
	 */
	public String getDataEndTimeRwcStr() {
		return UtilVerisenseDriver.convertMilliSecondsToCsvHeaderFormat((long)getDataEndTimeRwcMs());
	}

	public double getStartTimeRwcMs() {
		return timeDetailsRwc.getStartTimeMs();
	}

	public String getStartTimeRwcStr() {
		return timeDetailsRwc.getStartTimeStr();
	}
	
	public double calculatePayloadDurationMs() {
		return getEndTimeRwcMs() - getStartTimeRwcMs();
	}

	public String getPayloadDurationStr() {
		return UtilVerisenseDriver.convertSecondsToHHmmssSSS(calculatePayloadDurationMs()/1000);
	}

	public VerisenseTimeDetails getTimeDetailsRwc() {
		return timeDetailsRwc;
	}

	public VerisenseTimeDetails getTimeDetailsUcClock() {
		return timeDetailsUcClock;
	}

	public double getPayloadPackagingDelayMs() {
		return payloadPackagingDelayMs;
	}

	public void calculateAndSetPayloadPackagingDelayMs() {
		double endTimeOfPayloadMs = getEndTimeRwcMs();
		double endTimeOfLastDataBlockMs = getDataEndTimeRwcMs();
		payloadPackagingDelayMs = endTimeOfPayloadMs - endTimeOfLastDataBlockMs;
	}

	public String getPayloadPackagingDelayStr() {
		double payloadPackagingDelayMs = getPayloadPackagingDelayMs();
		if(!Double.isNaN(payloadPackagingDelayMs)) {
			return UtilVerisenseDriver.convertSecondsToHHmmssSSS(payloadPackagingDelayMs/1000);
		} else {
			return "Unknown";
		}
	}

	public void setTemperature(long temperatureUncal, double temperatureCal) {
		this.temperatureUncal = temperatureUncal;
		this.temperatureCal = temperatureCal;
	}

	public long getTemperatureUncal() {
		return temperatureUncal;
	}

	public double getTemperatureCal() {
		return temperatureCal;
	}

	public void setBatteryVoltage(long batteryVoltageCal) {
		this.batteryVoltageCal = batteryVoltageCal;
	}
	
	public long getBatteryVoltageCal() {
		return batteryVoltageCal;
	}

	public void setByteBuffer(byte[] byteBuffer) {
		this.byteBuffer = byteBuffer;
	}

	public byte[] getByteBuffer() {
		return byteBuffer;
	}

	public void setPayloadIndex(int payloadIndex) {
		this.payloadIndex = payloadIndex;
	}

	public int getPayloadIndex() {
		return this.payloadIndex;
	}

	public static int calculateExtendedPayloadConfigBytesSize(ShimmerVerObject svo) {
		int extendedPayloadConfigSize = 0;
		if(isPayloadDesignV2orAbove(svo)) {
			extendedPayloadConfigSize += 1;
		}
		if(isPayloadDesignV3orAbove(svo)) {
			extendedPayloadConfigSize += 4;
		}
		if(isPayloadDesignV4orAbove(svo)) {
			extendedPayloadConfigSize += 5;
		}
		if(isPayloadDesignV5orAbove(svo)) {
			extendedPayloadConfigSize += 1;
		}
		if(isPayloadDesignV6orAbove(svo)) {
			extendedPayloadConfigSize += 2;
		}
		if(isPayloadDesignV7orAbove(svo)) {
			extendedPayloadConfigSize += 1;
		}
		if(isPayloadDesignV8orAbove(svo)) {
			extendedPayloadConfigSize += 4;
		}
		if(isPayloadDesignV12orAbove(svo)) {
			extendedPayloadConfigSize += 1;
		}
		if(isPayloadDesignV13orAbove(svo)) {
			// Second-generation (FW v2.00.004+): config header grows from 25 to 32 bytes
			// (abs bytes 4..35). +7 = LSM6DSV already occupies the gen-1 gyro/accel2 slots;
			// the extra 7 are GEN_CFG_3 (29), light gain/exposure (30,31), skin-temp (32),
			// algo report (33), and the 2-byte calibration CRC (34,35).
			extendedPayloadConfigSize += 7;
		}
		return extendedPayloadConfigSize;
	}

	public static boolean isPayloadDesignV1orAbove(ShimmerVerObject svo) {
		return VerisenseDevice.compareFwVersions(svo, VerisenseDevice.FW_SPECIAL_VERSIONS.V_0_31_000);
	}

	public static boolean isPayloadDesignV2orAbove(ShimmerVerObject svo) {
		return VerisenseDevice.compareFwVersions(svo, VerisenseDevice.FW_CHANGES.CCF19_027);
	}

	public static boolean isPayloadDesignV3orAbove(ShimmerVerObject svo) {
		return VerisenseDevice.compareFwVersions(svo, VerisenseDevice.FW_CHANGES.CCF19_035);
	}

	public static boolean isPayloadDesignV4orAbove(ShimmerVerObject svo) {
		return VerisenseDevice.compareFwVersions(svo, VerisenseDevice.FW_CHANGES.CCF20_012_1);
	}

	public static boolean isPayloadDesignV5orAbove(ShimmerVerObject svo) {
		return VerisenseDevice.compareFwVersions(svo, VerisenseDevice.FW_CHANGES.CCF20_012_2);
	}

	public static boolean isPayloadDesignV6orAbove(ShimmerVerObject svo) {
		return VerisenseDevice.compareFwVersions(svo, VerisenseDevice.FW_CHANGES.CCF20_012_3);
	}

	public static boolean isPayloadDesignV7orAbove(ShimmerVerObject svo) {
		return (VerisenseDevice.compareFwVersions(svo, VerisenseDevice.FW_CHANGES.CCF20_012_4) 
				&& !VerisenseDevice.isFwMajorMinorInternalVerEqual(svo, VerisenseDevice.FW_SPECIAL_VERSIONS.V_1_02_071));
	}

	public static boolean isPayloadDesignV8orAbove(ShimmerVerObject svo) {
		return VerisenseDevice.compareFwVersions(svo, VerisenseDevice.FW_CHANGES.CCF20_012_5);
	}

	public static boolean isPayloadDesignV9orAbove(ShimmerVerObject svo) {
		return VerisenseDevice.compareFwVersions(svo, VerisenseDevice.FW_CHANGES.CCF20_012_6);
	}

	public static boolean isPayloadDesignV10orAbove(ShimmerVerObject svo) {
		return VerisenseDevice.compareFwVersions(svo, VerisenseDevice.FW_CHANGES.CCF21_010_1);
	}

	public static boolean isPayloadDesignV11orAbove(ShimmerVerObject svo) {
		return VerisenseDevice.compareFwVersions(svo, VerisenseDevice.FW_CHANGES.CCF21_010_2);
	}

	public static boolean isPayloadDesignV12orAbove(ShimmerVerObject svo) {
		return VerisenseDevice.compareFwVersions(svo, VerisenseDevice.FW_CHANGES.CCF21_010_3);
	}

	/**
	 * Second-generation hardware payload design (SR68-9/10, SR61-5/6). The
	 * firmware calls this its "payload design v9" (32-byte config header, bytes
	 * 4..35, of which bytes 34..35 are the calibration-blob CRC). It is NOT the
	 * same as {@link #isPayloadDesignV9orAbove(ShimmerVerObject)} above, which is
	 * this driver's own internal sequence (FW v1.02.074). Gen-2 firmware
	 * (v2.00.004+, confirmed on a real SR68-9) is the next step after V12 in this
	 * driver's numbering.
	 *
	 * @see VerisenseDevice.FW_CHANGES#CCF_GEN2
	 */
	public static boolean isPayloadDesignV13orAbove(ShimmerVerObject svo) {
		return VerisenseDevice.compareFwVersions(svo, VerisenseDevice.FW_CHANGES.CCF_GEN2);
	}

	/**
	 * This is a similar method to "!isPayloadDesignGen7OrAbove" but has been
	 * created to make it easy to distinguish in the code sections that control the
	 * differ CSV output formats.
	 * 
	 * If there are future firmware releases to that client (like
	 * VerisenseDevice.FW_SPECIAL_VERSIONS.V_1_02_071), additional checks can be
	 * added to this function.
	 * 
	 * @return
	 */
	public static boolean isCsvHeaderDesignAzMarkingPoint(ShimmerVerObject svo) {
		return !isPayloadDesignV7orAbove(svo);
	}

	public static int calculatePayloadHeaderBytesSize(ShimmerVerObject svo) {
		int payloadHeaderSize = BYTE_COUNT.PAYLOAD_INDEX+BYTE_COUNT.PAYLOAD_LENGTH;
		payloadHeaderSize += calculatePayloadConfigBytesSize(svo);
		return payloadHeaderSize;
	}
	
	public static int calculatePayloadConfigBytesSize(ShimmerVerObject svo) {
		int payloadConfigByteSize = BYTE_COUNT.PAYLOAD_CONFIG_CORE;
		if(isPayloadDesignV2orAbove(svo)) {
			//4 bytes for FW Version (Major, Minor, Internal LSB & MSB)
			payloadConfigByteSize += 4;
			payloadConfigByteSize += calculateExtendedPayloadConfigBytesSize(svo);
		}
		return payloadConfigByteSize;
	}

	public boolean parsePayloadContentsHeaderAndFooter(byte[] ramBlockDataBytes, int payloadIndex, int binFileByteIndex) throws IOException {
		setPayloadIndex(payloadIndex);
		
		byte[] byteBuffer = ramBlockDataBytes;
	
		if(verisenseDevice.dataCompressionMode==DATA_COMPRESSION_MODE.ZLIB) {
			byteBuffer = Compressor.decompress(ramBlockDataBytes);
		} else if(verisenseDevice.dataCompressionMode!=DATA_COMPRESSION_MODE.NONE) {
			verisenseDevice.consolePrintLn("Compression method not supported");
		}
		
		if(byteBuffer==null) {
			System.err.println("issue with data decompression, returning.");
			return false;
		}
		setByteBuffer(byteBuffer);
		
		parsePayloadContentsMetaData(binFileByteIndex);
		sortDataBlocksByContinuity();
		// Needed here for console prints describing the current payload
		datasetToSave.updateSampleCountForEachDataSegmentDetails();

		if (DEBUG_DATA_BLOCKS) {
			printListOfDataBlockDetails();
			System.out.println("");
			datasetToSave.printListOfDataBlockDetailsBySensor();
		}

		return true;
	}
	
	protected int parseTemperatureBytes(int currentByteIndex) {
		//2's complement, left-justified, 12-bit with 16 LSB per degree C
		byte[] temperatureArray = new byte[BYTE_COUNT.PAYLOAD_CONTENTS_TEMPERATURE];
		System.arraycopy(byteBuffer, currentByteIndex, temperatureArray, 0, BYTE_COUNT.PAYLOAD_CONTENTS_TEMPERATURE);
		long temperatureUncal = UtilParseData.parseData(temperatureArray, CHANNEL_DATA_TYPE.INT16, CHANNEL_DATA_ENDIAN.LSB);
		double temperatureCal = verisenseDevice.calibrateTemperature(temperatureUncal);
		setTemperature(temperatureUncal, temperatureCal);
		currentByteIndex += temperatureArray.length;
		return currentByteIndex;
	}

	protected int parseBatteryVoltageBytes(int currentByteIndex) {
		byte[] batteryVoltageArray = new byte[BYTE_COUNT.PAYLOAD_CONTENTS_BATTERY_VOLTAGE];
		System.arraycopy(byteBuffer, currentByteIndex, batteryVoltageArray, 0, BYTE_COUNT.PAYLOAD_CONTENTS_BATTERY_VOLTAGE);
		// Second-generation firmware writes the full battery voltage in mV which can
		// exceed 12-bits (e.g. 4133 mV) - masking to UINT12 would wrap it (-> 37 mV).
		// Keep the legacy UINT12 mask for gen-1 to preserve existing behaviour.
		CHANNEL_DATA_TYPE battChannelDataType = isPayloadDesignV13orAbove(verisenseDevice.getShimmerVerObject())
				? CHANNEL_DATA_TYPE.UINT16 : CHANNEL_DATA_TYPE.UINT12;
		long batteryVoltageCal = UtilParseData.parseData(batteryVoltageArray, battChannelDataType, CHANNEL_DATA_ENDIAN.LSB);
		setBatteryVoltage(batteryVoltageCal);
		currentByteIndex += batteryVoltageArray.length;
		return currentByteIndex;
	}

	public void printListOfDataBlockDetails() {
		System.out.println("Sorted in temporal order:");
		DataSegmentDetails.printListOfDataBlockDetails(listOfDataBlocksInOrder, false);
		System.out.println("");
	}

	public List<DataSegmentDetails> getListOfDataSegmentsForSensorClassKey(SENSORS sensorClassKey) {
		return datasetToSave.getListOfDataSegmentsForSensorClassKey(sensorClassKey);
	}
	
	public void consolePrintTsDifferenceUnexpected(String debugStr) {
		StringBuilder debugSb = new StringBuilder();
		debugSb.append("Time gap between payloads is large, starting new files:");
		debugSb.append(debugStr);
		debugSb.append("\tFor sampling rate=" + verisenseDevice.getFastestSamplingRateOfSensors(COMMUNICATION_TYPE.SD) + CHANNEL_UNITS.FREQUENCY);
		System.out.println(debugSb.toString());
	}

	private void sortDataBlocksByContinuity() {
		datasetToSave.reset();
		for(int i=0;i<listOfDataBlocksInOrder.size();i++) {
			DataBlockDetails dataBlockDetails = listOfDataBlocksInOrder.get(i);
			
			DATABLOCK_SENSOR_ID datablockSensorId = dataBlockDetails.datablockSensorId;
			
			List<SENSORS> listOfSensorClassKeys = verisenseDevice.getMapOfSensorIdsPerDataBlock().get(datablockSensorId);
			for(SENSORS sensorClassKey:listOfSensorClassKeys) {
				
				if(SENSORS_TO_EXCLUDE_FROM_SENSOR_CSV_GENERATION.contains(sensorClassKey)) {
					continue;
				}
				
				DataSegmentDetails dataSegmentToAddTo = null;
				List<DataSegmentDetails> listOfDataSegmentDetails = datasetToSave.getMapOfDataSegmentsPerSensor().get(sensorClassKey);
				if (listOfDataSegmentDetails == null) {
					// If sensor ID is not present, create a new list and DataSegmentDetails.
					listOfDataSegmentDetails = new ArrayList<DataSegmentDetails>();
					datasetToSave.putInMapOfDataSegmentsPerSensor(sensorClassKey, listOfDataSegmentDetails);
				} else if(dataBlockDetails.isResultOfSplitAtMiddayOrMidnight()){
					// Midnight/midday transition detected it in the middle of a data block (for
					// PayloadContentsDetailsV8orAbove), allow it to create a new DataSegment
				} else {
					// If a datablock has been split on a per sample basis based on a
					// midnight/midday transition, it's a more reliable method of checking for
					// continuity to check the original datablock and not the first part of the
					// newly split datablock. That does mean though that the original datablock
					// needs to be temporarily reassembled.
					DataBlockDetails dataBlockDetailsToCheckForContinuity = null;
					if(dataBlockDetails.isFirstPartOfSplitDataBlock() && listOfDataBlocksInOrder.size()>i) {
						dataBlockDetailsToCheckForContinuity = DataBlockDetails.recombineDataBlockDetailsForContinuityCheck(dataBlockDetails, listOfDataBlocksInOrder.get(i+1));
					} else {
						dataBlockDetailsToCheckForContinuity = dataBlockDetails;
					}
					
					// If the time-gap between the previous and the latest data blocks is expected,
					// add the latest data block to the previous data segment
					DataSegmentDetails dataSegmentDetailsPrevious = listOfDataSegmentDetails.get(listOfDataSegmentDetails.size()-1);
					if(UtilCsvSplitting.isDataBlockContinuous(sensorClassKey, dataSegmentDetailsPrevious, dataBlockDetailsToCheckForContinuity).isEmpty()) {
						dataSegmentToAddTo = dataSegmentDetailsPrevious;
					} else {
						dataBlockDetails.setFirstDataBlockAfterSplitBySampleDueToTimeGapOrOverlap();
					}
				}
				
				if (dataSegmentToAddTo == null) {
					dataSegmentToAddTo = new DataSegmentDetails();
					listOfDataSegmentDetails.add(dataSegmentToAddTo);
				}
				
				dataSegmentToAddTo.addDataBlock(dataBlockDetails);
				
			}
		}
	}
	
	public TreeMap<SENSORS, List<DataSegmentDetails>> getMapOfDataSegmentsPerSensor() {
		return datasetToSave.getMapOfDataSegmentsPerSensor();
	}

	public void calculatePayloadStartTimeMsRwc() {
		calculatePayloadStartTimeMsUcClockOrRwc(false);
	}

	public void calculatePayloadStartTimeMsUcClock() {
		calculatePayloadStartTimeMsUcClockOrRwc(true);
	}

	public void calculatePayloadStartTimeMsUcClockOrRwc(boolean setUcClockTime) {
		List<DATABLOCK_SENSOR_ID> payloadSensorIdsToFind = new ArrayList<DATABLOCK_SENSOR_ID>();
		for(DATABLOCK_SENSOR_ID payloadSensorId:setOfPayloadSensorIds) {
			payloadSensorIdsToFind.add(payloadSensorId);
		}
		
		double calculatedStartTimeMs = Double.NaN;	
		if(listOfDataBlocksInOrder.size()>0) {	
			DataBlockDetails firstDataBlock = listOfDataBlocksInOrder.get(0);
			VerisenseTimeDetails firstDataBlockTimeDetails = setUcClockTime? firstDataBlock.getTimeDetailsUcClock():firstDataBlock.getTimeDetailsRwc();
			calculatedStartTimeMs = firstDataBlockTimeDetails.getStartTimeMs();	
			// need to cycle through them all here (at least per sensor) because different	
			// sensors will have different FIFOs sizes and sampling rates which means the	
			// second datablock could have started before the first datablock	
			for(DataBlockDetails dataBlockDetails:listOfDataBlocksInOrder) {
				if(payloadSensorIdsToFind.size()==0) {
					break;
				}
				if(payloadSensorIdsToFind.contains(dataBlockDetails.datablockSensorId)) {
					VerisenseTimeDetails dataBlockTimeDetails = setUcClockTime? dataBlockDetails.getTimeDetailsUcClock():dataBlockDetails.getTimeDetailsRwc();
					calculatedStartTimeMs = Math.min(calculatedStartTimeMs, dataBlockTimeDetails.getStartTimeMs());	
					payloadSensorIdsToFind.remove(dataBlockDetails.datablockSensorId);
				}
			}	
		}
		
		VerisenseTimeDetails payloadTimeDetails = setUcClockTime? getTimeDetailsUcClock():getTimeDetailsRwc();
		payloadTimeDetails.setStartTimeMs(calculatedStartTimeMs);
	}
	
	public boolean isSplitDetectedWithinPayload() {
		for(List<DataSegmentDetails> listOfDataSegments : datasetToSave.getMapOfDataSegmentsPerSensor().values()) {
			if(listOfDataSegments.size()>1) {
				return true;
			}
		}
		return false;
	}

	public int getPayloadSplitIndex() {
		return payloadSplitIndex;
	}

	public void setPayloadSplitIndex(int payloadSplitIndex) {
		this.payloadSplitIndex = payloadSplitIndex;
	}

	public PayloadContentsDetails deepClone() {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(this);

			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (PayloadContentsDetails) ois.readObject();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public void shiftEndTimesByMsValue(double timeDiff) {
		timeDetailsRwc.setEndTimeMs(timeDetailsRwc.getEndTimeMs()+timeDiff);
		timeDetailsUcClock.setEndTimeMs(timeDetailsUcClock.getEndTimeMs()+timeDiff);
	}

	public void shiftStartTimesByMsValue(double timeDiff) {
		timeDetailsRwc.setStartTimeMs(timeDetailsRwc.getStartTimeMs()+timeDiff);
		timeDetailsUcClock.setStartTimeMs(timeDetailsUcClock.getStartTimeMs()+timeDiff);
	}
	
}
