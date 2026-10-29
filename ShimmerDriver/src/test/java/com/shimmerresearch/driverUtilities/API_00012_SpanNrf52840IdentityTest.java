package com.shimmerresearch.driverUtilities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.shimmerresearch.driverUtilities.HwDriverShimmerDeviceDetails.SPAN_VERSION;

/**
 * How the host recognises the nRF52840 Span, and what it assumes about one once found
 * (DEV-1047).
 * <p>
 * The Span is matched on Nordic's vendor ID and its USB product string, not on its
 * PID: 0x5210 is provisional and unallocated, and a PID match would need a host release
 * whenever it changes. The strings below are the ones Windows reported for the bench
 * devices on 2026-09-24.
 */
public class API_00012_SpanNrf52840IdentityTest {

	/** iProduct of the nRF52840 Span, verisense-firmware SR9_SPAN_NRF52840 */
	private static final String SPAN_NRF52840_PRODUCT = "NeuroLynQ Span";
	/** A Verisense in NeuroLynQ mode, on the same VID. It is a node, not a Span. */
	private static final String VERISENSE_NEUROLYNQ_PRODUCT = "Verisense-NeuroLynQ-01-2511210195BC";
	private static final String VERISENSE_PRODUCT = "Verisense-01-2511210195BC";
	private static final String SR9_SPAN_PRODUCT = "SHIMMER SPAN SR1-3.1";

	@Test
	public void matchesNordicVendorAndSpanProductString() {
		assertTrue(HwDriverShimmerDeviceDetails.isSpanNrf52840("1915", SPAN_NRF52840_PRODUCT));
	}

	@Test
	public void productStringMatchIgnoresCase() {
		assertTrue(HwDriverShimmerDeviceDetails.isSpanNrf52840("1915", SPAN_NRF52840_PRODUCT.toUpperCase()));
	}

	@Test
	public void rejectsTheProductStringUnderAnotherVendor() {
		assertFalse(HwDriverShimmerDeviceDetails.isSpanNrf52840(
				HwDriverShimmerDeviceDetails.SH_SEARCH.SERIAL_PORT.FTDI_VEND_ID, SPAN_NRF52840_PRODUCT));
	}

	@Test
	public void rejectsOtherNordicDevices() {
		assertFalse(HwDriverShimmerDeviceDetails.isSpanNrf52840("1915", VERISENSE_NEUROLYNQ_PRODUCT));
		assertFalse(HwDriverShimmerDeviceDetails.isSpanNrf52840("1915", VERISENSE_PRODUCT));
	}

	@Test
	public void rejectsTheSr9() {
		assertFalse(HwDriverShimmerDeviceDetails.isSpanNrf52840(
				HwDriverShimmerDeviceDetails.SH_SEARCH.SERIAL_PORT.FTDI_VEND_ID, SR9_SPAN_PRODUCT));
	}

	@Test
	public void rejectsMissingValues() {
		assertFalse(HwDriverShimmerDeviceDetails.isSpanNrf52840(null, SPAN_NRF52840_PRODUCT));
		assertFalse(HwDriverShimmerDeviceDetails.isSpanNrf52840("1915", null));
		assertFalse(HwDriverShimmerDeviceDetails.isSpanNrf52840("", ""));
	}

	@Test
	public void nrf52840SpanIsSupportedButHasNoBsl() {
		assertTrue(SPAN_VERSION.SPAN_NRF52840.isSupported());
		assertFalse(SPAN_VERSION.SPAN_NRF52840.isMspBslSupported());
	}

	@Test
	public void msp430SpansKeepTheirBsl() {
		assertTrue(SPAN_VERSION.SPAN_SR1_3_1.isSupported());
		assertTrue(SPAN_VERSION.SPAN_SR1_3_1.isMspBslSupported());
		assertFalse(SPAN_VERSION.SPAN_SR1_3_0.isSupported());
		assertTrue(SPAN_VERSION.SPAN_SR1_3_0.isMspBslSupported());
		assertFalse(SPAN_VERSION.VIRTUAL.isMspBslSupported());
		assertFalse(SPAN_VERSION.UNKNOWN.isMspBslSupported());
	}
}
