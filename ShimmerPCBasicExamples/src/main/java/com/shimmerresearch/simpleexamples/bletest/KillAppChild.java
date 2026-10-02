package com.shimmerresearch.simpleexamples.bletest;

/**
 * The process the "kill" test starts and then kills: connects, starts streaming, announces it, and
 * waits to be killed. Not meant to be run by hand.
 */
public class KillAppChild {

	public static void main(String[] args) throws Exception {
		TestOptions options = TestOptions.parse(args);
		BleTransport transport = BleTransport.create(options, null);
		DeviceUnderTest dut = transport.open(options.device, options.knownId());
		dut.connect(HardwareTests.CONNECT_TIMEOUT_MS);
		dut.startStreaming(HardwareTests.STREAM_TIMEOUT_MS);
		System.out.println(HardwareTests.CHILD_STREAMING + " id=" + dut.deviceId());
		System.out.flush();
		while (true) {
			Thread.sleep(1000);
		}
	}
}
