package com.shimmerresearch.simpleexamples;

import java.util.ArrayList;
import java.util.List;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.tools.bluetooth.BasicShimmerBluetoothManagerPc;

/**
 * DEV-982 / PR #293 - exercises the NACK path that the earlier streaming tests
 * could not reach.
 *
 * <h2>Why the streaming tests did not show the connection drop</h2>
 *
 * {@code checkForAckOrRespTask} in ShimmerBluetooth branches on whether the
 * device is streaming:
 *
 * <pre>
 * if(mIsStreaming){
 *     ... clearAllInstructions();   // connection stays UP, queue is destroyed
 * }
 * else {
 *     ... connectionLost();         // the ~2 s drop described in the ticket
 * }
 * </pre>
 *
 * TestNackWhileStreaming and TestNackQueuedCommand both refuse a command while
 * sensing, so both take the first branch - which on master never drops the
 * connection. The ~2 s teardown lives only in the second branch, and only a
 * refusal while NOT streaming reaches it. That is what this test does.
 *
 * Configuring a device mid-stream is also not a supported operation, so those
 * two tests were exercising a path the product does not use.
 *
 * <h2>How this test forces a NACK</h2>
 *
 * The firmware gates every command before dispatching it
 * ({@code ShimBt_processCmd} in log-and-stream-common/Comms/shimmer_bt_uart.c):
 *
 * <pre>
 * if (storedConfigPtr-&gt;syncEnable ^ ShimBt_isCmdAllowedWhileSdSyncing(gAction))
 *     sendNack = 1;
 * </pre>
 *
 * and the allow-list is only two entries:
 *
 * <pre>
 * uint8_t ShimBt_isCmdAllowedWhileSdSyncing(uint8_t command)
 * {
 *   return (command == SET_SD_SYNC_COMMAND || command == ACK_COMMAND_PROCESSED);
 * }
 * </pre>
 *
 * So with SD sync enabled the device refuses <em>everything else</em>, including
 * the whole driver initialisation sequence (GET_FW_VERSION, INQUIRY,
 * GET_STATUS ...). Nothing is written to the device to set this up and nothing
 * is written by the test, so the run is non-destructive.
 *
 * <h2>Setup</h2>
 *
 * <ol>
 * <li>In Consensys, enable SD sync on the Shimmer, and write the configuration.</li>
 * <li>Set {@link #COM_PORT} below.</li>
 * <li>Run this class on {@code master}, then on the PR #293 branch, unchanged.</li>
 * <li>Afterwards, disable SD sync again in Consensys.</li>
 * </ol>
 *
 * <h2>What to expect</h2>
 *
 * <ul>
 * <li><b>master</b> - the NACK is read and silently discarded, {@code mWaitForAck}
 * stays true, the ACK timer expires after {@code ACK_TIMER_DURATION} (2 s) and
 * {@code connectionLost()} is called. Expect CONNECTION_LOST / CONNECTION_FAILED
 * a couple of seconds after connecting, with nothing in the log explaining why.</li>
 *
 * <li><b>PR #293</b> - the NACK is recognised, the transaction is unwound and the
 * link is left up. Expect no teardown inside the observation window, and
 * "NACK Received for Command:" lines in the driver log.</li>
 * </ul>
 *
 * Note that with sync enabled the device cannot finish initialising on either
 * branch - every GET it needs is refused. That is expected and is not what is
 * being measured here. The difference under test is <em>how</em> it fails: a
 * silent link teardown, or a logged refusal with the connection intact.
 *
 * If FULLY_INITIALIZED arrives, SD sync was not actually enabled and the run
 * proves nothing - the test says so rather than reporting a pass.
 */
public class TestNackNotStreaming extends BasicProcessWithCallBack {

	/** TODO set this to your device's COM port */
	private static final String COM_PORT = "COM18";

	/**
	 * Comfortably longer than ACK_TIMER_DURATION (2 s) plus the driver's retry
	 * allowance, so a teardown has every chance to happen before we judge.
	 */
	private static final long OBSERVATION_WINDOW_MS = 30000;

	private BasicShimmerBluetoothManagerPc mBtManager;
	private long mTimeOfConnect;

	private volatile boolean mSawConnectionTeardown = false;
	private volatile boolean mSawFullyInitialised = false;
	private final List<String> mTransitions = new ArrayList<String>();

	public static void main(String[] args) throws Exception {
		new TestNackNotStreaming().run();
	}

	private void run() throws Exception {
		mBtManager = new BasicShimmerBluetoothManagerPc();
		setWaitForData(mBtManager.callBackObject);

		log("Connecting to " + COM_PORT + " - SD sync must be ENABLED on this device");
		mTimeOfConnect = System.currentTimeMillis();
		mBtManager.connectShimmerThroughCommPort(COM_PORT);

		long deadline = mTimeOfConnect + OBSERVATION_WINDOW_MS;
		while (System.currentTimeMillis() < deadline && !mSawConnectionTeardown) {
			Thread.sleep(250);
		}

		report();

		try {
			mBtManager.disconnectAllDevices();
		} catch (Exception e) {
			//Nothing useful to do here - the run is over either way
		}
		System.exit(0);
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

	private void report() {
		log("");
		log("---------------- observed ----------------");
		for (String t : mTransitions) {
			log(t);
		}
		log("------------------------------------------");

		if (mSawFullyInitialised) {
			log("INCONCLUSIVE - the device initialised, so SD sync was not enabled.");
			log("               Enable SD sync in Consensys and run this again.");
		}
		else if (mSawConnectionTeardown) {
			log("MASTER BEHAVIOUR - the link was torn down after the refusal.");
			log("                   This is the DEV-982 defect: a refused command");
			log("                   is indistinguishable from a dead link.");
		}
		else {
			log("PR #293 BEHAVIOUR - the link survived the refusals for "
					+ (OBSERVATION_WINDOW_MS / 1000) + " s.");
			log("                    Check the driver log for 'NACK Received for Command:'");
			log("                    to confirm the refusals were actually seen.");
		}
	}

	private void record(String event) {
		String line = String.format("t+%5d ms  %s",
				System.currentTimeMillis() - mTimeOfConnect, event);
		mTransitions.add(line);
		log(line);
	}

	private static void log(String msg) {
		System.out.println(msg);
	}
}
