package com.shimmerresearch.verisense.sensors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Verisense;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.sensors.SensorADC.MICROCONTROLLER_ADC_PROPERTIES;
import com.shimmerresearch.sensors.SensorGSR;
import com.shimmerresearch.sensors.SensorGSR.ObjectClusterSensorName;
import com.shimmerresearch.verisense.VerisenseDevice;
import com.shimmerresearch.verisense.sensors.SensorGSRVerisense.GSR_RANGE;

/**
 * An open circuit on GSR range 3 of the second-generation front end (Pulse+ SR68, and the
 * SR61-5/6 IMU) has to decode as open (DEV-1067).
 * <p>
 * With nothing across the electrodes the amplifier's gain falls to one and its output settles on
 * the reference, so the ADC reads a few codes either side of it. The decode raises every range-3
 * code below {@link SensorGSRVerisense#VERISENSE_PULSE_PLUS_GSR_UNCAL_LIMIT_RANGE3} to that limit,
 * which only works if the limit is itself above the reference. It was 1134, below the 0.5 V this
 * decode divides by (code 1137.5 at 1.8 V), so codes 1134 to 1137 decoded to a negative
 * resistance: auto-range floored it at 8 kOhm, the highest conductance the device can report, and
 * fixed range 3 pinned it to 680 kOhm, the bottom of the range. On an SR68-9 with the electrodes
 * open (DEV-793 dataset B6, ASM_PC Test_056) the range-3 codes peak at 1126-1136, so most of an
 * open-circuit recording read 125 uS.
 * <p>
 * Each case goes through the real decode: {@link VerisenseDevice} builds the sensor class for the
 * board and {@link SensorGSR#processDataCustom} parses the sample, so the wiring from hardware ID
 * to limit is covered as well as the arithmetic.
 */
public class API_00015_VerisenseGsrOpenCircuitLimit {

	private static final int RANGE_3 = 3;
	/** Top of range 3, 4.7 MOhm - an open circuit should read at least this. */
	private static final double RANGE_3_MAX_KOHMS = SensorGSR.SHIMMER3_GSR_RESISTANCE_MIN_MAX_KOHMS[RANGE_3][1];
	/** Codes around the gen-2 amplifier reference, where an open circuit sits. */
	private static final int FIRST_CODE = 1134, LAST_CODE = 1138;

	@Test
	public void test001_sr68AutoRangeOpenCircuitReadsOpen() {
		assertOpenCircuitReadsOpen(HW_ID.VERISENSE_PULSE_PLUS, 9, GSR_RANGE.AUTO_RANGE);
	}

	@Test
	public void test002_sr68FixedRange3OpenCircuitReadsOpen() {
		assertOpenCircuitReadsOpen(HW_ID.VERISENSE_PULSE_PLUS, 9, GSR_RANGE.RANGE_3);
	}

	@Test
	public void test003_sr61AutoRangeOpenCircuitReadsOpen() {
		assertOpenCircuitReadsOpen(HW_ID.VERISENSE_IMU, 5, GSR_RANGE.AUTO_RANGE);
	}

	@Test
	public void test004_sr61FixedRange3OpenCircuitReadsOpen() {
		assertOpenCircuitReadsOpen(HW_ID.VERISENSE_IMU, 5, GSR_RANGE.RANGE_3);
	}

	/**
	 * The limit is exactly the first code above the reference: one lower and the clamp produces
	 * negative resistances again, any higher and it overwrites codes that decode properly.
	 */
	@Test
	public void test005_limitIsTheFirstCodeAboveTheAmplifierReference() {
		int limit = SensorGSRVerisense.VERISENSE_PULSE_PLUS_GSR_UNCAL_LIMIT_RANGE3;
		assertTrue(calibrateRange3(limit) > 0);
		assertTrue(calibrateRange3(limit - 1) < 0);
	}

	private static void assertOpenCircuitReadsOpen(int hwId, int hwRev, GSR_RANGE gsrRange) {
		SensorGSRVerisense sensorGsr = createSensorGsr(hwId, hwRev);
		sensorGsr.setGsrRange(gsrRange);

		for (int code = FIRST_CODE; code <= LAST_CODE; code++) {
			ObjectCluster ojc = decodeRange3Sample(sensorGsr, code);
			double kOhms = ojc.getFormatClusterValue(ObjectClusterSensorName.GSR_RESISTANCE, CHANNEL_TYPE.CAL.toString());
			double uS = ojc.getFormatClusterValue(ObjectClusterSensorName.GSR_CONDUCTANCE, CHANNEL_TYPE.CAL.toString());

			String context = "SR" + hwId + "-" + hwRev + " " + gsrRange + " code " + code;
			assertTrue(context + " read " + kOhms + " kOhm", kOhms >= RANGE_3_MAX_KOHMS);
			assertTrue(context + " read " + uS + " uS", uS > 0 && uS <= 1000.0 / RANGE_3_MAX_KOHMS);
		}
	}

	private static SensorGSRVerisense createSensorGsr(int hwId, int hwRev) {
		ExpansionBoardDetails ebd = new ExpansionBoardDetails(hwId, hwRev, 0);
		VerisenseDevice verisenseDevice = new VerisenseDevice();
		verisenseDevice.setShimmerVersionObject(VerisenseDevice.FW_CHANGES.CCF21_010_3);
		verisenseDevice.setHardwareVersion(ebd.getExpansionBoardId());
		verisenseDevice.setExpansionBoardDetails(ebd);
		verisenseDevice.sensorAndConfigMapsCreate();

		SensorGSRVerisense sensorGsr = verisenseDevice.getSensorGsr();
		assertNotNull("no GSR sensor registered for SR" + hwId + "-" + hwRev, sensorGsr);
		return sensorGsr;
	}

	/** One GSR sample as the firmware sends it: range in bits 15-14 over the 12-bit code, LSB first. */
	private static ObjectCluster decodeRange3Sample(SensorGSRVerisense sensorGsr, int code) {
		int raw = (RANGE_3 << 14) | code;
		byte[] sample = new byte[] {(byte) (raw & 0xFF), (byte) ((raw >> 8) & 0xFF)};
		SensorDetails sensorDetails = sensorGsr.getSensorDetails(Verisense.SENSOR_ID.GSR);
		return sensorGsr.processDataCustom(sensorDetails, sample, COMMUNICATION_TYPE.SD, new ObjectCluster(), false, 0);
	}

	private static double calibrateRange3(int code) {
		return SensorGSR.calibrateGsrDataToKOhmsUsingAmplifierEq(code, RANGE_3,
				MICROCONTROLLER_ADC_PROPERTIES.VERISENSE_1V8,
				SensorGSRVerisense.VERISENSE_PULSE_PLUS_GSR_REF_RESISTORS_KOHMS);
	}
}
