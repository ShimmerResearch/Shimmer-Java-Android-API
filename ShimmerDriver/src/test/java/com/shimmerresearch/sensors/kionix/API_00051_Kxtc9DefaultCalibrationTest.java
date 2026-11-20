package com.shimmerresearch.sensors.kionix;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shimmerresearch.driver.calibration.UtilCalibration;

/**
 * DEV-1521: the KXTC9-2050's default calibration on a Shimmer3.
 * <p>
 * Kionix specifies the KXTC9-2050 at Vdd = 3.3 V (zero-g 1.65 V, 660 mV/g) and gives an offset and
 * a sensitivity "ratiometric error": its output scales with its supply. Every Shimmer3 board that
 * carries it (SR31-10, SR47-6, SR48-5, SR49-3) powers it from the same 3.0 V LDO as the MSP430's
 * AVCC, the ADC reference. In ADC counts it therefore reads like the KXRB5-2042 it replaced, and
 * its defaults must be that part's: offset 2047, sensitivity 83. The old 2253 and 92 assumed 3.3 V
 * and made an uncalibrated new-IMU Shimmer3 read gravity about 17 % high.
 *
 * @author Mark Nolan
 */
public class API_00051_Kxtc9DefaultCalibrationTest {

	private static final double ADC_FULL_SCALE = 4095, ADC_VREF = 3.0, SUPPLY = 3.0, G = 9.81;

	@Test
	public void defaultsAreTheDatasheetFiguresScaledToTheShimmersThreeVoltSupply() {
		double zeroG = 1.65 * SUPPLY / 3.3; // V
		double mvPerG = 660 * SUPPLY / 3.3;
		double offset = zeroG / ADC_VREF * ADC_FULL_SCALE; // 2047.5
		double sensitivity = mvPerG / 1000 / ADC_VREF * ADC_FULL_SCALE / G; // 83.5

		for (int axis = 0; axis < 3; axis++) {
			assertEquals("offset, axis " + axis, offset, SensorKionixKXTC92050.OffsetVectorLowNoiseAccelShimmer3[axis][0], 1.0);
			assertEquals("sensitivity, axis " + axis, sensitivity, SensorKionixKXTC92050.SensitivityMatrixLowNoiseAccel2gShimmer3[axis][axis], 1.0);
		}
	}

	@Test
	public void defaultsMatchTheKxrb5s() {
		assertArrayEquals(SensorKionixKXRB52042.OffsetVectorLowNoiseAccelShimmer3, SensorKionixKXTC92050.OffsetVectorLowNoiseAccelShimmer3);
		assertArrayEquals(SensorKionixKXRB52042.SensitivityMatrixLowNoiseAccel2gShimmer3, SensorKionixKXTC92050.SensitivityMatrixLowNoiseAccel2gShimmer3);
		assertArrayEquals(SensorKionixKXRB52042.AlignmentMatrixLowNoiseAccelShimmer3, SensorKionixKXTC92050.AlignmentMatrixLowNoiseAccelShimmer3);
	}

	/**
	 * Shimmer3 A615 (SR49-3-1) lying still, 20 Nov 2026: its raw LN accel at rest. On the defaults
	 * it must read 1 g; on the old 2253 and 92 it read 11.5 m/s2.
	 */
	@Test
	public void aShimmer3AtRestReadsOneG() {
		double[] cal = UtilCalibration.calibrateInertialSensorData(new double[] { 1232, 2011, 2125 },
				SensorKionixKXTC92050.AlignmentMatrixLowNoiseAccelShimmer3,
				SensorKionixKXTC92050.SensitivityMatrixLowNoiseAccel2gShimmer3,
				SensorKionixKXTC92050.OffsetVectorLowNoiseAccelShimmer3);
		double magnitude = Math.sqrt(cal[0] * cal[0] + cal[1] * cal[1] + cal[2] * cal[2]);
		assertEquals(9.81, magnitude, 0.15);
	}
}
