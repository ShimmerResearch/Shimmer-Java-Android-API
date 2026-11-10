package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.junit.After;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_CRC_MODE;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.comms.wiredProtocol.ShimmerCrc;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.pcDriver.ShimmerPC;
import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortTimeoutException;

/**
 * DEV-1431: firmware that cannot start a log (no SD card or a bad one, docked, on USB power, logging
 * turned off in its configuration) NACKs START_LOGGING_ONLY_COMMAND and START_SDBT_COMMAND and starts
 * nothing. The driver had no NACK handling: it dropped the 0xFE as a stray byte and waited out the ACK
 * timer, which then took START_LOGGING_ONLY for started (checkForAckOrRespTask's Consensys special
 * case) and dropped the connection for START_SDBT.
 * <p>
 * Now either NACK retires its command at once: the device is left not logging (and not streaming),
 * eventLogStartRefused() is called, the connection is kept and the next command goes out. The same
 * refusal in the middle of a stream is API_00037_StreamingLogStartRefusedTest.
 * <p>
 * No hardware: the IOThread runs as connect() starts it, over an in-memory radio that answers as the
 * firmware does. It lives in this package because mIOThread is a protected member of ShimmerBluetooth.
 */
public class API_00036_LogStartRefusedTest {

	/** Idle on a Shimmer3R: an SD card in, the clock set, not sensing; status byte 1 empty */
	private static final byte[] STATUS_IDLE = {0x24, 0x00};

	private static class AnsweringRadio implements ByteCommunication {
		final ConcurrentLinkedDeque<Byte> rx = new ConcurrentLinkedDeque<Byte>();
		final List<byte[]> written = Collections.synchronizedList(new ArrayList<byte[]>());
		/** What a log start gets: an ACK, or a NACK as firmware that refuses it sends */
		volatile byte startReply = ShimmerObject.NACK_COMMAND_PROCESSED;
		/** How many bytes of link CRC the firmware appends to every reply */
		volatile int crcBytes = 0;

		void reply(byte... bytes) {
			for(byte b:bytes){
				rx.add(b);
			}
			byte[] crc = ShimmerCrc.shimmerUartCrcCalc(bytes, bytes.length);
			for(int i=0;i<crcBytes;i++){
				rx.add(crc[i]);
			}
		}

		boolean wrote(byte command) {
			synchronized(written){
				for(byte[] w:written){
					if(w[0]==command){
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public boolean writeBytes(byte[] buffer) {
			written.add(buffer.clone());
			byte op = buffer[0];
			if(op==ShimmerObject.START_LOGGING_ONLY_COMMAND || op==ShimmerObject.START_SDBT_COMMAND){
				reply(startReply);
			}
			else if(op==ShimmerObject.GET_STATUS_COMMAND){
				reply(ShimmerObject.ACK_COMMAND_PROCESSED, ShimmerObject.INSTREAM_CMD_RESPONSE,
						ShimmerObject.STATUS_RESPONSE, STATUS_IDLE[0], STATUS_IDLE[1]);
			}
			else if(op==ShimmerObject.GET_RWC_COMMAND){
				// startDataLogAndStreaming() reads the clock first: [ACK][RWC_RESPONSE][8 bytes of ticks]
				byte[] rwc = new byte[10];
				rwc[0] = ShimmerObject.ACK_COMMAND_PROCESSED;
				rwc[1] = ShimmerObject.RWC_RESPONSE;
				reply(rwc);
			}
			else {
				// Every other command here is a SET, which the firmware acknowledges
				reply(ShimmerObject.ACK_COMMAND_PROCESSED);
			}
			return true;
		}

		@Override
		public byte[] readBytes(int byteCount, int timeout) throws SerialPortTimeoutException {
			long end = System.currentTimeMillis()+timeout;
			while(rx.size()<byteCount && System.currentTimeMillis()<end){
				try {
					Thread.sleep(2);
				} catch (InterruptedException e) {
					break;
				}
			}
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

	private ShimmerPC mDevice;
	private final List<Byte> mRefused = Collections.synchronizedList(new ArrayList<Byte>());

	@After
	public void tearDown() {
		if(mDevice!=null){
			mDevice.stopTimerCheckForAckOrResp();
			mDevice.stopTimerReadStatus();
			mDevice.disconnectNoException();
		}
	}

	/** An initialised, idle Shimmer3R on LogAndStream, its IOThread running as connect() starts it */
	private AnsweringRadio idleShimmer3r(BT_CRC_MODE crc) {
		AnsweringRadio radio = new AnsweringRadio();
		radio.crcBytes = crc.getNumCrcBytes();
		mDevice = new ShimmerPC("COM99") {
			@Override
			protected void eventLogStartRefused(byte command) {
				mRefused.add(command);
				super.eventLogStartRefused(command);
			}
		};
		mDevice.setTestRadio(radio);
		mDevice.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 25));
		mDevice.setCurrentBtCommsCrcMode(crc);
		mDevice.setIsInitialised(true);
		mDevice.setBluetoothRadioState(BT_STATE.CONNECTED);
		mDevice.mIOThread = mDevice.new IOThread();
		mDevice.mIOThread.start();
		return radio;
	}

	private void waitFor(long timeoutMs, java.util.function.BooleanSupplier done) throws InterruptedException {
		long end = System.currentTimeMillis()+timeoutMs;
		while(!done.getAsBoolean() && System.currentTimeMillis()<end){
			Thread.sleep(10);
		}
	}

	/** The refusal handled in well under the ACK timer, the device not logging and the link kept */
	private void assertRefusedAtOnce(AnsweringRadio radio, byte command, long sentAt) throws InterruptedException {
		waitFor(1500, () -> !mRefused.isEmpty());
		long took = System.currentTimeMillis()-sentAt;
		assertEquals("eventLogStartRefused, once, for the start sent", Collections.singletonList(command), mRefused);
		assertTrue("refused at once, not after the ACK timer (" + took + " ms)", took < 1500);
		assertFalse("the device is not taken to be logging", mDevice.isSDLogging());
		assertFalse("nor streaming", mDevice.isStreaming());
		assertNotEquals("the connection is kept", BT_STATE.CONNECTION_LOST, mDevice.getBluetoothRadioState());

		// The next command goes out and is answered: nothing is left waiting on the refused start
		mDevice.writeAccelRange(1);
		waitFor(3000, () -> radio.wrote(ShimmerObject.SET_ACCEL_SENSITIVITY_COMMAND) && mDevice.getListofInstructions().isEmpty());
		assertTrue("the next command was sent", radio.wrote(ShimmerObject.SET_ACCEL_SENSITIVITY_COMMAND));
		assertTrue("and acknowledged", mDevice.getListofInstructions().isEmpty());
		assertNotEquals("the connection is still kept", BT_STATE.CONNECTION_LOST, mDevice.getBluetoothRadioState());
	}

	@Test
	public void aRefusedLogOnlyStartLeavesTheDeviceNotLogging() throws Exception {
		AnsweringRadio radio = idleShimmer3r(BT_CRC_MODE.OFF);
		long sentAt = System.currentTimeMillis();
		mDevice.startSDLogging();
		waitFor(2000, () -> radio.wrote(ShimmerObject.START_LOGGING_ONLY_COMMAND));
		assertTrue("START_LOGGING_ONLY was sent", radio.wrote(ShimmerObject.START_LOGGING_ONLY_COMMAND));
		assertRefusedAtOnce(radio, ShimmerObject.START_LOGGING_ONLY_COMMAND, sentAt);
	}

	/**
	 * The NACK's CRC (C5, or C5 56) is left unread, as an ACK's is when not streaming, and the reply
	 * to the next command is found past it
	 */
	private void aRefusedLogOnlyStartWithALinkCrc(BT_CRC_MODE crc) throws Exception {
		AnsweringRadio radio = idleShimmer3r(crc);
		long sentAt = System.currentTimeMillis();
		mDevice.startSDLogging();
		waitFor(2000, () -> radio.wrote(ShimmerObject.START_LOGGING_ONLY_COMMAND));
		assertRefusedAtOnce(radio, ShimmerObject.START_LOGGING_ONLY_COMMAND, sentAt);
	}

	@Test
	public void aRefusedLogOnlyStartWithAOneByteCrcLetsTheNextCommandThrough() throws Exception {
		aRefusedLogOnlyStartWithALinkCrc(BT_CRC_MODE.ONE_BYTE_CRC);
	}

	@Test
	public void aRefusedLogOnlyStartWithATwoByteCrcLetsTheNextCommandThrough() throws Exception {
		aRefusedLogOnlyStartWithALinkCrc(BT_CRC_MODE.TWO_BYTE_CRC);
	}

	@Test
	public void aRefusedStreamAndLogStartStartsNothingAndKeepsTheConnection() throws Exception {
		AnsweringRadio radio = idleShimmer3r(BT_CRC_MODE.OFF);
		long sentAt = System.currentTimeMillis();
		mDevice.startDataLogAndStreaming();
		waitFor(2000, () -> radio.wrote(ShimmerObject.START_SDBT_COMMAND));
		assertTrue("START_SDBT was sent", radio.wrote(ShimmerObject.START_SDBT_COMMAND));
		assertRefusedAtOnce(radio, ShimmerObject.START_SDBT_COMMAND, sentAt);
		// startDataLogAndStreaming() stopped the status reads; the refusal starts them again
		waitFor(2000, () -> radio.wrote(ShimmerObject.GET_STATUS_COMMAND));
		assertTrue("the status is read again", radio.wrote(ShimmerObject.GET_STATUS_COMMAND));
	}

	/** An ACKed start is unchanged: the device is taken to be logging, as before */
	@Test
	public void anAcknowledgedLogOnlyStartStillLogs() throws Exception {
		AnsweringRadio radio = idleShimmer3r(BT_CRC_MODE.OFF);
		radio.startReply = ShimmerObject.ACK_COMMAND_PROCESSED;
		mDevice.startSDLogging();
		waitFor(2000, () -> mDevice.isSDLogging());
		assertTrue("logging after the ACK", mDevice.isSDLogging());
		assertTrue("no refusal", mRefused.isEmpty());
	}
}
