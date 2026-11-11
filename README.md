# Bike Mileage Pro v0.3.0

Native Android app for motorcycle trip tracking, fuel logs and mileage calculation.

## Screens
- Dashboard: overall full-tank mileage, total GPS trip distance, fuel spend and mileage chart.
- Vehicle: editable vehicle profile and BLE device discovery.
- Trips: foreground GPS trip start/stop, distance, speed, duration and trip history.
- Fuel & Odometer: date, odometer, litres, amount and full/partial tank records.

## Mileage method
Mileage is calculated only between consecutive full-tank entries:
(current full-tank odometer - previous full-tank odometer) / litres added at current fill.
For best accuracy, fill to the same automatic pump cut-off each time. Partial fills are saved but excluded from mileage calculations.

## Bluetooth limitation
The app currently discovers nearby BLE advertisements. It does not read Honda ECU values. Live ECU data needs a compatible diagnostic adapter, wiring/protocol confirmation for the exact motorcycle and an implemented adapter protocol.

## Build
GitHub Actions builds a debug APK on push to main/master or manual workflow dispatch. Download the `BikeMileagePro-v0.3.0-debug-apk` artifact from the successful workflow run.

## Permissions
Location is required for GPS trip tracking. Bluetooth and nearby-device permissions are used only for BLE discovery. Trip data is stored locally in SQLite.
