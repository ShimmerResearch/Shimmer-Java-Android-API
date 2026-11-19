package com.shimmerresearch.driverUtilities;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driverUtilities.HwDriverShimmerDeviceDetails.DEVICE_TYPE;


public class BluetoothDeviceDetails {

	public String mComPort = "";
	public String mComPortDescription = "";
	public String mFriendlyName = "";

	public String mShimmerMacId = "";
	public String mShimmerMacIdParsed = "";

	public DEVICE_TYPE mDeviceTypeDetected = DEVICE_TYPE.UNKOWN;

	//TODO Testing for GUI feedback - shouldn't really be here 
	public boolean mAttemptingConnection = false;
	public BT_STATE mLastConnectionSate = BT_STATE.DISCONNECTED;
	public boolean isBleDevice = false;

	public BluetoothDeviceDetails(String comPort, String comPortDescription){
		mComPort = comPort;
		mComPortDescription = comPortDescription;
	}

	public BluetoothDeviceDetails(String comPort, String macId, String friendlyName){
		mComPort = comPort;
		mFriendlyName = friendlyName;
		setMacId(macId);
		checkDeviceType();
	}

	public void setMacId(String mac){
		if((mac.startsWith("Shimmer") && mac.contains("-"))
				|| HwDriverShimmerDeviceDetails.SH_SEARCH.BT.isNeurosmartName(mac)) {
			//If using device name (e.g. Shimmer3-6813, or Neurosmart-6813-BLE (DEV-1389)), then leave as is
			mShimmerMacId = mac;
			mShimmerMacId = mac;
		} else {
			mac = mac.replace("-", "");
	
			if(mac.length()>=12) {
				mShimmerMacId = mac.toUpperCase();
				mac = mac.replace(":", "").toUpperCase();
				mShimmerMacIdParsed = mac.substring(8);
			}
		}
	}

	public String getGuiName() {
		if(!mFriendlyName.isEmpty())
			return mFriendlyName;
		else
			return mShimmerMacIdParsed;
	}

	public void checkDeviceType() {
		if(!mShimmerMacId.equals(UtilShimmer.MAC_ADDRESS_ZEROS)){
			if(mFriendlyName.contains(HwDriverShimmerDeviceDetails.SH_SEARCH.BT.SHIMMER3)
					|| mFriendlyName.contains(HwDriverShimmerDeviceDetails.SH_SEARCH.BT.SHIMMER3_RN4678_BLE)
					|| HwDriverShimmerDeviceDetails.SH_SEARCH.BT.isNeurosmartName(mFriendlyName)){
				mDeviceTypeDetected = DEVICE_TYPE.SHIMMER3;
			}
			else if(mFriendlyName.contains(HwDriverShimmerDeviceDetails.DEVICE_TYPE.SHIMMER3_OUTPUT.getLabel())){
				mDeviceTypeDetected = DEVICE_TYPE.SHIMMER3_OUTPUT;
			} 
			else if(mFriendlyName.contains(HwDriverShimmerDeviceDetails.SH_SEARCH.BT.SHIMMER4)){
				mDeviceTypeDetected = DEVICE_TYPE.SHIMMER4;
			}
			else if(mFriendlyName.contains(HwDriverShimmerDeviceDetails.SH_SEARCH.BT.SHIMMER_ECG_MD)){
				mDeviceTypeDetected = DEVICE_TYPE.SHIMMER_ECG_MD;
			}
			else if(mFriendlyName.contains(HwDriverShimmerDeviceDetails.SH_SEARCH.BT.RN42)){
				mDeviceTypeDetected = DEVICE_TYPE.RN42;
			}
			else if(mFriendlyName.contains(HwDriverShimmerDeviceDetails.SH_SEARCH.BT.RNBT)){
				mDeviceTypeDetected = DEVICE_TYPE.RNBT;
			}
			else if(mFriendlyName.contains(HwDriverShimmerDeviceDetails.SH_SEARCH.BT.MANUFACTURER_LUMAFIT)){
				mDeviceTypeDetected = DEVICE_TYPE.LUMAFIT;
			}
			else if(mFriendlyName.contains(HwDriverShimmerDeviceDetails.SH_SEARCH.BT.MANUFACTURER_NONIN)){
				mDeviceTypeDetected = DEVICE_TYPE.NONIN_ONYX_II;
			} 
			else if(mFriendlyName.contains(HwDriverShimmerDeviceDetails.SH_SEARCH.BT.VERISENSE)){
				mDeviceTypeDetected = DEVICE_TYPE.VERISENSE;
			} 
			else {
				mDeviceTypeDetected = DEVICE_TYPE.UNKOWN;
			}
		} else {
			mDeviceTypeDetected = DEVICE_TYPE.UNKOWN;
		}
	}

	public void update(BluetoothDeviceDetails bluetoothDeviceDetails) {
		mComPort = bluetoothDeviceDetails.mComPort;
		setMacId(bluetoothDeviceDetails.mShimmerMacId);
		mFriendlyName = bluetoothDeviceDetails.mFriendlyName;
		mComPortDescription = bluetoothDeviceDetails.mComPortDescription;

		checkDeviceType();
	}
}
