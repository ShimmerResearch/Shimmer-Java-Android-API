package com.shimmerresearch.usb;

import com.shimmerresearch.driverUtilities.UtilShimmer;

public class PlatformHwManagerUsbExample {

    private UsbDebugListener mUSBDebugListener;

    public void startWindowsUsbListener() {
    	if (UtilShimmer.isOsWindows()) {
    	    System.out.println("Running on Windows");
    	    mUSBDebugListener = new UsbDebugListenerWindows(new UsbDockChangeListener() {
                @Override
                public void onUsbDeviceConnected() {
                    System.out.println("[PLATFORM] USB connect event received");
                    
                }

                @Override
                public void onUsbDeviceDisconnected() {
                    System.out.println("[PLATFORM] USB disconnect event received");
                    
                }
            });

    	} else if (UtilShimmer.isOsMac()) {
    	    System.out.println("Running on macOS");
    	    mUSBDebugListener = new UsbDebugListenerMacOS(new UsbDockChangeListener() {
                @Override
                public void onUsbDeviceConnected() {
                    System.out.println("[PLATFORM] USB connect event received");
                    
                }

                @Override
                public void onUsbDeviceDisconnected() {
                    System.out.println("[PLATFORM] USB disconnect event received");
                    
                }
            });

    	} else {
    	    System.out.println("Other OS: " + System.getProperty("os.name"));
    	}
    	
    	
    	mUSBDebugListener.start();
    }

    public void stopWindowsUsbListener() {
        if (mUSBDebugListener != null) {
        	mUSBDebugListener.stop();
        	mUSBDebugListener = null;
        }
    }

    public static void main(String[] args) throws Exception {
        PlatformHwManagerUsbExample example = new PlatformHwManagerUsbExample();
        example.startWindowsUsbListener();

        System.out.println("[DEBUG] Press Enter to stop.");
        System.in.read();

        example.stopWindowsUsbListener();
        System.out.println("[DEBUG] Exiting.");
    }
}