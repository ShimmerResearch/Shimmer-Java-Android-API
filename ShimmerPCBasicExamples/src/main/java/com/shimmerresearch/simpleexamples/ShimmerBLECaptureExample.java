package com.shimmerresearch.simpleexamples;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.driver.BasicProcessWithCallBack;
import com.shimmerresearch.driver.CallbackObject;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerMsg;
import com.shimmerresearch.driver.ble.nativeble.BleCentral;
import com.shimmerresearch.driver.ble.nativeble.BleScanListener;
import com.shimmerresearch.driver.ble.nativeble.NativeBleDevice;
import com.shimmerresearch.driver.ble.nativeble.NativeBleException;
import com.shimmerresearch.driver.ble.nativeble.NativeBleRadio;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.exceptions.ShimmerException;
import com.shimmerresearch.guiUtilities.configuration.EnableSensorsDialog;
import com.shimmerresearch.guiUtilities.configuration.SensorConfigDialog;
import com.shimmerresearch.guiUtilities.configuration.SignalsToPlotDialog;
import com.shimmerresearch.guiUtilities.plot.BasicPlotManagerPC;
import com.shimmerresearch.pcDriver.ShimmerBLENative;

import info.monitorenter.gui.chart.Chart2D;

/**
 * Shimmer Capture-style example for Shimmer3 and Shimmer3R over BLE, using the in-process native
 * library ({@link ShimmerBLENative}) rather than the gRPC BLE server.
 * <p>
 * Scan, connect, configure, stream with a live plot and packet reception rate, log to CSV,
 * disconnect.
 * <p>
 * Needs the native library: run {@code ./gradlew buildNative} in ShimmerDriverPC first, or pass
 * {@code -Dshimmer.ble.lib=<path to library>}. On macOS, run from Terminal and allow Terminal to use
 * Bluetooth (System Settings, Privacy &amp; Security, Bluetooth).
 */
public class ShimmerBLECaptureExample {

	private final JFrame mFrame = new JFrame("Shimmer BLE Capture (native)");
	private final DefaultListModel<NativeBleDevice> mDeviceModel = new DefaultListModel<NativeBleDevice>();
	private final JList<NativeBleDevice> mDeviceList = new JList<NativeBleDevice>(mDeviceModel);
	private final JButton mBtnScan = new JButton("Scan");
	private final JButton mBtnConnect = new JButton("Connect");
	private final JButton mBtnDisconnect = new JButton("Disconnect");
	private final JButton mBtnSensors = new JButton("Enable sensors");
	private final JButton mBtnConfig = new JButton("Sensor config");
	private final JButton mBtnPlot = new JButton("Signals to plot");
	private final JButton mBtnStart = new JButton("Start streaming");
	private final JButton mBtnStop = new JButton("Stop streaming");
	private final JCheckBox mChkLog = new JCheckBox("Log to CSV");
	private final JLabel mLblAdapter = new JLabel("Loading native BLE library...");
	private final JLabel mLblState = new JLabel("Not connected");
	private final JLabel mLblStats = new JLabel(" ");

	private final Chart2D mChart = new Chart2D();
	private final BasicPlotManagerPC mPlotManager = new BasicPlotManagerPC();
	private final NativeBleBluetoothManager mConfigManager = new NativeBleBluetoothManager();
	private final CsvLog mCsvLog = new CsvLog();
	private final AtomicLong mPackets = new AtomicLong();

	private BleCentral mCentral;
	private volatile ShimmerBLENative mShimmer;
	private volatile double mPacketReceptionRate = Double.NaN;
	private boolean mScanning = false;
	private long mPacketsAtLastTick = 0;

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new ShimmerBLECaptureExample().show();
			}
		});
	}

	private void show() {
		buildUi();
		updateButtons();
		mFrame.setVisible(true);
		runInBackground("init", new Runnable() {
			public void run() {
				initCentral();
			}
		});
		new Timer(1000, e -> updateStats()).start();
	}

	private void buildUi() {
		JPanel connectionRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		connectionRow.add(mBtnScan);
		connectionRow.add(mBtnConnect);
		connectionRow.add(mBtnDisconnect);
		connectionRow.add(mLblAdapter);

		JPanel deviceRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		deviceRow.add(mBtnSensors);
		deviceRow.add(mBtnConfig);
		deviceRow.add(mBtnPlot);
		deviceRow.add(mBtnStart);
		deviceRow.add(mBtnStop);
		deviceRow.add(mChkLog);

		JPanel north = new JPanel();
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
		north.add(connectionRow);
		north.add(deviceRow);

		mDeviceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane devices = new JScrollPane(mDeviceList);
		devices.setPreferredSize(new Dimension(300, 400));
		devices.setBorder(BorderFactory.createTitledBorder("Shimmer3 / Shimmer3R devices"));

		JPanel south = new JPanel();
		south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
		south.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		south.add(mLblState);
		south.add(mLblStats);

		mPlotManager.addChart(mChart);

		mFrame.getContentPane().setLayout(new BorderLayout());
		mFrame.getContentPane().add(north, BorderLayout.NORTH);
		mFrame.getContentPane().add(devices, BorderLayout.WEST);
		mFrame.getContentPane().add(mChart, BorderLayout.CENTER);
		mFrame.getContentPane().add(south, BorderLayout.SOUTH);
		mFrame.setSize(1100, 700);
		mFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		mFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				shutdown();
			}
		});

		mBtnScan.addActionListener(e -> toggleScan());
		mBtnConnect.addActionListener(e -> connectSelected());
		mBtnDisconnect.addActionListener(e -> disconnect());
		mBtnSensors.addActionListener(e -> new EnableSensorsDialog(mShimmer, mConfigManager).showDialog());
		mBtnConfig.addActionListener(e -> new SensorConfigDialog(mShimmer, mConfigManager).showDialog());
		mBtnPlot.addActionListener(e -> new SignalsToPlotDialog().initialize(mShimmer, mPlotManager, mChart));
		mBtnStart.addActionListener(e -> startStreaming());
		mBtnStop.addActionListener(e -> stopStreaming());
	}

	private void initCentral() {
		try {
			mCentral = BleCentral.getDefault();
			String state = ((NativeBleRadio) mCentral.getRadio()).getAdapterState();
			mCentral.addScanListener(new BleScanListener() {
				public void onDeviceFound(final NativeBleDevice device) {
					if (device.getProfile() != null && device.getProfile().isShimmer3Family()) {
						SwingUtilities.invokeLater(() -> addOrUpdateDevice(device));
					}
				}
			});
			onUi(() -> {
				mLblAdapter.setText("Bluetooth adapter: " + state);
				updateButtons();
			});
		} catch (NativeBleException e) {
			onUi(() -> {
				mLblAdapter.setText("Native BLE unavailable");
				JOptionPane.showMessageDialog(mFrame, e.getMessage(), "Native BLE library", JOptionPane.ERROR_MESSAGE);
			});
		}
	}

	private void addOrUpdateDevice(NativeBleDevice device) {
		for (int i = 0; i < mDeviceModel.size(); i++) {
			if (mDeviceModel.get(i).getId().equals(device.getId())) {
				boolean selected = mDeviceList.getSelectedIndex() == i;
				mDeviceModel.set(i, device);
				if (selected) {
					mDeviceList.setSelectedIndex(i);
				}
				return;
			}
		}
		mDeviceModel.addElement(device);
	}

	private void toggleScan() {
		final boolean start = !mScanning;
		runInBackground("scan", () -> {
			try {
				if (start) {
					mCentral.startScan();
				} else {
					mCentral.stopScan();
				}
				mScanning = start;
			} catch (NativeBleException e) {
				showError("Scan", e.getMessage());
			}
			onUi(this::updateButtons);
		});
	}

	private void connectSelected() {
		final NativeBleDevice device = mDeviceList.getSelectedValue();
		if (device == null) {
			JOptionPane.showMessageDialog(mFrame, "Scan, then select a device first.");
			return;
		}
		runInBackground("connect", () -> {
			// Scanning while connecting slows the connection on some adapters.
			if (mScanning) {
				try {
					mCentral.stopScan();
				} catch (NativeBleException e) {
					// Not fatal: connect anyway.
				}
				mScanning = false;
			}
			ShimmerBLENative shimmer = new ShimmerBLENative(device);
			new DeviceCallbacks().setWaitForData(shimmer);
			mConfigManager.setDevice(shimmer);
			mShimmer = shimmer;
			mPackets.set(0);
			mPacketReceptionRate = Double.NaN;
			shimmer.connect("", "");
			onUi(this::updateButtons);
		});
	}

	private void disconnect() {
		final ShimmerBLENative shimmer = mShimmer;
		if (shimmer == null) {
			return;
		}
		runInBackground("disconnect", () -> {
			try {
				shimmer.disconnect();
			} catch (ShimmerException e) {
				showError("Disconnect", e.getMessage());
			}
			closeLog();
		});
	}

	private void startStreaming() {
		ShimmerBLENative shimmer = mShimmer;
		if (shimmer == null) {
			return;
		}
		if (mChkLog.isSelected()) {
			String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
			File file = new File(System.getProperty("user.dir"), "shimmer_ble_" + shimmer.getDeviceName() + "_" + stamp + ".csv");
			try {
				mCsvLog.open(file);
			} catch (IOException e) {
				showError("Log", "Could not open " + file + ": " + e.getMessage());
			}
		}
		try {
			mPackets.set(0);
			shimmer.startStreaming();
		} catch (ShimmerException e) {
			showError("Start streaming", e.getMessage());
		}
	}

	private void stopStreaming() {
		ShimmerBLENative shimmer = mShimmer;
		if (shimmer != null) {
			shimmer.stopStreaming();
		}
		closeLog();
	}

	private void closeLog() {
		final File file = mCsvLog.close();
		if (file != null) {
			onUi(() -> mLblStats.setText("Logged to " + file.getAbsolutePath()));
		}
	}

	private void shutdown() {
		ShimmerBLENative shimmer = mShimmer;
		if (shimmer != null && shimmer.isConnected()) {
			try {
				shimmer.disconnect();
			} catch (ShimmerException e) {
				e.printStackTrace();
			}
		}
		mCsvLog.close();
		System.exit(0);
	}

	private void updateButtons() {
		ShimmerBLENative shimmer = mShimmer;
		boolean ready = mCentral != null;
		boolean connected = shimmer != null && shimmer.isConnected();
		boolean streaming = connected && shimmer.isStreaming();
		mBtnScan.setEnabled(ready);
		mBtnScan.setText(mScanning ? "Stop scan" : "Scan");
		mBtnConnect.setEnabled(ready && !connected);
		mBtnDisconnect.setEnabled(connected);
		mBtnSensors.setEnabled(connected && !streaming);
		mBtnConfig.setEnabled(connected && !streaming);
		mBtnPlot.setEnabled(connected);
		mBtnStart.setEnabled(connected && !streaming);
		mBtnStop.setEnabled(streaming);
		mChkLog.setEnabled(!streaming);
	}

	private void updateStats() {
		ShimmerBLENative shimmer = mShimmer;
		if (shimmer == null || !shimmer.isConnected()) {
			return;
		}
		long packets = mPackets.get();
		long perSecond = packets - mPacketsAtLastTick;
		mPacketsAtLastTick = packets;
		String prr = Double.isNaN(mPacketReceptionRate) ? "-" : String.format("%.1f%%", mPacketReceptionRate);
		mLblStats.setText(String.format("MTU %d   |   sampling %.1f Hz   |   %d packets/s   |   %d packets   |   reception %s",
				shimmer.getMtu(), shimmer.getSamplingRateShimmer(), perSecond, packets, prr));
	}

	/** Handles the driver's callbacks for the connected device. */
	private class DeviceCallbacks extends BasicProcessWithCallBack {
		@Override
		protected void processMsgFromCallback(ShimmerMsg msg) {
			int id = msg.mIdentifier;
			if (id == ShimmerBluetooth.MSG_IDENTIFIER_STATE_CHANGE) {
				final BT_STATE state = ((CallbackObject) msg.mB).mState;
				onUi(() -> {
					mLblState.setText(mShimmer.getDeviceName() + ": " + state);
					updateButtons();
				});
				if (state == BT_STATE.CONNECTION_LOST || state == BT_STATE.DISCONNECTED) {
					mCsvLog.close();
				}
			} else if (id == ShimmerBluetooth.MSG_IDENTIFIER_NOTIFICATION_MESSAGE) {
				int indicator = ((CallbackObject) msg.mB).mIndicator;
				if (indicator == ShimmerBluetooth.NOTIFICATION_SHIMMER_FULLY_INITIALIZED
						|| indicator == ShimmerBluetooth.NOTIFICATION_SHIMMER_START_STREAMING
						|| indicator == ShimmerBluetooth.NOTIFICATION_SHIMMER_STOP_STREAMING) {
					onUi(ShimmerBLECaptureExample.this::updateButtons);
				}
			} else if (id == ShimmerBluetooth.MSG_IDENTIFIER_DATA_PACKET) {
				ObjectCluster ojc = (ObjectCluster) msg.mB;
				mPackets.incrementAndGet();
				mCsvLog.write(ojc);
				try {
					mPlotManager.filterDataAndPlot(ojc);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else if (id == ShimmerBluetooth.MSG_IDENTIFIER_PACKET_RECEPTION_RATE_OVERALL) {
				mPacketReceptionRate = ((CallbackObject) msg.mB).mPacketReceptionRate;
			}
		}
	}

	/** Calibrated values, one row per packet, columns in the order the driver adds channels. */
	private static class CsvLog {
		private PrintWriter mOut;
		private File mFile;
		private List<String> mChannels;

		synchronized void open(File file) throws IOException {
			close();
			mOut = new PrintWriter(new BufferedWriter(new FileWriter(file)));
			mFile = file;
			mChannels = null;
		}

		synchronized void write(ObjectCluster ojc) {
			if (mOut == null) {
				return;
			}
			if (mChannels == null) {
				mChannels = new ArrayList<String>(ojc.getChannelNamesByInsertionOrder());
				mOut.println(String.join(",", mChannels));
			}
			StringBuilder row = new StringBuilder();
			for (int i = 0; i < mChannels.size(); i++) {
				if (i > 0) {
					row.append(',');
				}
				double value = ojc.getFormatClusterValue(mChannels.get(i), CHANNEL_TYPE.CAL.toString());
				if (!Double.isNaN(value)) {
					row.append(value);
				}
			}
			mOut.println(row);
		}

		/** Returns the file just closed, or null if none was open. */
		synchronized File close() {
			if (mOut == null) {
				return null;
			}
			mOut.close();
			mOut = null;
			File closed = mFile;
			mFile = null;
			return closed;
		}
	}

	private void showError(final String title, final String message) {
		onUi(() -> JOptionPane.showMessageDialog(mFrame, message, title, JOptionPane.ERROR_MESSAGE));
	}

	private static void onUi(Runnable r) {
		SwingUtilities.invokeLater(r);
	}

	private static void runInBackground(String name, Runnable r) {
		Thread t = new Thread(r, "BLECapture-" + name);
		t.setDaemon(true);
		t.start();
	}
}
