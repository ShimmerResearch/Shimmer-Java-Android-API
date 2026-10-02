package com.shimmerresearch.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What one call into a protocol state machine produced: the bytes the host must write to the
 * device, one entry per write, and the events to deliver, both in order.
 */
public final class ProtocolOutput {

	private final List<byte[]> mWrites = new ArrayList<byte[]>();
	private final List<ProtocolEvent> mEvents = new ArrayList<ProtocolEvent>();

	void write(byte[] bytes) {
		mWrites.add(bytes);
	}

	void event(ProtocolEvent event) {
		mEvents.add(event);
	}

	public List<byte[]> getWrites() {
		return Collections.unmodifiableList(mWrites);
	}

	public List<ProtocolEvent> getEvents() {
		return Collections.unmodifiableList(mEvents);
	}

	public boolean isEmpty() {
		return mWrites.isEmpty() && mEvents.isEmpty();
	}
}
