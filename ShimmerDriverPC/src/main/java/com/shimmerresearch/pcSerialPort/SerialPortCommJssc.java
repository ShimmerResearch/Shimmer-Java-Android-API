package com.shimmerresearch.pcSerialPort;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;

import com.shimmerresearch.comms.serialPortInterface.ErrorCodesSerialPort;
import com.shimmerresearch.comms.serialPortInterface.AbstractSerialPortHal;
import com.shimmerresearch.comms.serialPortInterface.SerialPortListener;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.exceptions.ShimmerException;

/**
 * @author Mark Nolan
 *
 */
public class SerialPortCommJssc extends AbstractSerialPortHal implements SerialPortListener {

	protected transient SerialPort mSerialPort = null;
	//Using JsscByteWriter by Bastien Aracil as the JSSC library does not support timeouts for serial port writing
	protected JsscByteWriter jsscByteWriter = null;
	
	public String mUniqueId = "";
	public String mComPort = "";
	private int mBaudToUse = SerialPort.BAUDRATE_115200;
	private boolean setRtsOnConnect = true;
	private boolean setDtrOnConnect = false;

	private transient ShimmerUartListener mShimmerUartListener;
	private boolean mIsSerialPortReaderStarted = false;
	private transient SerialPortListener mShimmerSerialEventCallback;
	
	/** 0 = normal speed (bad implementation), 1 = fast speed */
	public int mTxSpeed = 1;
	
	private boolean mIsDebugMode = true;
	private boolean mVerboseMode = true;
	private UtilShimmer mUtilShimmer = new UtilShimmer(getClass().getSimpleName(), mVerboseMode);
	
	public SerialPortCommJssc(String comPort, String uniqueId, int baudToUse) {
		mUniqueId = uniqueId;
		mComPort = comPort;
		setConnectionHandle(comPort);
		mBaudToUse = baudToUse;
        mSerialPort = new SerialPort(mComPort);
        jsscByteWriter = new JsscByteWriter(mSerialPort);
	}

	public SerialPortCommJssc(String comPort, String uniqueId, int baudToUse, SerialPortListener shimmerSerialEventCallback) {
		this(comPort, uniqueId, baudToUse);
		registerSerialPortRxEventCallback(shimmerSerialEventCallback);
	}

	/** Opens and configures the Shimmer UART COM port
	 * @throws ShimmerException 
	 */
	@Override
	public void connect() throws ShimmerException {
        try {
    		consolePrintLn("Connecting to COM port:" + mComPort);
    		
//        	serialPort.setRTS(true);
//        	serialPort.setDTR(false);
    		consolePrintLn("Port open: " + mSerialPort.openPort());
    		consolePrintLn("Params set: " + mSerialPort.setParams(
            		mBaudToUse, 
            		SerialPort.DATABITS_8, 
            		SerialPort.STOPBITS_1, 
            		SerialPort.PARITY_NONE, 
            		setRtsOnConnect,
            		setDtrOnConnect));//Set params.
    		consolePrintLn("Port Status : " + Boolean.toString(mSerialPort.isOpened()));
//            mSerialPort.openPort();//Open serial port
//            mSerialPort.setParams(
//			            		mBaudToUse, 
//			            		SerialPort.DATABITS_8, 
//			            		SerialPort.STOPBITS_1, 
//			            		SerialPort.PARITY_NONE, 
//			            		true,
//			            		false);//Set params.
            mSerialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
            eventDeviceConnected();
        }
        catch (SerialPortException e) {
        	eventDeviceDisconnected();
        	ShimmerException de = generateException(ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTON_OPENING); 
        	de.updateDeviceException(e.getMessage(), e.getStackTrace());
			throw(de);
        }
        catch (Exception e) {
        	eventDeviceDisconnected();
        	ShimmerException de = generateException(ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTON_OPENING); 
        	de.updateDeviceException(e.getMessage(), e.getStackTrace());
			throw(de);
        }
        
		startSerialPortReader();
        
	}
	
	
	/** Closes the Shimmer UART COM port
	 * @throws ShimmerException 
	 */
	@Override
	public void disconnect() throws ShimmerException {
    	if(mSerialPort!=null && mSerialPort.isOpened()) {
	        try {
	        	mSerialPort.purgePort(1);
	        	mSerialPort.purgePort(2);
	        	mSerialPort.closePort();
	        	eventDeviceDisconnected();
			} catch (SerialPortException e) {
	        	ShimmerException de = generateException(ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTON_CLOSING); 
	        	de.updateDeviceException(e.getMessage(), e.getStackTrace());
				throw(de);
			}
    	}
    }
	
	@Override
	public void closeSafely() throws ShimmerException {
		if(isSerialPortReaderStarted()){
			stopSerialPortReader();
		}
		else {
			disconnect();
		}
	}


	@Override
	public void clearSerialPortRxBuffer() throws ShimmerException {
    	try {
			mSerialPort.purgePort(SerialPort.PURGE_RXCLEAR);
		} catch (SerialPortException e) {
        	ShimmerException de = generateException(ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTION); 
        	de.updateDeviceException(e.getMessage(), e.getStackTrace());
			throw(de);
		}
    	
    }

	/** Transmits a byte array to the Shimmer UART COM port
	 * @param buf the Tx buffer array
	 * @throws ShimmerException 
	 */
	@Override
	public void txBytes(byte[] buf) throws ShimmerException {
    	try {
        	if(mTxSpeed == 0) { // normal speed
        		for(int i = 0; i<buf.length;i++) {
//        			mSerialPort.writeByte(buf[i]);
//        			mSerialPort.purgePort(SerialPort.PURGE_TXCLEAR); // This doesn't make sense but was working for Consensys in Windows up to v0.4.0
        			jsscByteWriter.write(buf[i], mSerialPortTimeout);
        		}
        	}
        	else {  // fast speed - was causing issues with Windows but needed for OSX - Windows might be fixed now (23/03/2016)
//    			mSerialPort.writeBytes(buf);
    			jsscByteWriter.write(buf, mSerialPortTimeout);
        	}
//		} catch (SerialPortException e) {
		} catch (IOException | InterruptedException | TimeoutException e) {
        	ShimmerException de = generateException(ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_WRITING_DATA); 
        	de.updateDeviceException(e.getMessage(), e.getStackTrace());
			throw(de);
		}
    }    

    /** Receives a byte array from the Shimmer UART COM port
	 * @param numBytes the number of bytes to receive
	 * @return byte array of received bytes
     * @throws ShimmerException 
	 * @see ShimmerUartRxCmdDetails
	 */
	@Override
	public byte[] rxBytes(int numBytes) throws ShimmerException {
		try {
			byte[] rxBuf = mSerialPort.readBytes(numBytes, mSerialPortTimeout);
			if(this.mIsDebugMode){
				consolePrintLn("Serial Port RX(" + rxBuf.length + ")" + UtilShimmer.bytesToHexStringWithSpacesFormatted(rxBuf));
			}
			return rxBuf;
		} catch (SerialPortException e) {
        	ShimmerException de = generateException(ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_READING_DATA); 
        	de.updateDeviceException(e.getMessage(), e.getStackTrace());
			throw(de);
		} catch (SerialPortTimeoutException e) {
        	ShimmerException de = generateException(ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_TIMEOUT); 
        	de.updateDeviceException(e.getMessage(), e.getStackTrace());
			throw(de);
		}
    }
	
	public ShimmerException generateException(int lowLevelError){
    	ShimmerException de = new ShimmerException(
    			mUniqueId, 
    			mComPort,
    			ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTION,
    			lowLevelError); 
    	return de;
	}
	
    public void startSerialPortReader() throws ShimmerException {
    	
    	if(!mIsSerialPortReaderStarted){
            int mask = SerialPort.MASK_RXCHAR;//Prepare mask
        	mShimmerUartListener = new ShimmerUartListener(mUniqueId);
//        	mShimmerUartListener.setVerbose(mVerboseModeUart);
            try {
            	mSerialPort.setEventsMask(mask);//Set mask
            	mSerialPort.addEventListener(mShimmerUartListener);//Add SerialPortEventListener
//        		setWaitForData(mShimmerUartListener);
        		mIsSerialPortReaderStarted = true;
            } catch (SerialPortException e) {
        		mIsSerialPortReaderStarted = false;
    			ShimmerException de = generateException(ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_READER_START);
    			de.updateDeviceException(e.getMessage(), e.getStackTrace());
    			throw(de);
            }
    	}
    }

    public void stopSerialPortReader() throws ShimmerException {
        try {
        	mSerialPort.removeEventListener();
        } catch (SerialPortException e) {
			ShimmerException de = generateException(ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_READER_STOP);
			de.updateDeviceException(e.getMessage(), e.getStackTrace());
			throw(de);
        }
        
    	try {
    		// Finish up
    		disconnect();
    		mIsSerialPortReaderStarted = false;
		} catch (ExecutionException e) {
			ShimmerException de = generateException(ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_READER_STOP);
			de.updateDeviceException(e.getMessage(), e.getStackTrace());
			throw(de);
		}

    }
    
    @Override
	public boolean isSerialPortReaderStarted(){
		return mIsSerialPortReaderStarted;
	}
	
	public class ShimmerUartListener implements SerialPortEventListener {
//	    StringBuilder stringBuilder = new StringBuilder(128);
//	    
	    String mUniqueId = "";
	    boolean mIsParserBusy = false;

	    ShimmerUartListener(String uniqueID) {
	    	mUniqueId = uniqueID;
	    }

		@Override
		public void serialEvent(SerialPortEvent event) {
	        if (event.isRXCHAR()) {//If data is available
	        	int eventLength = event.getEventValue();
	        	//Check bytes count in the input buffer. 
//	            if (eventLength > 3) { // was 0 but at least 3 gives a little filter
	            if (!mIsParserBusy && eventLength>0) { // was 0 but at least 3 gives a little filter
	            	mIsParserBusy = true;
	            	serialPortRxEvent(eventLength);
//	            	mShimmerUart.serialPortRxEvent(eventLength);
	            	
	            	mIsParserBusy = false;
	            }
	        }
		}

	}
	
	@Override
	public void serialPortRxEvent(int byteLength) {
		//	consolePrintLn("Complete RX Received(" + packet.length + "):" + UtilShimmer.bytesToHexStringWithSpacesFormatted(packet));
		if(mShimmerSerialEventCallback!=null){
			mShimmerSerialEventCallback.serialPortRxEvent(byteLength);
		}
	}
	
	@Override
	public void registerSerialPortRxEventCallback(SerialPortListener shimmerSerialEventCallback) {
		mShimmerSerialEventCallback = shimmerSerialEventCallback;
	}

//	public void sendRxCallback(byte[] packet, long timestampMs){
////		consolePrintLn("Complete RX Received(" + packet.length + "):" + UtilShimmer.bytesToHexStringWithSpacesFormatted(packet));
//		if(mUartRxCallback!=null){
//			mUartRxCallback.newMsg(packet, timestampMs);
//		}
//	}
//
//	public void registerRxCallback(UartRxCallback uartRxCallback) {
//		mUartRxCallback = uartRxCallback;
//	}

	private void consolePrintLn(String string) {
		mUtilShimmer.consolePrintLn(mUniqueId + "\t" + string);
	}

	@Override
	public void setVerboseMode(boolean verboseMode, boolean isDebugMode) {
		mVerboseMode = verboseMode;
		mIsDebugMode = isDebugMode;
		mUtilShimmer.setVerboseMode(verboseMode);
	}

	@Override
	public boolean bytesAvailableToBeRead() throws ShimmerException {
		try {
			if(mSerialPort != null){
				if (mSerialPort.getInputBufferBytesCount()!=0){
					return true;
				}
			}

		} catch (SerialPortException e) {
			ShimmerException de = generateException(ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTION); 
        	de.updateDeviceException(e.getMessage(), e.getStackTrace());
			throw(de);
		}
		return false;
	}

	@Override
	public int availableBytes() throws ShimmerException {
		try {
			if(mSerialPort != null){
				return mSerialPort.getInputBufferBytesCount();
			}
			else{
				return 0;
			}
		} catch (SerialPortException ex) {
			ShimmerException de = generateException(ErrorCodesSerialPort.SHIMMERUART_COMM_ERR_PORT_EXCEPTION); 
        	de.updateDeviceException(ex.getMessage(), ex.getStackTrace());
			throw(de);
		}
	}

	@Override
	public boolean isConnected() {
		return mSerialPort.isOpened();
	}

	@Override
	public boolean isDisonnected() {
		return !mSerialPort.isOpened();
	}

	public SerialPort getSerialPort(){
		return mSerialPort;
	}

	public boolean isSetRtsOnConnect() {
		return setRtsOnConnect;
	}

	public void setRtsOnConnect(boolean setRtsOnConnect) {
		this.setRtsOnConnect = setRtsOnConnect;
	}

	public boolean isSetDtrOnConnect() {
		return setDtrOnConnect;
	}

	public void setDtrOnConnect(boolean setDtrOnConnect) {
		this.setDtrOnConnect = setDtrOnConnect;
	}

}
