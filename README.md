# Bike Mileage Pro — Native Android v0.5.0

This is the native Android Kotlin + Jetpack Compose project. The Flutter experiment is discontinued; use this project as the active Android codebase.

## Included functionality
- Native Jetpack Compose dark UI with working Material icons and bottom navigation.
- Dashboard with full-tank overall mileage, trip distance, fuel spend, odometer and mileage trend chart.
- Editable single-vehicle profile (name, year, registration, variant/colour and odometer).
- Phone GPS trip tracking through an Android foreground location service and persistent notification.
- Live trip distance, speed and elapsed duration updates while the service is running.
- Saved trip history in native SQLite.
- Fuel entry form with date, odometer, litres, amount and full/partial tank selection.
- Actual full-tank mileage intervals using odometer difference divided by fuel added between consecutive full-tank records; partial fills are included in the interval fuel total.
- Nearby Bluetooth Low Energy scan and device discovery.
- GitHub Actions workflow to build and upload an installable debug APK.

## Important feature boundaries
- Bluetooth currently scans/discovers BLE devices. It does not yet pair with an ELM327 device or decode live ECU/PID values.
- Vehicle profile is currently one editable profile, not multi-vehicle management.
- Trip history stores trip summary (date, distance, maximum speed and duration). Route-point history/map replay is not implemented in this native baseline.
- GPS requires location permission and phone Location/GPS enabled. The app uses a foreground service while a ride is active; test notification visibility and screen-locked recording on the target phone.
- Mileage from full-tank records is most reliable when each interval begins and ends with a consistently filled tank, using the same pump and automatic cut-off.

## Build in GitHub Actions
1. Upload the contents of this folder to the repository root (not the enclosing folder).
2. Push to `main` or `master`, or manually run **Build Bike Mileage Pro Native Android APK**.
3. Download artifact `BikeMileagePro-Native-Kotlin-v0.5.0-debug` from a successful Actions run.

## Build locally
Install JDK 17, Android SDK and Gradle 8.9, then run from the project root:

```bash
gradle --no-daemon clean assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Physical test checklist
1. Install the APK and grant Location and Nearby devices permissions when requested.
2. Enable phone Location/GPS.
3. Start a trip, lock the screen for several minutes, unlock, then stop and verify the saved distance/duration.
4. Add two full-tank entries with increasing odometer readings; add any partial fills between them. Verify the mileage interval on the later full-tank record and dashboard.
5. Enable Bluetooth and scan with a powered BLE device nearby.

The source ZIP is not represented as a verified APK. A successful GitHub Actions build and physical-device test are still required.
