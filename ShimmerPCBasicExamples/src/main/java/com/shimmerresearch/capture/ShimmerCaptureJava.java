package com.shimmerresearch.capture;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.shimmer2r3.EventRecord;
import com.shimmerresearch.simpleexamples.EventMarkersFrame;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.guiUtilities.configuration.EnableSensorsDialog;
import com.shimmerresearch.guiUtilities.configuration.SensorConfigDialog;
import com.shimmerresearch.tools.bluetooth.BasicShimmerBluetoothManagerPc;

import jssc.SerialPortList;

/**
 * Shimmer Capture on the Java driver: what shimmer-host-core's bindings/CAPTURE.md says every
 * Capture app does, as far as the Java driver can do it today, over Bluetooth Classic, one device
 * at a time. The same figures under the same names and the same CSV files as the host-core apps, so
 * that a result from this app compares with theirs. What the driver cannot do yet is listed in
 * README.md.
 */
public final class ShimmerCaptureJava implements CaptureDevice.Listener {

	static final String[] FIGURES = {"Device", "Link", "State", "Rate Hz", "Rx /s", "Samples", "Missing",
			"Reception", "PRR", "Delay ms", "Last sample", "Battery", "Link lost", "Errors", "Data rate",
			"Hardware / firmware"};
	static final String[] RATES = {"51.2", "102.4", "128", "204.8", "256", "512", "1024"};
	private static final String CAL = CHANNEL_TYPE.CAL.toString();
	private static final String UNCAL = CHANNEL_TYPE.UNCAL.toString();

	/** Bluetooth Classic only: the manager's BLE server (gRPC) is not started */
	private final BasicShimmerBluetoothManagerPc mManager = new BasicShimmerBluetoothManagerPc(false);
	private final CaptureDevice mDevice;
	private final File mFolder = new File(new File(System.getProperty("user.home"), "Documents"), "Shimmer Capture");

	private final JFrame mFrame = new JFrame("Shimmer Capture (Java driver)");
	private final JComboBox<String> mPorts = new JComboBox<String>();
	private final DefaultTableModel mFigures = new DefaultTableModel(new Object[] {"Figure", "Value"}, 0) {
		private static final long serialVersionUID = 1L;
		@Override public boolean isCellEditable(int r, int c) { return false; }
	};
	private final JTextArea mLog = new JTextArea(8, 80);
	private final JTextArea mDetails = new JTextArea(9, 60);
	private final JComboBox<String> mWindow = new JComboBox<String>(new String[] {"5", "10", "30"});
	private final JComboBox<String> mFormat = new JComboBox<String>(new String[] {"Calibrated", "Raw"});
	private final JComboBox<String> mRate = new JComboBox<String>(RATES);
	private final JButton mRecord = new JButton("Record to CSV");
	private final JLabel mRecordLabel = new JLabel(" ");
	private final Map<String, PlotView> mPlots = new LinkedHashMap<String, PlotView>();
	private final JPanel mPlotPanel = new JPanel();
	private final List<JButton> mReadyOnly = new ArrayList<JButton>();
	private final List<JButton> mReadyOrStreaming = new ArrayList<JButton>();
	private final List<JButton> mStreamingOnly = new ArrayList<JButton>();
	private final List<JButton> mConnectedOnly = new ArrayList<JButton>();
	private final SimpleDateFormat mLogTime = new SimpleDateFormat("HH:mm:ss.SSS");
	private long mLastWindowEnd = System.currentTimeMillis();
	/** The latest sample's device time (Timestamp, calibrated, ms): the plot's x axis, to place event markers on */
	private volatile double mLastDeviceMs = Double.NaN;
	/** The plot's format, as the Stream tab's box has it; read on the driver's thread */
	private volatile boolean mPlotRaw;
	private EventMarkersFrame mEventMarkers;

	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			// the default look then
		}
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				new ShimmerCaptureJava().show();
			}
		});
	}

	public ShimmerCaptureJava() {
		mDevice = new CaptureDevice(mManager, this);
		build();
		refreshPorts();
		new Timer(500, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refresh();
			}
		}).start();
		new Timer(100, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for (PlotView p : mPlots.values()) {
					p.repaint();
				}
			}
		}).start();
	}

	void show() {
		mFrame.setVisible(true);
	}

	// ------------------------------------------------------------------
	// Layout
	// ------------------------------------------------------------------

	private void build() {
		// Closing the window stops a stream, closes a recording (so its last rows are written) and
		// closes the link, then exits
		mFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		mFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				try {
					mDevice.stopRecording("the window was closed");
					mDevice.disconnect();
				} finally {
					System.exit(0);
				}
			}
		});
		mFrame.setSize(1280, 860);

		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		top.add(new JLabel("Bluetooth Classic port"));
		mPorts.setEditable(true);
		mPorts.setPreferredSize(new Dimension(160, 26));
		mPorts.setToolTipText("The paired Shimmer's outgoing COM port (Windows), /dev/cu.Shimmer3R-XXXX (macOS) or /dev/rfcomm<n> (Linux)");
		top.add(mPorts);
		top.add(button("Refresh", null, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshPorts();
			}
		}));
		top.add(button("Connect", null, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Object port = mPorts.getSelectedItem();
				if (port == null || port.toString().trim().isEmpty()) {
					log("Choose a port first");
					return;
				}
				if (mDevice.isConnecting()) {
					log("Already connecting on " + mDevice.getPort());
					return;
				}
				if (mDevice.isConnected()) {
					log("Already connected on " + mDevice.getPort() + ": one device at a time");
					return;
				}
				clearPlots();
				mDevice.connect(port.toString().trim());
			}
		}));
		top.add(button("Disconnect", mConnectedOnly, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mDevice.disconnect();
			}
		}));

		JTable figures = new JTable(mFigures);
		figures.getColumnModel().getColumn(0).setPreferredWidth(110);
		figures.getColumnModel().getColumn(1).setPreferredWidth(260);
		for (String f : FIGURES) {
			mFigures.addRow(new Object[] {f, "-"});
		}
		JScrollPane figuresScroll = new JScrollPane(figures);
		figuresScroll.setPreferredSize(new Dimension(380, 400));

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Stream", buildStreamTab());
		tabs.addTab("Device", buildDeviceTab());

		JSplitPane centre = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, figuresScroll, tabs);
		centre.setDividerLocation(390);

		mLog.setEditable(false);
		JScrollPane logScroll = new JScrollPane(mLog);
		logScroll.setBorder(BorderFactory.createTitledBorder("Log"));
		JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centre, logScroll);
		main.setResizeWeight(0.78);

		mFrame.getContentPane().setLayout(new BorderLayout());
		mFrame.getContentPane().add(top, BorderLayout.NORTH);
		mFrame.getContentPane().add(main, BorderLayout.CENTER);
		mFrame.setLocationRelativeTo(null);
		updateEnabled();
	}

	private JComponent buildStreamTab() {
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
		bar.add(button("Start", mReadyOnly, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearPlots();
				mDevice.startStreaming();
			}
		}));
		bar.add(button("Stop", mStreamingOnly, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mDevice.stopStreaming();
			}
		}));
		mRecord.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (mDevice.isRecording()) {
					mDevice.stopRecording("stopped");
				} else {
					mDevice.startRecording(mFolder);
				}
			}
		});
		mStreamingOnly.add(mRecord);
		bar.add(mRecord);
		bar.add(mRecordLabel);
		bar.add(new JLabel("   Window s"));
		mWindow.setSelectedItem("10");
		mWindow.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				double s = Double.parseDouble(mWindow.getSelectedItem().toString());
				for (PlotView p : mPlots.values()) {
					p.setWindowSeconds(s);
				}
			}
		});
		bar.add(mWindow);
		mFormat.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mPlotRaw = mFormat.getSelectedIndex() == 1;
				clearPlots();
			}
		});
		bar.add(mFormat);
		bar.add(button("Clear", null, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearPlots();
			}
		}));
		JButton events = button("Event markers...", mConnectedOnly, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openEventMarkers();
			}
		});
		events.setToolTipText("The button and TTL event channel (Shimmer3R, LogAndStream v1.01.026 on): byte 232, opt in, live records; each edge is marked on the plot");
		bar.add(events);

		mPlotPanel.setLayout(new BoxLayout(mPlotPanel, BoxLayout.Y_AXIS));
		for (String panel : Panels.ALL) {
			PlotView v = new PlotView(panel);
			v.setWindowSeconds(10);
			v.setVisible(false);
			mPlots.put(panel, v);
			mPlotPanel.add(v);
		}
		JPanel tab = new JPanel(new BorderLayout());
		tab.add(bar, BorderLayout.NORTH);
		tab.add(new JScrollPane(mPlotPanel), BorderLayout.CENTER);
		return tab;
	}

	private JComponent buildDeviceTab() {
		JPanel commands = new JPanel(new GridLayout(0, 3, 6, 6));
		commands.setBorder(BorderFactory.createTitledBorder("Commands"));
		commands.add(button("Read battery", mReadyOrStreaming, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mDevice.readBattery();
			}
		}));
		commands.add(button("Read status", mReadyOrStreaming, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mDevice.readStatus();
			}
		}));
		commands.add(button("Toggle red LED", mReadyOrStreaming, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mDevice.toggleRedLed();
			}
		}));
		commands.add(button("Read clock", mReadyOnly, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mDevice.readClock();
			}
		}));
		commands.add(button("Set clock", mReadyOnly, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mDevice.setClock();
			}
		}));
		commands.add(button("Read Bluetooth module", mReadyOnly, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mDevice.readBluetoothModule();
			}
		}));
		commands.add(button("Start logging", mReadyOnly, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mDevice.startLogging();
			}
		}));
		commands.add(button("Stop logging", mReadyOnly, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mDevice.stopLogging();
			}
		}));
		JPanel rate = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		rate.add(mRate);
		rate.add(button("Set rate (Hz)", mReadyOnly, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mDevice.writeSamplingRate(Double.parseDouble(mRate.getSelectedItem().toString()));
			}
		}));
		commands.add(rate);
		commands.add(button("Sensors...", mReadyOnly, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ShimmerBluetooth sb = mDevice.shimmer();
				if (sb != null) {
					new EnableSensorsDialog(sb, mManager).showDialog();
				}
			}
		}));
		commands.add(button("Settings...", mReadyOnly, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ShimmerBluetooth sb = mDevice.shimmer();
				if (sb != null) {
					new SensorConfigDialog(sb, mManager).showDialog();
				}
			}
		}));

		mDetails.setEditable(false);
		JScrollPane details = new JScrollPane(mDetails);
		details.setBorder(BorderFactory.createTitledBorder("The device"));

		JPanel notYet = new JPanel(new FlowLayout(FlowLayout.LEFT));
		notYet.add(new JLabel("<html><i>Not in the Java driver yet (see README): the data-rate test, the factory self-test, "
				+ "the names, the SD card over Bluetooth, the SD configuration file update, the clock drift, calibration.</i></html>"));

		JPanel tab = new JPanel(new BorderLayout());
		tab.add(commands, BorderLayout.NORTH);
		tab.add(details, BorderLayout.CENTER);
		tab.add(notYet, BorderLayout.SOUTH);
		return tab;
	}

	private static JButton button(String text, List<JButton> group, ActionListener l) {
		JButton b = new JButton(text);
		b.addActionListener(l);
		if (group != null) {
			group.add(b);
		}
		return b;
	}

	private void refreshPorts() {
		Object current = mPorts.getSelectedItem();
		mPorts.removeAllItems();
		List<String> names = new ArrayList<String>(Arrays.asList(SerialPortList.getPortNames()));
		for (String n : names) {
			mPorts.addItem(n);
		}
		if (current != null) {
			mPorts.setSelectedItem(current);
		}
	}

	// ------------------------------------------------------------------
	// Refresh
	// ------------------------------------------------------------------

	private void updateEnabled() {
		boolean ready = mDevice.isReady();
		boolean streaming = mDevice.isStreaming();
		boolean connected = mDevice.isConnected();
		for (JButton b : mReadyOnly) {
			b.setEnabled(ready);
		}
		for (JButton b : mReadyOrStreaming) {
			b.setEnabled(ready || streaming);
		}
		for (JButton b : mStreamingOnly) {
			b.setEnabled(streaming);
		}
		for (JButton b : mConnectedOnly) {
			b.setEnabled(connected);
		}
		mRecord.setText(mDevice.isRecording() ? "Stop recording" : "Record to CSV");
	}

	private void refresh() {
		long now = System.currentTimeMillis();
		boolean endWindow = now - mLastWindowEnd >= 1000;
		if (endWindow) {
			mLastWindowEnd = now;
		}
		ReceptionStats.Snapshot s = mDevice.snapshot(endWindow);
		ShimmerBluetooth sb = mDevice.shimmer();
		String state = mDevice.getState();
		set("Device", sb == null ? "-" : sb.getShimmerUserAssignedName());
		set("Link", mDevice.getPort() == null ? "-" : "Classic, " + mDevice.getPort());
		set("State", state);
		set("Rate Hz", sb == null ? "-" : CaptureDevice.trim(sb.getSamplingRateShimmer()));
		if (endWindow) {
			set("Rx /s", String.format("%.0f", s.rxPerSecond()));
		}
		set("Samples", Long.toString(s.samples));
		set("Missing", Long.toString(s.missing));
		set("Reception", Double.isNaN(s.reception) ? "-" : String.format("%.2f %%", s.reception));
		set("PRR", Double.isNaN(mDevice.getPrr()) ? "-" : String.format("%.2f %%", mDevice.getPrr()));
		set("Delay ms", Double.isNaN(s.delayMs) ? "-" : String.format("%.0f (worst %.0f)", s.delayMs, s.maxDelayMs));
		set("Last sample", lastSample(s.sinceLastMs, "STREAMING".equals(state)));
		double batt = mDevice.getBatteryPercent();
		set("Battery", Double.isNaN(batt) ? "-" : String.format("%.0f %%", batt));
		set("Link lost", Integer.toString(mDevice.getLinkLost()));
		set("Errors", mDevice.getErrors() == 0 ? "0" : mDevice.getErrors() + ": " + mDevice.getLastError());
		set("Data rate", "- (no data-rate test in the Java driver)");
		set("Hardware / firmware", mDevice.getSummary());

		mDetails.setText("Battery:   " + mDevice.getBatteryDetails() + "\n"
				+ "Status:    " + mDevice.getStatusDetails() + "\n"
				+ "MAC:       " + mDevice.getMac() + "\n"
				+ "Bluetooth module: " + mDevice.getBluetoothModule() + "\n"
				+ "Clock:     " + mDevice.getClockDetails() + "\n"
				+ "Logging:   " + (sb == null ? "-" : (sb.isSDLogging() ? "yes" : "no")) + "\n");

		File rec = mDevice.getRecordingFile();
		mRecordLabel.setText(rec == null ? (mDevice.isRecording() ? "waiting for the first sample" : " ") : rec.getName());
		updateEnabled();
	}

	/** The Last sample figure: how long ago, marked stalled after 1 s while streaming; - before any */
	static String lastSample(long sinceMs, boolean streaming) {
		if (sinceMs < 0) {
			return "-";
		}
		return String.format(java.util.Locale.ROOT, "%.1f s ago", sinceMs / 1000.0) + (streaming && sinceMs > 1000 ? ", stalled" : "");
	}

	private void set(String figure, String value) {
		for (int r = 0; r < mFigures.getRowCount(); r++) {
			if (figure.equals(mFigures.getValueAt(r, 0))) {
				if (!value.equals(mFigures.getValueAt(r, 1))) {
					mFigures.setValueAt(value, r, 1);
				}
				return;
			}
		}
	}

	private void openEventMarkers() {
		if (mEventMarkers == null || !mEventMarkers.isDisplayable()) {
			mEventMarkers = new EventMarkersFrame(new EventMarkersFrame.Host() {
				@Override
				public ShimmerDevice getConnectedDevice() {
					return mDevice.shimmer();
				}

				@Override
				public void plotEventRecord(ShimmerBluetooth shimmer, EventRecord record) {
					markEvent(record);
				}
			});
			mEventMarkers.setLocationRelativeTo(mFrame);
		}
		mEventMarkers.refresh();
		mEventMarkers.setVisible(true);
		mEventMarkers.toFront();
	}

	/**
	 * Marks an event record's edge on every panel shown, and logs it. The plot's x axis is the
	 * samples' device time, and a record carries the same clock in full: its low 24 bits line up
	 * with a packet's timestamp, which the driver unwraps from where the stream began. Called on
	 * the Swing thread.
	 */
	void markEvent(EventRecord record) {
		log("Event seq " + record.getSequenceNumber() + ": " + record.getDescription());
		double latest = mLastDeviceMs;
		if (!record.isEdge() || Double.isNaN(latest) || !mDevice.isStreaming()) {
			return;
		}
		double ticksPerMs = EventRecord.TICKS_PER_SECOND / 1000.0;
		double eventMs = record.getTicksOnStreamTimeline(latest * ticksPerMs) / ticksPerMs;
		java.awt.Color colour = record.getChannel() == EventRecord.CHANNEL_TTL ? new java.awt.Color(0x6A1B9A) : java.awt.Color.RED;
		String label = record.getChannel() == EventRecord.CHANNEL_TTL ? (record.isTtlHigh() ? "TTL high" : "TTL low")
				: (record.isButtonDown() ? "press" : "release");
		for (PlotView p : mPlots.values()) {
			if (!p.isEmpty()) {
				p.addMarker(eventMs, label, colour);
			}
		}
	}

	private void clearPlots() {
		for (PlotView p : mPlots.values()) {
			p.clear();
			p.setVisible(false);
		}
		mPlotPanel.revalidate();
	}

	// ------------------------------------------------------------------
	// CaptureDevice.Listener, on the driver's thread
	// ------------------------------------------------------------------

	@Override
	public void log(final String text) {
		final String line = mLogTime.format(new Date()) + "  " + text + "\n";
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				mLog.append(line);
				mLog.setCaretPosition(mLog.getDocument().getLength());
			}
		});
	}

	@Override
	public void stateChanged(String state) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				refresh();
			}
		});
	}

	@Override
	public void sample(ObjectCluster sample, double deviceMs, double hostMs) {
		if (Double.isNaN(deviceMs)) {
			return;
		}
		mLastDeviceMs = deviceMs;
		String format = mPlotRaw ? UNCAL : CAL;
		final List<PlotView> shown = new ArrayList<PlotView>();
		for (String channel : sample.getChannelNamesByInsertionOrder()) {
			String panel = Panels.of(channel);
			if (panel == null) {
				continue;
			}
			double v = sample.getFormatClusterValue(channel, format);
			if (Double.isNaN(v) && UNCAL.equals(format)) {
				continue; // calibrated only, such as Batt_Percentage
			}
			PlotView view = mPlots.get(panel);
			if (view.isEmpty()) {
				shown.add(view);
			}
			view.add(channel, deviceMs, v);
		}
		if (!shown.isEmpty()) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					for (PlotView v : shown) {
						v.setVisible(true);
					}
					mPlotPanel.revalidate();
				}
			});
		}
	}

}
