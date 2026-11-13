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
 * on the Swing thread, and holds the lock only to copy out what it will draw.
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

	/** What one paint draws of a series: its points inside the window, copied out */
	private static final class Shown {
		final String name;
		final double[] t;
		final double[] v;

		Shown(String name, double[] t, double[] v) {
			this.name = name;
			this.t = t;
			this.v = v;
		}
	}

	/**
	 * Copies out what is inside the window, and the markers. This is all a paint does under the
	 * lock that add() takes on the driver's thread, so drawing never holds a sample up.
	 *
	 * @return the latest time of any series, or NaN where there is no point
	 */
	private synchronized double copyShown(double windowMs, List<Shown> shown, List<Marker> markers) {
		double latest = Double.NEGATIVE_INFINITY;
		for (Series s : mSeries.values()) {
			if (s.size > 0) {
				latest = Math.max(latest, s.t[(s.start + s.size - 1) % CAPACITY]);
			}
		}
		if (latest == Double.NEGATIVE_INFINITY) {
			return Double.NaN;
		}
		double from = latest - windowMs;
		for (Map.Entry<String, Series> e : mSeries.entrySet()) {
			Series s = e.getValue();
			// times rise, so the window is the newest points: count back to its start
			int n = 0;
			while (n < s.size && s.t[(s.start + s.size - 1 - n) % CAPACITY] >= from) {
				n++;
			}
			double[] t = new double[n];
			double[] v = new double[n];
			int first = s.start + s.size - n;
			for (int i = 0; i < n; i++) {
				int k = (first + i) % CAPACITY;
				t[i] = s.t[k];
				v[i] = s.v[k];
			}
			shown.add(new Shown(e.getKey(), t, v));
		}
		markers.addAll(mMarkers);
		return latest;
	}

	private static boolean finite(double v) {
		return !Double.isNaN(v) && !Double.isInfinite(v);
	}

	@Override
	protected void paintComponent(Graphics g0) {
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

		double windowMs = mWindowMs;
		List<Shown> shown = new ArrayList<Shown>();
		List<Marker> markers = new ArrayList<Marker>();
		double latest = copyShown(windowMs, shown, markers);
		if (Double.isNaN(latest)) {
			return;
		}
		double from = latest - windowMs;
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (Shown s : shown) {
			for (int i = 0; i < s.v.length; i++) {
				if (finite(s.v[i])) {
					min = Math.min(min, s.v[i]);
					max = Math.max(max, s.v[i]);
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
		g.drawString(String.format("%.0f s", windowMs / 1000.0), left, h - 3);

		int colour = 0;
		int legendX = left + 80;
		g.setStroke(new BasicStroke(1.2f));
		for (Shown s : shown) {
			g.setColor(COLORS[colour++ % COLORS.length]);
			// at most two points a pixel are drawn
			int step = Math.max(1, s.t.length / (plotW * 2));
			boolean have = false;
			int lastX = 0;
			int lastY = 0;
			for (int i = 0; i < s.t.length; i += step) {
				if (!finite(s.v[i])) {
					continue;
				}
				int x = left + (int) Math.round((s.t[i] - from) / windowMs * plotW);
				int y = top + plotH - (int) Math.round((s.v[i] - min) / (max - min) * plotH);
				if (have) {
					g.drawLine(lastX, lastY, x, y);
				}
				have = true;
				lastX = x;
				lastY = y;
			}
			g.drawString(s.name, legendX, 13);
			legendX += fm.stringWidth(s.name) + 14;
		}

		g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {4f, 3f}, 0f));
		for (Marker m : markers) {
			if (m.t < from || m.t > latest) {
				continue;
			}
			int x = left + (int) Math.round((m.t - from) / windowMs * plotW);
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
