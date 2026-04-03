package com.shimmerresearch.usb;

import com.sun.jna.platform.win32.DBT;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.ATOM;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;

public class UsbDebugListenerWindows extends UsbDebugListener {

    private volatile boolean running = false;
    private HWND hwnd;
    private Thread listenerThread;
    private final UsbDockChangeListener listener;

    public UsbDebugListenerWindows(UsbDockChangeListener listener) {
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) {
            System.out.println("[DEBUG] USB listener already running.");
            return;
        }

        running = true;

        listenerThread = new Thread(() -> {
            try {
                runMessageLoop();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                running = false;
                System.out.println("[DEBUG] USB listener thread finished.");
            }
        }, "UsbDebugListenerWindows");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (hwnd != null) {
            System.out.println("[DEBUG] Posting WM_CLOSE...");
            User32.INSTANCE.PostMessage(hwnd, WinUser.WM_CLOSE, null, null);
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void runMessageLoop() {
        System.out.println("[DEBUG] Starting USB debug listener...");

        HMODULE hInstance = Kernel32.INSTANCE.GetModuleHandle("");
        System.out.println("[DEBUG] hInstance = " + hInstance);

        String className = "UsbDebugListenerWindow_" + System.currentTimeMillis();

        WindowProc wndProc = new WindowProc() {
            @Override
            public LRESULT callback(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam) {
                if (uMsg == WinUser.WM_CREATE) {
                    System.out.println("[DEBUG] WM_CREATE received");
                } 
                else if (uMsg == WinUser.WM_DEVICECHANGE) {
                    int eventType = wParam.intValue();
                    System.out.println("[DEBUG] WM_DEVICECHANGE received, wParam=" + eventType + ", lParam=" + lParam);

                    if (eventType == DBT.DBT_DEVICEARRIVAL) {
                        System.out.println("[EVENT] Device connected");
                        if (listener != null) {
                            listener.onUsbDeviceConnected();
                        }
                    } 
                    else if (eventType == DBT.DBT_DEVICEREMOVECOMPLETE) {
                        System.out.println("[EVENT] Device disconnected");
                        if (listener != null) {
                            listener.onUsbDeviceDisconnected();
                        }
                    } 
                    else {
                        System.out.println("[DEBUG] Other device event type: " + eventType);
                    }
                    return new LRESULT(1);
                } 
                else if (uMsg == WinUser.WM_CLOSE) {
                    System.out.println("[DEBUG] WM_CLOSE received");
                    User32.INSTANCE.DestroyWindow(hWnd);
                    return new LRESULT(0);
                } 
                else if (uMsg == WinUser.WM_DESTROY) {
                    System.out.println("[DEBUG] WM_DESTROY received");
                    User32.INSTANCE.PostQuitMessage(0);
                    return new LRESULT(0);
                }

                return User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam);
            }
        };

        WNDCLASSEX wc = new WNDCLASSEX();
        wc.cbSize = wc.size();
        wc.hInstance = hInstance;
        wc.lpfnWndProc = wndProc;
        wc.lpszClassName = className;

        System.out.println("[DEBUG] Registering window class...");
        ATOM atom = User32.INSTANCE.RegisterClassEx(wc);
        int registerErr = Kernel32.INSTANCE.GetLastError();
        System.out.println("[DEBUG] RegisterClassEx atom = " + atom + ", lastError = " + registerErr);

        if (atom == null || atom.intValue() == 0) {
            throw new RuntimeException("RegisterClassEx failed, GetLastError=" + registerErr);
        }

        System.out.println("[DEBUG] Creating hidden window...");
        hwnd = User32.INSTANCE.CreateWindowEx(
                0,
                className,
                "USB Debug Hidden Window",
                0,
                0, 0, 0, 0,
                null,
                null,
                hInstance,
                null
        );

        int createErr = Kernel32.INSTANCE.GetLastError();
        System.out.println("[DEBUG] hwnd = " + hwnd + ", lastError = " + createErr);

        if (hwnd == null) {
            throw new RuntimeException("CreateWindowEx failed, GetLastError=" + createErr);
        }

        System.out.println("[DEBUG] Entering message loop...");
        MSG msg = new MSG();

        while (running) {
            int result = User32.INSTANCE.GetMessage(msg, null, 0, 0);
            System.out.println("[DEBUG] GetMessage returned: " + result);

            if (result == -1) {
                int err = Kernel32.INSTANCE.GetLastError();
                throw new RuntimeException("GetMessage failed, GetLastError=" + err);
            } 
            else if (result == 0) {
                System.out.println("[DEBUG] WM_QUIT received, exiting loop.");
                break;
            } 
            else {
                System.out.println("[DEBUG] Dispatching message: " + msg.message);
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }
        }

        System.out.println("[DEBUG] Listener stopped.");
    }
}