package com.shimmerresearch.sensors.bmpX80;

import com.shimmerresearch.driver.calibration.CalibDetails.CALIB_READ_SOURCE;

/**
 * BMP581 streams already-compensated values, so there are no per-device trim
 * coefficients to parse or store (unlike BMP180/BMP280/BMP390). This CalibDetails
 * is therefore a pass-through scaler:
 * <li> pressure    = raw / 64      -> Pa     (unsigned 24-bit)
 * <li> temperature = raw / 65536   -> deg C  (<b>signed</b> 24-bit, two's complement)
 * (see BST-BMP581-DS004).
 * <p>
 * The temperature channel arrives as an unsigned 24-bit field, so it has to be
 * sign-extended here: without it a reading of -0.5 deg C decodes as 255.5
 * (DEV-1102).
 *
 * @author Shimmer
 */
public class CalibDetailsBmp581 extends CalibDetailsBmpX80 {

	private static final long serialVersionUID = 8046182982777461001L;

	public String mSensorMacID;

	public CalibDetailsBmp581(String mMacIdFromUart) {
		mSensorMacID = mMacIdFromUart;
	}

	@Override
	public double[] calibratePressureSensorData(double UP, double UT) {
		double[] caldata = new double[2];
		caldata[0] = UP / 64.0;      // Pa  (downstream /1000 -> kPa, same as BMP390 legacy path)
		caldata[1] = signExtend24(UT) / 65536.0;   // deg C
		return caldata;
	}

	/**
	 * Reads a 24-bit field as two's complement: the BMP581 temperature register
	 * is signed, but both of the driver's parse paths deliver it as UINT24.
	 * Idempotent, so a value that is already signed passes through unchanged.
	 *
	 * @param raw the 24-bit field, as parsed
	 * @return the signed value, -2^23 to 2^23-1
	 */
	public static double signExtend24(double raw) {
		long bits = ((long) raw) & 0xFFFFFFL;
		return (bits & 0x800000L) != 0 ? bits - 0x1000000L : bits;
	}

	@Override
	public byte[] generateCalParamByteArray() {
		// No coefficients on BMP581.
		return null;
	}

	@Override
	public void parseCalParamByteArray(byte[] bufferCalibrationParameters, CALIB_READ_SOURCE calibReadSource) {
		// No-op: BMP581 self-compensates; no coefficient block exists.
	}

	@Override
	public void resetToDefaultParameters() {
		// No calibration state to reset.
	}

}
