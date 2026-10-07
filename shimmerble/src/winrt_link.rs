//! Windows only: a short connection interval for links whose data arrives as indications.
//!
//! When a characteristic offers indications, btleplug 0.13.3 on Windows subscribes with them,
//! even if it offers notifications too (CoreBluetooth and BlueZ choose notifications). Each
//! indication is acknowledged before the next can be sent, so the rate is bounded by the
//! connection interval. The Shimmer3's RN4678 offers both on its data characteristic, and at
//! Windows' default 60 ms interval it cannot sustain 51.2 Hz over indications: it falls behind
//! and drops whole buffers (measured 75-94% received). At 15 ms it keeps up (100%, 33 ms behind).
//!
//! Switching the subscription to notifications instead is not possible from here: btleplug's
//! service object holds the service exclusively, so a second one gets a sharing violation. The
//! connection parameters belong to the device, not the service, so they can be requested. The
//! request lasts while it is held, so it is kept for the life of the connection. It needs
//! Windows 11; elsewhere it fails and the link stays on Windows' parameters.

use std::future::IntoFuture;

use windows::Devices::Bluetooth::{
    BluetoothLEDevice, BluetoothLEPreferredConnectionParameters, BluetoothLEPreferredConnectionParametersRequest,
    BluetoothLEPreferredConnectionParametersRequestStatus,
};

/// A held request for throughput-optimized connection parameters; released when dropped.
pub struct FastLink {
    device: BluetoothLEDevice,
    request: BluetoothLEPreferredConnectionParametersRequest,
}

impl Drop for FastLink {
    fn drop(&mut self) {
        let _ = self.request.Close();
        let _ = self.device.Close();
    }
}

/// Requests Windows' throughput-optimized parameters (15 ms interval) on the link to `address`.
pub async fn request_throughput(address: u64) -> Result<FastLink, String> {
    let device = BluetoothLEDevice::FromBluetoothAddressAsync(address)
        .map_err(|e| format!("open device: {}", e))?
        .into_future()
        .await
        .map_err(|e| format!("open device: {}", e))?;
    let parameters = BluetoothLEPreferredConnectionParameters::ThroughputOptimized().map_err(|e| e.to_string())?;
    let request = device.RequestPreferredConnectionParameters(&parameters).map_err(|e| format!("request: {}", e))?;
    let status = request.Status().map_err(|e| format!("request status: {}", e))?;
    if status != BluetoothLEPreferredConnectionParametersRequestStatus::Success {
        return Err(format!("request status {:?}", status));
    }
    Ok(FastLink { device, request })
}
