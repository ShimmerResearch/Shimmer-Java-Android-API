package com.shimmerresearch.driver.ble.nativeble;

/** A failure reported by the native BLE library, or while loading it. */
public class NativeBleException extends Exception {

	private static final long serialVersionUID = 6017842284215432101L;

	public NativeBleException(String message) {
		super(message);
	}

	public NativeBleException(String message, Throwable cause) {
		super(message, cause);
	}
}
