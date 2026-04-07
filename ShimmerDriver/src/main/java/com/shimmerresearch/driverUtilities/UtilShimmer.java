package com.shimmerresearch.driverUtilities;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;

import com.shimmerresearch.driver.ObjectCluster;
import com.shimmerresearch.driver.ShimmerDevice;
import com.shimmerresearch.driverUtilities.ChannelDetails.CHANNEL_TYPE;
import com.shimmerresearch.exceptions.ShimmerException;

/** Utility class with commonly useful methods
 * 
 * @author Mark Nolan
 *
 */
public class UtilShimmer implements Serializable {
	
	/** * */
	private static final long serialVersionUID = -3892204042703820796L;
	
	public String mParentClassName = "UpdateCheck";
	public Boolean mDebugMode = true;
	public Boolean mVerboseMode = true;
	
	public static final String STRING_CONSTANT_FOR_UNKNOWN = "Unknown";
	public static final String MAC_ADDRESS_ZEROS = "000000000000";
	public static final String MAC_ADDRESS_FFFFS = "FFFFFFFFFFFF";
	
	public static final String UNICODE_PLUS_MINUS = "\u00B1";
	public static final String UNICODE_CHECK_MARK = "\u2713";
	public static final String UNICODE_CROSS_MARK = "\u274C";
	public static final String UNICODE_APPROX_EQUAL = "\u2248";
	public static final String UNICODE_MICRO = "\u00B5";
	public static final String UNICODE_OHMS = "\u2126";
	//TODO move above into below
	public static class UNICODE_CHAR {
		public static final String ARROW_FROM_START = "\u21A6";
		public static final String ARROW_TO_END = "\u21E5";
	}
	
	public static final String CHECK_MARK_STRING = " " + UNICODE_CHECK_MARK;
	public static final String CROSS_MARK_STRING = "  x"; //unicode for cross wasn't working on all PCs " " + UNICODE_CROSS_MARK;

	public static final String STRING_CONSTANT_FOR_BUTTON_EVENT = "EVENT BUTTON PRESSED: ";
	
	// Numeric value so that they`ll work with android
	public static class SHIMMER_DEFAULT_COLOURS{
		// Shimmer Orange
		public static final int[] colourShimmerOrange = new int[]{241, 93, 34};
		public static final int[] colourBrown = new int[]{153, 76, 0};
		public static final int[] colourCyanAqua = new int[]{0, 153, 153};
		public static final int[] colourPurple = new int[]{102, 0, 204};
		public static final int[] colourMaroon = new int[]{102, 0, 0};
		public static final int[] colourGreen = new int[]{0, 153, 76};
		// Shimmer Grey
		public static final int[] colourShimmerGrey = new int[]{119, 120, 124};
		// Shimmer Blue
		public static final int[] colourShimmerBlue = new int[]{0, 129, 198};
		
		public static final int[] colourLightRed = new int[]{255, 0, 0};
	}
	
	//TODO utilise this enum widely in future
	public enum ENUM_FILE_DELIMITERS{
		TAB("\t", "tab (\\t)"),
		COMMA(",", "Comma (,)"),
		SEMI_COLON(";", "Semicolon (;)");
		
		public String delimiter;
		public String guiFriendlyName;
		
		ENUM_FILE_DELIMITERS(String delimiter, String guiFriendlyName){
			this.delimiter = delimiter;
			this.guiFriendlyName = guiFriendlyName;
		}
	}
	
	//TODO utilise this enum widely in future
	public enum ENUM_FILE_FORMAT{
		CSV(".csv"),
		DAT(".dat"),
		TXT(".txt"),
		MAT(".mat");
		
		public String fileExtension;
		
		ENUM_FILE_FORMAT(String fileExtension){
			this.fileExtension = fileExtension;
		}
	}

	public UtilShimmer(String parentClassName, Boolean verboseMode){
		this.mParentClassName = parentClassName;
		this.mVerboseMode = verboseMode;
	}
	
	public UtilShimmer(String parentClassName, boolean verboseMode, boolean debugMode) {
		this(parentClassName, verboseMode);
		this.mDebugMode = debugMode;
	}

	public void consolePrintLn(Object message) {
		if(mVerboseMode) {
			System.out.println(generateConsolePrintHeader() + message);
		}		
	}
	
	public void consolePrintErrLn(Object message) {
//		if(mVerboseMode) {
			System.err.println(generateConsolePrintHeader() + message);
//		}		
	}
	
	public void consolePrintExeptionLn(String message, StackTraceElement[] stackTrace) {
		if(mVerboseMode) {
			consolePrintErrLn(message + "\n" + UtilShimmer.convertStackTraceToString(stackTrace));
		}
	}

	public void consolePrintShimmerException(ShimmerException shimmerException) {
		if(mVerboseMode) {
			consolePrintErrLn(shimmerException.getErrStringFormatted());
		}
	}

	private String generateConsolePrintHeader() {
		Calendar rightNow = Calendar.getInstance();
		
		//Negligable difference here between StringBuilder and manually creating the String
		String rightNowString = "[" + String.format("%02d",rightNow.get(Calendar.HOUR_OF_DAY)) 
				+ ":" + String.format("%02d",rightNow.get(Calendar.MINUTE)) 
				+ ":" + String.format("%02d",rightNow.get(Calendar.SECOND)) 
				+ ":" + String.format("%03d",rightNow.get(Calendar.MILLISECOND)) + "]";
		return(rightNowString + " " + mParentClassName + ": ");
		
//		StringBuilder builder = new StringBuilder();
//		builder.append("[");
//		builder.append(String.format("%02d",rightNow.get(Calendar.HOUR_OF_DAY)));
//		builder.append(":");
//		builder.append(String.format("%02d",rightNow.get(Calendar.MINUTE)));
//		builder.append(":");
//		builder.append(String.format("%02d",rightNow.get(Calendar.SECOND)));
//		builder.append(":");
//		builder.append(String.format("%03d",rightNow.get(Calendar.MILLISECOND)));
//		builder.append("]");
//
//		builder.append(mParentClassName);
//		builder.append(message);
//		
//		return builder.toString();
	}
	
	public void setParentClassName(String parentClassName){
		mParentClassName = parentClassName;
	}

	public void consolePrint(String message) {
		if(mVerboseMode) {
			System.out.print(message);
		}		
	}

	public void consolePrintLnDebug(String message) {
		if(mDebugMode){
			consolePrintLn(message);
		}
	}

	public void setVerboseMode(boolean verboseMode) {
		mVerboseMode = verboseMode;
	}

	public static String convertSecondsToDateString(long seconds) {
		return convertMilliSecondsToDateString(seconds * 1000, false);
	}
	
	public static String convertMilliSecondsToDateString(long milliSeconds, boolean showMillis) {
		Calendar cal = Calendar.getInstance();
		
		cal.setTimeInMillis(milliSeconds);
		int dayIndex = cal.get(Calendar.DAY_OF_MONTH);
		String dayString = getDayOfMonthSuffix(dayIndex);

		int monthIndex = cal.get(Calendar.MONTH);
		String monthString = getMonthString(monthIndex);
		
		String simpleDateFormat = showMillis ? "//yyyy HH:mm:ss.SSS" : "//yyyy HH:mm:ss";
		
    	DateFormat dfLocal = new SimpleDateFormat(simpleDateFormat);
    	String timeString = dfLocal.format(new Date(milliSeconds));
    	timeString = timeString.replaceFirst("//", dayIndex + dayString + " " + monthString + " ");
		return timeString;
	}
	
	public static String convertMilliSecondsToHrMinSecUTC(long milliSeconds) {
		return convertMilliSecondsToUTC(milliSeconds, "HH:mm:ss");
	}
	
	public static String convertMilliSecondsToHrMinSecMilliSecUTC(long milliSeconds) {
		return convertMilliSecondsToUTC(milliSeconds, "HH:mm:ss.SSS");
	}
	
	private static String convertMilliSecondsToUTC(long milliSeconds, String simpleDateFormat) {
		DateFormat dfLocal = new SimpleDateFormat(simpleDateFormat);
		dfLocal.setTimeZone(TimeZone.getTimeZone("UTC"));
		String timeString = dfLocal.format(new Date(milliSeconds));
		
		return timeString;
	}
	
	/**
	 * Converts from milliseconds in Unix time to a formatted local time string
	 * (specific to the local timezone of the computer)
	 * 
	 * @param milliSeconds
	 * @param format
	 * @return
	 */
	public static String convertMilliSecondsToHrMinSecLocal(long milliSeconds) {
		return convertMilliSecondsToFormat(milliSeconds, "HH:mm:ss", false);
	}

	/**
	 * Converts from milliseconds in Unix time to a formatted local time string
	 * (specific to the local timezone of the computer)
	 * 
	 * @param milliSeconds
	 * @param format
	 * @return
	 */
	public static String convertMilliSecondsToHrMinSecMilliSecLocal(long milliSeconds) {
		return convertMilliSecondsToFormat(milliSeconds, "HH:mm:ss.SSS", false);
	}
	
	/**
	 * Converts from milliseconds in Unix time 
	 * 
	 * @param milliSeconds
	 * @param format
	 * @return
	 */
	public static String convertMilliSecondsToFormat(long milliSeconds, String format, boolean setTimezoneUtc) {
		DateFormat dfLocal = new SimpleDateFormat(format);
		if(setTimezoneUtc){
			dfLocal.setTimeZone(TimeZone.getTimeZone("UTC"));
		}
		String timeString = dfLocal.format(new Date(milliSeconds));
		
		if(milliSeconds == 0){
			return "00:00:00";
		}
		else{
			return timeString;
		}
		
	}

	private static String getDayOfMonthSuffix(final int n) {
	    if (n >= 11 && n <= 13) {
	        return "th";
	    }
	    switch (n % 10) {
	        case 1:  return "st";
	        case 2:  return "nd";
	        case 3:  return "rd";
	        default: return "th";
	    }
	}
	
	
	public static String convertBytesToReadableString(double bytes){
		
		bytes = (double)((bytes) / 1024 / 1024 / 1024);
	    
	    String mFormattedBytesTxt = " GB";
	    
    	if(bytes < 0.001){
    		bytes = bytes * 1024 * 1024;
    		mFormattedBytesTxt = " KB";
    	}	
	    
    	else if (bytes < 1.0) {
	    	bytes = bytes * 1024;
	    	mFormattedBytesTxt = " MB";
	    }
	    mFormattedBytesTxt = String.format("%.2f", bytes) + mFormattedBytesTxt;
	    return mFormattedBytesTxt;
	    
	}
	
	public static boolean isNumeric(String str){
		if(str==null) {
			return false;
		}
		if(str.isEmpty()) {
			return false;
		}
		
	    for (char c : str.toCharArray()){
	        if (!Character.isDigit(c)) return false;
	    }
	    return true;
	}
	
	
	public static boolean isAsciiPrintable(char ch) {
	      return ch >= 32 && ch < 127;
	}

	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHexString(byte[] bytes) {
		if(bytes!=null){
		    char[] hexChars = new char[bytes.length * 2];
		    for ( int j = 0; j < bytes.length; j++ ) {
		        int v = bytes[j] & 0xFF;
		        hexChars[j * 2] = hexArray[v >>> 4];
		        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		    }
		    return new String(hexChars);
		}
		else {
			return null;
		}
	}
	
	public static String byteToHexString(byte bytes) {
	    char hexChars[] = new char[2];
        int v = bytes & 0xFF;
        hexChars[0] = hexArray[v >>> 4];
        hexChars[1] = hexArray[v & 0x0F];
	    return new String(hexChars);
	}
	
	public static String bytesToHexStringWithSpaces(byte[] bytes) {
		if(bytes!=null){
		    char[] hexChars = new char[bytes.length * 3];
		    for ( int j = 0; j < bytes.length; j++ ) {
		        int v = bytes[j] & 0xFF;
		        hexChars[j * 3] = hexArray[v >>> 4];
		        hexChars[j * 3 + 1] = hexArray[v & 0x0F];
		        hexChars[j * 3 + 2] = ' ';
		    }
		    return new String(hexChars);
		}
		else {
			return null;
		}
	}
	
	public static String byteToHexStringFormatted(byte bytes) {
		int charCntPerByte = 4;
	    char hexChars[] = new char[charCntPerByte];
        int v = bytes & 0xFF;
        hexChars[0] = '0';
        hexChars[1] = 'x';
        hexChars[2] = hexArray[v >>> 4];
        hexChars[3] = hexArray[v & 0x0F];
	    String returnString = new String(hexChars);
	    returnString = "[" + returnString + "]";
	    return returnString;
	}
	
	public static String intToHexStringFormatted(int intValue, int numBytes, boolean isMsbOrder) {
		byte[] bytes = null;
		if(isMsbOrder) {
			bytes = intToByteArrayMsb(intValue, numBytes);
		} else {
			bytes = intToByteArrayLsb(intValue, numBytes);
		}
		int charCntPerByte = 2+(numBytes*2);
	    char hexChars[] = new char[charCntPerByte];
        hexChars[0] = '0';
        hexChars[1] = 'x';
        int i = 2;
        for(byte v:bytes) {
            hexChars[i] = hexArray[(v&0xFF) >>> 4];
            hexChars[i+1] = hexArray[(v&0xFF) & 0x0F];
            i+=2;
        }
	    String returnString = new String(hexChars);
//	    returnString = "[" + returnString + "]";
	    return returnString;
	}
	

	public static byte[] intToByteArrayLsb(int intToUse, int numBytes) {
		byte[] buffer = new byte[numBytes]; 
		for(int i=0;i<numBytes;i++) {
			buffer[i] = (byte) (intToUse >> (i*8));
		}
	    return buffer;
	}

	public static byte[] intToByteArrayMsb(int intToUse, int numBytes) {
		byte[] buffer = new byte[numBytes]; 
		for(int i=0;i<numBytes;i++) {
			buffer[i] = (byte) (intToUse >> ((numBytes-1-i)*8));
		}
	    return buffer;
	}

	public static String bytesToHexStringWithSpacesFormatted(byte[] bytes) {
		if(bytes!=null && bytes.length>0){
			int charCntPerByte = 5;
		    char[] hexChars = new char[(bytes.length * charCntPerByte)-1];
		    for ( int j = 0; j < bytes.length; j++ ) {
		        int v = bytes[j] & 0xFF;
		        hexChars[j * charCntPerByte] = '0';
		        hexChars[j * charCntPerByte + 1] = 'x';

		        hexChars[j * charCntPerByte + 2] = hexArray[v >>> 4];
		        hexChars[j * charCntPerByte + 3] = hexArray[v & 0x0F];
		        if(j!=bytes.length-1){
			        hexChars[j * charCntPerByte + 4] = ' ';
		        }
		    }
		    String returnString = new String(hexChars);
		    returnString = "[" + returnString + "]";
		    return returnString;
		}
		else {
			return null;
		}
	}	
	
	public static byte[] hexStringToByteArray(String s) {
	    int len = s.length();
	    byte[] data = new byte[1];
	    if(s.toCharArray().length == 0){
	    	data[0] = 0;
	    }
	    else if(s.toCharArray().length == 1){
	    	data[0] = (byte) Character.digit(s.charAt(0), 16); 
	    }
	    else {
	    	data = new byte[len / 2];
		    for (int i = 0; i < len; i += 2) {
		        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
		                             + Character.digit(s.charAt(i+1), 16));
		    }
	    }
	    return data;
	}

	public static int hexStringToInt(String s){
		int len = s.length();
		int data = 0;
		int radix = 16;

		if(len == 0){
			data = 0;	
		}
		else if(len <= 8){	

			//max value fitting in int: "7FFFFFFF" (= 2^31-1)
			if(len == 8 && Integer.parseInt(s.substring(0,1), radix) > 7){
				data = 0;	
			}
			else{
				data = Integer.parseInt(s, radix);
			}
		}
		return data;

	}
	
	public static String convertByteToUnsignedIntegerString(byte b) {
		return Integer.toString(b&0x00FF);
	}

	
	//TODO move to ByteUtils
	public static byte[] convertLongToByteArray(long longNumber){
		byte[] returnVal = ByteBuffer.allocate(8).putLong(longNumber).array();

//		byte[] returnVal = new byte[8];
//		returnVal = ByteBuffer.allocate(8).putLong(longNumber).array();
		
		return returnVal;
	}

	//TODO move to ByteUtils
	public static long convertByteArrayToLong(byte[] byteArray){
		byte[] bSystemTS = byteArray;
		ByteBuffer bb = ByteBuffer.allocate(8);
    	bb.put(bSystemTS);
    	bb.flip();
    	long returnVal = bb.getLong();
		return returnVal;
	}

	
	/**Returns true if FW ID and HW_ID are the same and "this" version is greater or equal then comparison version
	 * @param thisFwIdent
	 * @param thisMajor
	 * @param thisMinor
	 * @param thisInternal
	 * @param compFwIdent
	 * @param compMajor
	 * @param compMinor
	 * @param compInternal
	 * @return
	 */
	public static boolean compareVersions(int thisHwIdent, int thisFwIdent, int thisMajor, int thisMinor, int thisInternal,
			int compHwIdent, int compFwIdent, int compMajor, int compMinor, int compInternal) {

		if(compHwIdent!=ShimmerVerDetails.ANY_VERSION){
			if (thisHwIdent!=compHwIdent){
				return false;
			}
		}
		return compareVersions(thisFwIdent, thisMajor, thisMinor, thisInternal, compFwIdent, compMajor, compMinor, compInternal);

//		if (thisHwIdent==compHwIdent){
//			return compareVersions(thisFwIdent, thisMajor, thisMinor, thisInternal, compFwIdent, compMajor, compMinor, compInternal);
//		}
//		return false; // if less or not the same FW_ID and HW_ID
	}

	public static boolean compareVersions(ShimmerVerObject svo, ShimmerVerObject svoTarget) {
		int thisHwIdent = svo.mHardwareVersion;
		int thisFwIdent = svo.mFirmwareIdentifier;
		int thisMajor = svo.mFirmwareVersionMajor;
		int thisMinor = svo.mFirmwareVersionMinor;
		int thisInternal = svo.mFirmwareVersionInternal;
		int compHwIdent = svoTarget.mHardwareVersion;
		int compFwIdent= svoTarget.mFirmwareIdentifier;
		int compMajor = svoTarget.mFirmwareVersionMajor;
		int compMinor = svoTarget.mFirmwareVersionMinor;
		int compInternal = svoTarget.mFirmwareVersionInternal;
		
		if(compHwIdent!=ShimmerVerDetails.ANY_VERSION){
			if (thisHwIdent!=compHwIdent){
				return false;
			}
		}
		return compareVersions(thisFwIdent, thisMajor, thisMinor, thisInternal, compFwIdent, compMajor, compMinor, compInternal);
	}
	
	
	/**Returns true if FW ID is the same and "this" version is greater or equal then comparison version
	 * @param thisFwIdent
	 * @param thisMajor
	 * @param thisMinor
	 * @param thisInternal
	 * @param compFwIdent
	 * @param compMajor
	 * @param compMinor
	 * @param compInternal
	 * @return
	 */
	public static boolean compareVersions(int thisFwIdent, int thisMajor, int thisMinor, int thisInternal,
			int compFwIdent, int compMajor, int compMinor, int compInternal) {

		// if not the same FW ID, fail
		if(compFwIdent!=ShimmerVerDetails.ANY_VERSION){
			if (thisFwIdent!=compFwIdent){
				return false;
			}
		}
		return compareVersions(thisMajor, thisMinor, thisInternal, compMajor, compMinor, compInternal);
	}
	
	
    /**
     * @param byteArray needs to be larger or same size as the target
     * @param targetValue 
     * @return true if the first x bytes match, x is the length of the target value
     */
    public static boolean doesFirstBytesMatch(byte[] byteArray, byte[] targetValue) {
        if (byteArray.length >= targetValue.length) {
            for (int i = 0; i < targetValue.length; i++) {
                if (byteArray[i] != targetValue[i]) {
                    return false;
                }
            }
            return true; // All bytes match
        }
        return false; // The array is too short to contain the target value
    }
	/**Returns true if "this" version is greater or equal then comparison version
	 * @param thisMajor
	 * @param thisMinor
	 * @param thisInternal
	 * @param compMajor
	 * @param compMinor
	 * @param compInternal
	 * @return
	 */
	public static boolean compareVersions(int thisMajor, int thisMinor, int thisInternal,
			int compMajor, int compMinor, int compInternal) {

		if ((thisMajor>compMajor)
				||(thisMajor==compMajor && thisMinor>compMinor)
				||(thisMajor==compMajor && thisMinor==compMinor && thisInternal>=compInternal)){
			return true; // if FW ID is the same and version is greater or equal 
		}
		return false; // if less or not the same FW ID
	}

	/**Returns true if "this" version is greater or equal then comparison version
	 * @param thisMajor
	 * @param thisMinor
	 * @param thisInternal
	 * @param compMajor
	 * @param compMinor
	 * @param compInternal
	 * @return
	 */
	public static boolean compareVersions(String thisMajor, String thisMinor, String thisInternal,
			String compMajor, String compMinor, String compInternal) {
		try {
			return compareVersions(Integer.parseInt(thisMajor), Integer.parseInt(thisMinor), Integer.parseInt(thisInternal),
					Integer.parseInt(compMajor), Integer.parseInt(compMinor), Integer.parseInt(compInternal));
		} catch (NumberFormatException nFE) {
			System.out.println("UpdateChecker - Version parsing error");
			nFE.printStackTrace();
		}
		return true;
	}

	public static String convertDuration(int duration){
		
		double totalSecs = duration/1000; //convert from miliseconds to seconds
		int hours = (int) (totalSecs / 3600);
		int minutes = (int) ((totalSecs % 3600) / 60);
		int seconds = (int) (totalSecs % 60);
		String timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds);
		
		return timeString;
	}
	
	
	public String fromMilToDate(double miliseconds){
		
		long mili = (long) miliseconds;
		Date date = new Date(mili);		
		DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		return formatter.format(date);
	}
	
	
	public String fromSecondsToDate(String seconds){
		
		double miliseconds = 1000*Double.valueOf(seconds);
		long mili = (long) miliseconds;
		Date date = new Date(mili);		
		DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		return formatter.format(date);
	}
	
	public static String fromMilToDateExcelCompatible(String miliseconds, Boolean showMillis){
		return fromMilToDataExcelCompatible(miliseconds, showMillis, Integer.MAX_VALUE);
	}
	
	public static String fromMilToDataExcelCompatible(String miliseconds, boolean showMillis, int timezoneOffset){
		if (miliseconds==null){
			return "null";
		} else {
			String defaultTimeZoneId = null;
			double miliInDouble = Double.parseDouble(miliseconds);
			if(timezoneOffset != Integer.MAX_VALUE){
				defaultTimeZoneId = TimeZone.getDefault().getID();
				TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
				miliInDouble += timezoneOffset;
			}
			
			long mili = (long) miliInDouble;
			Date date = new Date(mili);
			DateFormat formatter;
			if(showMillis){
				formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
			}
			else{
				formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			}
			
			String dateFormatted = formatter.format(date);
			
			if(defaultTimeZoneId != null){
				TimeZone.setDefault(TimeZone.getTimeZone(defaultTimeZoneId));
			}
			
			return dateFormatted;
		}
	}
	
	public static long fromTimeStringToMilliseconds (String timeString){
		DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss:SSS");
		
		try {
			Date date = dateFormat.parse(timeString);
			return date.getTime();
		} 
		catch (ParseException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	public static long fromDateAndTimeMillisToTimeMilli (long dateAndTimeInMillis){
		DateFormat dateFormat = new SimpleDateFormat("HH.mm.ss.SSS");
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(dateAndTimeInMillis);
		String formattedDateAndTime = dateFormat.format(cal.getTime());
		
		Date date;
		try {
			date = dateFormat.parse(formattedDateAndTime);
			return date.getTime();
		} 
		catch (ParseException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	/**
	 * @return String: current date and time from the system i.e. PC in format yyyy-MM-dd_HH.mm.ss
	 */
	public static String getCurrentDateAndTimeFormatted(){
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
		Calendar cal = Calendar.getInstance();
		String formattedDateAndTime = dateFormat.format(cal.getTime());
		
		return formattedDateAndTime;
	}
	
	/** Returns the current local timezone offset in milliseconds, taking into account DST */
	public static int getCurrentLocalTimezoneOffsetMillis() {
		return ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds()*1000;
	}
	
	/** Returns the current local timezone offset in milliseconds for a specific date (Unix Time, milliseconds), taking into account DST */
	public static int getLocalTimezoneOffsetMillisForSpecificDate(long unixTimeMillis) {
		Instant instant = Instant.ofEpochMilli(unixTimeMillis);
		return ZoneId.systemDefault().getRules().getOffset(instant).getTotalSeconds()*1000;
	}
	
	public static File[] getArrayOfFilesWithFileType(File directory, final String fileType){
		File[] listOfFiles = directory.listFiles(new FilenameFilter() {
	        @Override
	        public boolean accept(File dir, String fileName) {
	        	String extension = "";
	        	int i = fileName.lastIndexOf('.');
	        	int p = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
	        	if (i > p) {
	        	    extension = fileName.substring(i+1);
	        	}
//	            return extension.matches("txt");
	        	return extension.matches(fileType);
	        }
	    });
		return listOfFiles;
	}
	
	
	public static boolean stringContainsItemFromListUpperCaseCheck(String inputString, String[] items) {
	    for(int i =0; i < items.length; i++) {
	        if(inputString.toUpperCase().contains(items[i].toUpperCase())) {
	            return true;
	        }
	    }
	    return false;
	}
	
	public static boolean stringContainsItemFromList(String inputString, String[] items) {
	    for(int i =0; i < items.length; i++) {
	        if(inputString.contains(items[i])) {
	            return true;
	        }
	    }
	    return false;
	}
	
	public static boolean stringContainsOnlyWordsItemFromList(String inputString, String[] items) {

	    boolean allMatched = true;
	    String target = inputString.toUpperCase();

	    for (String item : items) {

	        String[] words = item.toUpperCase().split("\\s+");
	        for (String w : words) {
	            if (!target.contains(w)) {
	                allMatched = false;
	            }
	        }
	        
	        if(allMatched) {
	    	    return true;
	        }
	    }
	    
	    return false;
	}

	public void threadSleep(long millis) {
		millisecondDelay(millis);
	}

	public void millisecondDelay(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			consolePrintLn("Thread sleep FAIL");
		//	e.printStackTrace();
		}
	}

	public void nanosecondDelay(int nanos) {
		try {
			Thread.sleep(0,nanos);
		} catch (InterruptedException e) {
			consolePrintLn("Thread sleep FAIL");
		//	e.printStackTrace();
		}
	}

	/**Used by the RTC sent over Bluetooth/Dock comms and calibration Dump file
	 * @param milliseconds
	 * @return
	 */
	public static byte[] convertMilliSecondsToShimmerRtcDataBytesLSB(long milliseconds) {
		byte[] rtcTimeArray = convertMilliSecondsToShimmerRtcDataBytesMSB(milliseconds);
		ArrayUtils.reverse(rtcTimeArray); // Big-endian by default
		return rtcTimeArray;
	}

	/** 
	 * @param milliseconds
	 * @return
	 */
	public static byte[] convertMilliSecondsToShimmerRtcDataBytesMSB(long milliseconds) {
		long milisecondTicks = (long)(((double)milliseconds) * 32.768); // Convert miliseconds to clock ticks
		byte[] rtcTimeArray = ByteBuffer.allocate(8).putLong(milisecondTicks).array();
		return rtcTimeArray;
	}
	
	/**Used by the RTC sent over Bluetooth/Dock comms and calibration Dump file
	 * @param rtcTimeArray
	 * @return
	 */
	public static long convertShimmerRtcDataBytesToMilliSecondsLSB(byte[] rtcTimeArray) {
		byte[] reversedArray = ArrayUtils.addAll(rtcTimeArray, null); //Create a clone
		ArrayUtils.reverse(reversedArray); // Big-endian by default
		return convertShimmerRtcDataBytesToMilliSecondsMSB(reversedArray);
	}

	/**
	 * @param rtcTimeArray
	 * @return
	 */
	public static long convertShimmerRtcDataBytesToMilliSecondsMSB(byte[] rtcTimeArray) {
		long timeWrapped = ByteBuffer.wrap(rtcTimeArray).getLong();
		long milisecondTicks = (long)((((double)timeWrapped)/32.768));  // Convert clock ticks to milliseconds
		return milisecondTicks;
	}

	/**Joins all string in array, each separated by a space 
	 * @param a an array of Strings to join
	 * @return
	 */
	public static String joinStrings(String[] a){
		String js="";
		for (int i=0;i<a.length;i++){
			if (i==0){
				js = a[i];
			} else{
				js = js + " " + a[i];
			}
		}
		return js;
	}

	public static double[][] deepCopyDoubleMatrix(double[][] input) {
	    if (input == null)
	        return null;
	    double[][] result = new double[input.length][];
	    for (int r = 0; r < input.length; r++) {
	        result[r] = input[r].clone();
	    }
	    return result;
	}
	
	
	
	public static ArrayList<Double> sortByComparator(Map<Double,Double> unsortMap, final boolean order) {
        List<Entry<Double, Double>> list = new LinkedList<Entry<Double, Double>>(unsortMap.entrySet());

        // Sorting the list based on values
        Collections.sort(list, new Comparator<Entry<Double,Double>>(){ 
			@Override
			public int compare(Entry<Double, Double> o1,
					Entry<Double, Double> o2) {
				if (order) {
                    return o1.getValue().compareTo(o2.getValue());
                }
                else {
                    return o2.getValue().compareTo(o1.getValue());
                }
			}
        });

        // Maintaining insertion order with the help of LinkedList
        Map<Double,Double> sortedMap = new LinkedHashMap<Double,Double>();
        for (Entry<Double,Double> entry : list){
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return new ArrayList<Double>(sortedMap.keySet());
    }
	// Regex breakdown:
    // ^          : Start of string
    // [0-9a-fA-F]{2} : Two hex characters
    // ( (:[0-9a-fA-F]{2}){5} | ([0-9a-fA-F]{2}){5} ) : Either 5 groups preceded by colons OR 5 groups with nothing
    // $          : End of string
    private static final String MAC_REGEX = "^([0-9a-fA-F]{2})((:[0-9a-fA-F]{2}){5}|([0-9a-fA-F]{2}){5})$";
    private static final Pattern MAC_PATTERN = Pattern.compile(MAC_REGEX);

    public static boolean isValidMacAddress(String mac) {
        if (mac == null) return false;
        return MAC_PATTERN.matcher(mac).matches();
    }
    
	public static boolean isValidMac(String mac) {
		if(mac==null){
			return false;
		}

		if(mac.isEmpty()){
			return false;
		}

		if(mac.equals(MAC_ADDRESS_FFFFS) || mac.equals(MAC_ADDRESS_ZEROS)){
			return false;
		}
		
		return true;
	}
	
	public static String getDayString(int dayIndex) {
		switch (dayIndex) {
		    case Calendar.SUNDAY:
		    	return "Sun";
		    case Calendar.MONDAY:
		    	return "Mon";
		    case Calendar.TUESDAY:
		    	return "Tue";
		    case Calendar.WEDNESDAY:
		    	return "Wed";
		    case Calendar.THURSDAY:
		    	return "Thur";
		    case Calendar.FRIDAY:
		    	return "Fri";
		    case Calendar.SATURDAY:
		    	return "Sat";
		    default:
		    	return "";
		}
	}
	
	private static String getMonthString(int monthIndex) {
    	switch(monthIndex){
			case(Calendar.JANUARY):
				return "Jan";
			case(Calendar.FEBRUARY):
				return "Feb";
			case(Calendar.MARCH):
				return "Mar";
			case(Calendar.APRIL):
				return "Apr";
			case(Calendar.MAY):
				return "May";
			case(Calendar.JUNE):
				return "June";
			case(Calendar.JULY):
				return "July";
			case(Calendar.AUGUST):
				return "Aug";
			case(Calendar.SEPTEMBER):
				return "Sept";
			case(Calendar.OCTOBER):
				return "Oct";
			case(Calendar.NOVEMBER):
				return "Nov";
			case(Calendar.DECEMBER):
				return "Dec";
            default:
            	return "";
    	}
	}
	
	
	public static File createFileAndDeleteIfExists(String filePath){
		
		File fileOld = new File(filePath);
		File fileNew = null;
		
		if(fileOld.exists()){
			fileOld.delete();
			fileNew = new File(filePath);
		}
		else{
			fileNew = fileOld;
		}
		
		return fileNew;
	}

	public static byte[] generateRadioConfigByteArray(int radioChannel, int radioGroupId, int radioDeviceId, int radioResponseWindow) {
		byte[] radioConfigArray = new byte[7];
		
        radioConfigArray[0] = (byte)((radioChannel >> 0) & 0x00FF);
        
        //All MSB first
        radioConfigArray[1] = (byte)((radioGroupId >> 8) & 0x00FF);
        radioConfigArray[2] = (byte)((radioGroupId >> 0) & 0x00FF);
        radioConfigArray[3] = (byte)((radioDeviceId >> 8) & 0x00FF);
        radioConfigArray[4] = (byte)((radioDeviceId >> 0) & 0x00FF);
        radioConfigArray[5] = (byte)((radioResponseWindow >> 8) & 0x00FF);
        radioConfigArray[6] = (byte)((radioResponseWindow >> 0) & 0x00FF);
        
		return radioConfigArray;
	}

	
	public static String getConfigTimeFromFullTrialName(String trialName){
		
		String[] splittedTrialName = trialName.split("_");
		String configTime = splittedTrialName[splittedTrialName.length-1];
		return configTime;
	}

	
	public static String convertStackTraceToString(StackTraceElement[] exceptionStackTrace){
		Exception e = new Exception();
		e.setStackTrace(exceptionStackTrace);
//		e.printStackTrace();
		
//		for(StackTraceElement element:msg.mExceptionStackTrace) {
//			consolePrint("Exception thrown from " + element.getMethodName()
//              + " in class " + element.getClassName() + " [on line number "
//              + element.getLineNumber() + " of file " + element.getFileName() + "\n");
//		}
		
		//create new StringWriter object
		StringWriter sWriter = new StringWriter();
		//create PrintWriter for StringWriter
		PrintWriter pWriter = new PrintWriter(sWriter);
		//now print the stacktrace to PrintWriter we just created
		e.printStackTrace(pWriter);
		//use toString method to get stacktrace to String from StringWriter object
		String strStackTrace = sWriter.toString();
		return strStackTrace;
	}

	public static String longToHexStringWithSpacesFormatted(long number, int numBytes) {
		byte[] bytesArray = new byte[numBytes];
		for(int i=0;i<numBytes;i++){
			bytesArray[i] = (byte) ((number >> (i*8)) & 0xFF);
		}
		return bytesToHexStringWithSpacesFormatted(bytesArray);
	}

	public static String longToHexString(long number, int numBytes) {
		byte[] bytesArray = new byte[numBytes];
		for(int i=0;i<numBytes;i++){
			bytesArray[i] = (byte) ((number >> (i*8)) & 0xFF);
		}
		return bytesToHexString(bytesArray);
	}
	public static String convertLongToHexString(long longNumber) {
		byte[] bytesArray = convertLongToByteArray(longNumber);
		return bytesToHexString(bytesArray);
	}
	public static String doubleArrayToString(double[][] doubleArray) {
		String returnString = "";
		for(int x=0;x<doubleArray.length;x++){
			for(int y=0;y<doubleArray[x].length;y++){
				returnString += doubleArray[x][y] + "\t";
			}
			returnString += "\n";
		}
		return returnString;
	}

	public static double[][] nudgeDoubleArray(double maxVal, double minVal, int precision, double[][] newArray) {
		for(int x=0;x<newArray.length;x++){
			for(int y=0;y<newArray[x].length;y++){
				//nudge into range
				newArray[x][y] = UtilShimmer.nudgeDouble(newArray[x][y], minVal, maxVal);
				//correct the precision
				newArray[x][y] = applyPrecisionCorrection(newArray[x][y], precision);
			}
		}
		return newArray;
	}
	
	public static double applyPrecisionCorrection(double value, int precision) {
		return new BigDecimal(value).setScale(precision, BigDecimal.ROUND_HALF_UP).doubleValue();
	}
	
	public static double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    BigDecimal bd = new BigDecimal(value);
	    bd = bd.setScale(places, RoundingMode.HALF_UP);
	    return bd.doubleValue();
	}

	public static double nudgeDouble(double valToNudge, double minVal, double maxVal) {
	    return Math.max(minVal, Math.min(maxVal, valToNudge));
	}
	
	public static int nudgeInteger(int valToNudge, int minVal, int maxVal) {
	    return Math.max(minVal, Math.min(maxVal, valToNudge));
	}

	public static long nudgeLong(long valToNudge, long minVal, long maxVal) {
		return Math.max(minVal, Math.min(maxVal, valToNudge));
	}

	public static boolean isAllZeros(double[][] matrix){
		if(matrix==null){
			return false;
		}

		boolean allZeros = true;
		for(int j = 0; j < matrix[1].length; j++){
			for(int i = 0; i < matrix.length; i++){
				if(matrix[j][i]!=0){
					return false;
				}
			}
		}
		return allZeros;
	}

	public static boolean isAnyValueOutsideRange(double[][] matrix, int range){
		if(matrix == null){
			return true;
		}

		boolean isAnyOutsideRange = false;
		for(int j = 0; j < matrix[1].length; j++){
			for(int i = 0; i < matrix.length; i++){
				double value = Math.abs(matrix[j][i]);
				if(value > range){
					return true;
				}
			}
		}
		return isAnyOutsideRange;
	}
	
	public static boolean isAllFF(byte[] bufferCalibrationParameters) {
		for(byte myByte:bufferCalibrationParameters){
			if(myByte!=(byte)0xFF){
				return false;
			}
		}
		return true;
	}
	
	public static boolean isAllZeros(byte[] bufferCalibrationParameters) {
		for(byte myByte:bufferCalibrationParameters){
			if(myByte!=(byte)0x00){
				return false;
			}
		}
		return true;
	}

	public static String formatDouble(double d){
		//TODO not sure if there is an easier way to do this by just using DecimalFormat
		if((d==0) || (d>=1.0 && d<=9999.0)){
			return Double.toString(UtilShimmer.round(d, 2));
		}
		else if(d>=0.001 && d<1.0){
			return Double.toString(UtilShimmer.round(d, 3));
		}
		else{
			DecimalFormat format = new DecimalFormat("##0.0E0");
			return format.format(d);
		}
	}
	
	public static String formatDoubleToNdecimalPlaces(double doubleToFormat, int n){
		
		StringBuilder sb = new StringBuilder();
		for(int i=0; i<n; i++){ //number of digits to right hand side of decimal point
			sb.append("0"); 
		}
		
		DecimalFormat df = new DecimalFormat("#." + sb.toString()); 
		String formatted = df.format(doubleToFormat);
		
		int index = formatted.indexOf("."); 
		
		if(index == 0){ // left pad with zero if required e.g .34 -> 0.34
			formatted =  "0" + formatted; 
		}
		else if(formatted.charAt(index-1) == '-'){ // left pad with zero if required e.g -.34 -> -0.34
			formatted = new StringBuilder(formatted).insert(1, "0").toString();
		}
		return formatted;
	}

    /**
     * RSSI = TxPower - 10 * n * lg(d)
     * n = 2 (in free space)
     * 
     * d = 10 ^ ((TxPower - RSSI) / (10 * n))
     */
	public static double calculateDistanceFromRssi(long rssi, double txPower) {
	    return Math.pow(10d, (txPower - rssi) / (10.0 * 2.0));
	}

	public static StackTraceElement[] getCurrentStackTrace() {
		return Thread.currentThread().getStackTrace();
	}

	public static void consolePrintCurrentStackTrace() {
		System.out.println(UtilShimmer.convertStackTraceToString(getCurrentStackTrace()));
	}

	public static boolean doesFileExist(String filePath){ 
		File file = new File(filePath);
		return file.exists();
	}

	public static byte[] interleaveByteArrays(byte[] bytes, byte[] bytes2) {
		byte[] interleave = new byte[ bytes.length + bytes2.length];
		int count1=0;
		int count2=0;
		int curPos=0;
		for (int i=0;i<interleave.length/2;i++){
			if (i%2==0){
				interleave[curPos]=bytes[count1];
				count1++;
				curPos++;
				interleave[curPos]=bytes[count1];
				count1++;
				curPos++;
			} else {
				interleave[curPos]=bytes2[count2];
				count2++;
				curPos++;
				interleave[curPos]=bytes2[count2];
				count2++;
				curPos++;
			}
		}
		return interleave;
	}
	
	public static byte[] my_int_to_bb_le(int myInteger) {
		return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(myInteger).array();
	}

	public static int my_bb_to_int_le(byte[] byteBarray) {
		return ByteBuffer.wrap(byteBarray).order(ByteOrder.LITTLE_ENDIAN).getInt();
	}

	public static byte[] my_int_to_bb_be(int myInteger) {
		return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(myInteger).array();
	}

	public static int my_bb_to_int_be(byte[] byteBarray) {
		return ByteBuffer.wrap(byteBarray).order(ByteOrder.BIG_ENDIAN).getInt();
	}

	
	public static List<ShimmerDevice> cloneShimmerDevices(List<ShimmerDevice> listOfShimmerDevices){
		List<ShimmerDevice> listOfClonedShimmerDevices = new ArrayList<ShimmerDevice>();
		
		Iterator<ShimmerDevice> iterator = listOfShimmerDevices.iterator();
		while(iterator.hasNext()){
			ShimmerDevice shimmerDevice = iterator.next();
			listOfClonedShimmerDevices.add(shimmerDevice.deepClone());
		}
		
		return listOfClonedShimmerDevices;
	}

	public static List<String> getListOfUniqueIdsFromShimmerDevices(List<ShimmerDevice> listOfShimmerDevices) {
		List<String> listOfUniqueIds = new ArrayList<String>();
		Iterator<ShimmerDevice> iterator = listOfShimmerDevices.iterator();
		while(iterator.hasNext()){
			ShimmerDevice shimmerDevice = iterator.next();
			listOfUniqueIds.add(shimmerDevice.getUniqueId());
		}
		return listOfUniqueIds;
	}

	public static String getCheckOrCrossForBoolean(boolean state) {
		return state? CHECK_MARK_STRING:CROSS_MARK_STRING;
	}

	public static String removeCheckMarkIfPresent(String origString) {
		String modifiedString = origString.replace((CHECK_MARK_STRING), "");
		modifiedString = modifiedString.replace((CROSS_MARK_STRING), "");
		return modifiedString;
	}

	public static int[] generateSawToothIntSignal(int min, int max, int size, int increment, int startVal) {
		int[] signal = new int[size];
		
		signal[0] = startVal;
		for(int i=1;i<size;i++) {
			signal[i] = signal[i-1] + increment;
			
			if(signal[i]>max) {
				signal[i] = min;
			}
		}
		
		return signal;
	}

	public static int generateRandomIntInRange(int low, int high) {
		Random r = new Random();
		return r.nextInt(high-low) + low;
	}
	
	/** Method to return 1 or 0 depending if a bit is 'high' or 'low' respectively in a long value
	 * @param value is number you wish to check if bit is 'high' in
	 * @param bitIndex is the index of the bit to check if 'high'
	 * @return true '1' if bit in the long value is 'high', otherwise '0'
	 */
	public static int getBitSetBinaryValue(long value, int bitIndex) {
		return (isBitSet(value, bitIndex)) ? 1 : 0;
	}
	
	/**
	 * Method to return true/false if a bit is 'high' in a long value
	 * @param value is number you wish to check if bit is 'high' in
	 * @param bitIndex is the index of the bit to check if 'high'
	 * @return true if bit in the long value is 'high', otherwise false
	 */
	public static boolean isBitSet(long value, int bitIndex) {
		return (value & (1L << bitIndex)) != 0;
	}
	
	public static String getParsedSamplingRateWithTwoDecimalPlaces(double samplingRate){
		// limit rate to 2 decimal places
		String convertedDouble = Double.toString(samplingRate);
		String[] splitArray=String.valueOf(samplingRate).split("\\."); 
		
		if(splitArray.length >= 2){
			if(splitArray[1].length()>2){
				convertedDouble = splitArray[0]+".";
				for(int i =0; i < 2; i++){
					convertedDouble += splitArray[1].charAt(i);
				}
			}
		}
		return convertedDouble;
	}
	
	public static String getTimeZoneID(){
		return TimeZone.getDefault().getID();
	}
	
	public static int getTimeZoneOffset(){
		return TimeZone.getDefault().getOffset(System.currentTimeMillis());
	}
	
//	public static void main(String[] args) {
//		long milliSeconds = System.currentTimeMillis();
//		
//		System.err.println(convertMilliSecondsToDateString(milliSeconds, true));
//		System.err.println(milliSeconds);
//		System.err.println(UtilShimmer.bytesToHexStringWithSpacesFormatted(convertMilliSecondsToShimmerRtcDataBytesLSB(milliSeconds)));
//	}
	
	public static boolean isOsMac() {
		return System.getProperty("os.name").toLowerCase().contains("mac");
	}

	public static boolean isOsWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}
}
