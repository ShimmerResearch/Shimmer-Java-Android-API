package com.shimmerresearch.shimmer3.communication;

import static org.junit.Assert.fail;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.pcDriver.ShimmerPC;

/** Shared helpers for the byte-communication simulator tests. */
public class ByteCommunicationTestUtils {

	/** Generous on purpose: a connect to the simulator normally finishes in well under a second,
	 *  but a loaded CI runner can stall it for several. Only a genuine hang should hit this. */
	public static final long CONNECT_TIMEOUT_MS = 30000;

	/**
	 * A ShimmerPC for the simulator tests whose periodic read-status timer never starts.
	 * <p>
	 * The connect sequence unlocks the instruction stack and then starts that timer with no delay,
	 * so its first readStatusTask (which locks the device, then the instruction list) runs just as
	 * the IOThread takes the first post-connect instruction in ShimmerBluetooth.getInstruction()
	 * (which locks the list, then the device). When the two meet they deadlock and the connect never
	 * finishes. That hung these tests on Linux CI every time (DEV-1198). The driver is deliberately
	 * left as it is; the tests only lose the 5 s status poll, and the connect sequence still reads
	 * the status once.
	 */
	public static ShimmerPC newShimmerPCWithoutStatusTimer(String comPort) {
		return new ShimmerPC(comPort) {
			@Override
			public void startTimerReadStatus() {
				// Deliberately empty - see above
			}
		};
	}

	/**
	 * Connects and blocks until the device has finished its post-connect config read (infomem,
	 * calibration, etc.), i.e. isInitialised() is set and the state has moved to CONNECTED.
	 * isConnected() alone is not enough - it turns true before that read has run, so a fixed
	 * sleep made these tests flaky on slow runners.
	 */
	public static void connectAndWaitUntilInitialised(ShimmerPC device) {
		device.connect("","");
		long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
		while (!(device.isInitialised() && device.getBluetoothRadioState()==BT_STATE.CONNECTED)) {
			if (System.currentTimeMillis() > deadline) {
				// A hang here has so far only reproduced on CI, so print where every thread is
				// before failing - the log is the only evidence there is.
				System.out.println(dumpAllThreads());
				fail("Device did not finish initialising within " + CONNECT_TIMEOUT_MS + " ms (state: " + device.getBluetoothRadioState() + ")");
			}
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				fail("Interrupted while waiting for the device to initialise");
			}
		}
	}

	/** Every thread's full stack with the monitors it holds and the lock it waits on, plus any
	 *  deadlock the JVM can see. ThreadInfo.toString() stops at 8 frames, too few for the driver. */
	public static String dumpAllThreads() {
		ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
		StringBuilder sb = new StringBuilder("==== Thread dump ====\n");

		long[] deadlocked = mxBean.findDeadlockedThreads();
		if (deadlocked != null) {
			sb.append("DEADLOCK between thread ids:");
			for (long id : deadlocked) {
				sb.append(' ').append(id);
			}
			sb.append('\n');
		}

		for (ThreadInfo ti : mxBean.dumpAllThreads(true, true)) {
			sb.append('"').append(ti.getThreadName()).append("\" id=").append(ti.getThreadId())
					.append(' ').append(ti.getThreadState());
			if (ti.getLockName() != null) {
				sb.append(" on ").append(ti.getLockName());
			}
			if (ti.getLockOwnerName() != null) {
				sb.append(" owned by \"").append(ti.getLockOwnerName()).append("\" id=").append(ti.getLockOwnerId());
			}
			sb.append('\n');

			StackTraceElement[] stack = ti.getStackTrace();
			MonitorInfo[] monitors = ti.getLockedMonitors();
			for (int i = 0; i < stack.length; i++) {
				sb.append("\tat ").append(stack[i]).append('\n');
				for (MonitorInfo mi : monitors) {
					if (mi.getLockedStackDepth() == i) {
						sb.append("\t- locked ").append(mi).append('\n');
					}
				}
			}
			for (LockInfo li : ti.getLockedSynchronizers()) {
				sb.append("\t- locked synchronizer ").append(li).append('\n');
			}
			sb.append('\n');
		}
		return sb.append("==== End of thread dump ====").toString();
	}

}
