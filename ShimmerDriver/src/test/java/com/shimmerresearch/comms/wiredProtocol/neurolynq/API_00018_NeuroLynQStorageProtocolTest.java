package com.shimmerresearch.comms.wiredProtocol.neurolynq;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.shimmerresearch.comms.wiredProtocol.CommsProtocolWiredShimmerViaDock;
import com.shimmerresearch.comms.wiredProtocol.DockException;
import com.shimmerresearch.comms.wiredProtocol.ErrorCodesWiredProtocol;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.ERASE_SCOPE;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.FORMAT_MODE;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.FileEntry;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.Info;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.STATE;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.Session;

/**
 * The host's half of a NeuroLynQ node's storage (DEV-1061), over the wired protocol, against
 * a simulated node that sends as the firmware does - a NAND page's frames at a time, a READ's
 * burst on the reader's thread - and loses, corrupts and cuts short what it sends.
 * <p>
 * The rule under test is neurolynq-web-sdk's: a file is whole once every byte has arrived
 * in order, with or without the END that closes the burst, and a window whose END
 * contradicts what arrived is not kept.
 */
public class API_00018_NeuroLynQStorageProtocolTest {

	private SimulatedNeuroLynQNodeSerialPort mNode;
	private CommsProtocolWiredShimmerViaDock mWired;
	private NeuroLynQStorageProtocol mStorage;

	/** Bytes that differ everywhere, so a frame put in the wrong place cannot look right */
	private static byte[] file(int length, long seed) {
		byte[] b = new byte[length];
		new Random(seed).nextBytes(b);
		return b;
	}

	private static final byte[] FILE_A = file(3 * SimulatedNeuroLynQNodeSerialPort.PAGE_PAYLOAD_BYTES + 1234, 1);
	private static final byte[] FILE_B = file(700, 2);
	private static final byte[] FILE_C = file(100000, 3);

	@Before
	public void setUp() {
		mNode = new SimulatedNeuroLynQNodeSerialPort();
		mNode.addSession(5, 7, "trial1_1727000000", "NodeA-000", FILE_A, FILE_B);
		mNode.addSession(6, 8, "trial1_1727000000", "NodeA-001", FILE_C);
		// As a dock names a docked device, which a DockException takes apart
		mWired = new CommsProtocolWiredShimmerViaDock("SIM", "Verisense.01.01", mNode);
		mStorage = new NeuroLynQStorageProtocol(mWired);
		mStorage.setReadStallMs(250);
	}

	@After
	public void tearDown() {
		mNode.shutdown();
	}

	@Test
	public void itListsTheNodesSessionsAndFiles() throws Exception {
		Info info = mStorage.info();
		assertEquals(STATE.READY, info.state);
		assertEquals(2, info.sessions);

		List<Session> sessions = mStorage.sessions();
		assertEquals(2, sessions.size());
		assertEquals(5, sessions.get(0).handle);
		assertEquals("NodeA-001", sessions.get(1).sessionFolder);
		assertEquals(FILE_A.length + FILE_B.length, sessions.get(0).totalBytes);

		List<FileEntry> files = mStorage.allFiles(5);
		assertEquals(2, files.size());
		assertEquals(FILE_A.length, files.get(0).size);
		assertEquals(FILE_B.length, files.get(1).size);
	}

	@Test
	public void aFileIsReadWhole() throws Exception {
		final List<Long> progress = new ArrayList<Long>();
		byte[] got = mStorage.readFile(5, 0, FILE_A.length, (read, size) -> progress.add(read));
		assertArrayEquals(FILE_A, got);
		assertEquals(Long.valueOf(FILE_A.length), progress.get(progress.size() - 1));
		assertArrayEquals(FILE_B, mStorage.readFile(5, 1, FILE_B.length, null));
		assertArrayEquals(FILE_C, mStorage.readFile(6, 0, FILE_C.length, null));
	}

	/** Frames cut anywhere across the port's reads parse as whole frames */
	@Test
	public void framesSplitAcrossReadsParse() throws Exception {
		mNode.chunkBytes = 7;
		assertArrayEquals(FILE_A, mStorage.readFile(5, 0, FILE_A.length, null));
	}

	/** A lost frame is a gap: what came after it is asked for again */
	@Test
	public void aDroppedFrameIsAskedForAgain() throws Exception {
		mNode.dropFrames.add(3);
		mNode.dropFrames.add(40);
		assertArrayEquals(FILE_A, mStorage.readFile(5, 0, FILE_A.length, null));
		assertTrue("more than one READ", readCount() > 1);
	}

	/** A frame whose `$` CRC fails is lost, not believed */
	@Test
	public void aCorruptFrameIsAskedForAgain() throws Exception {
		mNode.corruptFrames.add(5);
		assertArrayEquals(FILE_A, mStorage.readFile(5, 0, FILE_A.length, null));
		assertTrue("more than one READ", readCount() > 1);
	}

	/** Every byte arrived: the file is whole without the END that would have closed it */
	@Test
	public void aLostEndLosesNothing() throws Exception {
		mNode.loseEnds.add(0);
		assertArrayEquals(FILE_B, mStorage.readFile(5, 1, FILE_B.length, null));
	}

	/** An END whose CRC-32 disagrees with what arrived: the window is not kept */
	@Test
	public void aWindowTheEndContradictsIsReadAgain() throws Exception {
		mNode.wrongCrcEnds.add(0);
		assertArrayEquals(FILE_B, mStorage.readFile(5, 1, FILE_B.length, null));
		assertEquals(2, readCount());
	}

	/** A NACK in the middle of a burst ends it, as any NACK ends a request */
	@Test
	public void aNackInABurstThrows() {
		mNode.nackAfterFrames = 4;
		try {
			mStorage.readFile(5, 0, FILE_A.length, null);
			fail("no exception");
		} catch (DockException de) {
			assertEquals(ErrorCodesWiredProtocol.SHIMMERUART_COMM_ERR_RESPONSE_BAD_ARG, de.mErrorCodeLowLevel);
			assertEquals(ErrorCodesWiredProtocol.SHIMMERUART_CMD_ERR_STORAGE_READ, de.mErrorCode);
		} catch (Exception e) {
			fail("threw " + e);
		}
	}

	/** Nothing arriving at all is a failure, after a few windows, not a hang */
	@Test
	public void aNodeThatSendsNothingFailsTheRead() throws Exception {
		for (int i = 0; i < 100; i++) {
			mNode.dropFrames.add(i);
			mNode.loseEnds.add(i);
		}
		try {
			mStorage.readFile(5, 1, FILE_B.length, null);
			fail("no exception");
		} catch (NeuroLynQStorageProtocol.StorageReadException e) {
			assertEquals(-1, e.endStatus);
			assertEquals(NeuroLynQStorageProtocol.MAX_STALLS + 1, readCount());
		}
	}

	/**
	 * A cancel lands between windows: the one already asked for is never cut short. The
	 * next request on the link is answered, whatever of the burst is still arriving: a
	 * response is matched by its component and property, and a READ frame is neither.
	 */
	@Test
	public void aCancelledReadStopsAtTheNextWindow() throws Exception {
		byte[] big = file((int) NeuroLynQStorageProtocol.READ_WINDOW_BYTES + 5000, 4);
		mNode.addSession(7, 9, "trial1_1727000000", "NodeA-002", big);
		final List<Long> progress = new ArrayList<Long>();
		try {
			mStorage.readFile(7, 0, big.length, (read, size) -> {
				progress.add(read);
				mStorage.cancel();
			});
			fail("no exception");
		} catch (NeuroLynQStorageProtocol.ReadCancelledException expected) {
			assertEquals(1, readCount());
			assertEquals(1, progress.size());
			assertTrue(progress.get(0) < big.length);
		}
		assertTrue(mStorage.isCancelled());

		NeuroLynQStorageProtocol again = new NeuroLynQStorageProtocol(mWired);
		assertEquals(3, again.info().sessions);
		assertArrayEquals(FILE_B, again.readFile(5, 1, FILE_B.length, null));
	}

	/**
	 * A whole window's burst in one read - the reader held up while the node sent it - is
	 * parsed frame by frame. The parser called itself once a frame, and a read of a couple
	 * of thousand ran its thread out of stack: the port said nothing more after that.
	 */
	@Test
	public void aReadHoldingAWholeBurstParses() throws Exception {
		byte[] big = file((int) NeuroLynQStorageProtocol.READ_WINDOW_BYTES, 5);
		mNode.addSession(7, 9, "trial1_1727000000", "NodeA-002", big);
		mNode.oneReadPerBurst = true;
		assertArrayEquals(big, mStorage.readFile(7, 0, big.length, null));
		assertEquals(1, readCount());
		assertEquals(3, mStorage.info().sessions);
	}

	/**
	 * The host stopped waiting before the burst ended, and its next request is answered
	 * all the same: the node ends a burst for a new request, with END ABORTED, and what of
	 * the burst was already on its way is not taken for the answer. Unended, this burst
	 * would outlast INFO's 500 ms.
	 */
	@Test
	public void aRequestEndsABurstInProgress() throws Exception {
		byte[] big = file((int) NeuroLynQStorageProtocol.READ_WINDOW_BYTES, 6);
		mNode.addSession(7, 9, "trial1_1727000000", "NodeA-002", big);
		mNode.pageDelayMs = 10;
		mStorage.setReadStallMs(1);
		NeuroLynQStorageProtocol.ReadWindow w = mStorage.readWindow(7, 0, 0, big.length);
		assertNull("the host stopped waiting before the burst ended", w.end);
		assertEquals(3, new NeuroLynQStorageProtocol(mWired).info().sessions);
		assertEquals(Arrays.asList(NeuroLynQStorageCodec.END_STATUS.ABORTED), mNode.endStatuses);
	}

	/** Cancelled before it starts, a read sends nothing */
	@Test
	public void aReadCancelledFirstSendsNothing() throws Exception {
		mStorage.cancel();
		try {
			mStorage.readFile(5, 0, FILE_A.length, null);
			fail("no exception");
		} catch (NeuroLynQStorageProtocol.ReadCancelledException expected) {
			assertEquals(0, readCount());
		}
	}

	@Test
	public void sessionsAreErasedAndTheNodeFormatted() throws Exception {
		mStorage.erase(ERASE_SCOPE.ONE, 5);
		assertEquals(1, mStorage.info().sessions);
		assertEquals(6, mStorage.session(0).handle);
		mStorage.format(FORMAT_MODE.QUICK);
		assertEquals(0, mStorage.info().sessions);
	}

	@Test
	public void erasingASessionTheNodeDoesNotHoldIsRefused() {
		try {
			mStorage.erase(ERASE_SCOPE.ONE, 99);
			fail("no exception");
		} catch (DockException de) {
			assertEquals(ErrorCodesWiredProtocol.SHIMMERUART_COMM_ERR_RESPONSE_BAD_ARG, de.mErrorCodeLowLevel);
		}
	}

	/** A GQ, or a node whose firmware predates storage: INFO answers BAD_CMD */
	@Test
	public void aDeviceWithoutStorageSaysSo() {
		mNode.hasStorage = false;
		try {
			mStorage.info();
			fail("no exception");
		} catch (DockException de) {
			assertTrue(NeuroLynQStorageProtocol.isStorageAbsent(de));
		}
	}

	/** A dock reads a node's MAC around a CLEAR, so the simulated node answers it as a node does */
	@Test
	public void theNodeAnswersItsMacWhenItHasOne() throws Exception {
		mNode.macId = new byte[] { 0x00, 0x06, 0x66, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF };
		assertEquals("000666ABCDEF", mWired.readMacId());
	}

	/**
	 * A NACK back before the request's write has returned is not lost. The driver cleared
	 * its last exception after sending, so a node that answered that fast looked like one
	 * that never answered: a timeout 500 ms on, and a node without storage not seen as one.
	 */
	@Test
	public void aNackFasterThanTheWriteIsNotLost() {
		mNode.hasStorage = false;
		mNode.answerBeforeTheWriteReturns = true;
		try {
			mStorage.info();
			fail("no exception");
		} catch (DockException de) {
			assertEquals(ErrorCodesWiredProtocol.SHIMMERUART_COMM_ERR_RESPONSE_BAD_CMD, de.mErrorCodeLowLevel);
			assertTrue(NeuroLynQStorageProtocol.isStorageAbsent(de));
		}
	}

	@Test
	public void aNackIsNotAbsence() {
		try {
			mStorage.session(9);
			fail("no exception");
		} catch (DockException de) {
			assertFalse(NeuroLynQStorageProtocol.isStorageAbsent(de));
		}
	}

	private int readCount() {
		int n = 0;
		synchronized (mNode.requests) {
			for (String r : mNode.requests) {
				if (r.endsWith("/0C/03")) {
					n++;
				}
			}
		}
		return n;
	}
}
