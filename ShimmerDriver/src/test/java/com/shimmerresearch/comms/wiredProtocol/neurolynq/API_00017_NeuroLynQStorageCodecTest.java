package com.shimmerresearch.comms.wiredProtocol.neurolynq;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.FileEntry;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.Files;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.Info;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.ReadEnd;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.ReadFrame;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.STATE;
import com.shimmerresearch.comms.wiredProtocol.neurolynq.NeuroLynQStorageCodec.Session;

/**
 * The storage component's codec, byte for byte (DEV-1061).
 * <p>
 * The vectors are neurolynq-web-sdk's (tests/neurolynq/storage.test.ts), which are
 * verisense-firmware's own codec test's (Tools/tests/gq_dock/test_gq_dock_storage.c):
 * three implementations held to the same bytes in the same places.
 */
public class API_00017_NeuroLynQStorageCodecTest {

	private static byte[] hex(String s) {
		String[] parts = s.trim().split("\\s+");
		byte[] out = new byte[parts.length];
		for (int i = 0; i < parts.length; i++) {
			out[i] = (byte) Integer.parseInt(parts[i], 16);
		}
		return out;
	}

	private static void assertThrowsArgument(Runnable r) {
		try {
			r.run();
			fail("no exception");
		} catch (IllegalArgumentException expected) {
			// A short answer is refused rather than read past
		}
	}

	private static final byte[] INFO = hex("01 02 04 01 00 10 40 00 00 08 34 12 00 f8 07 00 45 23 01 00"
			+ " 02 00 67 00 cd ab 39 f5 00 00 00 01 02 01 04 03");

	@Test
	public void infoIs36BytesLaidOutAsTheFirmwareLaysItOut() {
		assertEquals(NeuroLynQStorageCodec.INFO_BYTES, INFO.length);
		Info i = NeuroLynQStorageCodec.parseInfo(INFO);
		assertEquals(1, i.layoutVersion);
		assertEquals(STATE.READY, i.state);
		assertEquals(0x04, i.flags);
		assertEquals(1, i.part);
		assertEquals(4096, i.pageBytes);
		assertEquals(64, i.pagesPerBlock);
		assertEquals(2048, i.blocks);
		assertEquals(0x1234, i.badBlocks);
		assertEquals(0x0007f800L, i.capacityKiB);
		assertEquals(0x00012345L, i.usedKiB);
		assertEquals(2, i.sessions);
		assertEquals(103, i.maxSessions);
		assertEquals(0xabcd, i.generation);
		assertEquals(57, i.formatPercent);
		assertEquals(245, i.readFrameMax);
		assertEquals(0x01000000L, i.readMax);
		assertEquals(0x0102, i.droppedRecords);
		assertEquals(0x0304, i.nandErrors);
		assertArrayEquals(INFO, NeuroLynQStorageCodec.buildInfo(i));
		assertThrowsArgument(() -> NeuroLynQStorageCodec.parseInfo(Arrays.copyOf(INFO, 35)));
	}

	private static Session session() {
		Session s = new Session();
		s.total = 2;
		s.index = 1;
		s.handle = 8;
		s.state = 3;
		s.flags = 0x31;
		s.dbSession = 0x0506;
		s.folder = 12;
		s.fileCount = 4;
		s.totalBytes = 0x01020304L;
		s.trialFolder = "trial1_1727000000";
		s.sessionFolder = "Shimmer_ABCD-012";
		return s;
	}

	@Test
	public void sessionIs18FixedBytesAndTwoCountedNames() {
		byte[] bytes = NeuroLynQStorageCodec.buildSession(session());
		assertEquals(53, bytes.length);
		assertArrayEquals(hex("02 00 01 00 08 00 03 31 06 05 0c 00 04 00 04 03 02 01"), Arrays.copyOf(bytes, 18));
		assertEquals(17, bytes[18]);
		assertEquals(16, bytes[36]);
		assertEquals("Shimmer_ABCD-012", new String(bytes, 37, 16, StandardCharsets.ISO_8859_1));
		Session back = NeuroLynQStorageCodec.parseSession(bytes);
		assertEquals(8, back.handle);
		assertEquals(0x0506, back.dbSession);
		assertEquals(0x01020304L, back.totalBytes);
		assertEquals("trial1_1727000000", back.trialFolder);
		assertEquals("Shimmer_ABCD-012", back.sessionFolder);
		assertThrowsArgument(() -> NeuroLynQStorageCodec.parseSession(Arrays.copyOf(bytes, 52)));
		assertThrowsArgument(() -> NeuroLynQStorageCodec.parseSession(Arrays.copyOf(bytes, 30)));
	}

	@Test
	public void filesAreHandleTotalFirstNAndNFileSizePairs() {
		Files f = new Files();
		f.handle = 7;
		f.total = 4;
		f.first = 1;
		f.files = Arrays.asList(new FileEntry(1, 245), new FileEntry(2, 1), new FileEntry(3, 70000));
		byte[] bytes = NeuroLynQStorageCodec.buildFiles(f);
		assertArrayEquals(hex("07 00 04 01 03  01 00 f5 00 00 00  02 00 01 00 00 00  03 00 70 11 01 00"), bytes);
		Files back = NeuroLynQStorageCodec.parseFiles(bytes);
		assertEquals(7, back.handle);
		assertEquals(4, back.total);
		assertEquals(1, back.first);
		assertEquals(3, back.files.size());
		assertEquals(70000L, back.files.get(2).size);
		assertThrowsArgument(() -> NeuroLynQStorageCodec.parseFiles(Arrays.copyOf(bytes, 22)));
	}

	@Test
	public void aReadFrameIsHandleFileOffsetAndItsBytes() {
		byte[] bytes = NeuroLynQStorageCodec.buildReadFrame(new ReadFrame(3, 0, 4080, hex("de ad be ef")));
		assertArrayEquals(hex("03 00 00 00 f0 0f 00 00 de ad be ef"), bytes);
		ReadFrame back = NeuroLynQStorageCodec.parseReadFrame(bytes);
		assertEquals(3, back.handle);
		assertEquals(4080L, back.offset);
		assertArrayEquals(hex("de ad be ef"), back.data);
	}

	/** LEN is a byte: COMP, PROP, the frame's 8 bytes and at most 245 of data */
	@Test
	public void aReadFrameCarriesAtMost245Bytes() {
		assertEquals(253, NeuroLynQStorageCodec.buildReadFrame(new ReadFrame(1, 0, 0, new byte[245])).length);
		assertThrowsArgument(() -> NeuroLynQStorageCodec.buildReadFrame(new ReadFrame(1, 0, 0, new byte[246])));
	}

	@Test
	public void endIs21Bytes() {
		ReadEnd e = new ReadEnd();
		e.handle = 3;
		e.file = 0;
		e.reqOffset = 4000;
		e.bytesSent = 10000;
		e.fileSize = 70000;
		e.crc32 = 0xcbf43926L;
		e.status = 1;
		byte[] bytes = NeuroLynQStorageCodec.buildReadEnd(e);
		assertArrayEquals(hex("03 00 00 00 a0 0f 00 00 10 27 00 00 70 11 01 00 26 39 f4 cb 01"), bytes);
		assertEquals(NeuroLynQStorageCodec.END_BYTES, bytes.length);
		ReadEnd back = NeuroLynQStorageCodec.parseReadEnd(bytes);
		assertEquals(0xcbf43926L, back.crc32);
		assertEquals(70000L, back.fileSize);
		assertThrowsArgument(() -> NeuroLynQStorageCodec.parseReadEnd(Arrays.copyOf(bytes, 20)));
	}

	/** 0xCBF43926 is CRC-32/IEEE's check value, over "123456789": the END's CRC is java.util.zip's */
	@Test
	public void theEndsCrcIsJavasCrc32() {
		java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update("123456789".getBytes(StandardCharsets.US_ASCII));
		assertEquals(0xcbf43926L, crc.getValue());
	}

	@Test
	public void theRequestArgumentsAreTheFirmwaresOrder() {
		assertArrayEquals(hex("02 01"), NeuroLynQStorageCodec.sessionArgs(0x0102));
		assertArrayEquals(hex("07 00 28 28"), NeuroLynQStorageCodec.filesArgs(7, 40, 40));
		byte[] read = NeuroLynQStorageCodec.readArgs(0xfffe, 2, 0x00012345L, 0x01000000L);
		assertArrayEquals(hex("fe ff 02 00 45 23 01 00 00 00 00 01"), read);
		assertArrayEquals(new long[] { 0xfffe, 2, 0x00012345L, 0x01000000L }, NeuroLynQStorageCodec.parseReadArgs(read));
		assertArrayEquals(hex("01 03 02 45 52 41 53"), NeuroLynQStorageCodec.eraseArgs(1, 0x0203));
		assertArrayEquals(hex("01 46 52 4d 54"), NeuroLynQStorageCodec.formatArgs(1));
	}
}
