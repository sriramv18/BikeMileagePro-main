# Bike Mileage Pro — v1.0.3 user-requested dashboard, fuel and settings update

## Changes
- Persisted the Fuel screen's Bluetooth Auto-read checkbox in app preferences. It remains selected after closing and reopening the app.
- Fuel form now clears odometer/litres/amount only after SQLite confirms a successful insert. A failed insert shows an error and retains the entered values.
- Fuel records remain in the per-vehicle SQLite history; saving a new entry does not delete earlier history.
- Dashboard mileage summary now explains the number of full-tank intervals and why mileage is pending when there are not yet two full-tank readings.
- Added Settings screen to bottom navigation.
- Settings includes a Google Maps API key field saved locally, plus persistent default chart metric, period and chart style.
- Added Bar and Line chart display modes. Chart choices persist after app restart.
- Kept offline trip route preview and start/end map links.
- Removed duplicate `DashboardScreen` source file so only `Dashboard.kt` declares the composable.
- Version updated to 1.0.3 / versionCode 14.

## Mileage calculation note
A true full-tank mileage interval requires two full-tank entries with increasing odometer readings. Partial fills between those full tanks are included in the interval litres. A single fill-up cannot produce a reliable km/L result by itself. The dashboard reports this instead of inventing a mileage value.

## Map API key note
The app currently draws the recorded route using its offline route preview and opens start/end coordinates in an external maps app. The Settings field stores the Google Maps API key locally, but Google Maps SDK tiles/navigation are not integrated by this update. Restrict any key in Google Cloud to this Android package and signing certificate; do not share the key publicly.

## Verification
Source and ZIP structure were checked. No Android SDK/Gradle build or device test was run in this environment. Run GitHub Actions and test fuel persistence, chart preferences and settings on the phone before treating this as a release.
