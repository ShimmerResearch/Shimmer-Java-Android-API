//! Windows only: the devices already connected to this machine that expose a given service.
//!
//! A connected device does not advertise, so a scan never sees it; Windows, for one, keeps a link
//! open after the process that owned it is killed. btleplug 0.13.3 can look these up by service,
//! but on Windows its lookup fails outright if any connected device cannot be opened as a BLE
//! device ("The provided device ID is not a valid BluetoothLEDevice object", HRESULT 0x80070057),
//! which an ordinary PC's other Bluetooth devices are enough to cause. This does the same
//! enumeration and skips such devices. The caller then retrieves each match from btleplug by
//! address, which btleplug does device by device.
//!
//! It also returns each device's name, which btleplug does not know for a device it never saw
//! advertise, and by which the Java side recognises a Shimmer.
//!
//! And it waits, without touching the device, for Windows to drop such a link (see
//! engine::open_link).

use std::future::IntoFuture;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

use uuid::Uuid;
use windows::Devices::Bluetooth::Advertisement::{
    BluetoothLEAdvertisementReceivedEventArgs, BluetoothLEAdvertisementWatcher, BluetoothLEScanningMode,
};
use windows::Devices::Bluetooth::GenericAttributeProfile::GattCommunicationStatus;
use windows::Devices::Bluetooth::{BluetoothCacheMode, BluetoothConnectionStatus, BluetoothLEDevice};
use windows::Devices::Enumeration::DeviceInformation;
use windows::Foundation::TypedEventHandler;

/// As btleplug's: a stale device's cached service lookup has been seen to hang for tens of seconds.
const SERVICES_TIMEOUT: Duration = Duration::from_secs(5);

/// (Bluetooth address, name) of each connected device that exposes any of `services`.
pub async fn connected_with_services(services: &[Uuid]) -> Result<Vec<(u64, String)>, String> {
    let selector = BluetoothLEDevice::GetDeviceSelectorFromConnectionStatus(BluetoothConnectionStatus::Connected)
        .map_err(|e| format!("connected-device selector: {}", e))?;
    let devices = DeviceInformation::FindAllAsyncAqsFilter(&selector)
        .map_err(|e| format!("enumerate connected devices: {}", e))?
        .into_future()
        .await
        .map_err(|e| format!("enumerate connected devices: {}", e))?;
    // Concurrently, as btleplug does, so stale devices cost about one timeout in total.
    let lookups = devices.into_iter().map(|info| async move { exposing(&info, services).await });
    Ok(futures::future::join_all(lookups).await.into_iter().flatten().collect())
}

/// The device's address and name if it exposes any of `services`; None if it does not, or if it
/// cannot be opened. Every WinRT object opened here is closed again, so that nothing is left
/// holding the device or its services when btleplug connects.
async fn exposing(info: &DeviceInformation, services: &[Uuid]) -> Option<(u64, String)> {
    let id = info.Id().ok()?;
    let device = BluetoothLEDevice::FromIdAsync(&id).ok()?.into_future().await.ok()?;
    let mut matched = false;
    if let Ok(lookup) = device.GetGattServicesWithCacheModeAsync(BluetoothCacheMode::Cached) {
        match tokio::time::timeout(SERVICES_TIMEOUT, lookup.clone().into_future()).await {
            Ok(Ok(result)) => {
                let succeeded = matches!(result.Status(), Ok(GattCommunicationStatus::Success));
                for service in result.Services().into_iter().flatten() {
                    if let Ok(guid) = service.Uuid() {
                        matched |= succeeded && services.contains(&Uuid::from_u128(guid.to_u128()));
                    }
                    let _ = service.Close();
                }
            }
            Ok(Err(_)) => {}
            Err(_) => {
                let _ = lookup.Cancel();
            }
        }
    }
    let found = if matched {
        let name = device.Name().map(|n| n.to_string()).unwrap_or_default();
        device.BluetoothAddress().ok().map(|address| (address, name))
    } else {
        None
    };
    let _ = device.Close();
    found
}

/// Waits until `address` advertises, which a device does once Windows drops its link to it.
/// Listens only: any use of the device, even opening it to ask about its link, keeps a link
/// that nobody else holds from being dropped. The caller bounds the wait.
pub async fn wait_for_advertisement(address: u64) -> Result<(), String> {
    let seen = Arc::new(AtomicBool::new(false));
    let watcher = BluetoothLEAdvertisementWatcher::new().map_err(|e| format!("watcher: {}", e))?;
    let _ = watcher.SetScanningMode(BluetoothLEScanningMode::Passive);
    let flag = seen.clone();
    let handler = TypedEventHandler::<BluetoothLEAdvertisementWatcher, BluetoothLEAdvertisementReceivedEventArgs>::new(
        move |_, args| {
            if let Some(args) = args.as_ref() {
                if args.BluetoothAddress().ok() == Some(address) {
                    flag.store(true, Ordering::SeqCst);
                }
            }
            Ok(())
        },
    );
    let token = watcher.Received(&handler).map_err(|e| format!("watcher: {}", e))?;
    watcher.Start().map_err(|e| format!("watcher start: {}", e))?;
    // Stopped however this ends, the caller's timeout included.
    let _stop = StopOnDrop { watcher: &watcher, token };
    while !seen.load(Ordering::SeqCst) {
        tokio::time::sleep(ADVERTISEMENT_POLL).await;
    }
    Ok(())
}

const ADVERTISEMENT_POLL: Duration = Duration::from_millis(100);

struct StopOnDrop<'a> {
    watcher: &'a BluetoothLEAdvertisementWatcher,
    token: i64,
}

impl Drop for StopOnDrop<'_> {
    fn drop(&mut self) {
        let _ = self.watcher.Stop();
        let _ = self.watcher.RemoveReceived(self.token);
    }
}
