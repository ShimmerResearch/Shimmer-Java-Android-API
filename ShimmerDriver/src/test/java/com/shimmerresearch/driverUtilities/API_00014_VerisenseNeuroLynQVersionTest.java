package com.shimmerresearch.driverUtilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.sensors.SensorADC.MICROCONTROLLER_ADC_PROPERTIES;

/**
 * A Verisense in NeuroLynQ mode is a GQ to the host (DEV-1047).
 * <p>
 * Docked, it answers VER with its own hardware ID and the GQ's firmware ID. On the bench
 * on 2026-09-24 that was hardware 68, firmware 9, v2.1.1, which the host used to leave as
 * an unrecognised device with "Unknown" firmware.
 */
public class API_00014_VerisenseNeuroLynQVersionTest {

	private static final ShimmerVerObject NODE = new ShimmerVerObject(HW_ID.VERISENSE_PULSE_PLUS, FW_ID.GQ_802154, 2, 1, 1);
	private static final ShimmerVerObject STOCK_VERISENSE = new ShimmerVerObject(HW_ID.VERISENSE_PULSE_PLUS, FW_ID.VERISENSE, 2, 1, 1);
	private static final ShimmerVerObject GQ = new ShimmerVerObject(HW_ID.SHIMMER_GQ_802154_NR, FW_ID.GQ_802154, 1, 1, 1);

	@Test
	public void aNeuroLynQVerisenseIsAGq() {
		assertTrue(NODE.isVerisenseNeuroLynQ());
		assertTrue(NODE.isShimmerGenGq());
		assertFalse(NODE.isShimmerGenVerisense());
	}

	@Test
	public void everyVerisenseRunningTheGqFirmwareIsAGq() {
		for (int hwId : new int[] {HW_ID.VERISENSE_IMU, HW_ID.VERISENSE_GSR_PLUS, HW_ID.VERISENSE_PPG,
				HW_ID.VERISENSE_DEV_BRD, HW_ID.VERISENSE_PULSE_PLUS}) {
			assertTrue("hardware " + hwId, new ShimmerVerObject(hwId, FW_ID.GQ_802154, 2, 1, 1).isShimmerGenGq());
		}
	}

	@Test
	public void aStockVerisenseIsStillAVerisense() {
		assertFalse(STOCK_VERISENSE.isVerisenseNeuroLynQ());
		assertFalse(STOCK_VERISENSE.isShimmerGenGq());
		assertTrue(STOCK_VERISENSE.isShimmerGenVerisense());
	}

	@Test
	public void aGqIsUnchanged() {
		assertTrue(GQ.isShimmerGenGq());
		assertFalse(GQ.isVerisenseNeuroLynQ());
		assertFalse(GQ.isShimmerGenVerisense());
	}

	@Test
	public void itsFirmwareIsNamedAsAGqs() {
		String gqFirmwareLabel = FW_ID.mMapOfFirmwareLabels.get(FW_ID.GQ_802154);
		assertEquals(gqFirmwareLabel, NODE.mFirmwareIdentifierParsed);
		assertEquals(gqFirmwareLabel + " v2.1.1", NODE.getFirmwareVersionParsed());
	}

	/** Docked, its InfoMem image and clock are read and written as a GQ's are */
	@Test
	public void itIsConfiguredOverTheDockLinkAsAGqIs() {
		assertTrue(NODE.isSupportedConfigViaUart());
		assertTrue(NODE.isSupportedRtcConfigViaUart());
		assertFalse(STOCK_VERISENSE.isSupportedConfigViaUart());
		assertFalse(STOCK_VERISENSE.isSupportedRtcConfigViaUart());
	}

	/** Unlike a GQ it has no SD card, and its USB has no mass storage */
	@Test
	public void itHasNoSdCard() {
		assertFalse(NODE.isSupportedSdCardAccess());
		assertTrue(GQ.isSupportedSdCardAccess());
	}

	/** It sends GQ data, already scaled to the GQ's ADC, so it takes the GQ's calibration */
	@Test
	public void itTakesTheGqsAdcProperties() {
		assertEquals(MICROCONTROLLER_ADC_PROPERTIES.getMicrocontrollerAdcPropertiesForShimmerVersionObject(GQ),
				MICROCONTROLLER_ADC_PROPERTIES.getMicrocontrollerAdcPropertiesForShimmerVersionObject(NODE));
		assertEquals(MICROCONTROLLER_ADC_PROPERTIES.VERISENSE_1V8,
				MICROCONTROLLER_ADC_PROPERTIES.getMicrocontrollerAdcPropertiesForShimmerVersionObject(STOCK_VERISENSE));
	}
}
