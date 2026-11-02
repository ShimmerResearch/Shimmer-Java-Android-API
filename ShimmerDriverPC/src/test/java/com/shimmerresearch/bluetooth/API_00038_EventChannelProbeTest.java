package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.junit.After;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.SHIMMER_FEATURE;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.pcDriver.ShimmerPC;
import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortTimeoutException;

/**
 * DEV-971: whether a Shimmer has the event channel, and with it configuration
 * byte 232, is asked rather than read off its version, because the event
 * channel's builds report the release before them. The question is SET_FEATURE
 * with FEATURE_EVENT_STREAM and 0: firmware with the event channel ACKs it and
 * firmware without NACKs it.
 * <p>
 * The driver did not handle a NACK: it discarded it, kept waiting for the ACK,
 * and its ACK timer then dropped the connection. So the probe's NACK is now
 * handled. Firmware older than SET_FEATURE answers nothing at all and takes the
 * two bytes after 0xB7 as commands, so it is never asked.
 * <p>
 * No hardware: the IOThread runs as connect() starts it, over an in-memory
 * radio that answers as the firmware does. It lives in this package because
 * mIOThread is a protected member of ShimmerBluetooth.
 *
 * @author Mark Nolan
 */
public class API_00038_EventChannelProbeTest {

	private static final byte FEATURE_EVENT_STREAM = (byte) SHIMMER_FEATURE.EVENT_STREAM.ordinal();

	private static class AnsweringRadio implements ByteCommunication {
		final ConcurrentLinkedDeque<Byte> rx = new ConcurrentLinkedDeque<Byte>();
		final List<byte[]> written = Collections.synchronizedList(new ArrayList<byte[]>());
		/** How the firmware answers FEATURE_EVENT_STREAM */
		final byte eventStreamAnswer;

		AnsweringRadio(byte eventStreamAnswer) {
			this.eventStreamAnswer = eventStreamAnswer;
		}

		int countWritten(byte command) {
			int count = 0;
			synchronized(written){
				for(byte[] w:written){
					if(w[0]==command){
						count++;
					}
				}
			}
			return count;
		}

		@Override
		public boolean writeBytes(byte[] buffer) {
			written.add(buffer.clone());
			if(buffer[0]==ShimmerObject.SET_FEATURE && buffer[1]==FEATURE_EVENT_STREAM){
				rx.add(eventStreamAnswer);
			}
			else {
				// Every other command here is a SET, which the firmware acknowledges
				rx.add(ShimmerObject.ACK_COMMAND_PROCESSED);
			}
			return true;
		}

		@Override
		public byte[] readBytes(int byteCount, int timeout) throws SerialPortTimeoutException {
			if(rx.size()<byteCount){
				throw new SerialPortTimeoutException("TEST", "readBytes", timeout);
			}
			byte[] out = new byte[byteCount];
			for(int i=0;i<byteCount;i++){
				out[i] = rx.poll();
			}
			return out;
		}

		@Override public int getInputBufferBytesCount() { return rx.size(); }
		@Override public boolean isOpened() { return true; }
		@Override public boolean closePort() { return true; }
		@Override public boolean openPort() { return true; }
		@Override public boolean setParams(int i, int j, int k, int l) { return true; }
		@Override public boolean purgePort(int i) { return true; }
		@Override public void setByteCommunicationListener(ByteCommunicationListener byteCommListener) { }
		@Override public void removeRadioListenerList() { }
	}

	/** Records what the application would be told */
	private static class WatchedShimmer extends ShimmerPC {
		private static final long serialVersionUID = 1L;
		final List<Byte> progressReports = Collections.synchronizedList(new ArrayList<Byte>());
		volatile boolean connectionLost = false;

		WatchedShimmer() {
			super("COM99");
		}

		@Override
		protected void sendProgressReport(BluetoothProgressReportPerCmd pRPC) {
			progressReports.add((byte) pRPC.mCommandCompleted);
			super.sendProgressReport(pRPC);
		}

		@Override
		protected void connectionLost() {
			connectionLost = true;
			super.connectionLost();
		}
	}

	private WatchedShimmer mDevice;

	@After
	public void tearDown() {
		if(mDevice!=null){
			mDevice.stopTimerCheckForAckOrResp();
			mDevice.disconnectNoException();
		}
	}

	/** A connected Shimmer, not streaming, with its IOThread running as connect() starts it */
	private AnsweringRadio connectedShimmer(ShimmerVerObject svo, byte eventStreamAnswer) {
		AnsweringRadio radio = new AnsweringRadio(eventStreamAnswer);
		mDevice = new WatchedShimmer();
		mDevice.setTestRadio(radio);
		mDevice.setShimmerVersionObjectAndCreateSensorMap(svo);
		mDevice.setIsInitialised(true);
		mDevice.mIOThread = mDevice.new IOThread();
		mDevice.mIOThread.start();
		return radio;
	}

	/** Waits for the instruction list to empty, well inside the 5 s ACK timer that drops the connection */
	private void waitForInstructionsDone(long timeoutMs) throws InterruptedException {
		long end = System.currentTimeMillis()+timeoutMs;
		while((!mDevice.getListofInstructions().isEmpty() || mDevice.isInstructionStackLock()) && System.currentTimeMillis()<end){
			Thread.sleep(10);
		}
		assertTrue("every instruction answered", mDevice.getListofInstructions().isEmpty());
	}

	private static final ShimmerVerObject S3R_V1_1_25 = new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 25);

	@Test
	public void anAckMeansTheEventChannelIsThere() throws Exception {
		AnsweringRadio radio = connectedShimmer(S3R_V1_1_25, ShimmerObject.ACK_COMMAND_PROCESSED);
		mDevice.readEventChannelSupport();
		waitForInstructionsDone(2000);
		assertEquals(Boolean.TRUE, mDevice.isEventChannelSupported());
		assertEquals("sent as SET_FEATURE 3 0", 1, radio.countWritten(ShimmerObject.SET_FEATURE));
		byte[] probe = radio.written.get(0);
		assertArrayEquals(new byte[] {ShimmerObject.SET_FEATURE, FEATURE_EVENT_STREAM, 0}, probe);
	}

	@Test
	public void aNackMeansItIsNotAndTheConnectionStaysUp() throws Exception {
		AnsweringRadio radio = connectedShimmer(S3R_V1_1_25, ShimmerObject.NACK_COMMAND_PROCESSED);
		mDevice.readEventChannelSupport();
		// Queued behind the probe, as the connect sequence queues the rest
		mDevice.writeSetFeatureCommand(SHIMMER_FEATURE.NONE.ordinal(), 0);
		waitForInstructionsDone(2000);
		assertEquals(Boolean.FALSE, mDevice.isEventChannelSupported());
		assertEquals("the command behind it was sent", 2, radio.countWritten(ShimmerObject.SET_FEATURE));
		assertFalse(mDevice.connectionLost);
	}

	@Test
	public void aNackStillCountsTowardsTheOperation() throws Exception {
		connectedShimmer(S3R_V1_1_25, ShimmerObject.NACK_COMMAND_PROCESSED);
		mDevice.operationPrepare();
		mDevice.readEventChannelSupport();
		mDevice.writeSetFeatureCommand(SHIMMER_FEATURE.NONE.ordinal(), 0);
		mDevice.operationStart(BT_STATE.CONFIGURING);
		waitForInstructionsDone(2000);
		assertEquals("one report per command, the refused probe included", 2, mDevice.progressReports.size());
	}

	@Test
	public void firmwareOlderThanSetFeatureIsNotAsked() throws Exception {
		ShimmerVerObject[] older = {
				new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 0, 51),
				new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 0, 16),
				new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.BTSTREAM, 0, 8, 1),
		};
		for(ShimmerVerObject svo:older){
			WatchedShimmer shimmer = new WatchedShimmer();
			shimmer.setShimmerVersionObjectAndCreateSensorMap(svo);
			shimmer.readEventChannelSupport();
			assertEquals(svo.getFirmwareVersionParsed(), Boolean.FALSE, shimmer.isEventChannelSupported());
			assertTrue(svo.getFirmwareVersionParsed(), shimmer.getListofInstructions().isEmpty());
		}

		WatchedShimmer s3 = new WatchedShimmer();
		s3.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3, FW_ID.LOGANDSTREAM, 1, 0, 17));
		s3.readEventChannelSupport();
		assertNull("the first Shimmer3 release with SET_FEATURE is asked", s3.isEventChannelSupported());
		assertEquals(1, s3.getListofInstructions().size());
	}

	@Test
	public void aStreamingShimmerIsNotAsked() {
		WatchedShimmer shimmer = new WatchedShimmer();
		shimmer.setShimmerVersionObjectAndCreateSensorMap(S3R_V1_1_25);
		shimmer.setIsStreaming(true);
		shimmer.readEventChannelSupport();
		assertNull(shimmer.isEventChannelSupported());
		assertTrue(shimmer.getListofInstructions().isEmpty());
	}

}
