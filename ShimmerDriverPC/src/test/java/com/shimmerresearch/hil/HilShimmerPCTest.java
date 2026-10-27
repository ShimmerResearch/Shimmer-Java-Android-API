package com.shimmerresearch.hil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.shimmerresearch.pcDriver.ShimmerPC;

/**
 * configureAccelOnly copies the device the way Consensys does (deepClone()) while the driver's
 * threads keep logging into it. The copy must not trip over the log (DEV-1310).
 */
public class HilShimmerPCTest {

	@Test
	public void clonesWhileTheDriverLogs() throws Exception {
		HilShimmerPC device = new HilShimmerPC("COM99", System.nanoTime());
		for (int i = 0; i < 5000; i++) {
			device.consolePrintLn("line " + i);
		}
		AtomicBoolean stop = new AtomicBoolean();
		// A line a millisecond or so: often enough to land in most copies, slowly enough to stay
		// under the log's cap (MAX_LOG_LINES), past which it no longer changes
		Thread driver = new Thread(() -> {
			while (!stop.get()) {
				device.consolePrintLn("GET_STATUS_COMMAND Instruction added");
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					return;
				}
			}
		}, "driver");
		driver.setDaemon(true);
		driver.start();
		try {
			for (int i = 1; i <= 100; i++) {
				ShimmerPC clone = device.deepClone();
				assertNotNull("clone " + i, clone);
				assertEquals("clone " + i + " starts its own log", 0, ((HilShimmerPC) clone).logSize());
			}
		} finally {
			stop.set(true);
			driver.join(5000);
		}
	}
}
