package com.shimmerresearch.algorithms.orientation;

public class Orientation3DObject {

	private double quaternionW, quaternionX, quaternionY, quaternionZ;
	private double rho;
	
	private double theta, angleX, angleY, angleZ;
	/** (Yaw, Pitch, Roll) == (Heading, Attitude, Bank) == (Azimuth, elevation, tilt)*/
	private double yaw, pitch, roll; 
	
	private static boolean isUseQuatToEuler = false;

    public Orientation3DObject(double q1, double q2, double q3, double q4){
        this.quaternionW = q1;
        this.quaternionX = q2;
        this.quaternionY = q3;
        this.quaternionZ = q4;
        calculateAngles();
        calculateEuler();
    }
    
    private void calculateAngles(){
    	rho = Math.acos(quaternionW);
    	theta = rho * 2;
    	angleX = quaternionX / Math.sin(rho);
    	angleY = quaternionY / Math.sin(rho);
    	angleZ = quaternionZ / Math.sin(rho);
    }

	public double getQuaternionW() {
		return quaternionW;
	}

	public double getQuaternionX() {
		return quaternionX;
	}

	public double getQuaternionY() {
		return quaternionY;
	}

	public double getQuaternionZ() {
		return quaternionZ;
	}

	public double getTheta() {
		return theta;
	}

	public double getAngleX() {
		return angleX;
	}

	public double getAngleY() {
		return angleY;
	}

	public double getAngleZ() {
		return angleZ;
	}

	
	//TODO MN temporary code -> needs testing 
	public void calculateEuler() {
		if(isUseQuatToEuler){
			quaternianToEuler(quaternionW, quaternionX, quaternionY, quaternionZ);
		}
		else {
			axisAngleToEuler(angleX, angleY, angleZ, theta);
		}
	}
	
	//TODO MN temporary code -> needs testing 
	//From: http://www.euclideanspace.com/maths/geometry/rotations/conversions/angleToEuler/
	public void axisAngleToEuler(double x, double y, double z, double angle) {
		double s=Math.sin(angle);
		double c=Math.cos(angle);
		double t=1-c;
		//  if axis is not already normalised then uncomment this
		// double magnitude = Math.sqrt(x*x + y*y + z*z);
		// if (magnitude==0) throw error;
		// x /= magnitude;
		// y /= magnitude;
		// z /= magnitude;
		if ((x*y*t + z*s) > 0.998) { // north pole singularity detected
			yaw = 2*Math.atan2(x*Math.sin(angle/2),Math.cos(angle/2));
			pitch = Math.PI/2;
			roll = 0;
			return;
		}
		if ((x*y*t + z*s) < -0.998) { // south pole singularity detected
			yaw = -2*Math.atan2(x*Math.sin(angle/2),Math.cos(angle/2));
			pitch = -Math.PI/2;
			roll = 0;
			return;
		}
		yaw = Math.atan2(y * s- x * z * t , 1 - (y*y+ z*z ) * t) * 180/Math.PI;
		pitch = Math.asin(x * y * t + z * s)  * 180/Math.PI;
		roll = Math.atan2(x * s - y * z * t , 1 - (x*x + z*z) * t) * 180/Math.PI;
	}
	
	//TODO MN temporary code -> needs testing 
	//From: https://en.wikipedia.org/wiki/Conversion_between_quaternions_and_Euler_angles
	public void quaternianToEuler(double q0, double q1, double q2, double q3){
		roll = Math.atan2(2*(q0*q1 + q2*q3), 1 - 2*(Math.pow(q1,2) + Math.pow(q2,2))) * 180/Math.PI;
		pitch = Math.asin(2*(q0*q2 - q3*q1)) * 180/Math.PI;
		yaw = Math.atan2(2*(q0*q3 + q1*q2), 1 - 2*(Math.pow(q2,2) + Math.pow(q3,2))) * 180/Math.PI;
	}
	
//	//TODO MN temporary code -> needs testing 
//	//From: http://www.euclideanspace.com/maths/geometry/rotations/conversions/quaternionToEuler/indexLocal.htm
//	public void quaternianToEuler(double q1w, double q1x, double q1y, double q1z){
//		double sqw = q1w*q1w;
//		double sqx = q1x*q1x;
//		double sqy = q1y*q1y;
//		double sqz = q1z*q1z;
//				 
//		yaw = Math.atan2(2.0 * (q1x*q1y + q1z*q1w),(sqx - sqy - sqz + sqw));
//		roll = Math.atan2(2.0 * (q1y*q1z + q1x*q1w),(-sqx - sqy + sqz + sqw));
//		pitch = Math.asin(-2.0 * (q1x*q1z - q1y*q1w)/sqx + sqy + sqz + sqw); 
//	}
	
//	//http://examples.oreilly.com/0636920021735/ch16/16_10/AHRS.cpp
//	// Returns the Euler angles in radians defined with the Aerospace sequence.
//	// See Sebastian O.H. Madwick report 
//	// "An efficient orientation filter for inertial and intertial/magnetic sensor arrays" Chapter 2 Quaternion representation
//	public void AHRS::getEuler(float * angles) {
//	  float q[4]; // quaternion
//	  getQ(q);
//	  angles[0] = atan2(2 * q[1] * q[2] - 2 * q[0] * q[3], 2 * q[0]*q[0] + 2 * q[1] * q[1] - 1) * 180/M_PI; // psi
//	  angles[1] = -asin(2 * q[1] * q[3] + 2 * q[0] * q[2]) * 180/M_PI; // theta
//	  angles[2] = atan2(2 * q[2] * q[3] - 2 * q[0] * q[1], 2 * q[0] * q[0] + 2 * q[3] * q[3] - 1) * 180/M_PI; // phi
//	}
//
//	public void AHRS::getYawPitchRoll(float * ypr) {
//	  float q[4]; // quaternion
//	  float gx, gy, gz; // estimated gravity direction
//	  getQ(q);
//	  
//	  gx = 2 * (q[1]*q[3] - q[0]*q[2]);
//	  gy = 2 * (q[0]*q[1] + q[2]*q[3]);
//	  gz = q[0]*q[0] - q[1]*q[1] - q[2]*q[2] + q[3]*q[3];
//	  
//	  ypr[0] = atan2(2 * q[1] * q[2] - 2 * q[0] * q[3], 2 * q[0]*q[0] + 2 * q[1] * q[1] - 1) * 180/M_PI;
//	  ypr[1] = atan(gx / sqrt(gy*gy + gz*gz))  * 180/M_PI;
//	  ypr[2] = atan(gy / sqrt(gx*gx + gz*gz))  * 180/M_PI;
//	}
	
	public double getYaw() {
		return yaw;
	}

	public double getPitch() {
		return pitch;
	}

	public double getRoll() {
		return roll;
	}
    
}
