package com.shimmerresearch.bluetooth;

import static org.junit.Assert.*;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Test;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.pcDriver.ShimmerPC;
import com.shimmerresearch.shimmer3.communication.ByteCommunicationSimulatorS3;
import com.shimmerresearch.shimmer3.communication.ByteCommunicationTestUtils;

/**
 * DEV-1266: ShimmerBluetooth guards its instruction list with two locks, the
 * device (its synchronized methods) and the list itself. Every accessor took
 * them device first, list second, but getInstruction(), which the IO thread
 * calls for the next instruction, took the list and then called the
 * synchronized getListofInstructions(). So the IO thread could hold the list
 * while waiting for the device, as a timer (the ACK/response timeout calling
 * clearAllInstructions(), or the read-status timer) held the device while
 * waiting for the list. Both then waited for good, and so did every later API
 * call. The Android HIL caught it on a Shimmer3 (stopSDLogging() never
 * returned), and it was the Linux CI hang in DEV-1198.
 */
public class API_00033_InstructionListLockOrderTest {

	private static final long TIMEOUT_MS = 5000;

	/** Only for the connect test: tearDown must not touch a device whose lock a deadlock may still hold */
	private ShimmerPC mDevice;

	@After
	public void tearDown() {
		if(mDevice!=null){
			mDevice.disconnectNoException();
		}
	}

	/**
	 * The two locks taken the way they collided: a timer holds the device, as
	 * every synchronized accessor does, while the IO thread asks for the next
	 * instruction; the timer then queues an instruction, which needs the list.
	 * The IO thread's real getInstruction() is called, as it is private.
	 */
	@Test
	public void theIoThreadAndATimerTakeTheTwoLocksInOneOrder() throws Exception {
		ShimmerPC device = new ShimmerPC("COM99");
		device.readBattery(); // getInstruction() reads the head of a non-empty list
		Method getInstruction = ShimmerBluetooth.class.getDeclaredMethod("getInstruction");
		getInstruction.setAccessible(true);

		CountDownLatch timerHoldsTheDevice = new CountDownLatch(1);
		Thread ioThread = daemon("IO thread", () -> {
			try {
				timerHoldsTheDevice.await();
				getInstruction.invoke(device);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		Thread timer = daemon("timer", () -> {
			synchronized(device){
				timerHoldsTheDevice.countDown();
				// Wait until the IO thread is either done or stuck waiting for the device
				while(ioThread.getState()!=Thread.State.BLOCKED && ioThread.getState()!=Thread.State.TERMINATED){
					Thread.yield();
				}
				device.readBattery(); // writeInstruction(): the device, then the list
			}
		});
		ioThread.start();
		timer.start();

		ioThread.join(TIMEOUT_MS);
		timer.join(TIMEOUT_MS);
		long[] deadlocked = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
		assertNull("the IO thread and the timer deadlocked on the device and the instruction list:\n"
				+ ByteCommunicationTestUtils.dumpAllThreads(), deadlocked);
		assertFalse("the IO thread finished", ioThread.isAlive());
		assertFalse("the timer finished", timer.isAlive());
		assertEquals("both instructions are queued", 2, device.getListofInstructions().size());
	}

	/**
	 * The product as it runs: the read-status timer is left on, so it queues
	 * GET_STATUS_COMMAND from its own thread while the IO thread works through the
	 * connect sequence. The simulator tests otherwise build their device without it
	 * (ByteCommunicationTestUtils.newShimmerPCWithoutStatusTimer), since DEV-1198.
	 */
	@Test
	public void connectsWithTheStatusTimerOn() {
		for(int i=1;i<=5;i++){
			mDevice = new ShimmerPC("COM99");
			mDevice.setTestRadio(new ByteCommunicationSimulatorS3("COM99"));
			ByteCommunicationTestUtils.connectAndWaitUntilInitialised(mDevice);
			assertEquals("connect " + i, BT_STATE.CONNECTED, mDevice.getBluetoothRadioState());
			mDevice.disconnectNoException();
		}
		mDevice = null;
	}

	private Thread daemon(String name, Runnable runnable) {
		Thread thread = new Thread(runnable, name);
		thread.setDaemon(true); // a deadlocked pair must not keep the test JVM alive
		return thread;
	}
}
