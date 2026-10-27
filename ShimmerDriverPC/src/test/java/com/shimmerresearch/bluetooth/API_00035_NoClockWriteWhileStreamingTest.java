package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.junit.After;
import org.junit.Test;

import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.pcDriver.ShimmerPC;
import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortTimeoutException;

/**
 * DEV-1337: on a Shimmer3R the counter that stamps each streamed packet is the real-world clock, so
 * a clock write (SET_RWC) while it streams steps that counter. Packets come out off the sample grid
 * and sometimes behind the one before, and a step back beyond the timestamp unwrap's reorder window
 * reads as a counter wrap, stamping the rest of the session 512 s late (DEV-1245, firmware).
 * startSDLogging() wrote the clock before every logging start, so starting logging mid-stream
 * triggered it every time; the Android HIL's midStreamSet and inStream failed on a Shimmer3R for it.
 * <p>
 * writeRealTimeClock() now queues nothing while a Shimmer3R streams. The connect sequence still
 * writes the clock, and a Shimmer3, whose timestamps stay in order across a clock write, still gets
 * it mid-stream.
 * <p>
 * No hardware: the IOThread runs as connect() starts it, over an in-memory radio that acknowledges
 * every command, and records what reaches the unit.
 */
public class API_00035_NoClockWriteWhileStreamingTest {

	private static final long WAIT_MS = 5000;

	/** Not docked, sensing or logging; RTC set */
	private static final byte STATUS_IDLE = 0x04;

	/** Sensing and SD logging; RTC set */
	private static final byte STATUS_SD_LOGGING = 0x0E;

	/** A Shimmer3R's second status byte: USB not plugged in */
	private static final byte STATUS_USB_UNPLUGGED = 0x00;

	private static class AnsweringRadio implements ByteCommunication {
		final ConcurrentLinkedDeque<Byte> rx = new ConcurrentLinkedDeque<Byte>();
		final List<byte[]> written = Collections.synchronizedList(new ArrayList<byte[]>());

		void queue(byte... bytes) {
			for(byte b:bytes){
				rx.add(b);
			}
		}

		List<Byte> commandsWritten() {
			List<Byte> commands = new ArrayList<Byte>();
			synchronized(written){
				for(byte[] w:written){
					commands.add(w[0]);
				}
			}
			return commands;
		}

		@Override
		public boolean writeBytes(byte[] buffer) {
			written.add(buffer.clone());
			queue(ShimmerObject.ACK_COMMAND_PROCESSED);
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

	private ShimmerPC mDevice;

	@After
	public void tearDown() {
		if(mDevice!=null){
			mDevice.stopTimerCheckForAckOrResp();
			mDevice.disconnectNoException();
		}
	}

	/** An initialised unit, streaming or not, with its IOThread running as connect() starts it */
	private AnsweringRadio connected(int hardwareVersion, boolean streaming) {
		AnsweringRadio radio = new AnsweringRadio();
		mDevice = new ShimmerPC("COM99");
		mDevice.setTestRadio(radio);
		mDevice.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(hardwareVersion, FW_ID.LOGANDSTREAM, 1, 1, 22));
		mDevice.setIsInitialised(true);
		mDevice.setIsStreaming(streaming);
		mDevice.mIOThread = mDevice.new IOThread();
		mDevice.mIOThread.start();
		return radio;
	}

	/** Waits until the queue is empty and nothing is in flight */
	private void waitForQueueToDrain() throws InterruptedException {
		long end = System.currentTimeMillis()+WAIT_MS;
		while(System.currentTimeMillis()<end){
			if(mDevice.getListofInstructions().isEmpty() && mDevice.mTransactionCompleted){
				return;
			}
			Thread.sleep(10);
		}
		fail("the instruction queue did not drain within " + WAIT_MS + " ms: " + mDevice.getListofInstructions().size() + " left");
	}

	@Test
	public void startSdLoggingOnAStreamingShimmer3rSendsNoClockWrite() throws Exception {
		AnsweringRadio radio = connected(HW_ID.SHIMMER_3R, true);
		mDevice.startSDLogging();
		waitForQueueToDrain();
		List<Byte> sent = radio.commandsWritten();
		assertFalse("no SET_RWC sent to a streaming Shimmer3R: " + sent, sent.contains(ShimmerObject.SET_RWC_COMMAND));
		assertTrue("logging still starts: " + sent, sent.contains(ShimmerObject.START_LOGGING_ONLY_COMMAND));
	}

	/** An app's own call is held back the same way */
	@Test
	public void writeRealTimeClockOnAStreamingShimmer3rSendsNothing() throws Exception {
		AnsweringRadio radio = connected(HW_ID.SHIMMER_3R, true);
		mDevice.writeRealTimeClock();
		waitForQueueToDrain();
		assertEquals("nothing sent", new ArrayList<Byte>(), radio.commandsWritten());
	}

	@Test
	public void startSdLoggingOnAnIdleShimmer3rStillWritesTheClockFirst() throws Exception {
		AnsweringRadio radio = connected(HW_ID.SHIMMER_3R, false);
		mDevice.startSDLogging();
		waitForQueueToDrain();
		List<Byte> sent = radio.commandsWritten();
		assertEquals("SET_RWC, then START_LOGGING_ONLY: " + sent, 2, sent.size());
		assertEquals(ShimmerObject.SET_RWC_COMMAND, (byte) sent.get(0));
		assertEquals(ShimmerObject.START_LOGGING_ONLY_COMMAND, (byte) sent.get(1));
	}

	/**
	 * A Shimmer3 keeps its timestamps in order across a clock write, so it is unchanged. Only the
	 * first command is checked: while streaming the driver takes an ACK only among stream data, which
	 * this radio doesn't send, so the clock write's ACK timeout clears the logging start behind it.
	 */
	@Test
	public void startSdLoggingOnAStreamingShimmer3StillWritesTheClock() throws Exception {
		AnsweringRadio radio = connected(HW_ID.SHIMMER_3, true);
		mDevice.startSDLogging();
		waitForQueueToDrain();
		List<Byte> sent = radio.commandsWritten();
		assertFalse("something was sent", sent.isEmpty());
		assertEquals("SET_RWC sent first to a streaming Shimmer3: " + sent, ShimmerObject.SET_RWC_COMMAND, (byte) sent.get(0));
	}

	/**
	 * The clock is set at connect instead: the connect sequence reads the status, and while the unit
	 * isn't initialised or logging, that reply queues a clock write. No IOThread here: the reply is
	 * handed to the driver's parser as the IO thread does, and the queue is inspected.
	 */
	private ShimmerPC connectingShimmer3r(byte status) {
		AnsweringRadio radio = new AnsweringRadio();
		mDevice = new ShimmerPC("COM99");
		mDevice.setTestRadio(radio);
		mDevice.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 22));
		mDevice.mCurrentCommand = ShimmerObject.GET_STATUS_COMMAND;
		radio.queue(ShimmerObject.STATUS_RESPONSE, status, STATUS_USB_UNPLUGGED);
		mDevice.processInstreamResponse(false);
		assertTrue("the whole reply is read", radio.rx.isEmpty());
		return mDevice;
	}

	private static boolean queued(ShimmerPC device, byte command) {
		for(byte[] instruction:device.getListofInstructions()){
			if(instruction!=null && instruction[0]==command){
				return true;
			}
		}
		return false;
	}

	@Test
	public void aConnectToAUnitNotLoggingWritesTheClock() {
		ShimmerPC device = connectingShimmer3r(STATUS_IDLE);
		assertTrue("SET_RWC queued during connect", queued(device, ShimmerObject.SET_RWC_COMMAND));
	}

	/** A logging unit's clock is left alone, so its SD log isn't stepped */
	@Test
	public void aConnectToALoggingUnitLeavesTheClock() {
		ShimmerPC device = connectingShimmer3r(STATUS_SD_LOGGING);
		assertFalse("no SET_RWC queued for a logging unit", queued(device, ShimmerObject.SET_RWC_COMMAND));
	}
}
