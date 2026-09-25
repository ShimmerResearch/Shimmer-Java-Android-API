package com.shimmerresearch.comms.wiredProtocol.neurolynq;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

import com.shimmerresearch.comms.serialPortInterface.AbstractSerialPortHal;
import com.shimmerresearch.comms.serialPortInterface.SerialPortListener;
import com.shimmerresearch.comms.wiredProtocol.ShimmerCrc;
import com.shimmerresearch.comms.wiredProtocol.UartPacketDetails.UART_COMPONENT;
import com.shimmerresearch.comms.wiredProtocol.UartPacketDetails.UART_PACKET_CMD;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.END_STATUS;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.ERASE_SCOPE;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.FileEntry;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.Files;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.Info;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.ReadEnd;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.ReadFrame;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.STATE;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.SESSION_STATE;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.Session;
import com.shimmerresearch.exceptions.ShimmerException;

/**
 * A NeuroLynQ node's storage on a simulated dock link (DEV-1061), to test a host without
 * a node: it answers the `$` storage component as the firmware does
 * (verisense-firmware GqDockProtocol/gq_dock_storage.c), from sessions held in memory,
 * and can drop, corrupt and cut short what it sends. neurolynq-web-sdk's
 * SimulatedNeuroLynqNode is the web host's.
 * <p>
 * Like the firmware, a READ goes out a NAND page at a time - {@link #PAGE_PAYLOAD_BYTES}
 * of the file, as frames of at most 245 bytes that restart at each page boundary - on a
 * thread of its own, as a serial port's bytes arrive on its reader's. Anything but the
 * storage component is answered BAD_CMD.
 * <p>
 * It sits with the production code, not the tests, because Shimmer-Advance-API's dock
 * tests drive it too, and a project's test classes are not visible to another's.
 */
public class SimulatedNeuroLynQNodeSerialPort extends AbstractSerialPortHal {

	/** A TC58 page's payload (storage spec section 5.2): where the firmware's frames restart */
	public static final int PAGE_PAYLOAD_BYTES = 4080;

	private static final byte COMPONENT = UART_COMPONENT.STORAGE.toCmdByte();
	private static final byte PROP_INFO = 0x00;
	private static final byte PROP_SESSION = 0x01;
	private static final byte PROP_FILES = 0x02;
	private static final byte PROP_READ = 0x03;
	private static final byte PROP_END = 0x04;
	private static final byte PROP_ERASE = 0x05;
	private static final byte PROP_FORMAT = 0x06;

	/** A session the node holds */
	public static final class SimSession {
		public int handle;
		public int dbSession;
		public int folder;
		public int flags;
		public int state = SESSION_STATE.CLOSED;
		public String trialFolder;
		public String sessionFolder;
		public final List<byte[]> files = new ArrayList<byte[]>();
	}

	private final List<SimSession> mSessions = new ArrayList<SimSession>();
	private final ExecutorService mDevice = Executors.newSingleThreadExecutor();
	private final Object mRxLock = new Object();
	private byte[] mRx = new byte[0];
	private transient SerialPortListener mListener;
	private boolean mConnected = false;

	/** False: the component is unknown, as on a GQ or a node without storage */
	public volatile boolean hasStorage = true;
	public volatile int state = STATE.READY;
	/** Data frames, counted over every burst from 0, that are not sent */
	public final Set<Integer> dropFrames = new HashSet<Integer>();
	/** Data frames whose `$` CRC is sent wrong */
	public final Set<Integer> corruptFrames = new HashSet<Integer>();
	/** Bursts, counted from 0, whose END is not sent */
	public final Set<Integer> loseEnds = new HashSet<Integer>();
	/** Bursts answered with a CRC-32 in END that does not match what was sent */
	public final Set<Integer> wrongCrcEnds = new HashSet<Integer>();
	/** Bursts cut off by a BAD_ARG after this many data frames; -1 none */
	public volatile int nackAfterFrames = -1;
	/** Each page's bytes arrive in reads of this size, split across frames; 0 a page at a time */
	public volatile int chunkBytes = 0;
	/** Requests seen, in order: the first argument byte of each READ's offset is in the log */
	public final List<String> requests = new ArrayList<String>();

	private int mFramesSent = 0;
	private int mBursts = 0;

	public SimSession addSession(int handle, int dbSession, String trialFolder, String sessionFolder, byte[]... files) {
		SimSession s = new SimSession();
		s.handle = handle;
		s.dbSession = dbSession;
		s.trialFolder = trialFolder;
		s.sessionFolder = sessionFolder;
		for (byte[] f : files) {
			s.files.add(f);
		}
		synchronized (mSessions) {
			mSessions.add(s);
		}
		return s;
	}

	public List<SimSession> sessions() {
		synchronized (mSessions) {
			return new ArrayList<SimSession>(mSessions);
		}
	}

	// ------------------------------------------------------------ the link

	@Override
	public void connect() throws ShimmerException {
		mConnected = true;
	}

	@Override
	public void disconnect() throws ShimmerException {
		mConnected = false;
	}

	@Override
	public void closeSafely() throws ShimmerException {
		mConnected = false;
	}

	@Override
	public void clearSerialPortRxBuffer() throws ShimmerException {
		synchronized (mRxLock) {
			mRx = new byte[0];
		}
	}

	@Override
	public boolean isSerialPortReaderStarted() {
		return true;
	}

	@Override
	public void setVerboseMode(boolean verboseMode, boolean isDebugMode) {
	}

	@Override
	public boolean bytesAvailableToBeRead() throws ShimmerException {
		return availableBytes() > 0;
	}

	@Override
	public int availableBytes() throws ShimmerException {
		synchronized (mRxLock) {
			return mRx.length;
		}
	}

	@Override
	public boolean isConnected() {
		return mConnected;
	}

	@Override
	public boolean isDisonnected() {
		return !mConnected;
	}

	@Override
	public void registerSerialPortRxEventCallback(SerialPortListener listener) {
		mListener = listener;
	}

	/** Blocks, as a port does, until {@code numBytes} have arrived or the timeout passes */
	@Override
	public byte[] rxBytes(int numBytes) throws ShimmerException {
		long deadline = System.currentTimeMillis() + mSerialPortTimeout;
		synchronized (mRxLock) {
			while (mRx.length < numBytes) {
				long wait = deadline - System.currentTimeMillis();
				if (wait <= 0) {
					throw new ShimmerException("simulated node: " + mRx.length + " of " + numBytes + " bytes");
				}
				try {
					mRxLock.wait(wait);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new ShimmerException("simulated node: interrupted");
				}
			}
			byte[] out = new byte[numBytes];
			System.arraycopy(mRx, 0, out, 0, numBytes);
			byte[] rest = new byte[mRx.length - numBytes];
			System.arraycopy(mRx, numBytes, rest, 0, rest.length);
			mRx = rest;
			return out;
		}
	}

	/** The host's frame: answered on the device's thread, as a node answers its port */
	@Override
	public void txBytes(byte[] frame) throws ShimmerException {
		final byte[] copy = frame.clone();
		mDevice.execute(new Runnable() {
			@Override
			public void run() {
				answer(copy);
			}
		});
	}

	/** Stop the device's and the reader's threads */
	public void shutdown() {
		mStopped = true;
		mReader.interrupt();
		mDevice.shutdownNow();
		try {
			mDevice.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** The device's bytes into the port. The reader thread tells the listener, as a port's
	 * reader does, so a listener waiting in rxBytes() for the rest of a frame never waits
	 * on the thread that is to send it. */
	private void deliver(byte[] bytes) {
		synchronized (mRxLock) {
			byte[] joined = new byte[mRx.length + bytes.length];
			System.arraycopy(mRx, 0, joined, 0, mRx.length);
			System.arraycopy(bytes, 0, joined, mRx.length, bytes.length);
			mRx = joined;
			mRxLock.notifyAll();
		}
	}

	private volatile boolean mStopped = false;
	private final Thread mReader = new Thread(new Runnable() {
		@Override
		public void run() {
			while (!mStopped) {
				int available;
				synchronized (mRxLock) {
					while (mRx.length == 0 && !mStopped) {
						try {
							mRxLock.wait(100);
						} catch (InterruptedException e) {
							return;
						}
					}
					available = mRx.length;
				}
				SerialPortListener listener = mListener;
				if (listener != null && available > 0) {
					listener.serialPortRxEvent(available);
				}
			}
		}
	}, "SimulatedNeuroLynQNode reader");

	{
		mReader.setDaemon(true);
		mReader.start();
	}

	private void deliverChunked(byte[] bytes) {
		int chunk = chunkBytes;
		if (chunk <= 0) {
			deliver(bytes);
			return;
		}
		for (int at = 0; at < bytes.length; at += chunk) {
			byte[] part = new byte[Math.min(chunk, bytes.length - at)];
			System.arraycopy(bytes, at, part, 0, part.length);
			deliver(part);
		}
	}

	// ----------------------------------------------------------- the frames

	private static byte[] withCrc(byte[] body) {
		byte[] crc = ShimmerCrc.shimmerUartCrcCalc(body, body.length);
		byte[] out = new byte[body.length + 2];
		System.arraycopy(body, 0, out, 0, body.length);
		System.arraycopy(crc, 0, out, body.length, 2);
		return out;
	}

	private static byte[] response(byte property, byte[] payload) {
		byte[] body = new byte[5 + payload.length];
		body[0] = '$';
		body[1] = UART_PACKET_CMD.DATA_RESPONSE.toCmdByte();
		body[2] = (byte) (2 + payload.length);
		body[3] = COMPONENT;
		body[4] = property;
		System.arraycopy(payload, 0, body, 5, payload.length);
		return withCrc(body);
	}

	private static byte[] shortResponse(UART_PACKET_CMD cmd) {
		return withCrc(new byte[] { '$', cmd.toCmdByte() });
	}

	private void answer(byte[] frame) {
		if (frame.length < 4 || frame[0] != '$') {
			return;
		}
		if (!ShimmerCrc.shimmerUartCrcCheck(frame)) {
			deliver(shortResponse(UART_PACKET_CMD.BAD_CRC_RESPONSE));
			return;
		}
		byte cmd = frame[1];
		int len = (frame.length >= 7) ? (frame[2] & 0xFF) : 0;
		if (len < 2 || frame[3] != COMPONENT || !hasStorage) {
			deliver(shortResponse(UART_PACKET_CMD.BAD_CMD_RESPONSE));
			return;
		}
		byte prop = frame[4];
		byte[] args = new byte[len - 2];
		System.arraycopy(frame, 5, args, 0, args.length);
		synchronized (requests) {
			requests.add(String.format("%02X/%02X/%02X", cmd, frame[3], prop));
		}
		try {
			if (cmd == UART_PACKET_CMD.READ.toCmdByte()) {
				answerGet(prop, args);
			} else if (cmd == UART_PACKET_CMD.WRITE.toCmdByte()) {
				answerSet(prop, args);
			} else {
				deliver(shortResponse(UART_PACKET_CMD.BAD_CMD_RESPONSE));
			}
		} catch (IllegalArgumentException e) {
			deliver(shortResponse(UART_PACKET_CMD.BAD_ARG_RESPONSE));
		}
	}

	private SimSession sessionWithHandle(int handle) {
		for (SimSession s : sessions()) {
			if (s.handle == handle) {
				return s;
			}
		}
		throw new IllegalArgumentException("no session " + handle);
	}

	private void answerGet(byte prop, byte[] args) {
		List<SimSession> sessions = sessions();
		if (prop == PROP_INFO) {
			Info i = new Info();
			i.layoutVersion = 1;
			i.state = state;
			i.part = 1;
			i.pageBytes = 4096;
			i.pagesPerBlock = 64;
			i.blocks = 2048;
			i.capacityKiB = 522240;
			i.sessions = sessions.size();
			i.maxSessions = 103;
			i.generation = 1;
			i.readFrameMax = NeuroLynQStorageCodec.READ_DATA_MAX;
			i.readMax = NeuroLynQStorageCodec.READ_MAX;
			deliver(response(PROP_INFO, NeuroLynQStorageCodec.buildInfo(i)));
		} else if (prop == PROP_SESSION) {
			if (args.length != 2) {
				throw new IllegalArgumentException("SESSION args");
			}
			int index = (args[0] & 0xFF) | ((args[1] & 0xFF) << 8);
			if (index >= sessions.size()) {
				throw new IllegalArgumentException("SESSION index");
			}
			SimSession s = sessions.get(index);
			Session out = new Session();
			out.total = sessions.size();
			out.index = index;
			out.handle = s.handle;
			out.state = s.state;
			out.flags = s.flags;
			out.dbSession = s.dbSession;
			out.folder = s.folder;
			out.fileCount = s.files.size();
			long total = 0;
			for (byte[] f : s.files) {
				total += f.length;
			}
			out.totalBytes = total;
			out.trialFolder = s.trialFolder;
			out.sessionFolder = s.sessionFolder;
			deliver(response(PROP_SESSION, NeuroLynQStorageCodec.buildSession(out)));
		} else if (prop == PROP_FILES) {
			if (args.length != 4) {
				throw new IllegalArgumentException("FILES args");
			}
			SimSession s = sessionWithHandle((args[0] & 0xFF) | ((args[1] & 0xFF) << 8));
			int first = args[2] & 0xFF;
			int max = Math.min(args[3] & 0xFF, NeuroLynQStorageCodec.FILES_PER_ANSWER);
			Files f = new Files();
			f.handle = s.handle;
			f.total = s.files.size();
			f.first = first;
			List<FileEntry> entries = new ArrayList<FileEntry>();
			for (int i = first; i < s.files.size() && entries.size() < max; i++) {
				entries.add(new FileEntry(i, s.files.get(i).length));
			}
			f.files = entries;
			deliver(response(PROP_FILES, NeuroLynQStorageCodec.buildFiles(f)));
		} else if (prop == PROP_READ) {
			long[] a = NeuroLynQStorageCodec.parseReadArgs(args);
			SimSession s = sessionWithHandle((int) a[0]);
			if (a[1] >= s.files.size()) {
				throw new IllegalArgumentException("READ file");
			}
			byte[] file = s.files.get((int) a[1]);
			if (a[2] > file.length || a[3] == 0 || a[3] > NeuroLynQStorageCodec.READ_MAX) {
				throw new IllegalArgumentException("READ range");
			}
			burst(s.handle, (int) a[1], file, a[2], a[3]);
		} else {
			deliver(shortResponse(UART_PACKET_CMD.BAD_CMD_RESPONSE));
		}
	}

	/** A READ's burst: a page's frames at a time, then END, as gq_dock_storage.c sends it */
	private void burst(int handle, int file, byte[] bytes, long offset, long length) {
		int burst = mBursts++;
		long limit = Math.min(bytes.length, offset + length);
		CRC32 crc = new CRC32();
		long sent = 0;
		int framesThisBurst = 0;
		long at = offset;
		while (at < limit) {
			long pageEnd = Math.min(limit, (at / PAGE_PAYLOAD_BYTES + 1) * PAGE_PAYLOAD_BYTES);
			ByteArrayOutputStream page = new ByteArrayOutputStream();
			while (at < pageEnd) {
				if (nackAfterFrames >= 0 && framesThisBurst == nackAfterFrames) {
					deliverChunked(page.toByteArray());
					deliver(shortResponse(UART_PACKET_CMD.BAD_ARG_RESPONSE));
					return;
				}
				int n = (int) Math.min(NeuroLynQStorageCodec.READ_DATA_MAX, pageEnd - at);
				byte[] data = new byte[n];
				System.arraycopy(bytes, (int) at, data, 0, n);
				byte[] frame = response(PROP_READ, NeuroLynQStorageCodec.buildReadFrame(new ReadFrame(handle, file, at, data)));
				int index = mFramesSent++;
				framesThisBurst++;
				crc.update(data);
				sent += n;
				at += n;
				if (corruptFrames.contains(index)) {
					frame[frame.length - 1] ^= 0x5A;
				}
				if (!dropFrames.contains(index)) {
					page.write(frame, 0, frame.length);
				}
			}
			deliverChunked(page.toByteArray());
		}
		if (loseEnds.contains(burst)) {
			return;
		}
		ReadEnd end = new ReadEnd();
		end.handle = handle;
		end.file = file;
		end.reqOffset = offset;
		end.bytesSent = sent;
		end.fileSize = bytes.length;
		end.crc32 = wrongCrcEnds.contains(burst) ? (crc.getValue() ^ 1L) : crc.getValue();
		end.status = (offset + length > bytes.length) ? END_STATUS.EOF : END_STATUS.OK;
		deliver(response(PROP_END, NeuroLynQStorageCodec.buildReadEnd(end)));
	}

	private void answerSet(byte prop, byte[] args) {
		if (prop == PROP_ERASE) {
			if (args.length != 7 || args[3] != 'E' || args[4] != 'R' || args[5] != 'A' || args[6] != 'S') {
				throw new IllegalArgumentException("ERASE args");
			}
			int scope = args[0] & 0xFF;
			int handle = (args[1] & 0xFF) | ((args[2] & 0xFF) << 8);
			synchronized (mSessions) {
				if (scope == ERASE_SCOPE.ALL) {
					mSessions.clear();
				} else {
					int upTo = -1;
					for (int i = 0; i < mSessions.size(); i++) {
						if (mSessions.get(i).handle == handle) {
							upTo = i;
						}
					}
					if (upTo < 0 || mSessions.get(upTo).state == SESSION_STATE.OPEN) {
						throw new IllegalArgumentException("ERASE handle");
					}
					if (scope == ERASE_SCOPE.ONE) {
						mSessions.remove(upTo);
					} else if (scope == ERASE_SCOPE.UP_TO) {
						mSessions.subList(0, upTo + 1).clear();
					} else {
						throw new IllegalArgumentException("ERASE scope");
					}
				}
			}
			deliver(shortResponse(UART_PACKET_CMD.ACK_RESPONSE));
		} else if (prop == PROP_FORMAT) {
			if (args.length != 5 || args[1] != 'F' || args[2] != 'R' || args[3] != 'M' || args[4] != 'T') {
				throw new IllegalArgumentException("FORMAT args");
			}
			synchronized (mSessions) {
				mSessions.clear();
			}
			state = STATE.READY;
			deliver(shortResponse(UART_PACKET_CMD.ACK_RESPONSE));
		} else {
			deliver(shortResponse(UART_PACKET_CMD.BAD_CMD_RESPONSE));
		}
	}
}
