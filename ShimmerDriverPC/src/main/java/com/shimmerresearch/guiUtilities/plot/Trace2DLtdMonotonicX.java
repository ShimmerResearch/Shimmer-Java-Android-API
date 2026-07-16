package com.shimmerresearch.guiUtilities.plot;

import info.monitorenter.gui.chart.ITracePoint2D;
import info.monitorenter.gui.chart.traces.Trace2DLtd;

/**
 * DEV-896: A bounded (ring-buffer backed) trace optimised for the live time-series
 * streaming case where the X value is monotonically (non-decreasing) increasing.
 *
 * <p>Problem being solved: {@code Trace2DLtd.addPointInternal()} maintains the trace
 * min/max by, on every eviction of the oldest ring-buffer element, checking whether the
 * evicted point held an extreme and if so running a full linear rescan
 * ({@code ATrace2D.minXSearch()} / {@code maxXSearch()} / {@code minYSearch()} /
 * {@code maxYSearch()}) over the whole buffer. For a time-series plot X is strictly
 * increasing, so the evicted (oldest) point ALWAYS holds the minimum X, which triggers an
 * O(buffer) {@code minXSearch()} on essentially every sample in steady state. Because
 * {@code ATrace2D.addPoint()} does that work while holding {@code synchronized(chart)}
 * (the same monitor {@code Chart2D.paintComponent()} uses), the data thread starves the
 * Swing EDT and the plot stutters.</p>
 *
 * <p>Fix: when the ring buffer is known to be sorted ascending by X, the minimum X is
 * simply the oldest buffer element and the maximum X the youngest, both O(1). We override
 * {@code minXSearch()} / {@code maxXSearch()} to use those accessors on the fast path and
 * fall back to the (correct) superclass scan otherwise.</p>
 *
 * <p>Correctness / robustness:</p>
 * <ul>
 *   <li>We only take the O(1) path when the buffer is guaranteed sorted ascending by X.
 *       That guarantee holds iff the most recent {@code size()} insertions all had
 *       non-decreasing X, because a ring buffer holds exactly the most recent
 *       {@code size()} insertions. We track the length of the current run of consecutive
 *       non-decreasing-X insertions ({@link #mAscendingRunLength}); when it is at least the
 *       current buffer element count the whole buffer content is that non-decreasing suffix,
 *       hence sorted.</li>
 *   <li>If X ever arrives out of order (e.g. plot re-fed on rewind / replay / device reset)
 *       the run is reset and we fall back to the superclass scans until enough monotonic
 *       samples have refilled the buffer, so the displayed range is always exact.</li>
 *   <li>Y is left entirely to the superclass. For random Y the oldest point holds the Y
 *       extreme only ~2/size of the time, so the Y rescan cost is already amortised O(1);
 *       optimising it is unnecessary and would risk the displayed Y auto-scale.</li>
 *   <li>{@code setMaxSize(int)} is {@code final} in {@code Trace2DLtd} so it cannot be
 *       overridden, but no reset hook is needed: {@link #isBufferSortedAscendingByX()} reads
 *       the live {@code m_buffer.size()} each call, so growing the buffer transparently
 *       disables the fast path until it refills and shrinking (which only discards the
 *       oldest, smallest-X elements) keeps the buffer sorted.</li>
 * </ul>
 *
 * <p>Externally this class behaves identically to {@code Trace2DLtd} (same bounds, same
 * property-change events, same {@code setMaxSize} semantics); it only removes the redundant
 * O(n) X rescans. It extends {@code Trace2DLtd} so existing
 * {@code ((Trace2DLtd)trace).setMaxSize(...)} / {@code .iterator()} casts keep working.</p>
 */
public class Trace2DLtdMonotonicX extends Trace2DLtd {

	/** X value of the most recently inserted point, used to detect non-decreasing X. */
	private double mLastX = Double.NaN;

	/**
	 * Number of consecutive insertions (ending at the most recent one) whose X was
	 * non-decreasing. When this is {@code >= m_buffer.size()} the entire current buffer
	 * content was produced by a non-decreasing run and is therefore sorted ascending by X.
	 */
	private long mAscendingRunLength = 0L;

	public Trace2DLtdMonotonicX() {
		super();
	}

	public Trace2DLtdMonotonicX(int maxSize) {
		super(maxSize);
	}

	public Trace2DLtdMonotonicX(int maxSize, String name) {
		super(maxSize, name);
	}

	public Trace2DLtdMonotonicX(String name) {
		super(name);
	}

	/**
	 * @return {@code true} when the backing ring buffer is currently guaranteed to be sorted
	 *         ascending by X, i.e. the most recent {@code size()} insertions were all
	 *         non-decreasing in X. Reads the live buffer size so it stays correct across
	 *         {@code setMaxSize(int)}.
	 */
	private boolean isBufferSortedAscendingByX() {
		if (m_buffer == null || m_buffer.isEmpty()) {
			return false;
		}
		return mAscendingRunLength >= m_buffer.size();
	}

	@Override
	protected boolean addPointInternal(ITracePoint2D p) {
		double x = p.getX();
		if (Double.isNaN(mLastX) || x >= mLastX) {
			// Non-decreasing X: extend the ascending run (cap to avoid overflow; any value
			// above the buffer size already means "fully sorted").
			if (mAscendingRunLength < Long.MAX_VALUE) {
				mAscendingRunLength++;
			}
		} else {
			// X went backwards: the buffer is no longer sorted. This incoming point starts a
			// new ascending run of length 1. The fast path resumes once the run refills the
			// buffer; until then the superclass scans keep the range exact.
			mAscendingRunLength = 1L;
		}
		mLastX = x;
		// Delegates to Trace2DLtd, which on eviction virtually dispatches to our overridden
		// minXSearch()/maxXSearch() below (and to the unchanged Y searches).
		return super.addPointInternal(p);
	}

	@Override
	protected void minXSearch() {
		if (isBufferSortedAscendingByX()) {
			try {
				// Oldest element holds the smallest X when the buffer is sorted ascending.
				m_minX = m_buffer.getOldest().getX();
				return;
			} catch (RuntimeException e) {
				// Buffer emptied concurrently / unexpected state: fall back to the safe scan.
			}
		}
		super.minXSearch();
	}

	@Override
	protected void maxXSearch() {
		if (isBufferSortedAscendingByX()) {
			try {
				// Youngest element holds the largest X when the buffer is sorted ascending.
				m_maxX = m_buffer.getYoungest().getX();
				return;
			} catch (RuntimeException e) {
				// Fall back to the safe scan.
			}
		}
		super.maxXSearch();
	}
}
