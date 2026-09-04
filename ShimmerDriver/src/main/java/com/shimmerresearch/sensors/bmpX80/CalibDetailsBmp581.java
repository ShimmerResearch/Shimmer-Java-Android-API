package com.shimmerresearch.sensors.bmpX80;

import com.shimmerresearch.driver.calibration.CalibDetails.CALIB_READ_SOURCE;

/**
 * BMP581 streams already-compensated values, so there are no per-device trim
 * coefficients to parse or store (unlike BMP180/BMP280/BMP390). This CalibDetails
 * is therefore a pass-through scaler:
 * <li> pressure    = raw / 64      -> Pa
 * <li> temperature = raw / 65536   -> deg C
 * (see BST-BMP581-DS004).
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
		caldata[1] = UT / 65536.0;   // deg C
		return caldata;
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
