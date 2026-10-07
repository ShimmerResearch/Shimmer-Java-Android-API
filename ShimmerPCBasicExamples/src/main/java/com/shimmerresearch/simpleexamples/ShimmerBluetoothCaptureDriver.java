package com.shimmerresearch.simpleexamples;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driver.ble.nativeble.BleScanResult;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.pcDriver.ShimmerBLENative;

/** Today's driver: {@link ShimmerBLENative} (ShimmerBluetooth) over native BLE. */
class ShimmerBluetoothCaptureDriver implements CaptureDriver {

	private final ShimmerBLENativeConfigManager mConfigManager = new ShimmerBLENativeConfigManager();
	private volatile ShimmerBLENative mShimmer;
	private volatile double mPacketReceptionRate = Double.NaN;

	@Override
	public String label() {
		return "shimmerbluetooth";
	}

	/** For the driver's configuration dialogs, which write through a Bluetooth manager. */
	ShimmerBLENativeConfigManager getConfigManager() {
		return mConfigManager;
	}

	@Override
	public void connect(BleScanResult device, final Listener listener) {
		ShimmerBLENative shimmer = new ShimmerBLENative(device);
		new BasicProcessWithCallBack() {
			@Override
			protected void processMsgFromCallback(ShimmerMsg msg) {
				int id = msg.mIdentifier;
				if (id == ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE) {
					BT_STATE state = ((CallbackObject) msg.mB).mState;
					listener.onState(state.toString());
				} else if (id == ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE) {
					if (((CallbackObject) msg.mB).mIndicator == ShimmerBluetooth.NOTIFICATION_SHIMMER_FULLY_INITIALIZED) {
						listener.onReady();
					}
				} else if (id == ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET) {
					listener.onSample((ObjectCluster) msg.mB);
				} else if (id == ShimmerBluetooth.MSG_IDENTIFIER_PACKET_RECEPTION_RATE_OVERALL) {
					mPacketReceptionRate = ((CallbackObject) msg.mB).mPacketReceptionRate;
				}
			}
		}.setWaitForData(shimmer);
		mConfigManager.setDevice(shimmer);
		mShimmer = shimmer;
		mPacketReceptionRate = Double.NaN;
		shimmer.connect("", "");
	}

	@Override
	public void disconnect() {
		ShimmerBLENative shimmer = mShimmer;
		if (shimmer != null) {
			try {
				shimmer.disconnect();
			} catch (ShimmerException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void startStreaming() throws ShimmerException {
		mShimmer.startStreaming();
	}

	@Override
	public void stopStreaming() {
		mShimmer.stopStreaming();
	}

	@Override
	public boolean isConnected() {
		ShimmerBLENative shimmer = mShimmer;
		return shimmer != null && shimmer.isConnected();
	}

	@Override
	public boolean isStreaming() {
		ShimmerBLENative shimmer = mShimmer;
		return shimmer != null && shimmer.isStreaming();
	}

	@Override
	public double getSamplingRate() {
		ShimmerBLENative shimmer = mShimmer;
		return shimmer == null ? Double.NaN : shimmer.getSamplingRateShimmer();
	}

	@Override
	public int getMtu() {
		ShimmerBLENative shimmer = mShimmer;
		return shimmer == null ? 0 : shimmer.getMtu();
	}

	@Override
	public double getPacketReceptionRate() {
		return mPacketReceptionRate;
	}

	@Override
	public ShimmerDevice getDeviceForPlot() {
		return mShimmer;
	}

	@Override
	public boolean canConfigure() {
		return true;
	}
}
