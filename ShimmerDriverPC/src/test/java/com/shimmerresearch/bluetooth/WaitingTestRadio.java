package com.shimmerresearch.bluetooth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import com.shimmerresearch.shimmer3.communication.ByteCommunication;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortTimeoutException;

/**
 * An in-memory radio for tests that run the driver's IOThread, which reads
 * while the test writes. Two things keep such tests deterministic:
 * <ul>
 * <li>{@link #queue(byte[]...)} adds a burst whole, under the same lock reads
 * take, so the IOThread never sees half of it;</li>
 * <li>a read waits up to its timeout for the bytes it asks for, as a serial
 * port does, rather than failing the moment they are short.</li>
 * </ul>
 * Subclasses answer what the driver writes by overriding {@link #answer(byte[])}.
 */
abstract class WaitingTestRadio implements ByteCommunication {

	private final Deque<Byte> mRx = new ArrayDeque<Byte>();
	final List<byte[]> written = Collections.synchronizedList(new ArrayList<byte[]>());

	/** Adds the chunks as one burst: a read sees all of it or none of it */
	void queue(byte[]... chunks) {
		synchronized(mRx) {
			for(byte[] chunk:chunks) {
				for(byte b:chunk) {
					mRx.add(b);
				}
			}
		}
	}

	/** Adds single bytes as one burst */
	void queueBytes(byte... bytes) {
		queue(bytes);
	}

	/** The reply to one command the driver wrote, as one burst; empty for none */
	abstract byte[] answer(byte[] command);

	@Override
	public boolean writeBytes(byte[] buffer) {
		written.add(buffer.clone());
		byte[] reply = answer(buffer.clone());
		if(reply!=null && reply.length>0) {
			queue(reply);
		}
		return true;
	}

	@Override
	public byte[] readBytes(int byteCount, int timeout) throws SerialPortTimeoutException {
		long end = System.currentTimeMillis()+Math.max(timeout, 1);
		while(true) {
			synchronized(mRx) {
				if(mRx.size()>=byteCount) {
					byte[] out = new byte[byteCount];
					for(int i=0;i<byteCount;i++) {
						out[i] = mRx.poll();
					}
					return out;
				}
			}
			if(System.currentTimeMillis()>=end) {
				throw new SerialPortTimeoutException("TEST", "readBytes", timeout);
			}
			try {
				Thread.sleep(2);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new SerialPortTimeoutException("TEST", "readBytes", timeout);
			}
		}
	}

	@Override
	public int getInputBufferBytesCount() {
		synchronized(mRx) {
			return mRx.size();
		}
	}

	@Override public boolean isOpened() { return true; }
	@Override public boolean closePort() { return true; }
	@Override public boolean openPort() { return true; }
	@Override public boolean setParams(int i, int j, int k, int l) { return true; }
	@Override public boolean purgePort(int i) { return true; }
	@Override public void setByteCommunicationListener(ByteCommunicationListener byteCommListener) { }
	@Override public void removeRadioListenerList() { }

	/** The bytes of several chunks, in order */
	static byte[] concat(byte[]... chunks) {
		int n = 0;
		for(byte[] c:chunks) {
			n += c.length;
		}
		byte[] out = new byte[n];
		int at = 0;
		for(byte[] c:chunks) {
			System.arraycopy(c, 0, out, at, c.length);
			at += c.length;
		}
		return out;
	}

}
