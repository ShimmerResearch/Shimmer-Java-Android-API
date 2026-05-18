package com.shimmerresearch.driverUtilities;

import java.io.Serializable;

public class EepromSensorSettingsDetails implements Serializable {

	private static final long serialVersionUID = -5497659197227328138L;

	public static final int EXP_BOARD_MEMORY_LOCATION_FOR_SENSOR_SETTINGS = 2016;
	public static final int EXP_BOARD_MEMORY_PAGE_SIZE = 16;

	public byte[] mEepromPageArray = new byte[]{};

	public int radioHwVer = 0;
	public int baudRate = 0;
	public BTRADIO_STATE mRadioState = BTRADIO_STATE.BT_CLASSIC_BLE_ENABLED; 
	public boolean mUsbHighSpeed = true; 

	public enum EXP_BOARD_ARRAY_BYTE_INDEX {
		RADIO_HW_VER,
		BAUD_RATE,
		SENSOR_OPTIONS1,
		BT_CNT_DISCONNECT_WHILE_STREAMING_LSB, // Used for Shimmer3 RN4678 error tracking
		BT_CNT_DISCONNECT_WHILE_STREAMING_MSB, // Used for Shimmer3 RN4678 error tracking
		BT_CNT_UNSOLICITED_REBOOT_LSB, // Used for Shimmer3 RN4678 error tracking
		BT_CNT_UNSOLICITED_REBOOT_MSB, // Used for Shimmer3 RN4678 error tracking
		BT_CNT_RTS_LOCKUP_LSB, // Used for Shimmer3 RN4678 error tracking
		BT_CNT_RTS_LOCKUP_MSB, // Used for Shimmer3 RN4678 error tracking
		BT_CNT_DATA_RATE_TEST_BLOCKAGE_LSB, // Used for Shimmer3 RN4678 error tracking
		BT_CNT_DATA_RATE_TEST_BLOCKAGE_MSB, // Used for Shimmer3 RN4678 error tracking
		UNUSED_BYTE_INDEX_11,
		UNUSED_BYTE_INDEX_12,
		UNUSED_BYTE_INDEX_13,
		UNUSED_BYTE_INDEX_14,
		UNUSED_BYTE_INDEX_15
	}

	public enum BTRADIO_STATE{
		BT_CLASSIC_BLE_ENABLED("BT Classic and BLE Enabled", 0x03),
		BT_CLASSIC_ENABLED("BT Classic Enabled", 0x02),
		BLE_ENABLED("BLE Enabled", 0x01),
		NONE_ENABLED("None Enabled", 0x00);
		
	    private String text;
		private int bits;

	    /**
	     * @param text
	     * @param bits 
	     */
	    private BTRADIO_STATE(final String text, int bits) {
	        this.text = text;
	        this.bits = bits;
	    }

	    /* (non-Javadoc)
	     * @see java.lang.Enum#toString()
	     */
	    @Override
	    public String toString() {
	        return text;
	    }

		public byte getBitValue() {
			return (byte)bits;
		}
	}
	
	public enum USB_SPEED {
		HIGH_SPEED("High-Speed (480 Mbps)", 0x04),
		FULL_SPEED("Full-Speed (12 Mbps)", 0x00);
		
	    private String text;
		private int bits;

	    /**
	     * @param text
	     * @param bits 
	     */
	    private USB_SPEED(final String text, int bits) {
	        this.text = text;
	        this.bits = bits;
	    }

	    /* (non-Javadoc)
	     * @see java.lang.Enum#toString()
	     */
	    @Override
	    public String toString() {
	        return text;
	    }

		public byte getBitValue() {
			return (byte)bits;
		}
	}

	public EepromSensorSettingsDetails() {

	}

	public void parseEepromSensorSettingsDetails(byte[] byteArray) {
	    this.mEepromPageArray = byteArray;

	    radioHwVer = mEepromPageArray[EXP_BOARD_ARRAY_BYTE_INDEX.RADIO_HW_VER.ordinal()] & 0xFF;
	    baudRate = mEepromPageArray[EXP_BOARD_ARRAY_BYTE_INDEX.BAUD_RATE.ordinal()] & 0xFF;

	    int radioStateBits = mEepromPageArray[EXP_BOARD_ARRAY_BYTE_INDEX.SENSOR_OPTIONS1.ordinal()] & 0x03;
	    if (radioStateBits == BTRADIO_STATE.BT_CLASSIC_BLE_ENABLED.getBitValue()) {
	        mRadioState = BTRADIO_STATE.BT_CLASSIC_BLE_ENABLED;
	    } else if (radioStateBits == BTRADIO_STATE.BT_CLASSIC_ENABLED.getBitValue()) {
	        mRadioState = BTRADIO_STATE.BT_CLASSIC_ENABLED;
	    } else if (radioStateBits == BTRADIO_STATE.BLE_ENABLED.getBitValue()) {
	        mRadioState = BTRADIO_STATE.BLE_ENABLED;
	    } else {
	        mRadioState = BTRADIO_STATE.NONE_ENABLED;
	    }

	    mUsbHighSpeed = (mEepromPageArray[EXP_BOARD_ARRAY_BYTE_INDEX.SENSOR_OPTIONS1.ordinal()] & USB_SPEED.HIGH_SPEED.getBitValue()) != 0;
	}

	public String generateDebugString() {
		return (mRadioState.toString() + ", USB Speed: " 
	+ (mUsbHighSpeed ? USB_SPEED.HIGH_SPEED.toString() : USB_SPEED.FULL_SPEED.toString()));
	}

	public void setBtRadioStateFromString(String option) {
		if(option.equals(BTRADIO_STATE.BT_CLASSIC_BLE_ENABLED.toString())) {
			mRadioState = BTRADIO_STATE.BT_CLASSIC_BLE_ENABLED;
		}else if (option.equals(BTRADIO_STATE.BT_CLASSIC_ENABLED.toString())) {
			mRadioState = BTRADIO_STATE.BT_CLASSIC_ENABLED;
		}else if (option.equals(BTRADIO_STATE.BLE_ENABLED.toString())) {
			mRadioState = BTRADIO_STATE.BLE_ENABLED;
		}

		updateEepromPageArray();
	}

	public void setUsbSpeedStateFromString(String option) {
		if (option.equals(USB_SPEED.HIGH_SPEED.toString())) {
			mUsbHighSpeed = true;
		} else if (option.equals(USB_SPEED.FULL_SPEED.toString())) {
			mUsbHighSpeed = false;
		}
		
		updateEepromPageArray();
	}

	private void updateEepromPageArray() {
		mEepromPageArray[EXP_BOARD_ARRAY_BYTE_INDEX.RADIO_HW_VER.ordinal()] = (byte) radioHwVer;
		mEepromPageArray[EXP_BOARD_ARRAY_BYTE_INDEX.BAUD_RATE.ordinal()] = (byte) baudRate;
		
		mEepromPageArray[EXP_BOARD_ARRAY_BYTE_INDEX.SENSOR_OPTIONS1.ordinal()] &= 0xFC; // Clear the existing radio state bits (bits 0 and 1)
		mEepromPageArray[EXP_BOARD_ARRAY_BYTE_INDEX.SENSOR_OPTIONS1.ordinal()] |= mRadioState.getBitValue(); // Set the new radio state bits

		mEepromPageArray[EXP_BOARD_ARRAY_BYTE_INDEX.SENSOR_OPTIONS1.ordinal()] &= 0xFB; // Clear the existing USB speed bit (bit 2)
		mEepromPageArray[EXP_BOARD_ARRAY_BYTE_INDEX.SENSOR_OPTIONS1.ordinal()] |= (mUsbHighSpeed ? USB_SPEED.HIGH_SPEED.getBitValue() : USB_SPEED.FULL_SPEED.getBitValue()); // Set the new USB speed bit
	}

}
