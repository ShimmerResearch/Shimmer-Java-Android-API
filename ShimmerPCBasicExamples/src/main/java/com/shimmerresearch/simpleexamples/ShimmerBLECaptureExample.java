package com.shimmerresearch.simpleexamples;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.desktop.QuitStrategy;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ble.nativeble.BleCentral;
import com.shimmerresearch.driver.ble.nativeble.BleScanListener;
import com.shimmerresearch.driver.ble.nativeble.BleScanResult;
import com.shimmerresearch.driver.ble.nativeble.NativeBleException;
import com.shimmerresearch.driver.ble.nativeble.NativeBleRadio;
import com.shimmerresearch.guiUtilities.configuration.EnableSensorsDialog;
import com.shimmerresearch.guiUtilities.configuration.SensorConfigDialog;
import com.shimmerresearch.guiUtilities.plot.BasicPlotManagerPC;

import info.monitorenter.gui.chart.Chart2D;

/**
 * Shimmer Capture-style test app for Shimmer3 and Shimmer3R over BLE through the in-process native
 * library: several devices at once, each through either driver, so the two can run side by side:
 * <ul>
 * <li><b>ShimmerBluetooth driver</b> - ShimmerBLENative, the transport route DEV-1132 built;</li>
 * <li><b>Rust LogAndStream library</b> - the protocol from shimmer-logandstream, through
 * its Java binding. Offered only when this build has it: a checkout of shimmer-logandstream beside
 * this repository (see build.gradle).</li>
 * </ul>
 * For testing, each device's row shows its reception counted the same way whichever driver runs it
 * (see {@link ReceptionStats}), beside the driver's own PRR, and the Reception tab charts it over
 * time. Streaming can stop itself after a set time; each stream's figures go to the log; "Copy
 * summary" puts every device's figures and this machine's details on the clipboard, for a ticket.
 * Each device logs to its own CSV file, in the same format from both drivers.
 * <p>
 * The Rust library cannot change the configuration yet: set the device up with ShimmerBluetooth
 * (settings persist on the device), then reconnect with the Rust library. Only the Rust library
 * runs the firmware's data-rate test.
 * <p>
 * Needs the native library: run {@code ./gradlew buildNative} in ShimmerDriverPC first, or pass
 * {@code -Dshimmer.ble.lib=<path to library>}. On macOS, run from Terminal and allow Terminal to use
 * Bluetooth (System Settings, Privacy &amp; Security, Bluetooth).
 */
public class ShimmerBLECaptureExample implements ConnectedShimmer.Observer {

	private static final String MODE_SHIMMERBLUETOOTH = "ShimmerBluetooth driver (ShimmerBLENative)";
	private static final String MODE_RUST_LOGANDSTREAM = "Rust LogAndStream library (shimmer-logandstream)";
	private static final String RUST_LOGANDSTREAM_DRIVER = "com.shimmerresearch.simpleexamples.RustLogAndStreamCaptureDriver";
	/** The binding's classes the driver needs, loaded up front so a broken binding is caught here. */
	private static final String[] RUST_LOGANDSTREAM_BINDING_CLASSES = { "com.shimmerresearch.logandstream.LogAndStreamSession",
			"com.shimmerresearch.logandstream.LogAndStreamProtocol$State", "com.shimmerresearch.logandstream.Event" };
	/** How long the connect queue waits for one device to be ready before connecting the next. */
	private static final long CONNECT_WAIT_MS = 45000;
	private static final int LOG_LINES = 5000;

	private final JFrame mFrame = new JFrame("Shimmer BLE Capture (native)");
	private final DefaultListModel<BleScanResult> mScanModel = new DefaultListModel<BleScanResult>();
	private final JList<BleScanResult> mScanList = new JList<BleScanResult>(mScanModel);
	private final JComboBox<String> mMode = new JComboBox<String>(modes());
	private final JButton mBtnScan = new JButton("Scan");
	private final JButton mBtnConnect = new JButton("Connect");
	private final JLabel mLblAdapter = new JLabel("Loading native BLE library...");

	private final JButton mBtnStart = new JButton("Start");
	private final JButton mBtnStop = new JButton("Stop");
	private final JButton mBtnDisconnect = new JButton("Disconnect");
	private final JButton mBtnPlot = new JButton("Signals to plot");
	private final JButton mBtnSensors = new JButton("Enable sensors");
	private final JButton mBtnConfig = new JButton("Sensor config");
	private final JButton mBtnDataRate = new JButton("Data-rate test");
	private final JSpinner mDataRateSeconds = new JSpinner(new SpinnerNumberModel(5, 1, 120, 1));

	private final JButton mBtnStartAll = new JButton("Start all");
	private final JButton mBtnStopAll = new JButton("Stop all");
	private final JButton mBtnDisconnectAll = new JButton("Disconnect all");
	private final JButton mBtnRemove = new JButton("Remove disconnected");
	private final JSpinner mStreamSeconds = new JSpinner(new SpinnerNumberModel(0, 0, 86400, 10));

	private final JCheckBox mChkLog = new JCheckBox("Log to CSV");
	private final JCheckBox mChkPlot = new JCheckBox("Plot signals", true);
	private final JButton mBtnClearPlot = new JButton("Clear plot");
	private final JButton mBtnCopy = new JButton("Copy summary");
	private final JButton mBtnSaveLog = new JButton("Save log");

	private final ConnectedShimmerTableModel mTableModel = new ConnectedShimmerTableModel();
	private final JTable mTable = mTableModel.createTable();
	private final Chart2D mChart = new Chart2D();
	private final BasicPlotManagerPC mPlotManager = new BasicPlotManagerPC();
	private final ReceptionCharts mReceptionCharts = new ReceptionCharts();
	private final JTextArea mLog = new JTextArea();
	private final JLabel mLblTotals = new JLabel(" ");

	/** Connects one device at a time: several connecting at once is slow or fails on some adapters. */
	private final ExecutorService mConnectQueue = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "BLECapture-connect");
		t.setDaemon(true);
		return t;
	});
	/** Log lines from any thread, appended on the Swing thread. */
	private final ConcurrentLinkedQueue<String> mPendingLog = new ConcurrentLinkedQueue<String>();
	/** Devices whose samples the plot could not take, reported once each. */
	private final Set<String> mPlotErrorsReported = ConcurrentHashMap.newKeySet();
	private final SimpleDateFormat mLogTime = new SimpleDateFormat("HH:mm:ss.SSS");

	private BleCentral mCentral;
	private volatile boolean mScanning = false;
	private volatile boolean mPlotting = true;
	private volatile String mAdapterState = "unknown";

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
		runInBackground("init", this::initCentral);
		new Timer(1000, e -> tick()).start();
		new Timer(200, e -> flushLog()).start();
	}

	// --- Layout -----------------------------------------------------------------------------------

	private void buildUi() {
		mScanList.addListSelectionListener(e -> updateButtons());
		mScanList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					connectSelected();
				}
			}
		});
		JScrollPane scan = new JScrollPane(mScanList);
		scan.setPreferredSize(new Dimension(300, 160));
		scan.setBorder(BorderFactory.createTitledBorder("Shimmer3 / Shimmer3R devices"));

		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.add(section("Connect (select one or more above)", row(mMode), row(mBtnScan, mBtnConnect), row(mLblAdapter)));
		controls.add(section("Selected devices (select rows in the table)", row(mBtnStart, mBtnStop),
				row(mBtnDisconnect, mBtnPlot), row(mBtnSensors, mBtnConfig), row(mBtnDataRate, withUnit(mDataRateSeconds, "s"))));
		controls.add(section("All devices", row(mBtnStartAll, mBtnStopAll), row(mBtnDisconnectAll, mBtnRemove),
				row(new JLabel("Stream for (0: until stopped)"), withUnit(mStreamSeconds, "s"))));
		controls.add(section("Output", row(mChkLog, mChkPlot), row(mBtnClearPlot, mBtnSaveLog), row(mBtnCopy)));

		JPanel west = new JPanel(new BorderLayout());
		west.add(scan, BorderLayout.CENTER);
		west.add(controls, BorderLayout.SOUTH);

		JScrollPane table = new JScrollPane(mTable);
		table.setBorder(BorderFactory.createTitledBorder("Devices (hover over a heading for what it measures)"));
		table.setPreferredSize(new Dimension(1000, 150));

		mLog.setEditable(false);
		mLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Signals", mChart);
		tabs.addTab("Reception", mReceptionCharts);
		tabs.addTab("Log", new JScrollPane(mLog));

		JSplitPane center = new JSplitPane(JSplitPane.VERTICAL_SPLIT, table, tabs);
		center.setResizeWeight(0);

		mLblTotals.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		mPlotManager.addChart(mChart);
		mPlotManager.initializeAxesForTimeBig();
		mPlotManager.setXAxisLabel("Time (PC clock)");

		mFrame.getContentPane().setLayout(new BorderLayout());
		mFrame.getContentPane().add(new JScrollPane(west), BorderLayout.WEST);
		mFrame.getContentPane().add(center, BorderLayout.CENTER);
		mFrame.getContentPane().add(mLblTotals, BorderLayout.SOUTH);
		mFrame.setSize(1440, 900);
		mFrame.setLocationByPlatform(true);
		mFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		mFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				shutdown();
			}
		});
		// On macOS, Cmd+Q closes the window too, so the devices are disconnected rather than left streaming.
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_STRATEGY)) {
			Desktop.getDesktop().setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS);
		}

		String configTip = "Only the ShimmerBluetooth driver can change the configuration: select one of its devices, not streaming";
		mBtnSensors.setToolTipText(configTip);
		mBtnConfig.setToolTipText(configTip);
		mBtnDataRate.setToolTipText("The firmware's throughput test (Rust LogAndStream library only), on every selected device at once");
		mStreamSeconds.setToolTipText("Start and Start all stop streaming by themselves after this long; 0 streams until stopped");
		mBtnCopy.setToolTipText("Copies this machine's details and every device's figures, for a ticket");
		mChkPlot.setToolTipText("Untick to see whether plotting affects reception");
		mChkLog.setToolTipText("Each device logs to its own CSV file in " + System.getProperty("user.dir") + " from its next start");

		mTable.getSelectionModel().addListSelectionListener(e -> updateButtons());
		mBtnScan.addActionListener(e -> toggleScan());
		mBtnConnect.addActionListener(e -> connectSelected());
		mBtnStart.addActionListener(e -> start(selected()));
		mBtnStop.addActionListener(e -> stop(selected()));
		mBtnDisconnect.addActionListener(e -> disconnect(selected()));
		mBtnPlot.addActionListener(e -> chooseSignals(selected()));
		mBtnSensors.addActionListener(e -> {
			ShimmerBluetoothCaptureDriver driver = configurable();
			new EnableSensorsDialog(driver.getShimmer(), driver.getConfigManager()).showDialog();
		});
		mBtnConfig.addActionListener(e -> {
			ShimmerBluetoothCaptureDriver driver = configurable();
			new SensorConfigDialog(driver.getShimmer(), driver.getConfigManager()).showDialog();
		});
		mBtnDataRate.addActionListener(e -> testDataRate(selected()));
		mBtnStartAll.addActionListener(e -> start(mTableModel.all()));
		mBtnStopAll.addActionListener(e -> stop(mTableModel.all()));
		mBtnDisconnectAll.addActionListener(e -> disconnect(mTableModel.all()));
		mBtnRemove.addActionListener(e -> removeDisconnected());
		mChkPlot.addActionListener(e -> mPlotting = mChkPlot.isSelected());
		mBtnClearPlot.addActionListener(e -> {
			synchronized (mPlotManager) {
				mPlotManager.removeAllSignals();
			}
		});
		mBtnCopy.addActionListener(e -> {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(summary()), null);
			log(null, "summary copied to the clipboard");
		});
		mBtnSaveLog.addActionListener(e -> saveLog());
	}

	private static JPanel section(String title, JPanel... rows) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder(title));
		for (JPanel row : rows) {
			panel.add(row);
		}
		return panel;
	}

	/** Components side by side, sharing the row's width equally. */
	private static JPanel row(Component... components) {
		JPanel row = new JPanel(new GridLayout(1, components.length, 4, 0));
		row.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		for (Component c : components) {
			row.add(c);
		}
		return row;
	}

	private static JPanel withUnit(JSpinner spinner, String unit) {
		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.add(spinner, BorderLayout.CENTER);
		panel.add(new JLabel(unit), BorderLayout.EAST);
		return panel;
	}

	// --- Scan and connect -------------------------------------------------------------------------

	private void initCentral() {
		try {
			mCentral = BleCentral.getDefault();
			mAdapterState = ((NativeBleRadio) mCentral.getRadio()).getAdapterState();
			mCentral.addScanListener(new BleScanListener() {
				public void onDeviceFound(final BleScanResult device) {
					if (device.getProfile() != null && device.getProfile().isShimmer3Family()) {
						SwingUtilities.invokeLater(() -> addOrUpdateScanResult(device));
					}
				}
			});
			log(null, "Bluetooth adapter: " + mAdapterState + "; " + System.getProperty("os.name") + " "
					+ System.getProperty("os.version") + ", Java " + System.getProperty("java.version"));
			onUi(() -> {
				mLblAdapter.setText("Bluetooth adapter: " + mAdapterState);
				updateButtons();
			});
		} catch (NativeBleException e) {
			onUi(() -> {
				mLblAdapter.setText("Native BLE unavailable");
				JOptionPane.showMessageDialog(mFrame, e.getMessage(), "Native BLE library", JOptionPane.ERROR_MESSAGE);
			});
		}
	}

	private void addOrUpdateScanResult(BleScanResult device) {
		for (int i = 0; i < mScanModel.size(); i++) {
			if (mScanModel.get(i).getId().equals(device.getId())) {
				boolean selected = mScanList.isSelectedIndex(i);
				mScanModel.set(i, device);
				if (selected) {
					mScanList.addSelectionInterval(i, i);
				}
				return;
			}
		}
		mScanModel.addElement(device);
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

	/** Queues a connection to every device selected in the scan list, with the driver chosen. */
	private void connectSelected() {
		List<BleScanResult> devices = mScanList.getSelectedValuesList();
		if (devices.isEmpty()) {
			JOptionPane.showMessageDialog(mFrame, "Scan, then select one or more devices first.");
			return;
		}
		boolean rust = MODE_RUST_LOGANDSTREAM.equals(mMode.getSelectedItem());
		for (BleScanResult device : devices) {
			ConnectedShimmer existing = mTableModel.find(device.getId());
			if (existing != null && existing.isActive()) {
				log(existing, "already connected: disconnect it first to change driver");
				continue;
			}
			if (existing != null) {
				remove(existing);
			}
			CaptureDriver driver = rust ? newRustLogAndStreamDriver() : new ShimmerBluetoothCaptureDriver();
			if (driver == null) {
				return;
			}
			ConnectedShimmer shimmer = new ConnectedShimmer(device, driver, this);
			mTableModel.add(shimmer);
			mConnectQueue.execute(() -> {
				stopScan();
				shimmer.connectAndWait(CONNECT_WAIT_MS);
			});
		}
		updateButtons();
	}

	/** Scanning while connecting slows the connection on some adapters. */
	private void stopScan() {
		if (mScanning) {
			try {
				mCentral.stopScan();
			} catch (NativeBleException e) {
				// Not fatal: connect anyway.
			}
			mScanning = false;
			onUi(this::updateButtons);
		}
	}

	// --- Device actions ---------------------------------------------------------------------------

	private List<ConnectedShimmer> selected() {
		List<ConnectedShimmer> list = new ArrayList<ConnectedShimmer>();
		for (int row : mTable.getSelectedRows()) {
			list.add(mTableModel.get(mTable.convertRowIndexToModel(row)));
		}
		return list;
	}

	private void start(List<ConnectedShimmer> devices) {
		final File csvFolder = mChkLog.isSelected() ? new File(System.getProperty("user.dir")) : null;
		final int seconds = (Integer) mStreamSeconds.getValue();
		for (ConnectedShimmer s : devices) {
			if (!s.isIdle()) {
				continue;
			}
			runInBackground("start-" + s.name(), () -> {
				int run = s.startStreaming(csvFolder);
				if (seconds > 0) {
					log(s, "stops after " + seconds + " s");
					onUi(() -> {
						Timer stop = new Timer(seconds * 1000, e -> runInBackground("stop-" + s.name(), () -> s.stopStreamingRun(run)));
						stop.setRepeats(false);
						stop.start();
					});
				}
				onUi(this::updateButtons);
			});
		}
	}

	private void stop(List<ConnectedShimmer> devices) {
		for (ConnectedShimmer s : devices) {
			if (s.isStreaming()) {
				runInBackground("stop-" + s.name(), s::stopStreaming);
			}
		}
	}

	private void disconnect(List<ConnectedShimmer> devices) {
		for (ConnectedShimmer s : devices) {
			if (s.isActive()) {
				runInBackground("disconnect-" + s.name(), s::disconnect);
			}
		}
	}

	private void testDataRate(List<ConnectedShimmer> devices) {
		final int seconds = (Integer) mDataRateSeconds.getValue();
		for (ConnectedShimmer s : devices) {
			if (s.isIdle() && s.getDriver().canTestDataRate()) {
				runInBackground("data-rate-" + s.name(), () -> s.startDataRateTest(seconds));
			}
		}
	}

	private void chooseSignals(List<ConnectedShimmer> devices) {
		List<String[]> signals = new ArrayList<String[]>();
		for (ConnectedShimmer s : devices) {
			List<String[]> own = s.isReady() ? s.getDriver().getSignalsForPlot() : null;
			if (own != null) {
				signals.addAll(own);
			}
		}
		if (signals.isEmpty()) {
			JOptionPane.showMessageDialog(mFrame, "None of the selected devices is ready yet.");
			return;
		}
		SignalChooserDialog.show(mFrame, signals, mPlotManager, mChart);
	}

	/** The one selected device's ShimmerBluetooth driver, if it can be configured now; otherwise null. */
	private ShimmerBluetoothCaptureDriver configurable() {
		List<ConnectedShimmer> sel = selected();
		if (sel.size() != 1 || !sel.get(0).isIdle() || !(sel.get(0).getDriver() instanceof ShimmerBluetoothCaptureDriver)) {
			return null;
		}
		return (ShimmerBluetoothCaptureDriver) sel.get(0).getDriver();
	}

	private void removeDisconnected() {
		for (ConnectedShimmer s : mTableModel.all()) {
			if (!s.isActive()) {
				remove(s);
			}
		}
		updateButtons();
	}

	/** Removes a device's row, its plotted signals and its reception traces. */
	private void remove(ConnectedShimmer shimmer) {
		mTableModel.remove(shimmer);
		mReceptionCharts.remove(shimmer);
		synchronized (mPlotManager) {
			for (String[] signal : new ArrayList<String[]>(mPlotManager.mListofPropertiestoPlot)) {
				if (signal[0].equals(shimmer.name())) {
					mPlotManager.removeSignal(signal);
				}
			}
		}
		mPlotErrorsReported.remove(shimmer.name());
	}

	private void shutdown() {
		List<Thread> closing = new ArrayList<Thread>();
		for (ConnectedShimmer s : mTableModel.all()) {
			if (s.isActive()) {
				Thread t = new Thread(s::disconnect, "BLECapture-close-" + s.name());
				t.setDaemon(true);
				t.start();
				closing.add(t);
			}
		}
		for (Thread t : closing) {
			try {
				t.join(5000);
			} catch (InterruptedException e) {
				break;
			}
		}
		System.exit(0);
	}

	// --- ConnectedShimmer.Observer -------------------------------------------------------------

	@Override
	public void onLog(ConnectedShimmer shimmer, String message) {
		log(shimmer, message);
	}

	@Override
	public void onSample(ConnectedShimmer shimmer, ObjectCluster sample) {
		if (!mPlotting) {
			return;
		}
		try {
			synchronized (mPlotManager) {
				mPlotManager.filterDataAndPlot(sample);
			}
		} catch (Exception e) {
			if (mPlotErrorsReported.add(shimmer.name())) {
				log(shimmer, "plot error (reported once per device): " + e);
			}
		}
	}

	@Override
	public void onChanged(ConnectedShimmer shimmer) {
		onUi(() -> {
			mTableModel.refresh();
			updateButtons();
		});
	}

	// --- Once a second, and the log ------------------------------------------------------------

	private void tick() {
		int ready = 0;
		int streaming = 0;
		double received = 0;
		long missing = 0;
		for (ConnectedShimmer s : mTableModel.all()) {
			ReceptionStats.Snapshot snapshot = s.tick();
			if (s.isReady()) {
				ready++;
			}
			if (s.isStreaming()) {
				streaming++;
				received += snapshot.windowRate();
				missing += snapshot.missing;
				mReceptionCharts.plot(s, snapshot);
			}
		}
		mTableModel.refresh();
		updateButtons();
		mLblTotals.setText(String.format("%d device(s): %d connected, %d streaming   |   %.1f samples/s received in all, %d missing",
				mTableModel.getRowCount(), ready, streaming, received, missing));
	}

	private void log(ConnectedShimmer shimmer, String message) {
		String who = shimmer == null ? "" : shimmer.name() + " [" + shimmer.getDriver().label() + "]  ";
		String line;
		synchronized (mLogTime) {
			line = mLogTime.format(new Date()) + "  " + who + message;
		}
		System.out.println(line);
		mPendingLog.add(line);
	}

	private void flushLog() {
		StringBuilder lines = new StringBuilder();
		String line;
		while ((line = mPendingLog.poll()) != null) {
			lines.append(line).append('\n');
		}
		if (lines.length() == 0) {
			return;
		}
		mLog.append(lines.toString());
		int excess = mLog.getLineCount() - LOG_LINES;
		if (excess > 0) {
			try {
				mLog.replaceRange("", 0, mLog.getLineEndOffset(excess - 1));
			} catch (BadLocationException e) {
				// Leave it long.
			}
		}
		mLog.setCaretPosition(mLog.getDocument().getLength());
	}

	private void saveLog() {
		JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
		chooser.setSelectedFile(new File("shimmer_ble_capture_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".log"));
		if (chooser.showSaveDialog(mFrame) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = chooser.getSelectedFile();
		try {
			Files.write(file.toPath(), (summary() + "\n" + mLog.getText()).getBytes(StandardCharsets.UTF_8));
			log(null, "log saved to " + file.getAbsolutePath());
		} catch (IOException e) {
			showError("Save log", "Could not write " + file + ": " + e.getMessage());
		}
	}

	/** This machine, and every device's figures, as text. */
	private String summary() {
		StringBuilder b = new StringBuilder();
		b.append("Shimmer BLE Capture, ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append('\n');
		b.append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append(' ')
				.append(System.getProperty("os.arch")).append(", Java ").append(System.getProperty("java.version"))
				.append(", Bluetooth adapter ").append(mAdapterState).append('\n');
		for (ConnectedShimmer s : mTableModel.all()) {
			CaptureDriver d = s.getDriver();
			b.append(s.name()).append(" [").append(d.label()).append("] ").append(s.getState());
			if (d.getDeviceSummary() != null) {
				b.append(", ").append(d.getDeviceSummary());
			}
			if (d.isConnected()) {
				b.append(", MTU ").append(d.getMtu());
			}
			b.append('\n');
			if (s.getSnapshot().samples > 0) {
				b.append("    streamed ").append(s.receptionSummary()).append('\n');
			}
			if (s.getDataRateText() != null) {
				b.append("    data-rate test: ").append(s.getDataRateText()).append('\n');
			}
			if (s.getLinkLosses() > 0 || s.getErrors() > 0) {
				b.append("    link lost ").append(s.getLinkLosses()).append(" time(s), ").append(s.getErrors()).append(" error(s)");
				if (s.getLastError() != null) {
					b.append(", the last: ").append(s.getLastError());
				}
				b.append('\n');
			}
		}
		return b.toString();
	}

	// --- Buttons ------------------------------------------------------------------------------

	private void updateButtons() {
		List<ConnectedShimmer> sel = selected();
		List<ConnectedShimmer> all = mTableModel.all();
		boolean central = mCentral != null;
		mBtnScan.setEnabled(central);
		mBtnScan.setText(mScanning ? "Stop scan" : "Scan");
		mBtnConnect.setEnabled(central && !mScanList.isSelectionEmpty());
		mBtnStart.setEnabled(any(sel, ConnectedShimmer::isIdle));
		mBtnStop.setEnabled(any(sel, ConnectedShimmer::isStreaming));
		mBtnDisconnect.setEnabled(any(sel, ConnectedShimmer::isActive));
		mBtnPlot.setEnabled(any(sel, ConnectedShimmer::isReady));
		boolean configurable = configurable() != null;
		mBtnSensors.setEnabled(configurable);
		mBtnConfig.setEnabled(configurable);
		mBtnDataRate.setEnabled(any(sel, s -> s.isIdle() && s.getDriver().canTestDataRate()));
		mBtnStartAll.setEnabled(any(all, ConnectedShimmer::isIdle));
		mBtnStopAll.setEnabled(any(all, ConnectedShimmer::isStreaming));
		mBtnDisconnectAll.setEnabled(any(all, ConnectedShimmer::isActive));
		mBtnRemove.setEnabled(any(all, s -> !s.isActive()));
	}

	private static boolean any(List<ConnectedShimmer> devices, Predicate<ConnectedShimmer> test) {
		for (ConnectedShimmer s : devices) {
			if (test.test(s)) {
				return true;
			}
		}
		return false;
	}

	/** The modes this build offers: the Rust LogAndStream one only if the build has its driver. */
	private static String[] modes() {
		return newRustLogAndStreamDriver() != null ? new String[] { MODE_SHIMMERBLUETOOTH, MODE_RUST_LOGANDSTREAM } : new String[] { MODE_SHIMMERBLUETOOTH };
	}

	/**
	 * The Rust LogAndStream driver, or null if this build does not have it. Found by name, so
	 * that the app builds without shimmer-logandstream. If the build has it but its binding cannot
	 * be loaded (built for a newer Java, say), says why on the console and offers no such mode.
	 */
	static CaptureDriver newRustLogAndStreamDriver() {
		Class<?> driver;
		try {
			driver = Class.forName(RUST_LOGANDSTREAM_DRIVER);
		} catch (ClassNotFoundException e) {
			return null;
		}
		try {
			for (String binding : RUST_LOGANDSTREAM_BINDING_CLASSES) {
				Class.forName(binding);
			}
			return (CaptureDriver) driver.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException | LinkageError e) {
			System.err.println("The Rust LogAndStream mode is unavailable: its Java binding cannot be loaded: " + e);
			return null;
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
