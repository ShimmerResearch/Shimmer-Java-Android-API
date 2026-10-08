package com.shimmerresearch.simpleexamples;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

/**
 * The device table of {@link ShimmerBLECaptureExample}: one row per {@link ConnectedShimmer}, its
 * driver's figures and its {@link ReceptionStats}. Swing thread only.
 */
final class ConnectedShimmerTableModel extends AbstractTableModel {

	private static final Color AMBER = new Color(255, 226, 160);
	private static final Color RED = new Color(255, 185, 185);
	/** No sample for this long while streaming marks the row as stalled. */
	private static final long STALL_MS = 1000;

	/** The test figures first; the long hardware text last, where the table scrolls to it. */
	private enum Column {
		DEVICE("Device", 130, "The advertised BLE name; hover for its BLE id"),
		DRIVER("Driver", 110, "shimmerbluetooth: ShimmerBLENative (the Java driver). rust-logandstream: the Rust LogAndStream library"),
		STATE("State", 90, "The driver's state"),
		RATE("Rate Hz", 55, "The sampling rate configured on the device"),
		RECEIVED("Rx /s", 48, "Samples received in the last second"),
		SAMPLES("Samples", 60, "Samples received since streaming started"),
		MISSING("Missing", 56, "Samples missing, by gaps in the device's own timestamps, counted the same for both drivers"),
		RECEPTION("Reception", 68, "Received / (received + missing), by device timestamp"),
		DRIVER_PRR("Driver PRR", 72, "The packet reception rate as the driver itself counts it (Packet_Reception_Rate_Trial)"),
		DELAY("Delay ms", 85, "Arrival delay now / worst: PC arrival time minus device time, relative to the least-delayed sample. Growing means the link is falling behind"),
		LAST("Last sample", 74, "How long ago the last sample arrived"),
		LINK_LOSSES("Link lost", 58, "Times the BLE link was lost"),
		ERRORS("Errors", 46, "Errors the driver reported; hover for the last one"),
		DATA_RATE("Data rate", 130, "The firmware's data-rate (throughput) test, in KiB/s and packets missing; Rust LogAndStream only"),
		MTU("MTU", 40, "The BLE MTU negotiated for the connection"),
		HARDWARE("Hardware / firmware", 420, "What the device reported in the handshake");

		final String title;
		final int width;
		final String tip;

		Column(String title, int width, String tip) {
			this.title = title;
			this.width = width;
			this.tip = tip;
		}
	}

	private static final Column[] COLUMNS = Column.values();

	private final List<ConnectedShimmer> mRows = new ArrayList<ConnectedShimmer>();

	void add(ConnectedShimmer shimmer) {
		mRows.add(shimmer);
		fireTableRowsInserted(mRows.size() - 1, mRows.size() - 1);
	}

	void remove(ConnectedShimmer shimmer) {
		int row = mRows.indexOf(shimmer);
		if (row >= 0) {
			mRows.remove(row);
			fireTableRowsDeleted(row, row);
		}
	}

	ConnectedShimmer get(int row) {
		return mRows.get(row);
	}

	List<ConnectedShimmer> all() {
		return new ArrayList<ConnectedShimmer>(mRows);
	}

	/** The row for this BLE device, or null. */
	ConnectedShimmer find(String deviceId) {
		for (ConnectedShimmer s : mRows) {
			if (s.getDevice().getId().equals(deviceId)) {
				return s;
			}
		}
		return null;
	}

	void refresh() {
		if (!mRows.isEmpty()) {
			fireTableRowsUpdated(0, mRows.size() - 1);
		}
	}

	@Override
	public int getRowCount() {
		return mRows.size();
	}

	@Override
	public int getColumnCount() {
		return COLUMNS.length;
	}

	@Override
	public String getColumnName(int column) {
		return COLUMNS[column].title;
	}

	@Override
	public Object getValueAt(int row, int column) {
		ConnectedShimmer s = mRows.get(row);
		CaptureDriver d = s.getDriver();
		ReceptionStats.Snapshot r = s.getSnapshot();
		switch (COLUMNS[column]) {
		case DEVICE:
			return s.name();
		case DRIVER:
			return d.label();
		case STATE:
			return s.getState();
		case HARDWARE:
			return orDash(d.getDeviceSummary());
		case MTU:
			return d.isConnected() ? String.valueOf(d.getMtu()) : "-";
		case RATE:
			return d.isConnected() ? ConnectedShimmer.hz(d.getSamplingRate()) : "-";
		case RECEIVED:
			return s.isStreaming() ? ConnectedShimmer.number(r.windowRate(), 1) : "-";
		case SAMPLES:
			return String.valueOf(r.samples);
		case MISSING:
			return String.valueOf(r.missing);
		case RECEPTION:
			return ConnectedShimmer.percent(r.receptionPercent);
		case DRIVER_PRR:
			return ConnectedShimmer.percent(d.getPacketReceptionRate());
		case DELAY:
			return ConnectedShimmer.number(r.delayMs, 0) + " / " + ConnectedShimmer.number(r.maxDelayMs, 0);
		case LAST:
			return r.msSinceLastSample < 0 ? "-" : String.format("%.1f s", r.msSinceLastSample / 1000.0);
		case LINK_LOSSES:
			return String.valueOf(s.getLinkLosses());
		case ERRORS:
			return String.valueOf(s.getErrors());
		case DATA_RATE:
			return d.canTestDataRate() ? orDash(s.getDataRate()) : "n/a";
		default:
			return "";
		}
	}

	/** The cell's background if it needs attention, otherwise null. */
	private Color warning(int row, int column) {
		ConnectedShimmer s = mRows.get(row);
		ReceptionStats.Snapshot r = s.getSnapshot();
		switch (COLUMNS[column]) {
		case STATE:
			String state = s.getState();
			return ConnectedShimmer.isLinkLost(state) || state.toLowerCase().contains("failed") ? RED : null;
		case MISSING:
			return r.missing > 0 ? AMBER : null;
		case RECEPTION:
			return Double.isNaN(r.receptionPercent) || r.receptionPercent >= 99.95 ? null
					: r.receptionPercent < 95 ? RED : AMBER;
		case LAST:
			return s.isStreaming() && r.msSinceLastSample > STALL_MS ? RED : null;
		case LINK_LOSSES:
			return s.getLinkLosses() > 0 ? AMBER : null;
		case ERRORS:
			return s.getErrors() > 0 ? RED : null;
		default:
			return null;
		}
	}

	private String tooltip(int row, int column) {
		ConnectedShimmer s = mRows.get(row);
		switch (COLUMNS[column]) {
		case DEVICE:
			return s.getDevice().toString();
		case HARDWARE:
			return s.getDriver().getDeviceSummary();
		case ERRORS:
			return s.getLastError();
		case DATA_RATE:
			return s.getDataRateText();
		default:
			return null;
		}
	}

	/** A table on this model, with its column widths, warning colours and tooltips. */
	JTable createTable() {
		JTable table = new JTable(this) {
			@Override
			public String getToolTipText(MouseEvent e) {
				int row = rowAtPoint(e.getPoint());
				int column = columnAtPoint(e.getPoint());
				return row < 0 || column < 0 ? null : tooltip(convertRowIndexToModel(row), convertColumnIndexToModel(column));
			}

			@Override
			protected JTableHeader createDefaultTableHeader() {
				return new JTableHeader(columnModel) {
					@Override
					public String getToolTipText(MouseEvent e) {
						int column = columnAtPoint(e.getPoint());
						return column < 0 ? null : COLUMNS[convertColumnIndexToModel(column)].tip;
					}
				};
			}
		};
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		for (int i = 0; i < COLUMNS.length; i++) {
			table.getColumnModel().getColumn(i).setPreferredWidth(COLUMNS[i].width);
		}
		table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
					int row, int column) {
				super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
				if (!isSelected) {
					Color c = warning(t.convertRowIndexToModel(row), t.convertColumnIndexToModel(column));
					setBackground(c != null ? c : t.getBackground());
				}
				return this;
			}
		});
		return table;
	}

	private static String orDash(String s) {
		return s == null ? "-" : s;
	}
}
