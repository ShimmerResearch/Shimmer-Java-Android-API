package com.shimmerresearch.sensors.bmpX80;

import java.awt.EventQueue;
import java.util.HashMap;
import java.util.Map;

import com.shimmerresearch.driverUtilities.UtilParseData;
import com.shimmerresearch.driverUtilities.UtilShimmer;

public class CalibDetailsBmp390 extends CalibDetailsBmpX80 {

	/**	 * 	 */
	private static final long serialVersionUID = 4602512529781344807L;
	public static Map<String, Bmp3CalibCoefficients> calibCoefficientsBySensor = new HashMap<>();
	public String mSensorMacID;

	public class Bmp3Constants {
	    // Floating-point constants
	    public static final double BMP3_MIN_PRES_DOUBLE = 30000.0;
	    public static final double BMP3_MAX_PRES_DOUBLE = 125000.0;
	    public static final double BMP3_MIN_TEMP_DOUBLE = -40.0;
	    public static final double BMP3_MAX_TEMP_DOUBLE = 85.0;

	    // Byte constants
	    public static final byte BMP3_OK = 0;
	    public static final byte BMP3_W_MIN_TEMP = 3;
	    public static final byte BMP3_W_MAX_TEMP = 4;
	    public static final byte BMP3_W_MIN_PRES = 5;
	    public static final byte BMP3_W_MAX_PRES = 6;
	}
	
	public class Bmp3CalibCoefficients{
		public double Bmp3QuantizedCalibData_ParT1;
	     public double Bmp3QuantizedCalibData_ParT2;
	     public double Bmp3QuantizedCalibData_ParT3;
	     public double Bmp3QuantizedCalibData_ParP1;
	     public double Bmp3QuantizedCalibData_ParP2;
	     public double Bmp3QuantizedCalibData_ParP3;
	     public double Bmp3QuantizedCalibData_ParP4;
	     public double Bmp3QuantizedCalibData_ParP5;
	     public double Bmp3QuantizedCalibData_ParP6;
	     public double Bmp3QuantizedCalibData_ParP7;
	     public double Bmp3QuantizedCalibData_ParP8;
	     public double Bmp3QuantizedCalibData_ParP9;
	     public double Bmp3QuantizedCalibData_ParP10;
	     public double Bmp3QuantizedCalibData_ParP11;
	     public double Bmp3QuantizedCalibData_TLin;
	}
 
 
	    // Trim Variables
     public short Bmp3RegCalibData_ParT1;
     public short Bmp3RegCalibData_ParT2;
     public byte Bmp3RegCalibData_ParT3;
     public short Bmp3RegCalibData_ParP1;
     public short Bmp3RegCalibData_ParP2;
     public byte Bmp3RegCalibData_ParP3;
     public byte Bmp3RegCalibData_ParP4;
     public int Bmp3RegCalibData_ParP5;
     public int Bmp3RegCalibData_ParP6;
     public byte Bmp3RegCalibData_ParP7;
     public byte Bmp3RegCalibData_ParP8;
     public short Bmp3RegCalibData_ParP9;
     public byte Bmp3RegCalibData_ParP10;
     public byte Bmp3RegCalibData_ParP11;
	 
	 
	 public double par_T1 ; 
	 public double par_T2 ; 
	 public double par_T3 ; 
	 public double par_P1 ;
	 public double par_P2 ;
	 public double par_P3 ;
	 public double par_P4 ;
	 public double par_P5 ;
	 public double par_P6 ;
	 public double par_P7 ;
	 public double par_P8 ;
	 public double par_P9 ;
	 public double par_P10;
	 public double par_P11;
	 
	 public CalibDetailsBmp390(String mMacIdFromUart) {
		 mSensorMacID = mMacIdFromUart;
	}

	public void setPressureCalib(
			 	double T1, double T2, double T3,
				double P1, double P2, double P3, 
				double P4, double P5, double P6, 
				double P7, double P8, double P9,
				double P10, double P11) {
			this.par_T1 = T1; 
			this.par_T2 = T2;
			this.par_T3 = T3;
			this.par_P1 = P1;
			this.par_P2 = P2;
			this.par_P3 = P3;
			this.par_P4 = P4;
			this.par_P5 = P5;
			this.par_P6 = P6;
			this.par_P7 = P7;
			this.par_P8 = P8;
			this.par_P9 = P9;
			this.par_P10 = P10;
			this.par_P11 = P11;
	}
	 
	@Override
	public double[] calibratePressureSensorData(double UP, double UT) {
		Bmp3CalibCoefficients calibCoefficientsPerSensor = calibCoefficientsBySensor.get(mSensorMacID);
		if(calibCoefficientsPerSensor==null){
			// No coefficients for this sensor: parseCalParamByteArray() skips an
			// all-0x00/0xFF block (e.g. an unwritten SD header), and streaming can
			// sample before the 0xA7 reply arrives. Unlike the BMP180/BMP280 there
			// are no published default coefficients to fall back on, so report the
			// values as not calibrated rather than invent them (DEV-1125).
			return new double[]{Double.NaN, Double.NaN};
		}

		byte rslt = Bmp3Constants.BMP3_OK;

        double uncompTemp = UT;
        double partialDataT1;
        double partialDataT2;

        partialDataT1 = uncompTemp - calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParT1;
        partialDataT2 = partialDataT1 * calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParT2;

        calibCoefficientsPerSensor.Bmp3QuantizedCalibData_TLin = partialDataT2 + (partialDataT1 * partialDataT1) * calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParT3;

        if (calibCoefficientsPerSensor.Bmp3QuantizedCalibData_TLin < Bmp3Constants.BMP3_MIN_TEMP_DOUBLE) {
        	calibCoefficientsPerSensor.Bmp3QuantizedCalibData_TLin = Bmp3Constants.BMP3_MIN_TEMP_DOUBLE;
            rslt = Bmp3Constants.BMP3_W_MIN_TEMP;
        }

        if (calibCoefficientsPerSensor.Bmp3QuantizedCalibData_TLin > Bmp3Constants.BMP3_MAX_TEMP_DOUBLE) {
        	calibCoefficientsPerSensor.Bmp3QuantizedCalibData_TLin = Bmp3Constants.BMP3_MAX_TEMP_DOUBLE;
            rslt = Bmp3Constants.BMP3_W_MAX_TEMP;
        }		
        
        
        byte result = Bmp3Constants.BMP3_OK;

        // Variable to store the compensated pressure
        double compPress;

        // Temporary variables used for compensation
        double partialData1;
        double partialData2;
        double partialData3;
        double partialData4;
        double partialOut1;
        double partialOut2;

        partialData1 = calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParP6 * calibCoefficientsPerSensor.Bmp3QuantizedCalibData_TLin;
        partialData2 = calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParP7 * powBmp3(calibCoefficientsPerSensor.Bmp3QuantizedCalibData_TLin, 2);
        partialData3 = calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParP8 * powBmp3(calibCoefficientsPerSensor.Bmp3QuantizedCalibData_TLin, 3);
        partialOut1 = calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParP5 + partialData1 + partialData2 + partialData3;

        partialData1 = calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParP2 * calibCoefficientsPerSensor.Bmp3QuantizedCalibData_TLin;
        partialData2 = calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParP3 * powBmp3(calibCoefficientsPerSensor.Bmp3QuantizedCalibData_TLin, 2);
        partialData3 = calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParP4 * powBmp3(calibCoefficientsPerSensor.Bmp3QuantizedCalibData_TLin, 3);
        partialOut2 = UP * (calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParP1 + partialData1 + partialData2 + partialData3);

        partialData1 = powBmp3(UP, 2);
        partialData2 = calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParP9 + calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParP10 * calibCoefficientsPerSensor.Bmp3QuantizedCalibData_TLin;
        partialData3 = partialData1 * partialData2;
        partialData4 = partialData3 + powBmp3(UP, 3) * calibCoefficientsPerSensor.Bmp3QuantizedCalibData_ParP11;
        compPress = partialOut1 + partialOut2 + partialData4;

        if (compPress < Bmp3Constants.BMP3_MIN_PRES_DOUBLE) {
            compPress = Bmp3Constants.BMP3_MIN_PRES_DOUBLE;
            result = Bmp3Constants.BMP3_W_MIN_PRES;
        }

        if (compPress > Bmp3Constants.BMP3_MAX_PRES_DOUBLE) {
            compPress = Bmp3Constants.BMP3_MAX_PRES_DOUBLE;
            result = Bmp3Constants.BMP3_W_MAX_PRES;
        }

        double[] caldata = new double[2];
		caldata[0]=compPress;
		caldata[1]=calibCoefficientsPerSensor.Bmp3QuantizedCalibData_TLin;
		return caldata;
	}

	// Method to calculate the power of a number
    private double powBmp3(double base, int exponent) {
        return Math.pow(base, exponent);
    }
    
	@Override
	public byte[] generateCalParamByteArray() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void parseCalParamByteArray(byte[] bufferCalibrationParameters, CALIB_READ_SOURCE calibReadSource) {
		if(calibReadSource.ordinal()>getCalibReadSource().ordinal()){

		if(UtilShimmer.isAllFF(bufferCalibrationParameters)
				||UtilShimmer.isAllZeros(bufferCalibrationParameters)){
			return;
		}
		Bmp3CalibCoefficients calib = new Bmp3CalibCoefficients();

		setPressureRawCoefficients(bufferCalibrationParameters);
		setCalibReadSource(calibReadSource);
		 byte[] pressureResoResTest = bufferCalibrationParameters;
	        double tempVar;

	        // 1 / 2^8
	        tempVar = 0.00390625;
	        Bmp3RegCalibData_ParT1 = (short) concatenateBytes(pressureResoResTest[1], pressureResoResTest[0]);
	        calib.Bmp3QuantizedCalibData_ParT1 = ((double) Bmp3RegCalibData_ParT1 / tempVar);

	        Bmp3RegCalibData_ParT2 = (short) concatenateBytes(pressureResoResTest[3], pressureResoResTest[2]);
	        tempVar = 1073741824.0;
	        calib.Bmp3QuantizedCalibData_ParT2 = ((double) Bmp3RegCalibData_ParT2 / tempVar);

	        Bmp3RegCalibData_ParT3 = (byte) pressureResoResTest[4];
	        tempVar = 281474976710656.0;
	        calib.Bmp3QuantizedCalibData_ParT3 = ((double) Bmp3RegCalibData_ParT3 / tempVar);

	        Bmp3RegCalibData_ParP1 = (short) concatenateBytes(pressureResoResTest[6], pressureResoResTest[5]);
	        tempVar = 1048576.0;
	        calib.Bmp3QuantizedCalibData_ParP1 = ((double) (Bmp3RegCalibData_ParP1 - 16384) / tempVar);

	        Bmp3RegCalibData_ParP2 = (short) concatenateBytes(pressureResoResTest[8], pressureResoResTest[7]);
	        tempVar = 536870912.0;
	        calib.Bmp3QuantizedCalibData_ParP2 = ((double) (Bmp3RegCalibData_ParP2 - 16384) / tempVar);

	        Bmp3RegCalibData_ParP3 = (byte) pressureResoResTest[9];
	        tempVar = 4294967296.0;
	        calib.Bmp3QuantizedCalibData_ParP3 = ((double) Bmp3RegCalibData_ParP3 / tempVar);

	        Bmp3RegCalibData_ParP4 = (byte) pressureResoResTest[10];
	        tempVar = 137438953472.0;
	        calib.Bmp3QuantizedCalibData_ParP4 = ((double) Bmp3RegCalibData_ParP4 / tempVar);

	        Bmp3RegCalibData_ParP5 = concatenateBytes(pressureResoResTest[12], pressureResoResTest[11]);

	        // 1 / 2^3
	        tempVar = 0.125;
	        calib.Bmp3QuantizedCalibData_ParP5 = ((double) Bmp3RegCalibData_ParP5 / tempVar);

	        Bmp3RegCalibData_ParP6 = concatenateBytes(pressureResoResTest[14], pressureResoResTest[13]);
	        tempVar = 64.0;
	        calib.Bmp3QuantizedCalibData_ParP6 = ((double) Bmp3RegCalibData_ParP6 / tempVar);

	        Bmp3RegCalibData_ParP7 = (byte) pressureResoResTest[15];
	        tempVar = 256.0;
	        calib.Bmp3QuantizedCalibData_ParP7 = ((double) Bmp3RegCalibData_ParP7 / tempVar);

	        Bmp3RegCalibData_ParP8 = (byte) pressureResoResTest[16];
	        tempVar = 32768.0;
	        calib.Bmp3QuantizedCalibData_ParP8 = ((double) Bmp3RegCalibData_ParP8 / tempVar);

	        Bmp3RegCalibData_ParP9 = (short) concatenateBytes(pressureResoResTest[18], pressureResoResTest[17]);
	        tempVar = 281474976710656.0;
	        calib.Bmp3QuantizedCalibData_ParP9 = ((double) Bmp3RegCalibData_ParP9 / tempVar);

	        Bmp3RegCalibData_ParP10 = (byte) pressureResoResTest[19];
	        tempVar = 281474976710656.0;
	        calib.Bmp3QuantizedCalibData_ParP10 = ((double) Bmp3RegCalibData_ParP10 / tempVar);

	        Bmp3RegCalibData_ParP11 = (byte) pressureResoResTest[20];
	        tempVar = 36893488147419103232.0;
	        calib.Bmp3QuantizedCalibData_ParP11 = ((double) Bmp3RegCalibData_ParP11 / tempVar);

	        System.out.println("par_T1 = " + calib.Bmp3QuantizedCalibData_ParT1);
	        System.out.println("par_T2 = " + calib.Bmp3QuantizedCalibData_ParT2);
	        System.out.println("par_T3 = " + calib.Bmp3QuantizedCalibData_ParT3);
	        System.out.println("par_P1 = " + calib.Bmp3QuantizedCalibData_ParP1);
	        System.out.println("par_P2 = " + calib.Bmp3QuantizedCalibData_ParP2);
	        System.out.println("par_P3 = " + calib.Bmp3QuantizedCalibData_ParP3);
	        System.out.println("par_P4 = " + calib.Bmp3QuantizedCalibData_ParP4);
	        System.out.println("par_P5 = " + calib.Bmp3QuantizedCalibData_ParP5);
	        System.out.println("par_P6 = " + calib.Bmp3QuantizedCalibData_ParP6);
	        System.out.println("par_P7 = " + calib.Bmp3QuantizedCalibData_ParP7);
	        System.out.println("par_P8 = " + calib.Bmp3QuantizedCalibData_ParP8);
	        System.out.println("par_P9 = " + calib.Bmp3QuantizedCalibData_ParP9);
	        System.out.println("par_P10 = " + calib.Bmp3QuantizedCalibData_ParP10);
	        System.out.println("par_P11 = " + calib.Bmp3QuantizedCalibData_ParP11);
	        
	        calibCoefficientsBySensor.put(mSensorMacID, calib);
		}
	}

	// Concatenate bytes method
    private int concatenateBytes(byte highByte, byte lowByte) {
        return ((highByte & 0xFF) << 8) | (lowByte & 0xFF);
    }
	@Override
	public void resetToDefaultParameters() {
		// TODO Auto-generated method stub
		
	}
	
	public static void main(String[] args) {

		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					CalibDetailsBmp390 calibDetailsBmp390 = new CalibDetailsBmp390("test");
					byte[] pressureResoResTest = {
						    (byte) 0xE7, (byte) 0x6B, (byte) 0xF0, (byte) 0x4A, (byte) 0xF9, 
						    (byte) 0xAB, (byte) 0x1C, (byte) 0x9B, (byte) 0x15, (byte) 0x06, 
						    (byte) 0x01, (byte) 0xD2, (byte) 0x49, (byte) 0x18, (byte) 0x5F, 
						    (byte) 0x03, (byte) 0xFA, (byte) 0x3A, (byte) 0x0F, (byte) 0x07, 
						    (byte) 0xF5
						};
					calibDetailsBmp390.parseCalParamByteArray(pressureResoResTest,CALIB_READ_SOURCE.INFOMEM);
					byte[] sensorDataP = { 
					    (byte) 0x00, (byte) 0x0D, (byte) 0x64 
					};

					byte[] sensorDataT = { 
					    (byte) 0x00, (byte) 0xBA, (byte) 0x7F 
					};

					// Array of strings in Java
					String[] sensorDataType = { "u24" };
					long[] uncalibResultP = UtilParseData.parseData(sensorDataP, sensorDataType);
					long[] uncalibResultT = UtilParseData.parseData(sensorDataT, sensorDataType);
			        System.out.println("uncalibResultP = " + uncalibResultP[0]);
			        System.out.println("uncalibResultT = " + uncalibResultT[0]);

					double[] calibdata = calibDetailsBmp390.calibratePressureSensorData(uncalibResultP[0], uncalibResultT[0]);
			        System.out.println("PRESSURE = " + calibdata[0]);
			        System.out.println("TEMPERATURE = " + calibdata[1]);
			        
			    	byte[] sensorDataP2 = { 
						    (byte) 0x00, (byte) 0x17, (byte) 0x64 
						};

					byte[] sensorDataT2 = { 
						    (byte) 0x00, (byte) 0xCF, (byte) 0x7F 
						};

						// Array of strings in Java
						long[] uncalibResultP2 = UtilParseData.parseData(sensorDataP2, sensorDataType);
						long[] uncalibResultT2 = UtilParseData.parseData(sensorDataT2, sensorDataType);
				        System.out.println("uncalibResultP2 = " + uncalibResultP2[0]);
				        System.out.println("uncalibResultT2 = " + uncalibResultT2[0]);

						double[] calibdata2 = calibDetailsBmp390.calibratePressureSensorData(uncalibResultP2[0], uncalibResultT2[0]);
				        System.out.println("PRESSURE = " + calibdata2[0]);
				        System.out.println("TEMPERATURE = " + calibdata2[1]);

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}


}
