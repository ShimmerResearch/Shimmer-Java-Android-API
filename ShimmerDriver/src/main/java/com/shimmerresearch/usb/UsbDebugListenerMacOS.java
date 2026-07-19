package com.shimmerresearch.usb;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;

public class UsbDebugListenerMacOS extends UsbDebugListener {

    private static final String kIOUSBDeviceClassName = "IOUSBDevice";
    private static final String kIOFirstMatchNotification = "IOServiceFirstMatch";
    private static final String kIOTerminatedNotification = "IOServiceTerminate";

    private volatile boolean running = false;
    private Thread listenerThread;
    private final UsbDockChangeListener listener;

    private IOKit.IONotificationPortRef notificationPort;
    private CoreFoundation.CFRunLoopRef runLoop;

    private int addedIterator = 0;
    private int removedIterator = 0;
    private boolean suppressInitialDeviceScan = true;

    // Held as fields so the JNA callbacks are not garbage-collected while the notifications
    // are armed (a collected callback would crash the native run loop).
    private IOKit.IOServiceMatchingCallback addCallback;
    private IOKit.IOServiceMatchingCallback removeCallback;

    public UsbDebugListenerMacOS(UsbDockChangeListener listener) {
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) {
            System.out.println("[DEBUG] macOS USB listener already running.");
            return;
        }

        running = true;

        listenerThread = new Thread(() -> {
            try {
                runNotificationLoop();
            } catch (Throwable t) {
                // Catch Throwable (not just Exception) so native-load failures such as
                // UnsatisfiedLinkError/ExceptionInInitializerError degrade gracefully to
                // polling instead of killing the thread with an unhandled error.
                t.printStackTrace();
            } finally {
                running = false;
                cleanup();
                System.out.println("[DEBUG] macOS USB listener thread finished.");
            }
        }, "UsbDebugListenerMacOS");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        System.out.println("[DEBUG] Stopping macOS USB listener...");
        running = false;

        if (runLoop != null) {
            CoreFoundation.INSTANCE.CFRunLoopStop(runLoop);
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void runNotificationLoop() {
        System.out.println("[DEBUG] Starting macOS USB debug listener...");

        runLoop = CoreFoundation.INSTANCE.CFRunLoopGetCurrent();
        if (runLoop == null) {
            throw new RuntimeException("CFRunLoopGetCurrent returned null");
        }

        // IOMainPort returns a kern_return_t and writes the mach port into the out-parameter.
        // The port (not the return code) must be passed to IONotificationPortCreate.
        IntByReference mainPortRef = new IntByReference();
        int mainPortResult = IOKit.INSTANCE.IOMainPort(0, mainPortRef);
        System.out.println("[DEBUG] IOMainPort result = " + mainPortResult);
        if (mainPortResult != 0) {
            throw new RuntimeException("IOMainPort failed: " + mainPortResult);
        }
        int mainPort = mainPortRef.getValue();

        notificationPort = IOKit.INSTANCE.IONotificationPortCreate(mainPort);
        if (notificationPort == null) {
            throw new RuntimeException("IONotificationPortCreate failed");
        }

        Pointer runLoopSource = IOKit.INSTANCE.IONotificationPortGetRunLoopSource(notificationPort);
        if (runLoopSource == null) {
            throw new RuntimeException("IONotificationPortGetRunLoopSource returned null");
        }

        if (CoreFoundation.kCFRunLoopDefaultMode == null) {
            throw new RuntimeException("kCFRunLoopDefaultMode is null");
        }

        CoreFoundation.INSTANCE.CFRunLoopAddSource(
                runLoop,
                runLoopSource,
                CoreFoundation.kCFRunLoopDefaultMode
        );

        Pointer matchingDictAdd = IOKit.INSTANCE.IOServiceMatching(kIOUSBDeviceClassName);
        if (matchingDictAdd == null) {
            throw new RuntimeException("IOServiceMatching(IOUSBDevice) failed for add");
        }

        Pointer matchingDictRemove = IOKit.INSTANCE.IOServiceMatching(kIOUSBDeviceClassName);
        if (matchingDictRemove == null) {
            throw new RuntimeException("IOServiceMatching(IOUSBDevice) failed for remove");
        }

        addCallback = (refCon, iterator) -> {
            System.out.println("[DEBUG] Device arrival callback triggered");
            drainIterator(iterator, true, false);
        };

        removeCallback = (refCon, iterator) -> {
            System.out.println("[DEBUG] Device removal callback triggered");
            drainIterator(iterator, false, false);
        };

        IntByReference addIterRef = new IntByReference();
        int kr = IOKit.INSTANCE.IOServiceAddMatchingNotification(
                notificationPort,
                kIOFirstMatchNotification,
                matchingDictAdd,
                addCallback,
                null,
                addIterRef
        );
        System.out.println("[DEBUG] IOServiceAddMatchingNotification(add) = " + kr);
        if (kr != 0) {
            throw new RuntimeException("IOServiceAddMatchingNotification(add) failed: " + kr);
        }
        addedIterator = addIterRef.getValue();

        IntByReference removeIterRef = new IntByReference();
        kr = IOKit.INSTANCE.IOServiceAddMatchingNotification(
                notificationPort,
                kIOTerminatedNotification,
                matchingDictRemove,
                removeCallback,
                null,
                removeIterRef
        );
        System.out.println("[DEBUG] IOServiceAddMatchingNotification(remove) = " + kr);
        if (kr != 0) {
            throw new RuntimeException("IOServiceAddMatchingNotification(remove) failed: " + kr);
        }
        removedIterator = removeIterRef.getValue();

        // Drain existing devices once to arm notifications.
        // Usually you do not want currently-connected devices to be treated as "new arrivals".
        drainIterator(addedIterator, true, suppressInitialDeviceScan);
        drainIterator(removedIterator, false, true);

        System.out.println("[DEBUG] Entering CFRunLoop...");
        CoreFoundation.INSTANCE.CFRunLoopRun();
        System.out.println("[DEBUG] CFRunLoop exited.");
    }

    private void drainIterator(int iterator, boolean connected, boolean suppressEvents) {
        while (true) {
            int service = IOKit.INSTANCE.IOIteratorNext(iterator);
            if (service == 0) {
                break;
            }

            try {
                if (!suppressEvents) {
                    if (connected) {
                        System.out.println("[EVENT] Device connected");
                        if (listener != null) {
                            listener.onUsbDeviceConnected();
                        }
                    } else {
                        System.out.println("[EVENT] Device disconnected");
                        if (listener != null) {
                            listener.onUsbDeviceDisconnected();
                        }
                    }
                } else {
                    System.out.println("[DEBUG] Suppressed initial device event");
                }
            } finally {
                IOKit.INSTANCE.IOObjectRelease(service);
            }
        }
    }

    private void cleanup() {
        if (addedIterator != 0) {
            IOKit.INSTANCE.IOObjectRelease(addedIterator);
            addedIterator = 0;
        }

        if (removedIterator != 0) {
            IOKit.INSTANCE.IOObjectRelease(removedIterator);
            removedIterator = 0;
        }

        if (notificationPort != null) {
            IOKit.INSTANCE.IONotificationPortDestroy(notificationPort);
            notificationPort = null;
        }

        runLoop = null;
        System.out.println("[DEBUG] macOS USB listener cleaned up.");
    }

    interface CoreFoundation extends Library {
        CoreFoundation INSTANCE = Native.load("CoreFoundation", CoreFoundation.class);

        class CFRunLoopRef extends PointerType {}

        Pointer kCFRunLoopDefaultMode = NativeLibrary
                .getInstance("CoreFoundation")
                .getGlobalVariableAddress("kCFRunLoopDefaultMode")
                .getPointer(0);

        CFRunLoopRef CFRunLoopGetCurrent();
        void CFRunLoopRun();
        void CFRunLoopStop(CFRunLoopRef rl);
        void CFRunLoopAddSource(CFRunLoopRef rl, Pointer source, Pointer mode);
    }

    interface IOKit extends Library {
        IOKit INSTANCE = Native.load("IOKit", IOKit.class);

        class IONotificationPortRef extends PointerType {}

        interface IOServiceMatchingCallback extends Callback {
            void invoke(Pointer refCon, int iterator);
        }

        int IOMainPort(int bootstrapPort, IntByReference mainPort);

        IONotificationPortRef IONotificationPortCreate(int masterPort);
        Pointer IONotificationPortGetRunLoopSource(IONotificationPortRef notifyPort);
        void IONotificationPortDestroy(IONotificationPortRef notifyPort);

        Pointer IOServiceMatching(String name);

        int IOServiceAddMatchingNotification(
                IONotificationPortRef notifyPort,
                String notificationType,
                Pointer matchingDictionary,
                IOServiceMatchingCallback callback,
                Pointer refCon,
                IntByReference notificationIterator
        );

        int IOIteratorNext(int iterator);
        int IOObjectRelease(int object);
    }
}