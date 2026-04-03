package com.shimmerresearch.usb;

public interface UsbDockChangeListener {
    void onUsbDeviceConnected();
    void onUsbDeviceDisconnected();
}