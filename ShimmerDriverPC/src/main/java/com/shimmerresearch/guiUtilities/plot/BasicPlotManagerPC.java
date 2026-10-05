/* Rev 0.1
 * 
 * PlotManager can only manage one chart at a time. Use multiple plot managers to manage multiple charts
 */
package com.shimmerresearch.guiUtilities.plot;

import info.monitorenter.gui.chart.Chart2D;
import info.monitorenter.gui.chart.IAxis;
import info.monitorenter.gui.chart.IAxis.AxisTitle;
import info.monitorenter.gui.chart.IAxisLabelFormatter;
import info.monitorenter.gui.chart.IAxisScalePolicy;
import info.monitorenter.gui.chart.IRangePolicy;
import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.ITracePainter;
import info.monitorenter.gui.chart.ITracePoint2D;
import info.monitorenter.gui.chart.axis.AAxis;
import info.monitorenter.gui.chart.axis.AxisLinear;
import info.monitorenter.gui.chart.axis.scalepolicy.AxisScalePolicyAutomaticBestFit;
import info.monitorenter.gui.chart.axis.scalepolicy.AxisScalePolicyManualTicks;
import info.monitorenter.gui.chart.labelformatters.ALabelFormatter;
import info.monitorenter.gui.chart.labelformatters.LabelFormatterAutoUnits;
import info.monitorenter.gui.chart.labelformatters.LabelFormatterDate;
import info.monitorenter.gui.chart.labelformatters.LabelFormatterNumber;
import info.monitorenter.gui.chart.labelformatters.LabelFormatterSimple;
import info.monitorenter.gui.chart.labelformatters.LabelFormatterUnit;
import info.monitorenter.gui.chart.rangepolicies.RangePolicyFixedViewport;
import info.monitorenter.gui.chart.rangepolicies.RangePolicyUnbounded;
import info.monitorenter.gui.chart.traces.Trace2DLtd;
import info.monitorenter.gui.chart.traces.painters.TracePainterDisc;
import info.monitorenter.gui.chart.traces.painters.TracePainterFill;
import info.monitorenter.gui.chart.traces.painters.TracePainterLine;
import info.monitorenter.gui.chart.traces.painters.TracePainterVerticalBar;
import info.monitorenter.util.Range;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import org.apache.commons.collections.buffer.CircularFifoBuffer;

import com.shimmerresearch.driver.FormatCluster;
import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driverUtilities.FftCalculateDetails;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_AXES;
import com.shimmerresearch.guiUtilities.AbstractPlotManager;

public class BasicPlotManagerPC extends AbstractPlotManager {
	
	protected String mEventMarkerCheck="";
	int mXAxisLimit = 500;
	double mXAxisTimeDuration = 5;
	//public List<ITrace2D> mListofTraces = new ArrayList<ITrace2D>();
	public List<ITrace2D> mListofTraces = Collections.synchronizedList(new ArrayList<ITrace2D>());
	
	public HashMap<String, CircularFifoBuffer> mMapOfCirculurBufferedTraceDataPoints = new HashMap<String, CircularFifoBuffer>();
	//public HashMap<String, ArrayList< Point2D.Double>> mMapofPoints = new HashMap<String, ArrayList< Point2D.Double>>();
	public HashMap<String,Integer> mMapofDefaultXAxisSizes = new HashMap<String,Integer>();
	int numberOfRowPropertiestoCheck = 2;
	boolean mClearGraphatLimit = false;
	Chart2D mChart = null;
	public int mWindowSize = 0;

	public HashMap<String,Double> mMapofHalfWindowSize = new HashMap<String,Double>();
	
	/** Default stroke width for continuous traces. A plot manager can override it for the traces it
	 * creates with {@link #setTraceLineThickness(float)}. */
	public static float DEFAULT_LINE_THICKNESS=2;
	
	/** DEV-896: stroke width this plot manager gives continuous traces, DEFAULT_LINE_THICKNESS
	 * unless {@link #setTraceLineThickness(float)} was called. */
	private float mTraceLineThickness = DEFAULT_LINE_THICKNESS;
	
	/** X value (for the time axis: System_Timestamp_Plot, epoch ms) of the most recently plotted
	 * sample. Written by the data thread in filterDataAndPlot(); volatile because the live-viewport
	 * frame clock reads it on the EDT (DEV-896). */
	protected volatile double mCurrentXValue = 0;
	protected boolean mIsPlotPaused = false;
	public boolean mIsLegendLabelsPainted = true;
	public boolean mIsScaleLabelsPainted = true;
	public boolean mIsAxisLabelsPainted = true;
	public boolean mIsGridOn = false;
	public boolean mIsHRVisible = false;
	public boolean mEnablePCTS = true;
	public boolean mSetTraceName = true;
	private boolean mIsDebugMode = false;
	private boolean mIsTraceDataBuffered = false;
	protected boolean isFirstPointOnFillTrace = true;
	protected boolean isSingleEventMarkerTest = true;
	
	public PlotCustomFeature pcf=null;
	
	private String mTitle = "";
	
	//private AAxis<IAxisScalePolicy> yAxisLeft;
	private AAxis<IAxisScalePolicy> yAxisRight;
	private IAxis< ? > xAxis;
	
	//Mark test code
	private CHANNEL_AXES mXAisType = CHANNEL_AXES.TIME;
	
	transient protected Timer mTimerCalculateFft;
	private int mTimerPeriodCalculateFft = 1000;
	private int mTimerDelayCalculateFft = 1000;
	public LinkedHashMap<String, FftCalculateDetails> mMapOfFftsToPlot = new LinkedHashMap<String, FftCalculateDetails>();
	private boolean mIsFftShowingDc = true;
	private int mFftOverlapPercent = 0;
	public HashMap<String, Double> mMapOfLastDataPoints = new HashMap<String, Double>();
	private TimeZone timeZone = Calendar.getInstance().getTimeZone();
	
	private UtilShimmer utilShimmer = new UtilShimmer(this.getClass().getSimpleName(), true);
	
	/** Scale type options */
	public enum SCALE_SETTING{ 
		AUTO,
		FIXED,
		CUSTOM
	}
	
	// --- Constructors START
	
	/**Constructor Used by API examples
	 * 
	 */
	public BasicPlotManagerPC(){
		mMapofXAxisGeneratedValue.clear();
		initializeAxesForTimeBig();
	}
	
	/**Constructor Used by Consensys
	 * @param propertiestoPlot Sets the properties to plot 
	 * @param limit Sets the X axis limit for the series
	 * @param chart the XYPlot in main UI thread so the series can be added
	 * @throws Exception 
	 */
	public BasicPlotManagerPC(List<String[]> propertiestoPlot, int limit, Chart2D chart) throws Exception {
		mXAxisLimit = limit;
		mChart = chart;
		mChart.setCursor(new Cursor(Cursor.DEFAULT_CURSOR)); // Dec 2016: RM put this in as the default cursor for jchart2d is cross-hair
		mChart.getAxisY().setFormatter(new LabelFormatterNumber());
		
		if(propertiestoPlot!=null){
			for (int i=0;i<propertiestoPlot.size();i++){
				addSignal(propertiestoPlot.get(i),chart);
			}
		}
		initializeAxesForTimeBig();
		
//		for(int j = 0; j< propertiestoPlot.size(); j++){
//			for(int l = 0 ; l < propertiestoPlot.get(j).length;l++){
//				utilShimmer.consolePrintLn(""+propertiestoPlot.get(j)[l]);
//			}
//		}
	}
	
	// --- Constructors END

	
	/** Adds a signal to the chart. The chart is referenced internally, for use in removing signals. Color is assigned randomly
	 * @param signal Signal to plot
	 * @param chart Chart from UI thread
	 * @throws Exception if signal already exist in plotmanager 
	 */
	public ITrace2D addSignal(String[] signal, Chart2D chart) throws Exception{
		return this.addSignal(signal, chart, mXAxisLimit);
	}

	/** Adds a signal to the chart. The chart is referenced internally, for use in removing signals. Color is assigned randomly
	 * @param signal Signal to plot
	 * @param chart Chart from UI thread
	 * @throws Exception if signal already exist in plotmanager 
	 */
	public ITrace2D addSignalAsBarPlot(String[] signal, Chart2D chart, int windowSize) throws Exception{
		return this.addSignalAsBarPlot(signal, chart, mXAxisLimit, windowSize);
	}
	
	
	/** Adds a signal to the chart. The chart is referenced internally, for use in removing signals. Color is assigned randomly
	 * @param signal Signal to plot
	 * @param chart Chart from UI thread
	 * @param usePaintIndividualPointsOnly No plot line generated only markers for data points, if true
	 * @throws Exception if signal already exist in plotmanager 
	 */
	public ITrace2D addSignal(String[] signal, Chart2D chart, boolean usePaintIndividualPointsOnly) throws Exception{
		ITrace2D trace = this.addSignal(signal, chart);
		
		if (usePaintIndividualPointsOnly){
			trace.setTracePainter(new TracePainterDisc(4)); 
		}
		
		return trace;
	}

	/** Adds a signal to the chart. The chart is referenced internally, for use in removing signals. Color is assigned randomly
	 * @param signal Signal to plot
	 * @param chart Chart from UI thread
	 * @param plotMaxSize Max Number of Data point on the plot
	 * @return 
	 * @throws Exception if signal already exist in plotmanager 
	 */
	public ITrace2D addSignalAsBarPlot(String [] signal, Chart2D chart, int plotMaxSize, int windowSize) throws Exception{
		ITrace2D trace;
		if (!checkIfPropertyExist(signal)){
			trace = addBarTrace(chart, plotMaxSize);
			String name = addSignalCommon(chart, trace, signal, plotMaxSize);
			
			if (windowSize!=0){
				double mhalf = ((double)windowSize)/2.0;
				mMapofHalfWindowSize.put(name, mhalf);
			}
		}	
		else {
			throw new Exception("Error: " + joinChannelStringArray(signal) +" Signal/Property already exist.");
		}
		return trace;
	}
	
	/** Adds a signal to the chart. The chart is referenced internally, for use in removing signals. Color is assigned randomly
	 * @param signal Signal to plot
	 * @param chart Chart from UI thread
	 * @param plotMaxSize Max Number of Data point on the plot
	 * @return 
	 * @throws Exception if signal already exist in plotmanager 
	 */
	public ITrace2D addSignal(String[] signal, Chart2D chart, int plotMaxSize) throws Exception{
		ITrace2D trace;
		if (!checkIfPropertyExist(signal)){
			
			if(mDefaultLineStyle==PLOT_LINE_STYLE.CONTINUOUS 
					|| mDefaultLineStyle==PLOT_LINE_STYLE.INDIVIDUAL_POINTS){
				trace = addNormalTraceLeft(chart, plotMaxSize);
				
				if(mDefaultLineStyle==PLOT_LINE_STYLE.INDIVIDUAL_POINTS){
					trace.setTracePainter(new TracePainterDisc(4)); 
				}
			}
			else if(mDefaultLineStyle==PLOT_LINE_STYLE.BAR){
				trace = addBarTrace(chart, plotMaxSize);
			}
			else{
				trace = addNormalTraceLeft(chart, plotMaxSize);
			}
			addSignalCommon(chart, trace, signal, plotMaxSize);
			setTraceSize(trace, plotMaxSize);
		}	
		else {
			throw new Exception("Error: " + joinChannelStringArray(signal) +" Signal/Property already exist.");
		}
		
//		printListOfTraces();
		
		return trace;
	}
	
	/** Adds a signal to the chart. The chart is referenced internally, for use in removing signals. Color is assigned randomly
	 * @param signal Signal to plot
	 * @param chart Chart from UI thread
	 * @param plotMaxSize Max Number of Data point on the plot
	 * @return 
	 * @throws Exception if signal already exist in plotmanager 
	 */
	public ITrace2D addSignalUsingRightYAxis(String[] signal, Chart2D chart, int plotMaxSize, String title, int minRange, int maxRange) throws Exception{
		ITrace2D trace;
		if (!checkIfPropertyExist(signal)){
			yAxisRight = createRightYAxis(chart);
			chart.setAxisYRight(yAxisRight, 0);
			trace = addNormalTraceRight(chart, plotMaxSize);
			addSignalCommon(chart, trace, signal, plotMaxSize);
			setTraceSize(trace, plotMaxSize);
		}	
		else {
			throw new Exception("Error: " + joinChannelStringArray(signal) +" Signal/Property already exist.");
		}
		return trace;
	}
	
	private AAxis<IAxisScalePolicy> createRightYAxis(Chart2D chart) {
		//AAxis<IAxisScalePolicy> yAxisRight;
		AAxis<IAxisScalePolicy> yAxisRight = new AxisLinear<IAxisScalePolicy>();
//		yAxisRight.setAxisScalePolicy(new AxisScalePolicyManualTicks());
		yAxisRight.setAxisScalePolicy(new AxisScalePolicyAutomaticBestFit());
		yAxisRight.setFormatter(new LabelFormatterNumber());
		//yAxisRight.setMinorTickSpacing(10);
		//yAxisRight.setStartMajorTick(true);
		yAxisRight.setPaintGrid(false);
		//yAxisRight.setAxisTitle(new IAxis.AxisTitle(title));
		//IRangePolicy rangePolicy = new RangePolicyFixedViewport(new Range(minRange,maxRange));
		
		//yRightAxis.setRangePolicy(rangePolicy);
		return yAxisRight;
	}

	private String addSignalCommon(Chart2D chart, ITrace2D trace, String[] signal, int plotMaxSize) {
		mListofTraces.add(trace);
		//super.addSignalGenerateRandomColor(signal);
		super.addSignalUseDefaultColors(signal);
		int i = mListOfTraceColorsCurrentlyUsed.size()-1;
		int [] colorrgbaray = mListOfTraceColorsCurrentlyUsed.get(i);
		Color color = new Color(colorrgbaray[0], colorrgbaray[1], colorrgbaray[2]);
		mListofTraces.get(i).setColor(color);
		String traceName = joinChannelStringArray(signal);
		//utilShimmer.consolePrintErrLn("TRACE NAME: " +name);
		if(mSetTraceName) {
			mListofTraces.get(i).setName(traceName);
		} else {
			mListofTraces.get(i).setName("");
		}
		mChart=chart;
		mMapofDefaultXAxisSizes.put(traceName, plotMaxSize);
		
		if(isXAxisFrequency()){
//			mMapOfFftsToPlot.put(traceName, new FftCalculateDetails(signal[0], signal, samplingRate));
			FftCalculateDetails fftCalculateDetails = new FftCalculateDetails(signal[0], signal);
			fftCalculateDetails.setFftOverlapPercent(mFftOverlapPercent);
			mMapOfFftsToPlot.put(traceName, fftCalculateDetails);
		}
		
		return traceName;
	}
	
	private ITrace2D addNormalTraceLeft(Chart2D chart, int plotMaxSize) {
		ITrace2D trace = createNormalTrace(plotMaxSize);
		chart.addTrace(trace);
		return trace;
	}

	private ITrace2D addNormalTraceRight(Chart2D chart, int plotMaxSize) {
		ITrace2D trace = createNormalTrace(plotMaxSize);
		chart.addTrace(trace,chart.getAxisX(),yAxisRight);
		return trace;
	}
	
	private ITrace2D addBarTrace(Chart2D chart, int plotMaxSize) {
		ITrace2D trace = createBarTrace(chart, plotMaxSize);
		chart.addTrace(trace);
		return trace;
	}

	/** DEV-896: stroke width for continuous traces this plot manager creates from now on, and for
	 * traces later set to CONTINUOUS/INDIVIDUAL_POINTS via setTraceLineStyle(). Existing traces
	 * keep their stroke, so call it before signals are added.
	 * <p>Cost: jchart2d paints a trace with TracePainterPolyline, i.e. Graphics.drawPolyline,
	 * antialiasing off. One paint of 2560 points at UHD measured ~0.4 ms at 1 px against ~3.7 ms at
	 * 2 px at identity transform (the installed DPI-unaware exe, LauncherConsensysInternal's
	 * uiScale=1.0), and ~3.5 ms against ~4.0 ms at uiScale 2, where even 1 px is scaled. */
	public void setTraceLineThickness(float thickness){
		mTraceLineThickness = thickness;
	}
	
	public float getTraceLineThickness(){
		return mTraceLineThickness;
	}

	private ITrace2D createNormalTrace(int plotMaxSize) {
		Trace2DLtd trace = new Trace2DLtdMonotonicX(plotMaxSize); //DEV-896: monotonic-X trace avoids O(n) minX rescans per sample
		BasicStroke stroke = ((BasicStroke)trace.getStroke());
		BasicStroke newStroke = new BasicStroke(mTraceLineThickness,stroke.getEndCap(),stroke.getLineJoin(),stroke.getMiterLimit(),stroke.getDashArray(),stroke.getDashPhase());
		trace.setStroke(newStroke);
		return trace;
	}

	private ITrace2D createBarTrace(Chart2D chart, int plotMaxSize) {
		ITrace2D trace = new Trace2DLtdMonotonicX(plotMaxSize); //DEV-896: monotonic-X trace avoids O(n) minX rescans per sample
		trace.setTracePainter(new TracePainterVerticalBar(chart));
		return trace;
	}


	
	/** Adds a signal to the chart. The chart is referenced internally, for use in removing signals. Color is assigned randomly
	 * @param signal Signal to plot
	 * @param plotMaxSize Max Number of Data point on the plot
	 * @throws Exception if signal already exist in plotmanager 
	 */
	private ITrace2D addSignalToExistingChartInternal(String[] signal, int plotMaxSize, Color color) throws Exception{
		if (!checkIfPropertyExist(signal)){
			ITrace2D trace = new Trace2DLtdMonotonicX(plotMaxSize); //DEV-896: monotonic-X trace avoids O(n) minX rescans per sample
			mChart.addTrace(trace);
			
			mListofTraces.add(trace);
			super.addSignalGenerateRandomColor(signal);
			int i = mListOfTraceColorsCurrentlyUsed.size()-1;
			int [] colorrgbaray = mListOfTraceColorsCurrentlyUsed.get(i);
			mListofTraces.get(i).setColor(color);
			String name = joinChannelStringArray(signal);
			if(mSetTraceName) {
				mListofTraces.get(i).setName(name);
			} else {
				mListofTraces.get(i).setName("");
			}
			return trace;
		}	
		else {
			throw new Exception("Error: " + joinChannelStringArray(signal) +" Signal/Property already exist.");
		}
	}
	
	public void addTrace2D(ITrace2D trace, String[] signal, int plotMaxSize){
		mChart.addTrace(trace);
		mListofTraces.add(trace);
		setTraceSize(trace, plotMaxSize);
		String name = joinChannelStringArray(signal);
		mMapofDefaultXAxisSizes.put(name, plotMaxSize);
		super.addSignal(signal);
	}
	
//	public void addXAxis(String[] key){
//		super.addXAxis(key);
//	}
	
	public Chart2D getChart(){
		return mChart;
	}
	

	/**Removes all traces, colours, and signal names from plot manager, and clears Chart2D
	 * @param chart the Chart to be cleared
	 */
	public void removeAllSignals(){
		stopLiveViewportFrameClock(); //DEV-896: also the frame-close path (InternalFrameWithPlotManager.frameClosed())
		mCurrentXValue=0;
		mFrameClockNewestX=0; //DEV-896
		super.removeAllSignals();
		if (mChart!=null){
			try {
				mChart.removeAllTraces();
				mChart.removeAll();
			}
			catch (Exception e){
				e.printStackTrace();
			}
		}
		mListofTraces.clear();
		mMapofXAxisGeneratedValue.clear();
		mMapofDefaultXAxisSizes.clear();
		mMapOfFftsToPlot.clear();
		mMapOfLastDataPoints.clear();
	}
	
	/**Removes signal from plotmanager and chart.
	 * 
	 * @param signal Signal to be removed
	 */
	private void removeSignalInternal(String[] signal){
		synchronized(mListofPropertiestoPlot){
			Iterator <String[]> entries = mListofPropertiestoPlot.iterator();
			int i = 0;
			while (entries.hasNext()) {
				String[] prop = entries.next();
		
				boolean found = true;
				for (int p=0;p<numberOfRowPropertiestoCheck;p++){
					if (!prop[p].equals(signal[p])){
						found = false;
//						utilShimmer.consolePrintLn("SIGNAL NOT FOUND: " + joinChannelStringArray(signal));
						break;
					}
				}
				if (found){
					String traceName = joinChannelStringArray(signal);
					
					removeSignalCommon(traceName);

					//utilShimmer.consolePrintErrLn("mChart.removeTrace: " +mListofTraces.get(i));
					mChart.removeTrace(mListofTraces.get(i));
					mListofTraces.remove(i);
					super.removeSignal(i);
				}
				i++;
			}
		}
	}
	
	private void removeSignalCommon(String traceName) {
		mMapOfFftsToPlot.remove(traceName);
		mMapOfLastDataPoints.remove(traceName);
	}

	/**Removes signal from plotmanager and chart.
	 * 
	 * @param signal Signal to be removed
	 */
	public void removeSignal(String[] signal){
		synchronized(mListofPropertiestoPlot){
			for (int i=0;i<mListofPropertiestoPlot.size();i++){
				String[] prop = mListofPropertiestoPlot.get(i);
				boolean found = true;
				for (int p=0;p<numberOfRowPropertiestoCheck;p++){
					if (!prop[p].equals(signal[p])){
						found = false;
	//					utilShimmer.consolePrintLn("SIGNAL NOT FOUND: " + joinChannelStringArray(signal));
						break;
					}
				}
				if (found){
					String traceName = joinChannelStringArray(signal);
					mMapofDefaultXAxisSizes.remove(traceName);
					mListofTraces.get(i).removeAllPoints(); // added this line for ConsensysGQ as we keep hold the trace for the single HR and GSR plot
					
					removeSignalCommon(traceName);
					
					mChart.removeTrace(mListofTraces.get(i));
					mListofTraces.remove(i);
					super.removeSignal(i);
				}
			}
		}
	}
	
	public void setTitle(String title) {
		mTitle = title;
	}
	
	public String getTitle() {
		return mTitle;
	}
	
	public void setYAxisLabel(String label){
		setYAxisLabel(label, null);
	}
	
	public void setYAxisLabel(String label, Font font){
		IAxis<?> y = mChart.getAxisY();
		AxisTitle axisTitle = new AxisTitle(label);
		if(font != null){
			axisTitle.setTitleFont(font);
		}
		y.setAxisTitle(axisTitle);
	}

	public void setXAxisLabel(String label){
		setXAxisLabel(label, null);
	}

	public void setXAxisLabel(String label, Font font){
		IAxis<?> x = mChart.getAxisX();
		AxisTitle axisTitle = new AxisTitle(label);
		if(font != null){
			axisTitle.setTitleFont(font);
		}
		x.setAxisTitle(axisTitle);
	}
	
	public void setXAxisRange(double minX,double maxY){
		IAxis<?> x = mChart.getAxisX();
		x.setRangePolicy(new RangePolicyFixedViewport(new Range(minX, maxY)));
	}
	
	/** Anchors the X window's right edge to the newest plotted sample. Allocates a new range policy
	 * per call, so it is for explicit, occasional callers (window change, pause, static contents).
	 * Live time-axis plots should use the frame clock instead of calling this per sample - see
	 * {@link #setLiveViewportFrameClockEnabled(boolean)} (DEV-896). */
	public void setXAxisRangeBasedOnXDuration(){
		setXAxisRange(mCurrentXValue-(mXAxisTimeDuration*1000), mCurrentXValue);
	}
	
	/** Makes the graph initially fill from right rather then the left. 
	 * @param samplingRate
	 */
	public void setXAxisRangeBasedOnXDurationSubtractSingleSamplingRate(double samplingRate){
		double minTime = mCurrentXValue-(mXAxisTimeDuration*1000);
		double samplingDurationInMs = (1/samplingRate)*1000;
		minTime=minTime+samplingDurationInMs;
		setXAxisRange(minTime, mCurrentXValue);
	}
	
	//---------------------- DEV-896: live X viewport frame clock START -----------------------//
	//
	// Live time-axis plots used to move the X window from the data thread, once per sample: the
	// window's right edge was the newest sample's X, so it advanced in whatever bursts the data
	// arrived in (Bluetooth delivers 10-30 samples at once), and Chart2D's own 50 ms repaint timer
	// then showed those bursts as 20 fps jumps. Here one Swing Timer per plot manager moves the
	// window instead, on the EDT, by the real time elapsed since the previous frame, and repaints
	// the chart right after, so every frame shows exactly one even step.
	//
	// The window trails the newest data by VIEWPORT_LATENCY_MS (a playout delay). It is NOT
	// anchored to System.currentTimeMillis(): the time axis plots System_Timestamp_Plot, which is
	// the device's own clock offset once, at the first packet, onto the PC epoch (see
	// SystemTimestampPlot / SensorShimmerClock). So it is epoch ms, but it carries that first
	// packet's latency and drifts with the device crystal (20 ppm is 72 ms per hour), and the PC
	// clock can be stepped by NTP. Instead the right edge is a playout clock that advances with
	// System.nanoTime() and is steered gently toward "newest X + time since it arrived - latency",
	// which needs no common epoch, follows drift, and cannot jump with the wall clock.
	//
	// Threading: the data thread only writes mCurrentXValue and the frame-clock volatiles
	// and, when the clock is not running, posts one start request to the EDT. Everything else runs
	// on the EDT. The tick never takes the mListofPropertiestoPlot or mListofTraces monitors; it
	// only takes the chart monitor (through jchart2d's property-change and repaint-flag methods),
	// so it cannot invert the list -> chart lock order that filterDataAndPlot() relies on.
	//
	// Lifecycle (DEV-895/DEV-717: an idle app must not tick at 60 fps): the timer runs only while
	// live time-axis samples are arriving into a chart that is showing. It stops itself on the
	// first tick at which no sample has arrived for FRAME_CLOCK_IDLE_STOP_MS, the plot is paused,
	// the chart is not showing (tab switched, frame iconified or closed), the X axis is not the
	// time axis, or the data was cleared (mFrameClockNewestX back to 0, which removeAllSignals() and
	// clearAllDataBuffer() do). removeAllSignals() - which InternalFrameWithPlotManager.frameClosed()
	// calls - also stops it immediately. A stopped javax.swing.Timer is no longer referenced by
	// Swing's TimerQueue, so it cannot pin this manager, its chart or the owning frame.
	
	/** Frame clock period: one X-window update and one chart repaint per tick, ~60 fps. If 60 fps
	 * proves too heavy for the EDT at UHD with many plots open, 33 (~30 fps) is the fallback; the
	 * motion stays time-correct at any rate because each tick advances by the real elapsed time. */
	public static final int FRAME_INTERVAL_MS = 16;
	/** Playout delay: the live window's right edge trails "newest sample's X + time since it
	 * arrived" by this much. It absorbs Bluetooth bursts and the IOThread -> callback deque ->
	 * plot-thread hops, so data scrolls in evenly instead of in jumps; a sample becomes visible
	 * roughly this long after it was plotted. */
	public static final double VIEWPORT_LATENCY_MS = 200;
	/** The frame clock stops once no live sample has arrived for this long. */
	public static final long FRAME_CLOCK_IDLE_STOP_MS = 2000;
	/** Extra history a live trace needs while the frame clock runs. A trace sized to exactly the
	 * window would leave a blank strip of up to ~VIEWPORT_LATENCY_MS at the window's left edge,
	 * because the window now trails the newest sample. See getLiveViewportTraceMarginInSeconds(). */
	public static final double LIVE_VIEWPORT_TRACE_MARGIN_MS = 2*VIEWPORT_LATENCY_MS;
	/** Beyond this distance from its target the playout clock snaps instead of slewing: on the
	 * first tick, after a long stall, or when the X values jump (new session, clock reset). */
	private static final double FRAME_CLOCK_RESYNC_MS = 1000;
	/** Share of the remaining error the playout clock corrects per tick (~0.3 s time constant). */
	private static final double FRAME_CLOCK_CORRECTION_GAIN = 0.05;
	/** Largest correction per tick, as a share of that tick's elapsed time, for small errors and
	 * for slowing down: in normal running a frame's X step stays within +-10% of real time. */
	private static final double FRAME_CLOCK_MAX_SLEW = 0.1;
	/** Largest speed-up per tick, as a share of the tick's elapsed time, once the window is behind
	 * its target by FRAME_CLOCK_CATCH_UP_FULL_MS or more (after a stall or a forward X jump below
	 * the snap threshold): the window then scrolls at up to 2x real time, so it recovers in about
	 * a second instead of the several seconds the 10% cap would take, and before the lag outgrows
	 * the trace margin. */
	private static final double FRAME_CLOCK_CATCH_UP_SLEW = 1.0;
	/** Behind by less than this, only FRAME_CLOCK_MAX_SLEW applies: covers normal burst jitter. */
	private static final double FRAME_CLOCK_CATCH_UP_START_MS = 50;
	/** Behind by this much or more, the full FRAME_CLOCK_CATCH_UP_SLEW applies; linear in between. */
	private static final double FRAME_CLOCK_CATCH_UP_FULL_MS = VIEWPORT_LATENCY_MS;
	/** Share of the remaining error corrected per tick while catching up. */
	private static final double FRAME_CLOCK_CATCH_UP_GAIN = 0.15;
	
	// Anchored fallback. The playout clock assumes X advances about 1 ms per wall-clock ms and
	// that new X arrives well within VIEWPORT_LATENCY_MS. Neither holds for Consensys DB playback
	// (PlaybackSession replays at ~1.3x at "x1", because its timer period is truncated, and 2-4x+
	// at x2/x4) or for low-rate series (e.g. the 2 Hz fusion-response plot): the clock would then
	// snap forward repeatedly, or freeze and rubber-band. So, from the arrivals of the newest X,
	// the clock estimates the X rate and the spacing between new X values over windows of at
	// least FRAME_CLOCK_RATE_WINDOW_MS, and when either is out of range - in the window itself and
	// in its EWMA - for FRAME_CLOCK_MODE_SWITCH_WINDOWS windows in a row it switches to anchored mode: each tick the
	// window's right edge is simply the newest X (the old behaviour, but still one update and one
	// paint per tick). It switches back, with hysteresis, the same way.
	/** Minimum span of newest-X arrivals per rate/spacing estimate. */
	private static final long FRAME_CLOCK_RATE_WINDOW_MS = 500;
	/** EWMA weight of each new window's rate and spacing. */
	private static final double FRAME_CLOCK_RATE_EWMA_ALPHA = 0.5;
	/** Consecutive windows a mode switch must be wanted for (a one-off X jump or a stall changes
	 * one window only). */
	private static final int FRAME_CLOCK_MODE_SWITCH_WINDOWS = 2;
	/** Anchor when the X rate (X ms per wall-clock ms) is above / below these. */
	private static final double FRAME_CLOCK_ANCHOR_RATE_HIGH = 1.5;
	private static final double FRAME_CLOCK_ANCHOR_RATE_LOW = 0.67;
	/** Leave anchored mode only when the rate is back between these. */
	private static final double FRAME_CLOCK_UNANCHOR_RATE_HIGH = 1.2;
	private static final double FRAME_CLOCK_UNANCHOR_RATE_LOW = 0.83;
	/** Anchor when new X values arrive further apart than this. Above VIEWPORT_LATENCY_MS the
	 * playout clock rubber-bands (freezes at the newest sample, then catches up); at or just below
	 * it, e.g. a 5 Hz series, it still scrolls evenly, so the threshold is 1.2x the latency rather
	 * than below it. */
	private static final double FRAME_CLOCK_ANCHOR_SPACING_MS = 1.2*VIEWPORT_LATENCY_MS;
	/** Leave anchored mode only when new X values arrive closer together than this. */
	private static final double FRAME_CLOCK_UNANCHOR_SPACING_MS = 0.75*VIEWPORT_LATENCY_MS;
	/** Anchored because X runs fast (playback): the right edge may advance at most this many times
	 * the estimated X rate per tick, so two deliveries landing in one tick (e.g. a 15.6 ms playback
	 * timer against 16 ms frames) are spread over the next frames instead of showing as one double
	 * step. Anchored for sparse data at ~1x rate, the edge jumps straight to the newest X. */
	private static final double FRAME_CLOCK_ANCHORED_MAX_SPEED_FACTOR = 1.5;
	/** Chart2D's own repaint timer delay while the frame clock repaints the chart itself. Kept long
	 * so that timer does not add a second, unsynchronised stream of paints; restored on stop. */
	private static final int FRAME_CLOCK_CHART_PAINT_LATENCY_MS = 250;
	/** At most one start request per this interval from the data thread. */
	private static final long FRAME_CLOCK_START_REQUEST_MIN_INTERVAL_NS = 100L*1000*1000;
	
	private volatile boolean mIsLiveViewportFrameClockEnabled = false;
	/** System.nanoTime() when the data thread last moved mCurrentXValue (any device). Used for
	 * idle detection. Data thread writes. */
	private volatile long mLastLiveSampleNanos = 0;
	/** Newest X seen by the frame clock, 0 = none since the last clear. Unlike mCurrentXValue,
	 * which alternates between devices' timestamps in a multi-device plot, this only moves
	 * forward, except on a genuine backward jump (reconnect, device reset): X more than
	 * FRAME_CLOCK_RESYNC_MS behind it, with nothing closer arriving for
	 * FRAME_CLOCK_BACKWARD_JUMP_CONFIRM_MS. Data thread writes (before mFrameClockNewestXNanos). */
	private volatile double mFrameClockNewestX = 0;
	/** System.nanoTime() when mFrameClockNewestX was last moved. */
	private volatile long mFrameClockNewestXNanos = 0;
	/** Data thread only: System.nanoTime() of the last sample within FRAME_CLOCK_RESYNC_MS of
	 * mFrameClockNewestX. */
	private long mFrameClockLastNearNewestNanos = 0;
	/** A sample far behind the newest X only counts as a backward jump once no sample near the
	 * newest X has arrived for this long. Without it, two devices whose timestamps differ by more
	 * than FRAME_CLOCK_RESYNC_MS would reset the newest X on every sample of the slower one. */
	private static final long FRAME_CLOCK_BACKWARD_JUMP_CONFIRM_MS = 500;
	private volatile javax.swing.Timer mFrameClockTimer = null;
	/** Written on the EDT only; read by the data thread to decide whether to request a start. */
	private volatile boolean mIsFrameClockRunning = false;
	private final AtomicBoolean mIsFrameClockStartPending = new AtomicBoolean(false);
	/** Data thread only. */
	private long mLastFrameClockStartRequestNanos = System.nanoTime() - FRAME_CLOCK_START_REQUEST_MIN_INTERVAL_NS;
	//EDT-only state below
	private double mFrameClockViewportEndX = Double.NaN;
	/** Anchored fallback state (see FRAME_CLOCK_ANCHOR_*). */
	private boolean mIsFrameClockAnchored = false;
	/** Just left anchored mode: hold the window still until the playout target catches up with it,
	 * instead of stepping back by up to VIEWPORT_LATENCY_MS. */
	private boolean mIsFrameClockHoldingAfterAnchor = false;
	private boolean mIsFrameClockRateBaseSet = false;
	private double mFrameClockRateBaseX = 0;
	private long mFrameClockRateBaseNanos = 0;
	private long mFrameClockRateLastSeenNanos = 0;
	private int mFrameClockRateUpdatesInWindow = 0;
	private double mFrameClockRateEwma = Double.NaN;
	private double mFrameClockSpacingEwmaMs = Double.NaN;
	private int mFrameClockModeSwitchVotes = 0;
	private long mLastFrameClockTickNanos = 0;
	private Chart2D mFrameClockChart = null;
	private int mFrameClockSavedPaintLatency = -1;
	/** Installed on the X axis once, then only its Range is swapped per tick (no per-frame policy
	 * allocation). Replaced only if something else installed a different policy in between. */
	private RangePolicyFixedViewport mFrameClockRangePolicy = null;
	private Range mFrameClockSpareRange = null;
	
	/** Lets a frame clock, rather than each incoming sample, move this plot's X window while the X
	 * axis is the time axis. Enable it before signals are added, so callers that size traces from
	 * {@link #getLiveViewportTraceMarginInSeconds()} see the margin. Non-time X axes (frequency/FFT,
	 * value) are unaffected: the clock never runs for them. */
	public void setLiveViewportFrameClockEnabled(boolean enabled){
		mIsLiveViewportFrameClockEnabled = enabled;
		if(!enabled){
			stopLiveViewportFrameClock();
		}
	}
	
	public boolean isLiveViewportFrameClockEnabled(){
		return mIsLiveViewportFrameClockEnabled;
	}
	
	public boolean isLiveViewportFrameClockRunning(){
		return mIsFrameClockRunning;
	}
	
	/** Extra seconds of history a live trace should hold beyond the X window, 0 when the frame
	 * clock is disabled. */
	public double getLiveViewportTraceMarginInSeconds(){
		return mIsLiveViewportFrameClockEnabled ? (LIVE_VIEWPORT_TRACE_MARGIN_MS/1000.0) : 0;
	}
	
	/** Stops the frame clock now; it restarts by itself when live samples arrive again. Safe from
	 * any thread: off the EDT the timer is stopped directly (javax.swing.Timer.stop() is
	 * thread-safe) and the rest of the teardown, which touches the chart, is posted to the EDT. */
	public void stopLiveViewportFrameClock(){
		if(SwingUtilities.isEventDispatchThread()){
			stopFrameClockOnEdt(false);
		}
		else{
			javax.swing.Timer timer = mFrameClockTimer;
			if(timer!=null){
				timer.stop();
			}
			SwingUtilities.invokeLater(new Runnable(){
				@Override
				public void run() {
					stopFrameClockOnEdt(false);
				}
			});
		}
	}
	
	/** Data thread, once per sample that moved mCurrentXValue on a time X axis. Cheap on purpose:
	 * a nanoTime() read, a few volatile accesses and, only while the clock is stopped, a throttled
	 * check. */
	private void noteLiveSampleForFrameClock(double xData){
		long nowNanos = System.nanoTime();
		mLastLiveSampleNanos = nowNanos;
		updateFrameClockNewestX(xData, nowNanos);
		if(!mIsFrameClockRunning
				&& (nowNanos-mLastFrameClockStartRequestNanos) >= FRAME_CLOCK_START_REQUEST_MIN_INTERVAL_NS){
			Chart2D chart = mChart;
			//isShowing() off the EDT is a benign racy read: at worst one request is skipped or wasted.
			//Checking it here keeps a hidden plot from posting a start request per sample.
			if(chart!=null && isChartDisplayed(chart) && mIsFrameClockStartPending.compareAndSet(false, true)){
				mLastFrameClockStartRequestNanos = nowNanos;
				SwingUtilities.invokeLater(new Runnable(){
					@Override
					public void run() {
						startFrameClockOnEdt();
					}
				});
			}
		}
	}
	
	/** Data thread (or a test). Keeps mFrameClockNewestX = max(X seen), restarting from xData on a
	 * genuine backward jump (see mFrameClockNewestX). */
	void updateFrameClockNewestX(double xData, long nowNanos){
		double newest = mFrameClockNewestX;
		boolean isNearNewest = xData>=newest-FRAME_CLOCK_RESYNC_MS;
		if(newest==0 || xData>newest
				|| (!isNearNewest && (nowNanos-mFrameClockLastNearNewestNanos) > FRAME_CLOCK_BACKWARD_JUMP_CONFIRM_MS*1000*1000)){
			mFrameClockNewestX = xData;
			mFrameClockNewestXNanos = nowNanos;
			mFrameClockLastNearNewestNanos = nowNanos;
		}
		else if(isNearNewest){
			mFrameClockLastNearNewestNanos = nowNanos;
		}
	}
	
	/** Showing, and its top-level frame not iconified (an iconified JFrame's components still
	 * report isShowing()). Off the EDT this is a benign racy read. */
	private static boolean isChartDisplayed(Chart2D chart){
		if(!chart.isShowing()){
			return false;
		}
		java.awt.Window window = SwingUtilities.getWindowAncestor(chart);
		return !(window instanceof java.awt.Frame) || (((java.awt.Frame)window).getExtendedState() & java.awt.Frame.ICONIFIED)==0;
	}
	
	private boolean isFrameClockWanted(){
		return mIsLiveViewportFrameClockEnabled && isXAxisTime() && !mIsPlotPaused && mFrameClockNewestX!=0;
	}
	
	/** EDT. */
	private void startFrameClockOnEdt(){
		mIsFrameClockStartPending.set(false);
		Chart2D chart = mChart;
		if(mIsFrameClockRunning || !isFrameClockWanted() || chart==null || !isChartDisplayed(chart)){
			return;
		}
		if(mFrameClockTimer==null){
			javax.swing.Timer timer = new javax.swing.Timer(FRAME_INTERVAL_MS, new java.awt.event.ActionListener(){
				@Override
				public void actionPerformed(java.awt.event.ActionEvent e) {
					onFrameClockTick();
				}
			});
			timer.setCoalesce(true);
			mFrameClockTimer = timer;
		}
		mFrameClockViewportEndX = Double.NaN;
		mFrameClockChart = chart;
		mFrameClockSavedPaintLatency = chart.getMinPaintLatency();
		chart.setMinPaintLatency(FRAME_CLOCK_CHART_PAINT_LATENCY_MS);
		mIsFrameClockRunning = true;
		mFrameClockTimer.start();
		//Apply a current window now rather than one timer interval later, so a chart that was just
		//shown again does not paint its stale window for the first frames.
		onFrameClockTick();
	}
	
	/** EDT.
	 * @param snapToNewestSample leave the window ending at the newest sample, as the old
	 * sample-driven path did, so nothing received stays hidden behind the playout delay. */
	private void stopFrameClockOnEdt(boolean snapToNewestSample){
		javax.swing.Timer timer = mFrameClockTimer;
		if(timer!=null){
			timer.stop();
		}
		boolean wasRunning = mIsFrameClockRunning;
		mIsFrameClockRunning = false;
		mFrameClockViewportEndX = Double.NaN;
		resetFrameClockRateEstimate();
		Chart2D chart = mFrameClockChart;
		mFrameClockChart = null;
		if(chart!=null && mFrameClockSavedPaintLatency>0){
			chart.setMinPaintLatency(mFrameClockSavedPaintLatency);
		}
		mFrameClockSavedPaintLatency = -1;
		
		double latestX = mFrameClockNewestX;
		if(wasRunning && snapToNewestSample && chart!=null && chart==mChart && latestX!=0){
			applyLiveViewport(chart, latestX-(mXAxisTimeDuration*1000), latestX);
			chart.repaint();
		}
	}
	
	/** EDT, once per FRAME_INTERVAL_MS while the clock runs. */
	private void onFrameClockTick(){
		if(!mIsFrameClockRunning){
			return; //an event already queued when the timer was stopped
		}
		Chart2D chart = mChart;
		if(!isXAxisTime()){
			stopFrameClockOnEdt(false); //do not put a time window on a value/frequency axis
			return;
		}
		if(chart==null || chart!=mFrameClockChart || !mIsLiveViewportFrameClockEnabled || !isChartDisplayed(chart)){
			stopFrameClockOnEdt(true);
			return;
		}
		//Read the arrival time before X: the data thread writes X first, so a fresh arrival time
		//implies a fresh X.
		long newestXNanos = mFrameClockNewestXNanos;
		double newestX = mFrameClockNewestX;
		if(newestX==0){
			stopFrameClockOnEdt(false); //cleared: leave the axis to whoever cleared it
			return;
		}
		long nowNanos = System.nanoTime();
		if(mIsPlotPaused || (nowNanos-mLastLiveSampleNanos) > FRAME_CLOCK_IDLE_STOP_MS*1000*1000){
			stopFrameClockOnEdt(true);
			return;
		}
		double end = computeLiveViewportEnd(nowNanos, newestX, newestXNanos);
		applyLiveViewport(chart, end-(mXAxisTimeDuration*1000), end);
		//This tick paints the chart itself, now, so each frame shows exactly one step. Clearing
		//the flag stops Chart2D's own timer from scheduling a second paint for the same changes.
		chart.setRequestedRepaint(false);
		chart.repaint();
	}
	
	/** EDT (or a test driving it directly). Advances the playout clock to nowNanos and returns the
	 * X window's new right edge.
	 * @param latestX newest X plotted (mFrameClockNewestX)
	 * @param lastSampleNanos System.nanoTime() when that X was plotted */
	double computeLiveViewportEnd(long nowNanos, double latestX, long lastSampleNanos){
		updateFrameClockRateEstimate(latestX, lastSampleNanos);
		double sinceSampleMs = (nowNanos-lastSampleNanos)/1e6;
		double target = latestX + sinceSampleMs - VIEWPORT_LATENCY_MS;
		double previousEnd = mFrameClockViewportEndX;
		double end;
		if(mIsFrameClockAnchored){
			//X does not advance like wall-clock time, or arrives too sparsely: follow it directly.
			end = latestX;
			if(!Double.isNaN(previousEnd) && mFrameClockRateEwma>FRAME_CLOCK_UNANCHOR_RATE_HIGH
					&& latestX>=previousEnd && latestX-previousEnd<=FRAME_CLOCK_RESYNC_MS){
				double frameMs = Math.max(0, (nowNanos-mLastFrameClockTickNanos)/1e6);
				end = Math.min(latestX, previousEnd + frameMs*mFrameClockRateEwma*FRAME_CLOCK_ANCHORED_MAX_SPEED_FACTOR);
			}
			mIsFrameClockHoldingAfterAnchor = true;
		}
		else if(Double.isNaN(previousEnd) || Math.abs(target-previousEnd) > FRAME_CLOCK_RESYNC_MS){
			end = Math.min(target, latestX);
			mIsFrameClockHoldingAfterAnchor = false;
		}
		else if(mIsFrameClockHoldingAfterAnchor && target<previousEnd){
			end = previousEnd;
		}
		else{
			mIsFrameClockHoldingAfterAnchor = false;
			double frameMs = Math.max(0, (nowNanos-mLastFrameClockTickNanos)/1e6);
			double predicted = previousEnd + frameMs;
			double error = target-predicted;
			//Asymmetric: slowing down (error<0) and small speed-ups stay within 10% of real time;
			//a window that has fallen well behind may speed up to 2x so it recovers in ~1 s.
			double catchUp = Math.max(0, Math.min(1, (error-FRAME_CLOCK_CATCH_UP_START_MS)
					/(FRAME_CLOCK_CATCH_UP_FULL_MS-FRAME_CLOCK_CATCH_UP_START_MS)));
			double gain = FRAME_CLOCK_CORRECTION_GAIN + catchUp*(FRAME_CLOCK_CATCH_UP_GAIN-FRAME_CLOCK_CORRECTION_GAIN);
			double maxSpeedUp = frameMs*(FRAME_CLOCK_MAX_SLEW + catchUp*(FRAME_CLOCK_CATCH_UP_SLEW-FRAME_CLOCK_MAX_SLEW));
			double maxSlowDown = frameMs*FRAME_CLOCK_MAX_SLEW;
			double correction = Math.max(-maxSlowDown, Math.min(maxSpeedUp, error*gain));
			end = predicted + correction;
			//Never run ahead of the newest sample (a stall freezes the window, as before), and
			//never scroll backwards.
			end = Math.max(previousEnd, Math.min(end, latestX));
		}
		mFrameClockViewportEndX = end;
		mLastFrameClockTickNanos = nowNanos;
		return end;
	}
	
	/** EDT. Updates the X-rate and X-spacing estimates from the newest X and its arrival time, and
	 * switches between playout and anchored mode (see FRAME_CLOCK_ANCHOR_*). Windows are measured
	 * between arrivals, so a stall followed by buffered data still reads as rate ~1. */
	private void updateFrameClockRateEstimate(double newestX, long newestXNanos){
		if(newestX==0){
			return; //no data yet
		}
		if(!mIsFrameClockRateBaseSet || newestX<mFrameClockRateBaseX){ //first sample, or X went back
			startFrameClockRateWindow(newestX, newestXNanos);
			return;
		}
		if(newestXNanos!=mFrameClockRateLastSeenNanos){
			mFrameClockRateLastSeenNanos = newestXNanos;
			mFrameClockRateUpdatesInWindow++;
		}
		double windowMs = (newestXNanos-mFrameClockRateBaseNanos)/1e6;
		if(windowMs<FRAME_CLOCK_RATE_WINDOW_MS || mFrameClockRateUpdatesInWindow==0){
			return;
		}
		double rate = (newestX-mFrameClockRateBaseX)/windowMs;
		double spacingMs = windowMs/mFrameClockRateUpdatesInWindow;
		if(Double.isNaN(mFrameClockRateEwma)){
			mFrameClockRateEwma = rate;
			mFrameClockSpacingEwmaMs = spacingMs;
		}
		else{
			mFrameClockRateEwma += FRAME_CLOCK_RATE_EWMA_ALPHA*(rate-mFrameClockRateEwma);
			mFrameClockSpacingEwmaMs += FRAME_CLOCK_RATE_EWMA_ALPHA*(spacingMs-mFrameClockSpacingEwmaMs);
		}
		startFrameClockRateWindow(newestX, newestXNanos);
		
		//A switch is wanted only when both this window and the smoothed estimate call for it: the
		//EWMA alone would carry a one-off X jump (one window at e.g. 3x) into the next window too.
		boolean isSwitchWanted;
		if(mIsFrameClockAnchored){
			isSwitchWanted = isFrameClockRateNormal(rate, spacingMs) && isFrameClockRateNormal(mFrameClockRateEwma, mFrameClockSpacingEwmaMs);
		}
		else{
			isSwitchWanted = isFrameClockRateAbnormal(rate, spacingMs) && isFrameClockRateAbnormal(mFrameClockRateEwma, mFrameClockSpacingEwmaMs);
		}
		mFrameClockModeSwitchVotes = isSwitchWanted ? mFrameClockModeSwitchVotes+1 : 0;
		if(mFrameClockModeSwitchVotes>=FRAME_CLOCK_MODE_SWITCH_WINDOWS){
			mIsFrameClockAnchored = !mIsFrameClockAnchored;
			mFrameClockModeSwitchVotes = 0;
		}
	}
	
	private static boolean isFrameClockRateAbnormal(double rate, double spacingMs){
		return rate>FRAME_CLOCK_ANCHOR_RATE_HIGH || rate<FRAME_CLOCK_ANCHOR_RATE_LOW || spacingMs>FRAME_CLOCK_ANCHOR_SPACING_MS;
	}
	
	private static boolean isFrameClockRateNormal(double rate, double spacingMs){
		return rate>=FRAME_CLOCK_UNANCHOR_RATE_LOW && rate<=FRAME_CLOCK_UNANCHOR_RATE_HIGH && spacingMs<FRAME_CLOCK_UNANCHOR_SPACING_MS;
	}
	
	private void startFrameClockRateWindow(double newestX, long newestXNanos){
		mIsFrameClockRateBaseSet = true;
		mFrameClockRateBaseX = newestX;
		mFrameClockRateBaseNanos = newestXNanos;
		mFrameClockRateLastSeenNanos = newestXNanos;
		mFrameClockRateUpdatesInWindow = 0;
	}
	
	/** EDT. Back to playout mode with no estimate, e.g. when the clock stops. */
	private void resetFrameClockRateEstimate(){
		mIsFrameClockAnchored = false;
		mIsFrameClockHoldingAfterAnchor = false;
		mIsFrameClockRateBaseSet = false;
		mFrameClockRateEwma = Double.NaN;
		mFrameClockSpacingEwmaMs = Double.NaN;
		mFrameClockModeSwitchVotes = 0;
	}
	
	/** EDT. Sets the X window without allocating a range policy per frame: the policy installed
	 * on the axis is kept and only its Range swapped (ARangePolicy.setRange() fires the property
	 * change that marks the chart for repaint). Protected so a test harness can observe frames. */
	protected void applyLiveViewport(Chart2D chart, double minX, double maxX){
		IAxis<?> axisX = chart.getAxisX();
		if(mFrameClockRangePolicy==null || axisX.getRangePolicy()!=mFrameClockRangePolicy){
			mFrameClockRangePolicy = new RangePolicyFixedViewport(new Range(minX, maxX));
			mFrameClockSpareRange = new Range(minX, maxX);
			axisX.setRangePolicy(mFrameClockRangePolicy);
			return;
		}
		Range spare = mFrameClockSpareRange;
		mFrameClockSpareRange = mFrameClockRangePolicy.getRange();
		spare.setMin(minX);
		spare.setMax(maxX);
		mFrameClockRangePolicy.setRange(spare);
	}
	
	//---------------------- DEV-896: live X viewport frame clock END -----------------------//
	
	public void setYAxisRange(double miny,double maxy){
		IAxis<?> yAxisLeft = mChart.getAxisY();
		yAxisLeft.setRangePolicy(new RangePolicyFixedViewport(new Range(miny, maxy)));
	}
	
	public void setYAxisMajorTickSpacing(double tickSpacing){
		try {
			IAxis<IAxisScalePolicy> yAxisLeft = (IAxis<IAxisScalePolicy>)mChart.getAxisY();
			yAxisLeft.setAxisScalePolicy(new AxisScalePolicyManualTicks());
			yAxisLeft.setMajorTickSpacing(tickSpacing);	
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public void setYAxisMinorTickSpacing(double tickSpacing){		
		try {
			IAxis<IAxisScalePolicy> yAxisLeft = (IAxis<IAxisScalePolicy>)mChart.getAxisY();
			yAxisLeft.setAxisScalePolicy(new AxisScalePolicyManualTicks());
			yAxisLeft.setMinorTickSpacing(tickSpacing);	
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public void setYAxisTickSize(double miny, double maxy){
		IAxis<?> yAxisLeft = mChart.getAxisY();
		yAxisLeft.setRangePolicy(new RangePolicyFixedViewport(new Range(miny, maxy)));
	}
	
	/**
	 * @return the mXAxisLimit
	 */
	public int getXAxisLimit() {
		return mXAxisLimit;
	}

	/**
	 * @param xAxisLimit the mXAxisLimit to set
	 */
	public void setXAxisLimit(int xAxisLimit) {
		this.mXAxisLimit = xAxisLimit;
	}
	
	public void initializeAxes(int pxWidth) {
		if(isXAxisTime()){
			if (pxWidth<300){
				initializeAxesForTimeSmall();
			} 
			else if (pxWidth<600){
				initializeAxesForTimeMedium();
			} 
			else {
				initializeAxesForTimeBig();
			}
		}
		else if(isXAxisFrequency()){
			initializeAxesAutoUnits();
			setXAxisLabel("Freq (Hz)", null);
//			setYAxisLabel("Power (dB)");
//			setXAxisRange(0, 100);
		} else if (isXAxisValue()){
			initializeAxesAutoUnits();
		}
	}
	
	public void initializeAxesForTimeBig(){
		initializeAxesForTime("HH:mm:ss");
	}
	
	public void initializeAxesForTimeMedium(){
		initializeAxesForTime("mm:ss");
	}
	
	public void initializeAxesForTimeSmall(){
		initializeAxesForTime("ss");
	}

	public void initializeAxesAutoUnits(){
		initializeAxesCommon(new LabelFormatterAutoUnits());
	}

	private void initializeAxesForTime(String format){
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
		simpleDateFormat.setTimeZone(timeZone);
		initializeAxesCommon(new LabelFormatterDate(simpleDateFormat));
	}
	
	private void initializeAxesCommon(IAxisLabelFormatter xAxisLblFormatter){
		if (mEnablePCTS && mChart!=null){
		  xAxis = mChart.getAxisX();
		  xAxis.setFormatter(xAxisLblFormatter);

//		  //mChart.setRequestedRepaint(true);
//		  
//		  // JC: the yAxis code seems to be legacy code which no longer does anything (20 Jan 2015)
//		  // RM: we need to create the yAxis so we can set the range
//		  yAxisLeft = new AxisLinear<IAxisScalePolicy>();
//		  
//		  //yAxisRight = new AxisLinear<IAxisScalePolicy>(); 
////			NumberFormat format = new DecimalFormat("#");
////			format.setMaximumIntegerDigits(3);
////			yAxis.setFormatter(new LabelFormatterNumber(format));
//		  if(mChart != null){
//			  if(yAxisLeft != null){
//				  //TODO the below line throws a NullPointerException sometimes! Don't know why (RM)
//				  
//				  try{
//					  mChart.setAxisYLeft(yAxisLeft, 0);   
//				  }
//				  catch(Exception e){
//					  // RM Double.Nan was causing a non critical exception here
//					  //e.printStackTrace();
//				  }
//			  }
//		  }		  
		  
		}
	}
	
	public void setTimeZone(TimeZone timeZone) {
		this.timeZone = timeZone;
	}
	
	public void clearTimeZone() {
		this.timeZone = TimeZone.getTimeZone("GMT");
	}
	
	//change color
	public void changeTraceColor(String traceName,int[] colorArray){
		int index = getTraceIndexFromName(traceName);
		if(index!=-1){
			mListOfTraceColorsCurrentlyUsed.set(index, colorArray);
			mListofTraces.get(index).setColor(new Color(colorArray[0],colorArray[1],colorArray[2]));
		}
	}
	
	public int getIndex(String name){
		int index=0;
		synchronized(mListofPropertiestoPlot){
			Iterator <String[]> entries = mListofPropertiestoPlot.iterator();
			while (entries.hasNext()) {
				String n = joinChannelStringArray(entries.next());
				if (n.equals(name)){
					return index;
				}
				index++;
			}
		}
		return -1;
	}

	private int getTraceIndexFromName(String traceName) {
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			int i=0;
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					if(trace.getName().equals(traceName)) {
						return i;
					}
				}
				i++;
			}
			return -1;
		}
	}

	public ITrace2D getTraceFromName(String traceName) {
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					if(trace.getName().equals(traceName)) {
						return trace;
					}
				}
			}
			return null;
		}
	}

	public void changeTraceColor(int index,int[] colorArray){
		//change color
		mListOfTraceColorsCurrentlyUsed.set(index, colorArray);
		mListofTraces.get(index).setColor(new Color(colorArray[0],colorArray[1],colorArray[2]));
	}
	
	public void changeAllTraceColor(int[] colorArray){
		//change color
		synchronized(mListOfTraceColorsCurrentlyUsed){
			Iterator <int[]> entries = mListOfTraceColorsCurrentlyUsed.iterator();
			while (entries.hasNext()) {
				int[] i = entries.next();
				i = colorArray;
			}
		}
		
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					trace.setColor(new Color(colorArray[0],colorArray[1],colorArray[2]));
				}
			}
		}
	}
	
	public Color getTraceColour(String traceName){
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					if(trace.getName().equals(traceName)) {
						return trace.getColor();
					}
				}
			}
			return Color.white;
		}
	}
	
	public void setTraceThickness(String traceName, float thickness) {
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					if(trace.getName().equals(traceName)) {
						BasicStroke stroke = ((BasicStroke)trace.getStroke());
						BasicStroke newstroke = new BasicStroke(thickness,stroke.getEndCap(),stroke.getLineJoin(),stroke.getMiterLimit(),stroke.getDashArray(),stroke.getDashPhase());
						trace.setStroke(newstroke);
					}
				}
			}
		}
	}
	
	public void setAllTraceThickness(float thickness) {
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					BasicStroke stroke = ((BasicStroke)trace.getStroke());
					BasicStroke newstroke = new BasicStroke(thickness,stroke.getEndCap(),stroke.getLineJoin(),stroke.getMiterLimit(),stroke.getDashArray(),stroke.getDashPhase());
					trace.setStroke(newstroke);
				}
			}
		}
	}
	
	public void increaseAllTraceThickness() {
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					BasicStroke stroke = ((BasicStroke)trace.getStroke());
					BasicStroke newstroke = new BasicStroke(stroke.getLineWidth()+1,stroke.getEndCap(),stroke.getLineJoin(),stroke.getMiterLimit(),stroke.getDashArray(),stroke.getDashPhase());
					trace.setStroke(newstroke);
				}
			}
		}
	}

	public void reduceAllTraceThickness(){
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					BasicStroke stroke = ((BasicStroke)trace.getStroke());
					if (stroke.getLineWidth()>=1){
						BasicStroke newstroke = new BasicStroke(stroke.getLineWidth()-1,stroke.getEndCap(),stroke.getLineJoin(),stroke.getMiterLimit(),stroke.getDashArray(),stroke.getDashPhase());
						trace.setStroke(newstroke);
					}
				}
			}
		}
	}
	
	public float getTraceThickness(String traceName) {
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					if(trace.getName().equals(traceName)) {
						BasicStroke stroke = ((BasicStroke)trace.getStroke());
						return stroke.getLineWidth();
					}
				}
			}
			return -1;
		}
	}

//	public void changeAllTraceStyle(TRACE_STYLE style) {
//		synchronized(mListofTraces){
//			Iterator <ITrace2D> entries = mListofTraces.iterator();
//			while (entries.hasNext()) {
//				ITrace2D trace = entries.next();
//				changeTraceStyle(trace, style);
//			}
//		}
//	}
//
//	public void changeTraceStyle(int index, TRACE_STYLE style) {
//		ITrace2D trace = mListofTraces.get(index);
//		changeTraceStyle(trace, style);
//	}
//
//	private void changeTraceStyle(ITrace2D trace, TRACE_STYLE style) {
//		if(trace != null){
//			BasicStroke strokeOld = ((BasicStroke)trace.getStroke());
//			BasicStroke strokeNew = null;
//			if (TRACE_STYLE.DASHED == style){
//				float dash1[] = {10.0f};
//				strokeNew = new BasicStroke(strokeOld.getLineWidth(),
//								BasicStroke.CAP_BUTT,
//								BasicStroke.JOIN_MITER,
//								10.0f, dash1, 0.0f);
//			}
//			else if (TRACE_STYLE.DOTTED == style){
//				float dash1[] = {3.0f};
//				strokeNew = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] {1,2}, 0);
//						/*new BasicStroke(stroke.getLineWidth(),
//								BasicStroke.CAP_ROUND,
//								BasicStroke.JOIN_ROUND,
//								3.0f, dash1, 0.0f);
//								*/
//			}
//			else if (TRACE_STYLE.CONTINUOUS == style){
//				strokeNew = new BasicStroke(strokeOld.getLineWidth());
//			}
//			
//			if(strokeNew!=null) {
//				trace.setStroke(strokeNew);
//			}
//		}
//	}

	@Override
	public void setTraceLineStyleAll(PLOT_LINE_STYLE lineStyle) {
		mDefaultLineStyle = lineStyle;
        synchronized(mListofTraces){
    		Iterator <ITrace2D> entries = mListofTraces.iterator();
    		while (entries.hasNext()) {
    			ITrace2D trace = entries.next();
    			if(trace != null){
    				setTraceLineStyle(trace, mDefaultLineStyle);
    			}
    		}
        }
	}
	
	public void setTraceLineStyle(String traceName, PLOT_LINE_STYLE plotLineStyle) {
		ITrace2D trace = getTraceFromName(traceName);
		if(trace!=null){
			setTraceLineStyle(trace, plotLineStyle);
		}
	}

	public void setTraceLineStyle(ITrace2D trace, PLOT_LINE_STYLE selectedLineStyle) {
		//Defaults
		trace.setTracePainter(new TracePainterLine());
		trace.setStroke(new BasicStroke());
		
		if(selectedLineStyle==PLOT_LINE_STYLE.CONTINUOUS 
				|| selectedLineStyle==PLOT_LINE_STYLE.INDIVIDUAL_POINTS
				|| selectedLineStyle==PLOT_LINE_STYLE.DASHED
				|| selectedLineStyle==PLOT_LINE_STYLE.DOTTED
				|| selectedLineStyle==PLOT_LINE_STYLE.INDIVIDUAL_POINTS){
			BasicStroke strokeOld = ((BasicStroke)trace.getStroke());
			BasicStroke strokeNew = null;

			if(selectedLineStyle==PLOT_LINE_STYLE.CONTINUOUS 
					|| selectedLineStyle==PLOT_LINE_STYLE.INDIVIDUAL_POINTS){
				strokeNew = new BasicStroke(
//						strokeOld.getLineWidth(),
						mTraceLineThickness, //DEV-896: was DEFAULT_LINE_THICKNESS
						strokeOld.getEndCap(),
						strokeOld.getLineJoin(),
						strokeOld.getMiterLimit(),
						strokeOld.getDashArray(),
						strokeOld.getDashPhase());
				trace.setStroke(strokeNew);
				
				if(selectedLineStyle==PLOT_LINE_STYLE.INDIVIDUAL_POINTS){
					trace.setTracePainter(new TracePainterDisc(4)); 
				}
			}
			else if (selectedLineStyle==PLOT_LINE_STYLE.DASHED){
				float dash1[] = {10.0f};
				strokeNew = new BasicStroke(strokeOld.getLineWidth(),
								BasicStroke.CAP_BUTT,
								BasicStroke.JOIN_MITER,
								10.0f, dash1, 0.0f);
				trace.setStroke(strokeNew);
			}
			else if (selectedLineStyle==PLOT_LINE_STYLE.DOTTED){
//				float dash1[] = {3.0f};
				strokeNew = new BasicStroke(
						1,
//						strokeOld.getLineWidth(),
//						DEFAULT_LINE_THICKNESS,
						BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] {1,2}, 0);
						/*new BasicStroke(stroke.getLineWidth(),
								BasicStroke.CAP_ROUND,
								BasicStroke.JOIN_ROUND,
								3.0f, dash1, 0.0f);
								*/
				trace.setStroke(strokeNew);
			}
		}
		else if(selectedLineStyle==PLOT_LINE_STYLE.BAR){
			trace.setTracePainter(new TracePainterVerticalBar(mChart));
		}
		else if(selectedLineStyle==PLOT_LINE_STYLE.FILL){
			trace.setTracePainter(new TracePainterFill(mChart));
		}
	}	
	
	/** Set the scale type on the y-axis.
	 * @param scaleSetting
	 * @param xAxisMin
	 * @param xAxisMax
	 * @param yAxisMin
	 * @param yAxisMax
	 */
	public void setYAxisScale(boolean isLeftYAxis, SCALE_SETTING scaleSetting, Object yAxisMin, Object yAxisMax){
		double yMin = 0;
		double yMax = 0;
		if(!mListofTraces.isEmpty()) {
			if(scaleSetting == SCALE_SETTING.AUTO) {
//				yMin = (double) yAxisMin;
//				yMax = (double) yAxisMax;

				IAxis<?> axisToUse = null;
				if(isLeftYAxis /*&& yAxisLeft != null*/){
					axisToUse = mChart.getAxisY();
				} else if (yAxisRight != null){
					axisToUse = yAxisRight;
				}

				// y-axis scale
//				axisToUse.setRangePolicy(new RangePolicyUnbounded(new Range(yMin, yMax)));
				axisToUse.setRangePolicy(new RangePolicyUnbounded());
				
//				// x-axis scale.
//		        double percentage = (double)5/InternalFrameWithPlotManager.mSliderMidValue;
//		        adjustTraceLength(percentage);
			}
			else if(scaleSetting == SCALE_SETTING.FIXED) {
				yMin = (double) yAxisMin;
				yMax = (double) yAxisMax;
				if(yAxisMin!=null && yAxisMax!=null) {
					setYAxisMinMax(isLeftYAxis, yMin, yMax);
				}
			}
			else if(scaleSetting == SCALE_SETTING.CUSTOM) {
				
				if(yAxisMin!=null && yAxisMax==null) {  // y-axis min only
					//utilShimmer.consolePrintLn("\nY-AXIS MIN ONLY\n");
					yMin = (double) yAxisMin;
					if(yMin<0) {
						setYAxisMinMax(isLeftYAxis, yMin, -yMin);
					}
					else {
						setYAxisMinMax(isLeftYAxis, yMin, yMin*2);
					}
				}
				else if(yAxisMin==null && yAxisMax!=null) {  // y-axis max only
					//utilShimmer.consolePrintLn("\nY-AXIS MAX ONLY\n");
					yMax = (double) yAxisMax;
					if(yMax>0) {
						setYAxisMinMax(isLeftYAxis, -yMax, yMax);
					}
					else {
						setYAxisMinMax(isLeftYAxis, (-yMax*yMax), yMax);
					}
					
				}
				else if(yAxisMin!=null && yAxisMax!=null) {  // y-axis both
					//utilShimmer.consolePrintLn("\nY-AXIS BOTH\n");
					yMin = (double) yAxisMin;
					yMax = (double) yAxisMax;
					
					setYAxisMinMax(isLeftYAxis, yMin, yMax);
				}
			}
		}
	}
	
	private void setYAxisMinMax(boolean isLeftYAxis, double minY, double maxY) {
		if(Double.isFinite(minY) && Double.isFinite(maxY)){
			Range range = new Range(minY, maxY);
			RangePolicyFixedViewport rangePolicy = new RangePolicyFixedViewport(range);

//			utilShimmer.consolePrintErrLn("\tminY=" + minY + "\tminY=" + maxY);

			IAxis<?> axisToUse = null;
			if(isLeftYAxis /*&& yAxisLeft != null*/){
				axisToUse = mChart.getAxisY();
			} else if (yAxisRight != null){
				axisToUse = yAxisRight;
			}

			if(axisToUse!=null){

				IRangePolicy currentRangePolicy = axisToUse.getRangePolicy();
				if(currentRangePolicy instanceof RangePolicyUnbounded){
					// TODO sometimes an IllegalArgurmentException error is thrown
					// when setRangePolicy() is called because there is already an
					// RangePolicyUnbounded set and this has +=Infinity as the
					// max/min values. Current solution is to try it twice in
					// order to try and replace the old RangePolicy
					
					//First fix attempt - doesn't work
//					currentRangePolicy.setRange(range);
					
					//Second fix attempt - works?
					try {
						axisToUse.setRangePolicy(rangePolicy);
						return;
					} catch (IllegalArgumentException e) {
						//Ignore
					}
				}
				
				axisToUse.setRangePolicy(rangePolicy);
			}
			
		} else {
			utilShimmer.consolePrintErrLn("\tPlot Y Axis error:\t minY=" + minY + "\tminY=" + maxY);
		}
	}

	public void adjustTraceLength(double percentage) {
		List<Color> listColor = new ArrayList<Color>();
		List<String[]> listNameArray = new ArrayList<String[]>();
		List<Set<ITracePainter<?>>> listTracePainters = new ArrayList<Set<ITracePainter<?>>>(); 
		
		//Store old settings
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					String name = trace.getName();
					int newSize = (int)(mMapofDefaultXAxisSizes.get(name)*percentage);
					String[] namearray = name.split(" ");
					listNameArray.add(namearray);
					listColor.add(trace.getColor());
					
					listTracePainters.add(trace.getTracePainters());
				}
			}
		}

		//now remove
		for (int i=0;i<listColor.size();i++){
			String[] namearray = listNameArray.get(i);
			removeSignalInternal(namearray);
		}
		//now create
		for (int i=0;i<listColor.size();i++){
			String[] namearray = listNameArray.get(i);
			String name = joinChannelStringArray(namearray);
			int newSize = (int)(mMapofDefaultXAxisSizes.get(name)*percentage);
			Color color = listColor.get(i);
			try {
				ITrace2D trace = addSignalToExistingChartInternal(namearray,newSize,color);
				
				// Trying to copy over tracepainter for the case where the trace
				// points are not joined by a line (usePaintIndividualPointsOnly)
				Set<ITracePainter<?>> tracePaintersPerTrace = listTracePainters.get(i);
				for(ITracePainter<?> iTP:tracePaintersPerTrace){
					trace.addTracePainter(iTP); 
//					trace.setTracePainter(iTP); 
				}
//				trace.setTracePainter(new TracePainterDisc(4)); 
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void adjustTraceLengthofSignalUsingSetSize(double percentage,String signal) {
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					String name = trace.getName();
					if(mMapofDefaultXAxisSizes.get(name) != null && trace.getName().contains(signal)){
						int newSize = (int)Math.round((mMapofDefaultXAxisSizes.get(name)*percentage));
						//utilShimmer.consolePrintLn("%: " + percentage +"   Size: " +mMapofDefaultXAxisSizes.get(name));
						setTraceSize(trace, newSize);
				        //utilShimmer.consolePrintErrLn("(Trace2DLtd)trace).setMaxSize: " +newSize);
					}
					else{
						//utilShimmer.consolePrintErrLn("mMapofDefaultXAxisSizes.get(name) is NULL");
					}
				}
			}
		}

	}

	private void setTraceSize(ITrace2D trace, int newSize) {
		utilShimmer.consolePrintErrLn( 
				"setTraceSize()\tTrace: " + trace.getName()
				+ " CurrentSize: " + trace.getSize()
				+ " NewSize: " + newSize);
//		UtilShimmer.consolePrintCurrentStackTrace();
		((Trace2DLtd)trace).setMaxSize(newSize);
	}

	public synchronized void adjustTraceLengthUsingSetSize(double percentage) {
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					String name = trace.getName();
					if(mMapofDefaultXAxisSizes.get(name) != null){
						int newSize = (int)Math.round((mMapofDefaultXAxisSizes.get(name)*percentage));
						setTraceSize(trace, newSize);
					}
				}
			}
		}
	}
	
	public int getTraceLengthMaxSize(String name){
		return mMapofDefaultXAxisSizes.get(name);
	}

	public int getMinTraceLengthFromTraces(){
		int min = 0;
		if(mListofTraces.size() > 0){
			min = mListofTraces.get(0).getMaxSize();
		}
		
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					if (min>trace.getMaxSize()){
						min = trace.getMaxSize();
					}
				}
			}
			return min;
		}
	}
	
	public int getMaxTraceLengthFromTraces(){
		int max = 0;
		if(mListofTraces.size() > 0){
			max = mListofTraces.get(0).getMaxSize();
		}
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					if (max<trace.getMaxSize()){
						max = trace.getMaxSize();
					}
				}
			}
			return max;
		}
	}
	
	public List<String> getTraceNamesWithMaxTraceLengthFromTraces(){
		int max = 0;
		if(mListofTraces.size() > 0){
			max = mListofTraces.get(0).getMaxSize();
		}
		
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries1 = mListofTraces.iterator();
			while (entries1.hasNext()) {
				ITrace2D trace = entries1.next();
				if(trace != null){
					if (max<trace.getMaxSize()){
						max = trace.getMaxSize();
					}
				}
			}
		}

		List<String> listS = new ArrayList<String>();
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries2 = mListofTraces.iterator();
			while (entries2.hasNext()) {
				ITrace2D trace = entries2.next();
				if(trace != null){
					if (max==trace.getMaxSize()){
						listS.add(trace.getName());
					}
				}
			}
		}

		return listS;
	}
	
	public int getMaxTraceLength(String name){
		synchronized(mListofTraces){
			Iterator<ITrace2D> iterator = mListofTraces.iterator();
			while(iterator.hasNext()){
				ITrace2D trace = iterator.next();
				if (trace.getName().contains(name)){
					return trace.getMaxSize();
				}
			}
			return -1;
		}
	}
	
	/** turn on/off legend labels along both axes */
	public void toggleLegendLabelsPainted() {
		if(mChart!=null){
			setLegendLabelsPainted(!mIsLegendLabelsPainted);
		}
	}
	
	public void setLegendLabelsPainted(boolean state){
		mIsLegendLabelsPainted = state;
		
		mChart.setPaintLabels(mIsLegendLabelsPainted);
	}
	
	public boolean isLegendLabelsPainted(){
		return mIsLegendLabelsPainted;
	}
	
	/** turn on/off scale labels along both axes */
	public void toggleScaleLabelsPainted() {
		if(mChart!=null){
			setScaleLabelsPainted(!mIsScaleLabelsPainted);
		}
	}
	
	public void setScaleLabelsPainted(boolean state){
		mIsScaleLabelsPainted = state;
		
		IAxis<?> axisX = mChart.getAxisX();
		axisX.setPaintScale(mIsScaleLabelsPainted);
		
		IAxis<?> axisY = mChart.getAxisY();
		axisY.setPaintScale(mIsScaleLabelsPainted);
		
		if(yAxisRight != null){
			yAxisRight.setPaintScale(mIsScaleLabelsPainted);
		}
	}
	
	public void setXAxisScaleLabelPainted(boolean state){
		IAxis<?> axisX = mChart.getAxisX();
		axisX.setPaintScale(state);
	}
	
	public void setYAxisScaleLabelPainted(boolean state){
		IAxis<?> axisY = mChart.getAxisY();
		axisY.setPaintScale(state);
	}
	
	public boolean isScaleLabelsPainted(){
		return mIsScaleLabelsPainted;
	}
	
	public void toggleAxisLabelsPainted() {
		if(mChart!=null){
			setAxisLabelsPainted(!mIsAxisLabelsPainted);
		}
	}
	
	public void setAxisLabelsPainted(boolean state){
		mIsAxisLabelsPainted = state;
		
		IAxis<?> axisX = mChart.getAxisX();
		IAxis<?> axisY = mChart.getAxisY();
		
		String axisXtitle = null;
		String axisYtitle = null;
		if(mIsAxisLabelsPainted){
			axisXtitle = "X";
			axisYtitle = "Y";
		}

		axisX.getAxisTitle().setTitle(axisXtitle);
		axisY.getAxisTitle().setTitle(axisYtitle);
		if(yAxisRight != null){
			yAxisRight.getAxisTitle().setTitle(null);
		}
	}
	
	public void setAxisLinePainted(boolean isAxisLinePainted){
		IAxis<?> axisX = mChart.getAxisX();
		IAxis<?> axisY = mChart.getAxisY();
		
		axisX.setVisible(false);
		axisY.setVisible(false);
	}
	
	public void setYaxisTitles(String yAxisTitleLeft, String yAxisTitleRight){
		setYaxisTitleLeft(yAxisTitleLeft);
		setYaxisTitleRight(yAxisTitleRight);
	}
	
	public void setYaxisTitleLeft(String yAxisTitleLeft){
		mChart.getAxisY().getAxisTitle().setTitle(yAxisTitleLeft);
	}
	
	public void setYaxisTitleRight(String yAxisTitleRight){
		if(yAxisRight != null){
			yAxisRight.getAxisTitle().setTitle(yAxisTitleRight);
		}
	}
	
	public boolean isAxisLabelsPainted(){
		return mIsAxisLabelsPainted;
	}
	
	/** turn on/off grids along both axes */
	public void toggleGrid() {
		if(mChart!=null){
			setGridOn(!mIsGridOn);
		}
	}
	
	/** turn on/off grids along both axes */
	public void setGridOn(boolean state) {
		if(mChart!=null){
			mIsGridOn = state;
			try{
				IAxis<?> axisX = mChart.getAxisX();
				if(axisX != null){
					axisX.setPaintGrid(mIsGridOn);
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
			try{
				IAxis<?> axisY = mChart.getAxisY();
				if(axisY != null){
					axisY.setPaintGrid(mIsGridOn);
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	
	public void turnOnGridWithSpacingValue(double spacingValue){
		((IAxis<IAxisScalePolicy>)mChart.getAxisX()).setAxisScalePolicy(new AxisScalePolicyManualTicks()); 
		mChart.getAxisX().setMinorTickSpacing(spacingValue);
	}
	
	public boolean isGridOn(){
		return mIsGridOn;
	}

	public void togglePause() {
		mIsPlotPaused = !mIsPlotPaused;
	}

	public boolean isPlotPaused() {
		return mIsPlotPaused;
	}

	public void setIsPlotPaused(boolean state) {
		mIsPlotPaused = state;
	}
	
	
	//---------------------- Heart Rate Value Display Starts -----------------------//
	public boolean isHRVisible() {
		return mIsHRVisible;
	}

	public void setIsHRVisible(boolean state) {
		mIsHRVisible = state;
	}
	
	
	//TODO
	public Color getAxisColor() {
		return null;
	}

	//TODO
	public void setAxisColor(int[] newColor) {
//		Graphics g2d = new Graphics (); 
//		g2d.setColor(this.getColor());
//		g2d.drawLine(xAxisLine, yAxisStart, xAxisLine, yAxisEnd);
//		g2d.setColor(this.getColor());
//
//		IAxisTickPainter tickPainter = new IAxisTickPainter();
//		mChart.setAxisTickPainter(tickPainter);.getAxisTickPainter().paintXTick(xAxisLine, tmp, label.isMajorTick(), true, g2d);
	}
	
	public boolean changeChannelType(String[] oldName, String[] newName){
		
		int channelIndex = getIndex(joinChannelStringArray(oldName));
		if(channelIndex>=0){
			String[] channel = mListofPropertiestoPlot.get(channelIndex);
			mListofPropertiestoPlot.remove(channelIndex);
			channel[2] = newName[2];
			mListofPropertiestoPlot.add(channelIndex, channel);
			
			int value = mMapofDefaultXAxisSizes.get(joinChannelStringArray(oldName));
			mMapofDefaultXAxisSizes.remove(joinChannelStringArray(oldName));
			mMapofDefaultXAxisSizes.put(joinChannelStringArray(newName),value);
			
			return true;
		}
		return false;
	}
	
	
	public boolean areArraysEqual(String[] array1, String[] array2){
		if(array1.length!=array2.length){
			return false;
		}
		
		for(int i=0;i<array1.length;i++){
			if(!(array1[i].equals(array2[i]))){
				return false;
			}
		}
		return true;
	}
	
	public synchronized void clearAllDataBuffer(){
		synchronized(mListofTraces){
			Iterator<ITrace2D> iterator = mListofTraces.iterator();
			while(iterator.hasNext()){
				ITrace2D trace = iterator.next();
				trace.removeAllPoints();
			}
		}

		if(mIsTraceDataBuffered){
			for(CircularFifoBuffer circularFifoBuffer : mMapOfCirculurBufferedTraceDataPoints.values()){
				circularFifoBuffer.clear();
			}
		}
		setXAxisDuration(mXAxisTimeDuration);
		mCurrentXValue=0;
		mFrameClockNewestX=0; //DEV-896
		isFirstPointOnFillTrace=true;
	}

	public void clearDataBufferAndMakeTraceVisible(String deviceName){
		clearDataBufferAndSetTraceVisibility(deviceName, true);
	}

	public void clearDataBufferAndMakeTraceInvi(String deviceName){
		clearDataBufferAndSetTraceVisibility(deviceName, false);
	}

	private void clearDataBufferAndSetTraceVisibility(String deviceName, boolean isVisible) {
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					String[] props = trace.getName().split(" ");
					// May 2017: RM commeneted out making event marker trace invisible as it was disappearing when one of multiple Shimmers stopped streaming in Consensys
					if (props[0].equals(deviceName) /*|| trace.getName().contains(InternalFrameWithPlotManager.EVENT_MARKER_PLOT_TITLE)*/){
						trace.removeAllPoints();
						trace.removeAllPointHighlighters();
						trace.setVisible(isVisible);
						//ITrace2D t = new Trace2DLtd(trace.getMaxSize());
						//t.setColor(trace.getColor());
						//t.setName(trace.getName());
						//mChart.removeTrace(trace);
						//mChart.addTrace(t);
					}
				}
			}
		}
	}

	public void setSingleTraceIsVisible(String channelName, boolean isVisible){
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					if (trace.getName().equals(channelName)){
						//trace.removeAllPoints();
						//trace.removeAllPointHighlighters();
						trace.setVisible(isVisible);
					}
				}
			}
		}
	}

	public boolean isAnyTraceVisible(){
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					if(trace.isVisible()){
						return true;
					}
				}
			}
			return false;
		}
	}

	public String getFirstTraceName(){
		if(mListofTraces != null && mListofTraces.size() > 0){
			return mListofTraces.get(0).getName();
		}
		return null;
	}
	
	public void resizeBarPlots(){
        int width = mChart.getWidth();
        synchronized(mListofTraces){
    		Iterator <ITrace2D> entries = mListofTraces.iterator();
    		while (entries.hasNext()) {
    			ITrace2D trace = entries.next();
    			if(trace != null){
    				int size = trace.getSize()-1;
    				if (size!=0){
    				 for (ITracePainter<?> t:trace.getTracePainters()){
    		            	if (t instanceof TracePainterVerticalBar){
    		            		((TracePainterVerticalBar) t).setBarWidth((int)Math.ceil(width/size));
    		            	}
    		            }
    				}
    			}
    		}
        }
	}
	
	protected void addTracePoint(ITrace2D currentTrace, double xData, double yData) {
		addPointToTrace(currentTrace, xData, yData);
		saveLastDataPoint(currentTrace.getName(), yData);
	}

	private void saveLastDataPoint(String name, double yData) {
		mMapOfLastDataPoints.put(name, yData);
	}
	
	public void addPointToTrace(ITrace2D trace, double xData, double yData){
		//TODO this is handled twice - also in checkAndCorrectData()
		if(xData==0.0 || Double.isNaN(xData) || Double.isInfinite(xData)){
			xData = 0.000001;
		}

		if(yData==0.0 || Double.isNaN(yData) || Double.isInfinite(yData)){
			yData = 0.000001;
		}

		if(mIsTraceDataBuffered){
			String traceName = trace.getName();
			if(traceName != null){
				if(!mMapOfCirculurBufferedTraceDataPoints.containsKey(traceName)){
					mMapOfCirculurBufferedTraceDataPoints.put(traceName, new CircularFifoBuffer(trace.getMaxSize()));
				}
				CircularFifoBuffer circularFifoBuffer = mMapOfCirculurBufferedTraceDataPoints.get(traceName);
				if(circularFifoBuffer != null){
					circularFifoBuffer.add(new Point2D.Double(xData, yData));
				}
			}
		}
		trace.addPoint(xData, yData);
	}

	/** DEV-896: Max points added per single chart-monitor acquisition in {@link #addPointsToTrace}.
	 * Bounds how long the batch can hold the chart lock so a large burst can't monopolise the EDT. */
	private static final int POINT_BATCH_MAX = 256;

	/** DEV-896: Max traces processed per single chart-monitor acquisition in
	 * {@link #filterDataAndPlot(ObjectCluster)}. Holding the monitor across every trace of a sample
	 * would let the worst-case EDT wait grow with the trace count: {@code Trace2DLtd.addPointInternal}
	 * still runs an O(buffer) {@code minYSearch/maxYSearch} whenever the evicted point held the Y
	 * extreme, which for a monotone or steadily drifting Y channel (battery, temperature, GSR
	 * baseline, sample counters) is essentially every sample. Re-acquiring every
	 * {@code TRACE_BATCH_MAX} traces keeps the churn saving while capping one hold at a constant
	 * number of those rescans. */
	private static final int TRACE_BATCH_MAX = 8;

	/**
	 * DEV-896: A unit of work recorded while {@link #filterDataAndPlot(ObjectCluster)} holds the
	 * chart monitor and replayed, in the order recorded, once the monitor has been released. Two
	 * kinds of work must not run under that monitor:
	 * <ul>
	 * <li>Swing calls - {@code updateHrPanelIfVisible()} is overridden downstream (Consensys) to do
	 * {@code JLabel.setText/revalidate/repaint} from the data thread. That would take
	 * chart -&gt; Swing tree/RepaintManager locks while the EDT takes tree lock -&gt; chart inside
	 * {@code Chart2D.paintComponent()}: a lock inversion.</li>
	 * <li>Console I/O - {@code throwExceptionSignalNotFound()} dumps a whole ObjectCluster per
	 * missing signal and {@code printSignalProps()} prints per sample in debug mode; holding the
	 * chart monitor across a blocking {@code System.out} write stalls the EDT for the duration.</li>
	 * </ul>
	 * A single ordered list is used (rather than one list per kind) so the replay preserves the
	 * original per-trace interleaving - the two console kinds share {@code System.out}, so grouping
	 * by kind would reorder the debug output. The list and its entries are allocated per sample,
	 * which is dwarfed by the per-sample {@code mListofTraces.toArray()} snapshot the batch already
	 * needs; they are deliberately not shared instance state, because the only monitor that would
	 * make sharing safe ({@code mListofPropertiestoPlot}) is a public non-final field that
	 * {@code AbstractPlotManager}'s constructors reassign.
	 */
	private static final class DeferredPlotAction {
		static final int KIND_SIGNAL_NOT_FOUND = 0;
		static final int KIND_PRINT_SIGNAL_PROPS = 1;
		static final int KIND_UPDATE_HR_PANEL = 2;

		final int mKind;
		final String mTraceName;
		final String[] mProps;
		/** Read while the chart monitor is held, so the deferred debug line reports the same trace
		 * size it reported before the print was moved out of the lock. */
		final int mTraceSize;
		final double mXData;
		final double mYData;

		private DeferredPlotAction(int kind, String traceName, String[] props, int traceSize, double xData, double yData){
			mKind = kind;
			mTraceName = traceName;
			mProps = props;
			mTraceSize = traceSize;
			mXData = xData;
			mYData = yData;
		}

		static DeferredPlotAction signalNotFound(String traceName){
			return new DeferredPlotAction(KIND_SIGNAL_NOT_FOUND, traceName, null, 0, 0, 0);
		}

		static DeferredPlotAction printSignalProps(int traceSize, String[] props, double xData, double yData){
			return new DeferredPlotAction(KIND_PRINT_SIGNAL_PROPS, null, props, traceSize, xData, yData);
		}

		static DeferredPlotAction updateHrPanel(String[] props){
			return new DeferredPlotAction(KIND_UPDATE_HR_PANEL, null, props, 0, 0, 0);
		}
	}

	/** DEV-896: {@code mChart} is optional - {@link #filterDataAndPlot(ObjectCluster)} falls back to
	 * another monitor when no chart is set yet - and the deferred replay can run
	 * {@code throwExceptionSignalNotFound()} / {@code printSignalProps()} in that state, so neither
	 * may dereference {@code mChart} directly. */
	private String getChartNameForPrinting(){
		return (mChart!=null)? mChart.getName() : "<no chart>";
	}

	/**
	 * DEV-896: In this base class {@link #updateHrPanelIfVisible(String[], ObjectCluster)} is a
	 * no-op, so recording a deferred HR action per trace per sample is skipped only for the base
	 * class itself. Any subclass records one deferred action per matching trace: the downstream
	 * override (Consensys {@code PlotManagerPC}) counts its calls, so every matching trace must
	 * still produce exactly one call.
	 *
	 * <p>A reflective lookup of the method by name is deliberately not used: ProGuard renaming it
	 * would make that lookup fail and silently disable the HR panel in the obfuscated release
	 * build while dev builds kept working.</p>
	 */
	private final boolean mIsHrPanelUpdateOverridden = (getClass() != BasicPlotManagerPC.class);

	/** DEV-896: Appends one action to the (lazily created) deferral list and returns the list to
	 * assign back, so the common case - no debug mode, no missing signal, no HR override - allocates
	 * nothing at all on this per-sample path. Ordering is unaffected: actions are still appended in
	 * the order they are recorded. */
	private static List<DeferredPlotAction> recordDeferredPlotAction(List<DeferredPlotAction> deferredActions, DeferredPlotAction action){
		if(deferredActions == null){
			deferredActions = new ArrayList<DeferredPlotAction>();
		}
		deferredActions.add(action);
		return deferredActions;
	}

	/** DEV-896: Replays the work recorded by {@link #filterDataAndPlot(ObjectCluster)} while it held
	 * the chart monitor. Must be called on every exit path from the batched loop, including the
	 * "Trace does not exist" throw, because before the lock was batched this work ran inline (the
	 * downstream HR panel keeps a per-call counter, so a dropped call is observable). A {@code null}
	 * list means nothing was recorded (see {@link #recordDeferredPlotAction}) and is a no-op. */
	private void replayDeferredPlotActions(List<DeferredPlotAction> deferredActions, ObjectCluster ojc) throws Exception {
		if(deferredActions == null){
			return;
		}
		for(int i=0; i<deferredActions.size(); i++){
			DeferredPlotAction action = deferredActions.get(i);
			switch(action.mKind){
				case DeferredPlotAction.KIND_SIGNAL_NOT_FOUND:
					throwExceptionSignalNotFound(action.mTraceName, ojc);
					break;
				case DeferredPlotAction.KIND_PRINT_SIGNAL_PROPS:
					printSignalProps(ojc, action.mTraceSize, action.mProps, action.mXData, action.mYData);
					break;
				case DeferredPlotAction.KIND_UPDATE_HR_PANEL:
					updateHrPanelIfVisible(action.mProps, ojc);
					break;
				default:
					break;
			}
		}
	}

	/**
	 * DEV-896: Batch variant of {@link #addPointToTrace(ITrace2D, double, double)}. Adds many
	 * points to a single trace while acquiring the chart monitor once per (bounded) chunk rather
	 * than once per point. {@code ATrace2D.addPoint()} synchronizes on the chart
	 * ({@code trace.getRenderer()}) - the same monitor {@code Chart2D.paintComponent()} holds - so
	 * adding N points individually took the monitor N times and starved the Swing EDT. Java monitors
	 * are reentrant, so {@code addPoint()}'s internal {@code synchronized(chart)} is free while we
	 * hold the outer lock. Behaviour per point is identical to {@link #addPointToTrace}.
	 */
	public void addPointsToTrace(ITrace2D trace, double[] xData, double[] yData){
		if(xData == null || yData == null){
			return;
		}
		addPointsToTrace(trace, xData, yData, 0, Math.min(xData.length, yData.length));
	}

	/**
	 * DEV-896: See {@link #addPointsToTrace(ITrace2D, double[], double[])}. Adds points
	 * {@code [fromIndex, toIndex)} from the given arrays.
	 */
	public void addPointsToTrace(ITrace2D trace, double[] xData, double[] yData, int fromIndex, int toIndex){
		if(trace == null || xData == null || yData == null){
			return;
		}
		int end = Math.min(toIndex, Math.min(xData.length, yData.length));
		int i = Math.max(0, fromIndex);
		//trace.getRenderer() returns the Chart2D the trace was added to (null until then); it is the
		//exact monitor ATrace2D.addPoint() locks, so holding it makes the per-point locks reentrant.
		Chart2D chart = trace.getRenderer();
		while(i < end){
			int chunkEnd = Math.min(i + POINT_BATCH_MAX, end);
			if(chart != null){
				synchronized(chart){
					for(; i < chunkEnd; i++){
						addPointToTrace(trace, xData[i], yData[i]);
					}
				}
			} else {
				for(; i < chunkEnd; i++){
					addPointToTrace(trace, xData[i], yData[i]);
				}
			}
		}
	}

	public CircularFifoBuffer getCirculurBufferedTraceData(String traceName){
		CircularFifoBuffer circularFifoBuffer = mMapOfCirculurBufferedTraceDataPoints.get(traceName);
		if(circularFifoBuffer != null){ 
			return circularFifoBuffer;
		}
		return null;
	}
	
	public void setIsTraceDataBuffered(boolean isTraceDataBuffered){
		mIsTraceDataBuffered = isTraceDataBuffered;
	}
	
	protected double checkAndCorrectData(String shimmerUserAssignedName, String channelName, String traceName, double data) throws Exception {
		double yData = data;
		if (Double.isNaN(yData)){
			throw new Exception("Signal data is NaN: (" + traceName + ")");
		}
		else if (Double.isInfinite(yData)){
			throw new Exception("Signal data is Infinite: (" + traceName + ")");
		}		
		// Make sure data isn't 0.0 for plotting, otherwise it causes GUI to hang
		else if(yData == 0.0){
			yData = 0.000001;
		}
		
		return yData;
	}
	
//	private void updateMetricPanelIfVisable(String[] props, ObjectCluster ojc) {
//		if(mIsMetricVisible){
//			if(mUpdateCounterForHRLabel == 0){
//				setPnlHR(props, ojc);
//			}
//			mUpdateCounterForHRLabel++;
//			if(mUpdateCounterForHRLabel > 128){ //update HR panel after 128 object clusters received to limit number of update calls
//				mUpdateCounterForHRLabel = 0;
//			}
//		}
//	}
	

	/**This plots the data of the specified signals where the signal to be plotted from ojc holds multiple samples
	 * This method is not used in Consensys or ConsensysGQ currently, it's just used in GUI Medica Balance, GUI Test Balance
	 * @param ojc ObjectCluster holding the data
	 * @param index int indicating which sample to plot
	 * @throws Exception When signal is not found
	 */
	//TODO don't duplicate an entire method, use common code from existing filterDataAndPlot method
	@Deprecated
	public void filterDataAndPlotList(ObjectCluster ojc, int index) throws Exception {
		if(!mIsPlotPaused){
			String shimmerName = ojc.getShimmerName();
			double xData = getXDataForPlotting(shimmerName, ojc, index);
			
			//MN testing
//			for(ITrace2D trace:mChart.getTraces()){
//				String[] props = trace.getName().split(" ");
				
			synchronized(mListofPropertiestoPlot){
				Iterator <String[]> entries = mListofPropertiestoPlot.iterator();
				int i = 0;
				while (entries.hasNext()) {
					String[] props = entries.next();
					
					if (shimmerName.equals(props[0])){
						FormatCluster f = ObjectCluster.returnFormatCluster(ojc.getCollectionOfFormatClusters(props[1]), props[2]);
						if (f!=null && f.getDataObject()!=null){
							mCurrentXValue = xData;
							double yData = f.getDataObject().get(index);
							ITrace2D trace = mListofTraces.get(i); 
							addTracePoint(trace, xData, yData);
													
//							if(InternalFrameWithPlotManager.mShowInstantaneousValuesPanel){
//								if(mCurrentXValue%12 == 0) {
//									String compareNames = props[0]+"_"+props[1];
//									for(String key : InternalFrameWithPlotManager.instantaneousValuesTextFields.keySet()) {
//										if(compareNames.equals(key)) {
//											DecimalFormat dc = new DecimalFormat("0.00");
//											String formattedText = dc.format(f.mData);
//											InternalFrameWithPlotManager.instantaneousValuesTextFields.get(key).setText(formattedText);
//										}
//									}
//								}
//							}
							
						} 
						else {
							throwExceptionSignalNotFound(props, ojc);
						}
					}
					i++;
				}
			}
		}
	}
	
	private void throwExceptionSignalNotFound(String[] props, ObjectCluster ojc) throws Exception {
		throwExceptionSignalNotFound(joinChannelStringArray(props), ojc);
	}

	private void throwExceptionSignalNotFound(String traceName, ObjectCluster ojc) throws Exception {
		utilShimmer.consolePrintLn("mChart.getName(): " +getChartNameForPrinting());
		if(ojc!=null) {
			ojc.consolePrintChannelsAndDataSingleLine();
		}
		//throw new Exception("Signal not found: (" + traceName + ")"); MAY 2018: RM commented out for NEUR-685 as it conflicts with 'continue' keyword where this method is called
	}

	private double getXDataForPlotting(String shimmerName, ObjectCluster ojc, int index) {
		double xData = 0;
		//first check is x axis signal exist
		if (mMapofXAxis.size()>0){
			if (mMapofXAxis.get(shimmerName)==null){
				//check if generated x axis exist
				if (mMapofXAxisGeneratedValue.get(shimmerName)==null){
					mMapofXAxisGeneratedValue.put(shimmerName, xData);
				} else {
					//if exist take the value
					xData = mMapofXAxisGeneratedValue.get(shimmerName);
				}
				 
				//check if x is the max value 
				if (xData==mCurrentXValue){
					xData=xData+1;
				} else {
					xData=mCurrentXValue;
				}
				mMapofXAxisGeneratedValue.remove(shimmerName);
				mMapofXAxisGeneratedValue.put(shimmerName, xData);
			} 
			else {
				String[] props = mMapofXAxis.get(shimmerName);
				FormatCluster f = ObjectCluster.returnFormatCluster(ojc.getCollectionOfFormatClusters(props[1]), props[2]);
				xData = f.mDataObject.get(index);
			}
		}
		else {
			utilShimmer.consolePrintErrLn("ERROR PLOTMANGERPC -> NO X DATA LOADED AT ALL");
		}
		return xData;
	}

	protected void printSignalProps(ObjectCluster ojc, ITrace2D currentTrace, String[] props, double xData, double yData){
		if(mIsDebugMode && currentTrace!=null){
			printSignalProps(ojc, currentTrace.getSize(), props, xData, yData);
		}
	}

	/** DEV-896: Variant taking an already-read trace size, so the caller can read the size while it
	 * holds the chart monitor but do the (blocking) console write after releasing it. */
	protected void printSignalProps(ObjectCluster ojc, int traceSize, String[] props, double xData, double yData){
		if(mIsDebugMode){
			utilShimmer.consolePrintErrLn(
					"ChartName:" + getChartNameForPrinting()
					+ "\tShimmerName:" + ojc.getShimmerName()
					+ "\ttrace size:" + traceSize + "."
					+ "\tprops1:" + props[1] + "."
					+ "\tprops2:" + props[2] + "."
					+ "\tx-value:" + xData + "."
					+ "\ty-value:" + yData + ".");
		}
	}
	
	
	
	public String getPlotTitleWithSignal(String signal){
		return  getTitle() + "_" + signal;
	}

	protected FormatCluster getFormatCluster(String[] props, ObjectCluster ojc) {
		return ObjectCluster.returnFormatCluster(ojc.getPropertyCluster().get(props[1]), props[2]);
	}



	protected String getHRvalue(FormatCluster f) {
		DecimalFormat dc = new DecimalFormat("0");
		String formattedText = " " + dc.format(f.mData) + " ";  // Padding String so that single ECGtoValue wont overflow on gui 
		return formattedText;
	}

	//----------------------FFT timer test code start ---------------------
	public void startTimerCalculateFft() {
		stopTimerCalculateFft();
		
		if (mTimerCalculateFft == null) {
			mTimerCalculateFft = new Timer(mChart.getName() + "_FFT_Timer");
			//mTimerCalculateFft.schedule(new calculateFftTimerTask(mTimerPeriodCalculateFft),mTimerDelayCalculateFft, mTimerPeriodCalculateFft);
			mTimerCalculateFft.schedule(new calculateFftTimerTask(),mTimerDelayCalculateFft, mTimerPeriodCalculateFft);
		}
	}

	public void stopTimerCalculateFft() {
		if (mTimerCalculateFft != null) {
			mTimerCalculateFft.cancel();
			mTimerCalculateFft.purge();
			mTimerCalculateFft = null;
		}
	}

	/**
	 * Timer used to read perdiocally the shimmer status when LogAndStream FW is
	 * installed
	 */
	public class calculateFftTimerTask extends TimerTask {
		
		@Override
		public void run() {
			if(!isPlotPaused()){
				// clear all traces
				//clearAllDataBuffer();
				
				Iterator<FftCalculateDetails> iterator = mMapOfFftsToPlot.values().iterator();
				while(iterator.hasNext()){
					FftCalculateDetails fftCalculateDetails = iterator.next();
					
//					// clear this trace
//					ITrace2D trace = getTraceFromName(joinChannelStringArray(fftCalculateDetails.mTraceName));
//					trace.removeAllPoints();
					
					double[][] results = fftCalculateDetails.calculateFftAndGenerateArray(mTimerPeriodCalculateFft);
					String traceName = fftCalculateDetails.getTraceNameJoined();
					ITrace2D trace = getTraceFromName(traceName);
					
					//InternalFrameWithPlotManager.setLblMetricIfEnabled(fftCalculateDetails.meanFreq, fftCalculateDetails.meanFreq);
					
					if(trace!=null){
						int startBin = (mIsFftShowingDc? 0:1);
						if(results.length==2 && results[0].length>startBin){
							
							trace.removeAllPoints();

							//DEV-896: batch the FFT bins into one chart-monitor acquisition per chunk
							addPointsToTrace(trace, results[0], results[1], startBin, results[0].length);
						}
					}
					
					//double[][] psdResults = fftCalculateDetails.calculatePSDAndGenerateArray(results);
					
//					ObjectCluster[] ojcArray = fftCalculateDetails.calculateFftAndGenerateOJC();
//					try {
//						for(ObjectCluster ojc:ojcArray){
//							filterDataAndPlot(ojc);
//						}
//					} catch (Exception e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
						
					fftCalculateDetails.clearBuffers();
				}				
			}

		}
	}
	
	public void setTraceVisible(String channelName){
		synchronized(mListofTraces){
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					String[] props = trace.getName().split(" ");
					if(props.length > 1){
						if (props[1].equals(channelName)
								|| channelName.equals("all")
								|| trace.getName().contains(mEventMarkerCheck)){
							//trace.removeAllPoints();
							//trace.removeAllPointHighlighters();
							trace.setVisible(true);
						}
						else{
							trace.setVisible(false);
						}
					}
					else{
						trace.setVisible(false);
					}
				}
			}
		}
	}


	/**This plots the data of the specified signals 
	 * 
	 * @param ojc ObjectCluster holding the data
	 * @throws Exception When signal is not found
	 */
	public void filterDataAndPlot(ObjectCluster ojc) throws Exception {
		if(!mIsPlotPaused){
			//		utilShimmer.consolePrintErrLn("PLOTMANGERPC -> STAGE1");
			String shimmerName = ojc.getShimmerName();

			double xData = getXDataForPlotting(shimmerName, ojc);
			//		utilShimmer.consolePrintErrLn("PLOTMANGERPC -> STAGE2");

			//Sometimes the first x data point of a new graphs comes back with a zero so return if it does  
			if(xData==0){
				return;
			};

			//MN testing trying to get rid of legend flutter
			//		for(ITrace2D trace:mChart.getTraces()){
			//			String[] props = trace.getName().split(" ");

			boolean isXAxisTime = isXAxisTime();
			boolean isXAxisFrequency = isXAxisFrequency();
			boolean isXAxisValue = isXAxisValue();
			synchronized(mListofPropertiestoPlot){
				Iterator <String[]> entries = mListofPropertiestoPlot.iterator();
				int indexOfTrace = 0;
				boolean isDummyPointAddedToFillTrace = false;

				//DEV-896: Acquire the chart monitor once per group of TRACE_BATCH_MAX traces instead
				//of once per trace inside ATrace2D.addPoint(). addPoint() synchronizes on the chart
				//(trace.getRenderer()) - the same monitor Chart2D.paintComponent() holds - so grabbing
				//it once per point was starving the Swing EDT. Java monitors are reentrant, so the
				//per-point synchronized(chart) inside addPoint() is free while we hold this outer lock.
				//The monitor is released and re-acquired between groups so one hold stays bounded by a
				//constant, not by the trace count (see TRACE_BATCH_MAX).
				//Falls back to the already-held mListofPropertiestoPlot monitor if no chart is set yet.
				//IMPORTANT (lock ordering): snapshot mListofTraces BEFORE the first chart-monitor
				//acquisition, and keep using that one snapshot for the whole sample. Other threads
				//(e.g. clearAllDataBuffer, trace resizing) hold the mListofTraces monitor while calling
				//chart-locking trace mutators (removeAllPoints/setMaxSize), i.e. mListofTraces -> chart.
				//Touching mListofTraces while holding the chart monitor here would be the reverse order
				//and a real deadlock cycle.
				//The per-sample toArray() allocation is deliberate: reusing a cached array would need
				//the mListofTraces monitor (or a copy under it) at exactly the point where taking that
				//monitor is what we are avoiding, so there is no trivially safe reuse here.
				//No explicit synchronized(mListofTraces) is needed for the snapshot itself either:
				//mListofTraces is a Collections.synchronizedList, so toArray() already copies under
				//that list's own mutex and cannot observe a half-applied structural change. Its index
				//alignment with mListofPropertiestoPlot is what actually matters here, and that is
				//protected by the mListofPropertiestoPlot monitor this method holds for the whole
				//sample: removeSignal()/removeSignalInternal() mutate both lists under it. The one
				//exception is removeAllSignals(), which holds neither - but it also clears
				//mListofPropertiestoPlot underneath this method's live iterator, a pre-existing hazard
				//that predates and is independent of this batching.
				ITrace2D[] tracesSnapshot = mListofTraces.toArray(new ITrace2D[0]);
				Object chartMonitor = (mChart != null) ? (Object)mChart : (Object)mListofPropertiestoPlot;
				//DEV-896: nothing inside the chart monitor below may call Swing or do console I/O -
				//such work is recorded here and replayed afterwards (see DeferredPlotAction).
				//Left null until something is actually recorded: on the typical sample (no debug
				//mode, no missing signal, no HR override) nothing is, so this per-sample path
				//allocates no list at all. See recordDeferredPlotAction().
				List<DeferredPlotAction> deferredActions = null;
				//DEV-896: stash rather than propagate, so the deferred work still gets replayed on the
				//"Trace does not exist" path (it used to run inline, before the batching).
				Exception pendingException = null;
				//DEV-896: whether this sample moved mCurrentXValue, i.e. is live data for the frame clock.
				boolean isCurrentXValueUpdated = false;
				double xDataForFrameClock = 0;
				try {
				while (entries.hasNext()) {
				synchronized(chartMonitor){
				for(int tracesThisBatch=0; tracesThisBatch<TRACE_BATCH_MAX && entries.hasNext(); tracesThisBatch++){
					String[] props = entries.next();
					
					String traceName = joinChannelStringArray(props);

					//prevent eventmarkers from plotting back in time
					boolean eventMarker=false;
					
					if (isEventMarkerData(shimmerName, props[0])){
						if (xData>mCurrentXValue){
							eventMarker=true;
						} 
						else { // skip any data which is in the past, as there are multiple shimmer devices, this is possible
							//JC: Just to be safe, do a check to ensure a marker is not missed, this is probably not needed..
							FormatCluster f = ObjectCluster.returnFormatCluster(ojc.getCollectionOfFormatClusters(props[1]), props[2]);
							if(f == null){
								indexOfTrace++;
								deferredActions = recordDeferredPlotAction(deferredActions, DeferredPlotAction.signalNotFound(traceName)); //DEV-896: printed after the chart monitor is released
								continue;
							}

							double yData = f.mData;
							try {
								yData = checkAndCorrectData(ojc.getShimmerName(), props[1], traceName, f.mData);
							} catch (Exception e) {
								indexOfTrace++;
								//2018-03-08 MN:Used to throw the entire method here but removing this for the moment
								continue;
							}
							
							if (yData!=-1){ //marker detected
								xData=mCurrentXValue; //ensure the timestamp doesnt go back in time
								eventMarker=true;
							} 
							else {
								eventMarker=false;
							}
						}
					}

					if (shimmerName.equals(props[0]) || eventMarker){

						FormatCluster f = ObjectCluster.returnFormatCluster(ojc.getCollectionOfFormatClusters(props[1]), props[2]);
						if(f == null){
							indexOfTrace++;
							deferredActions = recordDeferredPlotAction(deferredActions, DeferredPlotAction.signalNotFound(traceName)); //DEV-896: printed after the chart monitor is released
							continue;
						}

						double yData = f.mData;
						try {
							yData = checkAndCorrectData(shimmerName, props[1], traceName, f.mData);
						} catch (Exception e) {
							indexOfTrace++;
							//2018-03-08 MN:Used to throw the entire method here but removing this for the moment
							continue;
						}

						//DEV-896: was '>' (pre-existing off-by-one against mListofTraces.size());
						//indexOfTrace == length is already out of bounds.
						if (indexOfTrace>=tracesSnapshot.length){
							throw new Exception("Trace does not exist: (" + traceName + ")");
						}
						ITrace2D currentTrace = tracesSnapshot[indexOfTrace];
						//utilShimmer.consolePrintErrLn(currentTrace.getMaxY());

						//DEV-896: defensive null check only. The snapshot is taken before the first
						//chart-monitor acquisition, so in principle a trace removed mid-sample could
						//still be in it, but there is no cheap way to detect that: jchart2d 3.3.2's
						//Chart2D.removeTrace() does not clear the trace's renderer, and the only real
						//"still attached" check, Chart2D.getTraces(), builds a fresh TreeSet per call.
						//In practice removeSignal()/removeSignalInternal() mutate mListofTraces under
						//the mListofPropertiestoPlot monitor this method holds for the whole sample, so
						//a stale entry cannot appear via them; a stale entry from any other path just
						//receives points into a buffer nothing paints, as it did before the batching.
						//Do NOT filter on getRenderer()==null here: that only catches a trace that was
						//never attached to a chart, and silently swallowing the IllegalStateException
						//jchart2d raises for that would also skip the mCurrentXValue update below.
						if (currentTrace==null){
							indexOfTrace++;
							continue;
						}

						mCurrentXValue = xData;
						isCurrentXValueUpdated = true;
						xDataForFrameClock = xData;

						//DEV-896: record instead of printing/updating Swing here - see DeferredPlotAction.
						//The trace size is read now, under the monitor, so the deferred debug line
						//matches what it printed before batching.
						if(mIsDebugMode){
							deferredActions = recordDeferredPlotAction(deferredActions, DeferredPlotAction.printSignalProps(currentTrace.getSize(), props, xData, yData));
						}

						//Recorded once per matching trace whenever the runtime class actually overrides
						//updateHrPanelIfVisible(): whether a panel is currently visible is known only
						//to that override, and it counts its calls, so no further filtering is safe.
						//When it is not overridden the replayed call would be a no-op, so skip the
						//record (and its allocation) entirely - see mIsHrPanelUpdateOverridden.
						if(mIsHrPanelUpdateOverridden){
							deferredActions = recordDeferredPlotAction(deferredActions, DeferredPlotAction.updateHrPanel(props));
						}

						Double halfWindowSize = mMapofHalfWindowSize.get(traceName);
						if (halfWindowSize!=null){
							if(addDummyPointToFillTraceIfRequired(currentTrace, xData-halfWindowSize)) {
								isDummyPointAddedToFillTrace = true;
							}
							addPointToTrace(currentTrace, xData-halfWindowSize, yData);
						} 
						else {
							if(isXAxisTime){
								if(addDummyPointToFillTraceIfRequired(currentTrace, xData)) {
									isDummyPointAddedToFillTrace = true;
								}
								addTracePoint(currentTrace, xData, yData);
							}
							else if(isXAxisFrequency){
								//TODO buffer data for FFT calculation
								FftCalculateDetails fftCalculateDetails = mMapOfFftsToPlot.get(traceName);
								if(fftCalculateDetails!=null){
									fftCalculateDetails.addData(xData, yData);
								}
							} else if (isXAxisValue){
								if(addDummyPointToFillTraceIfRequired(currentTrace, xData)) {
									isDummyPointAddedToFillTrace = true;
								}
								addTracePoint(currentTrace, xData, yData);
							}
						}

						// the below isn't used.. yet..
						//					if(InternalFrameWithPlotManager.mShowInstantaneousValuesPanel){
						//						if(mCurrentXValue%12 == 0) {
						//							String compareNames = props[0]+"_"+props[1];
						//							for(String key : InternalFrameWithPlotManager.instantaneousValuesTextFields.keySet()) {
						//								if(compareNames.equals(key)) {
						//									DecimalFormat dc = new DecimalFormat("0.00");
						//									String formattedText = dc.format(f.mData);
						//									InternalFrameWithPlotManager.instantaneousValuesTextFields.get(key).setText(formattedText);
						//								}
						//							}
						//						}
						//					}
					}
					indexOfTrace++;
				}
				} //DEV-896: release the chart monitor between groups of TRACE_BATCH_MAX traces
				} //while(entries.hasNext())
				} catch (Exception e) {
					pendingException = e;
				}

				//DEV-896: replay, in the recorded order, the work that must not run under the chart
				//monitor. This runs on every exit path from the loop above, the "Trace does not exist"
				//throw included, because before the batching it ran inline per trace.
				try {
					replayDeferredPlotActions(deferredActions, ojc);
				} catch (Exception replayException) {
					//DEV-896: never let a replay failure hide the loop's own exception.
					if(pendingException != null){
						pendingException.addSuppressed(replayException);
					} else {
						pendingException = replayException;
					}
				}
				if(pendingException != null){
					throw pendingException;
				}

				if(isDummyPointAddedToFillTrace) {
					isFirstPointOnFillTrace = false;
				}
				
				//DEV-896: the frame clock, not this per-sample path, moves the X window of a live
				//time-axis plot (see setLiveViewportFrameClockEnabled()).
				if(isCurrentXValueUpdated && isXAxisTime && mIsLiveViewportFrameClockEnabled){
					noteLiveSampleForFrameClock(xDataForFrameClock);
				}
			}
		}
		//	mChart.getAxisX().setRange(new Range(mCurrentXValue-(mXAxisTimeDuraton*1000),mCurrentXValue));
		//setXAxisRange(mCurrentXValue-(mXAxisTimeDuraton*1000), mCurrentXValue);
		//filterOldDataOutOfTrace();
		if(pcf!=null){
			pcf.custom(this);
		}
	}

	public void setEventMarkerDataCheck(boolean isSingleEventMarkerTest) {
		this.isSingleEventMarkerTest = isSingleEventMarkerTest;
	}
	
	private boolean isEventMarkerData(String shimmerName, String signalName) {
		if(isSingleEventMarkerTest) {
			// used for Consensys (CON-628)
			return mEventMarkerCheck.equals(signalName);
		}
		else {
			// used for NeuroLynQ
			return mEventMarkerCheck.equals(signalName) && shimmerName.equals(signalName);
		}
	}
	
	/**
	 * Method to add a dummy point as the first point in the trace if the line style is fill
	 * so that the chart doesn't plot from (0, 0), this method is overriden in PlotManagerPC
	 * @param currentTrace
	 * @param xData
	 */
	public boolean addDummyPointToFillTraceIfRequired(ITrace2D currentTrace, double xData) {
		return false;
	}
	
	//TODO Method under development
	public void filterDataAndPlotBasic(List<String[]> listOfSignals, List<double[]> dataArray) throws Exception {
		
		for(int x=0;x<listOfSignals.size();x++){
			String[] signal = listOfSignals.get(x);
			synchronized(mListofPropertiestoPlot){
				Iterator <String[]> entries = mListofPropertiestoPlot.iterator();
				int i = 0;
				while (entries.hasNext()) {
					String[] props = entries.next();
					
					if(props[0].equals(signal[0]) 
							&& props[1].equals(signal[1])
							&& props[2].equals(signal[2])
							&& props[3].equals(signal[3])){
						ITrace2D trace = mListofTraces.get(i);
						
						for(double[] data:dataArray){
							//TODO hack. We assume index 0 is time and for cross-session aggregation the 2nd column is skipped
							trace.addPoint(data[0], data[x+2]);
						}
					}
					i++;
				}
			}
		}
	}

	//used by advance plot manager
	protected void updateHrPanelIfVisible(String[] props, ObjectCluster ojc) {
		//Does nothing in basic
		
	}

	private double getXDataForPlotting(String shimmerName, ObjectCluster ojc) throws Exception {
		double xData = 0;
		//first check is x axis signal exist
		if (mMapofXAxis.size()>0){ 
			//was
			//if (mMapofXAxis.get(shimmerName)==null){
			if (mMapofXAxis.get(shimmerName)==null && !mMapofXAxis.containsKey(mEventMarkerCheck)){
				//check if generated x axis exist
				if (mMapofXAxisGeneratedValue.get(shimmerName)==null){
					mMapofXAxisGeneratedValue.put(shimmerName, xData);
				} else {
					//if exist take the value
					xData = mMapofXAxisGeneratedValue.get(shimmerName);
					//				utilShimmer.consolePrintErrLn("X1 VALUE: " +xData);
				}

				//check if x is the max value 
				if (xData==mCurrentXValue){
					xData=xData+1;
					//				utilShimmer.consolePrintErrLn("X2 VALUE: " +xData);
				} else {
					xData=mCurrentXValue;
				}
				mMapofXAxisGeneratedValue.remove(shimmerName);
				mMapofXAxisGeneratedValue.put(shimmerName, xData);
			} 
			else {
				String[] props = mMapofXAxis.get(shimmerName);
				//New code
				if(props == null){
					props = mMapofXAxis.get(mEventMarkerCheck);
				}

				FormatCluster f = ObjectCluster.returnFormatCluster(ojc.getCollectionOfFormatClusters(props[1]), props[2]);
				if(f!=null){
					xData = f.mData;
				}
				else{
					utilShimmer.consolePrintErrLn("ERROR PLOTMANGERPC -> NO X DATA - " 
							+ "\nDeviceName=" + shimmerName 
							+ "\tSignalName=" + Arrays.toString(props)
							+ "\tmEventMarkerCheck=" + mEventMarkerCheck);
					throw new Exception("No X data: (" + joinChannelStringArray(props) + ")");
				}
			}
		}
		else{
			utilShimmer.consolePrintErrLn("ERROR PLOTMANGERPC -> NO X DATA LOADED AT ALL");
		}
		return xData;
	}
	
	
	public void setXAisType(CHANNEL_AXES xAxisType) {
		mXAisType = xAxisType;
		initializeAxes(1000);//Any value
	}

	public boolean isXAxisValue() {
		return (mXAisType==CHANNEL_AXES.VALUE? true:false);
	}
	
	public boolean isXAxisTime(){
		return (mXAisType==CHANNEL_AXES.TIME? true:false);
	}

	public boolean isXAxisFrequency(){
		return (mXAisType==CHANNEL_AXES.FREQUENCY? true:false);
	}

	public boolean isFftShowingDc() {
		return mIsFftShowingDc;
	}

	public void setIsFftShowingDc(boolean state) {
		mIsFftShowingDc = state;
	}

	public double getFftIntevalInSec(){
		return (mTimerPeriodCalculateFft/1000);
	}
	public void setFftInterval(double chosenScale) {
		stopTimerCalculateFft();
		mTimerPeriodCalculateFft = (int) (chosenScale*1000);
		mTimerDelayCalculateFft = (int) (chosenScale*1000);
		startTimerCalculateFft();
	}

	public int getFftOverlapPercent(){
		return mFftOverlapPercent ;
	}
	public void setFftOverlapPercent(int chosenScale) {
		mFftOverlapPercent = chosenScale;
		
		Iterator<FftCalculateDetails> iterator = mMapOfFftsToPlot.values().iterator();
		while(iterator.hasNext()){
			FftCalculateDetails fftCalculateDetails = iterator.next();
			fftCalculateDetails.setFftOverlapPercent(mFftOverlapPercent);
		}
	}
	
	/** Tries to create a transparent image of the chart. Contents based on the method Chart2D.snapShot()
	 * @return
	 */
	public BufferedImage getSnapShot() {
//		mChart.snapShot()

		synchronized (this) {
			Color savedColour = mChart.getBackground();
			
			mChart.setBackground(null);
			mChart.setOpaque(false);
			
			BufferedImage img = new BufferedImage(mChart.getWidth(), mChart.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = (Graphics2D) img.getGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			mChart.paint(g2d);
			
			mChart.setBackground(savedColour);
			mChart.setOpaque(true);

			return img;
		}
	}	
	
	
	public void printListOfTraces(){
		synchronized(mListofTraces){
			utilShimmer.consolePrintLn("List Of Traces");
			Iterator <ITrace2D> entries = mListofTraces.iterator();
			while (entries.hasNext()) {
				ITrace2D trace = entries.next();
				if(trace != null){
					utilShimmer.consolePrintLn("\tLabel: " + trace.getLabel());
				}
			}
			utilShimmer.consolePrintLn("");
		}
	}
	
	public void addChart(Chart2D chart) {
		mChart = chart;
	}
	
	/** Currently this method, has a problem when the end of the playback is reached, and the playback restarts itself
	 * 
	 */
	protected void filterOldDataOutOfTrace(){
		for (ITrace2D trace:mListofTraces){
			Iterator itr = trace.iterator(); 
			boolean reset=false;
			while (itr.hasNext()){
				ITracePoint2D itp = (ITracePoint2D) itr.next();
				if (itp.getX()<(mCurrentXValue-(mXAxisTimeDuration*1000))){
					reset=true;
					break;
				} else {
					break;
				}
			}
			if (reset){
				trace.setVisible(false);
			} else {
				trace.setVisible(true);
			}
		}
	}
	
	/**
	 * @param duration this sets the range policy of the x axis, depending on the most recent xaxis data value
	 */
	public void setXAxisDuration(double duration){
		mXAxisTimeDuration = duration;
	}
	//----------------------FFT timer test code start ---------------------

	
	
}
