package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.pcDriver.ShimmerPC;
import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;

/**
 * DEV-895: the IOThread and ProcessingThread must sleep rather than spin when
 * idle, must stop promptly on disconnect(), and a disconnect() must stay a
 * disconnect. closeConnection() only waits a bounded 2 s for the IOThread, so
 * one blocked in a non-interruptible serial call outlives it, and the port is
 * closed under it. When that call then fails, the I/O helpers used to report
 * CONNECTION_LOST, which NeuroLynQ answers with an auto-reconnect.
 * <p>
 * No hardware: the threads are started as connect() starts them, over an
 * in-memory radio. It lives in this package because mIOThread and mPThread
 * are protected members of ShimmerBluetooth.
 */
public class API_00028_IoThreadTeardownTest {

	/** Thread-safe in-memory radio. Its writes can block, ignoring interrupts, as a native jssc call does. */
	private static class BlockingRadio implements ByteCommunication {
		final ConcurrentLinkedDeque<Byte> rx = new ConcurrentLinkedDeque<Byte>();
		final long writeBlockMs;
		final CountDownLatch writeStarted = new CountDownLatch(1);
		volatile boolean open = true;

		BlockingRadio(long writeBlockMs) {
			this.writeBlockMs = writeBlockMs;
		}

		@Override
		public boolean writeBytes(byte[] buffer) throws SerialPortException {
			writeStarted.countDown();
			if(writeBlockMs>0){
				long end = System.currentTimeMillis()+writeBlockMs;
				boolean interrupted = false;
				long left;
				while((left = end-System.currentTimeMillis())>0){
					try {
						Thread.sleep(left);
					} catch (InterruptedException e) {
						interrupted = true;
					}
				}
				if(interrupted){
					Thread.currentThread().interrupt();
				}
				if(!open){
					throw new SerialPortException("TEST", "writeBytes", "Port closed during write");
				}
			}
			return true;
		}

		@Override
		public byte[] readBytes(int byteCount, int timeout) throws SerialPortTimeoutException, SerialPortException {
			if(!open){
				throw new SerialPortException("TEST", "readBytes", "Port not opened");
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

		@Override
		public int getInputBufferBytesCount() throws SerialPortException {
			if(!open){
				throw new SerialPortException("TEST", "getInputBufferBytesCount", "Port not opened");
			}
			return rx.size();
		}

		@Override public boolean isOpened() { return open; }
		@Override public boolean closePort() { open = false; return true; }
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

	/** What connect() does once the port is open: start the IOThread and, if used, the ProcessingThread */
	private ShimmerPC connectedDevice(BlockingRadio radio, boolean useProcessingThread) {
		mDevice = new ShimmerPC("COM99");
		mDevice.setTestRadio(radio);
		mDevice.mUseProcessingThread = useProcessingThread;
		mDevice.mIOThread = mDevice.new IOThread();
		mDevice.mIOThread.start();
		if(useProcessingThread){
			mDevice.mPThread = mDevice.new ProcessingThread();
			mDevice.mPThread.start();
		}
		return mDevice;
	}

	/** An idle device's threads sleep: a spin would use ~1000 ms of CPU per second each */
	@Test
	public void idleThreadsDoNotSpin() throws Exception {
		ThreadMXBean mx = ManagementFactory.getThreadMXBean();
		Assume.assumeTrue("thread CPU time is not supported on this JVM", mx.isThreadCpuTimeSupported());
		if(!mx.isThreadCpuTimeEnabled()){
			mx.setThreadCpuTimeEnabled(true);
		}
		ShimmerPC device = connectedDevice(new BlockingRadio(0), true);
		Thread.sleep(300);

		long io0 = mx.getThreadCpuTime(device.mIOThread.getId());
		long p0 = mx.getThreadCpuTime(device.mPThread.getId());
		Thread.sleep(1000);
		long ioMs = (mx.getThreadCpuTime(device.mIOThread.getId())-io0)/1000000;
		long pMs = (mx.getThreadCpuTime(device.mPThread.getId())-p0)/1000000;

		assertTrue("IOThread used " + ioMs + " ms of CPU in 1 s idle", ioMs<300);
		assertTrue("ProcessingThread used " + pMs + " ms of CPU in 1 s idle", pMs<300);
	}

	/** disconnect() interrupts the idle threads rather than waiting out the bounded join */
	@Test
	public void disconnectStopsBothThreadsPromptly() throws Exception {
		ShimmerPC device = connectedDevice(new BlockingRadio(0), true);
		Thread.sleep(100);
		Thread ioThread = device.mIOThread;
		Thread pThread = device.mPThread;

		long start = System.nanoTime();
		device.disconnect();
		long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);

		assertTrue("disconnect() took " + tookMs + " ms", tookMs<1500);
		assertFalse("IOThread has stopped", ioThread.isAlive());
		assertFalse("ProcessingThread has stopped", pThread.isAlive());
		assertEquals(BT_STATE.DISCONNECTED, device.getBluetoothRadioState());
	}

	/**
	 * The IOThread is blocked in a 3 s write when disconnect() is called, so it
	 * outlives the 2 s join and its write then fails on the closed port. That
	 * failure is the disconnect, not a lost connection.
	 */
	@Test
	public void writeFailingAfterDisconnectIsNotAConnectionLoss() throws Exception {
		BlockingRadio radio = new BlockingRadio(3000);
		ShimmerPC device = connectedDevice(radio, false);
		device.getListofInstructions().add(new byte[] {ShimmerBluetooth.GET_SAMPLING_RATE_COMMAND});
		assertTrue("the IOThread starts writing", radio.writeStarted.await(2, TimeUnit.SECONDS));
		Thread ioThread = device.mIOThread;

		device.disconnect();
		long disconnectedAt = System.currentTimeMillis();
		assertTrue("the IOThread is still blocked in its write", ioThread.isAlive());
		assertEquals(BT_STATE.DISCONNECTED, device.getBluetoothRadioState());

		ioThread.join(3000);
		assertFalse("the IOThread stops once its write fails", ioThread.isAlive());
		long left = disconnectedAt+2500-System.currentTimeMillis();
		if(left>0){
			Thread.sleep(left);
		}
		assertEquals("still disconnected, not CONNECTION_LOST", BT_STATE.DISCONNECTED, device.getBluetoothRadioState());
	}
}
