package com.shimmerresearch.bluetooth;

import java.util.ArrayList;
import java.util.List;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.tools.bluetooth.BasicShimmerBluetoothManagerPc;

/**
 * DEV-982 / PR #293, on hardware: a command refused while NOT streaming.
 *
 * <h2>Why not streaming</h2>
 *
 * {@code checkForAckOrRespTask} in ShimmerBluetooth branches on whether the
 * device is streaming. While streaming, an unanswered command calls
 * {@code clearAllInstructions()} and the link stays up; otherwise it calls
 * {@code connectionLost()}. So a refusal while not streaming is where master
 * costs the connection, when the ACK timer runs out about 5 s after the
 * command (not streaming, a SET gets {@code ACK_TIMER_DURATION} + 3 s), and it
 * is the path this test drives.
 *
 * <h2>How it gets a NACK</h2>
 *
 * It sends 0x30 with one argument byte. The driver calls it
 * {@code SET_BLINK_LED} and sends it from {@code writeLEDCommand()}; the
 * firmware calls it {@code SET_CHARGE_STATUS_LED_COMMAND}, dispatches it but
 * never implemented it, and so always refuses it (log-and-stream-common
 * {@code Comms/shimmer_bt_uart.c}). Nothing is written to the device. It queues {@code GET_SAMPLING_RATE_COMMAND} straight
 * behind it, to show that the instruction stack moves past the refusal.
 * <p>
 * Firmware has NACKed 0x30 since Shimmer3R LogAndStream v1.01.013 and Shimmer3
 * v1.01.004 (DEV-977). Older firmware ACKs it, so the test refuses to give a
 * verdict on it.
 * <p>
 * An earlier version of this test enabled SD sync and relied on the firmware
 * refusing every other command. That cannot work: with SD sync enabled the
 * firmware switches Bluetooth off while idle, so the port never opens.
 * <p>
 * The test queues the two instructions itself, so that it can follow those
 * two through the instruction stack. That is why it lives in the
 * {@code com.shimmerresearch.bluetooth} package: the instruction list and the
 * transaction state are protected.
 *
 * <h2>Running it</h2>
 *
 * Pass the Shimmer's Bluetooth COM port as the only argument, with the Shimmer
 * idle (not streaming or logging), and with SD sync off. Run it on master and
 * on the PR branch, unchanged. It takes about 15 s.
 *
 * <ul>
 * <li><b>PASS</b> (this PR): the refused command and the GET behind it both
 * leave the instruction stack, the GET is answered, and the link is still up 8
 * s later.</li>
 * <li><b>FAIL</b> (master): the NACK is dropped, the GET never goes out, and
 * the ACK timer tears the link down about 5 s later.</li>
 * <li><b>INCONCLUSIVE</b>: the Shimmer never finished connecting, is sensing,
 * or has firmware too old to refuse 0x30.</li>
 * </ul>
 */
public class TestNackNotStreaming extends BasicProcessWithCallBack {

	private static final long CONNECT_TIMEOUT_MS = 20000;

	/** Past the ACK timer, 5 s for a SET when not streaming, so a teardown has every chance to happen first */
	private static final long OBSERVATION_WINDOW_MS = 8000;

	private BasicShimmerBluetoothManagerPc mBtManager;
	private String mComPort;
	private long mTimeOfConnect;

	private volatile boolean mSawConnectionTeardown = false;
	private volatile boolean mSawFullyInitialised = false;
	private final List<String> mTransitions = new ArrayList<String>();

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			log("Usage: TestNackNotStreaming <Bluetooth COM port>, e.g. COM18");
			System.exit(2);
		}
		new TestNackNotStreaming().run(args[0]);
	}

	private void run(String comPort) throws Exception {
		mComPort = comPort;
		mBtManager = new BasicShimmerBluetoothManagerPc();
		setWaitForData(mBtManager.callBackObject);

		log("Connecting to " + comPort);
		mTimeOfConnect = System.currentTimeMillis();
		mBtManager.connectShimmerThroughCommPort(comPort);

		long connectDeadline = mTimeOfConnect + CONNECT_TIMEOUT_MS;
		while (!mSawFullyInitialised && !mSawConnectionTeardown && System.currentTimeMillis() < connectDeadline) {
			Thread.sleep(100);
		}

		String verdict = mSawFullyInitialised ? test() : "INCONCLUSIVE - the Shimmer did not finish connecting"
				+ (mSawConnectionTeardown ? " (the link closed first)" : " within " + (CONNECT_TIMEOUT_MS / 1000) + " s");
		report(verdict);

		try {
			mBtManager.disconnectAllDevices();
		} catch (Exception e) {
			//Nothing useful to do here - the run is over either way
		}
		System.exit(0);
	}

	/** Sends the refused command and the GET behind it, watches, and gives the verdict */
	private String test() throws InterruptedException {
		ShimmerDevice shimmerDevice = mBtManager.getShimmerDeviceBtConnected(mComPort);
		if (!(shimmerDevice instanceof ShimmerBluetooth)) {
			return "INCONCLUSIVE - no Bluetooth Shimmer found on " + mComPort;
		}
		ShimmerBluetooth device = (ShimmerBluetooth) shimmerDevice;
		String version = device.getHardwareVersionParsed() + " " + device.getFirmwareVersionParsed();
		record("connected: " + version);

		boolean refuses0x30 = device.isThisVerCompatibleWith(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 13)
				|| (device.getHardwareVersion() == HW_ID.SHIMMER_3
						&& device.isThisVerCompatibleWith(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 1, 4));
		if (!refuses0x30) {
			return "INCONCLUSIVE - " + version + " ACKs 0x30. It needs Shimmer3R LogAndStream v1.01.013+"
					+ " or Shimmer3 v1.01.004+.";
		}
		if (device.isSensing()) {
			return "INCONCLUSIVE - the Shimmer is streaming or logging. Stop it and run this again.";
		}

		byte[] refused = new byte[] {ShimmerObject.SET_BLINK_LED, 0x00};
		byte[] get = new byte[] {ShimmerObject.GET_SAMPLING_RATE_COMMAND};
		List<byte[]> instructions = device.getListofInstructions();
		synchronized (instructions) {
			instructions.add(refused);
			instructions.add(get);
		}
		long sentAt = System.currentTimeMillis();
		record("queued 0x30 00 (SET_BLINK_LED), then GET_SAMPLING_RATE");

		long refusedRetiredAt = -1;
		long getAnsweredAt = -1;
		boolean getSent = false;
		while (System.currentTimeMillis() - sentAt < OBSERVATION_WINDOW_MS && !mSawConnectionTeardown) {
			boolean refusedQueued;
			boolean getQueued;
			synchronized (instructions) {
				refusedQueued = instructions.contains(refused);
				getQueued = instructions.contains(get);
			}
			if (device.mCurrentCommand == ShimmerObject.GET_SAMPLING_RATE_COMMAND) {
				getSent = true;
			}
			long now = System.currentTimeMillis();
			if (refusedRetiredAt < 0 && !refusedQueued) {
				refusedRetiredAt = now;
				record("0x30 left the instruction stack");
			}
			// A GET leaves the stack on its ACK, and is answered when no reply is awaited any more
			if (getAnsweredAt < 0 && getSent && !getQueued && !device.mWaitForAck && !device.mWaitForResponse) {
				getAnsweredAt = now;
				record("GET_SAMPLING_RATE answered");
			}
			Thread.sleep(20);
		}

		if (mSawConnectionTeardown) {
			return "FAIL - the link was torn down after the refusal (master behaviour: a refused command"
					+ " cannot be told from a dead link)";
		}
		if (refusedRetiredAt < 0) {
			return "FAIL - the link is up, but the refused 0x30 was never retired";
		}
		if (getAnsweredAt < 0) {
			return "FAIL - the refusal was retired, but the GET behind it was not answered";
		}
		return "PASS - 0x30 refused and retired after " + (refusedRetiredAt - sentAt) + " ms, the GET answered after "
				+ (getAnsweredAt - sentAt) + " ms, and the link is still up after " + (OBSERVATION_WINDOW_MS / 1000) + " s";
	}

	@Override
	protected void processMsgFromCallback(ShimmerMsg shimmerMSG) {
		if (!(shimmerMSG.mB instanceof CallbackObject)) {
			return;
		}
		CallbackObject cbo = (CallbackObject) shimmerMSG.mB;

		if (shimmerMSG.mIdentifier == ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE) {
			record("BT_STATE -> " + cbo.mState);
			if (cbo.mState == BT_STATE.CONNECTION_LOST
					|| cbo.mState == BT_STATE.CONNECTION_FAILED
					|| cbo.mState == BT_STATE.DISCONNECTED) {
				mSawConnectionTeardown = true;
			}
		}
		else if (shimmerMSG.mIdentifier == ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE) {
			if (cbo.mIndicator == ShimmerBluetooth.NOTIFICATION_SHIMMER_FULLY_INITIALIZED) {
				record("FULLY_INITIALIZED");
				mSawFullyInitialised = true;
			}
		}
	}

	private void report(String verdict) {
		log("");
		log("---------------- observed ----------------");
		synchronized (mTransitions) {
			for (String t : mTransitions) {
				log(t);
			}
		}
		log("------------------------------------------");
		log(verdict);
	}

	private void record(String event) {
		String line = String.format("t+%5d ms  %s", System.currentTimeMillis() - mTimeOfConnect, event);
		synchronized (mTransitions) {
			mTransitions.add(line);
		}
		log(line);
	}

	private static void log(String msg) {
		System.out.println(msg);
	}
}
