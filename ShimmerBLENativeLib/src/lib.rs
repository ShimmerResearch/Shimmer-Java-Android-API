//! JNI entry points for `com.shimmerresearch.driver.ble.nativeble.NativeBle`.
//!
//! The exported names are derived from that Java class's package and name, so renaming or moving
//! `NativeBle.java` means renaming every function here. The same applies to the `NativeBleEvent`
//! constructor signature in [`EVENT_CTOR_SIG`].
//!
//! Every entry point runs inside [`guard`]: an error or a panic is turned into a
//! `NativeBleException` on the Java side, never an abort of the JVM.

pub mod engine;
#[cfg(target_os = "windows")]
mod winrt_link;

use std::panic::{self, AssertUnwindSafe};
use std::sync::{Mutex, OnceLock};
use std::time::Duration;

use jni::objects::{JByteArray, JClass, JObject, JObjectArray, JString, JValue};
use jni::sys::{jint, jlong, jobject, jstring};
use jni::JNIEnv;
use uuid::Uuid;

use crate::engine::{BleCore, BleError, Event, Result};

const EXCEPTION_CLASS: &str = "com/shimmerresearch/driver/ble/nativeble/NativeBleException";
const EVENT_CLASS: &str = "com/shimmerresearch/driver/ble/nativeble/NativeBleEvent";
/// NativeBleEvent(int type, long handle, String id, String name, String address, int rssi,
///                byte[] data, String message)
const EVENT_CTOR_SIG: &str =
    "(IJLjava/lang/String;Ljava/lang/String;Ljava/lang/String;I[BLjava/lang/String;)V";

// Must match the TYPE_* constants in NativeBleEvent.java.
const EVENT_DEVICE_FOUND: jint = 1;
const EVENT_BYTES: jint = 2;
const EVENT_DISCONNECTED: jint = 3;
/// Sent to Java when the platform gives no RSSI. Must match NativeBleEvent.RSSI_UNKNOWN.
const RSSI_UNKNOWN: jint = jint::MIN;

static CORE: OnceLock<BleCore> = OnceLock::new();
static INIT_LOCK: Mutex<()> = Mutex::new(());

impl From<jni::errors::Error> for BleError {
    fn from(e: jni::errors::Error) -> Self {
        BleError(format!("JNI: {}", e))
    }
}

fn engine() -> Result<&'static BleCore> {
    CORE.get().ok_or_else(|| BleError("NativeBle.init() has not been called".into()))
}

/// Runs `f`, converting an error or a panic into a pending Java exception and returning `default`.
fn guard<T>(env: &mut JNIEnv, default: T, f: impl FnOnce(&mut JNIEnv) -> Result<T>) -> T {
    let outcome = panic::catch_unwind(AssertUnwindSafe(|| f(env)));
    let message = match outcome {
        Ok(Ok(value)) => return value,
        Ok(Err(e)) => e.0,
        Err(payload) => {
            let detail = payload
                .downcast_ref::<&str>()
                .map(|s| s.to_string())
                .or_else(|| payload.downcast_ref::<String>().cloned())
                .unwrap_or_else(|| "unknown panic".into());
            format!("native panic: {}", detail)
        }
    };
    // If a JNI call already left an exception pending, keep that one.
    if !env.exception_check().unwrap_or(true) {
        let _ = env.throw_new(EXCEPTION_CLASS, message);
    }
    default
}

fn java_string(env: &mut JNIEnv, s: &JString) -> Result<String> {
    Ok(env.get_string(s)?.into())
}

fn java_uuid(env: &mut JNIEnv, s: &JString) -> Result<Uuid> {
    let text = java_string(env, s)?;
    Uuid::parse_str(&text).map_err(|e| BleError(format!("bad UUID {:?}: {}", text, e)))
}

#[no_mangle]
pub extern "system" fn Java_com_shimmerresearch_driver_ble_nativeble_NativeBle_nativeVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    guard(&mut env, std::ptr::null_mut(), |env| {
        Ok(env.new_string(env!("CARGO_PKG_VERSION"))?.into_raw())
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shimmerresearch_driver_ble_nativeble_NativeBle_init(
    mut env: JNIEnv,
    _class: JClass,
) {
    guard(&mut env, (), |_| {
        let _serialise = INIT_LOCK.lock().unwrap_or_else(|p| p.into_inner());
        if CORE.get().is_none() {
            let _ = CORE.set(BleCore::new()?);
        }
        Ok(())
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shimmerresearch_driver_ble_nativeble_NativeBle_adapterState(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    guard(&mut env, std::ptr::null_mut(), |env| {
        let state = engine()?.adapter_state()?;
        Ok(env.new_string(state)?.into_raw())
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shimmerresearch_driver_ble_nativeble_NativeBle_startScan(
    mut env: JNIEnv,
    _class: JClass,
) {
    guard(&mut env, (), |_| engine()?.start_scan())
}

#[no_mangle]
pub extern "system" fn Java_com_shimmerresearch_driver_ble_nativeble_NativeBle_stopScan(
    mut env: JNIEnv,
    _class: JClass,
) {
    guard(&mut env, (), |_| engine()?.stop_scan())
}

/// Reports already-connected devices exposing any of `service_uuids` as DeviceFound events.
#[no_mangle]
pub extern "system" fn Java_com_shimmerresearch_driver_ble_nativeble_NativeBle_retrieveConnected(
    mut env: JNIEnv,
    _class: JClass,
    service_uuids: JObjectArray,
) -> jint {
    guard(&mut env, 0, |env| {
        let count = env.get_array_length(&service_uuids)?;
        let mut services = Vec::with_capacity(count as usize);
        for i in 0..count {
            let element = JString::from(env.get_object_array_element(&service_uuids, i)?);
            services.push(java_uuid(env, &element)?);
        }
        let found = engine()?.retrieve_connected(services)?;
        Ok(found as jint)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shimmerresearch_driver_ble_nativeble_NativeBle_connect(
    mut env: JNIEnv,
    _class: JClass,
    id: JString,
    service_uuid: JString,
    write_uuid: JString,
    notify_uuid: JString,
    timeout_ms: jint,
) -> jlong {
    guard(&mut env, 0, |env| {
        let id = java_string(env, &id)?;
        let service = java_uuid(env, &service_uuid)?;
        let write = java_uuid(env, &write_uuid)?;
        let notify = java_uuid(env, &notify_uuid)?;
        let timeout = Duration::from_millis(timeout_ms.max(0) as u64);
        engine()?.connect(&id, service, write, notify, timeout)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shimmerresearch_driver_ble_nativeble_NativeBle_write(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    data: JByteArray,
) {
    guard(&mut env, (), |env| {
        let bytes = env.convert_byte_array(&data)?;
        engine()?.write(handle, &bytes)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shimmerresearch_driver_ble_nativeble_NativeBle_mtu(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    guard(&mut env, 0, |_| Ok(jint::from(engine()?.mtu(handle))))
}

#[no_mangle]
pub extern "system" fn Java_com_shimmerresearch_driver_ble_nativeble_NativeBle_disconnect(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    guard(&mut env, (), |_| {
        engine()?.disconnect(handle);
        Ok(())
    })
}

/// Returns the next NativeBleEvent, or null if none arrived within `timeout_ms`.
#[no_mangle]
pub extern "system" fn Java_com_shimmerresearch_driver_ble_nativeble_NativeBle_nextEvent(
    mut env: JNIEnv,
    _class: JClass,
    timeout_ms: jint,
) -> jobject {
    guard(&mut env, std::ptr::null_mut(), |env| {
        let timeout = Duration::from_millis(timeout_ms.max(0) as u64);
        match engine()?.next_event(timeout) {
            Some(event) => to_java_event(env, event),
            None => Ok(std::ptr::null_mut()),
        }
    })
}

fn to_java_event(env: &mut JNIEnv, event: Event) -> Result<jobject> {
    let null = JObject::null();
    let (kind, handle, id, name, address, rssi, data, message) = match event {
        Event::DeviceFound { id, name, address, rssi } => (
            EVENT_DEVICE_FOUND,
            0,
            Some(id),
            Some(name),
            Some(address),
            rssi.map(jint::from).unwrap_or(RSSI_UNKNOWN),
            None,
            None,
        ),
        Event::Bytes { handle, data } => {
            (EVENT_BYTES, handle, None, None, None, RSSI_UNKNOWN, Some(data), None)
        }
        Event::Disconnected { handle, reason } => {
            (EVENT_DISCONNECTED, handle, None, None, None, RSSI_UNKNOWN, None, Some(reason))
        }
    };

    let id = optional_string(env, id)?;
    let name = optional_string(env, name)?;
    let address = optional_string(env, address)?;
    let message = optional_string(env, message)?;
    let data = match data {
        Some(bytes) => JObject::from(env.byte_array_from_slice(&bytes)?),
        None => null,
    };

    let event = env.new_object(
        EVENT_CLASS,
        EVENT_CTOR_SIG,
        &[
            JValue::Int(kind),
            JValue::Long(handle),
            JValue::Object(&id),
            JValue::Object(&name),
            JValue::Object(&address),
            JValue::Int(rssi),
            JValue::Object(&data),
            JValue::Object(&message),
        ],
    )?;
    Ok(event.into_raw())
}

fn optional_string<'local>(env: &mut JNIEnv<'local>, s: Option<String>) -> Result<JObject<'local>> {
    match s {
        Some(s) => Ok(JObject::from(env.new_string(s)?)),
        None => Ok(JObject::null()),
    }
}
