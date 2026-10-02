package com.shimmerresearch.protocol;

import com.shimmerresearch.driver.ObjectCluster;

/** Something a protocol state machine reports. */
public final class ProtocolEvent {

	public enum Type {
		/** The state changed; see {@link #state}. */
		STATE_CHANGED,
		/** The handshake finished: the device is configured and ready to stream. */
		INITIALISED,
		/** One decoded data packet; see {@link #sample}. */
		SAMPLE,
		/** The protocol failed, for instance a command timed out; see {@link #message}. */
		ERROR,
		/** Bytes were dropped while resynchronising; see {@link #message}. */
		DISCARDED
	}

	public final Type type;
	public final Shimmer3RProtocol.State state;
	public final ObjectCluster sample;
	public final String message;

	private ProtocolEvent(Type type, Shimmer3RProtocol.State state, ObjectCluster sample, String message) {
		this.type = type;
		this.state = state;
		this.sample = sample;
		this.message = message;
	}

	static ProtocolEvent stateChanged(Shimmer3RProtocol.State state) {
		return new ProtocolEvent(Type.STATE_CHANGED, state, null, null);
	}

	static ProtocolEvent initialised(String summary) {
		return new ProtocolEvent(Type.INITIALISED, null, null, summary);
	}

	static ProtocolEvent sample(ObjectCluster sample) {
		return new ProtocolEvent(Type.SAMPLE, null, sample, null);
	}

	static ProtocolEvent error(String message) {
		return new ProtocolEvent(Type.ERROR, null, null, message);
	}

	static ProtocolEvent discarded(String message) {
		return new ProtocolEvent(Type.DISCARDED, null, null, message);
	}

	@Override
	public String toString() {
		switch (type) {
		case STATE_CHANGED:
			return "STATE_CHANGED " + state;
		case SAMPLE:
			return "SAMPLE";
		default:
			return type + " " + message;
		}
	}
}
