# Bike Mileage Pro — Native Android v1.0.0

Native Kotlin + Jetpack Compose Android project. This release is based on the native v0.5.0 baseline; the Flutter experiment is not used.

## Included
- Native dark Jetpack Compose UI with Material navigation icons.
- Dashboard with actual overall full-tank mileage, recorded trip distance, fuel spend, odometer and mileage trend.
- Editable vehicle profile (name, year, registration, variant/colour and odometer).
- Android GPS foreground service with persistent notification, live distance, speed and duration while the screen is locked.
- Trip history saved in native SQLite.
- Fuel entry form with date, odometer, litres, amount and full/partial fill-up.
- Full-tank interval mileage calculated as odometer difference divided by all litres added after the previous full tank, including partial fills and the current full fill.
- Overall mileage calculated as the combined valid interval distance divided by the combined interval fuel.
- BLE scan and nearby device discovery.
- GitHub Actions debug APK build and artifact upload.

## v1.0.0 changes over v0.5.0
- App version updated to 1.0.0 (versionCode 10).
- Vehicle name/year/variant shown on dashboard now reflect the saved vehicle profile instead of a hardcoded Honda label.
- Fuel entry validation is centralized and checks finite values, positive litres, non-negative amount, and increasing odometer.
- Fuel history interval display now includes intermediate partial-fill litres. Previously the history display used only the final full-tank entry's litres, which could overstate mileage.
- BLE scan permission declares `neverForLocation` because the app does not use BLE scan results to derive location.
- CI verifies that the APK file exists before uploading it.

## Feature boundaries
- Bluetooth is BLE discovery only. ELM327 pairing, classic Bluetooth SPP support and live ECU/PID decoding are not implemented.
- Vehicle profile is a single editable profile, not a multi-vehicle garage.
- Trip history stores summaries (date, distance, maximum speed and duration). GPS breadcrumb route replay/map is not included.
- GPS tracking runs in an Android location foreground service. The user must grant location permission, enable phone Location/GPS and allow notifications for the persistent notification. OEM battery restrictions may affect long rides; verify on the target phone.
- Actual mileage requires at least two consecutive full-tank entries. Record every partial fill between them, with accurate odometer readings. For best accuracy, use the same pump and automatic cut-off method.

## Build in GitHub Actions
1. Upload the **contents** of this folder to the repository root (not the enclosing folder).
2. Push to `main` or `master`, or manually run **Build Bike Mileage Pro Native Android APK**.
3. Download artifact `BikeMileagePro-Native-Kotlin-v1.0.0-debug` from a successful Actions run.

## Build locally
Install JDK 17, Android SDK and Gradle 8.9, then run from the project root:

```bash
gradle --no-daemon clean assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Physical test checklist
1. Install APK and grant Location and Nearby devices permissions when requested.
2. Enable phone Location/GPS and verify the ongoing Bike Mileage Pro notification appears after starting a trip.
3. Start a trip, lock screen for 5–10 minutes, unlock, stop and verify saved distance/duration.
4. Add a full-tank record, one or more partial fills, then the next full-tank record. Verify interval mileage uses the sum of all intervening litres.
5. Add another full-tank interval and verify dashboard overall mileage is the weighted combined mileage, not a simple average of interval values.
6. Edit vehicle name/year/variant and verify the dashboard reflects saved values.
7. Enable Bluetooth and scan with a powered BLE device nearby. BLE discovery does not guarantee ELM327 data decoding.

## Verification status
Source changes and ZIP structure are checked. This environment does not have the Android SDK/Gradle toolchain configured, so this ZIP is **not represented as a compiled APK or physically tested release**. GitHub Actions must pass before installing the APK.
