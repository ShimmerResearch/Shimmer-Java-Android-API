package com.shimmerresearch.driver.shimmer2r3;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * @author Mark Nolan
 *
 */
public class BluetoothModuleVersionDetails implements Serializable {

	private static final long serialVersionUID = -4602970064289358794L;

	public static enum BT_MODULE_VERSION {
		NOT_READ("", "", ""),

		RN41_VERSION_RESPONSE_V4_77("Ver 4.77 05/12/09 \r\n(c) Roving Networks", "Ver 4.77 05", "RN41 v4.77"),
		
		RN42_VERSION_RESPONSE_V4_77("Ver 4.77 RN-42 01/05/10 \r\n(c) Roving Networks", "Ver 4.77 RN-42", "RN42 v4.77"),
		RN42_VERSION_RESPONSE_V6_15("Ver 6.15 04/26/2013\r\n(c) Roving Networks", "Ver 6.15 04", "RN42 v6.15"),

		RN4678_VERSION_RESPONSE_V1_00_5("RN4678 V1.00.5 11/15/2016 (c)Microchip Technology Inc", "RN4678 V1.00.5 ", "RN4678 v1.00.5"),
		RN4678_VERSION_RESPONSE_V1_11_0("RN4678 V1.11.00 6/1/2017 (c)Microchip Technology Inc", "RN4678 V1.11.00 ", "RN4678 v1.11.0"),
		RN4678_VERSION_RESPONSE_V1_13_5("RN4678 V1.13.5 8/29/2018 (c)Microchip Technology Inc", "RN4678 V1.13.5 ", "RN4678 v1.15.5"),
		RN4678_VERSION_RESPONSE_V1_22_0("RN4678 V1.22 12/08/2020 (c)Microchip Technology Inc   ", "RN4678 V1.22 ", "RN4678 v1.23"),
		RN4678_VERSION_RESPONSE_V1_23_0("RN4678 V1.23 06/30/2021 (c)Microchip Technology Inc", "RN4678 V1.23 ", "RN4678 v1.23"),

		VELA_IF820_V01_04_12_12("CYW20820 app=v01.04.12.12", "CYW20820 app=v01.04.12.12", "IF820 v01.04.12.12"),
		VELA_IF820_V01_04_16_16("CYW20820 app=v01.04.16.16", "CYW20820 app=v01.04.16.16", "IF820 v01.04.16.16"),
		VELA_IF820_V01_04_18_18("CYW20820 app=v01.04.18.18", "CYW20820 app=v01.04.18.18", "IF820 v01.04.18.18"),

		UNKNOWN("Unknown", "Unknown", "Unknown");

		public String btModuleVerStrFull = "";
		public String btModuleVerStrComparison = "";
		public String btModuleVerStrUserFriendly = "";

		BT_MODULE_VERSION(String btModuleVerStrExpected, String btModuleVerStrComparison, String btModuleVerStrUserFriendly) {
			this.btModuleVerStrFull = btModuleVerStrExpected;
			this.btModuleVerStrComparison = btModuleVerStrComparison;
			this.btModuleVerStrUserFriendly = btModuleVerStrUserFriendly;
		}
	}

	public BT_MODULE_VERSION mBtModuleVersionParsed = BT_MODULE_VERSION.NOT_READ;
	public String mBtModuleVersionReceived = mBtModuleVersionParsed.btModuleVerStrFull;

	public void parseBtModuleVerBytes(byte[] responseData) {
		mBtModuleVersionReceived = new String(responseData, StandardCharsets.UTF_8);
		
		mBtModuleVersionParsed = BT_MODULE_VERSION.UNKNOWN;
		for (BT_MODULE_VERSION btModuleVersion : BT_MODULE_VERSION.values()) {
			if(btModuleVersion == BT_MODULE_VERSION.NOT_READ) {
				if(mBtModuleVersionReceived.isEmpty()) {
					mBtModuleVersionParsed = BT_MODULE_VERSION.NOT_READ;
					break;
				}
			}
			else if(mBtModuleVersionReceived.contains(btModuleVersion.btModuleVerStrComparison)) {
				mBtModuleVersionParsed = btModuleVersion;
				break;
			}
		}
	}

	public String getUserFriendlyName() {
		if (mBtModuleVersionParsed == BT_MODULE_VERSION.UNKNOWN) {
			return mBtModuleVersionReceived;
		} else {
			return mBtModuleVersionParsed.btModuleVerStrUserFriendly;
		}
	}

	public String getBtModuleVersionReceived() {
		return mBtModuleVersionReceived;
	}

	public boolean isBtModuleVersionKnown() {
		return mBtModuleVersionParsed != BT_MODULE_VERSION.UNKNOWN && mBtModuleVersionParsed != BT_MODULE_VERSION.NOT_READ;
	}
	
	public boolean isBtModuleVersionRn42() {
		return mBtModuleVersionParsed == BT_MODULE_VERSION.RN42_VERSION_RESPONSE_V4_77 ||
				mBtModuleVersionParsed == BT_MODULE_VERSION.RN42_VERSION_RESPONSE_V6_15;
	}
	
	public boolean isBtModuleVersionRn41() {
		return mBtModuleVersionParsed == BT_MODULE_VERSION.RN41_VERSION_RESPONSE_V4_77;
	}

	public boolean isBtModuleVersionRn4678() {
		return mBtModuleVersionParsed == BT_MODULE_VERSION.RN4678_VERSION_RESPONSE_V1_00_5
				|| mBtModuleVersionParsed == BT_MODULE_VERSION.RN4678_VERSION_RESPONSE_V1_11_0
				|| mBtModuleVersionParsed == BT_MODULE_VERSION.RN4678_VERSION_RESPONSE_V1_13_5
				|| mBtModuleVersionParsed == BT_MODULE_VERSION.RN4678_VERSION_RESPONSE_V1_22_0
				|| mBtModuleVersionParsed == BT_MODULE_VERSION.RN4678_VERSION_RESPONSE_V1_23_0;
	}
	
	public boolean isBtModuleVersionVelaIf820() {
		return mBtModuleVersionParsed == BT_MODULE_VERSION.VELA_IF820_V01_04_12_12
				|| mBtModuleVersionParsed == BT_MODULE_VERSION.VELA_IF820_V01_04_16_16
						|| mBtModuleVersionParsed == BT_MODULE_VERSION.VELA_IF820_V01_04_18_18;
	}
}
