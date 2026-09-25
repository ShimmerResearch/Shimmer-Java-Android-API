package com.shimmerresearch.comms.wiredProtocol.neurolynq;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
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
		mWired = new CommsProtocolWiredShimmerViaDock("SIM", "node", mNode);
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
