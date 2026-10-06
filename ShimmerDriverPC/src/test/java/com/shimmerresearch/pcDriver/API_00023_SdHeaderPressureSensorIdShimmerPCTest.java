package com.shimmerresearch.pcDriver;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID_SR_CODES;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.sensors.bmpX80.SdHeaderPressureSensorId.STATE;
import com.shimmerresearch.sensors.bmpX80.SensorBMP390;
import com.shimmerresearch.sensors.bmpX80.SensorBMP581;

/**
 * DEV-1123: a ShimmerPC with the SD header's pressure sensor ID applied, through
 * the paths that see the whole device. API_00022 covers decoding the byte.
 *
 * @author Mark Nolan
 */
public class API_00023_SdHeaderPressureSensorIdShimmerPCTest {

	/** A BMP581 reads 101325 Pa as 101325 x 64 and 23.5 degC as 23.5 x 65536 */
	private static final int RAW_PRESSURE = 101325 * 64;
	private static final int RAW_TEMPERATURE = 23 * 65536 + 32768;

	/** SR48-8-2, a BMP581 board by the SR-number rule, on firmware that writes offset 224 */
	private static ShimmerPC sr48_8_2(int sdHeaderValue) {
		ShimmerPC device = new ShimmerPC("COM99");
		device.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 18));
		device.setExpansionBoardDetailsAndCreateSensorMap(new ExpansionBoardDetails(HW_ID_SR_CODES.EXP_BRD_GSR_UNIFIED, 8, 2));
		device.setPressureSensorIdFromSdHeader(sdHeaderValue);
		device.sensorAndConfigMapsCreate();
		return device;
	}

	/** The pressure and temperature channel names buildMsg uses for this device */
	private static String[] pressureChannels(ShimmerPC device) {
		return device.isSupportedBmp581()
				? new String[] {SensorBMP581.ObjectClusterSensorName.PRESSURE_BMP581, SensorBMP581.ObjectClusterSensorName.TEMPERATURE_BMP581}
				: new String[] {SensorBMP390.ObjectClusterSensorName.PRESSURE_BMP390, SensorBMP390.ObjectClusterSensorName.TEMPERATURE_BMP390};
	}

	/** One packet carrying only the pressure sensor: [timestamp][temperature][pressure] */
	private static ObjectCluster pressurePacket(ShimmerPC device, boolean arraysDataStructure) {
		device.mNChannels = 2;
		device.interpretDataPacketFormat(2, new byte[] {0x1A, 0x1B});
		device.setEnabledSensors(ShimmerObject.SENSOR_BMPX80);
		device.enableArraysDataStructure(arraysDataStructure);
		byte[] packet = new byte[device.getPacketSize()];
		int iTemperature = packet.length - 6;
		putU24(packet, iTemperature, RAW_TEMPERATURE);
		putU24(packet, iTemperature + 3, RAW_PRESSURE);
		return device.buildMsg(packet, COMMUNICATION_TYPE.BLUETOOTH, false, 0);
	}

	private static void putU24(byte[] packet, int index, int value) {
		packet[index] = (byte) value;
		packet[index + 1] = (byte) (value >> 8);
		packet[index + 2] = (byte) (value >> 16);
	}

	private static double value(ObjectCluster objectCluster, String channel, CHANNEL_TYPE channelType) {
		return objectCluster.getFormatClusterValue(channel, channelType.toString());
	}

	/** The calibrated array buildMsg hands over, read directly, whichever data structure is enabled */
	private static double calArray(ObjectCluster objectCluster, String channel) {
		int index = objectCluster.getIndexForChannelName(channel);
		assertTrue(channel + " is in the packet", index >= 0);
		return objectCluster.mEnableArraysDataStructure? objectCluster.sensorDataArray.mCalData[index]:objectCluster.mCalData[index];
	}

	@Test
	public void deepCloneKeepsTheSdHeaderPressureSensorId() {
		// ShimmerPC.deepClone() serialises the device, and returns null if any field can't be
		for (int sdHeaderValue : new int[] {0x03, 0x83, 0x04, 0xFE, 0xFF}) {
			String header = "0x" + Integer.toHexString(sdHeaderValue);
			ShimmerPC device = sr48_8_2(sdHeaderValue);
			ShimmerPC clone = device.deepClone();
			assertNotNull(header, clone);
			assertEquals(header, device.getPressureSensorIdSdHeader().toString(), clone.getPressureSensorIdSdHeader().toString());
			assertEquals(header, device.isSupportedBmp581(), clone.isSupportedBmp581());
			assertEquals(header, device.isPressureSensorCalibratable(), clone.isPressureSensorCalibratable());
			assertEquals(header, device.isPressureSensorInferred(), clone.isPressureSensorInferred());
		}
	}

	@Test
	public void sensorItCannotCalibrateReadsNaNInEveryDataStructure() {
		// 0x04 is a sensor newer than this parser, 0xFE none fitted
		for (int sdHeaderValue : new int[] {0x04, 0xFE}) {
			for (boolean arraysDataStructure : new boolean[] {false, true}) {
				String context = "0x" + Integer.toHexString(sdHeaderValue) + (arraysDataStructure? ", arrays":", multimap");
				ShimmerPC device = sr48_8_2(sdHeaderValue);
				assertFalse(context, device.isPressureSensorCalibratable());
				ObjectCluster objectCluster = pressurePacket(device, arraysDataStructure);

				for (String channel : pressureChannels(device)) {
					assertTrue(context + " " + channel, Double.isNaN(value(objectCluster, channel, CHANNEL_TYPE.CAL)));
					assertTrue(context + " " + channel, Double.isNaN(calArray(objectCluster, channel)));
				}
				// The raw readings still come through
				String[] channels = pressureChannels(device);
				assertEquals(context, RAW_PRESSURE, value(objectCluster, channels[0], CHANNEL_TYPE.UNCAL), 0);
				assertEquals(context, RAW_TEMPERATURE, value(objectCluster, channels[1], CHANNEL_TYPE.UNCAL), 0);
			}
		}
	}

	@Test
	public void knownSensorIsStillCalibrated() {
		for (boolean arraysDataStructure : new boolean[] {false, true}) {
			String context = arraysDataStructure? "arrays":"multimap";
			ShimmerPC device = sr48_8_2(0x03);
			assertEquals(STATE.KNOWN, device.getPressureSensorIdSdHeader().getState());
			assertTrue(device.isSupportedBmp581());
			ObjectCluster objectCluster = pressurePacket(device, arraysDataStructure);

			String[] channels = pressureChannels(device);
			assertEquals(context, 101.325, value(objectCluster, channels[0], CHANNEL_TYPE.CAL), 1e-9);
			assertEquals(context, 23.5, value(objectCluster, channels[1], CHANNEL_TYPE.CAL), 1e-9);
			assertEquals(context, 101.325, calArray(objectCluster, channels[0]), 1e-9);
			assertEquals(context, 23.5, calArray(objectCluster, channels[1]), 1e-9);
		}
	}
}
