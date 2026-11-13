package com.shimmerresearch.capture;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

/**
 * One plot panel: the channels of one group (Panels), each a line over a window of 5, 10 or 30 s
 * of device time, the y axis fitted to what is shown. Points are added on any thread; painting is
 * on the Swing thread.
 */
public final class PlotView extends JPanel {

	private static final long serialVersionUID = 1L;
	/** 30 s at 1024 Hz, with room */
	private static final int CAPACITY = 32768;
	private static final Color[] COLORS = {
			new Color(0x1F77B4), new Color(0xD62728), new Color(0x2CA02C), new Color(0xFF7F0E),
			new Color(0x9467BD), new Color(0x8C564B), new Color(0xE377C2), new Color(0x17BECF)};

	private static final class Series {
		final double[] t = new double[CAPACITY];
		final double[] v = new double[CAPACITY];
		int start;
		int size;

		void add(double time, double value) {
			int end = (start + size) % CAPACITY;
			t[end] = time;
			v[end] = value;
			if (size < CAPACITY) {
				size++;
			} else {
				start = (start + 1) % CAPACITY;
			}
		}
	}

	/** An event marker: a vertical line at a device time, with a short label */
	private static final class Marker {
		final double t;
		final String label;
		final Color color;

		Marker(double t, String label, Color color) {
			this.t = t;
			this.label = label;
			this.color = color;
		}
	}

	private final String mTitle;
	private final Map<String, Series> mSeries = new LinkedHashMap<String, Series>();
	private final List<Marker> mMarkers = new ArrayList<Marker>();
	private volatile double mWindowMs = 10000;

	public PlotView(String title) {
		mTitle = title;
		setPreferredSize(new Dimension(600, 160));
		setBackground(Color.WHITE);
	}

	public String getTitle() {
		return mTitle;
	}

	public void setWindowSeconds(double seconds) {
		mWindowMs = seconds * 1000.0;
	}

	public synchronized void add(String channel, double timeMs, double value) {
		Series s = mSeries.get(channel);
		if (s == null) {
			s = new Series();
			mSeries.put(channel, s);
		}
		s.add(timeMs, value);
	}

	public synchronized void clear() {
		mSeries.clear();
		mMarkers.clear();
	}

	/** Marks an event at {@code timeMs} of device time, on the same axis as the samples */
	public synchronized void addMarker(double timeMs, String label, Color color) {
		mMarkers.add(new Marker(timeMs, label, color));
		if (mMarkers.size() > 500) {
			mMarkers.remove(0);
		}
	}

	public synchronized boolean isEmpty() {
		return mSeries.isEmpty();
	}

	@Override
	protected synchronized void paintComponent(Graphics g0) {
		super.paintComponent(g0);
		Graphics2D g = (Graphics2D) g0;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		int left = 64;
		int right = 8;
		int top = 18;
		int bottom = 16;
		g.setColor(Color.DARK_GRAY);
		g.drawString(mTitle, left, 13);

		double latest = Double.NEGATIVE_INFINITY;
		for (Series s : mSeries.values()) {
			if (s.size > 0) {
				latest = Math.max(latest, s.t[(s.start + s.size - 1) % CAPACITY]);
			}
		}
		if (latest == Double.NEGATIVE_INFINITY) {
			return;
		}
		double from = latest - mWindowMs;
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (Series s : mSeries.values()) {
			for (int i = 0; i < s.size; i++) {
				int k = (s.start + i) % CAPACITY;
				if (s.t[k] >= from && !Double.isNaN(s.v[k]) && !Double.isInfinite(s.v[k])) {
					min = Math.min(min, s.v[k]);
					max = Math.max(max, s.v[k]);
				}
			}
		}
		if (min == Double.POSITIVE_INFINITY) {
			return;
		}
		if (max - min < 1e-9) {
			max += 0.5;
			min -= 0.5;
		}
		int plotW = Math.max(1, w - left - right);
		int plotH = Math.max(1, h - top - bottom);
		g.setColor(new Color(0xE0E0E0));
		g.drawRect(left, top, plotW, plotH);
		g.setColor(Color.GRAY);
		FontMetrics fm = g.getFontMetrics();
		String maxText = format(max);
		String minText = format(min);
		g.drawString(maxText, left - 4 - fm.stringWidth(maxText), top + fm.getAscent());
		g.drawString(minText, left - 4 - fm.stringWidth(minText), top + plotH);
		g.drawString(String.format("%.0f s", mWindowMs / 1000.0), left, h - 3);

		int colour = 0;
		int legendX = left + 80;
		g.setStroke(new BasicStroke(1.2f));
		for (Map.Entry<String, Series> e : mSeries.entrySet()) {
			Series s = e.getValue();
			Color c = COLORS[colour++ % COLORS.length];
			g.setColor(c);
			List<int[]> points = new ArrayList<int[]>();
			for (int i = 0; i < s.size; i++) {
				int k = (s.start + i) % CAPACITY;
				if (s.t[k] < from || Double.isNaN(s.v[k]) || Double.isInfinite(s.v[k])) {
					continue;
				}
				int x = left + (int) Math.round((s.t[k] - from) / mWindowMs * plotW);
				int y = top + plotH - (int) Math.round((s.v[k] - min) / (max - min) * plotH);
				points.add(new int[] {x, y});
			}
			int step = Math.max(1, points.size() / (plotW * 2));
			for (int i = step; i < points.size(); i += step) {
				int[] a = points.get(i - step);
				int[] b = points.get(i);
				g.drawLine(a[0], a[1], b[0], b[1]);
			}
			g.drawString(e.getKey(), legendX, 13);
			legendX += fm.stringWidth(e.getKey()) + 14;
		}

		g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {4f, 3f}, 0f));
		for (Marker m : mMarkers) {
			if (m.t < from || m.t > latest) {
				continue;
			}
			int x = left + (int) Math.round((m.t - from) / mWindowMs * plotW);
			g.setColor(m.color);
			g.drawLine(x, top, x, top + plotH);
			g.drawString(m.label, x + 3, top + plotH - 3);
		}
	}

	private static String format(double v) {
		double a = Math.abs(v);
		if (a != 0 && (a < 0.01 || a >= 1e6)) {
			return String.format("%.3g", v);
		}
		return String.format("%.2f", v);
	}

}
