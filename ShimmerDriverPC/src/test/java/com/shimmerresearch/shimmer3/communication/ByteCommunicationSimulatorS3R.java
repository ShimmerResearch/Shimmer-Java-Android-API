package com.shimmerresearch.shimmer3.communication;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.verisense.communication.ByteCommunicationListener;

import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;

public class ByteCommunicationSimulatorS3R extends ByteCommunicationSimulatorS3{

	public ByteCommunicationSimulatorS3R(String address) {
		super(address);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	protected void txShimmerVersion() {
		mBuffer.add((byte) 0xff);
		mBuffer.add((byte) 0x25);
		mBuffer.add((byte) 0x0A);
	}
	
	/**
	 * A Shimmer3R's inquiry reply has 11 bytes of settings, with the channel count
	 * at index 9, so the Shimmer3 reply this class inherited never parsed: the
	 * inquiry timed out, and the tests only passed because a status reply part way
	 * through the connect sequence reported the unit ready first (DEV-1275). The
	 * settings match this simulator's InfoMem: the sampling rate, config setup
	 * bytes 0-3 from the first block and 4-6 from the second, then no channels
	 * and a buffer size of 1.
	 */
	@Override
	protected void inquiryResponse() {
		mBuffer.add((byte) 0xff);
		mBuffer.add((byte) 0x02);
		byte[] bytes = UtilShimmer.hexStringToByteArray("80024D9B0E080000000001");
		for (byte b:bytes) {
			mBuffer.add(b);
		}
	}

	@Override
	protected void txFirmwareVersion() {
		mBuffer.add((byte) 0xff);
		mBuffer.add((byte) 0x2f);
		mBuffer.add((byte) 0x03);
		mBuffer.add((byte) 0x00);
		mBuffer.add((byte) 0x00);
		mBuffer.add((byte) 0x00);
		mBuffer.add((byte) 0x00);
		mBuffer.add((byte) 0x01);
	}
	
	//BMP390 
	
	public byte[] getPressureResoTest() {
		byte[] pressureResoResTest = {
			    (byte) 0xE7, (byte) 0x6B, (byte) 0xF0, (byte) 0x4A, (byte) 0xF9, 
			    (byte) 0xAB, (byte) 0x1C, (byte) 0x9B, (byte) 0x15, (byte) 0x06, 
			    (byte) 0x01, (byte) 0xD2, (byte) 0x49, (byte) 0x18, (byte) 0x5F, 
			    (byte) 0x03, (byte) 0xFA, (byte) 0x3A, (byte) 0x0F, (byte) 0x07, 
			    (byte) 0xF5
			};
		
		return pressureResoResTest;
	}
	
	public byte[] getTestDataPacket() {
    	byte[] newPacket = {-68, 19, 112, -53, 7, 9, 8, -66, 4, 24, -5, 7, 2, -16, -70, (byte) 0x00, (byte) 0xCF, (byte) 0x7F ,(byte) 0x00, (byte) 0x17, (byte) 0x64, -128, -70, -75, 80, 4, -87, 40, -128, 127, -1, -1, -3, 80, 71};
    	return newPacket;
	}
	
	public String[] getTestDataType() {
    	String[] dataType = {"u24", "i16", "i16", "i16", "i16", "i16", "i16", "u24", "u24", "u8", "i24r", "i24r", "u8", "i24r", "i24r", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null};
    	return dataType;
	}

}
