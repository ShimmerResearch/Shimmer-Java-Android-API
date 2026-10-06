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
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.ble.nativeble.BleCentral;
import com.shimmerresearch.driver.ble.nativeble.BleScanListener;
import com.shimmerresearch.driver.ble.nativeble.NativeBleDevice;
import com.shimmerresearch.driver.ble.nativeble.NativeBleException;
import com.shimmerresearch.driver.ble.nativeble.NativeBleRadio;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.guiUtilities.configuration.EnableSensorsDialog;
import com.shimmerresearch.guiUtilities.configuration.SensorConfigDialog;
import com.shimmerresearch.guiUtilities.configuration.SignalsToPlotDialog;
import com.shimmerresearch.guiUtilities.plot.BasicPlotManagerPC;

import info.monitorenter.gui.chart.Chart2D;

/**
 * Shimmer Capture-style example for Shimmer3 and Shimmer3R over BLE through the in-process native
 * library, with a choice of driver:
 * <ul>
 * <li><b>Today's driver</b> - ShimmerBLENative (ShimmerBluetooth), as DEV-1132 built it;</li>
 * <li><b>New state machine</b> - the DEV-1134 I/O-free LogAndStream protocol prototype;</li>
 * <li><b>Rust protocol core</b> - the same protocol from shimmer-protocol-core, through its Java
 * binding. Offered only when this build has it: a checkout of shimmer-protocol-core beside this
 * repository (see build.gradle).</li>
 * </ul>
 * All write the same CSV format, so recordings from the same device can be compared directly.
 * Neither the state machine nor the core can change the configuration yet: set the device up in
 * driver mode (settings persist on the device), then reconnect in the other mode.
 * <p>
 * Needs the native library: run {@code ./gradlew buildNative} in ShimmerDriverPC first, or pass
 * {@code -Dshimmer.ble.lib=<path to library>}. On macOS, run from Terminal and allow Terminal to use
 * Bluetooth (System Settings, Privacy &amp; Security, Bluetooth).
 */
public class ShimmerBLECaptureExample {

	private static final String MODE_DRIVER = "Today's driver (ShimmerBLENative)";
	private static final String MODE_STATE_MACHINE = "New state machine (DEV-1134)";
	private static final String MODE_CORE = "Rust protocol core (shimmer-protocol-core)";
	private static final String CORE_BACKEND = "com.shimmerresearch.simpleexamples.CoreCaptureBackend";
	/** The binding's classes the backend needs, loaded up front so a broken binding is caught here. */
	private static final String[] CORE_BINDING_CLASSES = { "com.shimmerresearch.protocolcore.CoreHost",
			"com.shimmerresearch.protocolcore.LogAndStreamProtocolCore$State", "com.shimmerresearch.protocolcore.CoreEvent" };

	private final JFrame mFrame = new JFrame("Shimmer BLE Capture (native)");
	private final DefaultListModel<NativeBleDevice> mDeviceModel = new DefaultListModel<NativeBleDevice>();
	private final JList<NativeBleDevice> mDeviceList = new JList<NativeBleDevice>(mDeviceModel);
	private final JComboBox<String> mMode = new JComboBox<String>(modes());
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
	private final CsvLog mCsvLog = new CsvLog();
	private final AtomicLong mPackets = new AtomicLong();

	private BleCentral mCentral;
	private volatile CaptureBackend mBackend;
	private volatile String mDeviceName = "";
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
		JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		modeRow.add(new JLabel("Driver:"));
		modeRow.add(mMode);

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
		north.add(modeRow);
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
		mFrame.setSize(1100, 720);
		mFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		mFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				shutdown();
			}
		});

		String configTip = "Only today's driver can change the configuration yet: configure in driver mode";
		mBtnSensors.setToolTipText(configTip);
		mBtnConfig.setToolTipText(configTip);

		mMode.addActionListener(e -> updateButtons());
		mBtnScan.addActionListener(e -> toggleScan());
		mBtnConnect.addActionListener(e -> connectSelected());
		mBtnDisconnect.addActionListener(e -> disconnect());
		mBtnSensors.addActionListener(e -> {
			DriverCaptureBackend driver = (DriverCaptureBackend) mBackend;
			new EnableSensorsDialog(driver.getDeviceForPlot(), driver.getConfigManager()).showDialog();
		});
		mBtnConfig.addActionListener(e -> {
			DriverCaptureBackend driver = (DriverCaptureBackend) mBackend;
			new SensorConfigDialog(driver.getDeviceForPlot(), driver.getConfigManager()).showDialog();
		});
		mBtnPlot.addActionListener(e -> {
			ShimmerDevice device = mBackend.getDeviceForPlot();
			List<String[]> signals = mBackend.getSignalsForPlot();
			if (device != null) {
				new SignalsToPlotDialog().initialize(device, mPlotManager, mChart);
			} else if (signals != null) {
				SignalChooserDialog.show(mFrame, signals, mPlotManager, mChart);
			}
		});
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
		final CaptureBackend backend = MODE_STATE_MACHINE.equals(mMode.getSelectedItem()) ? new ProtocolCaptureBackend()
				: MODE_CORE.equals(mMode.getSelectedItem()) ? newCoreBackend() : new DriverCaptureBackend();
		mBackend = backend;
		mDeviceName = device.getName();
		mPackets.set(0);
		mFrame.setTitle("Shimmer BLE Capture (native) - " + mMode.getSelectedItem());
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
			backend.connect(device, new BackendListener(backend));
			onUi(this::updateButtons);
		});
	}

	/** Receives one backend's events; ignores them once another backend has replaced it. */
	private class BackendListener implements CaptureBackend.Listener {
		private final CaptureBackend mOwner;

		BackendListener(CaptureBackend owner) {
			mOwner = owner;
		}

		@Override
		public void onState(final String state) {
			if (mBackend != mOwner) {
				return;
			}
			if (state.startsWith("DISCONNECTED") || state.startsWith("CONNECTION_LOST")) {
				mCsvLog.close();
			}
			onUi(() -> {
				mLblState.setText(mDeviceName + " [" + mOwner.label() + "]: " + state);
				updateButtons();
			});
		}

		@Override
		public void onReady() {
			if (mBackend == mOwner) {
				onUi(ShimmerBLECaptureExample.this::updateButtons);
			}
		}

		@Override
		public void onSample(ObjectCluster sample) {
			if (mBackend != mOwner) {
				return;
			}
			mPackets.incrementAndGet();
			mCsvLog.write(sample);
			try {
				mPlotManager.filterDataAndPlot(sample);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onError(String message) {
			if (mBackend == mOwner) {
				showError(mOwner.label(), message);
			}
		}
	}

	private void disconnect() {
		final CaptureBackend backend = mBackend;
		if (backend == null) {
			return;
		}
		runInBackground("disconnect", () -> {
			backend.disconnect();
			closeLog();
			onUi(this::updateButtons);
		});
	}

	private void startStreaming() {
		CaptureBackend backend = mBackend;
		if (backend == null) {
			return;
		}
		if (mChkLog.isSelected()) {
			String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
			File file = new File(System.getProperty("user.dir"),
					"shimmer_ble_" + mDeviceName + "_" + backend.label() + "_" + stamp + ".csv");
			try {
				mCsvLog.open(file);
			} catch (IOException e) {
				showError("Log", "Could not open " + file + ": " + e.getMessage());
			}
		}
		try {
			mPackets.set(0);
			backend.startStreaming();
		} catch (Exception e) {
			showError("Start streaming", e.getMessage());
		}
	}

	private void stopStreaming() {
		CaptureBackend backend = mBackend;
		if (backend != null) {
			backend.stopStreaming();
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
		CaptureBackend backend = mBackend;
		if (backend != null && backend.isConnected()) {
			backend.disconnect();
		}
		mCsvLog.close();
		System.exit(0);
	}

	/** The modes this build offers: the core's only if the build has its backend. */
	private static String[] modes() {
		return newCoreBackend() != null ? new String[] { MODE_DRIVER, MODE_STATE_MACHINE, MODE_CORE }
				: new String[] { MODE_DRIVER, MODE_STATE_MACHINE };
	}

	/**
	 * The Rust protocol core's backend, or null if this build does not have it. Found by name, so
	 * that the app builds without shimmer-protocol-core. If the build has it but its binding cannot
	 * be loaded (built for a newer Java, say), says why on the console and offers no such mode.
	 */
	static CaptureBackend newCoreBackend() {
		Class<?> backend;
		try {
			backend = Class.forName(CORE_BACKEND);
		} catch (ClassNotFoundException e) {
			return null;
		}
		try {
			for (String binding : CORE_BINDING_CLASSES) {
				Class.forName(binding);
			}
			return (CaptureBackend) backend.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException | LinkageError e) {
			System.err.println("The Rust protocol core mode is unavailable: its Java binding cannot be loaded: " + e);
			return null;
		}
	}

	private void updateButtons() {
		CaptureBackend backend = mBackend;
		boolean ready = mCentral != null;
		boolean connected = backend != null && backend.isConnected();
		boolean streaming = connected && backend.isStreaming();
		boolean configurable = connected && !streaming && backend.canConfigure();
		mMode.setEnabled(!connected);
		mBtnScan.setEnabled(ready);
		mBtnScan.setText(mScanning ? "Stop scan" : "Scan");
		mBtnConnect.setEnabled(ready && !connected);
		mBtnDisconnect.setEnabled(backend != null);
		mBtnSensors.setEnabled(configurable);
		mBtnConfig.setEnabled(configurable);
		mBtnPlot.setEnabled(connected);
		mBtnStart.setEnabled(connected && !streaming);
		mBtnStop.setEnabled(streaming);
		mChkLog.setEnabled(!streaming);
	}

	private void updateStats() {
		CaptureBackend backend = mBackend;
		if (backend == null || !backend.isConnected()) {
			return;
		}
		updateButtons();
		long packets = mPackets.get();
		long perSecond = packets - mPacketsAtLastTick;
		mPacketsAtLastTick = packets;
		double prr = backend.getPacketReceptionRate();
		mLblStats.setText(String.format("[%s]   MTU %d   |   sampling %.1f Hz   |   %d packets/s   |   %d packets   |   reception %s",
				backend.label(), backend.getMtu(), backend.getSamplingRate(), perSecond, packets,
				Double.isNaN(prr) ? "-" : String.format("%.1f%%", prr)));
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
