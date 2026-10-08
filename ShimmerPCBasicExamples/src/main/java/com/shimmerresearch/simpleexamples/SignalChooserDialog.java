package com.shimmerresearch.simpleexamples;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.guiUtilities.plot.BasicPlotManagerPC;

import info.monitorenter.gui.chart.Chart2D;

/**
 * Chooses signals to plot from a list given as {device name, channel, CAL or UNCAL, units} (see
 * {@link CaptureDriver#getSignalsForPlot()}), for one device or several, each under its name. Adds
 * them as SignalsToPlotDialog does, against each device's System_Timestamp_Plot, and removes those
 * unticked. Changes the plot manager only while holding its lock, which the capture app holds
 * while plotting a sample.
 */
final class SignalChooserDialog {

	private SignalChooserDialog() {
	}

	static void show(Component parent, List<String[]> signals, BasicPlotManagerPC plotManager, Chart2D chart) {
		JDialog dialog = new JDialog();
		dialog.setModal(true);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setTitle("Select Signals to Plot");
		dialog.setSize(340, 800);
		dialog.setLocationRelativeTo(parent);

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		JCheckBox[] boxes = new JCheckBox[signals.size()];
		String device = null;
		for (int i = 0; i < boxes.length; i++) {
			String[] s = signals.get(i);
			if (!s[0].equals(device)) {
				device = s[0];
				JLabel heading = new JLabel(device);
				heading.setFont(heading.getFont().deriveFont(Font.BOLD));
				heading.setBorder(BorderFactory.createEmptyBorder(i == 0 ? 2 : 10, 2, 2, 2));
				panel.add(heading);
			}
			boxes[i] = new JCheckBox(s[1] + " " + s[2], plotManager.checkIfPropertyExist(s));
			panel.add(boxes[i]);
		}
		dialog.getContentPane().add(new JScrollPane(panel), BorderLayout.CENTER);

		JButton set = new JButton("Set");
		set.addActionListener(e -> {
			synchronized (plotManager) {
				setSignals(signals, boxes, plotManager, chart);
			}
			dialog.dispose();
		});
		dialog.getContentPane().add(set, BorderLayout.SOUTH);
		dialog.setVisible(true);
	}

	private static void setSignals(List<String[]> signals, JCheckBox[] boxes, BasicPlotManagerPC plotManager, Chart2D chart) {
		for (int i = 0; i < boxes.length; i++) {
			String[] signal = signals.get(i);
			boolean plotted = plotManager.checkIfPropertyExist(signal);
			try {
				if (boxes[i].isSelected() && !plotted) {
					plotManager.addSignal(signal, chart);
					plotManager.addXAxis(new String[] { signal[0],
							Configuration.Shimmer3.ObjectClusterSensorName.SYSTEM_TIMESTAMP_PLOT, CHANNEL_TYPE.CAL.toString() });
				} else if (!boxes[i].isSelected() && plotted) {
					plotManager.removeSignal(signal);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
}
