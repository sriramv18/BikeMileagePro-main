# Bike Mileage Pro v1.0.5 — Bluetooth OBD capability diagnostics

## Bluetooth behavior
- Scans BLE devices, including unnamed/generic BLE peripherals.
- Connects through GATT and discovers known UART services (Nordic UART, HM-10) or a generic writable + notifiable characteristic pair.
- Sends ELM initialization commands and checks whether the bike ECU answers standard OBD-II PID 0100.
- If the ECU responds, probes PID 012F (fuel tank level), PID 015E (engine fuel rate), and PID 01A6 (odometer).
- Reports supported vs unsupported/no-response readings in the connection status. It does not invent readings or add an adapter as a vehicle.

## Important compatibility limits
- This release's in-app scanner/connect flow is BLE GATT. A Classic Bluetooth SPP adapter paired in Android Settings is not connectable through this BLE screen.
- A generic BLE device can connect at the radio/GATT level, but it must expose a serial-like writable + notify/indicate characteristic pair and speak ELM327 commands to provide OBD data.
- OBD PID support depends on the adapter and the motorcycle ECU. Many motorcycles do not expose standard OBD-II odometer or fuel PIDs. A successful Bluetooth connection does not guarantee ECU data.
- PID 012F is fuel tank level percentage, not litres filled at a petrol station. PID 015E is engine fuel rate, not a fuel-fill receipt. Fuel mileage reports still require correct fuel-fill and odometer records unless a validated ECU odometer feed is implemented for the specific bike.
- Unsupported PIDs are reported as unavailable; manual GPS trip and fuel-entry workflows remain available.

## Build
Use the included GitHub Actions workflow to build the debug APK. Source/ZIP structure was checked in this environment; a full Gradle build and physical adapter/bike test were not run here.


## v1.0.6 automatic trip start/stop

- Automatically starts a GPS trip when location permission is granted, GPS is enabled, and the BLE OBD adapter reaches ADAPTER_READY or ECU_READY.
- Automatically stops and saves the active trip when the BLE OBD connection reports DISCONNECTED or ERROR.
- Automatically stops and saves the active trip when Android reports the GPS provider disabled.
- Requires the app to have been opened and the BLE adapter connected at least once; this update does not implement an always-on background BLE scanner or Bluetooth radio-off receiver.
- Uses the currently selected vehicle, falling back to the first saved vehicle.

Build validation: source and ZIP structure checked only. Android Gradle compilation and physical-device testing were not performed in this environment.


## v1.1.0 — Mileage & chart reliability
- Adds the missing Trips chart metric used by the dashboard selector and chart data flow.
- Keeps full-tank-to-full-tank mileage with partial refills included between full fills.
- Filters invalid fuel rows from mileage calculations and adds regression tests.
- Version code 18 / version name 1.1.0.

Mileage requires two full-tank entries with increasing odometer readings. The first full-tank entry is a baseline; partial fills are included in the next interval, but a partial fill is not itself a mileage result.
