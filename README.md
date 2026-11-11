# Bike Mileage Pro v0.4.0 — Premium Compose UI

Native Android app using Kotlin, Jetpack Compose and Material 3 with a premium dark navy + teal visual theme.

## Screens
- Dashboard: overall full-tank km/L, total GPS trip distance, fuel spend, last odometer, mileage trend chart and live trip snapshot.
- Vehicle: editable Honda H’ness CB350 DLX Pro profile and nearby BLE discovery.
- Trips: foreground GPS tracking, live distance/speed/duration and saved trip history.
- Fuel: fill-up date, odometer, litres, amount, full/partial tank and fill-up history.

## Mileage calculation
Mileage is calculated between consecutive full-tank entries using the odometer distance divided by litres added at the current full-tank refill. Keep the same pump and stop at the automatic cut-off for best consistency. Partial fills are stored but excluded as mileage intervals.

## GPS
Trip tracking uses phone GPS and a foreground location service. GPS distance/speed are estimates and can differ from the motorcycle odometer. Allow location permission and enable GPS.

## Bluetooth limitation
The app currently discovers nearby BLE advertisements only. It does not yet read Honda ECU values. Live ECU data requires a compatible diagnostic adapter, confirmed pinout/protocol for the exact motorcycle, and an implemented adapter protocol.

## Build
Push to main/master or manually run the GitHub Actions workflow. Download the `BikeMileagePro-v0.4.0-premium-debug-apk` artifact from a successful run.
