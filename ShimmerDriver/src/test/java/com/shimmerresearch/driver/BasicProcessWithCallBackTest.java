package com.shimmerresearch.driver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class BasicProcessWithCallBackTest {

	/** Records each message it gets, and throws for identifier 1. */
	private static class ThrowingConsumer extends BasicProcessWithCallBack {
		final List<Integer> received = new CopyOnWriteArrayList<Integer>();
		final CountDownLatch gotLast = new CountDownLatch(1);

		@Override
		protected void processMsgFromCallback(ShimmerMsg shimmerMSG) {
			received.add(shimmerMSG.mIdentifier);
			if (shimmerMSG.mIdentifier == 1) {
				throw new IllegalStateException("simulated failure in a callback handler");
			}
			if (shimmerMSG.mIdentifier == 3) {
				gotLast.countDown();
			}
		}
	}

	/**
	 * A runtime exception from one message must not kill the consumer thread.
	 * When it did, the queue stopped draining, producers blocked in put() once it
	 * held 1024 messages, and Consensys hung at exit inside its shutdown hook.
	 */
	@Test
	public void consumerThreadSurvivesExceptionInHandler() throws Exception {
		ThrowingConsumer consumer = new ThrowingConsumer();
		consumer.startConsumerThreadIfNull();
		try {
			consumer.queueMethod(new ShimmerMsg(1, null));
			consumer.queueMethod(new ShimmerMsg(2, null));
			consumer.queueMethod(new ShimmerMsg(3, null));

			assertTrue("message after the exception was never processed",
					consumer.gotLast.await(5, TimeUnit.SECONDS));
			assertEquals(3, consumer.received.size());
			assertTrue(consumer.mGUIConsumerThread.isAlive());
		} finally {
			consumer.stopConsumerThread();
		}
	}
}
