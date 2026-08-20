package com.shimmerresearch.simpleexamples;

import javax.swing.JFrame;

import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.comms.radioProtocol.ShimmerLiteProtocolInstructionSet.LiteProtocolInstructionSet.InstructionsGet;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driver.Configuration.COMMUNICATION_TYPE;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ShimmerObject;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driverUtilities.AssembleShimmerConfig;
import com.shimmerresearch.driverUtilities.BluetoothDeviceDetails;
import com.shimmerresearch.driverUtilities.SensorDetails;
import com.shimmerresearch.driverUtilities.ShimmerVerDetails.HW_ID;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.grpc.ShimmerGRPC;
import com.shimmerresearch.guiUtilities.configuration.EnableLowPowerModeDialog;
import com.shimmerresearch.guiUtilities.configuration.EnableSensorsDialog;
import com.shimmerresearch.guiUtilities.configuration.SensorConfigDialog;
import com.shimmerresearch.guiUtilities.configuration.SignalsToPlotDialog;
import com.shimmerresearch.guiUtilities.plot.BasicPlotManagerPC;
import com.shimmerresearch.pcDriver.ShimmerPC;
import com.shimmerresearch.sensors.lis2dw12.SensorLIS2DW12;
import com.shimmerresearch.sensors.lisxmdl.SensorLIS2MDL;
import com.shimmerresearch.sensors.lsm6dsv.SensorLSM6DSV;
import com.shimmerresearch.tools.LoggingPC;
import com.shimmerresearch.tools.bluetooth.BasicShimmerBluetoothManagerPc;
import com.shimmerresearch.verisense.VerisenseDevice;
import com.shimmerresearch.verisense.communication.SyncProgressDetails;
import com.shimmerresearch.verisense.communication.VerisenseProtocolByteCommunication;
import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import info.monitorenter.gui.chart.Chart2D;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.ActionEvent;
import javax.swing.JMenu;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.Image;
import javax.swing.JComboBox;

public class SensorMapsExample extends BasicProcessWithCallBack {
	
	private JFrame frame;
	private JTextField textField;
	JTextPane textPaneStatus;
	static JTextArea textAreaDeviceSpec;
	static ShimmerDevice shimmerDevice;
	static BasicShimmerBluetoothManagerPc btManager = new BasicShimmerBluetoothManagerPc();
	BasicPlotManagerPC plotManager = new BasicPlotManagerPC();
	static String btComport;
	final Chart2D mChart = new Chart2D();
	private JTextField textFieldSamplingRate;
	static JLabel lblPRR;
	static final String PREFIX_COM = "COM";
	static final String PREFIX_DEV = "dev";
	JComboBox<String> comboBox;
	JLabel lblFilePath;
	LoggingPC lpc;
	JCheckBox chckbxWriteDataToFile;
	String[] options = {"Shimmer3", "Verisense"};
	/**
	 * Initialize the contents of the frame
	 * @wbp.parser.entryPoint
	 */
	public void initialize() {
		//Set the default port to 500XX, this is used if the software cannot start the gRPC server automatically
		if(UtilShimmer.isOsMac()) {
			BasicShimmerBluetoothManagerPc.mGRPCPort = 50000; //the bundled /libs/ShimmerBLEGrpc/ for Mac uses port 50000
		} else {
			BasicShimmerBluetoothManagerPc.mGRPCPort = 50052;
		}
		
		frame = new JFrame("Shimmer SensorMaps Example");
		frame.setBounds(100, 100, 1200, 591);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(null);
		
		JLabel lblSetComPort = new JLabel("Windows: Set COM Port (e.g. COMX or /dev/rfcommX) or Mac Address (e.g. XX:XX:XX:XX:XX:XX , note this requires grpc and BLE)"
				+ " | MacOS: Set device name (e.g. Shimmer3-XXXX) - gRPC/BLE");
		lblSetComPort.setBounds(10, 25, 1200, 23);
		frame.getContentPane().add(lblSetComPort);
		
		textField = new JTextField();
		textField.setToolTipText("for example COM1, COM2, etc");
		textField.setBounds(10, 56, 144, 29);
		frame.getContentPane().add(textField);
		textField.setColumns(10);
		
		JButton btnConnect = new JButton("CONNECT");
		btnConnect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				
				btComport = textField.getText();
				if (btComport.length()==17 || (btComport.startsWith("Shimmer") && btComport.contains("-"))) {
					//Mac address			or	device name (e.g. Shimmer3-6813)
					if (comboBox.getSelectedItem().equals("Shimmer3")) {
						if(UtilShimmer.isOsMac()) { //MacOS uses the device name as both com port and friendly name
							BluetoothDeviceDetails bdd = new BluetoothDeviceDetails("", btComport, btComport);
							btManager.connectShimmer3BleGrpc(bdd);
						} else {
							BluetoothDeviceDetails bdd = new BluetoothDeviceDetails("", btComport, "Shimmer3BLE");
							btManager.connectShimmer3BleGrpc(bdd);	
						}
					} else if (comboBox.getSelectedItem().equals("Verisense")) {
						BluetoothDeviceDetails bdd = new BluetoothDeviceDetails(btComport, btComport, "ShimmerGRPC");
						btManager.connectVerisenseDevice(bdd);	
					}
				} else {
					//COM port
					btManager.connectShimmerThroughCommPort(btComport);
				}
			}
		});
		btnConnect.setToolTipText("attempt connection to Shimmer device");
		btnConnect.setBounds(290, 56, 199, 31);
		frame.getContentPane().add(btnConnect);
		
		JButton btnDisconnect = new JButton("DISCONNECT");
		btnDisconnect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				btManager.disconnectShimmer(btComport);
				
			}
		});
		btnDisconnect.setToolTipText("disconnect from Shimmer device");
		btnDisconnect.setBounds(520, 56, 187, 31);
		frame.getContentPane().add(btnDisconnect);
		
		JLabel lblShimmerStatus = new JLabel("Shimmer Status");
		lblShimmerStatus.setBounds(10, 139, 144, 23);
		frame.getContentPane().add(lblShimmerStatus);
		
		textPaneStatus = new JTextPane();
		textPaneStatus.setBounds(10, 181, 144, 36);
		frame.getContentPane().add(textPaneStatus);
		
		JMenuBar menuBar = new JMenuBar();
		menuBar.setBounds(0, 0, 1184, 23);
		frame.getContentPane().add(menuBar);
		
		JMenu mnTools = new JMenu("Tools");
		menuBar.add(mnTools);
		
		JMenuItem mntmSelectSensors = new JMenuItem("Select sensors");
		mntmSelectSensors.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				ShimmerDevice SD = btManager.getShimmerDeviceBtConnected(btComport);
				boolean connected = false;
				if (SD!=null) {
					if (SD instanceof VerisenseDevice) {
						VerisenseDevice vd = (VerisenseDevice) SD;
						if (vd.getBluetoothRadioState().equals(BT_STATE.CONNECTED)){
							connected = true;
						}
						
			            
					} else {
						ShimmerBluetooth sb = (ShimmerBluetooth) SD;
						if (sb.getBluetoothRadioState().equals(BT_STATE.CONNECTED)){
							connected = true;
						}
					}
				}
				
				//Ensure the Shimmer is not streaming or SD logging before configuring it
				if(connected) {
						EnableSensorsDialog sensorsDialog = new EnableSensorsDialog(((ShimmerDevice)btManager.getShimmerDeviceBtConnected(btComport)),btManager);
						sensorsDialog.showDialog();
					
				} else {
					JOptionPane.showMessageDialog(frame, "Device not in a connected state!", "Info", JOptionPane.WARNING_MESSAGE);
				}
				
//				EnableSensorsDialog sensorsDialog = new EnableSensorsDialog(shimmerDevice);
//				sensorsDialog.initialize();
			}
		});
		mnTools.add(mntmSelectSensors);
		
		JMenuItem mntmDeviceConfiguration = new JMenuItem("Sensor configuration");
		mntmDeviceConfiguration.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				ShimmerDevice SD = btManager.getShimmerDeviceBtConnected(btComport);
				boolean connected = false;
				if (SD!=null) {
					if (SD instanceof VerisenseDevice) {
						VerisenseDevice vd = (VerisenseDevice) SD;
						if (vd.getBluetoothRadioState().equals(BT_STATE.CONNECTED)){
							connected = true;
						}
						
			            
					} else {
						ShimmerBluetooth sb = (ShimmerBluetooth) SD;
						if (sb.getBluetoothRadioState().equals(BT_STATE.CONNECTED)){
							connected = true;
						}
					}
				}
				if(connected) {
					SensorConfigDialog configDialog = new SensorConfigDialog(((ShimmerDevice)btManager.getShimmerDeviceBtConnected(btComport)),btManager);
					configDialog.showDialog();
				
			} else {
				JOptionPane.showMessageDialog(frame, "Device not in a connected state!", "Info", JOptionPane.WARNING_MESSAGE);
			}	
				
			}
		});
		mnTools.add(mntmDeviceConfiguration);
		
		JPanel plotPanel = new JPanel();
		plotPanel.setBounds(10, 269, 1164, 272);
		frame.getContentPane().add(plotPanel);
		plotPanel.setLayout(null);
		

		mChart.setLocation(12, 13);
		mChart.setSize(1142, 246);
		plotPanel.add(mChart);
		plotManager.addChart(mChart);
		
		JMenuItem mntmSignalsToPlot = new JMenuItem("Signals to plot");
		mntmSignalsToPlot.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mChart.removeAll();
				plotManager.removeAllSignals();
				SignalsToPlotDialog signalsToPlotDialog = new SignalsToPlotDialog();
				signalsToPlotDialog.initialize(((ShimmerDevice)btManager.getShimmerDeviceBtConnected(btComport)), plotManager, mChart);
			}
		});
		mnTools.add(mntmSignalsToPlot);
	
		
		JMenuItem mntmLowPowerMode = new JMenuItem("Low Power Mode");
		mntmLowPowerMode.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				ShimmerDevice SD = btManager.getShimmerDeviceBtConnected(btComport);
				boolean connected = false;
				if (SD!=null) {
					if (SD instanceof VerisenseDevice) {
						VerisenseDevice vd = (VerisenseDevice) SD;
						if (vd.getBluetoothRadioState().equals(BT_STATE.CONNECTED)){
							connected = true;
						}
						
			            
					} else {
						ShimmerBluetooth sb = (ShimmerBluetooth) SD;
						if (sb.getBluetoothRadioState().equals(BT_STATE.CONNECTED)){
							connected = true;
						}
					}
				}
				if(connected) {
					EnableLowPowerModeDialog lpModeDialog = new EnableLowPowerModeDialog(((ShimmerDevice)btManager.getShimmerDeviceBtConnected(btComport)),btManager);
					lpModeDialog.showDialog();
				
			} else {
				JOptionPane.showMessageDialog(frame, "Device not in a connected state!", "Info", JOptionPane.WARNING_MESSAGE);
			}	
				
			}
		});
		mnTools.add(mntmLowPowerMode);
		
		JButton btnStartStreaming = new JButton("START STREAMING");
		btnStartStreaming.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (chckbxWriteDataToFile.isSelected()) {
					String ts = Long.toString(System.currentTimeMillis());
                	lpc = new LoggingPC(ts+"_test.csv");
				}
				
				try {
					ShimmerDevice device = btManager.getShimmerDeviceBtConnectedFromMac(btComport);
					if (btComport.contains(PREFIX_COM) || btComport.contains(PREFIX_DEV)) {
						device = btManager.getShimmerDeviceBtConnected(btComport);
					} else {
						device = btManager.getShimmerDeviceBtConnectedFromMac(btComport);
					}
					if (device instanceof VerisenseDevice) {
						((VerisenseDevice)device).startStreaming();
					}else{
						((ShimmerBluetooth)device).startStreaming();
					}
					
				} catch (ShimmerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		});
		btnStartStreaming.setBounds(185, 181, 199, 31);
		frame.getContentPane().add(btnStartStreaming);
		
		JButton btnStopStreaming = new JButton("STOP STREAMING");
		btnStopStreaming.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				if (lpc!=null) {
					lpc.closeFile();
					lpc=null;
				}
				
				ShimmerDevice device = btManager.getShimmerDeviceBtConnected(btComport);
				try {
				if (device instanceof VerisenseDevice) {
					
					((VerisenseDevice)device).stopStreaming();

				}else{
					((ShimmerBluetooth)device).stopStreaming();
				}
				
				} catch (ShimmerException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		});
		btnStopStreaming.setBounds(415, 181, 187, 31);
		frame.getContentPane().add(btnStopStreaming);
		
		JButton btnTakeSnapshot = new JButton("Take Snapshot");
		btnTakeSnapshot.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
							
			    try {

			        File outputfile = new File("saved.png");
			        ImageIO.write(mChart.snapShot(), "png", outputfile);
			    } catch (IOException e) {
			   
			    }
			}
		});
		btnTakeSnapshot.setBounds(625, 181, 187, 31);
		frame.getContentPane().add(btnTakeSnapshot);
		
		textFieldSamplingRate = new JTextField();
		textFieldSamplingRate.setToolTipText("for example COM1, COM2, etc");
		textFieldSamplingRate.setColumns(10);
		textFieldSamplingRate.setBounds(10, 99, 144, 29);
		frame.getContentPane().add(textFieldSamplingRate);
		
		JButton btnWriteSamplingRate = new JButton("Write Sampling Rate (Shimmer3)");
		btnWriteSamplingRate.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ShimmerDevice sd = ((ShimmerDevice)btManager.getShimmerDeviceBtConnected(btComport));
				ShimmerDevice cloned = sd.deepClone();
				cloned.setSamplingRateShimmer(Double.parseDouble(textFieldSamplingRate.getText()));
				AssembleShimmerConfig.generateSingleShimmerConfig(cloned, COMMUNICATION_TYPE.BLUETOOTH);
                btManager.configureShimmer(cloned);
			}
		});
		btnWriteSamplingRate.setToolTipText("attempt connection to Shimmer device");
		btnWriteSamplingRate.setBounds(164, 98, 220, 31);
		frame.getContentPane().add(btnWriteSamplingRate);
		
		lblPRR = new JLabel("Packet Reception Rate: ");
		lblPRR.setBounds(10, 228, 372, 14);
		frame.getContentPane().add(lblPRR);
        
		DefaultComboBoxModel<String> comboModel = new DefaultComboBoxModel<String>(options);
        comboBox = new JComboBox<>(comboModel);
        comboBox.setBounds(164, 59, 116, 22);
		frame.getContentPane().add(comboBox);

		JLabel lblDeviceSpec = new JLabel("Device Specifications");
		lblDeviceSpec.setBounds(830, 34, 200, 16);
		frame.getContentPane().add(lblDeviceSpec);

		textAreaDeviceSpec = new JTextArea();
		textAreaDeviceSpec.setEditable(false);
		textAreaDeviceSpec.setLineWrap(false);
		textAreaDeviceSpec.setText("Not connected");
		JScrollPane deviceSpecScrollPane = new JScrollPane(textAreaDeviceSpec);
		deviceSpecScrollPane.setBounds(830, 52, 354, 122);
		frame.getContentPane().add(deviceSpecScrollPane);
		
		JButton btnNewButton = new JButton("Sync (Verisense)");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				VerisenseDevice device = (VerisenseDevice)btManager.getShimmerDeviceBtConnected(btComport);
				try {
				
					((VerisenseProtocolByteCommunication)device.getMapOfVerisenseProtocolByteCommunication().get(COMMUNICATION_TYPE.BLUETOOTH)).readLoggedData();
            

				
				
				} catch (ShimmerException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		});
		btnNewButton.setBounds(415, 97, 187, 31);
		frame.getContentPane().add(btnNewButton);
		
		JButton btnEraseDataverisense = new JButton("Erase Data (Verisense)");
		btnEraseDataverisense.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				VerisenseDevice device = (VerisenseDevice)btManager.getShimmerDeviceBtConnected(btComport);
				try {
				
					((VerisenseProtocolByteCommunication)device.getMapOfVerisenseProtocolByteCommunication().get(COMMUNICATION_TYPE.BLUETOOTH)).eraseDataTask();
					
				} catch (ShimmerException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} 
			}
		});
		btnEraseDataverisense.setBounds(632, 97, 187, 31);
		frame.getContentPane().add(btnEraseDataverisense);
		
		lblFilePath = new JLabel(" ");
		lblFilePath.setBounds(12, 253, 611, 14);
		frame.getContentPane().add(lblFilePath);
		
		chckbxWriteDataToFile = new JCheckBox("Write Data to File");
		chckbxWriteDataToFile.setBounds(829, 185, 135, 23);
		chckbxWriteDataToFile.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if(e.getStateChange() == ItemEvent.SELECTED) {

                } else {

                }
            }
        });
		frame.getContentPane().add(chckbxWriteDataToFile);
		
		JButton btnStartSDLogging = new JButton("START SD");
		btnStartSDLogging.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ShimmerDevice device = btManager.getShimmerDeviceBtConnectedFromMac(btComport);
				if (btComport.contains(PREFIX_COM) || btComport.contains(PREFIX_DEV)) {
					device = btManager.getShimmerDeviceBtConnected(btComport);
				} else {
					device = btManager.getShimmerDeviceBtConnectedFromMac(btComport);
				}
				if (device instanceof VerisenseDevice) {
					((VerisenseDevice)device).startSDLogging();
				}else{
					((ShimmerBluetooth)device).startSDLogging();
				}
			}
		});
		btnStartSDLogging.setBounds(965, 181, 106, 31);
		frame.getContentPane().add(btnStartSDLogging);
		
		JButton btnStopSd = new JButton("STOP SD");
		btnStopSd.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

				ShimmerDevice device = btManager.getShimmerDeviceBtConnectedFromMac(btComport);
				if (btComport.contains(PREFIX_COM) || btComport.contains(PREFIX_DEV)) {
					device = btManager.getShimmerDeviceBtConnected(btComport);
				} else {
					device = btManager.getShimmerDeviceBtConnectedFromMac(btComport);
				}
				if (device instanceof VerisenseDevice) {
					((VerisenseDevice)device).stopSDLogging();
				}else{
					((ShimmerBluetooth)device).stopSDLogging();
				}
			
			}
		});
		btnStopSd.setBounds(1075, 181, 107, 31);
		frame.getContentPane().add(btnStopSd);
				
		plotManager.setTitle("Plot");		
	}

	public static void main(String args[]) {
		SensorMapsExample s = new SensorMapsExample();
		s.initialize();
		s.frame.setVisible(true);
		s.setWaitForData(btManager.callBackObject);		
		//s.setWaitForData(shimmer);
	}
	
	static Timer timer = null;
    static class PRRTask extends TimerTask {
        @Override
        public void run() {
        	ShimmerDevice sd = ((ShimmerDevice)btManager.getShimmerDeviceBtConnected(btComport));
        	if (sd!=null) {
        		lblPRR.setText("Packet Reception Rate: " +Double.toString(sd.getPacketReceptionRateOverall()));
        	}
        }
    }
	
    
	
	/**
	 * Builds a multi-line summary of the connected device's specifications
	 * (HW/FW versions, expansion/daughter board, MAC, etc.) for display on the UI.
	 */
	static String buildDeviceSpecString(ShimmerDevice device) {
		if (device == null) {
			return "Not connected";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("Name:\t").append(device.getShimmerUserAssignedName()).append("\n");
		sb.append("MAC:\t").append(device.getMacId()).append("\n");
		sb.append("HW:\t").append(device.getHardwareVersionParsed())
				.append(" (id ").append(device.getHardwareVersion()).append(")\n");
		sb.append("FW:\t").append(device.getFirmwareVersionParsed()).append("\n");

		if (device instanceof VerisenseDevice) {
			return sb.toString();
		}

		// Shimmer3/3R specifics: daughter (expansion) board + BMP581 eligibility
		sb.append("Exp board:\t").append(device.getExpansionBoardParsedWithVer()).append("\n");
		sb.append("Exp board id:\t").append(device.getExpansionBoardId())
				.append(", rev ").append(device.getExpansionBoardRev())
				.append(".").append(device.getExpansionBoardRevSpecial()).append("\n");
		sb.append("Sampling rate:\t").append(device.getSamplingRateShimmer()).append(" Hz\n");

		if (device instanceof ShimmerObject) {
			sb.append("BMP581 supported:\t").append(((ShimmerObject) device).isSupportedBmp581()).append("\n");
		}
		return sb.toString();
	}

	/** Refresh the on-screen device spec panel from the currently connected device. */
	static void updateDeviceSpec() {
		if (textAreaDeviceSpec == null) {
			return;
		}
		final ShimmerDevice device = btManager.getShimmerDeviceBtConnected(btComport);
		final String specString = buildDeviceSpecString(device);
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (textAreaDeviceSpec == null) {
					return;
				}
				textAreaDeviceSpec.setText(specString);
				textAreaDeviceSpec.setCaretPosition(0);
			}
		});
	}

	@Override
	protected void processMsgFromCallback(ShimmerMsg shimmerMSG) {
		// TODO Auto-generated method stub

		// TODO Auto-generated method stub
		  int ind = shimmerMSG.mIdentifier;

		  Object object = (Object) shimmerMSG.mB;

		if (ind == ShimmerPC.MSG_IDENTIFIER_STATE_CHANGE) {
			CallbackObject callbackObject = (CallbackObject)object;
			
			if (callbackObject.mState == BT_STATE.CONNECTING) {
				textPaneStatus.setText("connecting...");
			} else if (callbackObject.mState == BT_STATE.CONNECTED) {
				if (timer!=null){
                    timer.cancel();
                    timer = new Timer();
                }
				textPaneStatus.setText("connected");
				updateDeviceSpec();

				//shimmer = (ShimmerPC) btManager.getShimmerDeviceBtConnected(btComport);
//				shimmerDevice = btManager.getShimmerDeviceBtConnected(btComport);
				//shimmer.startStreaming();
			}  else if (callbackObject.mState == BT_STATE.STREAMING_LOGGED_DATA) {
				if (timer!=null){
                    timer.cancel();
                    timer = new Timer();
                }
				textPaneStatus.setText("Syncing");
				//shimmer = (ShimmerPC) btManager.getShimmerDeviceBtConnected(btComport);
//				shimmerDevice = btManager.getShimmerDeviceBtConnected(btComport);
				//shimmer.startStreaming();
			} else if (callbackObject.mState == BT_STATE.DISCONNECTED
//					|| callbackObject.mState == BT_STATE.NONE
					|| callbackObject.mState == BT_STATE.CONNECTION_LOST){
				if (timer!=null) {
                    timer.cancel();
                    timer = null;
                }
				textPaneStatus.setText("disconnected");
				if (textAreaDeviceSpec != null) {
					textAreaDeviceSpec.setText("Not connected");
				}
			}else if (callbackObject.mState == BT_STATE.STREAMING) {
				if (timer!=null){
                    timer.cancel();
                    timer = new Timer();
                } else {
                    timer = new Timer();
                }
                // Schedule a task to be executed after a delay of 2 seconds
                timer.schedule(new PRRTask(), 0 , 2000);
			
			}
		} else if (ind == ShimmerPC.MSG_IDENTIFIER_NOTIFICATION_MESSAGE) {
			CallbackObject callbackObject = (CallbackObject)object;
			int msg = callbackObject.mIndicator;
			if (msg== ShimmerPC.NOTIFICATION_SHIMMER_FULLY_INITIALIZED){
				textPaneStatus.setText("device fully initialized");
				updateDeviceSpec();
				ShimmerDevice device = btManager.getShimmerDeviceBtConnectedFromMac(btComport);
				if (btComport.contains(PREFIX_COM) || btComport.contains(PREFIX_DEV)) {
					device = btManager.getShimmerDeviceBtConnected(btComport);
				} else {
					device = btManager.getShimmerDeviceBtConnectedFromMac(btComport);
				}
				
				if (device instanceof VerisenseDevice) {
					
					textFieldSamplingRate.setText(Double.toString(((VerisenseDevice)device).getSamplingRateShimmer()));
				}else{
					textFieldSamplingRate.setText(Double.toString(((ShimmerBluetooth)device).getSamplingRateShimmer()));
				}
			}
			if (msg == ShimmerPC.NOTIFICATION_SHIMMER_STOP_STREAMING) {
				textPaneStatus.setText("device stopped streaming");
			} else if (msg == ShimmerPC.NOTIFICATION_SHIMMER_START_STREAMING) {
				textPaneStatus.setText("device streaming");
			} else {}
		} else if (ind == ShimmerPC.MSG_IDENTIFIER_DATA_PACKET) {
			System.out.println("Shimmer MSG_IDENTIFIER_DATA_PACKET");
			ObjectCluster objc = (ObjectCluster) shimmerMSG.mB;
			if (lpc!=null) {
				lpc.logData(objc);
			}
			try {
				plotManager.filterDataAndPlot(objc);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		} else if (ind == ShimmerPC.MSG_IDENTIFIER_PACKET_RECEPTION_RATE_OVERALL) {
			
		} else if(ind == ShimmerPC.MSG_IDENTIFIER_SYNC_PROGRESS){
            SyncProgressDetails mDetails = (SyncProgressDetails)((CallbackObject)shimmerMSG.mB).mMyObject;
            String progress = "Payload Index : " + Double.toString(mDetails.mPayloadIndex) + ", Transfer Rate (bytes/s) : " + Double.toString(mDetails.mTransferRateBytes) ;
            lblPRR.setText(progress);
            lblFilePath.setText(mDetails.mBinFilePath);
        }
	
	
		
		
	}
}

