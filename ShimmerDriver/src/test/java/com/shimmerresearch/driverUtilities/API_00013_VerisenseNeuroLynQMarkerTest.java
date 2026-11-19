package com.shimmerresearch.driverUtilities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * How the host tells a NeuroLynQ-mode Verisense from a stock one (DEV-1047).
 * <p>
 * Both enumerate as 1915:520F. The NeuroLynQ firmware adds "-NeuroLynQ" after the name
 * prefix in its USB product string, whichever prefix it has, and nothing else changes.
 * The first string is the bench node's on 2026-09-24.
 */
public class API_00013_VerisenseNeuroLynQMarkerTest {

	@Test
	public void recognisesTheNeuroLynQMarker() {
		assertTrue(HwDriverShimmerDeviceDetails.isVerisenseNeuroLynQ("Verisense-NeuroLynQ-01-2511210195BC"));
	}

	/** Without a passkey ID, and with the factory string an unprovisioned unit reports */
	@Test
	public void recognisesItWithoutAPasskeyOrAProductionConfig() {
		assertTrue(HwDriverShimmerDeviceDetails.isVerisenseNeuroLynQ("Verisense-NeuroLynQ-2511210195BC"));
		assertTrue(HwDriverShimmerDeviceDetails.isVerisenseNeuroLynQ("Verisense-NeuroLynQ-FFFFFFFF95BC"));
	}

	/** The marker follows whatever name prefix the production config carries */
	@Test
	public void recognisesItAfterAProvisionedPrefix() {
		assertTrue(HwDriverShimmerDeviceDetails.isVerisenseNeuroLynQ("Acme-NeuroLynQ-01-2511210195BC"));
	}

	@Test
	public void aStockVerisenseIsNotNeuroLynQ() {
		assertFalse(HwDriverShimmerDeviceDetails.isVerisenseNeuroLynQ("Verisense-01-2511210195BC"));
		assertFalse(HwDriverShimmerDeviceDetails.isVerisenseNeuroLynQ("Verisense-FFFFFFFF95BC"));
	}

	/** The nRF52840 Span says NeuroLynQ too, but it is a Span, not a node */
	@Test
	public void theSpanIsNotANode() {
		assertFalse(HwDriverShimmerDeviceDetails.isVerisenseNeuroLynQ("NeuroLynQ Span"));
	}

	@Test
	public void noProductStringIsNotNeuroLynQ() {
		assertFalse(HwDriverShimmerDeviceDetails.isVerisenseNeuroLynQ(null));
		assertFalse(HwDriverShimmerDeviceDetails.isVerisenseNeuroLynQ(""));
	}
}
