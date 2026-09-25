package com.shimmerresearch.driverUtilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.ShimmerObject.SDLogHeader;
import com.shimmerresearch.driver.shimmerGq.ConfigByteLayoutShimmerGq802154;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.sensors.SensorPPGNeuroLynQ;
import com.shimmerresearch.sensors.SensorPPGNeuroLynQ.ObjectClusterSensorName;

/**
 * What a host needs to read a NeuroLynQ node's logged files (DEV-1061).
 * <p>
 * A node logs a GQ's file under its own hardware ID and the GQ's firmware ID
 * (verisense-firmware docs/VERISENSE_NEUROLYNQ_STORAGE.md section 3), with 3-byte timestamps,
 * and on an SR68 a PPG block the GQ never had. The file-level tests are in
 * Shimmer-Advance-API, where ShimmerSDLog is; these are the driver's parts.
 */
public class API_00016_NeuroLynQNodeSdLogTest {

	private static final ShimmerVerObject SR68_NODE = new ShimmerVerObject(HW_ID.VERISENSE_PULSE_PLUS, FW_ID.GQ_802154, 2, 1, 1);
	private static final ShimmerVerObject SR61_NODE = new ShimmerVerObject(HW_ID.VERISENSE_GSR_PLUS, FW_ID.GQ_802154, 2, 1, 1);
	private static final ShimmerVerObject STOCK_VERISENSE = new ShimmerVerObject(HW_ID.VERISENSE_PULSE_PLUS, FW_ID.VERISENSE, 2, 1, 1);
	private static final ShimmerVerObject GQ = new ShimmerVerObject(HW_ID.SHIMMER_GQ_802154_NR, FW_ID.GQ_802154, 1, 1, 1);

	/** Firmware version code 6 is what gives a GQ's file its 3-byte timestamps
	 * (ShimmerObject.updateTimestampByteLength). A node had none, which read them as 2. */
	@Test
	public void aNodeTakesTheGqsFirmwareVersionCode() {
		assertEquals(6, GQ.mFirmwareVersionCode);
		assertEquals(GQ.mFirmwareVersionCode, SR68_NODE.mFirmwareVersionCode);
		assertEquals(GQ.mFirmwareVersionCode, SR61_NODE.mFirmwareVersionCode);
	}

	@Test
	public void aStockVerisensesVersionCodeIsUnchanged() {
		assertEquals(-1, STOCK_VERISENSE.mFirmwareVersionCode);
	}

	@Test
	public void aNodesFilesAreImportedAndAStockVerisensesAreNot() {
		assertTrue(ShimmerVerObject.isSupportedSdDataImport(SR68_NODE.getHardwareVersion(), SR68_NODE.getFirmwareIdentifier()));
		assertTrue(ShimmerVerObject.isSupportedSdDataImport(SR61_NODE.getHardwareVersion(), SR61_NODE.getFirmwareIdentifier()));
		assertTrue(ShimmerVerObject.isSupportedSdDataImport(GQ.getHardwareVersion(), GQ.getFirmwareIdentifier()));
		assertFalse(ShimmerVerObject.isSupportedSdDataImport(STOCK_VERISENSE.getHardwareVersion(), STOCK_VERISENSE.getFirmwareIdentifier()));
		// The one-argument form is unchanged: it cannot tell a node from a stock Verisense
		assertFalse(ShimmerVerObject.isSupportedSdDataImport(SR68_NODE.getHardwareVersion()));
	}

	/** A node's sessions are downloaded over its dock link; it has no card to scan */
	@Test
	public void onlyANodeDownloadsItsStorage() {
		assertTrue(SR68_NODE.isSupportedNodeStorageDownload());
		assertTrue(SR61_NODE.isSupportedNodeStorageDownload());
		assertFalse(GQ.isSupportedNodeStorageDownload());
		assertFalse(STOCK_VERISENSE.isSupportedNodeStorageDownload());
		assertFalse(SR68_NODE.isSupportedSdCardAccess());
	}

	/** Header byte 15's low three bits, green, IR and red, in record order */
	@Test
	public void thePpgMaskGivesTheChannelsInRecordOrder() {
		assertEquals(Collections.emptyList(), SensorPPGNeuroLynQ.channelsForMask(0));
		assertEquals(Arrays.asList(ObjectClusterSensorName.PPG_GREEN), SensorPPGNeuroLynQ.channelsForMask(SensorPPGNeuroLynQ.MASK_GREEN));
		assertEquals(Arrays.asList(ObjectClusterSensorName.PPG_IR, ObjectClusterSensorName.PPG_RED),
				SensorPPGNeuroLynQ.channelsForMask(SensorPPGNeuroLynQ.MASK_IR | SensorPPGNeuroLynQ.MASK_RED));
		assertEquals(ObjectClusterSensorName.IN_RECORD_ORDER, SensorPPGNeuroLynQ.channelsForMask(SensorPPGNeuroLynQ.MASK_ALL));
		// Only the low three bits count, as the firmware and the web SDK count them
		assertEquals(ObjectClusterSensorName.IN_RECORD_ORDER, SensorPPGNeuroLynQ.channelsForMask(0xFF));
	}

	@Test
	public void thePpgSensorIsEnabledByHeaderBit16AndNeverStreamed() {
		assertEquals(1 << 16, SDLogHeader.NEUROLYNQ_PPG);
		assertEquals(SDLogHeader.NEUROLYNQ_PPG, SensorPPGNeuroLynQ.sensorPpgNeuroLynQ.mSensorBitmapIDSDLogHeader);
		assertEquals(0, SensorPPGNeuroLynQ.sensorPpgNeuroLynQ.mSensorBitmapIDStreaming);
	}

	/** The sensor stores only the channels its file logs, so a green-only node's database
	 * gets no IR or red columns */
	@Test
	public void theMaskNarrowsTheChannelsStored() {
		SensorPPGNeuroLynQ sensor = new SensorPPGNeuroLynQ(SR68_NODE);
		SensorDetails details = sensor.mSensorMap.get(Configuration.Shimmer3.SENSOR_ID.NEUROLYNQ_PPG);
		assertNotNull(details);
		// Green only until told otherwise, as a node logs until configured otherwise
		assertEquals(SensorPPGNeuroLynQ.MASK_GREEN, sensor.getChannelMask());
		assertEquals(Arrays.asList(ObjectClusterSensorName.PPG_GREEN), channelNames(details.mListOfChannels));
		assertEquals(SensorPPGNeuroLynQ.DatabaseChannelHandles.PPG_GREEN, details.mListOfChannels.get(0).getDatabaseChannelHandle());

		sensor.setChannelMask(SensorPPGNeuroLynQ.MASK_ALL);
		assertEquals(ObjectClusterSensorName.IN_RECORD_ORDER, channelNames(details.mListOfChannels));

		sensor.setChannelMask(SensorPPGNeuroLynQ.MASK_GREEN | SensorPPGNeuroLynQ.MASK_RED);
		assertEquals(Arrays.asList(ObjectClusterSensorName.PPG_GREEN, ObjectClusterSensorName.PPG_RED), channelNames(details.mListOfChannels));
	}

	/** InfoMem 263 as the firmware reads it (gq_file_header.c GqFile_ppgMask()) */
	@Test
	public void anUnwrittenOrEmptyMaskIsGreenOnly() {
		assertEquals(SensorPPGNeuroLynQ.MASK_GREEN, SensorPPGNeuroLynQ.maskFromInfoMem(0xFF));
		assertEquals(SensorPPGNeuroLynQ.MASK_GREEN, SensorPPGNeuroLynQ.maskFromInfoMem(0x00));
		assertEquals(SensorPPGNeuroLynQ.MASK_GREEN, SensorPPGNeuroLynQ.maskFromInfoMem(0xF8));
		assertEquals(SensorPPGNeuroLynQ.MASK_IR | SensorPPGNeuroLynQ.MASK_RED, SensorPPGNeuroLynQ.maskFromInfoMem(0x06));
		assertEquals(SensorPPGNeuroLynQ.MASK_ALL, SensorPPGNeuroLynQ.maskFromInfoMem(0x0F));
		assertEquals(SensorPPGNeuroLynQ.MASK_ALL, SensorPPGNeuroLynQ.maskFromInfoMem((byte) 0x87));
	}

	/** The mask is stored with the device's configuration, and comes back from it */
	@Test
	public void theMaskRoundTripsThroughTheConfigMap() {
		SensorPPGNeuroLynQ sensor = new SensorPPGNeuroLynQ(SR68_NODE);
		sensor.setChannelMask(SensorPPGNeuroLynQ.MASK_IR | SensorPPGNeuroLynQ.MASK_RED);
		java.util.LinkedHashMap<String, Object> config = sensor.generateConfigMap();
		assertEquals(6.0, (Double) config.get(SensorPPGNeuroLynQ.DatabaseConfigHandle.PPG_MASK), 0.0);

		SensorPPGNeuroLynQ restored = new SensorPPGNeuroLynQ(SR68_NODE);
		restored.parseConfigMap(config);
		assertEquals(SensorPPGNeuroLynQ.MASK_IR | SensorPPGNeuroLynQ.MASK_RED, restored.getChannelMask());
	}

	@Test
	public void eachPpgChannelIsA24BitLittleEndianCount() {
		for (ChannelDetails channel : Arrays.asList(SensorPPGNeuroLynQ.channelPpgGreen, SensorPPGNeuroLynQ.channelPpgIr, SensorPPGNeuroLynQ.channelPpgRed)) {
			assertEquals(3, channel.mDefaultNumBytes);
			assertEquals(ChannelDetails.CHANNEL_DATA_TYPE.UINT24, channel.mDefaultChannelDataType);
			assertEquals(ChannelDetails.CHANNEL_DATA_ENDIAN.LSB, channel.mDefaultChannelDataEndian);
		}
	}

	/** The byte after the radio settings, where the firmware reads it (gq_file_header.c IM_PPG_MASK) */
	@Test
	public void theNodesPpgMaskIsInfoMem263() {
		ConfigByteLayoutShimmerGq802154 layout = new ConfigByteLayoutShimmerGq802154(FW_ID.GQ_802154, 2, 1, 1);
		assertEquals(263, layout.idxNeuroLynQPpgMask);
		assertEquals(layout.idxSrRadioConfigStart + layout.lengthRadioConfig, layout.idxNeuroLynQPpgMask);
		assertEquals(0x07, layout.maskNeuroLynQPpgMask);
	}

	private static List<String> channelNames(List<ChannelDetails> channels) {
		String[] names = new String[channels.size()];
		for (int i = 0; i < names.length; i++) {
			names[i] = channels.get(i).mObjectClusterName;
		}
		return Arrays.asList(names);
	}
}
