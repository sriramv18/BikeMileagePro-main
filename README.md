# Bike Mileage Pro — Flutter

Flutter/Dart Android-first rebuild starter for Bike Mileage Pro.

## Included in this source
- Dark dashboard and Home / Vehicles / Trips / Fuel navigation
- SQLite local database with a default vehicle
- Add/delete vehicle records with reference mileage and fuel price
- Fuel fill-up entry form with odometer, litres, amount and full/partial flag
- Foreground GPS trip recording while the Trips screen is open
- Per-trip estimated fuel and fuel cost snapshots
- Saved trip history and Google Maps route polyline view
- Dashboard totals based on saved trip/fuel records
- GitHub Actions workflow for Flutter analyze, tests and release APK

## Build locally
Install Flutter stable and Android SDK, then:
```bash
flutter pub get
flutter analyze
flutter test
flutter build apk --release
```
APK: `build/app/outputs/flutter-apk/app-release.apk`

## Android project generation
This repository contains Flutter application source. Generate the platform host using:
```bash
flutter create . --platforms=android
```
Then merge the location permissions from `android/README_ANDROID_SETUP.md` into the generated AndroidManifest.xml.

## Google Maps
Add a Google Maps Android SDK API key in the generated `android/app/src/main/AndroidManifest.xml`:
```xml
<meta-data android:name="com.google.android.geo.API_KEY" android:value="YOUR_API_KEY"/>
```
Do not commit a production key. Configure it using GitHub Actions secrets / a local untracked secrets file.

## Current limitations — important
This is a first Flutter implementation scaffold, not a production-ready migration:
- GPS recording currently runs while the app's trip screen is active. Background tracking / foreground service is not implemented yet.
- The trip route map requires a valid Google Maps API key.
- Route coordinates are stored in a compact text field; consider normalized route-point storage for long trips.
- Overall estimated mileage is computed from saved trip estimates. Full-tank measured mileage calculation across consecutive full fills is not wired into the dashboard yet.
- OBD-II / Bluetooth integration, export, backup, route replay, and production signing are not implemented.
- The first GPS prototype should be tested on a physical Android phone; GPS filtering, pause/resume, app-kill recovery and battery optimization handling need further work.
- No signed production key is included. GitHub Actions currently creates an unsigned release APK suitable for installation/testing only after allowing installation from unknown sources; Play Store publishing requires secure signing setup.

## Calculation model
For each trip:
- estimated fuel litres = trip distance / vehicle reference mileage
- estimated trip fuel cost = estimated fuel litres × vehicle fuel price
- trip stores a snapshot of reference mileage and fuel price

Dashboard overall estimated mileage:
- sum of saved trip distances / sum of saved estimated fuel litres

This estimate is not actual measured fuel economy. Actual measured mileage should use consecutive full-tank odometer records.


## v1.0.1 build correction
Fixed a missing closing parenthesis in the fuel-entry dialog (`fuel_screen.dart`) that caused `flutter analyze` to stop with `expected token`. The remaining `material.dart` notices in the reported workflow are deprecation notices, not the syntax failure shown in the screenshot.

\n## v1.0.2 GitHub analyzer compatibility\nThe GitHub workflow runs `flutter analyze --no-fatal-infos`. The current Flutter stable channel reports the Material library imports as informational deprecations; these are not Dart syntax errors. The analyzer still reports errors and warnings as failures, while informational notices no longer block the test and APK build stages.\n