package com.shimmerresearch.simpleexamples;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import com.shimmerresearch.bluetooth.ShimmerBluetooth;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.BT_STATE;
import com.shimmerresearch.bluetooth.ShimmerBluetooth.EventChannelListener;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driver.shimmer2r3.ButtonLogMode;
import com.shimmerresearch.driver.shimmer2r3.EventChannelStatus;
import com.shimmerresearch.driver.shimmer2r3.EventFile;
import com.shimmerresearch.driver.shimmer2r3.EventRecord;

/**
 * A bench window for the Shimmer3 / Shimmer3R event channel (DEV-971), to test
 * the Java driver's support against a unit, as Shimmer Capture web does for the
 * web SDK:
 * <ul>
 * <li>whether the unit has the event channel;</li>
 * <li>configuration byte 232: the button mode, what starts logging, the TTL
 * input, written and read back;</li>
 * <li>the live records after an opt-in, with sequence gaps flagged, and each
 * one marked on the main window's plot;</li>
 * <li>the event channel status;</li>
 * <li>an event file from a card, its header and records.</li>
 * </ul>
 */
public class EventMarkersFrame extends JFrame implements EventChannelListener {

	private static final long serialVersionUID = 1L;

	/** Supplies the connected device, and marks a record on the plot */
	public interface Host {
		ShimmerDevice getConnectedDevice();
		void plotEventRecord(ShimmerBluetooth shimmer, EventRecord record);
	}

	private static final String[] BUTTON_MODES = {
			"0  As before (User Button setting decides)",
			"1  Disabled",
			"2  Single press starts / stops logging",
			"3  Double press starts / stops logging",
			"4  Double press starts / stops, every press recorded",
			"5  Every press recorded, logging untouched",
	};
	private static final String[] LOG_TRIGGERS = {
			"0  A host command only",
			"1  A host command or an undock",
			"2  Nothing: logging off",
	};

	private final Host mHost;
	private ShimmerBluetooth mAttachedTo = null;

	private final JLabel mLblSupport = new JLabel(" ");
	private final JComboBox<String> mComboButtonMode = new JComboBox<String>(BUTTON_MODES);
	private final JComboBox<String> mComboLogTriggers = new JComboBox<String>(LOG_TRIGGERS);
	private final JCheckBox mChkTtl = new JCheckBox("Record TTL input");
	private final JLabel mLblConfig = new JLabel(" ");
	private final JLabel mLblCounters = new JLabel(" ");
	private final JTextArea mTxtStatus = new JTextArea(5, 40);
	private final JTextArea mTxtFile = new JTextArea(6, 40);

	private static final String[] LIVE_COLUMNS = {"#", "Received", "Seq", "Shimmer time (UTC)", "Record", "Flags", "State", "Dropped", "Note"};
	private static final String[] FILE_COLUMNS = {"#", "Seq", "From file open (s)", "Shimmer time (UTC)", "Record", "Flags", "State", "Dropped"};
	private final DefaultTableModel mLiveModel = new DefaultTableModel(LIVE_COLUMNS, 0) {
		private static final long serialVersionUID = 1L;
		@Override public boolean isCellEditable(int r, int c) { return false; }
	};
	private final DefaultTableModel mFileModel = new DefaultTableModel(FILE_COLUMNS, 0) {
		private static final long serialVersionUID = 1L;
		@Override public boolean isCellEditable(int r, int c) { return false; }
	};

	private Integer mLastSeq = null;
	private int mGaps = 0;

	private final SimpleDateFormat mUtc = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	private final SimpleDateFormat mLocal = new SimpleDateFormat("HH:mm:ss.SSS");

	public EventMarkersFrame(Host host) {
		super("Event markers (DEV-971)");
		mHost = host;
		mUtc.setTimeZone(TimeZone.getTimeZone("UTC"));
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setSize(new Dimension(1000, 720));
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				detach();
			}
		});
		buildUi();
		refresh();
	}

	private void buildUi() {
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

		// --- Device
		JPanel device = new JPanel(new FlowLayout(FlowLayout.LEFT));
		device.setBorder(BorderFactory.createTitledBorder("Device"));
		JButton btnRefresh = button("Refresh", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refresh();
			}
		});
		JButton btnProbe = button("Ask again", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ShimmerBluetooth sb = shimmer();
				if(sb==null) {
					return;
				}
				if(sb.isStreaming()) {
					warn("Stop streaming first: the driver does not ask a streaming Shimmer.");
					return;
				}
				sb.readEventChannelSupport();
				refreshLater();
			}
		});
		device.add(mLblSupport);
		device.add(btnRefresh);
		device.add(btnProbe);
		top.add(device);

		// --- Configuration byte 232
		JPanel config = new JPanel(new GridBagLayout());
		config.setBorder(BorderFactory.createTitledBorder("Configuration byte 232 (written with the whole configuration)"));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 4, 2, 4);
		c.anchor = GridBagConstraints.WEST;
		c.gridx = 0; c.gridy = 0;
		config.add(new JLabel("Button"), c);
		c.gridx = 1;
		config.add(mComboButtonMode, c);
		c.gridx = 0; c.gridy = 1;
		config.add(new JLabel("Logging starts on"), c);
		c.gridx = 1;
		config.add(mComboLogTriggers, c);
		c.gridx = 0; c.gridy = 2;
		config.add(mChkTtl, c);
		JPanel configButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		configButtons.add(button("Write", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				writeButtonLogMode();
			}
		}));
		configButtons.add(button("Read back", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ShimmerBluetooth sb = shimmer();
				if(sb==null) {
					return;
				}
				if(sb.isStreaming() || sb.isSDLogging()) {
					warn("Stop streaming and logging first: the firmware refuses configuration reads and writes while sensing.");
					return;
				}
				sb.readConfigBytes();
				refreshLater();
			}
		}));
		c.gridx = 1;
		config.add(configButtons, c);
		c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
		config.add(mLblConfig, c);
		top.add(config);

		// --- Live records
		JPanel live = new JPanel(new FlowLayout(FlowLayout.LEFT));
		live.setBorder(BorderFactory.createTitledBorder("Live records over Bluetooth (this connection only)"));
		live.add(button("Opt in", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				optIn(true);
			}
		}));
		live.add(button("Opt out", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				optIn(false);
			}
		}));
		live.add(button("Get status", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ShimmerBluetooth sb = shimmer();
				if(sb==null) {
					return;
				}
				attach(sb);
				if(!sb.readEventChannelStatus()) {
					warn(sb.isStreaming()
							? "Not while streaming: the status reply is not wrapped as replies between data packets are."
							: "Not sent: this unit is not known to have the event channel.");
				}
			}
		}));
		live.add(button("Clear list", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mLiveModel.setRowCount(0);
				mLastSeq = null;
				mGaps = 0;
				updateCounters();
			}
		}));
		live.add(mLblCounters);
		top.add(live);

		JPanel north = new JPanel(new BorderLayout());
		north.add(top, BorderLayout.CENTER);
		mTxtStatus.setEditable(false);
		JScrollPane statusScroll = new JScrollPane(mTxtStatus);
		statusScroll.setBorder(BorderFactory.createTitledBorder("Event channel status"));
		statusScroll.setPreferredSize(new Dimension(320, 100));
		north.add(statusScroll, BorderLayout.EAST);

		// --- Tables
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Live records", new JScrollPane(new JTable(mLiveModel)));

		JPanel filePanel = new JPanel(new BorderLayout());
		JPanel fileButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
		fileButtons.add(button("Open event file...", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openEventFile();
			}
		}));
		fileButtons.add(new JLabel("events/<experiment>/<session>/<NNN> on the card, beside data/<experiment>/<session>/<NNN>"));
		filePanel.add(fileButtons, BorderLayout.NORTH);
		mTxtFile.setEditable(false);
		JPanel fileCentre = new JPanel(new BorderLayout());
		fileCentre.add(new JScrollPane(mTxtFile), BorderLayout.NORTH);
		fileCentre.add(new JScrollPane(new JTable(mFileModel)), BorderLayout.CENTER);
		filePanel.add(fileCentre, BorderLayout.CENTER);
		tabs.addTab("Event file", filePanel);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(north, BorderLayout.NORTH);
		getContentPane().add(tabs, BorderLayout.CENTER);
	}

	private static JButton button(String text, ActionListener l) {
		JButton b = new JButton(text);
		b.addActionListener(l);
		return b;
	}

	private void warn(String message) {
		JOptionPane.showMessageDialog(this, message, "Event markers", JOptionPane.WARNING_MESSAGE);
	}

	/** The connected Shimmer3 / Shimmer3R, or null with a message */
	private ShimmerBluetooth shimmer() {
		ShimmerDevice device = mHost.getConnectedDevice();
		if(!(device instanceof ShimmerBluetooth)) {
			warn("Connect a Shimmer3 or Shimmer3R first.");
			return null;
		}
		ShimmerBluetooth sb = (ShimmerBluetooth) device;
		BT_STATE state = sb.getBluetoothRadioState();
		if(state==null || state==BT_STATE.DISCONNECTED || state==BT_STATE.CONNECTION_LOST || state==BT_STATE.CONNECTING) {
			warn("The Shimmer is not connected.");
			return null;
		}
		return sb;
	}

	private void attach(ShimmerBluetooth sb) {
		if(mAttachedTo!=sb) {
			detach();
			sb.addEventChannelListener(this);
			mAttachedTo = sb;
		}
	}

	private void detach() {
		if(mAttachedTo!=null) {
			mAttachedTo.removeEventChannelListener(this);
			mAttachedTo = null;
		}
	}

	/** Re-reads what the driver knows of the connected unit */
	public void refresh() {
		ShimmerDevice device = mHost.getConnectedDevice();
		if(!(device instanceof ShimmerBluetooth)) {
			mLblSupport.setText("No Shimmer3 / Shimmer3R connected");
			mLblConfig.setText(" ");
			return;
		}
		ShimmerBluetooth sb = (ShimmerBluetooth) device;
		attach(sb);
		Boolean supported = sb.isEventChannelSupported();
		mLblSupport.setText(sb.getFirmwareVersionParsed() + " - event channel: "
				+ (supported==null? "not asked yet" : (supported? "yes" : "no")));
		if(sb.isButtonLogModeKnown()) {
			int buttonMode = sb.getButtonMode();
			int logTriggers = sb.getLogTriggers();
			if(buttonMode<BUTTON_MODES.length) {
				mComboButtonMode.setSelectedIndex(buttonMode);
			}
			if(logTriggers<LOG_TRIGGERS.length) {
				mComboLogTriggers.setSelectedIndex(logTriggers);
			}
			mChkTtl.setSelected(sb.isTtlInputEnabled());
			mLblConfig.setText(String.format("On the device: byte 232 = 0x%02X (button mode %d, log triggers %d, TTL %s)%s",
					sb.getButtonLogModeCorrected(), buttonMode, logTriggers, sb.isTtlInputEnabled()? "on" : "off",
					ButtonLogMode.isRecordingButtonPresses(sb.getButtonLogModeCorrected())? " - presses are recorded" : " - presses are not recorded"));
		}
		else {
			mLblConfig.setText("Byte 232 not read from this unit yet");
		}
		updateCounters();
	}

	private void refreshLater() {
		javax.swing.Timer t = new javax.swing.Timer(1500, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refresh();
			}
		});
		t.setRepeats(false);
		t.start();
	}

	private void writeButtonLogMode() {
		ShimmerBluetooth sb = shimmer();
		if(sb==null) {
			return;
		}
		if(!Boolean.TRUE.equals(sb.isEventChannelSupported())) {
			warn("This unit is not known to have the event channel, so byte 232 is not written.");
			return;
		}
		if(sb.isStreaming() || sb.isSDLogging()) {
			warn("Stop streaming and logging first: the firmware refuses configuration writes while sensing.");
			return;
		}
		int buttonMode = mComboButtonMode.getSelectedIndex();
		int logTriggers = mComboLogTriggers.getSelectedIndex();
		boolean ttl = mChkTtl.isSelected();
		sb.setButtonLogMode(buttonMode, logTriggers, ttl);
		// The whole configuration, read back after: the firmware corrects the byte, and the read-back shows what it kept
		sb.writeConfigBytes(true);
		mLblConfig.setText("Written; reading back...");
		refreshLater();
	}

	private void optIn(boolean enable) {
		ShimmerBluetooth sb = shimmer();
		if(sb==null) {
			return;
		}
		attach(sb);
		if(enable) {
			mLastSeq = null;
		}
		if(!sb.writeEventStream(enable)) {
			warn("Not sent: the firmware is older than SET_FEATURE or does not have the event channel.");
		}
		updateCounters();
	}

	private void updateCounters() {
		ShimmerBluetooth sb = mAttachedTo;
		if(sb==null) {
			mLblCounters.setText(" ");
			return;
		}
		mLblCounters.setText("Opted in: " + (sb.isEventStreamRequested()? "yes" : "no")
				+ "   frames: " + sb.getEventFramesReceived()
				+ "   bad CRC: " + sb.getEventFramesBadCrc()
				+ "   sequence gaps: " + mGaps);
	}

	private String utc(long ticks) {
		return mUtc.format(new Date(Math.round((double)ticks*1000.0/EventRecord.TICKS_PER_SECOND)));
	}

	private static String flags(EventRecord r) {
		StringBuilder sb = new StringBuilder();
		if((r.getFlags()&EventRecord.FLAG_HW_CAPTURE)!=0) sb.append("timer ");
		if((r.getFlags()&EventRecord.FLAG_SYNTHESIZED)!=0) sb.append("synth ");
		if((r.getFlags()&EventRecord.FLAG_LATE)!=0) sb.append("late ");
		if((r.getFlags()&EventRecord.FLAG_STATE_STALE)!=0) sb.append("stale ");
		if((r.getFlags()&EventRecord.FLAG_FINAL)!=0) sb.append("final ");
		return sb.length()==0? "-" : sb.toString().trim();
	}

	private static String state(EventRecord r) {
		return "btn " + (r.isButtonDown()? "down" : "up") + ", ttl " + (r.isTtlHigh()? "high" : "low");
	}

	// ------------------------------------------------------------------
	// EventChannelListener, called on the driver's IO thread
	// ------------------------------------------------------------------

	@Override
	public void onEventRecord(final ShimmerBluetooth shimmer, final EventRecord record) {
		final long receivedAt = System.currentTimeMillis();
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				String note = "";
				int seq = record.getSequenceNumber();
				if(record.getType()==EventRecord.TYPE_INITIAL_STATE && seq==0) {
					note = "opt-in";
				}
				else if(mLastSeq!=null && seq!=((mLastSeq+1)&0xFFFF)) {
					int lost = (seq-mLastSeq-1)&0xFFFF;
					note = "GAP: " + lost + " record(s) lost on the link";
					mGaps++;
				}
				mLastSeq = seq;
				mLiveModel.addRow(new Object[] {
						mLiveModel.getRowCount()+1,
						mLocal.format(new Date(receivedAt)),
						seq,
						utc(record.getTicks()),
						record.getDescription(),
						flags(record),
						state(record),
						record.getDropped(),
						note});
				updateCounters();
				mHost.plotEventRecord(shimmer, record);
			}
		});
	}

	@Override
	public void onEventChannelStatus(ShimmerBluetooth shimmer, final EventChannelStatus s) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				mTxtStatus.setText(
						"Inputs enabled:  " + ((s.getInputsEnabled()&EventRecord.INPUT_BUTTON)!=0? "button " : "")
								+ ((s.getInputsEnabled()&EventRecord.INPUT_TTL)!=0? "TTL" : "") + "\n"
						+ "Stream on:  " + s.isStreaming() + "\n"
						+ "Event file open:  " + s.isFileOpen() + "   error: " + s.isFileError() + "\n"
						+ "TTL armed:  " + s.isTtlArmed() + "   throttled: " + s.isTtlThrottled() + "\n"
						+ "State now:  btn " + (((s.getState()&EventRecord.INPUT_BUTTON)!=0)? "down" : "up")
								+ ", ttl " + (((s.getState()&EventRecord.INPUT_TTL)!=0)? "high" : "low") + "\n"
						+ "Next seq:  " + s.getNextSequenceNumber()
								+ "   lost this session: " + s.getSessionDropped()
								+ "   lost on link: " + s.getLinkLost());
			}
		});
	}

	// ------------------------------------------------------------------
	// Event files
	// ------------------------------------------------------------------

	private void openEventFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Open an event file (events/<experiment>/<session>/<NNN>)");
		if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION) {
			return;
		}
		File f = chooser.getSelectedFile();
		EventFile ef;
		try {
			ef = EventFile.parse(f);
		} catch (Exception e) {
			warn("Could not read " + f.getName() + ": " + e.getMessage());
			return;
		}
		File data = companionDataFile(f);
		StringBuilder sb = new StringBuilder();
		sb.append(f.getAbsolutePath()).append("\n");
		sb.append("Hardware ID ").append(ef.getHardwareId())
				.append(ef.getHardwareId()==10? " (Shimmer3R)" : ef.getHardwareId()==3? " (Shimmer3)" : "")
				.append(",  firmware ").append(ef.getFirmwareId()).append(" v").append(ef.getFirmwareMajor())
				.append(".").append(ef.getFirmwareMinor()).append(".").append(ef.getFirmwareInternal()).append("\n");
		sb.append("Opened ").append(ef.getOpenReason()==EventFile.OPEN_REASON_SPLIT? "at an hourly split" : "at the session start")
				.append(" at ").append(mUtc.format(new Date(Math.round(ef.getUnixMs(new EventRecord(0, 0, 0, ef.getFileOpenTicks(), 0, 0, 0))))))
				.append(" UTC (file-open ticks 0x").append(Long.toHexString(ef.getFileOpenTicks())).append(")\n");
		sb.append("Records: ").append(ef.getRecords().size())
				.append(",  inputs: ").append((ef.getInputs()&EventRecord.INPUT_BUTTON)!=0? "button " : "")
				.append((ef.getInputs()&EventRecord.INPUT_TTL)!=0? "TTL" : "")
				.append(",  ").append(ef.isComplete()? "complete (ends with a final record)" : "NOT complete: no final record, cut short")
				.append(ef.getTrailingBytes()>0? ", " + ef.getTrailingBytes() + " trailing bytes" : "").append("\n");
		sb.append("Data file: ").append(data==null? "not found by the card layout" : data.getPath() + (data.exists()? "" : " (not present)"));
		mTxtFile.setText(sb.toString());

		mFileModel.setRowCount(0);
		int i = 0;
		for(EventRecord r:ef.getRecords()) {
			mFileModel.addRow(new Object[] {
					i++,
					r.getSequenceNumber(),
					String.format("%+.6f", r.getSecondsFrom(ef.getFileOpenTicks())),
					mUtc.format(new Date(Math.round(ef.getUnixMs(r)))),
					r.getDescription(),
					flags(r),
					state(r),
					r.getDropped()});
		}
	}

	/** events/X/NNN pairs with data/X/NNN */
	private static File companionDataFile(File eventFile) {
		File session = eventFile.getParentFile();
		File experiment = session==null? null : session.getParentFile();
		File eventsRoot = experiment==null? null : experiment.getParentFile();
		if(eventsRoot==null || !"events".equalsIgnoreCase(eventsRoot.getName())) {
			return null;
		}
		return new File(new File(new File(new File(eventsRoot.getParentFile(), "data"), experiment.getName()), session.getName()), eventFile.getName());
	}

}
