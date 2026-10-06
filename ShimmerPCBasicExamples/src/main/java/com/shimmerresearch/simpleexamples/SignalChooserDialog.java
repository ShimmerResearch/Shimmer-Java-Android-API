package com.shimmerresearch.simpleexamples;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.driver.Configuration;
import com.shimmerresearch.guiUtilities.plot.BasicPlotManagerPC;

import info.monitorenter.gui.chart.Chart2D;

/**
 * Chooses signals to plot from a list given as {device name, channel, CAL or UNCAL, units}, for a
 * backend with no ShimmerDevice to describe its channels (see
 * {@link CaptureBackend#getSignalsForPlot()}). Adds them as SignalsToPlotDialog does, against
 * the device's System_Timestamp_Plot.
 */
final class SignalChooserDialog {

	private SignalChooserDialog() {
	}

	static void show(Component parent, List<String[]> signals, BasicPlotManagerPC plotManager, Chart2D chart) {
		JDialog dialog = new JDialog();
		dialog.setModal(true);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setTitle("Select Signals to Plot");
		dialog.setSize(300, 800);
		dialog.setLocationRelativeTo(parent);

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		JCheckBox[] boxes = new JCheckBox[signals.size()];
		for (int i = 0; i < boxes.length; i++) {
			String[] s = signals.get(i);
			boxes[i] = new JCheckBox(s[1] + " " + s[2], plotManager.checkIfPropertyExist(s));
			panel.add(boxes[i]);
		}
		dialog.getContentPane().add(new JScrollPane(panel), BorderLayout.CENTER);

		JButton set = new JButton("Set");
		set.addActionListener(e -> {
			for (int i = 0; i < boxes.length; i++) {
				String[] signal = signals.get(i);
				if (boxes[i].isSelected() && !plotManager.checkIfPropertyExist(signal)) {
					try {
						plotManager.addSignal(signal, chart);
						plotManager.addXAxis(new String[] { signal[0],
								Configuration.Shimmer3.ObjectClusterSensorName.SYSTEM_TIMESTAMP_PLOT, CHANNEL_TYPE.CAL.toString() });
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
			dialog.dispose();
		});
		dialog.getContentPane().add(set, BorderLayout.SOUTH);
		dialog.setVisible(true);
	}
}
