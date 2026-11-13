# Bike Mileage Pro – Flutter functional update

This update prioritizes vehicle, GPS trip, fuel/mileage and Bluetooth LE discovery. Map rendering has been removed from the app flow as requested; saved trip GPS points remain stored locally for future use.

## Implemented in this update

- Vehicle add, edit and delete with input validation and local SQLite persistence.
- Foreground GPS trip recording while the app and trip screen remain active. GPS permission and location-service checks show actionable errors.
- Trip history with saved vehicle, date, distance, estimated fuel use/cost, GPS point count, detail view and delete.
- Fuel entry with date, odometer, litres, amount and full/partial fill-up selection.
- Measured mileage using the full-tank method: odometer distance between consecutive full-tank records divided by all litres added between those records (including partial fill-ups).
- Dashboard shows measured full-tank mileage, saved GPS trip distance, recorded fuel expenses and vehicle count.
- Bluetooth LE device discovery screen. It lists nearby BLE advertisements and RSSI; this is discovery only, not a paired OBD connection or live ECU data reader.
- Material icons are provided by Flutter's bundled Material icon font (`uses-material-design: true`).
- SQLite schema version 2 adds query indexes while retaining existing records.

## Physical testing steps

1. Install the release APK on a real Android phone.
2. Add a vehicle (e.g. Honda CB350) and save it. Edit it and confirm the changes remain after closing/reopening the app.
3. Enable Android Location/GPS. Start a trip outdoors with a clear sky view, allow location permission, keep the app open for a short test ride, then stop and save. Confirm distance is greater than zero and the trip appears in history.
4. Open a trip to inspect its saved details. Trip fuel use/cost is explicitly an estimate based on the vehicle's reference km/L.
5. Add a full-tank fuel record at an odometer reading. Add one or more partial records, then a second full-tank record at a higher odometer. The measured mileage should use the odometer difference divided by litres added after the first full tank through the second full tank.
6. Open Vehicles → Scan Bluetooth devices, enable Bluetooth and grant Nearby devices permission. Test with a powered BLE device nearby. Classic Bluetooth-only devices may not appear in BLE scan results.
7. Pull down on Home to refresh dashboard totals.

## Important limitations (not represented as complete)

- GPS tracking in this version is foreground-only. Keep the app open and trip screen active during physical testing. It does not claim reliable tracking after the app is backgrounded or killed.
- Bluetooth scan discovers BLE devices only. It does not pair, connect to, or decode ELM327/OBD-II vehicle telemetry.
- Map display is intentionally excluded per request.
- This source ZIP has not been compiled on a Flutter SDK in this environment. GitHub Actions must pass `flutter pub get`, `flutter analyze`, `flutter test`, and `flutter build apk --release` before treating the APK as build-verified.
