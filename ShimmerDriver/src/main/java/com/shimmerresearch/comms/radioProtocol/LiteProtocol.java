package com.shimmerresearch.comms.radioProtocol;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.lang3.ArrayUtils;

import com.shimmerresearch.bluetooth.BluetoothProgressReportPerCmd;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.comms.radioProtocol.ShimmerLiteProtocolInstructionSet.LiteProtocolInstructionSet.*;
import com.shimmerresearch.comms.serialPortInterface.InterfaceSerialPortHal;
import com.shimmerresearch.driverUtilities.ExpansionBoardDetails;
import com.shimmerresearch.driverUtilities.ShimmerBattStatusDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerObject;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.FW_ID;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.sensors.SensorSTC3100Details;

public class LiteProtocol extends AbstractCommsProtocol{

	//Using this as a staging post to add new BT commands before the ProtoBuf auto-code generation is run.
	public class Temp{
		public class InstructionsSet{
//			 public static final int SET_I2C_BATT_STATUS_FREQ_COMMAND_VALUE = 0x9C;
		}
		public class InstructionsResponse{
//			 public static final int RSP_I2C_BATT_STATUS_COMMAND_VALUE = 0x9D;
//			public static final int BMP280_CALIBRATION_COEFFICIENTS_RESPONSE_VALUE = 0x9F;
//			public static final int UNIQUE_SERIAL_RESPONSE_VALUE = 0x3D;
		}
		public class InstructionsGet{
//			public static final int GET_I2C_BATT_STATUS_COMMAND_VALUE = 0x9E;
//			public static final int GET_BMP280_CALIBRATION_COEFFICIENTS_COMMAND = 0xA0;
//			public static final int GET_UNIQUE_SERIAL_COMMAND_VALUE = 0x3E;
		}
	}

	protected List<byte []> mListofInstructions = new  ArrayList<byte[]>();
	protected int mCurrentCommand;
	
	transient protected IOThread mIOThread;
	transient ByteArrayOutputStream mByteArrayOutputStream = new ByteArrayOutputStream();
	private boolean mInstructionStackLock = false;
	
	protected boolean mWaitForAck = false;                                          // This indicates whether the device is waiting for an acknowledge packet from the Shimmer Device  
	protected boolean mWaitForResponse = false; 									// This indicates whether the device is waiting for a response packet from the Shimmer Device 
	protected boolean mTransactionCompleted = true;									// Variable is used to ensure a command has finished execution prior to executing the next command (see initialize())
	private final int ACK_TIMER_DURATION = 10; 									// Duration to wait for an ack packet (seconds)
	protected boolean mDummy = false;
	
	//Used in the ShimmerBluetooth processing thread but has not been ported to the LiteProtocol yet
//	ArrayBlockingQueue<RawBytePacketWithPCTimeStamp> mABQPacketByeArray = new ArrayBlockingQueue<RawBytePacketWithPCTimeStamp>(10000);
	
	List<Long> mListofPCTimeStamps = new ArrayList<Long>();
	private int mNumberofTXRetriesCount = 1;
	private final static int NUMBER_OF_TX_RETRIES_LIMIT = 0;
	protected Stack<Byte> byteStack = new Stack<Byte>();
	protected boolean mOperationUnderway = false;

	/**
	 * LogAndStream will try to recreate the SD config. file for each block of
	 * InfoMem that is written - need to give it time to do so.
	 */
	public static final int DELAY_BETWEEN_CONFIG_BYTE_WRITES = 100;
	/** Delay to allow LogAndStream to create SD config. file and reinitialise */
	public static final int DELAY_AFTER_CONFIG_BYTE_WRITE = 500;

	//startregion --------- TIMERS ---------
	public static final int TIMER_READ_STATUS_PERIOD = 5000;
	public static final int TIMER_READ_BATT_STATUS_PERIOD = 60000; // Batt status is updated every 1min
	public static final int TIMER_CHECK_ALIVE_PERIOD = 2000;
	public static final int TIMER_CONNECTING_TIMEOUT = 20000;

	transient protected Timer mTimerCheckForAckOrResp;								// Timer variable used when waiting for an ack or response packet
	transient protected Timer mTimerCheckAlive;
	transient protected Timer mTimerReadStatus;
	transient protected Timer mTimerReadBattStatus;								// 
	
	private int mCountDeadConnection = 0;
	private boolean mCheckIfConnectionisAlive = true;
	//endregion --------- TIMERS ---------

	//Local copy of 
	public int mHardwareVersion;
	private ShimmerVerObject mShimmerVerObject;

	public UtilShimmer mUtilShimmer = new UtilShimmer(getClass().getSimpleName(), true);

	//TODO mUseShimmerBluetoothApproach is only a temporary boolean for development -> default to true is working out best
	private boolean mUseShimmerBluetoothApproach = true;
	private boolean mUseLegacyDelayToDelayForResponse = false;
	

	//TODO Should not be here?
	public String mComPort = "";
	public String mMyBluetoothAddress = "";
	private int mTempChipID;

	public String mConnectionHandle = "";

//	public long mPacketReceivedCount = 0; 	//Used by ShimmerGQ
//	public long mPacketExpectedCount = 0; 	//Used by ShimmerGQ
//	protected long mPacketLossCount = 0;		//Used by ShimmerBluetooth
//	protected double mPacketReceptionRate = 100;
//	protected double mPacketReceptionRateCurrent = 100;

	//States -> Should not be here? these need to be synced with the same booleans in the parent device
	public boolean mIsSensing = false;
	public boolean mIsSDLogging = false;											// This is used to monitor whether the device is in sd log mode
	public boolean mIsStreaming = false;											// This is used to monitor whether the device is in streaming mode
	public boolean mIsInitialised = false;
	public boolean mIsDocked = false;
	public boolean mHaveAttemptedToReadConfig = false;

	//TODO Should not be here
	protected String mDirectoryName;
	protected int numBytesToReadFromExpBoard=0;
	private static final int MAX_CALIB_DUMP_MAX = 4096;
	/* The generated LiteProtocolInstructionSet has no NACK entry, so the value
	 * is declared here rather than read from InstructionsSet. Adding it to
	 * grpcprotosrc/src/LiteProtocolInstructionSet.proto and regenerating would
	 * be tidier, but that rewrites a large generated file committed in two
	 * places for the sake of one constant. */
	private static final int NACK_COMMAND_PROCESSED_VALUE = 0xFE;


	/**
	 * Constructor
	 * @param connectionHandle 
	 */
	public LiteProtocol(String connectionHandle){
		super();
		mConnectionHandle = connectionHandle;
		mUtilShimmer.setParentClassName(getClass().getSimpleName() + "-" + mConnectionHandle);
	}
	
	/** Constructor
	 * @param commsInterface
	 */
	public LiteProtocol(InterfaceSerialPortHal commsInterface) {
		super(commsInterface);
	}
	
	
	protected void writeInstruction(int commandValue) {
		writeInstruction(new byte[]{(byte) (commandValue&0xFF)});
	}
	
	@Override
	public void writeInstruction(byte[] instruction){
		getListofInstructions().add(instruction);
	};
	
	
	/**
	 * @return the mFirmwareIdentifier
	 */
	public int getFirmwareIdentifier() {
		if(mShimmerVerObject!=null){
			return mShimmerVerObject.getFirmwareIdentifier();
		}
		return FW_ID.UNKNOWN;
	}

	public int getShimmerVersion() {
		return mHardwareVersion;
	}

	/**
	 * @return the mInstructionStackLock
	 */
	public boolean isInstructionStackLock() {
		return mInstructionStackLock;
	}
	
	/**
	 * @return the mListofInstructions
	 */
	public List<byte []> getListofInstructions() {
		return mListofInstructions;
	}
	
	/**
	 * @param state the mInstructionStackLock to set
	 */
	@Override
	public void setInstructionStackLock(boolean state) {
		this.mInstructionStackLock = state;
	}
	
	/**this is to clear the buffer
	 * @throws ShimmerException 
	 * 
	 */
	private void clearSerialBuffer() throws ShimmerException {
		/* JC: not working well on android
		if(bytesAvailableToBeRead()){
			byte[] buffer = readBytes(availableBytes());
			printLogDataForDebugging("Discarding:\t\t" + UtilShimmer.bytesToHexStringWithSpacesFormatted(buffer));
		}
		*/
		
		while (availableBytes()!=0){
			if (bytesAvailableToBeRead()){
				int available = availableBytes();
				byte[] tb=readBytes(available);
				String msg = "Clearing Serial Buffer : " + UtilShimmer.bytesToHexStringWithSpacesFormatted(tb);
				printLogDataForDebugging(msg);
			}
		}		
	}
	
//	public double getPacketReceptionRate(){
//		return mPacketReceptionRate;
//	}

	@Override
	public void initialize() {
		mIamAlive = false;
		getListofInstructions().clear();
		mFirstTime=true;

		startIoThread();
		
		stopTimerReadStatus();
		stopTimerCheckForAckOrResp();
		setInstructionStackLock(false);
		
		dummyreadSamplingRate(); // it actually acts to clear the write buffer
		readShimmerVersionNew();
	}

	@Override
	public void stopProtocol() {
		stopAllTimers();
		
		stopIoThread();
		
		setIsStreaming(false);
		setIsInitialised(false);
	}

	private void startIoThread() {
		stopIoThread();

		mIOThread = new IOThread();
		mIOThread.setName(getClass().getSimpleName()+"-"+mConnectionHandle);
		mIOThread.start();
	}

	private void stopIoThread() {
		if(mIOThread!=null){
			mIOThread.stop=true;
			mIOThread = null;
		}
	}

	
	public class IOThread extends Thread {
		byte[] byteBuffer = {0};
		public boolean stop = false;
		
		public synchronized void run() {
			while (!stop) {
				try {
					// Process Instruction on stack. is an instruction running? if not proceed
					if(!isInstructionStackLock()){
						processNextInstruction();
					}
				
					if(isStreaming()){
						processWhileStreaming();
					}
					else if(bytesAvailableToBeRead()){
						if(mWaitForAck) {
							processNotStreamingWaitForAck();
						} 
						else if(mWaitForResponse) {
							processNotStreamingWaitForResp();
						} 
						
						processBytesAvailableAndInstreamSupported();
					}
				} catch (ShimmerException dE) {
//					stop=true;
					
					killConnection(dE);
//					e.printStackTrace();
					//TODO send event up the ladder
				}
			} 
		} 
		
		private void processNextInstruction() throws ShimmerException {
			// check instruction stack, are there any other instructions left to be executed?
			if(!getListofInstructions().isEmpty()) {
				if(getListofInstructions().get(0)==null) {
					getListofInstructions().remove(0);
					printLogDataForDebugging("Null Removed");
				}
			}
			
			if(!getListofInstructions().isEmpty()){
				if(getListofInstructions().get(0)!=null) {
					byte[] insBytes = (byte[]) getListofInstructions().get(0);
					mCurrentCommand=((int)insBytes[0])&0xFF;
					setInstructionStackLock(true);
					mWaitForAck=true;
					
					if(!isStreaming()){
						clearSerialBuffer();
					}
					//Special cases
					if(mCurrentCommand==InstructionsSet.SET_RWC_COMMAND_VALUE){
						// for Real-world time -> grab PC time just before
						// writing to Shimmer
						byte[] rtcTimeArray = UtilShimmer.convertMilliSecondsToShimmerRtcDataBytesLSB(System.currentTimeMillis());
						System.arraycopy(rtcTimeArray, 0, insBytes, 1, 8);
					}
					
					//TODO: are the two stops needed here? better to wait for ack from Shimmer
					if(mCurrentCommand==InstructionsSet.STOP_STREAMING_COMMAND_VALUE
							|| mCurrentCommand==InstructionsSet.STOP_SDBT_COMMAND_VALUE){
						//DO NOTHING
					} 
					else {
						// Overwritten for commands that aren't supported
						// for older versions of Shimmer
						if(mCurrentCommand==InstructionsSet.SET_SENSORS_COMMAND_VALUE
								&& getShimmerVersion()==HW_ID.SHIMMER_2R){
							startTimerCheckForAckOrResp(ACK_TIMER_DURATION+8);
						}
						else if((mCurrentCommand==InstructionsGet.GET_FW_VERSION_COMMAND_VALUE)
								||(mCurrentCommand==InstructionsGet.GET_SAMPLING_RATE_COMMAND_VALUE)
								||(mCurrentCommand==InstructionsGet.GET_SHIMMER_VERSION_COMMAND_NEW_VALUE)){
							startTimerCheckForAckOrResp(ACK_TIMER_DURATION);
						}
						else {
							if(isStreaming()){
								startTimerCheckForAckOrResp(ACK_TIMER_DURATION);
							}
							else {
								startTimerCheckForAckOrResp(ACK_TIMER_DURATION+3);
							}
						}
					}
					
					threadSleep((int)((Math.random()+.1)*100.0));
					writeBytes(insBytes);
					printLogDataForDebugging("Command Transmitted: \t\t\t" + btCommandToString(mCurrentCommand) + " " + UtilShimmer.bytesToHexStringWithSpacesFormatted(insBytes));

					//TODO: are the two stops needed here? better to wait for ack from Shimmer
					if(mCurrentCommand==InstructionsSet.STOP_STREAMING_COMMAND_VALUE
							|| mCurrentCommand==InstructionsSet.STOP_SDBT_COMMAND_VALUE){
						setIsStreaming(false);
						if (mCurrentCommand==InstructionsSet.STOP_SDBT_COMMAND_VALUE){
							setIsSDLogging(false);
						}
						
						if(!mUseShimmerBluetoothApproach){
						//TODO 2016-07-06 MN removed to make consistent with ShimmerBluetooth implementation 
//							eventAckReceived((byte[]) getListofInstructions().get(0)); //DUMMY
//							hasStopStreaming();
						}
						
						getListofInstructions().removeAll(Collections.singleton(null));
					} 
					else {
						/*
						// Overwritten for commands that aren't supported
						// for older versions of Shimmer
						if((mCurrentCommand==GET_FW_VERSION_COMMAND)
								||(mCurrentCommand==GET_SAMPLING_RATE_COMMAND)
								||(mCurrentCommand==GET_SHIMMER_VERSION_COMMAND_NEW)){
							startTimerCheckForAckOrResp(ACK_TIMER_DURATION);
						}
						else {
							if(mIsStreaming){
								startTimerCheckForAckOrResp(ACK_TIMER_DURATION);
							}
							else {
								startTimerCheckForAckOrResp(ACK_TIMER_DURATION+3);
							}
						}*/
					}
					
					
					mTransactionCompleted=false;
				}
			} 
			else {
				if (!isStreaming() && !bytesAvailableToBeRead()){
					threadSleep(50);
				}
			}
		}

		private void processWhileStreaming() throws ShimmerException {
			byteBuffer = readBytes(1);
			if(byteBuffer!=null){
				mByteArrayOutputStream.write(byteBuffer[0]);
				//Everytime a byte is received the timestamp is taken
				mListofPCTimeStamps.add(System.currentTimeMillis());
			} 
			else {
				printLogDataForDebugging("readbyte null");
			}

			//If there is a full packet and the subsequent sequence number of following packet
			if(mByteArrayOutputStream.size()>=mPacketSize+2){ // +2 because there are two acks
				processPacket();
			} 
		}
		
		private void processPacket() {
			mIamAlive = true;
			byte[] bufferTemp = mByteArrayOutputStream.toByteArray();
			
			//Data packet followed by another data packet
			if(bufferTemp[0]==InstructionsSet.DATA_PACKET_VALUE 
					&& bufferTemp[mPacketSize+1]==InstructionsSet.DATA_PACKET_VALUE){
				//Handle the data packet
				processDataPacket(bufferTemp);
				clearSingleDataPacketFromBuffers(bufferTemp, mPacketSize+1);
			} 
			
			//Data packet followed by an ACK (suggesting an ACK in response to a SET BT command or else a BT response command)
			else if(bufferTemp[0]==InstructionsSet.DATA_PACKET_VALUE 
					&& bufferTemp[mPacketSize+1]==InstructionsSet.ACK_COMMAND_PROCESSED_VALUE){
				if(mByteArrayOutputStream.size()>mPacketSize+2){
					
					if(bufferTemp[mPacketSize+2]==InstructionsSet.DATA_PACKET_VALUE){
						//Firstly handle the data packet
						processDataPacket(bufferTemp);
						clearSingleDataPacketFromBuffers(bufferTemp, mPacketSize+2);
						
						//Then handle the ACK from the last SET command
						if(isKnownSetCommand(mCurrentCommand)){
							stopTimerCheckForAckOrResp(); //cancel the ack timer
							mWaitForAck=false;
							
							processAckFromSetCommandAndInstructions(((int)mCurrentCommand)&0xFF);
							
							mTransactionCompleted = true;
							setInstructionStackLock(false);
						}
						else{
							printLogDataForDebugging("Unknown SET command = " + mCurrentCommand);	
						}
						printLogDataForDebugging("Ack Received for Command: \t\t\t\t" + btCommandToString(mCurrentCommand));
					}
					
					//this is for LogAndStream support, command is transmitted and ack received
					else if(((getHardwareVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM)
							||(getHardwareVersion()==HW_ID.SHIMMER_4_SDK && getFirmwareIdentifier()==FW_ID.SHIMMER4_SDK_STOCK))
							&& bufferTemp[mPacketSize+2]==((byte)(InstructionsResponse.INSTREAM_CMD_RESPONSE_VALUE))){ 
						printLogDataForDebugging("COMMAND TXed and ACK RECEIVED IN STREAM");
						printLogDataForDebugging("INS CMD RESP");

						//Firstly handle the in-stream response
						stopTimerCheckForAckOrResp(); //cancel the ack timer
						mWaitForResponse=false;
						mWaitForAck=false;

						processInstreamResponse();

						// Need to remove here because it is an
						// in-stream response while streaming so not
						// handled elsewhere
						if(getListofInstructions().size()>0){
							getListofInstructions().remove(0);
						}
						
						mTransactionCompleted=true;
						setInstructionStackLock(false);

						//Then process the Data packet
						processDataPacket(bufferTemp);
						clearBuffers();
					} 
					else {
						printLogDataForDebugging("Unknown parsing error while streaming");
					}
				} 
				if(mByteArrayOutputStream.size()>mPacketSize+2){
					printLogDataForDebugging("Unknown packet error (check with JC):\tExpected: " + (mPacketSize+2) + "bytes but buffer contains " + mByteArrayOutputStream.size() + "bytes");
					discardFirstBufferByte(); //throw the first byte away
				}
				
			} 
			//TODO: ACK in bufferTemp[0] not handled
			//else if
			else {
				printLogDataForDebugging("Packet syncing problem, could not lock on to data steam. \nExpected: " + (mPacketSize+2) + "bytes. Buffer contains " + mByteArrayOutputStream.size() + "bytes\n" + UtilShimmer.bytesToHexStringWithSpacesFormatted(mByteArrayOutputStream.toByteArray()));
				discardFirstBufferByte(); //throw the first byte away
			}
		}

		/** Process ACK from a GET or SET command while not streaming */ 
		private void processNotStreamingWaitForAck() throws ShimmerException {
			//JC TEST:: IMPORTANT TO REMOVE // This is to simulate packet loss 
			/*
			if (Math.random()>0.9 && mIsInitialised==true){
				if(bytesAvailableToBeRead() 
						&& mCurrentCommand!=TEST_CONNECTION_COMMAND	
						&& mCurrentCommand!=GET_STATUS_COMMAND	
						&& mCurrentCommand!= GET_VBATT_COMMAND 
						&& mCurrentCommand!=START_STREAMING_COMMAND
						&& mCurrentCommand!=STOP_STREAMING_COMMAND 
						&& mCurrentCommand!=SET_RWC_COMMAND 
						&& mCurrentCommand!=GET_RWC_COMMAND){
					tb=readBytes(1);
					tb=null;
				}
			}
			*/
			//JC TEST:: IMPORTANT TO REMOVE
			
			if(bytesAvailableToBeRead()){
				byteBuffer=readBytes(1);
				mNumberofTXRetriesCount = 0;
				mIamAlive = true;
				
				//TODO: ACK is probably now working for STOP_STREAMING_COMMAND so merge in with others?
				if(mCurrentCommand==InstructionsSet.STOP_STREAMING_COMMAND_VALUE 
						|| mCurrentCommand==InstructionsSet.STOP_SDBT_COMMAND_VALUE) { //due to not receiving the ack from stop streaming command we will skip looking for it.
					stopTimerCheckForAckOrResp();
					setIsStreaming(false);
					mTransactionCompleted=true;
					mWaitForAck=false;
					
					delayForBtResponse(200); // Wait to ensure the packet has been fully received
					byteStack.clear();

					clearSerialBuffer();
					
					hasStopStreaming();					
					getListofInstructions().remove(0);
					getListofInstructions().removeAll(Collections.singleton(null));
					if (mCurrentCommand==InstructionsSet.STOP_SDBT_COMMAND_VALUE){
						eventLogAndStreamStatusChanged(mCurrentCommand);	
					}
					setInstructionStackLock(false);
				}
//				//TODO: ACK is probably now working for STOP_STREAMING_COMMAND so merge in with others?
//				if(mCurrentCommand==STOP_SDBT_COMMAND) { //due to not receiving the ack from stop streaming command we will skip looking for it.
//					stopTimerCheckForAckOrResp();
//					mIsStreaming=false;
//					mIsSDLogging=false;
//					mTransactionCompleted=true;
//					mWaitForAck=false;
//					
//					delayForBtResponse(200); // Wait to ensure the packet has been fully received
//					byteStack.clear();
//
//					clearSerialBuffer();
//					
//					hasStopStreaming();					
//					getListofInstructions().remove(0);
//					getListofInstructions().removeAll(Collections.singleton(null));
//					setInstructionStackLock(false);
//				}
				
				if(byteBuffer != null){
					if((((int)byteBuffer[0])&0xFF)==InstructionsSet.ACK_COMMAND_PROCESSED_VALUE) {

						mWaitForAck=false;
						printLogDataForDebugging("Ack Received for Command: \t\t\t" + btCommandToString(mCurrentCommand));

						// Send status report if needed by the
						// application and is not one of the below
						// commands that are triggered by timers
						if(mCurrentCommand!=InstructionsGet.GET_STATUS_COMMAND_VALUE 
								&& mCurrentCommand!=InstructionsSet.TEST_CONNECTION_COMMAND_VALUE 
								&& mCurrentCommand!=InstructionsSet.SET_BLINK_LED_VALUE
								//&& mCurrentCommand!= GET_VBATT_COMMAND
								&& mOperationUnderway){
							sendProgressReport(new BluetoothProgressReportPerCmd(mCurrentCommand, getListofInstructions().size(), mMyBluetoothAddress, mComPort));
						}
						
						// Process if currentCommand is a SET command
						if(isKnownSetCommand(mCurrentCommand)){
							stopTimerCheckForAckOrResp(); //cancel the ack timer
							
							//TODO 2016-07-06 MN removed to make consistent with ShimmerBluetooth implementation
							if(!mUseShimmerBluetoothApproach){
//								byte[] insBytes = getListofInstructions().get(0);
//								eventAckReceived(insBytes);
							}
							
							processAckFromSetCommandAndInstructions(((int)mCurrentCommand)&0xFF);
							mTransactionCompleted = true;
							setInstructionStackLock(false);
						}
						
						// Process if currentCommand is a GET command
						else if(isKnownGetCommand(mCurrentCommand)){
							//Special cases
							processSpecialGetCmdsAfterAck(((int)mCurrentCommand)&0xFF);
							mWaitForResponse=true;
							getListofInstructions().remove(0);
						}
						
					}
					/* The Shimmer refused the command. Without this branch the NACK
					 * was discarded and the transaction stalled until the ACK timer
					 * expired, which is treated as a lost connection. */
					else if((((int)byteBuffer[0])&0xFF)==NACK_COMMAND_PROCESSED_VALUE) {
						processNackFromCommand();
					}
				}
			}
		}

		/** Process RESPONSE while not streaming */ 
		private void processNotStreamingWaitForResp() throws ShimmerException {
			//Discard first read
			if(mFirstTime){
//				printLogDataForDebugging("First Time read");
				clearSerialBuffer();
				
//				while (availableBytes()!=0){
//					int available = availableBytes();
//					if (bytesAvailableToBeRead()){
//						byteBuffer=readBytes(1);
//						String msg = "First Time : " + UtilShimmer.bytesToHexStringWithSpacesFormatted(byteBuffer);
//						printLogDataForDebugging(msg);
//					}
//				}
				
				//TODO: Check with JC on the below!!! Or just clear seriable buffer and remove need for mFirstTime
				//Below added from original implementation -> doesn't wait for timeout on first command
				//TODO: if keeping the below, remove "mFirstTime = false" from the TimerCheckForAckOrResp
				stopTimerCheckForAckOrResp(); //cancel the ack timer
				mWaitForResponse=false;
				mTransactionCompleted=true;
				setInstructionStackLock(false);
				mFirstTime = false;
			} 
			
			else if(bytesAvailableToBeRead()){
				byteBuffer=readBytes(1);
				mIamAlive = true;
				
				//Check to see whether it is a response byte
				if(isKnownResponseByte(byteBuffer[0])){
					byte responseCommand = byteBuffer[0];
					
					if(mUseShimmerBluetoothApproach){
						processResponseCommand(((int)responseCommand)&0xFF);
					}
					else {
//						processResponseCommand(responseCommand, byteBuffer);
					}
					//JD: only stop timer after process because there are readbyte opeartions in the processresponsecommand
					stopTimerCheckForAckOrResp(); //cancel the ack timer
					mWaitForResponse=false;
					mTransactionCompleted=true;
					setInstructionStackLock(false);
					printLogDataForDebugging("Response Received:\t\t\t\t" + btCommandToString(responseCommand));
					
					// Special case for FW_VERSION_RESPONSE because it
					// needs to initialize the Shimmer after releasing
					// the setInstructionStackLock
					if(byteBuffer[0]==InstructionsResponse.FW_VERSION_RESPONSE_VALUE){
//						processFirmwareVerResponse();
						eventResponseReceived(byteBuffer[0], null);
					}
				}
			}
		}

		/** Process LogAndStream INSTREAM_CMD_RESPONSE while not streaming */ 
		private void processBytesAvailableAndInstreamSupported() throws ShimmerException {
			if(((getHardwareVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM) 
					|| (getHardwareVersion()==HW_ID.SHIMMER_4_SDK && getFirmwareIdentifier()==FW_ID.SHIMMER4_SDK_STOCK))
					&& !mWaitForAck 
					&& !mWaitForResponse 
					&& bytesAvailableToBeRead()) {
				
				byteBuffer=readBytes(1);
				if(byteBuffer != null){
					if((byteBuffer[0]&0xFF)==InstructionsSet.ACK_COMMAND_PROCESSED_VALUE) {
						printLogDataForDebugging("ACK RECEIVED , Connected State!!");
						byteBuffer = readBytes(1);
						if (byteBuffer!=null){ //an android fix. not fully investigated (JC)
							if((byteBuffer[0]&0xFF)==InstructionsSet.ACK_COMMAND_PROCESSED_VALUE){
								byteBuffer = readBytes(1);
							}
							//2016-11-13 Old code
//							if((byteBuffer[0]&0xFF)==InstructionsResponse.INSTREAM_CMD_RESPONSE_VALUE){
//								processInstreamResponse();
//							}
						}
					}
					
					//2016-11-13 New code
					if((byteBuffer[0]&0xFF)==InstructionsResponse.INSTREAM_CMD_RESPONSE_VALUE){
						processInstreamResponse();
					}
				}
				
				
				clearSerialBuffer();
			}
		}

	}

//	@Deprecated
//	private void processResponseCommand(byte responseCommand, byte[] tb) throws DeviceException {
//		// response have to read bytes and return the values
//		 int lengthOfResponse = (int)InstructionsResponse.valueOf(responseCommand&0xff).getValueDescriptor().getOptions().getField(LiteProtocolInstructionSet.getDescriptor().findFieldByName("response_size"));
//		if (lengthOfResponse ==-1){
//			lengthOfResponse = (int)(readBytes(1)[0] & 0xFF);
//		} 
//		byte[] response = readBytes(lengthOfResponse);
//		response = ArrayUtils.addAll(tb,response);
//		eventNewResponse(response);
//	}
	
	//TODO implement the remaining (i.e., commented out code sections) from ShimmerBluetooth
	/**
	 * @param responseCommand
	 */
	protected void processResponseCommand(int responseCommand) {
		byte[] length = null;
		byte[] rxBuf = null;
		
		try{
			if(responseCommand==InstructionsResponse.INQUIRY_RESPONSE_VALUE){
				delayForBtResponse(500); // Wait to ensure the packet has been fully received
				List<Byte> buffer = new  ArrayList<Byte>();
				//JC TEST:: IMPORTANT TO REMOVE // This is to simulate packet loss
				/*
				if (Math.random()>0.5 && mIsInitialised==true){
					if(bytesAvailableToBeRead() 
							&& mCurrentCommand!=TEST_CONNECTION_COMMAND	
							&& mCurrentCommand!=GET_STATUS_COMMAND	
							&& mCurrentCommand!= GET_VBATT_COMMAND 
							&& mCurrentCommand!=START_STREAMING_COMMAND
							&& mCurrentCommand!=STOP_STREAMING_COMMAND 
							&& mCurrentCommand!=SET_RWC_COMMAND 
							&& mCurrentCommand!=GET_RWC_COMMAND){
						readByte();
					}
				}
				*/
				//JC TEST:: IMPORTANT TO REMOVE // This is to simulate packet loss
				
				//Shimmer3
				int lengthSettings = 8;// get Sampling rate, accel range, config setup byte0, num chans and buffer size
				int lengthChannels = 6;// read each channel type for the num channels
				if(mShimmerVerObject.isShimmerGen2()) {
					lengthSettings = 5;
					lengthChannels = 3;
				}
				else if(mShimmerVerObject.getHardwareVersion()==HW_ID.ARDUINO) {
					lengthSettings = 10;
					lengthChannels = 0;
				}
           	// get Sampling rate, accel range, config setup byte0, num chans and buffer size
				for (int i = 0; i < lengthSettings; i++) {
                   buffer.add(readByte());
               }
               // read each channel type for the num channels
               for (int i = 0; i < (int)buffer.get(lengthChannels); i++) {
               	buffer.add(readByte());
               }

               rxBuf = new byte[buffer.size()];
				for (int i = 0; i < rxBuf.length; i++) {
					rxBuf[i] = (byte) buffer.get(i);
				}
					
				printLogDataForDebugging("Inquiry Response Received: " + UtilShimmer.bytesToHexStringWithSpacesFormatted(rxBuf));
				
//				interpretInqResponse(rxBuf);
//				prepareAllAfterConfigRead();
//				inquiryDone();
				eventResponseReceived(responseCommand, rxBuf);
			}
			else if(responseCommand==InstructionsResponse.SAMPLING_RATE_RESPONSE_VALUE){
				if(!mIsStreaming) {
//					if(getHardwareVersion()==HW_ID.SHIMMER_2R || getHardwareVersion()==HW_ID.SHIMMER_2){    
//						byte[] bufferSR = readBytes(1);
//						if(mCurrentCommand==GET_SAMPLING_RATE_COMMAND) { // this is a double check, not necessary 
//							double val=(double)(bufferSR[0] & (byte) ACK_COMMAND_PROCESSED);
//							setSamplingRateShimmer(1024/val);
//						}
//					} 
//					else if(getHardwareVersion()==HW_ID.SHIMMER_3){
//						byte[] bufferSR = readBytes(2); //read the sampling rate
//			//TODO get clock freq from ShimmerDevice
//						setSamplingRateShimmer(32768.0/(double)((int)(bufferSR[0] & 0xFF) + ((int)(bufferSR[1] & 0xFF) << 8)));
//					}
//					printLogDataForDebugging("Sampling Rate Response Received: " + Double.toString(getSamplingRateShimmer()));
					
					byte[] bufferSR = null;
					if(mShimmerVerObject.isShimmerGen2()) {
						bufferSR = readBytes(1);
					} else {
						bufferSR = readBytes(2);
					}
					eventResponseReceived(responseCommand, bufferSR);
				}

			}
			else if(responseCommand==InstructionsResponse.FW_VERSION_RESPONSE_VALUE){
				delayForBtResponse(200); // Wait to ensure the packet has been fully received
				rxBuf = readBytes(6);
				int firmwareIdentifier=(int)((rxBuf[1]&0xFF)<<8)+(int)(rxBuf[0]&0xFF);
				int firmwareVersionMajor = (int)((rxBuf[3]&0xFF)<<8)+(int)(rxBuf[2]&0xFF);
				int firmwareVersionMinor = ((int)((rxBuf[4]&0xFF)));
				int firmwareVersionInternal=(int)(rxBuf[5]&0xFF);
//				ShimmerVerObject shimmerVerObject = new ShimmerVerObject(getHardwareVersion(), firmwareIdentifier, firmwareVersionMajor, firmwareVersionMinor, firmwareVersionInternal);
//				setShimmerVersionInfoAndCreateSensorMap(shimmerVerObject);

				ShimmerVerObject shimmerVerObject = new ShimmerVerObject(getHardwareVersion(), firmwareIdentifier, firmwareVersionMajor, firmwareVersionMinor, firmwareVersionInternal);
				this.mShimmerVerObject = shimmerVerObject; // store a local copy
				
				printLogDataForDebugging("FW Version Response Received. FW Code: " + shimmerVerObject.getFirmwareVersionCode());
				printLogDataForDebugging("FW Version Response Received: " + shimmerVerObject.getFirmwareVersionParsed());

				eventResponseReceived(responseCommand, shimmerVerObject);
			}

//			else if(responseCommand==InstructionsResponse.ALL_CALIBRATION_RESPONSE_VALUE){
//				if(getHardwareVersion()==HW_ID.SHIMMER_3){
//					processAccelCalReadBytes();
//					processGyroCalReadBytes();
//					processMagCalReadBytes();
//					processLsm303dlhcAccelCalReadBytes();
//				} 
//				else { //Shimmer2R etc.
//					processAccelCalReadBytes();
//					processGyroCalReadBytes();
//					processMagCalReadBytes();
//					processShimmer2EmgCalReadBytes();
//					processShimmer2EcgCalReadBytes();
//				}
//			}
//			else if(responseCommand==InstructionsResponse.ACCEL_CALIBRATION_RESPONSE_VALUE){
//				processAccelCalReadBytes();
//			}
//			else if(responseCommand==InstructionsResponse.GYRO_CALIBRATION_RESPONSE_VALUE){
//				processGyroCalReadBytes();
//			}
//			else if(responseCommand==InstructionsResponse.MAG_CALIBRATION_RESPONSE_VALUE){
//				processMagCalReadBytes();
//			}
//			else if(responseCommand==InstructionsResponse.ECG_CALIBRATION_RESPONSE_VALUE){
//				processShimmer2EcgCalReadBytes();
//			}
//			else if(responseCommand==InstructionsResponse.EMG_CALIBRATION_RESPONSE_VALUE){
//				processShimmer2EmgCalReadBytes();
//			}
//			else if(responseCommand==InstructionsResponse.LSM303DLHC_ACCEL_CALIBRATION_RESPONSE_VALUE){
//				processLsm303dlhcAccelCalReadBytes();
//			}
			else if(responseCommand==InstructionsResponse.CONFIG_BYTE0_RESPONSE_VALUE){
				byte[] bufferConfigByte0 = null;
//				long configByte0 = 0;
				if(mShimmerVerObject.isShimmerGen2()){    
					bufferConfigByte0 = readBytes(1);
//					configByte0 = bufferConfigByte0[0] & 0xFF;
				} 
				else {
					bufferConfigByte0 = readBytes(4);
//					configByte0 = ((long)(bufferConfigByte0[0] & 0xFF) +((long)(bufferConfigByte0[1] & 0xFF) << 8)+((long)(bufferConfigByte0[2] & 0xFF) << 16) +((long)(bufferConfigByte0[3] & 0xFF) << 24));
				}
//				eventResponseReceived(responseCommand, configByte0);
				eventResponseReceived(responseCommand, bufferConfigByte0);
			}
//			else if(responseCommand==InstructionsResponse.DERIVED_CHANNEL_BYTES_RESPONSE_VALUE){
//				byte[] byteArray = readBytes(3);
//				mDerivedSensors=(long)(((byteArray[2]&0xFF)<<16) + ((byteArray[1]&0xFF)<<8)+(byteArray[0]&0xFF));
//				
//				if(mShimmerVerObject.isSupportedEightByteDerivedSensors()){
//					byteArray = readBytes(5);
//					
//					mDerivedSensors |= ((long)(byteArray[0] & 0xFF)) << (8*3); 
//					mDerivedSensors |= ((long)(byteArray[1] & 0xFF)) << (8*4); 
//					mDerivedSensors |= ((long)(byteArray[2] & 0xFF)) << (8*5); 
//					mDerivedSensors |= ((long)(byteArray[3] & 0xFF)) << (8*6); 
//					mDerivedSensors |= ((long)(byteArray[4] & 0xFF)) << (8*7); 
//				}
//
//				if (mEnabledSensors!=0){
//					prepareAllAfterConfigRead();
//					inquiryDone();
//				}
//			}
			else if(responseCommand==InstructionsResponse.GET_SHIMMER_VERSION_RESPONSE_VALUE){
				delayForBtResponse(100); // Wait to ensure the packet has been fully received
				byte[] bufferShimmerVersion = new byte[1]; 
				bufferShimmerVersion = readBytes(1);
				
				setHardwareVersion((int)bufferShimmerVersion[0]); //Save a local copy
				eventResponseReceived(responseCommand, (int)bufferShimmerVersion[0]);

//				if(mShimmerVersion==HW_ID.SHIMMER_2R){
//					initializeShimmer2R();
//				} 
//				else if(mShimmerVersion==HW_ID.SHIMMER_3) {
//					initializeShimmer3();
//				}
				
				printLogDataForDebugging("Shimmer Version (HW) Response Received: " + UtilShimmer.bytesToHexStringWithSpacesFormatted(bufferShimmerVersion));
				
				readFWVersion();
			}
//			else if(responseCommand==InstructionsResponse.ACCEL_SENSITIVITY_RESPONSE_VALUE){
//				byte[] bufferAccelSensitivity = readBytes(1);
//				mAccelRange=bufferAccelSensitivity[0];
//				if(mDefaultCalibrationParametersAccel){
//					if(getHardwareVersion()!=HW_ID.SHIMMER_3){
//						if(getAccelRange()==0){
//							mSensitivityMatrixAnalogAccel = SensitivityMatrixAccel1p5gShimmer2; 
//						} 
//						else if(getAccelRange()==1){
//							mSensitivityMatrixAnalogAccel = SensitivityMatrixAccel2gShimmer2; 
//						} 
//						else if(getAccelRange()==2){
//							mSensitivityMatrixAnalogAccel = SensitivityMatrixAccel4gShimmer2; 
//						} 
//						else if(getAccelRange()==3){
//							mSensitivityMatrixAnalogAccel = SensitivityMatrixAccel6gShimmer2; 
//						}
//					} 
//					else if(getHardwareVersion()==HW_ID.SHIMMER_3){
//						if(getAccelRange()==0){
//							mSensitivityMatrixAnalogAccel = SensitivityMatrixLowNoiseAccel2gShimmer3;
//							mAlignmentMatrixAnalogAccel = AlignmentMatrixLowNoiseAccelShimmer3;
//							mOffsetVectorAnalogAccel = OffsetVectorLowNoiseAccelShimmer3;
//						} 
//						else if(getAccelRange()==1){
//							mSensitivityMatrixAnalogAccel = SensitivityMatrixWideRangeAccel4gShimmer3;
//							mAlignmentMatrixAnalogAccel = AlignmentMatrixWideRangeAccelShimmer3;
//							mOffsetVectorAnalogAccel = OffsetVectorWideRangeAccelShimmer3;
//						} 
//						else if(getAccelRange()==2){
//							mSensitivityMatrixAnalogAccel = SensitivityMatrixWideRangeAccel8gShimmer3;
//							mAlignmentMatrixAnalogAccel = AlignmentMatrixWideRangeAccelShimmer3;
//							mOffsetVectorAnalogAccel = OffsetVectorWideRangeAccelShimmer3;
//						} 
//						else if(getAccelRange()==3){
//							mSensitivityMatrixAnalogAccel = SensitivityMatrixWideRangeAccel16gShimmer3;
//							mAlignmentMatrixAnalogAccel = AlignmentMatrixWideRangeAccelShimmer3;
//							mOffsetVectorAnalogAccel = OffsetVectorWideRangeAccelShimmer3;
//						}
//					}
//				}
//			}
//			else if(responseCommand==InstructionsResponse.MPU9150_GYRO_RANGE_RESPONSE_VALUE){
//				byte[] bufferGyroSensitivity = readBytes(1);
//				mGyroRange=bufferGyroSensitivity[0];
//				if(mDefaultCalibrationParametersGyro){
//					if(getHardwareVersion()==HW_ID.SHIMMER_3){
//						mAlignmentMatrixGyroscope = AlignmentMatrixGyroShimmer3;
//						mOffsetVectorGyroscope = OffsetVectorGyroShimmer3;
//						if(mGyroRange==0){
//							mSensitivityMatrixGyroscope = SensitivityMatrixGyro250dpsShimmer3;
//						} 
//						else if(mGyroRange==1){
//							mSensitivityMatrixGyroscope = SensitivityMatrixGyro500dpsShimmer3;
//						} 
//						else if(mGyroRange==2){
//							mSensitivityMatrixGyroscope = SensitivityMatrixGyro1000dpsShimmer3;
//						} 
//						else if(mGyroRange==3){
//							mSensitivityMatrixGyroscope = SensitivityMatrixGyro2000dpsShimmer3;
//						}
//					}
//				}
//			}
//			else if(responseCommand==InstructionsResponse.GSR_RANGE_RESPONSE_VALUE){
//				byte[] bufferGSRRange = readBytes(1); 
//				mGSRRange=bufferGSRRange[0];
//				
//				printLogDataForDebugging("GSR Range Response Received: " + UtilShimmer.bytesToHexStringWithSpacesFormatted(bufferGSRRange));
//			}
			else if(responseCommand==InstructionsResponse.BLINK_LED_RESPONSE_VALUE){
				byte[] byteled = readBytes(1);
//				mCurrentLEDStatus = byteled[0]&0xFF;
				eventResponseReceived(responseCommand, (int)((byte)(byteled[0]&0xFF)));
			}
			else if(responseCommand==InstructionsResponse.BUFFER_SIZE_RESPONSE_VALUE){
				byte[] byteled = readBytes(1);
				int bufferSize = byteled[0] & 0xFF;
				eventResponseReceived(responseCommand, bufferSize);
			}
//			else if(responseCommand==InstructionsResponse.MAG_GAIN_RESPONSE_VALUE){
//				byte[] bufferAns = readBytes(1); 
//				mMagRange=bufferAns[0];
//			}
//			else if(responseCommand==InstructionsResponse.MAG_SAMPLING_RATE_RESPONSE_VALUE){
//				byte[] bufferAns = readBytes(1); 
//				mLSM303MagRate=bufferAns[0];
//				
//				printLogDataForDebugging("Mag Sampling Rate Response Received: " + UtilShimmer.bytesToHexStringWithSpacesFormatted(bufferAns));
//			}
//			else if(responseCommand==InstructionsResponse.ACCEL_SAMPLING_RATE_RESPONSE_VALUE){
//				byte[] bufferAns = readBytes(1); 
//				mLSM303DigitalAccelRate=bufferAns[0];
//			}
			else if(responseCommand==InstructionsResponse.BMP180_CALIBRATION_COEFFICIENTS_RESPONSE_VALUE){
				//get pressure
				delayForBtResponse(100); // Wait to ensure the packet has been fully received
				byte[] pressureResoRes = new byte[22]; 
				pressureResoRes = readBytes(22);
				byte[] mPressureCalRawParams = new byte[23];
				System.arraycopy(pressureResoRes, 0, mPressureCalRawParams, 1, 22);
				mPressureCalRawParams[0] = (byte) responseCommand;
//				retrievePressureCalibrationParametersFromPacket(pressureResoRes,responseCommand);
				eventResponseReceived(responseCommand, pressureResoRes);
			}
//			else if(responseCommand==InstructionsResponse.EXG_REGS_RESPONSE_VALUE){
//				delayForBtResponse(300); // Wait to ensure the packet has been fully received
//				byte[] bufferAns = readBytes(11);
//				if(mTempChipID==EXG_CHIP_INDEX.CHIP1.ordinal()){
//					byte[] EXG1RegisterArray = new byte[10];
//					System.arraycopy(bufferAns, 1, EXG1RegisterArray, 0, 10);
//					setEXG1RegisterArray(EXG1RegisterArray);
//				} 
//				else if(mTempChipID==EXG_CHIP_INDEX.CHIP2.ordinal()){
//					byte[] EXG2RegisterArray = new byte[10];
//					System.arraycopy(bufferAns, 1, EXG2RegisterArray, 0, 10);
//					setEXG2RegisterArray(EXG2RegisterArray);
//				}
//			}
			else if(responseCommand==InstructionsResponse.DAUGHTER_CARD_ID_RESPONSE_VALUE){
				byte[] expBoardArray = readBytes(numBytesToReadFromExpBoard+1);
//				getExpBoardID();//CHANGED TO NEWER UP-TO-DATE method
				byte[] expBoardArraySplit = Arrays.copyOfRange(expBoardArray, 1, 4);
//				setExpansionBoardDetails(new ExpansionBoardDetails(expBoardArraySplit));
				eventResponseReceived(responseCommand, new ExpansionBoardDetails(expBoardArraySplit));
			}
			else if(responseCommand==InstructionsResponse.BAUD_RATE_RESPONSE_VALUE){
				byte[] bufferBaud = readBytes(1);
				int mBluetoothBaudRate=bufferBaud[0] & 0xFF;
				eventResponseReceived(responseCommand, mBluetoothBaudRate);
			}
			else if(responseCommand==InstructionsResponse.TRIAL_CONFIG_RESPONSE_VALUE){
				rxBuf = readBytes(3);
//				fillTrialShimmer3(data);
				eventResponseReceived(responseCommand, rxBuf);
			}
			else if(responseCommand==InstructionsResponse.CENTER_RESPONSE_VALUE){
				length = readBytes(1);
				rxBuf = readBytes(length[0]);
				String center = new String(rxBuf);
//				setCenter(center);
				eventResponseReceived(responseCommand, center);
			}
			else if(responseCommand==InstructionsResponse.SHIMMERNAME_RESPONSE_VALUE){
				length = readBytes(1);
				rxBuf = readBytes(length[0]);
				String name = new String(rxBuf);
//				setShimmerUserAssignedName(name);
				eventResponseReceived(responseCommand, name);
			}
			else if(responseCommand==InstructionsResponse.EXPID_RESPONSE_VALUE){
				length = readBytes(1);
				rxBuf = readBytes(length[0]);
				String expId = new String(rxBuf);
//				setTrialName(expId);
				eventResponseReceived(responseCommand, expId);
			}
			else if(responseCommand==InstructionsResponse.CONFIGTIME_RESPONSE_VALUE){
				length = readBytes(1);
				rxBuf = readBytes(length[0]);
				String time = new String(rxBuf);
//				if(time.isEmpty()){
//					setConfigTime(0);
//				} 
//				else {
//					setConfigTime(Long.parseLong(time));	
//				}
				eventResponseReceived(responseCommand, time);
			}
			else if(responseCommand==InstructionsResponse.RWC_RESPONSE_VALUE){
				rxBuf = readBytes(8);
				
				// Parse response string
				rxBuf = Arrays.copyOf(rxBuf, 8);
				ArrayUtils.reverse(rxBuf);
				long responseTime = (long)(((double)(ByteBuffer.wrap(rxBuf).getLong())/32.768)); // / 1000
				
//				setLastReadRealTimeClockValue(responseTime);
				eventResponseReceived(responseCommand, responseTime);
			}
			else if(responseCommand==InstructionsResponse.INSTREAM_CMD_RESPONSE_VALUE){
				processInstreamResponse();
			}
//			else if(responseCommand==InstructionsResponse.LSM303DLHC_ACCEL_LPMODE_RESPONSE_VALUE){
//				byte[] responseData = readBytes(1);
//				mLowPowerAccelWR = (((int)(responseData[0]&0xFF))>=1? true:false);
//			}
//			else if(responseCommand==InstructionsResponse.LSM303DLHC_ACCEL_HRMODE_RESPONSE_VALUE){
//				byte[] responseData = readBytes(1);
//				mHighResAccelWR = (((int)(responseData[0]&0xFF))>=1? true:false);
//			}
//			else if(responseCommand==InstructionsResponse.MYID_RESPONSE_VALUE){
//				byte[] responseData = readBytes(1);
//				mTrialId = (int)(responseData[0]&0xFF);
//			}
//			else if(responseCommand==InstructionsResponse.NSHIMMER_RESPONSE_VALUE){
//				byte[] responseData = readBytes(1);
//				mTrialNumberOfShimmers = (int)(responseData[0]&0xFF);
//			}
//			else if(responseCommand==InstructionsResponse.MPU9150_SAMPLING_RATE_RESPONSE_VALUE){
//				byte[] responseData = readBytes(1);
//				setMPU9150MPLSamplingRate(((int)(responseData[0]&0xFF)));
//			}
//			else if(responseCommand==InstructionsResponse.BMP180_PRES_RESOLUTION_RESPONSE_VALUE){
//				byte[] responseData = readBytes(1);
//				mPressureResolution = (int)(responseData[0]&0xFF);
//			}
//			else if(responseCommand==InstructionsResponse.BMP180_PRES_CALIBRATION_RESPONSE_VALUE){
//				//TODO: Not used
//			}
//			else if(responseCommand==InstructionsResponse.MPU9150_MAG_SENS_ADJ_VALS_RESPONSE_VALUE){
//				//TODO: Not used
//			}
//			else if(responseCommand==InstructionsResponse.INTERNAL_EXP_POWER_ENABLE_RESPONSE_VALUE){
//				byte[] responseData = readBytes(1);
//				setInternalExpPower((int)(responseData[0]&0xFF));
//			}
			else if(responseCommand==InstructionsResponse.UNIQUE_SERIAL_RESPONSE_VALUE){
				rxBuf = readBytes(8);
				eventResponseReceived(responseCommand, rxBuf);
			}
			else if(responseCommand==InstructionsResponse.INFOMEM_RESPONSE_VALUE){
				// Get data length to read
				rxBuf = readBytes(1);
				int lengthToRead = (int)(rxBuf[0]&0xFF);
				rxBuf = readBytes(lengthToRead);
				printLogDataForDebugging("INFOMEM_RESPONSE Received: " + UtilShimmer.bytesToHexStringWithSpacesFormatted(rxBuf));
				
				//Copy to local buffer
//				System.arraycopy(rxBuf, 0, mMemBuffer, mCurrentMemAddress, lengthToRead);
				mMemBuffer = ArrayUtils.addAll(mMemBuffer, rxBuf);
	
				MemReadDetails memReadDetails = mMapOfMemReadDetails.get(InstructionsGet.GET_INFOMEM_COMMAND_VALUE);
				if(memReadDetails!=null){
					int currentEndAddress = memReadDetails.mCurrentMemAddress+memReadDetails.mCurrentMemLengthToRead; 
					//Update configuration when all bytes received.
					if(currentEndAddress>=memReadDetails.mEndMemAddress){
	//					setShimmerInfoMemBytes(mMemBuffer);
						eventResponseReceived(responseCommand, mMemBuffer);
						clearMemReadBuffer(InstructionsGet.GET_INFOMEM_COMMAND_VALUE);
					}
				}
			}
			else if(responseCommand==InstructionsResponse.RSP_CALIB_DUMP_COMMAND_VALUE) {
				rxBuf = readBytes(3);
				int currentMemLength = rxBuf[0]&0xFF;
				//Memory is currently read sequentially so no need to use the below at the moment.
				int currentMemOffset = ((rxBuf[2]&0xFF)<<8) | (rxBuf[1]&0xFF);
				
				//For debugging
				byte[] rxBufFull = rxBuf;
				
				rxBuf = readBytes(currentMemLength);
				mMemBuffer = ArrayUtils.addAll(mMemBuffer, rxBuf);

				//For debugging
				rxBufFull = ArrayUtils.addAll(rxBufFull, rxBuf);
				printLogDataForDebugging("CALIB_DUMP Received:\t" + UtilShimmer.bytesToHexStringWithSpacesFormatted(rxBuf));
				printLogDataForDebugging("CALIB_DUMP concat:\t" + UtilShimmer.bytesToHexStringWithSpacesFormatted(mMemBuffer));
				
				MemReadDetails memReadDetails = mMapOfMemReadDetails.get(InstructionsGet.GET_CALIB_DUMP_COMMAND_VALUE);
				if(memReadDetails!=null){
					if(memReadDetails.mCurrentMemAddress==0){
						//First read
						int totalCalibLength = (rxBuf[1]&0xFF)<<8 | (rxBuf[0]&0xFF);
						 // +2 because calib dump length bytes are not included
						memReadDetails.setTotalMemLengthToRead(totalCalibLength+2); 
						
						if(memReadDetails.getTotalMemLengthToRead()>memReadDetails.mCurrentMemLengthToRead){
							int nextAddress = memReadDetails.mCurrentMemLengthToRead;
							int remainingBytes = memReadDetails.getTotalMemLengthToRead()-memReadDetails.mCurrentMemLengthToRead;
							readCalibrationDump(nextAddress, remainingBytes);
							rePioritiseReadCalibDumpInstructions();
						}
					}
				
					int currentEndAddress = memReadDetails.mCurrentMemAddress+memReadDetails.mCurrentMemLengthToRead; 
					//Update calibration dump when all bytes received.
					if(currentEndAddress>=memReadDetails.mEndMemAddress){
	//					parseCalibByteDump(mMemBuffer, CALIB_READ_SOURCE.RADIO_DUMP);
						eventResponseReceived(responseCommand, mMemBuffer);
						clearMemReadBuffer(InstructionsGet.GET_CALIB_DUMP_COMMAND_VALUE);
					}
				}
			}
			else if(responseCommand==InstructionsResponse.RSP_I2C_BATT_STATUS_COMMAND_VALUE) {
				byte[] responseData = readBytes(10); 
				System.err.println("STC3100 response = " + UtilShimmer.bytesToHexStringWithSpacesFormatted(responseData));
			}

			else{
				printLogDataForDebugging("Unhandled BT response: " + UtilShimmer.bytesToHexStringWithSpacesFormatted(new byte[]{(byte) responseCommand}));
			}

		} catch(ShimmerException dE){
			mUtilShimmer.consolePrintShimmerException(dE);
		}

	}
	

//	private void processFirmwareVerResponse() {
//		if(isShimmerBluetoothApproach){
////			if(getHardwareVersion()==HW_ID.SHIMMER_2R){
////				initializeShimmer2R();
////			} 
////			else if(getHardwareVersion()==HW_ID.SHIMMER_3) {
////				initializeShimmer3();
////			}
////			
////			startTimerCheckIfAlive();
////	//		readShimmerVersion();
//		}
//	}
	

	private void processSpecialGetCmdsAfterAck(int currentCommand) {
		byte[] insBytes = getListofInstructions().get(0);
		if(mUseShimmerBluetoothApproach){
			if(currentCommand==InstructionsGet.GET_EXG_REGS_COMMAND_VALUE){
				// Need to store ExG chip number before receiving response
				mTempChipID = insBytes[1];
			}
			else if(currentCommand==InstructionsGet.GET_INFOMEM_COMMAND_VALUE || currentCommand==InstructionsGet.GET_CALIB_DUMP_COMMAND_VALUE){
				// store current address/InfoMem segment
				MemReadDetails memReadDetails = mMapOfMemReadDetails.get(currentCommand);
				if(memReadDetails!=null){
					memReadDetails.mCurrentMemAddress = ((insBytes[3]&0xFF)<<8)+(insBytes[2]&0xFF);
					memReadDetails.mCurrentMemLengthToRead = (insBytes[1]&0xFF);
				}
			}
		}
		else{
			//TODO 2016-07-06 MN removed to make consistent with ShimmerBluetooth implementation 
			eventAckReceived(insBytes[0]);
		}
	}

	protected boolean isKnownResponseByte(byte response) {
		return ((InstructionsResponse.valueOf(response&0xff)==null)? false:true);
	}

	private boolean isKnownGetCommand(int getCmd) {
//		return ((InstructionsGet.valueOf(getCmd&0xff)==null)? false:true);
		return isKnownGetCommandByte((byte) getCmd);
	}

	protected boolean isKnownGetCommandByte(byte getCmd) {
		return ((InstructionsGet.valueOf(getCmd&0xff)==null)? false:true);
	}

	private boolean isKnownSetCommand(int setCmd) {
//		return ((InstructionsSet.valueOf(setCmd)==null)? false:true);
		return isKnownSetCommandByte((byte) setCmd);
	}

	protected boolean isKnownSetCommandByte(byte setCmd) {
		return ((InstructionsSet.valueOf(setCmd&0xff)==null)? false:true);
	}

	private boolean bytesAvailableToBeRead() throws ShimmerException {
		if(mCommsInterface.isConnected()){
			return mCommsInterface.bytesAvailableToBeRead();
		}
		return false;
	}

	protected void writeBytes(byte[] insBytes) throws ShimmerException {
		mCommsInterface.txBytes(insBytes);
	}

	protected byte[] readBytes(int i) throws ShimmerException {
		return mCommsInterface.rxBytes(i);
	}

	private byte readByte() throws ShimmerException {
		byte[] rxBytes = readBytes(1);
		return rxBytes[0];
	}

	private int availableBytes() throws ShimmerException {
		return mCommsInterface.availableBytes();
	}
	
	public void eventLogAndStreamStatusChanged(int currentCommand){
		mProtocolListener.eventLogAndStreamStatusChangedCallback(currentCommand);
	}
	
	/**
	 * Unwinds the in-flight transaction after the Shimmer answered a command
	 * with NACK (0xFE) instead of ACK. The refused instruction is dropped so the
	 * stack advances, and the connection is left up - a refusal means the device
	 * declined this command, not that the link is gone.
	 */
	private void processNackFromCommand() {
		stopTimerCheckForAckOrResp(); //cancel the ack timer
		printLogDataForDebugging("NACK Received for Command: \t\t" + btCommandToString(mCurrentCommand));
		
		int refusedCommand = ((int)mCurrentCommand)&0xFF;
		
		mWaitForAck=false;
		mWaitForResponse=false;
		
		if(getListofInstructions().size()>0){
			getListofInstructions().remove(0);
		}
		getListofInstructions().removeAll(Collections.singleton(null));
		
		mTransactionCompleted=true;
		setInstructionStackLock(false);
		
		eventCommandRefused(refusedCommand);
	}
	
	/**
	 * Called when the Shimmer refused a command with a NACK. The transaction has
	 * already been unwound and the connection is still up. Concrete rather than
	 * a new ProtocolListener callback so that existing implementers keep
	 * compiling; override to surface the refusal to the application.
	 *
	 * @param command the command value that was refused
	 */
	protected void eventCommandRefused(int command) {
		//Default: no application-level notification, the log line above stands
	}

	private void isNowStreaming() {
		mProtocolListener.isNowStreaming();
	}
	
	private void hasStopStreaming() {
		mProtocolListener.hasStopStreaming();
	}

	private void eventAckReceived(int lastSentInstruction) {
		mProtocolListener.eventAckReceived(lastSentInstruction);
	}

	private void sendProgressReport(BluetoothProgressReportPerCmd bluetoothProgressReportPerCmd) {
		mProtocolListener.sendProgressReport(bluetoothProgressReportPerCmd);
	}

	private void eventNewResponse(byte[] response) {
		mProtocolListener.eventNewResponse(response);
	}
	
	protected void eventResponseReceived(int responseCommand, Object parsedResponse) {
		mProtocolListener.eventResponseReceived(responseCommand, parsedResponse);
	}

	@Deprecated
	protected void eventAckInstruction(byte[] bs) {
		mProtocolListener.eventAckInstruction(bs);
	}

	private void eventNewPacket(byte[] newPacket, long pcTimestamp) {
		mProtocolListener.eventNewPacket(newPacket, pcTimestamp);
	}
	
	private void startOperation(BT_STATE currentOperation, int totalNumOfCmds) {
		mProtocolListener.startOperation(currentOperation, totalNumOfCmds);
	}

	private void sendStatusMSGtoUI(String msg) {
		mProtocolListener.sendStatusMSGtoUI(msg);
	}

	private void eventDockedStateChange() {
		mProtocolListener.eventDockedStateChange();
	}
	
	private void threadSleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected String btCommandToString(int cmd) {
		InstructionsResponse instructionResponse = InstructionsResponse.valueOf(cmd&0xff); 
		if(instructionResponse!=null){
			return instructionResponse.name();
		}
		else{
			InstructionsGet instructionGet = InstructionsGet.valueOf(cmd&0xff); 
			if(instructionGet!=null){
				return instructionGet.name();
			}
			else{
				InstructionsSet instructionSet = InstructionsSet.valueOf(cmd&0xff); 
				if(instructionSet!=null){
					return instructionSet.name();
				}
				else {
					return Integer.toHexString(mCurrentCommand);
				}
			}
		}
	}

	public void printLogDataForDebugging(String msg){
		mUtilShimmer.consolePrintLn(msg);
	}
	
	/**
	 * Due to the nature of the Bluetooth SPP stack a delay has been added to
	 * ensure the buffer is filled before it is read
	 * 
	 */
	private void delayForBtResponse(long millis){
		if(mUseLegacyDelayToDelayForResponse)
			threadSleep(millis);
	}
	

	private void processAckFromSetCommandAndInstructions(int currentCommand) {
		// check for null and size were put in because if Shimmer was abruptly
		// disconnected there is sometimes indexoutofboundsexceptions
		if(getListofInstructions().size() > 0){
			byte[] currentInstruction = getListofInstructions().get(0);
			if(currentInstruction!=null){
//			if(getListofInstructions().get(0)!=null){
				
				processAckFromSetCommand(currentCommand);
				
				if(getListofInstructions().size() > 0){
					getListofInstructions().remove(0);
				}

				eventAckInstruction(currentInstruction);
			}
		}
	}
	
	protected void processAckFromSetCommand(int currentCommand) {
		if(currentCommand==InstructionsSet.START_STREAMING_COMMAND_VALUE 
				|| currentCommand==InstructionsSet.START_SDBT_COMMAND_VALUE) {
			mByteArrayOutputStream.reset();
			setIsStreaming(true);
			if(currentCommand==InstructionsSet.START_SDBT_COMMAND_VALUE){
				setIsSDLogging(true);
				eventLogAndStreamStatusChanged(mCurrentCommand);
			}
			byteStack.clear();
			isNowStreaming();
		}
		else if((currentCommand==InstructionsSet.STOP_STREAMING_COMMAND_VALUE)
				||(currentCommand==InstructionsSet.STOP_SDBT_COMMAND_VALUE)){
			setIsStreaming(false);
			if(currentCommand==InstructionsSet.STOP_SDBT_COMMAND_VALUE) {
				setIsSDLogging(false);
				eventLogAndStreamStatusChanged(mCurrentCommand);
			}
			mByteArrayOutputStream = new ByteArrayOutputStream();
			byteStack.clear();

			//TODO try statement is not used in ShimmerBluetooth
			try {
				clearSerialBuffer();
			} catch (ShimmerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			hasStopStreaming();					
			getListofInstructions().removeAll(Collections.singleton(null));
		}
		else if(currentCommand==InstructionsSet.START_LOGGING_ONLY_COMMAND_VALUE) {
			setIsSDLogging(true);
			eventLogAndStreamStatusChanged(mCurrentCommand);
		}
		else if(currentCommand==InstructionsSet.STOP_LOGGING_ONLY_COMMAND_VALUE) {
			setIsSDLogging(false);
			eventLogAndStreamStatusChanged(mCurrentCommand);
		}
		else if(currentCommand==InstructionsSet.SET_INFOMEM_COMMAND_VALUE 
				|| currentCommand==InstructionsSet.SET_CALIB_DUMP_COMMAND_VALUE){
			//SET InfoMem is automatically followed by a GET so no need to handle here
			
			//Sleep for Xsecs to allow Shimmer to process new configuration
			mNumOfMemSetCmds -= 1;
			if(!mShimmerVerObject.isBtMemoryUpdateCommandSupported()){
				if(mNumOfMemSetCmds==0){
					threadSleep(DELAY_BETWEEN_CONFIG_BYTE_WRITES);
				}
				else {
					threadSleep(DELAY_AFTER_CONFIG_BYTE_WRITE);
				}
			}
		}
		else if(currentCommand==InstructionsSet.UPD_CONFIG_MEMORY_COMMAND_VALUE){
			if(mShimmerVerObject.isBtMemoryUpdateCommandSupported()){
				threadSleep(DELAY_AFTER_CONFIG_BYTE_WRITE);
			}
		}
		else{
//			printLogDataForDebugging("Unhandled ACK for current command:\t" + btCommandToString(currentCommand));
		}
	}

	public void readRealTimeClock(){
		if((getHardwareVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM) 
				|| (getHardwareVersion()==HW_ID.SHIMMER_4_SDK && getFirmwareIdentifier()==FW_ID.SHIMMER4_SDK_STOCK)){
			writeInstruction(InstructionsGet.GET_RWC_COMMAND_VALUE);
		}
	}
	
	/**Gets pc time and writes the 8 byte value to shimmer device
	 * 
	 */
	public void writeRealTimeClock(){
		if((getHardwareVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM) 
				|| (getHardwareVersion()==HW_ID.SHIMMER_4_SDK && getFirmwareIdentifier()==FW_ID.SHIMMER4_SDK_STOCK)){
			//Just fill empty bytes here for RTC, set them just before writing to Shimmer
		    byte[] bytearraycommand = new byte[9];
			bytearraycommand[0]=(byte) InstructionsSet.SET_RWC_COMMAND_VALUE;
			getListofInstructions().add(bytearraycommand);
		}
	}
	
	/**
	 * 
	 */
	private void clearBuffers() {
		mByteArrayOutputStream.reset();
		mListofPCTimeStamps.clear();
	}
	
	/**
	 * 
	 */
	private void discardFirstBufferByte(){
		byte[] bTemp = mByteArrayOutputStream.toByteArray();
		mByteArrayOutputStream.reset();
		mByteArrayOutputStream.write(bTemp, 1, bTemp.length-1); //this will throw the first byte away
		mListofPCTimeStamps.remove(0);
		printLogDataForDebugging("Throw Byte" + UtilShimmer.byteToHexStringFormatted(bTemp[0]));
	}
	
	/**
	 * @param bufferTemp
	 */
	private void processDataPacket(byte[] bufferTemp){
		//Create newPacket buffer
		byte[] newPacket = new byte[mPacketSize];
		//Skip the first byte as it is the identifier DATA_PACKET
		System.arraycopy(bufferTemp, 1, newPacket, 0, mPacketSize);
		
		long pcTimestamp = System.currentTimeMillis();
		eventNewPacket(newPacket, pcTimestamp);
	}

	/**
	 * Clear the parsed packet from the mByteArrayOutputStream, NOTE the
	 * last two bytes(seq number of next packet) are added back on after the
	 * reset
	 * 
	 * @param bufferTemp
	 * @param packetSize
	 */
	private void clearSingleDataPacketFromBuffers(byte[] bufferTemp, int packetSize) {
		mByteArrayOutputStream.reset();
		mByteArrayOutputStream.write(bufferTemp[packetSize]);
//		printLogDataForDebugging(Integer.toString(bufferTemp[mPacketSize+2]));
		for (int i=0;i<packetSize;i++){
			mListofPCTimeStamps.remove(0);
		}
	}

	/** process responses to in-stream response */
	private void processInstreamResponse(){
		try {
			byte[] inStreamResponseCommandArray = readBytes(1);
			processInstreamResponse(inStreamResponseCommandArray);
		} catch (ShimmerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void processInstreamResponse(byte[] inStreamResponseCommandArray) throws ShimmerException{
		int inStreamResponseCommand = ((int)inStreamResponseCommandArray[0])&0xFF;
		printLogDataForDebugging("In-stream received = " + btCommandToString(inStreamResponseCommand));
		
		if(inStreamResponseCommand==InstructionsResponse.DIR_RESPONSE_VALUE){ 
			byte[] responseData = readBytes(1);
			int directoryNameLength = responseData[0];
			byte[] bufferDirectoryName = new byte[directoryNameLength];
			bufferDirectoryName = readBytes(directoryNameLength);
			String tempDirectory = new String(bufferDirectoryName);
			mDirectoryName = tempDirectory;
			printLogDataForDebugging("Directory Name = " + mDirectoryName);
			
			if(!mUseShimmerBluetoothApproach){
				bufferDirectoryName = ArrayUtils.addAll(responseData, bufferDirectoryName);
				eventNewResponse(bufferDirectoryName);
			}
			else{
				eventResponseReceived(inStreamResponseCommand, tempDirectory);
			}
		}
		else if(inStreamResponseCommand==InstructionsResponse.STATUS_RESPONSE_VALUE){
			if(mUseShimmerBluetoothApproach){
				byte[] responseData = readBytes(1);
				parseStatusByte(responseData[0]);
//				eventResponseReceived(inStreamResponseCommand, responseData[0]);
			}
			
			if(!isSensing()){
				if(mUseShimmerBluetoothApproach){
					if(!isInitialised()){
						writeRealTimeClock();
					}
				}
				else{
					writeRealTimeClock();
				}
			}
			eventLogAndStreamStatusChanged(mCurrentCommand);
		} 
		else if(inStreamResponseCommand==InstructionsResponse.VBATT_RESPONSE_VALUE) {
			byte[] responseData = readBytes(3); 
			if(mUseShimmerBluetoothApproach){
				ShimmerBattStatusDetails battStatusDetails = new ShimmerBattStatusDetails(((responseData[1]&0xFF)<<8)+(responseData[0]&0xFF),responseData[2]);
//				setBattStatusDetails(battStatusDetails);
				eventResponseReceived(inStreamResponseCommand, battStatusDetails);
				printLogDataForDebugging("Battery Status:"
						+ "\tVoltage=" + battStatusDetails.getBattVoltageParsed()
						+ "\tCharging status=" + battStatusDetails.getChargingStatusParsed()
						+ "\tBatt %=" + battStatusDetails.getEstimatedChargePercentageParsed());
			}
			else{
				responseData = ArrayUtils.addAll(inStreamResponseCommandArray, responseData);
				eventNewResponse(responseData);
			}
		}
		else if(inStreamResponseCommand==InstructionsResponse.RSP_I2C_BATT_STATUS_COMMAND_VALUE) {
			byte[] responseData = readBytes(10); 
			SensorSTC3100Details sensorSTC3100Details = new SensorSTC3100Details(responseData); 
			eventResponseReceived(inStreamResponseCommand, sensorSTC3100Details);
		}		
	}
	
	//TODO copied from ShimmerBluetooth
	private void parseStatusByte(byte statusByte) {
		Boolean savedDockedState = isDocked();
		
		setIsDocked(((statusByte & 0x01) > 0)? true:false);
		setIsSensing(((statusByte & 0x02) > 0)? true:false);
//		reserved = ((statusByte & 0x03) > 0)? true:false;
		setIsSDLogging(((statusByte & 0x08) > 0)? true:false);
		setIsStreaming(((statusByte & 0x10) > 0)? true:false); 
		
		printLogDataForDebugging("Status Response = " + UtilShimmer.byteToHexStringFormatted(statusByte)
				+ " | IsDocked = " + isDocked()
				+ " | IsSensing = " + isSensing()
				+ " | IsSDLogging = "+ isSDLogging()
				+ " | IsStreaming = " + isStreaming()
				);
		
		if(savedDockedState!=isDocked()){
			eventDockedStateChange();
		}
	}
	
	public void readSamplingRate() {
		writeInstruction(InstructionsGet.GET_SAMPLING_RATE_COMMAND_VALUE);
	}

	/**
	 * The reason for this is because sometimes the 1st response is not received by the phone
	 */
	protected void dummyreadSamplingRate() {
		mDummy=true;
		writeInstruction(InstructionsGet.GET_SAMPLING_RATE_COMMAND_VALUE);
	}

	/**
	 * @param rate Defines the sampling rate to be set (e.g.51.2 sets the sampling rate to 51.2Hz). User should refer to the document Sampling Rate Table to see all possible values.
	 */
//	public void writeShimmerAndSensorsSamplingRate(int samplingByteValue) {
	public void writeShimmerAndSensorsSamplingRate(byte[] samplingRateBytes) {
//		if(mIsInitialised) {
//			if(mShimmerVerObject.isShimmerGen2()){
//				writeInstruction(new byte[]{InstructionsSet.SET_SAMPLING_RATE_COMMAND_VALUE, (byte)Math.rint(samplingByteValue), 0x00});
//			} 
//			else if(getHardwareVersion()==HW_ID.SHIMMER_3) {
//				writeInstruction(new byte[]{InstructionsSet.SET_SAMPLING_RATE_COMMAND_VALUE, (byte)(samplingByteValue&0xFF), (byte)((samplingByteValue>>8)&0xFF)});
//			}
			
			writePacket(InstructionsSet.SET_SAMPLING_RATE_COMMAND_VALUE, samplingRateBytes);
//		}
		readSamplingRate();
	}
	
	private byte[] buildCmdArray(int cmd, byte[] payload) {
		byte[] packet = new byte[payload.length+1];
		packet[0] = (byte) cmd;
		System.arraycopy(payload, 0, packet, 1, payload.length);
		return packet;
	}
	
	private void writePacket(int cmd, byte[] payload) {
		writeInstruction(buildCmdArray(cmd, payload));
	}
	
	
	@Override
	public void toggleLed() {
		byte[] instructionLED = {InstructionsSet.TOGGLE_LED_COMMAND_VALUE};
		writeInstruction(instructionLED);
	}

	@Override
	public void readFWVersion() {
		mDummy=false;//false
		writeInstruction(InstructionsGet.GET_FW_VERSION_COMMAND_VALUE);
	}

	@Override
	public void readShimmerVersionNew() {
		mDummy=false;//false
//		if(mFirmwareVersionParsed.equals(boilerPlateString)){
//			mShimmerVersion = HW_ID.SHIMMER_2R; // on Shimmer2r has 
			
//		} 
//		else if(mFWVersion!=1.2){
			writeInstruction(InstructionsGet.GET_SHIMMER_VERSION_COMMAND_NEW_VALUE);
//		} 
//			else {
//			mListofInstructions.add(GET_SHIMMER_VERSION_COMMAND});
//		}
	}
	
	@Override
	public void readPressureCalibrationCoefficients() {
		if((getHardwareVersion()==HW_ID.SHIMMER_3)
				||(getHardwareVersion()==HW_ID.SHIMMER_4_SDK)){
			if(getFirmwareVersionCode()>1){
				writeInstruction(InstructionsGet.GET_BMP180_CALIBRATION_COEFFICIENTS_COMMAND_VALUE);
			}
		}
	}

	public void readBaudRate(){
		if(getFirmwareVersionCode()>=5){ 
			writeInstruction(InstructionsGet.GET_BAUD_RATE_COMMAND_VALUE);
		}
	}

	/**
	 * writeBaudRate(value) sets the baud rate on the Shimmer. 
	 * @param value numeric value defining the desired Baud rate. Valid rate settings are 0 (115200 default),
	 *  1 (1200), 2 (2400), 3 (4800), 4 (9600) 5 (19200),
	 *  6 (38400), 7 (57600), 8 (230400), 9 (460800) and 10 (921600)
	 */
	public void writeBaudRate(int value) {
		if(getFirmwareVersionCode()>=5){ 
			if(value>=0 && value<=10){
				writeInstruction(new byte[]{InstructionsSet.SET_BAUD_RATE_COMMAND_VALUE, (byte)value});
				delayForBtResponse(200);
				reconnect();
			}
		}
	}

	//TODO butchered from ShimmerBluetooth
	private void reconnect() {
//        if(isConnected() && !mIsStreaming){
//        	String msgReconnect = "Reconnecting the Shimmer...";
//			sendStatusMSGtoUI(msgReconnect);
//            stop();
//            threadSleep(300);
//            connect(mMyBluetoothAddress,"default");
//            setUniqueID(this.mMacIdFromUart); 
//        }

		try {
			mCommsInterface.disconnect();
	        threadSleep(300);
			mCommsInterface.connect();
		} catch (ShimmerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void readConfigByte0() {
		writeInstruction(InstructionsGet.GET_CONFIG_BYTE0_COMMAND_VALUE);
	}
	
	/**
	 * writeConfigByte0(configByte0) sets the config byte0 value on the Shimmer to the value of the input configByte0. 
	 * @param configByte0 is an unsigned 8 bit value defining the desired config byte 0 value.
	 */
	public void writeConfigByte0(byte[] configByte0) {
		writePacket(InstructionsSet.SET_CONFIG_BYTE0_COMMAND_VALUE, configByte0);
		readConfigByte0();
	}
	
	public void readBufferSize() {
		writeInstruction(InstructionsGet.GET_BUFFER_SIZE_COMMAND_VALUE);
	}

	/**
	 * writeAccelRange(range) sets the Accelerometer range on the Shimmer to the value of the input range. When setting/changing the accel range, please ensure you have the correct calibration parameters. Note that the Shimmer device can only carry one set of accel calibration parameters at a single time.
	 * @param range is a numeric value defining the desired accelerometer range. Valid range setting values for the Shimmer 2 are 0 (+/- 1.5g), 1 (+/- 2g), 2 (+/- 4g) and 3 (+/- 6g). Valid range setting values for the Shimmer 2r are 0 (+/- 1.5g) and 3 (+/- 6g).
	 */
	public void writeBufferSize(int size) {
		writeInstruction(new byte[]{InstructionsSet.SET_BUFFER_SIZE_COMMAND_VALUE, (byte)size});
	}
	
	@Override
	public void readCalibrationDump(){ //This starts off the read process, request the first block = 128 bytes
		if(this.getFirmwareVersionCode()>=7){
			readMem(InstructionsGet.GET_CALIB_DUMP_COMMAND_VALUE, 0, 128, MAX_CALIB_DUMP_MAX); //some max number
		}
	}

	@Override
	public void readCalibrationDump(int address, int size){
		if(this.getFirmwareVersionCode()>=7){
			readMem(InstructionsGet.GET_CALIB_DUMP_COMMAND_VALUE, address, size, MAX_CALIB_DUMP_MAX); //some max number
		}
	}

	public void rePioritiseReadCalibDumpInstructions(){
		List<byte[]> listOfInstructions = new ArrayList<byte[]>();

		//This for loop will prioritise the GET_CALIB_DUMP_COMMAND
		Iterator<byte[]> iterator = mListofInstructions.iterator();
		while(iterator.hasNext()){
			byte[] instruction = iterator.next();
			if(instruction[0]==(byte) InstructionsGet.GET_CALIB_DUMP_COMMAND_VALUE){
				listOfInstructions.add(instruction);
				iterator.remove();
			}
		}
		
		if(listOfInstructions.size()>0){
			mListofInstructions.addAll(0, listOfInstructions);
		}
	}

	@Override
	public void writeCalibrationDump(byte[] calibDump){
		if(this.getFirmwareVersionCode()>=7){
			writeMem(InstructionsSet.SET_CALIB_DUMP_COMMAND_VALUE, 0, calibDump, MAX_CALIB_DUMP_MAX);
			writeUpdateConfigMemory();
//			readCalibrationDump(0, calibDump.length);
			readCalibrationDump();
		}
	}
	
	@Override
	protected void readInfoMem(int address, int size) {
		readMem(InstructionsGet.GET_INFOMEM_COMMAND_VALUE, address, size, 512); //some max number
	}

	@Override
	public void writeInfoMem(int startAddress, byte[] buf) {
		writeMem(InstructionsSet.SET_INFOMEM_COMMAND_VALUE, startAddress, buf, 512); //some max number
		writeUpdateConfigMemory();
	}

	private void writeUpdateConfigMemory() {
		if(mShimmerVerObject.isSupportedCalibDump()){
			writeInstruction(InstructionsSet.UPD_CONFIG_MEMORY_COMMAND_VALUE);
		}
	}

	@Override
	public void readMemCommand(int command, int address, int size) {
		if(this.getFirmwareVersionCode()>=6){
	    	byte[] memLengthToRead = new byte[]{(byte) size};
	    	byte[] memAddressToRead = ByteBuffer.allocate(2).putShort((short)(address&0xFFFF)).array();
			ArrayUtils.reverse(memAddressToRead);

	    	byte[] instructionBuffer = new byte[1 + memLengthToRead.length + memAddressToRead.length];
	    	instructionBuffer[0] = (byte)command;
	    	System.arraycopy(memLengthToRead, 0, instructionBuffer, 1, memLengthToRead.length);
	    	System.arraycopy(memAddressToRead, 0, instructionBuffer, 1 + memLengthToRead.length, memAddressToRead.length);

			writeInstruction(instructionBuffer);
		}
	}


	/**Could be used by InfoMem or Expansion board memory
	 * @param command
	 * @param address
	 * @param infoMemBytes
	 */
	public void writeMemCommand(int command, int address, byte[] infoMemBytes) {
//		if(this.getFirmwareVersionCode()>=6){
			byte[] memLengthToWrite = new byte[]{(byte) infoMemBytes.length};
			byte[] memAddressToWrite = ByteBuffer.allocate(2).putShort((short)(address&0xFFFF)).array();
			ArrayUtils.reverse(memAddressToWrite);

			// TODO check I'm not missing the last two bytes here because the mem
			// address length is not being included in the length field
			byte[] instructionBuffer = new byte[1 + memLengthToWrite.length + memAddressToWrite.length + infoMemBytes.length];
	    	instructionBuffer[0] = (byte)command;
			System.arraycopy(memLengthToWrite, 0, instructionBuffer, 1, memLengthToWrite.length);
			System.arraycopy(memAddressToWrite, 0, instructionBuffer, 1 + memLengthToWrite.length, memAddressToWrite.length);
			System.arraycopy(infoMemBytes, 0, instructionBuffer, 1 + memLengthToWrite.length + memAddressToWrite.length, infoMemBytes.length);
			
			writeInstruction(instructionBuffer);
//			getListofInstructions().add(instructionBuffer);
//		}
	}

	//region --------- OPERATIONS --------- 
	/**
	 * 
	 */
	@Override
	public void operationPrepare(){
		stopAllTimers();

		//make sure no more instructions
		//shouldnt matter since the configuration are being rewritten by consensys
		//used in initializeshimmer3 (this class) and BluetoothManager class
		getListofInstructions().clear();
		// wait for instruction stack to clear			
		while(getListofInstructions().size()>0); //TODO add timeout
		// lock the instruction stack
		setInstructionStackLock(true);
		mOperationUnderway = true; 
	}
	
	/**
	 * 
	 */
	public void operationWaitForFinish(){
		// unlock the instruction stack
		setInstructionStackLock(false);
		// wait for instruction stack to clear			
		while(getListofInstructions().size()>0); //TODO add timeout
	}
	
	/**
	 * @param btState
	 */
	@Override
	public void operationStart(BT_STATE btState){
//		mOperationUnderway = true;
		startOperation(btState, getListofInstructions().size());
		//unlock instruction stack
		setInstructionStackLock(false);
	}
	
	/**
	 * 
	 */
	public void operationFinished(){
		/*
		startTimerCheckIfAlive();
		startTimerReadStatus();
		startTimerReadBattStatus();
		*/
		mOperationUnderway = false;
	}
	//endregion --------- OPERATIONS --------- 
	
	
	//region  --------- START/STOP STREAMING FUNCTIONS --------- 
	@Override
	public void startStreaming() {
		//mCurrentLEDStatus=-1;	
		
		initialiseStreaming();

		mByteArrayOutputStream.reset();
		mListofPCTimeStamps.clear();
		writeInstruction(InstructionsSet.START_STREAMING_COMMAND_VALUE);
	}
	
	@Override
	public void startDataLogAndStreaming(){
		if((getShimmerVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM)
				||(getShimmerVersion()==HW_ID.SHIMMER_4_SDK && getFirmwareIdentifier()==FW_ID.SHIMMER4_SDK_STOCK)){
//		if(getFirmwareIdentifier()==FW_ID.LOGANDSTREAM){ // if shimmer is using LogAndStream FW, stop reading its status perdiocally
			initialiseStreaming();
			
			//TODO: ask JC, should mByteArrayOutputStream.reset(); and mListofPCTimeStamps.clear(); be here as well? 
			writeInstruction(InstructionsSet.START_SDBT_COMMAND_VALUE);
		}
	}
	
	private void initialiseStreaming(){
		if(((getShimmerVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM)
				||(getShimmerVersion()==HW_ID.SHIMMER_4_SDK && getFirmwareIdentifier()==FW_ID.SHIMMER4_SDK_STOCK))
				&& getFirmwareVersionCode() >=6){
			readRealTimeClock();
		}

		stopTimerReadStatus(); // if shimmer is using LogAndStream FW, stop reading its status perdiocally

		mProtocolListener.initialiseStreamingCallback();
		System.out.println("initialiseStreamingCallback:\tRETURNED");

//		//provides a callback for users to initialize their algorithms when start streaming is called
//		if(mDataProcessing!=null){
//			mDataProcessing.InitializeProcessData();
//		} 	
//		else {
//			//do nothing
//		}
//		
//		mPacketLossCount = 0;
//		setPacketReceptionRateOverall(100);
//		mFirstPacketParsed=true;
//		mFirstTimeCalTime=true;
//		resetCalibratedTimeStamp();
//		mLastReceivedCalibratedTimeStamp = -1;
//		mSync=true; // a backup sync done every time you start streaming
	}
	
	@Override
	public void startSDLogging() {
		if(((getShimmerVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM)
				||(getShimmerVersion()==HW_ID.SHIMMER_4_SDK && getFirmwareIdentifier()==FW_ID.SHIMMER4_SDK_STOCK))
				&& getFirmwareVersionCode() >=6){
//		if(getFirmwareIdentifier()==FW_ID.LOGANDSTREAM && getFirmwareVersionCode() >=6){
			writeInstruction(InstructionsSet.START_LOGGING_ONLY_COMMAND_VALUE);
		}	
	}

	@Override
	public void stopSDLogging() {
		if(((getShimmerVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM)
				||(getShimmerVersion()==HW_ID.SHIMMER_4_SDK && getFirmwareIdentifier()==FW_ID.SHIMMER4_SDK_STOCK))
				&& getFirmwareVersionCode() >=6){
//		if(getFirmwareIdentifier()==FW_ID.LOGANDSTREAM && getFirmwareVersionCode() >=6){
			writeInstruction(InstructionsSet.STOP_LOGGING_ONLY_COMMAND_VALUE);
		}	
	}

	@Override
	public void stopStreaming() {
		if(isStreaming()){
			writeInstruction(InstructionsSet.STOP_STREAMING_COMMAND_VALUE);
			
			// For LogAndStream
			stopTimerReadStatus();
		}
	}
	
	
	/**Only applicable for logandstream
	 * 
	 */
	@Override
	public void stopStreamingAndLogging() {
		// if shimmer is using LogAndStream FW, stop reading its status periodically
		if((getShimmerVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM)
				||(getShimmerVersion()==HW_ID.SHIMMER_4_SDK && getFirmwareIdentifier()==FW_ID.SHIMMER4_SDK_STOCK)){
//		if(getFirmwareIdentifier()==FW_ID.LOGANDSTREAM){ 
			writeInstruction(InstructionsSet.STOP_SDBT_COMMAND_VALUE);
			// For LogAndStream
			stopTimerReadStatus();
		}
	}

	/**Set battery status (STC3100 chip) auto-transmission Period (in sec) 
	 */
	@Override
	public void writeBattStatusPeriod(int periodInSec) {
		if(getShimmerVersion()==HW_ID.SHIMMER_4_SDK){
			byte[] buf = new byte[2];
			buf[0] = (byte) (periodInSec&0xFF);
			buf[1] = (byte) ((periodInSec>>8)&0xFF);
			writeInstruction(new byte[]{(byte) (InstructionsSet.SET_I2C_BATT_STATUS_FREQ_COMMAND_VALUE&0xFF), buf[0], buf[1]});
		}
	}

	/**Get battery status (STC3100 chip) auto-transmission Period (in sec) 
	 */
	@Override
	public void readBattStatusPeriod() {
		if(getShimmerVersion()==HW_ID.SHIMMER_4_SDK){
			writeInstruction(InstructionsGet.GET_I2C_BATT_STATUS_COMMAND_VALUE);
		}
	}

	//endregion
	
	//region --------- TIMERS --------- 
	
	public void stopAllTimers(){
		stopTimerReadStatus();
		stopTimerCheckIfAlive();
		stopTimerCheckForAckOrResp();
		stopTimerReadBattStatus();
	}
	
	public void stopTimerCheckForAckOrResp(){
		//Terminate the timer thread
		if(mTimerCheckForAckOrResp!=null){
			mTimerCheckForAckOrResp.cancel();
			mTimerCheckForAckOrResp.purge();
			mTimerCheckForAckOrResp = null;
		}
	}
	
	public synchronized void startTimerCheckForAckOrResp(int seconds) {
	//public synchronized void responseTimer(int seconds) {
		if(mTimerCheckForAckOrResp!=null) {
			mTimerCheckForAckOrResp.cancel();
			mTimerCheckForAckOrResp.purge();
			mTimerCheckForAckOrResp = null;
		}
		printLogDataForDebugging("Waiting for ack/response for command:\t" + btCommandToString(mCurrentCommand));
		mTimerCheckForAckOrResp = new Timer("Shimmer_" + getConnectionHandle() + "_TimerCheckForResp");
		mTimerCheckForAckOrResp.schedule(new checkForAckOrRespTask(), seconds*1000);
	}
	
	/** Handles command response timeout
	 *
	 */
	class checkForAckOrRespTask extends TimerTask {
		
		@Override
		public void run() {
			int storedFirstTime = (mFirstTime? 1:0);
			
			//Timeout triggered 
			printLogDataForDebugging("Command:\t" + btCommandToString(mCurrentCommand) +" timeout");
			if(mWaitForAck){
				printLogDataForDebugging("Ack not received");
			}
			if(mWaitForResponse) {
				printLogDataForDebugging("Response not received");
				sendStatusMSGtoUI("Response not received, please reset Shimmer Device." + mMyBluetoothAddress); //Android?
			}
			//TODO
//				if(mIsStreaming && getPacketReceptionRateOverall()<100){
//					printLogDataForDebugging("Packet RR:  " + Double.toString(getPacketReceptionRateOverall()));
//				} 
			
			processCheckForAckOrRespPerCmd(mCurrentCommand);
			
			if(isStreaming()){
				stopTimerCheckForAckOrResp(); //Terminate the timer thread
				mWaitForAck=false;
				mTransactionCompleted=true; //should be false, so the driver will know that the command has to be executed again, this is not supported at the moment 
				setInstructionStackLock(false);
				getListofInstructions().clear();
			}
			else if(storedFirstTime==0){
				// If the command fails to get a response, the API should
				// assume that the connection has been lost and close the
				// serial port cleanly.
				
				try {
					if (bytesAvailableToBeRead()){
						readBytes(availableBytes());
					}
					stopTimerCheckForAckOrResp(); //Terminate the timer thread
					printLogDataForDebugging("RETRY TX COUNT: " + Integer.toString(mNumberofTXRetriesCount));
					if (mNumberofTXRetriesCount>=NUMBER_OF_TX_RETRIES_LIMIT 
							&& mCurrentCommand!=InstructionsGet.GET_SHIMMER_VERSION_COMMAND_NEW_VALUE 
							&& !isInitialised()){
						killConnection("Reached number of TX retries = " + NUMBER_OF_TX_RETRIES_LIMIT); //If command fail exit device	
					} else if(mNumberofTXRetriesCount>=NUMBER_OF_TX_RETRIES_LIMIT && isInitialised()){
						killConnection("Reached number of TX retries = " + NUMBER_OF_TX_RETRIES_LIMIT); //If command fail exit device	
					} else {
						mWaitForAck=false;
						mWaitForResponse=false;
						mTransactionCompleted=true; //should be false, so the driver will know that the command has to be executed again, this is not supported at the moment 
						setInstructionStackLock(false);
						//this is needed because if u dc the shimmer the write call gets stuck
						startTimerCheckForAckOrResp(ACK_TIMER_DURATION+3);
					}
				} catch (ShimmerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				mNumberofTXRetriesCount++;
			}
		} //End Run

	} //End TimerTask

	protected void processCheckForAckOrRespPerCmd(int currentCommand) {
		//handle the special case when we are starting/stopping to log in Consensys and we do not get the ACK response
		//we will send the status changed to the GUI anyway
		if((currentCommand&0xFF)==InstructionsSet.START_LOGGING_ONLY_COMMAND_VALUE){
			
			printLogDataForDebugging("START_LOGGING_ONLY_COMMAND response not received. Send feedback to the GUI without killing the connection");
			
			setIsSDLogging(true);
			eventLogAndStreamStatusChanged(currentCommand);
			mWaitForAck=false;
			mWaitForResponse=false;
			
			getListofInstructions().remove(0);
			mTransactionCompleted = true;
			setInstructionStackLock(false);
			
			return;
		}
		else if((currentCommand&0xFF)==InstructionsSet.STOP_LOGGING_ONLY_COMMAND_VALUE){
			
			printLogDataForDebugging("STOP_LOGGING_ONLY_COMMAND response not received. Send feedback to the GUI without killing the connection");
			
			setIsSDLogging(false);
			eventLogAndStreamStatusChanged(currentCommand);
			mWaitForAck=false;
			mWaitForResponse=false;
			
			getListofInstructions().remove(0);
			mTransactionCompleted = true;
			setInstructionStackLock(false);
			
			return;
		}
		

		if((currentCommand&0xFF)==InstructionsGet.GET_FW_VERSION_COMMAND_VALUE){
			mFirstTime=false;
//			eventAckReceived(mCurrentCommand);
//				setShimmerVersionInfoAndCreateSensorMap(new ShimmerVerObject(HW_ID.SHIMMER_2R, FW_ID.BOILER_PLATE, 0, 1, 0));
//				
////				/*Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
////      	        Bundle bundle = new Bundle();
////      	        bundle.putString(TOAST, "Firmware Version: " +mFirmwareVersionParsed);
////      	        msg.setData(bundle);*/
////				if(!mDummy){
////					//mHandler.sendMessage(msg);
////				}
//				
//				initializeBoilerPlate();
		}
		else if((currentCommand&0xFF)==InstructionsGet.GET_SAMPLING_RATE_COMMAND_VALUE && !isInitialised()){
			mFirstTime=false;
		} 
		else if((currentCommand&0xFF)==InstructionsGet.GET_SHIMMER_VERSION_COMMAND_NEW_VALUE){ //in case the new command doesn't work, try the old command
			mFirstTime=false;
			getListofInstructions().clear();
			readShimmerVersionDeprecated();
		}
	}
	
	private void killConnection(String info){
		killConnection(null, info);
	}

	private void killConnection(ShimmerException dE){
		killConnection(dE, "");
	}

	private void killConnection(ShimmerException dE, String info){
		printLogDataForDebugging("Killing Connection" + (info.isEmpty()? "":(": " + info)));
//		stop(); //If command fail exit device
		mProtocolListener.eventKillConnectionRequest(dE);
	}

	@Override
	public void startTimerReadStatus(){
		// if shimmer is using LogAndStream FW, stop reading its status periodically
		if((getHardwareVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM) 
				|| (getHardwareVersion()==HW_ID.SHIMMER_4_SDK && getFirmwareIdentifier()==FW_ID.SHIMMER4_SDK_STOCK)){
			if(mTimerReadStatus==null){ 
				mTimerReadStatus = new Timer("Shimmer_" + getConnectionHandle() + "_TimerReadStatus");
			} else {
				mTimerReadStatus.cancel();
				mTimerReadStatus.purge();
				mTimerReadStatus = null;
			}
			mTimerReadStatus.schedule(new readStatusTask(), TIMER_READ_STATUS_PERIOD, TIMER_READ_STATUS_PERIOD);
		}
	}
	
	public void stopTimerReadStatus(){
		if(mTimerReadStatus!=null){
			mTimerReadStatus.cancel();
			mTimerReadStatus.purge();
			mTimerReadStatus = null;
		}
	}
	
	/**
	 * Class used to read perdiocally the shimmer status when LogAndStream FW is installed
	 */
	public class readStatusTask extends TimerTask {

		@Override
		public void run() {
			if(getListofInstructions().size()==0 
					&& !getListofInstructions().contains(InstructionsGet.GET_STATUS_COMMAND_VALUE)){
				readStatusLogAndStream();
			}
		}
		
	}
	
	@Override
	public void startTimerCheckIfAlive(){
		if(mCheckIfConnectionisAlive){
			if(mTimerCheckAlive==null){ 
				mTimerCheckAlive = new Timer("Shimmer_" + getConnectionHandle() + "_TimerCheckAlive");
			}
			int hwId = getHardwareVersion();
			int fwId = getFirmwareIdentifier();
			//dont really need this for log and stream since we already have the get status timer
			if((hwId==HW_ID.SHIMMER_3 && fwId==FW_ID.LOGANDSTREAM)
					|| (hwId==HW_ID.SHIMMER_3 && fwId==FW_ID.BTSTREAM)
					|| (hwId==HW_ID.SHIMMER_4_SDK && fwId==FW_ID.SHIMMER4_SDK_STOCK)
					|| (hwId==HW_ID.SWEATCH)){
				mTimerCheckAlive.schedule(new checkIfAliveTask(), TIMER_CHECK_ALIVE_PERIOD, TIMER_CHECK_ALIVE_PERIOD);
			}
		}
	}
	
	@Override
	public void stopTimerCheckIfAlive(){
		if(mTimerCheckAlive!=null){
			mTimerCheckAlive.cancel();
			mTimerCheckAlive.purge();
			mTimerCheckAlive = null;
		}
	}
	
	/**
	 * @author Lim
	 * Used to check if the connection is alive 
	 */
	private class checkIfAliveTask extends TimerTask {
		
		@Override
		public void run() {
			if(mIamAlive){
				mCountDeadConnection = 0;
				mIamAlive=false;
			}
			else{
				int hwId = getHardwareVersion();
				int fwId = getFirmwareIdentifier();
				
				if(isStreaming()) {
//				if((hwId==HW_ID.SHIMMER_3 && fwId==FW_ID.LOGANDSTREAM && isStreaming())
//						|| (hwId==HW_ID.SHIMMER_3 && fwId==FW_ID.BTSTREAM)
//						|| (hwId==HW_ID.SHIMMER_4_SDK && fwId==FW_ID.SHIMMER4_SDK_STOCK && isStreaming())
//						|| (hwId==HW_ID.SWEATCH && isStreaming())){
					mCountDeadConnection++;
				}
				
				if(getFirmwareVersionCode()>=6 && !isStreaming()){
					if(getListofInstructions().size()==0 
							&& !getListofInstructions().contains(InstructionsSet.TEST_CONNECTION_COMMAND_VALUE)){
						printLogDataForDebugging("Check Alive Task");
						if((getShimmerVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM)
								||(getShimmerVersion()==HW_ID.SHIMMER_4_SDK && getFirmwareIdentifier()==FW_ID.SHIMMER4_SDK_STOCK)){
//						if(getShimmerVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM){
							//writeTestConnectionCommand(); //dont need this because of the get status command
						} 
						else if (getShimmerVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.BTSTREAM){
							writeTestConnectionCommand();
						}
					}
				} 
				else {
					printLogDataForDebugging("Check Alive Task");
					writeLEDCommand(0);
				}
				
				if(mCountDeadConnection>5){
//					setState(BT_STATE.NONE);
					killConnection("Keep Alive timer dead connection count > 5"); //If command fail exit device
				}
			} 
		} //End Run
	} //End TimerTask
	
	@Override
	public void startTimerReadBattStatus(){
		//Instream response only supported in LogAndStream
		if(((getShimmerVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM)
				||(getShimmerVersion()==HW_ID.SHIMMER_4_SDK && getFirmwareIdentifier()==FW_ID.SHIMMER4_SDK_STOCK))
				&&(getFirmwareVersionCode()>=6)){
			if(mTimerReadBattStatus==null){ 
				mTimerReadBattStatus = new Timer("Shimmer_" + getConnectionHandle() + "_TimerBattStatus");
			}
			mTimerReadBattStatus.schedule(new readBattStatusTask(), TIMER_READ_BATT_STATUS_PERIOD, TIMER_READ_BATT_STATUS_PERIOD);
		}
	}
	
	public void stopTimerReadBattStatus(){
		if(mTimerReadBattStatus!=null){
			mTimerReadBattStatus.cancel();
			mTimerReadBattStatus.purge();
			mTimerReadBattStatus = null;
		}
	}
	
	/**
	 * Used to check the current battery status 
	 */
	private class readBattStatusTask extends TimerTask {
		@Override
		public void run() {
			printLogDataForDebugging("Read Batt Task");
			readBattery();
		} //End Run
	} //End TimerTask

	@Override
	public void restartTimersIfNull() {
		if (mTimerCheckAlive==null && mTimerReadStatus==null && mTimerReadBattStatus==null){
			startTimerCheckIfAlive();
			startTimerReadStatus();
			startTimerReadBattStatus();
        }
	}

//	public synchronized void startTimerCheckForAckOrResp(int seconds) {
//		//public synchronized void responseTimer(int seconds) {
//		if(mTimerCheckForAckOrResp!=null) {
//			mTimerCheckForAckOrResp.cancel();
//			mTimerCheckForAckOrResp.purge();
//			mTimerCheckForAckOrResp = null;
//		}
//
//		printLogDataForDebugging("Waiting for ack/response for command:\t" + mCurrentCommand);
//		mTimerCheckForAckOrResp = new Timer("Shimmer_" + "_TimerCheckForResp");
//		mTimerCheckForAckOrResp.schedule(new checkForAckOrRespTask(), seconds*1000);
//	}
//	
//	public void stopTimerCheckForAckOrResp(){
//		//Terminate the timer thread
//		if(mTimerCheckForAckOrResp!=null){
//			mTimerCheckForAckOrResp.cancel();
//			mTimerCheckForAckOrResp.purge();
//			mTimerCheckForAckOrResp = null;
//		}
//	}
//
//	/** Handles command response timeout
//	 *
//	 */
//	class checkForAckOrRespTask extends TimerTask {
//		
//		@Override
//		public void run() {
//			{
//				int storedFirstTime = (mFirstTime? 1:0);
//				
//				//Timeout triggered 
//				printLogDataForDebugging("Command:\t" + mCurrentCommand +" timeout");
//				if(mWaitForAck){
//					printLogDataForDebugging("Ack not received");
//				}
//				if(mWaitForResponse) {
//					printLogDataForDebugging("Response not received");
//					
//				}
//				if(mIsStreaming && getPacketReceptionRate()<100){
//					printLogDataForDebugging("Packet RR:  " + Double.toString(getPacketReceptionRate()));
//				} 
//				
//				//handle the special case when we are starting/stopping to log in Consensys and we do not get the ACK response
//				//we will send the status changed to the GUI anyway
//				if(mCurrentCommand==InstructionsSet.START_LOGGING_ONLY_COMMAND_VALUE){
//					
//					printLogDataForDebugging("START_LOGGING_ONLY_COMMAND response not received. Send feedback to the GUI without killing the connection");
//					
//					mIsSDLogging = true;
//					eventLogAndStreamStatusChanged(mCurrentCommand);
//					mWaitForAck=false;
//					mWaitForResponse=false;
//					
//					getListofInstructions().remove(0);
//					mTransactionCompleted = true;
//					setInstructionStackLock(false);
//					
//					return;
//				}
//				else if(mCurrentCommand==InstructionsSet.STOP_LOGGING_ONLY_COMMAND_VALUE){
//					
//					printLogDataForDebugging("STOP_LOGGING_ONLY_COMMAND response not received. Send feedback to the GUI without killing the connection");
//					
//					mIsSDLogging = false;
//					eventLogAndStreamStatusChanged(mCurrentCommand);
//					mWaitForAck=false;
//					mWaitForResponse=false;
//					
//					getListofInstructions().remove(0);
//					mTransactionCompleted = true;
//					setInstructionStackLock(false);
//					
//					return;
//				}
//				
//
//				if(mCurrentCommand==InstructionsGet.GET_FW_VERSION_COMMAND_VALUE){
//					
//				}
//				else if(mCurrentCommand==InstructionsGet.GET_SAMPLING_RATE_COMMAND_VALUE && !mIsInitialised){
//					mFirstTime=false;
//				} 
//				else if(mCurrentCommand==InstructionsGet.GET_SHIMMER_VERSION_COMMAND_NEW_VALUE){ //in case the new command doesn't work, try the old command
//					mFirstTime=false;
//					
//				}
//
//				
//				
//				if(mIsStreaming){
//					stopTimerCheckForAckOrResp(); //Terminate the timer thread
//					mWaitForAck=false;
//					mTransactionCompleted=true; //should be false, so the driver will know that the command has to be executed again, this is not supported at the moment 
//					setInstructionStackLock(false);
//					getListofInstructions().clear();
//				}
//				else if(storedFirstTime==0){
//					// If the command fails to get a response, the API should
//					// assume that the connection has been lost and close the
//					// serial port cleanly.
//					
//					try {
//						if (bytesAvailableToBeRead()){
//							try {
//								readBytes(availableBytes());
//							} catch (DeviceException e) {
//								// TODO Auto-generated catch block
//								e.printStackTrace();
//							}
//						}
//					} catch (DeviceException e1) {
//						// TODO Auto-generated catch block
//						e1.printStackTrace();
//					}
//					stopTimerCheckForAckOrResp(); //Terminate the timer thread
//					printLogDataForDebugging("RETRY TX COUNT: " + Integer.toString(mNumberofTXRetriesCount));
//					if (mNumberofTXRetriesCount>=NUMBER_OF_TX_RETRIES_LIMIT){
//						disconnect();
//					} else {
//						mWaitForAck=false;
//						mWaitForResponse=false;
//						mTransactionCompleted=true; //should be false, so the driver will know that the command has to be executed again, this is not supported at the moment 
//						setInstructionStackLock(false);
//						//this is needed because if u dc the shimmer the write call gets stuck
//						startTimerCheckForAckOrResp(ACK_TIMER_DURATION+3);
//					}
//					
//					mNumberofTXRetriesCount++;
//				}
//			}
//		} //End Run
//	} //End TimerTask
	
	//endregion --------- TIMERS ---------
	
	@Override
	public void readExpansionBoardID() {
		if(getFirmwareVersionCode()>=5){ 
			numBytesToReadFromExpBoard=3;
			int offset=0;
			writeInstruction(new byte[]{InstructionsGet.GET_DAUGHTER_CARD_ID_COMMAND_VALUE, (byte) numBytesToReadFromExpBoard, (byte) offset});
		}
	}

	@Override
	public void readLEDCommand() {
		writeInstruction(InstructionsGet.GET_BLINK_LED_VALUE);
	}
	
	public void writeLEDCommand(int command) {
//		if(mShimmerVersion!=HW_ID.SHIMMER_3){
			if(mShimmerVerObject.compareVersions(HW_ID.SHIMMER_2R, FW_ID.BOILER_PLATE, 0, 1, 0)){
	//			if(mFirmwareVersionParsed.equals(boilerPlateStringDescription)){
			}
			else {
				writeInstruction(new byte[]{InstructionsSet.SET_BLINK_LED_VALUE, (byte)command});
			}
//		}
	}


	@Override
	public void readStatusLogAndStream() {
		if((getShimmerVersion()==HW_ID.SHIMMER_3 && getFirmwareIdentifier()==FW_ID.LOGANDSTREAM)
				||(getShimmerVersion()==HW_ID.SHIMMER_4_SDK && getFirmwareIdentifier()==FW_ID.SHIMMER4_SDK_STOCK)){ // check if Shimmer is using LogAndStream firmware
			writeInstruction(InstructionsGet.GET_STATUS_COMMAND_VALUE);
			printLogDataForDebugging("Instruction added to th e list");
		}
	}

	@Override
	public void readBattery() {
		writeInstruction(InstructionsGet.GET_VBATT_COMMAND_VALUE);
	}

	@Override
	public void inquiry() {
		writeInstruction(InstructionsGet.INQUIRY_COMMAND_VALUE);
	}
	
	public void writeTestConnectionCommand() {
		if(getFirmwareVersionCode()>=6){
			writeInstruction(InstructionsSet.TEST_CONNECTION_COMMAND_VALUE);
		}
	}

	@Deprecated
	public void readShimmerVersionDeprecated(){
		writeInstruction(InstructionsGet.GET_SHIMMER_VERSION_COMMAND_VALUE);
	}

	/**
	 * Transmits a command to the Shimmer device to enable the sensors. To enable multiple sensors an or operator should be used (e.g. writeEnabledSensors(SENSOR_ACCEL|SENSOR_GYRO|SENSOR_MAG)). Command should not be used consecutively. Valid values are SENSOR_ACCEL, SENSOR_GYRO, SENSOR_MAG, SENSOR_ECG, SENSOR_EMG, SENSOR_GSR, SENSOR_EXP_BOARD_A7, SENSOR_EXP_BOARD_A0, SENSOR_BRIDGE_AMP and SENSOR_HEART.
    SENSOR_BATT
	 * @param enabledSensors e.g SENSOR_ACCEL|SENSOR_GYRO|SENSOR_MAG
	 */
	@Override
	public void writeEnabledSensors(long enabledSensors) {
		
//		if(getFirmwareVersionCode()<=1){
//			if(!sensorConflictCheck(enabledSensors)){ //sensor conflict check - not needed with new SensorMap
//				return;
//			} 
//			else {
//				enabledSensors=generateSensorBitmapForHardwareControl(enabledSensors);
//			}
//		}
//			
//		tempEnabledSensors=enabledSensors;

		byte secondByte=(byte)((enabledSensors & 0xFF00)>>8);
		byte firstByte=(byte)(enabledSensors & 0xFF);

		//write(SET_SENSORS_COMMAND,(byte) lowByte, highByte});
		if((getHardwareVersion()==HW_ID.SHIMMER_3)
			||(getHardwareVersion()==HW_ID.SHIMMER_4_SDK)){
			byte thirdByte=(byte)((enabledSensors & 0xFF0000)>>16);
			writeInstruction(new byte[]{InstructionsSet.SET_SENSORS_COMMAND_VALUE,(byte) firstByte,(byte) secondByte,(byte) thirdByte});
		} 
		else {
			writeInstruction(new byte[]{InstructionsSet.SET_SENSORS_COMMAND_VALUE,(byte) firstByte,(byte) secondByte});
		}
		inquiry();
	}
	
	public void readUniqueSerial() {
		writeInstruction(new byte[]{InstructionsGet.GET_UNIQUE_SERIAL_COMMAND_VALUE});
	}

	
	//TODO TEMP HERE
	private int getFirmwareVersionCode() {
		if(mShimmerVerObject!=null){
			return mShimmerVerObject.getFirmwareVersionCode();
		}
		return 6;
	}

	//TODO TEMP HERE
	private int getHardwareVersion() {
		return mHardwareVersion;
	}
	
	private void setHardwareVersion(int hardwareVersion) {
		mHardwareVersion = hardwareVersion;
	}
	
	//TODO TEMP HERE
	private String getConnectionHandle() {
		return mConnectionHandle;
	}

	
	//***********************************
	// Copied from ShimmerDevice Start
	//*********************************** 
	
//	private void eventSyncStates(boolean isDocked, boolean isInitialised, boolean isSdLogging, boolean isSensing, boolean isStreaming, boolean haveAttemptedToRead) {
//		mProtocolListener.eventSyncStates(isDocked, isInitialised, isSdLogging, isSensing, isStreaming, haveAttemptedToRead);
//	}

	/**
	 * @param docked the mDocked to set
	 */
	public void setIsDocked(boolean docked) {
		mIsDocked = docked;
		if(mProtocolListener!=null){
			mProtocolListener.eventSetIsDocked(mIsDocked);
		}
	}

	/**
	 * @return the mDocked
	 */
	public boolean isDocked() {
		return mIsDocked;
	}

    /** Returns true if device is streaming (Bluetooth)
     * @return
     */
    public boolean isStreaming(){
    	return mIsStreaming;
    }

	public void setIsStreaming(boolean state) {
		mIsStreaming = state;
		if(mProtocolListener!=null){
			mProtocolListener.eventSetIsStreaming(mIsStreaming);
		}
	}

	/**Only used for LogAndStream
	 * @return
	 */
	public boolean isSensing(){
		return (mIsSensing || mIsSDLogging || mIsStreaming);
	}
	
	public void setIsSensing(boolean state) {
		mIsSensing = state;
		if(mProtocolListener!=null){
			mProtocolListener.eventSetIsSensing(mIsSensing);
		}
	}
	
	public boolean isSDLogging(){
		return mIsSDLogging;
	}	

	public void setIsSDLogging(boolean state){
		UtilShimmer.consolePrintCurrentStackTrace();
		mIsSDLogging = state;
		if(mProtocolListener!=null){
			mProtocolListener.eventSetIsSDLogging(mIsSDLogging);
		}
	}	


	/**
	 * @param isInitialized the mSuccessfullyInitialized to set
	 */
	public void setIsInitialised(boolean isInitialized) {
		mIsInitialised = isInitialized;
		if(mProtocolListener!=null){
			mProtocolListener.eventSetIsInitialised(mIsInitialised);
		}
	}
	
	/**
	 * @return the mIsInitialized
	 */
	public boolean isInitialised() {
		return mIsInitialised;
	}
	
	/**
	 * @return the mHaveAttemptedToRead
	 */
	public boolean isHaveAttemptedToRead() {
		return mHaveAttemptedToReadConfig;
	}

	/**
	 * @param haveAttemptedToRead the mHaveAttemptedToRead to set
	 */
	public void setHaveAttemptedToRead(boolean haveAttemptedToRead) {
		mHaveAttemptedToReadConfig = haveAttemptedToRead;
		if(mProtocolListener!=null){
			mProtocolListener.eventSetHaveAttemptedToRead(mHaveAttemptedToReadConfig);
		}
	}

	//***********************************
	// Copied from ShimmerDevice end
	//*********************************** 

//	public void eventSyncStates(){
//		eventSyncStates(isDocked(), isInitialised(), isSDLogging(), isSensing(), isStreaming(), isHaveAttemptedToRead());
//	}

	
}
