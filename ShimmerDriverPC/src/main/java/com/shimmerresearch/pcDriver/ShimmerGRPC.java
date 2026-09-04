package com.shimmerresearch.pcDriver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;

import javax.swing.JFrame;

import com.google.protobuf.ByteString;
import com.shimmerresearch.algorithms.Filter;
import com.shimmerresearch.bluetooth.BluetoothProgressReportPerCmd;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.Configuration.Shimmer3;
import com.shimmerresearch.driver.shimmer2r3.ConfigByteLayoutShimmer3;
import com.shimmerresearch.driverUtilities.AssembleShimmerConfig;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.grpc.ShimmerBLEByteServerGrpc;
import com.shimmerresearch.grpc.ShimmerBLEGRPC.BluetoothState;
import com.shimmerresearch.grpc.ShimmerBLEGRPC.ObjectClusterByteArray;
import com.shimmerresearch.grpc.ShimmerBLEGRPC.Reply;
import com.shimmerresearch.grpc.ShimmerBLEGRPC.Request;
import com.shimmerresearch.grpc.ShimmerBLEGRPC.StateStatus;
import com.shimmerresearch.grpc.ShimmerBLEGRPC.StreamRequest;
import com.shimmerresearch.grpc.ShimmerBLEGRPC.WriteBytes;
import com.shimmerresearch.sensors.SensorPPG;
import com.shimmerresearch.sensors.kionix.SensorKionixAccel;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerDeviceCallbackAdapter;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driver.ThreadSafeByteFifoBuffer;
import com.shimmerresearch.exceptions.ShimmerException;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class ShimmerGRPC extends ShimmerBluetooth implements Serializable{
	String mMacAddress;
	transient ShimmerBLEByteServerGrpc.ShimmerBLEByteServerBlockingStub blockingStub;
	transient ManagedChannel channel;
	transient ThreadSafeByteFifoBuffer mBuffer;
	String mServerHost = "localhost";
	int mServerPort = 50052;
	protected transient ShimmerDeviceCallbackAdapter mDeviceCallbackAdapter = new ShimmerDeviceCallbackAdapter(this);
	/**
	 * 
	 */
	private static final long serialVersionUID = 5029128107276324956L;
	
	public ShimmerGRPC(String macAddress, String serverHost, int serverPort) {
		super();
		mServerHost = serverHost;
		mServerPort = serverPort;
		mMacAddress = macAddress;
		mMyBluetoothAddress = "";
		mUseProcessingThread = true;
		if (channel==null) {
			InitializeProcess();
		}
	}
	
	/**
	 *  This constructor is used for MacOS for BLE where the mFriendlyName is the BT device name (e.g. Shimmer3-6813). 
	 *  BT device name is used in place of COM Port which is Classic BT only
	 */
	public ShimmerGRPC(String macAddress, String mFriendlyName, String serverHost, int serverPort) {
		this(mFriendlyName, serverHost, serverPort);
		mMyBluetoothAddress = macAddress;
		mComPort = mFriendlyName;
	}
	
	public void InitializeProcess() {
		channel = ManagedChannelBuilder.forTarget(mServerHost + ":" + mServerPort)
				.usePlaintext() // Use plaintext communication (insecure for testing)
				.build();

		// Create a gRPC client stub
		blockingStub = ShimmerBLEByteServerGrpc.newBlockingStub(channel);
	}

	
	public static void main(String[] args) {
		JFrame frame = new JFrame();
		frame.getContentPane().setLayout(null);

		JButton btnNewButton = new JButton("Connect");
		
		final ShimmerGRPC shimmer = new ShimmerGRPC("E8EB1B713E36","localhost",50052); 
		//Use constructor below instead for MacOS, which uses the BT device name to connect, e.g. Shimmer3-6813
		//final ShimmerGRPC shimmer = new ShimmerGRPC("E8EB1B713E36","Shimmer3-3E36","localhost",50052);
		
		SensorDataReceived sdr = shimmer.new SensorDataReceived();
		sdr.setWaitForData(shimmer);

		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				shimmer.connect("","");
			}
		});
		btnNewButton.setBounds(10, 30, 89, 23);
		frame.getContentPane().add(btnNewButton);

		JButton btnNewButton_1 = new JButton("Disconnect");
		btnNewButton_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					shimmer.disconnect();
				} catch (ShimmerException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		});
		btnNewButton_1.setBounds(10, 143, 89, 23);
		frame.getContentPane().add(btnNewButton_1);

		JButton btnNewButton_2 = new JButton("start streaming");
		btnNewButton_2.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					shimmer.startStreaming();
				} catch (ShimmerException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		});
		btnNewButton_2.setBounds(10, 64, 89, 23);
		frame.getContentPane().add(btnNewButton_2);

		JButton btnNewButton_3 = new JButton("Stop Streaming");
		btnNewButton_3.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				shimmer.stopStreaming();
			}
		});
		btnNewButton_3.setBounds(10, 98, 89, 23);
		frame.getContentPane().add(btnNewButton_3);
		frame.setSize(500, 500);
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		// TODO Auto-generated method stub


	}

	public static String byteArrayToHexString(byte[] byteArray) {
		StringBuilder hexString = new StringBuilder();
		for (byte b : byteArray) {
			// Convert each byte to a two-digit hexadecimal representation
			hexString.append(String.format("%02X", b));
		}
		return hexString.toString();
	}

	@Override
	public void connect(String address, String bluetoothLibrary) {
		// Create a request message
		//E8EB1B713E36
		//e7452c6d6f14
		setBluetoothRadioState(BT_STATE.CONNECTING);
		Request request = Request.newBuilder().setName(mMacAddress).build();

		// Call the remote gRPC service method



		StreamRequest sreq = StreamRequest.newBuilder().setMessage(mMacAddress).build();
		ShimmerBLEByteServerGrpc.ShimmerBLEByteServerStub stubConnect = ShimmerBLEByteServerGrpc.newStub(channel);

		StreamObserver<StateStatus> responseObserverState = new StreamObserver<StateStatus>() {

			@Override
			public void onNext(StateStatus value) {
				// TODO Auto-generated method stub
				System.out.println(value.getMessage() + " " + value.getState().toString());
				if (value.getState().equals(BluetoothState.Connected)) {
					mBuffer = new ThreadSafeByteFifoBuffer(1000000);

					StreamObserver<ObjectClusterByteArray> responseObserver = new StreamObserver<ObjectClusterByteArray>() {

						@Override
						public void onNext(ObjectClusterByteArray value) {
							// TODO Auto-generated method stub
							byte[] bytesData = value.getBinaryData().toByteArray();
							//System.out.println(byteArrayToHexString(bytesData));
							try {
								mBuffer.write(bytesData);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}

						@Override
						public void onError(Throwable t) {
							// TODO Auto-generated method stub
							System.out.println("error 1");
						}

						@Override
						public void onCompleted() {
							// TODO Auto-generated method stub
							System.out.println("completed");
						}
					};
					ShimmerBLEByteServerGrpc.ShimmerBLEByteServerStub stub = ShimmerBLEByteServerGrpc.newStub(channel);
					stub.getDataStream(sreq, responseObserver);

					mIOThread = new IOThread();
					mIOThread.start();
					if (mUseProcessingThread){
						mPThread = new ProcessingThread();
						mPThread.start();
					}

					initialize();
				} else if (value.getState().equals(BluetoothState.Disconnected)) {
					stopAllTimers();
					setBluetoothRadioState(BT_STATE.DISCONNECTED);
				}
			}

			@Override
			public void onError(Throwable t) {
				// TODO Auto-generated method stub
				System.out.println("error 0");
			}

			@Override
			public void onCompleted() {
				// TODO Auto-generated method stub
				System.out.println("completed");
			}

		};
		stubConnect.connectShimmer(request, responseObserverState);

	}

	@Override
	protected boolean bytesAvailableToBeRead() {
		// TODO Auto-generated method stub
		if (mBuffer.size()>0) {
			return true;
		}
		return false;
	}

	@Override
	protected int availableBytes() {
		// TODO Auto-generated method stub
		return mBuffer.size();
	}

	@Override
	protected void writeBytes(byte[] data) {

		// Create a request message
		WriteBytes request = WriteBytes.newBuilder().setAddress(mMacAddress).setByteToWrite(ByteString.copyFrom(data)).build();

		try {
			// Call the remote gRPC service method
			Reply response = blockingStub.writeBytesShimmer(request);
			
			// Process the response
			System.out.println("Received: " + response.getMessage());
		} catch(StatusRuntimeException sre) {
			// Remote gRPC service is not available, so disconnect Shimmer device
			sre.printStackTrace();
			System.out.println("ERROR: Lost connection to GRPC Server");	
			connectionLost();
		}
	}

	@Override
	protected void stop() {
		// TODO Auto-generated method stub
		try {
			disconnect();
		} catch (ShimmerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	protected void sendProgressReport(BluetoothProgressReportPerCmd pr) {
		// TODO Auto-generated method stub
		mDeviceCallbackAdapter.sendProgressReport(pr);
	}

	@Override
	protected void isReadyForStreaming() {
		// TODO Auto-generated method stub
		mDeviceCallbackAdapter.isReadyForStreaming();
		restartTimersIfNull();
	}

	@Override
	protected void isNowStreaming() {
		// TODO Auto-generated method stub
		mDeviceCallbackAdapter.isNowStreaming();
	}

	@Override
	protected void hasStopStreaming() {
		// TODO Auto-generated method stub
		mDeviceCallbackAdapter.hasStopStreaming();
	}

	@Override
	protected void sendStatusMsgPacketLossDetected() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void inquiryDone() {
		// TODO Auto-generated method stub
		mDeviceCallbackAdapter.inquiryDone();
		isReadyForStreaming();

	}

	@Override
	protected void sendStatusMSGtoUI(String msg) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void printLogDataForDebugging(String msg) {
		// TODO Auto-generated method stub
		consolePrintLn(msg);
	}

	@Override
	protected void connectionLost() {
		// TODO Auto-generated method stub
		try {
			disconnect();
		} catch (ShimmerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		setBluetoothRadioState(BT_STATE.CONNECTION_LOST);
	}

	@Override
	public void startOperation(BT_STATE currentOperation){
		this.startOperation(currentOperation, 1);
		consolePrintLn(currentOperation + " START");
	}

	@Override
	public boolean setBluetoothRadioState(BT_STATE state) {
		boolean isChanged = super.setBluetoothRadioState(state);
		mDeviceCallbackAdapter.setBluetoothRadioState(state, isChanged);
		return isChanged;
	}

	@Override
	public void startOperation(BT_STATE currentOperation, int totalNumOfCmds){
		mDeviceCallbackAdapter.startOperation(currentOperation, totalNumOfCmds);

	}

	@Override
	public void finishOperation(BT_STATE state){
		mDeviceCallbackAdapter.finishOperation(state);

	}

	@Override
	protected void eventLogAndStreamStatusChanged(byte currentCommand) {
		// TODO Auto-generated method stub
		if(currentCommand==STOP_LOGGING_ONLY_COMMAND){
			//TODO need to query the Bluetooth connection here!
			if(mIsStreaming){
				setBluetoothRadioState(BT_STATE.STREAMING);
			}
			else if(isConnected()){
				setBluetoothRadioState(BT_STATE.CONNECTED);
			}
			else{
				setBluetoothRadioState(BT_STATE.DISCONNECTED);
			}
		}
		else{
			if(mIsStreaming && isSDLogging()){
				setBluetoothRadioState(BT_STATE.STREAMING_AND_SDLOGGING);
			}
			else if(mIsStreaming){
				setBluetoothRadioState(BT_STATE.STREAMING);
			}
			else if(isSDLogging()){
				setBluetoothRadioState(BT_STATE.SDLOGGING);
			}
			else{
				//				if(!isStreaming() && !isSDLogging() && isConnected()){
				if(!mIsStreaming && !isSDLogging() && isConnected() && mBluetoothRadioState!=BT_STATE.CONNECTED){
					setBluetoothRadioState(BT_STATE.CONNECTED);	
				}
				//				if(getBTState() == BT_STATE.INITIALISED){
				//					
				//				}
				//				else if(getBTState() != BT_STATE.CONNECTED){
				//					setState(BT_STATE.CONNECTED);
				//				}

				CallbackObject callBackObject = new CallbackObject(NOTIFICATION_SHIMMER_STATE_CHANGE, mBluetoothRadioState, getMacId(), getComPort());
				sendCallBackMsg(MSG_IDENTIFIER_STATE_CHANGE, callBackObject);
			}
		}
	}

	@Override
	protected void batteryStatusChanged() {
		// TODO Auto-generated method stub
		mDeviceCallbackAdapter.batteryStatusChanged();
	}

	@Override
	protected byte[] readBytes(int numberofBytes) {
		// TODO Auto-generated method stub
		try {
			return mBuffer.read(numberofBytes);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	protected byte readByte() {
		// TODO Auto-generated method stub
		try {
			return mBuffer.read();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;
	}

	@Override
	protected void dockedStateChange() {
		mDeviceCallbackAdapter.dockedStateChange();
	}

	@Override
	public ShimmerDevice deepClone() {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(this);

			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return (ShimmerDevice) ois.readObject();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	protected void interpretDataPacketFormat(Object object, COMMUNICATION_TYPE commType) {
		// TODO Auto-generated method stub

	}

	@Override
	public void createConfigBytesLayout() {
		// TODO Auto-generated method stub
		if (mShimmerVerObject.mHardwareVersion==HW_ID.UNKNOWN) {
			mConfigByteLayout = new ConfigByteLayoutShimmer3(getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal(), HW_ID.SHIMMER_3);
		} else {
			mConfigByteLayout = new ConfigByteLayoutShimmer3(getFirmwareIdentifier(), getFirmwareVersionMajor(), getFirmwareVersionMinor(), getFirmwareVersionInternal(), mShimmerVerObject.mHardwareVersion);
		}
	}

	@Override
	protected void dataHandler(ObjectCluster ojc) {
		// TODO Auto-generated method stub
		mDeviceCallbackAdapter.dataHandler(ojc);
	}

	@Override
	protected void processMsgFromCallback(ShimmerMsg shimmerMSG) {
		// TODO Auto-generated method stub
		System.out.println(shimmerMSG.mIdentifier);
	}

	@Override
	public void disconnect() throws ShimmerException {
		//		super.disconnect();
		stopAllTimers();
		// TODO Auto-generated method stub
		// Create a request message
		Request request = Request.newBuilder().setName(mMacAddress).build();

		try {
			// Call the remote gRPC service method
			Reply response = blockingStub.disconnectShimmer(request);
			// Process the response
			System.out.println("Received: " + response.getMessage());
		} catch(Exception e) {
			e.printStackTrace();
		}

		closeConnection();
		setBluetoothRadioState(BT_STATE.DISCONNECTED);
	}

	private void closeConnection() {
	    try {
	        // closeConnection() is unsynchronized and can be entered concurrently - once from
	        // connectionLost() running on the IOThread itself, and once from a user-triggered
	        // disconnect() on another thread. Snapshot the fields into locals before touching
	        // them so a concurrent caller nulling the field out mid-method can't turn a passed
	        // null-check into an NPE further down (see ShimmerPC.closeConnection() for the
	        // same fix).
	        IOThread ioThread = mIOThread;
	        if (ioThread != null) {
	            ioThread.stop = true;
	            // Skip the join when closeConnection() is reached from the IOThread itself -
	            // a self-join can never succeed and would just burn the full timeout. The
	            // interrupt() call moves inside this guard too: calling it unconditionally
	            // would mark the *current* thread interrupted in the self-close case.
	            if (Thread.currentThread() != ioThread) {
	                // Interrupt before joining: run() now exits promptly on
	                // Thread.currentThread().isInterrupted(), and any idle Thread.sleep()
	                // it's waiting in will wake immediately - making shutdown faster and
	                // more deterministic than waiting out the bounded join first.
	                ioThread.interrupt();
	                try {
	                    ioThread.join(2000); // Bounded wait so this can't block indefinitely
	                } catch (InterruptedException e) {
	                    Thread.currentThread().interrupt();
	                }
	                if (ioThread.isAlive()) {
	                    // Still not terminated after the interrupt + bounded join.
	                    consolePrintLn("Warning: IOThread did not terminate within join timeout");
	                }
	            }
	            mIOThread = null;

	            if (mUseProcessingThread) {
	                ProcessingThread pThread = mPThread;
	                if (pThread != null) {
	                    pThread.stop = true;
	                    if (Thread.currentThread() != pThread) {
	                        // Interrupt before joining - see the IOThread teardown above.
	                        pThread.interrupt();
	                        try {
	                            pThread.join(2000);
	                        } catch (InterruptedException e) {
	                            Thread.currentThread().interrupt();
	                        }
	                        if (pThread.isAlive()) {
	                            consolePrintLn("Warning: ProcessingThread did not terminate within join timeout");
	                        }
	                    }
	                }
	                mPThread = null;
	            }
	        }
	        mIsStreaming = false;
	        mIsInitialised = false;

	        setBluetoothRadioState(BT_STATE.DISCONNECTED);
	    } catch (Exception ex) {
	        consolePrintException(ex.getMessage(), ex.getStackTrace());
	        setBluetoothRadioState(BT_STATE.DISCONNECTED);
	    }
	}
	
	//Need to override here because ShimmerDevice class uses a different map
	@Override
	public String getSensorLabel(int sensorKey) {
		//TODO 2017-08-03 MN: super does this but in a different way, don't know is either is better
		super.getSensorLabel(sensorKey);
		SensorDetails sensor = mSensorMap.get(sensorKey);
		if(sensor!=null){
			return sensor.mSensorDetailsRef.mGuiFriendlyLabel;
		}
		return null;
	}
	public class SensorDataReceived extends BasicProcessWithCallBack{

		@Override
		protected void processMsgFromCallback(ShimmerMsg shimmerMSG) {
			// TODO Auto-generated method stub
			System.out.println(shimmerMSG.mIdentifier);


			// TODO Auto-generated method stub

			// TODO Auto-generated method stub
			int ind = shimmerMSG.mIdentifier;

			Object object = (Object) shimmerMSG.mB;

			if (ind == ShimmerPC.MSG_IDENTIFIER_STATE_CHANGE) {
				CallbackObject callbackObject = (CallbackObject)object;

				if (callbackObject.mState == BT_STATE.CONNECTING) {
				} else if (callbackObject.mState == BT_STATE.CONNECTED) {} else if (callbackObject.mState == BT_STATE.DISCONNECTED
						//						|| callbackObject.mState == BT_STATE.NONE
						|| callbackObject.mState == BT_STATE.CONNECTION_LOST){

				}
			} else if (ind == ShimmerPC.MSG_IDENTIFIER_NOTIFICATION_MESSAGE) {
				CallbackObject callbackObject = (CallbackObject)object;
				int msg = callbackObject.mIndicator;
				if (msg== ShimmerPC.NOTIFICATION_SHIMMER_FULLY_INITIALIZED){}
				if (msg == ShimmerPC.NOTIFICATION_SHIMMER_STOP_STREAMING) {

				} else if (msg == ShimmerPC.NOTIFICATION_SHIMMER_START_STREAMING) {

				} else {}
			} else if (ind == ShimmerPC.MSG_IDENTIFIER_DATA_PACKET) {

				double accelX = 0;
				double accelY = 0;
				double accelZ = 0;
				FormatCluster formatx;
				FormatCluster formaty;
				FormatCluster formatz;

				int INVALID_RESULT = -1;

				ObjectCluster objc = (ObjectCluster) shimmerMSG.mB;

				Collection<FormatCluster> adcFormats = objc.getCollectionOfFormatClusters(SensorKionixAccel.ObjectClusterSensorName.ACCEL_LN_X);
				formatx = ((FormatCluster)ObjectCluster.returnFormatCluster(adcFormats, CHANNEL_TYPE.CAL.toString())); // retrieve the calibrated data

				adcFormats = objc.getCollectionOfFormatClusters(SensorKionixAccel.ObjectClusterSensorName.ACCEL_LN_Y);
				formaty = ((FormatCluster)ObjectCluster.returnFormatCluster(adcFormats, CHANNEL_TYPE.CAL.toString())); // retrieve the calibrated data

				adcFormats = objc.getCollectionOfFormatClusters(SensorKionixAccel.ObjectClusterSensorName.ACCEL_LN_Z);
				formatz = ((FormatCluster)ObjectCluster.returnFormatCluster(adcFormats, CHANNEL_TYPE.CAL.toString())); // retrieve the calibrated data

				if(formatx != null) {
					System.out.println("X:"+formatx.mData +" Y:"+formaty.mData+" Z:"+formatz.mData);

				}
				else {
					System.out.println("ERROR! FormatCluster is Null!");
				}

			} else if (ind == ShimmerPC.MSG_IDENTIFIER_PACKET_RECEPTION_RATE_OVERALL) {

			}





		}

	}

}
