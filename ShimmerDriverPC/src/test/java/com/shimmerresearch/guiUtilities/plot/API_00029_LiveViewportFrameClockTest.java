package com.shimmerresearch.guiUtilities.plot;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

import info.monitorenter.gui.chart.Chart2D;

/** DEV-896: pins the live X viewport frame clock's core invariants without Swing: the tests drive
 * updateFrameClockNewestX() (data thread side) and computeLiveViewportEnd() (EDT side) directly
 * with a synthetic nanoTime, so they are deterministic and need no display. */
public class API_00029_LiveViewportFrameClockTest {

	private static final double X0 = 1.78e12;     //epoch ms, like System_Timestamp_Plot
	private static final long T0_NS = 1_000_000_000L;
	private static final double TICK_MS = 16;

	private BasicPlotManagerPC pm;
	private Field fNewestX, fNewestXNanos;

	@Before
	public void setUp() throws Exception {
		pm = new BasicPlotManagerPC(new ArrayList<String[]>(), 3000, new Chart2D());
		fNewestX = BasicPlotManagerPC.class.getDeclaredField("mFrameClockNewestX");
		fNewestXNanos = BasicPlotManagerPC.class.getDeclaredField("mFrameClockNewestXNanos");
		fNewestX.setAccessible(true);
		fNewestXNanos.setAccessible(true);
	}

	private static long ns(double ms){ return T0_NS + (long)(ms*1e6); }

	private double newestX() throws Exception { return fNewestX.getDouble(pm); }

	private double tick(double tMs) throws Exception {
		return pm.computeLiveViewportEnd(ns(tMs), fNewestX.getDouble(pm), fNewestXNanos.getLong(pm));
	}

	/** 256 Hz, delivered in bursts every burstMs, X = arrival time - 20 ms; deliveries in
	 * [stallFrom, stallTo) are held back and arrive together at stallTo. */
	private void deliver(double fromMs, double toMs, double burstMs, double stallFrom, double stallTo){
		for(double bt=Math.floor(fromMs/burstMs)*burstMs+burstMs; bt<=toMs; bt+=burstMs){
			if(bt>=stallFrom && bt<stallTo) continue;
			double first = (bt>=stallTo && bt-burstMs<stallTo) ? stallFrom-burstMs : bt-burstMs;
			for(double x=first; x<bt; x+=1000.0/256){
				pm.updateFrameClockNewestX(X0+x-20, ns(bt));
			}
		}
	}

	@Test
	public void steadyBurstyStream_scrollsAtRealTime_withBoundedLag() throws Exception {
		double prevEnd = Double.NaN;
		for(double t=TICK_MS; t<20000; t+=TICK_MS){
			deliver(t-TICK_MS, t, 60, -1, -1);
			double end = tick(t);
			if(t>2000){
				double step = end-prevEnd;
				assertTrue("never backwards", step >= 0);
				assertEquals("one tick = one tick of X, within the 10% slew", TICK_MS, step, TICK_MS*0.11);
				double hidden = newestX()-end;
				assertTrue("never ahead of the newest sample", hidden >= 0);
				assertTrue("trails by about VIEWPORT_LATENCY_MS: "+hidden,
						hidden <= BasicPlotManagerPC.VIEWPORT_LATENCY_MS + 60);
				assertTrue("lag stays inside the trace margin",
						hidden <= BasicPlotManagerPC.LIVE_VIEWPORT_TRACE_MARGIN_MS);
			}
			prevEnd = end;
		}
	}

	@Test
	public void dataStall_freezesWithoutBackwardSteps_thenCatchesUpWithinASecond() throws Exception {
		double prevEnd = Double.NaN, recoveredAt = -1;
		double stallFrom = 10000, stallTo = 10600;
		for(double t=TICK_MS; t<16000; t+=TICK_MS){
			deliver(t-TICK_MS, t, 60, stallFrom, stallTo);
			double end = tick(t);
			if(!Double.isNaN(prevEnd)){
				assertTrue("never backwards", end >= prevEnd);
				assertTrue("at most 2x real time while catching up (no snap below 1 s)",
						t < 2000 || end-prevEnd <= 2*TICK_MS + 1e-6);
			}
			assertTrue("never ahead of the newest sample", end <= newestX());
			if(t>stallTo && recoveredAt<0 && newestX()-end < BasicPlotManagerPC.VIEWPORT_LATENCY_MS+60){
				recoveredAt = t;
			}
			prevEnd = end;
		}
		assertTrue("caught up within 1 s of the stall ending: "+recoveredAt, recoveredAt>0 && recoveredAt-stallTo <= 1000);
	}

	@Test
	public void longEdtStall_stepIsElapsedTimeNotDoubled() throws Exception {
		double t = 0, prevT = 0, prevEnd = Double.NaN;
		while(t < 12000){
			double next = t + ((t>=10000 && t<10000+TICK_MS) ? 300 : TICK_MS); //one 300 ms EDT hiccup
			deliver(t, next, 60, -1, -1);
			t = next;
			double end = tick(t);
			if(t>2000){
				//slew branch: step <= elapsed + min(elapsed, 0.15 * error), error <= FRAME_CLOCK_RESYNC_MS
				assertTrue("step "+(end-prevEnd)+" after "+(t-prevT)+" ms", end-prevEnd <= (t-prevT) + 150);
			}
			prevEnd = end; prevT = t;
		}
	}

	@Test
	public void secondDeviceFarBehind_doesNotPullNewestBack() throws Exception {
		for(double t=0; t<5000; t+=10){
			pm.updateFrameClockNewestX(X0+t, ns(t));
			pm.updateFrameClockNewestX(X0+t-1500, ns(t+1)); //device B, 1.5 s behind
			assertEquals(X0+t, newestX(), 0);
		}
	}

	@Test
	public void genuineBackwardJump_resetsAfterConfirmation_singleSnap() throws Exception {
		double prevEnd = Double.NaN; int backwardSteps = 0;
		for(double t=TICK_MS; t<20000; t+=TICK_MS){
			double x = X0 + t - 30 + (t>=10000 ? -60_000 : 0); //device clock reset by a minute
			pm.updateFrameClockNewestX(x, ns(t-2));
			double end = tick(t);
			if(!Double.isNaN(prevEnd) && end<prevEnd) backwardSteps++;
			prevEnd = end;
		}
		assertEquals("exactly one snap back", 1, backwardSteps);
		assertTrue(newestX() < X0 + 20000 - 60_000 + 1);
	}

	/** DB playback replays faster than real time: the clock must follow X (anchored mode) rather
	 * than snapping forward every second, and must not trail by more than the trace margin. */
	@Test
	public void xRate4x_noBigStepsAndLagWithinTraceMargin() throws Exception {
		double rate = 4, deliverMs = 15.6, nextDeliver = deliverMs, prevEnd = Double.NaN;
		for(double t=TICK_MS; t<25000; t+=TICK_MS){
			while(nextDeliver<=t){
				pm.updateFrameClockNewestX(X0+rate*nextDeliver, ns(nextDeliver));
				nextDeliver += deliverMs;
			}
			double end = tick(t);
			if(t>3000){
				double step = end-prevEnd;
				assertTrue("never backwards", step >= 0);
				assertTrue("no single-tick step > 100 ms: "+step+" at "+t, step <= 100);
				double lag = newestX()-end;
				assertTrue("lag "+lag+" within the trace margin", lag <= BasicPlotManagerPC.LIVE_VIEWPORT_TRACE_MARGIN_MS);
			}
			prevEnd = end;
		}
	}

	/** A 2 Hz series (e.g. the fusion-response plot) must not rubber-band: the window may wait for
	 * the next sample, but never stays frozen for longer than one sample interval. */
	@Test
	public void twoHzData_noFrozenRunLongerThanOneSampleInterval() throws Exception {
		double intervalMs = 500, nextSample = 0, prevEnd = Double.NaN, frozenSince = -1, longestFrozen = 0;
		for(double t=TICK_MS; t<30000; t+=TICK_MS){
			while(nextSample<=t){
				pm.updateFrameClockNewestX(X0+nextSample-20, ns(nextSample));
				nextSample += intervalMs;
			}
			double end = tick(t);
			if(t>5000){
				assertTrue("never backwards", end >= prevEnd);
				if(end==prevEnd){
					if(frozenSince<0) frozenSince = t-TICK_MS;
					longestFrozen = Math.max(longestFrozen, t-frozenSince);
				}
				else{
					frozenSince = -1;
				}
			}
			prevEnd = end;
		}
		assertTrue("longest frozen run "+longestFrozen+" ms", longestFrozen <= intervalMs);
	}
}
