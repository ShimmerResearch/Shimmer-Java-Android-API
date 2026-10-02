package com.shimmerresearch.driver.calibration;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.TreeMap;

import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driverUtilities.ChannelDetails;
import com.shimmerresearch.driverUtilities.UtilParseData;
import com.shimmerresearch.driverUtilities.UtilShimmer;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_DATA_TYPE;

/**
 * Class that holds the calibration parameters for a particular range in a
 * Kinematic sensor
 * 
 * @author Mark Nolan
 *
 */
public class CalibDetailsKinematic extends CalibDetails implements Serializable {
	
	/** * */
	private static final long serialVersionUID = -3556098650349506733L;
	
	public class CALIB_KINEMATIC_PARAM{
		public static final String OFFSET_B0 = "b0"; //called x elsewhere?
		public static final String OFFSET_B1 = "b1"; //called y elsewhere?
		public static final String OFFSET_B2 = "b2"; //called z elsewhere?
		public static final String SENSITIVITY_K0 = "k0"; //called x elsewhere?
		public static final String SENSITIVITY_K1 = "k1"; //called y elsewhere?
		public static final String SENSITIVITY_K2 = "k2"; //called z elsewhere?
		public static final String ALIGNMENT_R00 = "r00"; //called XX elsewhere?
		public static final String ALIGNMENT_R01 = "r01"; //called XY elsewhere?
		public static final String ALIGNMENT_R02 = "r02"; //called XZ elsewhere?
		public static final String ALIGNMENT_R10 = "r10"; //called YX elsewhere?
		public static final String ALIGNMENT_R11 = "r11"; //called YY elsewhere?
		public static final String ALIGNMENT_R12 = "r12"; //called YZ elsewhere?
		public static final String ALIGNMENT_R20 = "r20"; //called ZX elsewhere?
		public static final String ALIGNMENT_R21 = "r21"; //called ZY elsewhere?
		public static final String ALIGNMENT_R22 = "r22"; //called ZZ elsewhere?
	}
	
	public CalibArraysKinematic mCurrentCalibration = new CalibArraysKinematic();
	public CalibArraysKinematic mDefaultCalibration = new CalibArraysKinematic();
	
//	//TODO: improve below, needed here?
	public CalibArraysKinematic mEmptyCalibration = new CalibArraysKinematic(
			new double[][]{{0.0, 0.0, 0.0}, {0.0, 0.0, 0.0}, {0.0, 0.0, 0.0}},
			new double[][]{{0.0, 0.0, 0.0}, {0.0, 0.0, 0.0}, {0.0, 0.0, 0.0}},
			new double[][]{{0.0, 0.0, 0.0}, {0.0, 0.0, 0.0}, {0.0, 0.0, 0.0}});
	
	public enum CALIBRATION_SCALE_FACTOR{
		NONE(1.0),
		TEN(10.0),
		ONE_HUNDRED(100.0);
		
		double scaleFactor = 1.0;
		private CALIBRATION_SCALE_FACTOR(double scaleFactor) {
			this.scaleFactor = scaleFactor;
		}
	}
	//TODO offset scale factor currently not used
	public CALIBRATION_SCALE_FACTOR mOffsetScaleFactor = CALIBRATION_SCALE_FACTOR.NONE;
	public CALIBRATION_SCALE_FACTOR mSensitivityScaleFactor = CALIBRATION_SCALE_FACTOR.NONE;
	public CALIBRATION_SCALE_FACTOR mAlignmentScaleFactor = CALIBRATION_SCALE_FACTOR.ONE_HUNDRED;
	
	public CHANNEL_DATA_TYPE mAlignmentDataType = CHANNEL_DATA_TYPE.INT8; 
	public double mAlignmentMax = 0.0;
	public double mAlignmentMin = 0.0;
	public int mAlignmentPrecision = 2;
	
	public CHANNEL_DATA_TYPE mSensitivityDataType = CHANNEL_DATA_TYPE.INT16; 
	public double mSensitivityMax = 0.0;
	public double mSensitivityMin = 0.0;  
	public int mSensitivityPrecision = 0;
	
	public CHANNEL_DATA_TYPE mOffsetDataType = CHANNEL_DATA_TYPE.INT16; 
	public double mOffsetMax = 0.0;
	public double mOffsetMin = 0.0;  
	public int mOffsetPrecision = 0;
	
	public byte[] mCalRawParamsFromShimmer  = new byte[21];

	public CalibDetailsKinematic(int rangeValue, String rangeString) {
		this.mRangeValue = rangeValue;
		this.mRangeString = rangeString;
		
		updateOffsetMaxMin();
		updateSensitivityMaxMin();
		updateAlignmentMaxMin();
	}

	public CalibDetailsKinematic(int rangeValue, String rangeString, double[][] defaultAlignmentMatrix, double[][] defaultSensitivityMatrix, double[][] defaultOffsetVector) {
		this(rangeValue, rangeString);
		this.setDefaultValues(defaultOffsetVector, defaultSensitivityMatrix, defaultAlignmentMatrix);
	}

	public CalibDetailsKinematic(int rangeValue, String rangeString, 
			double[][] defaultAlignmentMatrix, double[][] defaultSensitivityMatrix, double[][] defaultOffsetVector,
			double[][] currentAlignmentMatrix, double[][] currentSensitivityMatrix, double[][] currentOffsetVector) {
		this(rangeValue, rangeString, defaultAlignmentMatrix, defaultSensitivityMatrix, defaultOffsetVector);
		this.setCurrentValues(currentOffsetVector, currentSensitivityMatrix, currentAlignmentMatrix, true);
	}

	public CalibDetailsKinematic(int rangeValue, String rangeString, 
			double[][] defaultAlignmentMatrix, double[][] defaultSensitivityMatrix, double[][] defaultOffsetVector,
			CALIBRATION_SCALE_FACTOR sensitivityScaleFactor) {
		this(rangeValue, rangeString, defaultAlignmentMatrix, defaultSensitivityMatrix, defaultOffsetVector);
		setSensitivityScaleFactor(sensitivityScaleFactor);
	}
	
	// Added for setCurrentValues
	public CalibDetailsKinematic(int rangeValue, String rangeString, 
			double[][] defaultAlignmentMatrix, double[][] defaultSensitivityMatrix, double[][] defaultOffsetVector,
			double[][] currentAlignmentMatrix, double[][] currentSensitivityMatrix, double[][] currentOffsetVector,
			CALIBRATION_SCALE_FACTOR sensitivityScaleFactor) {
		this(rangeValue, rangeString, defaultAlignmentMatrix, defaultSensitivityMatrix, defaultOffsetVector);
		this.setCurrentValues(currentOffsetVector, currentSensitivityMatrix, currentAlignmentMatrix, true);
		setSensitivityScaleFactor(sensitivityScaleFactor);
	}

	
	/**NOT CURRENTLY USED and could cause problems unless used for a specific purpose as the defaults param etc. will not be set
	 * @param bufferCalibrationParameters
	 */
	public CalibDetailsKinematic(byte[] bufferCalibrationParameters) {
		parseCalParamByteArray(bufferCalibrationParameters, CALIB_READ_SOURCE.UNKNOWN);
	}


	public void setCurrentValues(double[][] currentOffsetVector, double[][] currentSensitivityMatrix, double[][] currentAlignmentMatrix, boolean nudgeValues) {
		setCurrentAlignmentMatrix(currentAlignmentMatrix, nudgeValues);
		setCurrentSensitivityMatrix(currentSensitivityMatrix, nudgeValues);
		setCurrentOffsetVector(currentOffsetVector, nudgeValues);
		
//		System.out.println(generateDebugString());
	}

	public void setDefaultValues(double[][] defaultOffsetVector, double[][] defaultSensitivityMatrix, double[][] defaultAlignmentMatrix) {
		mDefaultCalibration.setValues(defaultOffsetVector, defaultSensitivityMatrix, defaultAlignmentMatrix);
	}

	@Override
	public void resetToDefaultParameters(){
		super.resetToDefaultParametersCommon();
		
		setCurrentOffsetVector(UtilShimmer.deepCopyDoubleMatrix(mDefaultCalibration.mOffsetVector), true);
		setCurrentSensitivityMatrix(UtilShimmer.deepCopyDoubleMatrix(mDefaultCalibration.mSensitivityMatrix), true);
		setCurrentAlignmentMatrix(UtilShimmer.deepCopyDoubleMatrix(mDefaultCalibration.mAlignmentMatrix), true);
	}
	
	
	public boolean isCurrentValuesSet(){
		return mCurrentCalibration.isCurrentValuesSet();
	}

	public boolean isAllCalibrationValid(){
		if((isSensitivityWithinRangeOfDefault() && mCurrentCalibration.isAllCalibrationValid()) || isUsingDefaultParameters()){
			return true;
		}
		return false;
	}
	
	/**
	 * Method to perform a basic sanity check on the sensitivity of the IMUs, implemented
	 * as there were instances when the sensitivity of the gyroscope was -0.01 or 0.65 for example
	 * @return boolean: true if the sensitivity is outside of the percentage range of the default sensitivity 
	 * where the percentage is defined by validScaling factor
	 */
//	public boolean isSensitivityWithinRangeOfDefault(){
//		boolean isValid = true; 
//		int validScalingFactor = 5;
//		
//		double[][] defaultSensitivityMatrix = getDefaultSensitivityMatrix();
//		double[][] currentSensitivityMatrix = getCurrentSensitivityMatrix();
//		
//		for (int i = 0; i<defaultSensitivityMatrix[0].length; i++){
//		     for (int j = 0; j<defaultSensitivityMatrix.length; j++){
//		    	 double defaultValue = defaultSensitivityMatrix[i][j];
//		    	 double currentValue = currentSensitivityMatrix[i][j];
//		    	 
//		    	 double minValidValue = defaultValue / validScalingFactor;
//		    	 double maxValidValue = defaultValue * validScalingFactor;
//		    	 
//		    	 if(currentValue != 0){
//			    	 if(currentValue >= maxValidValue || currentValue <= minValidValue){
//			    		 isValid = false;
//			    		 break;
//			    	 }
//		    	 }
//		     }
//		}
//		
//		return isValid;
//	}
	
	/**
	 * Method to perform a basic sanity check on the sensitivity of the IMUs, implemented
	 * as there were instances when the sensitivity of the gyroscope was -0.01 or 0.65 for example
	 * @return boolean: true if the sensitivity is outside of the percentage range of the default sensitivity 
	 * where the percentage is defined by validScaling factor
	 */
	public boolean isSensitivityWithinRangeOfDefault(){
		boolean isValid = true; 
		double validScalingFactor = 0.75; // +-75% = Aribitrary value
		
		double[][] defaultSensitivityMatrix = getDefaultSensitivityMatrix();
		double[][] currentSensitivityMatrix = getValidSensitivityMatrix();
		
		for (int i = 0; i<defaultSensitivityMatrix.length; i++){
		     for (int j = 0; j<defaultSensitivityMatrix[i].length; j++){
		    	 double defaultValue = defaultSensitivityMatrix[i][j];
		    	 double currentValue = currentSensitivityMatrix[i][j];
		    	 
		    	 double minValidValue = defaultValue * (1 - validScalingFactor);
		    	 double maxValidValue = defaultValue * (1 + validScalingFactor);
		    	 
		    	 if(currentValue != 0){
			    	 if(currentValue >= maxValidValue || currentValue <= minValidValue){
			    		 isValid = false;
			    		 break;
			    	 }
		    	 }
		     }
		}
		isValid = true; //HACK for CON-645 -> Do not merge to Dev/Master
		return isValid;
	}
	
	public boolean isUsingDefaultParameters(){
		if(isAlignmentUsingDefault() && isSensitivityUsingDefault() && isOffsetVectorUsingDefault()){
			return true;
		}
		return false;
	}
	
	public boolean isAlignmentUsingDefault(){
		return areCalibArraysEqual(mCurrentCalibration.mAlignmentMatrix, mDefaultCalibration.mAlignmentMatrix);
	}

	public boolean isSensitivityUsingDefault(){
		return areCalibArraysEqual(mCurrentCalibration.mSensitivityMatrix, mDefaultCalibration.mSensitivityMatrix);
	}

	public boolean isOffsetVectorUsingDefault(){
		return areCalibArraysEqual(mCurrentCalibration.mOffsetVector, mDefaultCalibration.mOffsetVector);
	}

	@Override
	public void parseCalParamByteArray(byte[] bufferCalibrationParameters, CALIB_READ_SOURCE calibReadSource){
		if(calibReadSource.ordinal()>=getCalibReadSource().ordinal()){
			if(UtilShimmer.isAllFF(bufferCalibrationParameters)
					||UtilShimmer.isAllZeros(bufferCalibrationParameters)){
				return;
			}
			
			setCalibReadSource(calibReadSource);

			mCalRawParamsFromShimmer = bufferCalibrationParameters;

			String[] dataType={"i16","i16","i16","i16","i16","i16","i8","i8","i8","i8","i8","i8","i8","i8","i8"};
			int[] formattedPacket = UtilParseData.formatDataPacketReverse(bufferCalibrationParameters,dataType);
			double[] AM=new double[9];
			for (int i=0;i<9;i++) {
				AM[i]=((double)formattedPacket[6+i])/mAlignmentScaleFactor.scaleFactor;
			}
			double[][] alignmentMatrix = {{AM[0],AM[1],AM[2]},{AM[3],AM[4],AM[5]},{AM[6],AM[7],AM[8]}}; 				
			double[][] sensitivityMatrix = {{formattedPacket[3],0,0},{0,formattedPacket[4],0},{0,0,formattedPacket[5]}}; 
			double[][] offsetVector = {{formattedPacket[0]},{formattedPacket[1]},{formattedPacket[2]}};
			
			for(int i=0;i<=2;i++){
				sensitivityMatrix[i][i] = sensitivityMatrix[i][i]/mSensitivityScaleFactor.scaleFactor;
			}
			
			setCurrentAlignmentMatrix(alignmentMatrix, true);
			setCurrentSensitivityMatrix(sensitivityMatrix, true); 	
			setCurrentOffsetVector(offsetVector, true);
		}
	}

	@Override
	public byte[] generateCalParamByteArray() {
		if(isCurrentValuesSet()){
			return generateCalParamByteArray(mCurrentCalibration.mOffsetVector, mCurrentCalibration.mSensitivityMatrix, mCurrentCalibration.mAlignmentMatrix);
		}
		else{
			return generateCalParamByteArray(mDefaultCalibration.mOffsetVector, mDefaultCalibration.mSensitivityMatrix, mDefaultCalibration.mAlignmentMatrix);
		}
	}
	
	public byte[] generateCalParamByteArray(double[][] offsetVector, double[][] sensitivityMatrix, double[][] alignmentMatrix) {
		
		//Scale the sensitivity if needed
		double[][] sensitivityMatrixToUse = UtilShimmer.deepCopyDoubleMatrix(sensitivityMatrix);
		for(int i=0;i<=2;i++){
			sensitivityMatrixToUse[i][i] = Math.round(sensitivityMatrixToUse[i][i]*mSensitivityScaleFactor.scaleFactor);
		}

		//Scale the alignment by 100
		double[][] alignmentMatrixToUse = UtilShimmer.deepCopyDoubleMatrix(alignmentMatrix);
		for(int i=0;i<=2;i++){
			alignmentMatrixToUse[i][0] = Math.round(alignmentMatrixToUse[i][0]*mAlignmentScaleFactor.scaleFactor);
			alignmentMatrixToUse[i][1] = Math.round(alignmentMatrixToUse[i][1]*mAlignmentScaleFactor.scaleFactor);
			alignmentMatrixToUse[i][2] = Math.round(alignmentMatrixToUse[i][2]*mAlignmentScaleFactor.scaleFactor);
		}
		
		//Generate the calibration bytes
		byte[] bufferCalibParam = new byte[21];
		// offsetVector -> buffer offset = 0
		for (int i=0; i<3; i++) {
			bufferCalibParam[0+(i*2)] = (byte) ((((int)offsetVector[i][0]) >> 8) & 0xFF);
			bufferCalibParam[0+(i*2)+1] = (byte) ((((int)offsetVector[i][0]) >> 0) & 0xFF);
		}
		// sensitivityMatrix -> buffer offset = 6
		for (int i=0; i<3; i++) {
			bufferCalibParam[6+(i*2)] = (byte) ((((int)sensitivityMatrixToUse[i][i]) >> 8) & 0xFF);
			bufferCalibParam[6+(i*2)+1] = (byte) ((((int)sensitivityMatrixToUse[i][i]) >> 0) & 0xFF);
		}
		// alignmentMatrix -> buffer offset = 12
		for (int i=0; i<3; i++) {
			bufferCalibParam[12+(i*3)] = (byte) (((int)(alignmentMatrixToUse[i][0])) & 0xFF);
			bufferCalibParam[12+(i*3)+1] = (byte) (((int)(alignmentMatrixToUse[i][1])) & 0xFF);
			bufferCalibParam[12+(i*3)+2] = (byte) (((int)(alignmentMatrixToUse[i][2])) & 0xFF);
		}
		return bufferCalibParam;
	}	
	
	public double[][] getValidAlignmentMatrix() {
		double[][] currentAlignmentMatrix = getCurrentAlignmentMatrix();
		return (currentAlignmentMatrix!=null? currentAlignmentMatrix:getDefaultAlignmentMatrix());
	}

	private double[][] getCurrentAlignmentMatrix() {
		if(mCurrentCalibration!=null && mCurrentCalibration.mAlignmentMatrix!=null)
			return mCurrentCalibration.mAlignmentMatrix;
		else
			return null;
	}

	public double[][] getValidSensitivityMatrix() {
		double[][] currentSensitivityMatrix = getCurrentSensitivityMatrix();
		return (currentSensitivityMatrix!=null? currentSensitivityMatrix:getDefaultSensitivityMatrix());
	}

	public double[][] getCurrentSensitivityMatrix() {
		if(mCurrentCalibration!=null && mCurrentCalibration.mSensitivityMatrix!=null)
			return mCurrentCalibration.mSensitivityMatrix;
		else
			return null;
	}

	public double[][] getValidOffsetVector() {
		double[][] currentOffsetVector = getCurrentOffsetVector();
		return (currentOffsetVector!=null? currentOffsetVector:getDefaultOffsetVector());
	}
	
	public double[][] getCurrentOffsetVector() {
		if(mCurrentCalibration!=null && mCurrentCalibration.mOffsetVector!=null)
			return mCurrentCalibration.mOffsetVector;
		else
			return null;
	}

	public double[][] getEmptyOffsetVector() {
		if(mEmptyCalibration==null){
			return null;
		}
		return mEmptyCalibration.mOffsetVector;
	}

	public double[][] getEmptySensitivityMatrix() {
		if(mEmptyCalibration==null){
			return null;
		}
		return mEmptyCalibration.mSensitivityMatrix;
	}

	public double[][] getEmptyAlignmentMatrix() {
		if(mEmptyCalibration==null){
			return null;
		}
		return mEmptyCalibration.mAlignmentMatrix;
	}

	
	public double[][] getDefaultOffsetVector() {
		if(mDefaultCalibration==null){
			return null;
		}
		return mDefaultCalibration.mOffsetVector;
	}

	public double[][] getDefaultSensitivityMatrix() {
		if(mDefaultCalibration==null){
			return null;
		}
		return mDefaultCalibration.mSensitivityMatrix;
	}

	public double[][] getDefaultAlignmentMatrix() {
		if(mDefaultCalibration==null){
			return null;
		}
		return mDefaultCalibration.mAlignmentMatrix;
	}

	
	
	public double[][] getCurrentMatrixMultipliedInverseAMSM() {
		if(mCurrentCalibration!=null && mCurrentCalibration.mMatrixMultipliedInverseAMSM!=null)
			return mCurrentCalibration.mMatrixMultipliedInverseAMSM;
		else
			return null;
	}

	public double[][] getDefaultMatrixMultipliedInverseAMSM() {
		if(mDefaultCalibration==null){
			return null;
		}
		return mDefaultCalibration.mMatrixMultipliedInverseAMSM;
	}
	
	public double[][] getValidMatrixMultipliedInverseAMSM() {
		double[][] currentMatrixMultipliedInverseAMSM = getCurrentMatrixMultipliedInverseAMSM();
		return (currentMatrixMultipliedInverseAMSM!=null? currentMatrixMultipliedInverseAMSM:getDefaultMatrixMultipliedInverseAMSM());
	}

	
	/** For example used by Gyro on the fly calibration
	 * @param mean
	 * @param mean2
	 * @param mean3
	 */
	//TODO no nudging implemented here
	public void updateCurrentOffsetVector(double XXvalue, double YYvalue, double ZZvalue) {
		mCurrentCalibration.updateOffsetVector(XXvalue, YYvalue, ZZvalue);
	}
	
	private void setCurrentOffsetVector(double[][] newArray, boolean nudgeValues) {
		if(nudgeValues) {
			mCurrentCalibration.setOffsetVector(UtilShimmer.nudgeDoubleArray(mOffsetMax, mOffsetMin, mOffsetPrecision, newArray));
		} else {
			mCurrentCalibration.setOffsetVector(newArray);
		}
	}

	//TODO no nudging implemented here
	public void updateCurrentSensitivityMatrix(double XXvalue, double YYvalue, double ZZvalue) {
		mCurrentCalibration.updateSensitivityMatrix(XXvalue, YYvalue, ZZvalue);
	}

	public void setCurrentSensitivityMatrix(double[][] newArray, boolean nudgeValues) {
		if(nudgeValues) {
			mCurrentCalibration.setSensitivityMatrix(UtilShimmer.nudgeDoubleArray(mSensitivityMax, mSensitivityMin, mSensitivityPrecision, newArray));
		} else {
			mCurrentCalibration.setSensitivityMatrix(newArray);
		}
	}

	//TODO no nudging implemented here
	public void updateCurrentAlignmentMatrix(
			double XXvalue, double XYvalue, double XZvalue, 
			double YXvalue, double YYvalue, double YZvalue,
			double ZXvalue, double ZYvalue, double ZZvalue) {
		mCurrentCalibration.updateAlignmentMatrix(XXvalue, XYvalue, XZvalue, YXvalue, YYvalue, YZvalue, ZXvalue, ZYvalue, ZZvalue);
	}

	public void setCurrentAlignmentMatrix(double[][] newArray, boolean nudgeValues) {
		if(nudgeValues) {
			mCurrentCalibration.setAlignmentMatrix(UtilShimmer.nudgeDoubleArray(mAlignmentMax, mAlignmentMin, mAlignmentPrecision, newArray));
		} else {
			mCurrentCalibration.setAlignmentMatrix(newArray);
		}
	}

	public void setSensitivityScaleFactor(CALIBRATION_SCALE_FACTOR scaleFactor) {
		mSensitivityScaleFactor = scaleFactor;
		setSensitivityPrecision(calculatePrecision(mSensitivityScaleFactor.scaleFactor));
	}

	private void setSensitivityPrecision(int precision) {
		mSensitivityPrecision = precision;
		updateSensitivityMaxMin();
	}

	public void setAlignmentScaleFactor(CALIBRATION_SCALE_FACTOR scaleFactor) {
		mAlignmentScaleFactor = scaleFactor;
		setAlignmentPrecision(calculatePrecision(mAlignmentScaleFactor.scaleFactor));
	}

	private void setAlignmentPrecision(int precision) {
		mAlignmentPrecision = precision;
		updateAlignmentMaxMin();
	}

	public void setOffsetScaleFactor(CALIBRATION_SCALE_FACTOR scaleFactor) {
		mOffsetScaleFactor = scaleFactor;
		setOffsetPrecision(calculatePrecision(mOffsetScaleFactor.scaleFactor));
	}

	public void setOffsetPrecision(int precision) {
		mOffsetPrecision = precision;
		updateOffsetMaxMin();
	}

	public void updateOffsetMaxMin() {
		mOffsetMax = mOffsetDataType.getMaxVal()/mOffsetScaleFactor.scaleFactor;
		mOffsetMax = UtilShimmer.applyPrecisionCorrection(mOffsetMax, mOffsetPrecision);
		
		mOffsetMin = mOffsetDataType.getMinVal()/mOffsetScaleFactor.scaleFactor;
		mOffsetMin = UtilShimmer.applyPrecisionCorrection(mOffsetMin, mOffsetPrecision);
	}

	public void updateSensitivityMaxMin() {
		mSensitivityMax = mSensitivityDataType.getMaxVal()/mSensitivityScaleFactor.scaleFactor;
		mSensitivityMax = UtilShimmer.applyPrecisionCorrection(mSensitivityMax, mSensitivityPrecision);
		
		mSensitivityMin = mSensitivityDataType.getMinVal()/mSensitivityScaleFactor.scaleFactor;
		mSensitivityMin = UtilShimmer.applyPrecisionCorrection(mSensitivityMin, mSensitivityPrecision);
	}
	
	public double getSensitivityScaleFactor() {
		return mSensitivityScaleFactor.scaleFactor;
	}

	public void updateAlignmentMaxMin() {
		mAlignmentMax = mAlignmentDataType.getMaxVal()/mAlignmentScaleFactor.scaleFactor;
		mAlignmentMax = UtilShimmer.applyPrecisionCorrection(mAlignmentMax, mAlignmentPrecision);
		
		mAlignmentMin = mAlignmentDataType.getMinVal()/mAlignmentScaleFactor.scaleFactor;
		mAlignmentMin = UtilShimmer.applyPrecisionCorrection(mAlignmentMin, mAlignmentPrecision);
	}
	
	public void updateCurrentCalibration(CalibArraysKinematic calibArraysKinematic) {
		setCurrentOffsetVector(calibArraysKinematic.mOffsetVector, true);
		setCurrentAlignmentMatrix(calibArraysKinematic.mAlignmentMatrix, true);
		setCurrentSensitivityMatrix(calibArraysKinematic.mSensitivityMatrix, true);
		setCalibTimeMs(calibArraysKinematic.getCalibTime());
	}
	
	public String getDebugString() {
		String debugString = "RangeString:" + mRangeString + "\t" + "RangeValue:" + mRangeValue + "\n";
		debugString += generateDebugStringPerProperty("Default Offset Vector", getDefaultOffsetVector());
		debugString += generateDebugStringPerProperty("Current Offset Vector", getValidOffsetVector());//mCurrentOffsetVector);
		debugString += generateDebugStringPerProperty("Default Sensitivity", getDefaultSensitivityMatrix());
		debugString += generateDebugStringPerProperty("CurrentSensitivity", getValidSensitivityMatrix());//mCurrentSensitivityMatrix);
		debugString += generateDebugStringPerProperty("Default Alignment", getDefaultAlignmentMatrix());
		debugString += generateDebugStringPerProperty("Current Alignment", getValidAlignmentMatrix());//mCurrentAlignmentMatrix);
		return debugString;
	}

	public static String generateDebugStringPerProperty(String property, double[][] calMatrix) {
		String debugString = property + " =\n";
		if(calMatrix==null){
			debugString += "NULL\n";
		}
		else{
			debugString += UtilShimmer.doubleArrayToString(calMatrix);
		}
		return debugString;
	}

	public static boolean areCalibArraysEqual(double[][] array1, double[][] array2) {
		if(array1==null || array2==null){
			return false;
		}
		return Arrays.deepEquals(array1, array2);
	}
	
	public static int calculatePrecision(double scaleFactor) {
		int length = (int)(Math.log10(scaleFactor)+1);
		return length-1;
	}

	public void parseCalibDetailsKinematicFromDb(
			LinkedHashMap<String, Object> mapOfConfigPerShimmer,
			String calibTimeHandle,
			String offsetX, String offsetY, String offsetZ, 
			String gainX, String gainY, String gainZ, 
			String alignXx, String alignXy, String alignXz, 
			String alignYx, String alignYy, String alignYz, 
			String alignZx, String alignZy, String alignZz) {

		CalibArraysKinematic calibArraysKinematic = CalibDetailsKinematic.parseCalibDetailsKinematicFromDbStatic(mapOfConfigPerShimmer,
				calibTimeHandle,
				offsetX, offsetY, offsetZ, 
				gainX, gainY, gainZ, 
				alignXx, alignXy, alignXz, 
				alignYx, alignYy, alignYz, 
				alignZx, alignZy, alignZz);
		
		if(calibArraysKinematic!=null){
			updateCurrentCalibration(calibArraysKinematic);
		}		
	}

	public static CalibArraysKinematic parseCalibDetailsKinematicFromDbStatic(
			LinkedHashMap<String, Object> mapOfConfigPerShimmer,
			String calibTimeHandle,
			String offsetX, String offsetY, String offsetZ, 
			String gainX, String gainY, String gainZ, 
			String alignXx, String alignXy, String alignXz, 
			String alignYx, String alignYy, String alignYz, 
			String alignZx, String alignZy, String alignZz) {

		if(mapOfConfigPerShimmer.containsKey(offsetX)
				&& mapOfConfigPerShimmer.containsKey(offsetY)
				&& mapOfConfigPerShimmer.containsKey(offsetZ)
				&& mapOfConfigPerShimmer.containsKey(gainX)
				&& mapOfConfigPerShimmer.containsKey(gainY)
				&& mapOfConfigPerShimmer.containsKey(gainZ)
				&& mapOfConfigPerShimmer.containsKey(alignXx)
				&& mapOfConfigPerShimmer.containsKey(alignXy)
				&& mapOfConfigPerShimmer.containsKey(alignXz)
				&& mapOfConfigPerShimmer.containsKey(alignYx)
				&& mapOfConfigPerShimmer.containsKey(alignYy)
				&& mapOfConfigPerShimmer.containsKey(alignYz)
				&& mapOfConfigPerShimmer.containsKey(alignZx)
				&& mapOfConfigPerShimmer.containsKey(alignZy)
				&& mapOfConfigPerShimmer.containsKey(alignZz)){
			CalibArraysKinematic calibArraysKinematic = new CalibArraysKinematic();
			
			Double XXvalue = (Double) mapOfConfigPerShimmer.get(offsetX);
			Double YYvalue = (Double) mapOfConfigPerShimmer.get(offsetY);
			Double ZZvalue = (Double) mapOfConfigPerShimmer.get(offsetZ);
			calibArraysKinematic.updateOffsetVector(XXvalue, YYvalue, ZZvalue);
			
			XXvalue = (Double) mapOfConfigPerShimmer.get(gainX);
			YYvalue = (Double) mapOfConfigPerShimmer.get(gainY);
			ZZvalue = (Double) mapOfConfigPerShimmer.get(gainZ);
			calibArraysKinematic.updateSensitivityMatrix(XXvalue, YYvalue, ZZvalue);
					
			XXvalue = (Double) mapOfConfigPerShimmer.get(alignXx);
			Double XYvalue = (Double) mapOfConfigPerShimmer.get(alignXy);
			Double XZvalue = (Double) mapOfConfigPerShimmer.get(alignXz);
			Double YXvalue = (Double) mapOfConfigPerShimmer.get(alignYx);
			YYvalue = (Double) mapOfConfigPerShimmer.get(alignYy);
			Double YZvalue = (Double) mapOfConfigPerShimmer.get(alignYz);
			Double ZXvalue = (Double) mapOfConfigPerShimmer.get(alignZx);
			Double ZYvalue = (Double) mapOfConfigPerShimmer.get(alignZy);
			ZZvalue = (Double) mapOfConfigPerShimmer.get(alignZz);
			calibArraysKinematic.updateAlignmentMatrix(XXvalue, XYvalue, XZvalue, YXvalue, YYvalue, YZvalue, ZXvalue, ZYvalue, ZZvalue);
			
			if(!calibTimeHandle.isEmpty() && mapOfConfigPerShimmer.containsKey(calibTimeHandle)){
				double calibTime = (double) mapOfConfigPerShimmer.get(calibTimeHandle);
				calibArraysKinematic.setCalibTime((long)calibTime);
			}

			return calibArraysKinematic;
		}

		return null;
	}

}
