# Native Android integration work

The TypeScript project deliberately defines a native bridge boundary. The following Kotlin implementation is required in the generated Android host app.

## Foreground GPS service
- Request foreground location permission and show an ongoing notification while recording.
- Android 14+ foreground service type: `location`; declare the appropriate permissions.
- Persist active trip ID, start time and last accepted GPS fix.
- Reject inaccurate fixes (initial policy: accuracy worse than 35 m), impossible jumps and stationary drift.
- Use elapsed realtime / timestamps to avoid duplicate points.
- Broadcast or emit trip state, GPS status, distance and speed to React Native.
- Recover an active trip after process death; never silently create a second trip.

## Bluetooth
- Support BLE scanning/GATT and Bluetooth Classic RFCOMM separately.
- Request Android 12+ BLUETOOTH_SCAN and BLUETOOTH_CONNECT permissions as applicable.
- Pair/connect only after the user selects a device.
- Normal hands-free/audio Bluetooth is not OBD telemetry.
- Connection state alone must never mean engine running.

## OBD-II
- Implement ELM327 command transport with adapter-specific timeouts and response parsing.
- Probe supported PIDs before enabling telemetry.
- RPM PID 010C and vehicle speed PID 010D are common standard PIDs, but availability is vehicle-specific.
- Treat ignition/engine-running as unknown unless reliably established.
- Never claim odometer is available unless the vehicle exposes a supported value.

## iOS later
- Implement CoreLocation background location and CoreBluetooth separately.
- Validate background execution, permissions and supported Bluetooth profiles on physical devices.
