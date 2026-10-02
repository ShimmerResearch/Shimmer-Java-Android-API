//! Platform-neutral BLE engine over btleplug, exposed as a blocking API.
//!
//! The engine owns one tokio runtime and one adapter. Callers (the JNI layer, or the blecli tool)
//! call the blocking methods from their own threads and read results from a single event queue
//! with [`BleCore::next_event`]. Nothing here calls back into the caller.
//!
//! Device IDs are opaque strings: the MAC address on Windows and Linux, a CoreBluetooth UUID on
//! macOS. A device must have been seen by a scan before it can be connected to, because btleplug
//! cannot add a peripheral by address on Windows or macOS.

use std::collections::HashMap;
use std::fmt;
use std::sync::atomic::{AtomicBool, AtomicI64, Ordering};
use std::sync::mpsc::{self, Receiver, RecvTimeoutError, Sender};
use std::sync::{Arc, Mutex, MutexGuard};
use std::time::Duration;

use btleplug::api::{
    Central, CentralEvent, CharPropFlags, Characteristic, Manager as _, Peripheral as _,
    RetrievePeripheralsOptions, ScanFilter, WriteType,
};
use btleplug::platform::{Adapter, Manager, Peripheral, PeripheralId};
use futures::stream::StreamExt;
use tokio::runtime::Runtime;
use tokio::task::JoinHandle;
use uuid::Uuid;

/// Smallest ATT payload every BLE link supports (default MTU of 23, minus the 3-byte header).
const MIN_WRITE_CHUNK: usize = 20;
const DISCONNECT_TIMEOUT: Duration = Duration::from_secs(5);

#[derive(Debug, Clone)]
pub struct BleError(pub String);

impl fmt::Display for BleError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl std::error::Error for BleError {}

impl From<btleplug::Error> for BleError {
    fn from(e: btleplug::Error) -> Self {
        BleError(e.to_string())
    }
}

impl From<std::io::Error> for BleError {
    fn from(e: std::io::Error) -> Self {
        BleError(e.to_string())
    }
}

pub type Result<T> = std::result::Result<T, BleError>;

fn err<T>(msg: impl Into<String>) -> Result<T> {
    Err(BleError(msg.into()))
}

/// Everything the engine reports, in the order it happened.
#[derive(Debug, Clone)]
pub enum Event {
    /// A device was seen while scanning. Sent repeatedly as advertisements arrive, so consumers
    /// should de-duplicate by `id`. `address` is empty where the platform hides it (macOS).
    DeviceFound { id: String, name: String, address: String, rssi: Option<i16> },
    /// Bytes notified by the device on the subscribed characteristic.
    Bytes { handle: i64, data: Vec<u8> },
    /// The link dropped without the caller asking. Never sent for [`BleCore::disconnect`].
    Disconnected { handle: i64, reason: String },
}

struct Connection {
    id: String,
    peripheral: Peripheral,
    write_char: Characteristic,
    write_type: WriteType,
    pump: JoinHandle<()>,
}

struct Shared {
    tx: Sender<Event>,
    scanning: AtomicBool,
    discovered: Mutex<HashMap<String, PeripheralId>>,
    connections: Mutex<HashMap<i64, Connection>>,
}

impl Shared {
    fn send(&self, event: Event) {
        // The receiver lives as long as the engine, so a failed send only happens at shutdown.
        let _ = self.tx.send(event);
    }

    /// Removes a connection and reports it as lost. Returns false if it was already gone, which
    /// makes the several paths that notice a dropped link report it exactly once.
    fn drop_connection(&self, handle: i64, reason: &str) -> bool {
        let removed = lock(&self.connections).remove(&handle);
        match removed {
            Some(conn) => {
                conn.pump.abort();
                self.send(Event::Disconnected { handle, reason: reason.to_string() });
                true
            }
            None => false,
        }
    }
}

fn lock<T>(m: &Mutex<T>) -> MutexGuard<'_, T> {
    // A panic elsewhere must not wedge the engine: keep using the data.
    m.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

pub struct BleCore {
    rt: Runtime,
    // Held so the platform manager outlives the adapter it produced.
    _manager: Manager,
    adapter: Adapter,
    shared: Arc<Shared>,
    rx: Mutex<Receiver<Event>>,
    next_handle: AtomicI64,
}

impl BleCore {
    /// Starts the runtime and opens the first Bluetooth adapter.
    pub fn new() -> Result<BleCore> {
        let rt = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .thread_name("shimmerble")
            .enable_all()
            .build()?;

        let (manager, adapter) = rt.block_on(async {
            let manager = Manager::new().await?;
            let adapter = manager.adapters().await?.into_iter().next();
            match adapter {
                Some(adapter) => Ok((manager, adapter)),
                None => err("no Bluetooth adapter found"),
            }
        })?;

        let (tx, rx) = mpsc::channel();
        let shared = Arc::new(Shared {
            tx,
            scanning: AtomicBool::new(false),
            discovered: Mutex::new(HashMap::new()),
            connections: Mutex::new(HashMap::new()),
        });

        let events = rt.block_on(adapter.events())?;
        rt.spawn(adapter_event_pump(adapter.clone(), events, shared.clone()));

        Ok(BleCore {
            rt,
            _manager: manager,
            adapter,
            shared,
            rx: Mutex::new(rx),
            next_handle: AtomicI64::new(1),
        })
    }

    /// The adapter's power state as reported by the platform, e.g. "PoweredOn".
    pub fn adapter_state(&self) -> Result<String> {
        let state = self.rt.block_on(self.adapter.adapter_state())?;
        Ok(format!("{:?}", state))
    }

    pub fn start_scan(&self) -> Result<()> {
        self.shared.scanning.store(true, Ordering::SeqCst);
        self.rt.block_on(self.adapter.start_scan(ScanFilter::default()))?;
        Ok(())
    }

    pub fn stop_scan(&self) -> Result<()> {
        self.shared.scanning.store(false, Ordering::SeqCst);
        self.rt.block_on(self.adapter.stop_scan())?;
        Ok(())
    }

    /// Reports devices already connected to this machine that expose any of `services`, as
    /// [`Event::DeviceFound`], and makes them connectable. A connected device does not advertise,
    /// so a scan never sees it: for instance a link that Windows keeps open after the process
    /// that owned it was killed. Returns how many were found.
    pub fn retrieve_connected(&self, services: Vec<Uuid>) -> Result<usize> {
        let found = self.rt.block_on(self.retrieve(services))?;
        for peripheral in &found {
            self.rt.block_on(report_peripheral(&self.shared, peripheral));
        }
        Ok(found.len())
    }

    async fn retrieve(&self, services: Vec<Uuid>) -> Result<Vec<Peripheral>> {
        let options = RetrievePeripheralsOptions { identifiers: None, services: Some(services) };
        Ok(self.adapter.retrieve_peripherals(options).await?)
    }

    /// Looks up one device that is connected to this machine by its ID, and makes it
    /// connectable. Unlike the service lookup, this one skips devices it cannot open rather than
    /// failing (btleplug 0.13.3's Windows service lookup fails outright if any connected device
    /// cannot be opened as a BLE device).
    fn retrieve_by_id(&self, id: &str) -> Result<Option<Peripheral>> {
        let options =
            RetrievePeripheralsOptions { identifiers: Some(vec![peripheral_id_from(id)?]), services: None };
        let found = self.rt.block_on(self.adapter.retrieve_peripherals(options))?;
        let peripheral = found.into_iter().next();
        if let Some(p) = &peripheral {
            lock(&self.shared.discovered).insert(id.to_string(), p.id());
        }
        Ok(peripheral)
    }

    /// Connects, subscribes to `notify` and returns a handle for the other calls.
    /// On any failure the half-open link is torn down before the error is returned.
    pub fn connect(
        &self,
        id: &str,
        service: Uuid,
        write: Uuid,
        notify: Uuid,
        timeout: Duration,
    ) -> Result<i64> {
        let known = lock(&self.shared.discovered).get(id).cloned();
        let peripheral = match known {
            Some(pid) => self.rt.block_on(self.adapter.peripheral(&pid))?,
            // Not seen advertising: it may be connected already, and so not advertising.
            None => match self.retrieve_by_id(id)? {
                Some(p) => p,
                None => {
                    return err(format!(
                        "device {} is neither advertising nor connected to this machine",
                        id
                    ))
                }
            },
        };
        let handle = self.next_handle.fetch_add(1, Ordering::SeqCst);

        let attempt = self.block_on_timeout(
            timeout,
            open_link(&self.shared, &peripheral, id, handle, service, write, notify),
        );
        let result = match attempt {
            Ok(result) => result,
            Err(_) => err(format!("connect timed out after {} ms", timeout.as_millis())),
        };
        if result.is_err() {
            let _ = self.block_on_timeout(DISCONNECT_TIMEOUT, peripheral.disconnect());
        }
        result.map(|_| handle)
    }

    /// Runs `future` on the runtime with a time limit. The timer must be created inside the
    /// runtime: tokio panics if one is created on a caller's thread.
    fn block_on_timeout<F: std::future::Future>(
        &self,
        limit: Duration,
        future: F,
    ) -> std::result::Result<F::Output, tokio::time::error::Elapsed> {
        self.rt.block_on(async move { tokio::time::timeout(limit, future).await })
    }

    /// Writes `data`, split into chunks that fit the negotiated MTU.
    pub fn write(&self, handle: i64, data: &[u8]) -> Result<()> {
        let (peripheral, write_char, write_type) = {
            let connections = lock(&self.shared.connections);
            match connections.get(&handle) {
                Some(c) => (c.peripheral.clone(), c.write_char.clone(), c.write_type),
                None => return err(format!("handle {} is not connected", handle)),
            }
        };
        let chunk = (peripheral.mtu() as usize).saturating_sub(3).max(MIN_WRITE_CHUNK);
        self.rt.block_on(async {
            for part in data.chunks(chunk) {
                peripheral.write(&write_char, part, write_type).await?;
            }
            Ok(())
        })
    }

    /// The negotiated ATT MTU, or 0 if the handle is not connected.
    pub fn mtu(&self, handle: i64) -> u16 {
        lock(&self.shared.connections).get(&handle).map(|c| c.peripheral.mtu()).unwrap_or(0)
    }

    /// Closes the link. Does not emit [`Event::Disconnected`]: the caller already knows.
    pub fn disconnect(&self, handle: i64) {
        let removed = lock(&self.shared.connections).remove(&handle);
        if let Some(conn) = removed {
            conn.pump.abort();
            let _ = self.block_on_timeout(DISCONNECT_TIMEOUT, conn.peripheral.disconnect());
        }
    }

    /// Waits up to `timeout` for the next event.
    pub fn next_event(&self, timeout: Duration) -> Option<Event> {
        match lock(&self.rx).recv_timeout(timeout) {
            Ok(event) => Some(event),
            Err(RecvTimeoutError::Timeout) | Err(RecvTimeoutError::Disconnected) => None,
        }
    }
}

/// Parses a device ID the way this platform writes it: a MAC address on Windows (with or without
/// ':' separators), a CoreBluetooth UUID on macOS.
fn peripheral_id_from(id: &str) -> Result<PeripheralId> {
    #[cfg(target_os = "windows")]
    {
        use btleplug::api::BDAddr;
        let parsed = if id.contains(':') { BDAddr::from_str_delim(id) } else { BDAddr::from_str_no_delim(id) };
        parsed.map(PeripheralId::from).map_err(|e| BleError(format!("bad device ID {:?}: {}", id, e)))
    }
    #[cfg(target_vendor = "apple")]
    {
        Uuid::parse_str(id).map(PeripheralId::from).map_err(|e| BleError(format!("bad device ID {:?}: {}", id, e)))
    }
    #[cfg(not(any(target_os = "windows", target_vendor = "apple")))]
    {
        err(format!("cannot look up device {} by ID on this platform; scan for it first", id))
    }
}

async fn open_link(
    shared: &Arc<Shared>,
    peripheral: &Peripheral,
    id: &str,
    handle: i64,
    service: Uuid,
    write: Uuid,
    notify: Uuid,
) -> Result<()> {
    if !peripheral.is_connected().await? {
        peripheral.connect().await?;
    }
    peripheral.discover_services().await?;

    let characteristics = peripheral.characteristics();
    let find = |uuid: Uuid| {
        characteristics.iter().find(|c| c.uuid == uuid && c.service_uuid == service).cloned()
    };
    let write_char = match find(write) {
        Some(c) => c,
        None => return err(format!("write characteristic {} not found in service {}", write, service)),
    };
    let notify_char = match find(notify) {
        Some(c) => c,
        None => return err(format!("notify characteristic {} not found in service {}", notify, service)),
    };
    let write_type = if write_char.properties.contains(CharPropFlags::WRITE_WITHOUT_RESPONSE) {
        WriteType::WithoutResponse
    } else {
        WriteType::WithResponse
    };

    // Take the stream before subscribing so the first notification cannot be missed.
    let mut notifications = peripheral.notifications().await?;
    peripheral.subscribe(&notify_char).await?;

    let pump_shared = shared.clone();
    let pump = tokio::spawn(async move {
        while let Some(n) = notifications.next().await {
            if n.uuid == notify {
                pump_shared.send(Event::Bytes { handle, data: n.value });
            }
        }
        pump_shared.drop_connection(handle, "notification stream ended");
    });

    lock(&shared.connections).insert(
        handle,
        Connection { id: id.to_string(), peripheral: peripheral.clone(), write_char, write_type, pump },
    );
    Ok(())
}

async fn adapter_event_pump(
    adapter: Adapter,
    mut events: std::pin::Pin<Box<dyn futures::Stream<Item = CentralEvent> + Send>>,
    shared: Arc<Shared>,
) {
    while let Some(event) = events.next().await {
        match event {
            CentralEvent::DeviceDiscovered(pid) | CentralEvent::DeviceUpdated(pid) => {
                if shared.scanning.load(Ordering::SeqCst) {
                    report_device(&adapter, &shared, pid).await;
                }
            }
            CentralEvent::DeviceDisconnected(pid) => {
                let id = pid.to_string();
                let handles: Vec<i64> = lock(&shared.connections)
                    .iter()
                    .filter(|(_, c)| c.id == id)
                    .map(|(h, _)| *h)
                    .collect();
                for handle in handles {
                    shared.drop_connection(handle, "link lost");
                }
            }
            _ => {}
        }
    }
}

async fn report_device(adapter: &Adapter, shared: &Shared, pid: PeripheralId) {
    if let Ok(peripheral) = adapter.peripheral(&pid).await {
        report_peripheral(shared, &peripheral).await;
    }
}

async fn report_peripheral(shared: &Shared, peripheral: &Peripheral) {
    let props = match peripheral.properties().await {
        Ok(Some(props)) => props,
        _ => return,
    };
    let pid = peripheral.id();
    let id = pid.to_string();
    lock(&shared.discovered).insert(id.clone(), pid);

    let address = props.address.to_string();
    let address = if address == "00:00:00:00:00:00" { String::new() } else { address };
    let name = props.local_name.or(props.advertisement_name).unwrap_or_default();
    shared.send(Event::DeviceFound { id, name, address, rssi: props.rssi });
}
