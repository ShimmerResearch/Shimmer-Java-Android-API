package com.shimmerresearch.sensors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Verisense;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.sensors.SensorADC.MICROCONTROLLER_ADC_PROPERTIES;
import com.shimmerresearch.sensors.SensorGSR.ObjectClusterSensorName;
import com.shimmerresearch.verisense.VerisenseDevice;
import com.shimmerresearch.verisense.sensors.SensorGSRVerisense;
import com.shimmerresearch.verisense.sensors.SensorGSRVerisense.GSR_RANGE;

/**
 * A GSR code below the amplifier reference is an open circuit, and has to decode as one on every
 * range (DEV-1070).
 * <p>
 * No skin resistance can pull the amplifier's output under its reference, so the equation
 * R = Rf / (V / Vref - 1) goes negative there. Range 3 has long raised such codes to its
 * open-circuit limit. Ranges 0-2 did not, and in auto-range they see them: when the electrodes come
 * off, the device climbs one range at a time and repeats the sample that triggered each switch for
 * 80 ms, tagged with the range it was measured on. The auto-range nudge then floored the negative
 * resistance at 8 kOhm, so an open circuit read 125 uS, the highest conductance the device can
 * report. DEV-793 dataset B6 (ASM_PC Test_056, SR68-9 2511210195BC) has 50 such samples, and their
 * raw words carry these ranges and codes:
 * <ul>
 * <li>at the start of the recording: range 0 at codes 0 and 1131, range 1 at 1131, range 2 at
 * 1132</li>
 * <li>as the 33 kOhm load comes off: range 1 at 1137</li>
 * </ul>
 * Every code below the limit now decodes as range 3 at the limit. The rule is the same on every front
 * end: gen-2 (SR68, SR61-5/6, limit 1138), the Verisense GSR+ (SR62), and the Shimmer3 and ShimmerGQ
 * (limit 683).
 */
public class API_00019_GsrBelowReferenceReadsOpen {

	/** Conductance below this reads as Disconnected in the C# API and the web SDK. */
	private static final double DISCONNECTED_BELOW_USIEMENS = 0.03;
	private static final double[][] WINDOWS_KOHMS = SensorGSR.SHIMMER3_GSR_RESISTANCE_MIN_MAX_KOHMS;
	private static final int AUTO_RANGE = 4;

	/** The below-reference codes in Test_056, by the range they were measured on. */
	private static final int[][] TEST_056_CODES_BY_RANGE = new int[][] {
			{0, 1131},
			{1131, 1137},
			{1132}};

	private enum FrontEnd {
		SHIMMER3(MICROCONTROLLER_ADC_PROPERTIES.SHIMMER2R3_3V0, SensorGSR.SHIMMER3_GSR_REF_RESISTORS_KOHMS,
				SensorGSR.GSR_UNCAL_LIMIT_RANGE3),
		VERISENSE_GSR_PLUS(MICROCONTROLLER_ADC_PROPERTIES.VERISENSE_3V0, SensorGSR.SHIMMER3_GSR_REF_RESISTORS_KOHMS,
				SensorGSR.GSR_UNCAL_LIMIT_RANGE3),
		VERISENSE_GEN2(MICROCONTROLLER_ADC_PROPERTIES.VERISENSE_1V8,
				SensorGSRVerisense.VERISENSE_PULSE_PLUS_GSR_REF_RESISTORS_KOHMS,
				SensorGSRVerisense.VERISENSE_PULSE_PLUS_GSR_UNCAL_LIMIT_RANGE3);

		final MICROCONTROLLER_ADC_PROPERTIES adc;
		final double[] refResistorsKohms;
		final int limit;

		FrontEnd(MICROCONTROLLER_ADC_PROPERTIES adc, double[] refResistorsKohms, int limit) {
			this.adc = adc;
			this.refResistorsKohms = refResistorsKohms;
			this.limit = limit;
		}

		double equation(int code, int range) {
			return SensorGSR.calibrateGsrDataToKOhmsUsingAmplifierEq(code, range, adc, refResistorsKohms);
		}

		double withOpenCircuitLimit(int code, int range) {
			return SensorGSR.calibrateGsrDataToKOhmsWithOpenCircuitLimit(code, range, limit, adc, refResistorsKohms);
		}

		double openCircuitKohms() {
			return equation(limit, 3);
		}
	}

	@Test
	public void test001_everyCodeBelowTheLimitDecodesAsRange3AtTheLimit() {
		for (FrontEnd frontEnd : FrontEnd.values()) {
			double open = frontEnd.openCircuitKohms();
			for (int range = 0; range <= 3; range++) {
				for (int code = 0; code < frontEnd.limit; code++) {
					assertEquals(frontEnd + " range " + range + " code " + code, open,
							frontEnd.withOpenCircuitLimit(code, range), 0.0);
				}
			}
			assertTrue(frontEnd + " reads an open circuit as " + (1000.0 / open) + " uS",
					1000.0 / open < DISCONNECTED_BELOW_USIEMENS);
		}
	}

	/**
	 * The limit is the first code above the 0.5 V reference on every front end, so the rule moves
	 * exactly the codes that the equation decoded to a negative resistance, which are the samples
	 * that ASM-2156's 8 kOhm floor already moved. Every other code decodes as it always has.
	 */
	@Test
	public void test002_onlyCodesTheEquationDecodedNegativeChange() {
		for (FrontEnd frontEnd : FrontEnd.values()) {
			for (int range = 0; range <= 3; range++) {
				for (int code = 0; code <= 4095; code++) {
					String context = frontEnd + " range " + range + " code " + code;
					double equation = frontEnd.equation(code, range);
					if (code < frontEnd.limit) {
						assertTrue(context + " decodes to " + equation + " kOhm", equation < 0);
					} else {
						assertTrue(context + " decodes to " + equation + " kOhm", equation > 0);
						assertEquals(context, equation, frontEnd.withOpenCircuitLimit(code, range), 0.0);
					}
				}
			}
		}
	}

	/** Range 0 at full scale already decodes above 8 kOhm, so nothing reaches the auto-range floor. */
	@Test
	public void test003_theAutoRangeFloorIsNoLongerReached() {
		for (FrontEnd frontEnd : FrontEnd.values()) {
			for (int range = 0; range <= 3; range++) {
				for (int code = 0; code <= 4095; code++) {
					double kOhms = frontEnd.withOpenCircuitLimit(code, range);
					assertEquals(frontEnd + " range " + range + " code " + code, kOhms,
							SensorGSR.nudgeGsrResistance(kOhms, AUTO_RANGE, WINDOWS_KOHMS), 0.0);
				}
			}
		}
	}

	@Test
	public void test004_sr68AutoRangeTransientFromTest056ReadsOpen() {
		SensorGSRVerisense sensorGsr = createVerisenseSensorGsr(HW_ID.VERISENSE_PULSE_PLUS, 9, GSR_RANGE.AUTO_RANGE);
		assertAutoRangeReadsOpen(sensorGsr, FrontEnd.VERISENSE_GEN2, TEST_056_CODES_BY_RANGE);
	}

	@Test
	public void test005_sr61AutoRangeTransientFromTest056ReadsOpen() {
		SensorGSRVerisense sensorGsr = createVerisenseSensorGsr(HW_ID.VERISENSE_IMU, 5, GSR_RANGE.AUTO_RANGE);
		assertAutoRangeReadsOpen(sensorGsr, FrontEnd.VERISENSE_GEN2, TEST_056_CODES_BY_RANGE);
	}

	@Test
	public void test006_sr62AutoRangeBelowReferenceReadsOpen() {
		SensorGSRVerisense sensorGsr = createVerisenseSensorGsr(HW_ID.VERISENSE_GSR_PLUS, 1, GSR_RANGE.AUTO_RANGE);
		assertAutoRangeReadsOpen(sensorGsr, FrontEnd.VERISENSE_GSR_PLUS, codesBelowLimit(FrontEnd.VERISENSE_GSR_PLUS));
	}

	/**
	 * A fixed range still clamps to its own window (DEV-1068), but an open circuit now pins it to
	 * the top of the window, as fixed range 3 already did, instead of the bottom.
	 */
	@Test
	public void test007_verisenseFixedRangesReadTheTopOfTheirWindow() {
		GSR_RANGE[] fixedRanges = new GSR_RANGE[] {GSR_RANGE.RANGE_0, GSR_RANGE.RANGE_1, GSR_RANGE.RANGE_2, GSR_RANGE.RANGE_3};
		for (int range = 0; range <= 3; range++) {
			SensorGSRVerisense sr68 = createVerisenseSensorGsr(HW_ID.VERISENSE_PULSE_PLUS, 9, fixedRanges[range]);
			assertFixedRangeReadsTopOfWindow(sr68, range, codesBelowLimit(FrontEnd.VERISENSE_GEN2)[0]);
			SensorGSRVerisense sr62 = createVerisenseSensorGsr(HW_ID.VERISENSE_GSR_PLUS, 1, fixedRanges[range]);
			assertFixedRangeReadsTopOfWindow(sr62, range, codesBelowLimit(FrontEnd.VERISENSE_GSR_PLUS)[0]);
		}
	}

	@Test
	public void test008_shimmer3BelowReferenceReadsOpen() {
		SensorGSR sensorGsr = new SensorGSR(new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 0, 16, 0));
		sensorGsr.setGSRRange(AUTO_RANGE);
		assertAutoRangeReadsOpen(sensorGsr, FrontEnd.SHIMMER3, codesBelowLimit(FrontEnd.SHIMMER3));

		for (int range = 0; range <= 3; range++) {
			sensorGsr.setGSRRange(range);
			assertFixedRangeReadsTopOfWindow(sensorGsr, range, codesBelowLimit(FrontEnd.SHIMMER3)[0]);
		}
	}

	/** The ShimmerGQ decodes to a single channel, in uS. */
	@Test
	public void test009_shimmerGqBelowReferenceReadsOpen() {
		SensorGSR sensorGsr = new SensorGSR(new ShimmerVerObject(HW_ID.SHIMMER_GQ_802154_NR, FW_ID.GQ_802154, 0, 4, 1));
		sensorGsr.setGSRRange(AUTO_RANGE);
		double openUS = 1000.0 / FrontEnd.SHIMMER3.openCircuitKohms();
		int[][] codesByRange = codesBelowLimit(FrontEnd.SHIMMER3);
		for (int range = 0; range < codesByRange.length; range++) {
			for (int code : codesByRange[range]) {
				ObjectCluster ojc = decodeSample(sensorGsr, Configuration.Shimmer3.SENSOR_ID.SHIMMER_GSR, range, code);
				double uS = ojc.getFormatClusterValue(ObjectClusterSensorName.GSR_GQ, CHANNEL_TYPE.CAL.toString());
				assertEquals("GQ range " + range + " code " + code, openUS, uS, openUS * 1e-12);
				assertTrue("GQ range " + range + " code " + code + " read " + uS + " uS", uS < DISCONNECTED_BELOW_USIEMENS);
			}
		}
	}

	private static void assertAutoRangeReadsOpen(SensorGSR sensorGsr, FrontEnd frontEnd, int[][] codesByRange) {
		double openKohms = frontEnd.openCircuitKohms();
		int sensorId = sensorId(sensorGsr);
		for (int range = 0; range < codesByRange.length; range++) {
			for (int code : codesByRange[range]) {
				ObjectCluster ojc = decodeSample(sensorGsr, sensorId, range, code);
				String context = frontEnd + " auto-range, range " + range + " code " + code;
				double kOhms = ojc.getFormatClusterValue(ObjectClusterSensorName.GSR_RESISTANCE, CHANNEL_TYPE.CAL.toString());
				double uS = ojc.getFormatClusterValue(ObjectClusterSensorName.GSR_CONDUCTANCE, CHANNEL_TYPE.CAL.toString());
				double reportedRange = ojc.getFormatClusterValue(ObjectClusterSensorName.GSR_RANGE, CHANNEL_TYPE.CAL.toString());

				assertEquals(context, openKohms, kOhms, 0.0);
				assertTrue(context + " read " + uS + " uS", uS > 0 && uS < DISCONNECTED_BELOW_USIEMENS);
				// Only the resistance changes: the range channel still says which resistor was in circuit
				assertEquals(context, range, reportedRange, 0.0);
			}
		}
	}

	private static void assertFixedRangeReadsTopOfWindow(SensorGSR sensorGsr, int range, int[] codes) {
		for (int code : codes) {
			ObjectCluster ojc = decodeSample(sensorGsr, sensorId(sensorGsr), range, code);
			double kOhms = ojc.getFormatClusterValue(ObjectClusterSensorName.GSR_RESISTANCE, CHANNEL_TYPE.CAL.toString());
			assertEquals("fixed range " + range + " code " + code, WINDOWS_KOHMS[range][1], kOhms, 0.0);
		}
	}

	/** 0, and the last code below the limit, on each of ranges 0-2. */
	private static int[][] codesBelowLimit(FrontEnd frontEnd) {
		int[] codes = new int[] {0, frontEnd.limit - 1};
		return new int[][] {codes, codes, codes};
	}

	private static int sensorId(SensorGSR sensorGsr) {
		return sensorGsr instanceof SensorGSRVerisense ? Verisense.SENSOR_ID.GSR : Configuration.Shimmer3.SENSOR_ID.SHIMMER_GSR;
	}

	private static SensorGSRVerisense createVerisenseSensorGsr(int hwId, int hwRev, GSR_RANGE gsrRange) {
		ExpansionBoardDetails ebd = new ExpansionBoardDetails(hwId, hwRev, 0);
		VerisenseDevice verisenseDevice = new VerisenseDevice();
		verisenseDevice.setShimmerVersionObject(VerisenseDevice.FW_CHANGES.CCF21_010_3);
		verisenseDevice.setHardwareVersion(ebd.getExpansionBoardId());
		verisenseDevice.setExpansionBoardDetails(ebd);
		verisenseDevice.sensorAndConfigMapsCreate();

		SensorGSRVerisense sensorGsr = verisenseDevice.getSensorGsr();
		assertNotNull("no GSR sensor registered for SR" + hwId + "-" + hwRev, sensorGsr);
		sensorGsr.setGsrRange(gsrRange);
		return sensorGsr;
	}

	/** One GSR sample as the firmware sends it: range in bits 15-14 over the 12-bit code, LSB first. */
	private static ObjectCluster decodeSample(SensorGSR sensorGsr, int sensorId, int range, int code) {
		int raw = (range << 14) | code;
		byte[] sample = new byte[] {(byte) (raw & 0xFF), (byte) ((raw >> 8) & 0xFF)};
		SensorDetails sensorDetails = sensorGsr.getSensorDetails(sensorId);
		assertNotNull("no GSR sensor details", sensorDetails);
		return sensorGsr.processDataCustom(sensorDetails, sample, COMMUNICATION_TYPE.SD, new ObjectCluster(), false, 0);
	}
}
