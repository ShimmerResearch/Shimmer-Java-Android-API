package com.shimmerresearch.simpleexamples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import info.monitorenter.gui.chart.Chart2D;
import info.monitorenter.gui.chart.IAxis.AxisTitle;
import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.rangepolicies.RangePolicyMinimumViewport;
import info.monitorenter.gui.chart.traces.Trace2DLtd;
import info.monitorenter.util.Range;

/**
 * Reception over time for every device {@link ShimmerBLECaptureExample} streams from, one point a
 * second from each device's {@link ReceptionStats} window: reception, samples received, and the
 * worst arrival delay. One colour per device across the three charts. Swing thread only.
 */
final class ReceptionCharts extends JPanel {

	/** Ten minutes at a point a second. */
	private static final int POINTS = 600;
	private static final Color[] COLOURS = { new Color(31, 119, 180), new Color(214, 39, 40), new Color(44, 160, 44),
			new Color(255, 127, 14), new Color(148, 103, 189), new Color(140, 86, 75), new Color(227, 119, 194),
			new Color(23, 190, 207) };

	private final Chart2D mReception = chart("%", new Range(0, 100));
	private final Chart2D mRate = chart("samples/s", new Range(0, 1));
	private final Chart2D mDelay = chart("ms", new Range(0, 10));
	private final Map<ConnectedShimmer, ITrace2D[]> mTraces = new HashMap<ConnectedShimmer, ITrace2D[]>();
	private final long mStartMs = System.currentTimeMillis();
	private int mNextColour = 0;

	ReceptionCharts() {
		super(new GridLayout(3, 1));
		add(titled(mReception, "Reception each second, by device timestamp (%)"));
		add(titled(mRate, "Samples received per second"));
		add(titled(mDelay, "Arrival delay, worst in each second (ms)"));
	}

	/** Adds this second's figures for a device that is streaming. */
	void plot(ConnectedShimmer shimmer, ReceptionStats.Snapshot s) {
		ITrace2D[] traces = mTraces.get(shimmer);
		if (traces == null) {
			Color colour = COLOURS[mNextColour++ % COLOURS.length];
			String name = shimmer.name() + " [" + shimmer.getDriver().label() + "]";
			traces = new ITrace2D[] { trace(mReception, name, colour), trace(mRate, name, colour), trace(mDelay, name, colour) };
			mTraces.put(shimmer, traces);
		}
		double x = (System.currentTimeMillis() - mStartMs) / 1000.0;
		double reception = s.windowReceptionPercent();
		if (!Double.isNaN(reception)) {
			traces[0].addPoint(x, reception);
		}
		traces[1].addPoint(x, s.windowRate());
		if (!Double.isNaN(s.windowMaxDelayMs)) {
			traces[2].addPoint(x, s.windowMaxDelayMs);
		}
	}

	void remove(ConnectedShimmer shimmer) {
		ITrace2D[] traces = mTraces.remove(shimmer);
		if (traces != null) {
			mReception.removeTrace(traces[0]);
			mRate.removeTrace(traces[1]);
			mDelay.removeTrace(traces[2]);
		}
	}

	private static Chart2D chart(String units, Range minimumRange) {
		Chart2D chart = new Chart2D();
		chart.getAxisX().setAxisTitle(new AxisTitle("s"));
		chart.getAxisY().setAxisTitle(new AxisTitle(units));
		chart.getAxisY().setRangePolicy(new RangePolicyMinimumViewport(minimumRange));
		return chart;
	}

	private static ITrace2D trace(Chart2D chart, String name, Color colour) {
		ITrace2D trace = new Trace2DLtd(POINTS, name);
		trace.setColor(colour);
		chart.addTrace(trace);
		return trace;
	}

	private static JPanel titled(Chart2D chart, String title) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createTitledBorder(title));
		panel.add(chart, BorderLayout.CENTER);
		return panel;
	}
}
