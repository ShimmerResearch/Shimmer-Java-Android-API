package com.shimmerresearch.usb;

public class UsbDebugListenerMacOS extends UsbDebugListener {

	///TODO: implement this class for macOS 
	public UsbDebugListenerMacOS(UsbDockChangeListener listener) {
		this.listener = listener;
	}

	@Override
	public void start() {
		System.out.println("[DEBUG] USB listener for macOS is not implemented yet.");
	}

	@Override
	public void stop() {
		System.out.println("[DEBUG] USB listener for macOS is not implemented yet.");
	}

}
