package com.shimmerresearch.shimmer3.communication;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.pcDriver.ShimmerPC;

/**
 * DEV-1275: the connect sequence reads the unit's status part way through, and
 * ShimmerPC.eventLogAndStreamStatusChanged() moved CONNECTING to CONNECTED on
 * that reply. ShimmerDeviceCallbackAdapter marks every CONNECTED initialised,
 * so an app waiting for CONNECTED and initialised was told the unit was ready
 * while the rest of the sequence (battery, calibration dump, inquiry) was still
 * queued. Whatever it did next went out in among that sequence: streaming
 * started part way through it, and a configure cut the calibration dump short,
 * which left a Shimmer3's accel NaN. The sequence's own end, inquiryDone() and
 * then isReadyForStreaming(), is what moves CONNECTING to CONNECTED.
 * <p>
 * No hardware: ByteCommunicationSimulatorS3 answers the whole connect sequence,
 * the status read included.
 */
public class API_00031_ConnectReadyAfterConnectSequenceTest {

	private ShimmerPC mDevice;
	private volatile boolean mStatusRead;
	private volatile boolean mSequenceEnded;
	/** What had happened when the state first became CONNECTED; null until then */
	private volatile Boolean mStatusReadAtFirstConnected;
	private volatile Boolean mSequenceEndedAtFirstConnected;
	private volatile List<String> mQueuedAtFirstConnected;

	@Before
	public void setUp() {
		mDevice = new ShimmerPC("COM99") {
			@Override
			public void startTimerReadStatus() {
				// As in ByteCommunicationTestUtils.newShimmerPCWithoutStatusTimer: only the
				// connect sequence's own status read should run
			}

			@Override
			protected void eventLogAndStreamStatusChanged(byte currentCommand) {
				mStatusRead = true;
				super.eventLogAndStreamStatusChanged(currentCommand);
			}

			@Override
			protected void isReadyForStreaming() {
				mSequenceEnded = true;
				super.isReadyForStreaming();
			}

			@Override
			public boolean setBluetoothRadioState(BT_STATE state) {
				if(state==BT_STATE.CONNECTED && mSequenceEndedAtFirstConnected==null){
					mStatusReadAtFirstConnected = mStatusRead;
					mSequenceEndedAtFirstConnected = mSequenceEnded;
					List<String> queued = new ArrayList<>();
					for(byte[] instruction:new ArrayList<>(getListofInstructions())){
						if(instruction!=null){
							queued.add(btCommandToString(instruction[0]));
						}
					}
					mQueuedAtFirstConnected = queued;
				}
				return super.setBluetoothRadioState(state);
			}
		};
		mDevice.setTestRadio(new ByteCommunicationSimulatorS3("COM99"));
	}

	@After
	public void tearDown() {
		mDevice.disconnectNoException();
	}

	@Test
	public void theUnitIsReportedReadyOnlyOnceTheConnectSequenceHasEnded() {
		ByteCommunicationTestUtils.connectAndWaitUntilInitialised(mDevice);

		assertNotNull("the state became CONNECTED", mSequenceEndedAtFirstConnected);
		assertTrue("the connect sequence read the status before the end, so the case is covered", mStatusReadAtFirstConnected);
		assertTrue("CONNECTED came only once the connect sequence had ended (inquiryDone, isReadyForStreaming); still queued: "
				+ mQueuedAtFirstConnected, mSequenceEndedAtFirstConnected);
		// Every read of the unit's configuration (status, battery, calibration dump,
		// inquiry) is answered. What may remain: the inquiry itself, still at the head
		// while its reply is handled, and the RTC write that the status reply queues
		// when the unit's clock is not set. That write runs before anything the app
		// queues, so it never lands in a stream.
		List<String> allowed = new ArrayList<>();
		allowed.add(ShimmerBluetooth.btCommandToString(ShimmerObject.INQUIRY_COMMAND));
		allowed.add(ShimmerBluetooth.btCommandToString(ShimmerObject.SET_RWC_COMMAND));
		for(String queued:mQueuedAtFirstConnected){
			assertTrue("no read from the connect sequence is still queued at CONNECTED, but " + queued + " is (queued: "
					+ mQueuedAtFirstConnected + ")", allowed.contains(queued));
		}
	}
}
