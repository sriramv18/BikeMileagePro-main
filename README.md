# Bike Mileage Pro — Native Android v1.0.2 (versionCode 13)

Native Kotlin + Jetpack Compose. No Flutter / React Native. Launcher icon resources and the manifest
`android:icon` / `android:roundIcon` declarations are unchanged from the previous package.

## IMPORTANT: verification status
This source was written **without an Android SDK, Gradle or a device available**, so it has **not been compiled
or run**. Only a bracket-balance check and a manual read-through were done. Please build it with the included
GitHub Actions workflow (or Android Studio) and test on a phone before relying on it. Any compile error that
shows up should be reported back and fixed. A JUnit test file for the calculation code is included
(`gradle testDebugUnitTest`) but has also **not been executed yet**.

## What changed in this version
1. **Location prompt** – On open the app asks for permission, and if GPS is switched off it shows a
   "Turn on location" dialog that opens the system location settings (plus a persistent banner with a FIX button).
   Start Trip does the same check. (It does not use Google Play Services' one-tap dialog.)
2. **Multiple vehicles** – Vehicle tab: add, edit, select and delete vehicles. Trips, fuel records, mileage and
   charts are per vehicle. A vehicle switcher is in the header. Existing data and the old single profile are
   migrated into vehicle #1 (database v2 → v3).
3. **Connection status on Trips** – GPS chip and OBD chip (ECU connected / adapter only / connecting / error /
   not connected).
4. **Fuel entry** – Live "mileage preview" while typing (km ÷ litres), price per litre, clearer validation, per-entry
   breakdown. Method: full-tank to full-tank; partial fills are added to the next full tank.
   Note: I could not reproduce the exact wrong value you saw, so please test with real entries and tell me the
   numbers if it is still wrong.
5. **Dashboard** – Mileage per fuel top-up, mileage per trip, and charts for Mileage / Distance / Fuel spend by
   Day, Month or Year.
6. **Trip start/end** – Every trip stores start and end coordinates, a route, readable place names (looked up
   when you open the trip, needs network + a geocoder; falls back to coordinates), a route preview drawn in the
   app (no map key needed) and "Open in Maps" buttons.
   **Trip mileage is an estimate unless you enter the litres used**: GPS cannot measure fuel. Each trip shows its
   source: Actual (litres entered), Interval est. (mileage of the full-tank interval the trip fell in) or Average est.

## Trip lifecycle (RideTrackingService)
Start, pause, resume, stop; notification buttons for pause/resume/stop; the active trip and its route points are
persisted continuously; duplicate START/STOP calls are ignored; each trip has a UNIQUE id so it cannot be saved twice;
after the app/process is killed the app offers Resume / Save / Discard. Distance travelled while tracking was not
running is not counted.

## ELM327 BLE
Scan, list devices ("likely OBD adapter" is highlighted), real GATT connection, UART discovery (Nordic UART, HM-10
FFE0/FFE1, fallback to any writable+notifiable pair), init (ATZ, ATE0, ATL0, ATS0, ATH0, ATSP0) and a `0100` check.
The UI reports whether the **ECU answered**; many motorcycles do not support standard OBD-II.
Not implemented: reading live PIDs (speed/RPM/fuel), classic-Bluetooth (SPP) adapters, keeping the adapter
connected after the app is closed.

## Not tested
Background/screen-off tracking, process-death recovery, BLE with a real adapter and GPS accuracy all need testing
on a physical phone.


## Build fix update
- Removed duplicate `DashboardScreen()` declaration from `app/src/main/java/com/sriram/bikemileagepro/Dashboard.kt`.
- Kept `DashboardScreen.kt` as the single canonical dashboard composable. The two files declared the same package-level composable signature, creating a Kotlin duplicate/conflicting declaration.
- Preserved the existing vehicle model and `VehicleScreen.kt` fields.

The supplied Actions screenshot showed the repeated `DashboardScreen(...)` declaration context before `compileDebugKotlin` failed. The duplicate source file is removed. Run GitHub Actions to confirm the full build; this environment does not have Gradle/Android SDK installed, so no successful APK build is claimed.
