package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.pcDriver.ShimmerPC;
import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortTimeoutException;

/**
 * DEV-1306: a configuration started straight after stopStreaming() lost its first SET_INFOMEM, the
 * chunk that holds the sampling rate, so the unit kept its old rate. The Java HIL's samplingRates (DEV-1305)
 * found it on a Shimmer3 and a Shimmer3R: 4 of 4 configurations started within ~400 ms of a stop lost
 * the chunk, and 4 of 4 started on a quiet link sent it.
 * <p>
 * The STOP stays at the head of the instruction list, in flight, until its stop handling retires it:
 * the first byte after it (stream data, while streaming) starts that handling, which sleeps 400 ms,
 * then removes the head of the list (removeInstruction(0)). A configuration's operationPrepare()
 * clears the list, so inside that window it removed the STOP, the operation queued its chunks, and
 * the stop handling then removed the first chunk in the STOP's place.
 * <p>
 * No hardware: the IOThread runs as connect() starts it, over an in-memory radio that acknowledges
 * every SET_INFOMEM as the firmware does. It lives in this package because mIOThread is a protected
 * member of ShimmerBluetooth.
 */
public class API_00031_ConfigureStraightAfterStopTest {

	/** Three 128-byte chunks, as a Shimmer3 or Shimmer3R configuration is written */
	private static final int CONFIG_BYTES = 384;

	private static class AnsweringRadio implements ByteCommunication {
		final ConcurrentLinkedDeque<Byte> rx = new ConcurrentLinkedDeque<Byte>();
		final List<byte[]> written = Collections.synchronizedList(new ArrayList<byte[]>());
		final CountDownLatch stopSent = new CountDownLatch(1);

		void queue(byte... bytes) {
			for(byte b:bytes){
				rx.add(b);
			}
		}

		/** The address of each SET_INFOMEM written: [0x8C, length, address LSB, address MSB, data...] */
		List<Integer> infoMemAddressesWritten() {
			List<Integer> addresses = new ArrayList<Integer>();
			synchronized(written){
				for(byte[] w:written){
					if(w[0]==ShimmerObject.SET_INFOMEM_COMMAND){
						addresses.add((w[2]&0xFF) | ((w[3]&0xFF)<<8));
					}
				}
			}
			return addresses;
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
			if(buffer[0]==ShimmerObject.STOP_STREAMING_COMMAND){
				// Its reply is the test's to send, so the test controls when the stop handling starts
				stopSent.countDown();
			}
			else {
				// Every other command here is a SET, which the firmware acknowledges
				queue(ShimmerObject.ACK_COMMAND_PROCESSED);
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

	private ShimmerPC mDevice;

	@After
	public void tearDown() {
		if(mDevice!=null){
			mDevice.stopTimerCheckForAckOrResp();
			mDevice.disconnectNoException();
		}
	}

	/** An initialised Shimmer3R part way through streaming, with its IOThread running as connect() starts it */
	private AnsweringRadio streamingShimmer3r() {
		AnsweringRadio radio = new AnsweringRadio();
		mDevice = new ShimmerPC("COM99");
		mDevice.setTestRadio(radio);
		mDevice.setShimmerVersionObjectAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_3R, FW_ID.LOGANDSTREAM, 1, 1, 7));
		mDevice.setIsInitialised(true);
		mDevice.setIsStreaming(true);
		mDevice.mIOThread = mDevice.new IOThread();
		mDevice.mIOThread.start();
		return radio;
	}

	/** Writes a configuration the way ShimmerBluetoothManager does: prepare, queue the chunks, start */
	private void configure() {
		mDevice.operationPrepare();
		mDevice.writeConfigBytes(0, new byte[CONFIG_BYTES], CONFIG_BYTES-1, false);
		mDevice.operationStart(BT_STATE.CONFIGURING);
	}

	private List<Integer> waitForChunks(AnsweringRadio radio, int count, long timeoutMs) throws InterruptedException {
		long end = System.currentTimeMillis()+timeoutMs;
		while(radio.infoMemAddressesWritten().size()<count && System.currentTimeMillis()<end){
			Thread.sleep(20);
		}
		return radio.infoMemAddressesWritten();
	}

	/** The firmware ready for the configuration */
	private static final Integer[] ALL_THREE_CHUNKS = {0x0000, 0x0080, 0x0100};

	/** The first byte of a data packet: while streaming, the unit's next byte after the STOP is usually stream data */
	private static final byte STREAM_DATA = ShimmerObject.DATA_PACKET;

	/** Hands the stop handling its first byte, and waits until it has taken it: it then sleeps 400 ms */
	private void startStopHandling(AnsweringRadio radio, byte first) throws InterruptedException {
		radio.queue(first);
		long end = System.currentTimeMillis()+1000;
		while(!radio.rx.isEmpty() && System.currentTimeMillis()<end){
			Thread.sleep(5);
		}
		assertTrue("the stop handling took the byte", radio.rx.isEmpty());
	}

	/** As the HIL's samplingRates does: stop, then configure while the stop is still being handled */
	@Test
	public void aConfigurationStartedInTheStopWindowSendsEveryChunk() throws Exception {
		AnsweringRadio radio = streamingShimmer3r();
		mDevice.stopStreaming();
		assertTrue("the STOP was sent", radio.stopSent.await(2, TimeUnit.SECONDS));
		startStopHandling(radio, STREAM_DATA);

		configure();

		List<Integer> chunks = waitForChunks(radio, ALL_THREE_CHUNKS.length, 5000);
		assertEquals("the SET_INFOMEM chunks sent, by address", java.util.Arrays.asList(ALL_THREE_CHUNKS), chunks);
	}

	/**
	 * When the unit's ACK is the first byte after the STOP, with no data packet in flight, the stop
	 * handling retired the STOP and the same byte then went on as an ACK and retired the next
	 * instruction too: here a plain SET queued straight after stopStreaming(), with no configuration
	 * operation at all.
	 */
	@Test
	public void aCommandQueuedAfterTheStopIsSentWhenTheAckComesFirst() throws Exception {
		AnsweringRadio radio = streamingShimmer3r();
		mDevice.stopStreaming();
		mDevice.writeAccelRange(1);
		assertTrue("the STOP was sent", radio.stopSent.await(2, TimeUnit.SECONDS));
		startStopHandling(radio, ShimmerObject.ACK_COMMAND_PROCESSED);

		long end = System.currentTimeMillis()+3000;
		while(!radio.wrote(ShimmerObject.SET_ACCEL_SENSITIVITY_COMMAND) && System.currentTimeMillis()<end){
			Thread.sleep(20);
		}
		assertTrue("the SET_ACCEL_SENSITIVITY queued after the STOP was sent", radio.wrote(ShimmerObject.SET_ACCEL_SENSITIVITY_COMMAND));
	}

	/** The same configuration once the stop has finished: what Consensys does, seconds after a stop */
	@Test
	public void aConfigurationStartedAfterTheStopSendsEveryChunk() throws Exception {
		AnsweringRadio radio = streamingShimmer3r();
		mDevice.stopStreaming();
		assertTrue("the STOP was sent", radio.stopSent.await(2, TimeUnit.SECONDS));
		radio.queue(STREAM_DATA);
		long end = System.currentTimeMillis()+3000;
		while(!mDevice.getListofInstructions().isEmpty() && System.currentTimeMillis()<end){
			Thread.sleep(20);
		}
		assertTrue("the stop was handled", mDevice.getListofInstructions().isEmpty());

		configure();

		List<Integer> chunks = waitForChunks(radio, ALL_THREE_CHUNKS.length, 5000);
		assertEquals("the SET_INFOMEM chunks sent, by address", java.util.Arrays.asList(ALL_THREE_CHUNKS), chunks);
	}
}
