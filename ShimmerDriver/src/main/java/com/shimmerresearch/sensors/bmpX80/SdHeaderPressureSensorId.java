package com.shimmerresearch.sensors.bmpX80;

import java.io.Serializable;

import com.shimmerresearch.driver.ShimmerObject.PRESSURE_SENSOR_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilShimmer;

/**
 * The pressure sensor LogAndStream firmware records at SD header offset 224
 * (SDH_PRESSURE_SENSOR_ID in log-and-stream-common SDCard/shimmer_sd_header.h).
 * Offset 224 is the same in the 256-byte Shimmer3 header and the 384-byte
 * Shimmer3R header.
 *
 * <p>
 * Bits 0-6 hold a {@link PRESSURE_SENSOR_ID}, the same numbering as the 0xA6
 * response. Bit 7 means the firmware inferred the sensor from the SR number
 * because the chip-ID check was inconclusive. 0xFE means no pressure sensor is
 * fitted (Shimmer3 only). The firmware pre-fills the header with 0xFF, so 0xFF
 * means the firmware predates the field.
 *
 * <p>
 * The byte is only trusted from LogAndStream_Shimmer3R v1.01.018 and
 * LogAndStream_Shimmer3 v1.01.006. The two version lines overlap, so the gate
 * checks the hardware version too.
 */
public class SdHeaderPressureSensorId implements Serializable {

	// ShimmerObject holds one, and ShimmerPC.deepClone() serialises the device
	private static final long serialVersionUID = 1258143216796546316L;

	public static final int SD_HEADER_INDEX = 224;

	public static final int ID_MASK = 0x7F;
	public static final int INFERRED_FLAG = 0x80;
	public static final int NOT_FITTED = 0xFE;
	public static final int NOT_RECORDED = 0xFF;

	public enum STATE {
		/** Firmware below the gate, or 0xFF: the SR-number rule decides, as before. */
		ABSENT,
		/** A sensor this platform's parser handles; it overrides the SR-number rule. */
		KNOWN,
		/** Present but not a sensor this parser handles on this platform: emit the channels uncalibrated, no SR-number fallback. */
		UNKNOWN,
		/** 0xFE, no pressure sensor fitted: any pressure channels in the file are emitted uncalibrated. */
		NOT_FITTED
	}

	private final STATE mState;
	private final int mSensorId;
	private final boolean mIsInferred;
	private final int mRawValue;

	private SdHeaderPressureSensorId(STATE state, int sensorId, boolean isInferred, int rawValue) {
		mState = state;
		mSensorId = sensorId;
		mIsInferred = isInferred;
		mRawValue = rawValue;
	}

	/**
	 * @param svo the version details from the same header
	 * @return true if this firmware writes a trustworthy offset 224
	 */
	public static boolean isSupported(ShimmerVerObject svo) {
		if(svo==null){
			return false;
		}
		return isVerAtLeast(svo, HW_ID.SHIMMER_3R, 1, 1, 18)
				|| isVerAtLeast(svo, HW_ID.SHIMMER_3, 1, 1, 6);
	}

	// ShimmerVerObject.compareVersions(svo, hw, ...) ignores the hardware
	// version, so go to UtilShimmer directly
	private static boolean isVerAtLeast(ShimmerVerObject svo, int hardwareVersion, int major, int minor, int internal) {
		return UtilShimmer.compareVersions(svo.getHardwareVersion(), svo.getFirmwareIdentifier(),
				svo.getFirmwareVersionMajor(), svo.getFirmwareVersionMinor(), svo.getFirmwareVersionInternal(),
				hardwareVersion, FW_ID.LOGANDSTREAM, major, minor, internal);
	}

	/**
	 * @param svo the version details from the same header
	 * @param headerValue the byte at {@link #SD_HEADER_INDEX}, 0-255
	 * @return what the byte means for this file
	 */
	public static SdHeaderPressureSensorId parse(ShimmerVerObject svo, int headerValue) {
		headerValue &= 0xFF;
		if(headerValue==NOT_RECORDED || !isSupported(svo)){
			return new SdHeaderPressureSensorId(STATE.ABSENT, -1, false, headerValue);
		}
		if(headerValue==NOT_FITTED){
			return new SdHeaderPressureSensorId(STATE.NOT_FITTED, -1, false, headerValue);
		}

		int sensorId = headerValue & ID_MASK;
		boolean isInferred = (headerValue & INFERRED_FLAG)!=0;
		STATE state = isSensorHandled(svo.getHardwareVersion(), sensorId)? STATE.KNOWN:STATE.UNKNOWN;
		return new SdHeaderPressureSensorId(state, sensorId, isInferred, headerValue);
	}

	/** The parser has no Shimmer3R path for a BMP180/280 or Shimmer3 path for a
	 * BMP390/581, so those are as unknown as a future ID would be. */
	private static boolean isSensorHandled(int hardwareVersion, int sensorId) {
		if(hardwareVersion==HW_ID.SHIMMER_3R){
			return sensorId==PRESSURE_SENSOR_ID.BMP390 || sensorId==PRESSURE_SENSOR_ID.BMP581;
		}
		return sensorId==PRESSURE_SENSOR_ID.BMP180 || sensorId==PRESSURE_SENSOR_ID.BMP280;
	}

	public STATE getState() {
		return mState;
	}

	/**
	 * @return true unless the SR-number rule should decide ({@link STATE#ABSENT})
	 */
	public boolean isPresent() {
		return mState!=STATE.ABSENT;
	}

	/**
	 * @return true if the byte names a sensor this parser can calibrate
	 */
	public boolean isKnown() {
		return mState==STATE.KNOWN;
	}

	/**
	 * @return true if the channels must not be calibrated as any BMP
	 *         ({@link STATE#UNKNOWN} or {@link STATE#NOT_FITTED})
	 */
	public boolean isUncalibrated() {
		return mState==STATE.UNKNOWN || mState==STATE.NOT_FITTED;
	}

	/**
	 * @param sensorId a {@link PRESSURE_SENSOR_ID}
	 * @return true if the byte names this sensor and the parser handles it
	 */
	public boolean isSensor(int sensorId) {
		return mState==STATE.KNOWN && mSensorId==sensorId;
	}

	/**
	 * @return the low 7 bits, a {@link PRESSURE_SENSOR_ID} if known, or -1 if
	 *         {@link STATE#ABSENT} or {@link STATE#NOT_FITTED}
	 */
	public int getSensorId() {
		return mSensorId;
	}

	/**
	 * @return true if the firmware inferred the sensor from the SR number
	 *         rather than confirming it by chip ID
	 */
	public boolean isInferred() {
		return mIsInferred;
	}

	public int getRawValue() {
		return mRawValue;
	}

	@Override
	public String toString() {
		return String.format("0x%02X (%s%s)", mRawValue, mState, mIsInferred? ", inferred from SR number":"");
	}
}
