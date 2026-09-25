package com.shimmerresearch.comms.wiredProtocol.neurolynq;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import com.shimmerresearch.comms.wiredProtocol.AbstractCommsProtocolWired;
import com.shimmerresearch.comms.wiredProtocol.AbstractCommsProtocolWired.ReadBurst;
import com.shimmerresearch.comms.wiredProtocol.DockException;
import com.shimmerresearch.comms.wiredProtocol.ErrorCodesWiredProtocol;
import com.shimmerresearch.comms.wiredProtocol.UartPacketDetails.UART_COMPONENT_AND_PROPERTY;
import com.shimmerresearch.comms.wiredProtocol.UartRxPacketObject;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.END_STATUS;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.FileEntry;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.Files;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.Info;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.ReadEnd;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.ReadFrame;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.Session;

/**
 * A NeuroLynQ node's session storage, over its dock link: the host's half of the `$`
 * storage component 0x0C (DEV-1061), as neurolynq-web-sdk's NeuroLynqNodeClient is the
 * web host's. The rules for reading a file are that client's, so both hosts take the
 * same thing as a file read whole.
 * <p>
 * A node that answers INFO with BAD_CMD has no storage - a GQ, or a node whose firmware
 * predates it - and {@link #isStorageAbsent(DockException)} says so.
 * <p>
 * HARDWARE-VERIFY: tested against SimulatedNeuroLynQNodeSerialPort and the firmware's
 * codec vectors; no node has answered it.
 */
public class NeuroLynQStorageProtocol {

	/** The most one READ asks for: between windows, the host takes stock of what arrived */
	public static final long READ_WINDOW_BYTES = 512L * 1024L;
	/** Between two frames of a READ burst (storage spec section 7, "Timeouts a host should allow") */
	public static final long READ_STALL_MS = 1000;
	/** Windows running that brought nothing before a file read gives up */
	public static final int MAX_STALLS = 5;

	/** Progress through one file */
	public interface ReadProgressListener {
		void onProgress(long bytesRead, long fileSize);
	}

	/** A file the node could not give whole */
	public static class StorageReadException extends Exception {
		private static final long serialVersionUID = 1L;
		/** {@link END_STATUS} of the last window, or -1 when none ended */
		public final int endStatus;

		public StorageReadException(String message, int endStatus) {
			super(message);
			this.endStatus = endStatus;
		}
	}

	/** Thrown by {@link NeuroLynQStorageProtocol#readFile readFile()} once {@link #cancel()} is called */
	public static class ReadCancelledException extends StorageReadException {
		private static final long serialVersionUID = 1L;

		public ReadCancelledException(String message) {
			super(message, -1);
		}
	}

	/** One READ's worth: what arrived in order, and how the node ended it */
	public static final class ReadWindow {
		public byte[] data = new byte[0];
		public ReadEnd end;
		public boolean complete;
		public boolean crcMismatch;
		public int duplicates;
		public int pastGap;
		public int badFrames;
	}

	private final AbstractCommsProtocolWired mWired;
	private long mReadStallMs = READ_STALL_MS;
	private volatile boolean mCancelled = false;

	public NeuroLynQStorageProtocol(AbstractCommsProtocolWired wired) {
		mWired = wired;
	}

	public void setReadStallMs(long readStallMs) {
		mReadStallMs = readStallMs;
	}

	/**
	 * Stop {@link #readFile readFile()} before its next window, which is under two seconds
	 * away at the link's rate: it throws {@link ReadCancelledException}. A window is never
	 * cut short, so the burst it asked for is over before anything else is sent, and the
	 * link is left as a finished read leaves it. An instance stays cancelled; read again
	 * with a new one.
	 */
	public void cancel() {
		mCancelled = true;
	}

	public boolean isCancelled() {
		return mCancelled;
	}

	/** INFO answered BAD_CMD: the device has no storage component, and nothing to download */
	public static boolean isStorageAbsent(DockException de) {
		return de.mErrorCodeLowLevel == ErrorCodesWiredProtocol.SHIMMERUART_COMM_ERR_RESPONSE_BAD_CMD;
	}

	// ------------------------------------------------------------- listing

	public Info info() throws DockException {
		return NeuroLynQStorageCodec.parseInfo(mWired.processShimmerGetCommand(
				UART_COMPONENT_AND_PROPERTY.STORAGE.INFO, ErrorCodesWiredProtocol.SHIMMERUART_CMD_ERR_STORAGE_INFO_GET));
	}

	public Session session(int index) throws DockException {
		return NeuroLynQStorageCodec.parseSession(mWired.processShimmerGetCommand(
				UART_COMPONENT_AND_PROPERTY.STORAGE.SESSION, ErrorCodesWiredProtocol.SHIMMERUART_CMD_ERR_STORAGE_SESSION_GET,
				NeuroLynQStorageCodec.sessionArgs(index)));
	}

	/** Every session on the node, oldest first */
	public List<Session> sessions() throws DockException {
		List<Session> out = new ArrayList<Session>();
		int total = info().sessions;
		for (int i = 0; i < total; i++) {
			Session s = session(i);
			out.add(s);
			total = s.total;
		}
		return out;
	}

	public Files files(int handle, int first, int max) throws DockException {
		return NeuroLynQStorageCodec.parseFiles(mWired.processShimmerGetCommand(
				UART_COMPONENT_AND_PROPERTY.STORAGE.FILES, ErrorCodesWiredProtocol.SHIMMERUART_CMD_ERR_STORAGE_FILES_GET,
				NeuroLynQStorageCodec.filesArgs(handle, first, max)));
	}

	/** Every file of session {@code handle}, in order */
	public List<FileEntry> allFiles(int handle) throws DockException {
		List<FileEntry> out = new ArrayList<FileEntry>();
		int first = 0;
		for (;;) {
			Files f = files(handle, first, NeuroLynQStorageCodec.FILES_PER_ANSWER);
			out.addAll(f.files);
			first += f.files.size();
			if (f.files.isEmpty() || first >= f.total) {
				return out;
			}
		}
	}

	// ------------------------------------------------------------- reading

	/**
	 * One READ: what arrived of {@code length} bytes from {@code offset}, in order and
	 * without a gap, and the END the node closed it with. Frames past a gap are counted
	 * but not kept: the next window asks for them again.
	 */
	public ReadWindow readWindow(int handle, int file, long offset, long length) throws DockException {
		long limit = offset + length;
		ReadWindow w = new ReadWindow();
		java.io.ByteArrayOutputStream kept = new java.io.ByteArrayOutputStream();
		CRC32 crc = new CRC32();
		long next = offset;
		boolean gap = false;

		ReadBurst burst = mWired.readBurst(UART_COMPONENT_AND_PROPERTY.STORAGE.READ,
				NeuroLynQStorageCodec.readArgs(handle, file, offset, length),
				UART_COMPONENT_AND_PROPERTY.STORAGE.END.mPropertyByte, mReadStallMs, burstBudgetMs(length),
				ErrorCodesWiredProtocol.SHIMMERUART_CMD_ERR_STORAGE_READ);
		w.badFrames = burst.crcErrors;
		for (UartRxPacketObject p : burst.packets) {
			if (p.mUartPropertyByte == UART_COMPONENT_AND_PROPERTY.STORAGE.END.mPropertyByte) {
				ReadEnd end = NeuroLynQStorageCodec.parseReadEnd(p.getPayload());
				// An earlier READ's, one this host stopped waiting for
				if (end.handle != handle || end.file != file || end.reqOffset != offset) {
					continue;
				}
				w.end = end;
				break;
			}
			ReadFrame f = NeuroLynQStorageCodec.parseReadFrame(p.getPayload());
			if (f.handle != handle || f.file != file || f.offset >= limit) {
				continue;
			}
			if (gap || f.offset > next) {
				gap = true;
				w.pastGap++;
			} else if (f.offset + f.data.length <= next) {
				w.duplicates++;
			} else {
				// Whole, or overlapping bytes already held: keep what is new
				int from = (int) (next - f.offset);
				int to = (int) Math.min(f.data.length, limit - f.offset);
				kept.write(f.data, from, to - from);
				crc.update(f.data, from, to - from);
				next += to - from;
			}
		}
		w.data = kept.toByteArray();
		boolean counted = w.end != null && !gap && w.end.bytesSent == w.data.length;
		w.crcMismatch = counted && w.end.crc32 != crc.getValue();
		w.complete = counted && !w.crcMismatch
				&& (w.end.status == END_STATUS.OK || w.end.status == END_STATUS.EOF);
		return w;
	}

	/**
	 * One file, whole: READ from wherever the last window stopped until {@code size}
	 * bytes have arrived in order. As neurolynq-web-sdk's readFile(): a file is whole once
	 * every byte has arrived, END or not - each frame carries its place and a `$` CRC of
	 * its own - and a window whose END contradicts it, by count or CRC-32, is not kept.
	 */
	public byte[] readFile(int handle, int file, long size, ReadProgressListener progress)
			throws DockException, StorageReadException {
		if (size > Integer.MAX_VALUE) {
			throw new StorageReadException("session " + handle + " file " + file + ": " + size + " bytes", -1);
		}
		byte[] out = new byte[(int) size];
		long have = 0;
		int stalls = 0;
		while (have < size) {
			if (mCancelled) {
				throw new ReadCancelledException("session " + handle + " file " + file + ": cancelled at " + have
						+ " of " + size + " bytes");
			}
			ReadWindow w = readWindow(handle, file, have, Math.min(READ_WINDOW_BYTES, size - have));
			if (w.end != null && w.end.fileSize != size) {
				throw new StorageReadException("session " + handle + " file " + file + ": the node sizes it "
						+ w.end.fileSize + ", not " + size, w.end.status);
			}
			int kept = w.crcMismatch ? 0 : w.data.length;
			System.arraycopy(w.data, 0, out, (int) have, kept);
			have += kept;
			if (kept > 0) {
				stalls = 0;
			} else if (++stalls > MAX_STALLS) {
				int status = (w.end == null) ? -1 : w.end.status;
				String why = w.crcMismatch ? "the CRC-32 at " + have + " disagrees " + (MAX_STALLS + 1) + " times"
						: status == END_STATUS.NAND_ERR ? "the node cannot read the page at " + have
								: "nothing at " + have + " in " + (MAX_STALLS + 1) + " tries";
				throw new StorageReadException("session " + handle + " file " + file + ": " + why, status);
			}
			if (progress != null) {
				progress.onProgress(have, size);
			}
		}
		return out;
	}

	// ---------------------------------------------------- erasing, formatting

	/**
	 * Remove sessions from the node's directory: {@code scope} ONE is session
	 * {@code handle}, UP_TO every session up to and including it, ALL every one. The node
	 * refuses (BAD_ARG) a session open for logging.
	 */
	public void erase(int scope, int handle) throws DockException {
		mWired.processShimmerSetCommand(UART_COMPONENT_AND_PROPERTY.STORAGE.ERASE,
				NeuroLynQStorageCodec.eraseArgs(scope, handle), ErrorCodesWiredProtocol.SHIMMERUART_CMD_ERR_STORAGE_ERASE_SET);
	}

	/** Start a format: ACKed at once, its progress in INFO. It destroys what the node holds. */
	public void format(int mode) throws DockException {
		mWired.processShimmerSetCommand(UART_COMPONENT_AND_PROPERTY.STORAGE.FORMAT,
				NeuroLynQStorageCodec.formatArgs(mode), ErrorCodesWiredProtocol.SHIMMERUART_CMD_ERR_STORAGE_FORMAT_SET);
	}

	/** Long enough for {@code bytes} at 100 kB/s, under a third of the link's rate, and 5 s besides */
	static long burstBudgetMs(long bytes) {
		return 5000 + (bytes + 99) / 100;
	}
}
