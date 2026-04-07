package com.shimmerresearch.usb;

public abstract class UsbDebugListener {
	
	public abstract void start();
	public abstract void stop();
	protected UsbDockChangeListener listener = null;
	

}
