# Bike Mileage Pro — Phase 2 Android project

Customized for the 2026 Honda H'ness CB350 DLX Pro.

## Included in v0.2.0
- Native Kotlin Android app.
- Foreground GPS ride-tracking service with persistent notification.
- GPS distance and speed updates while tracking, including when app is backgrounded.
- Local SQLite storage for trip records and fuel fill-ups.
- Fuel log with litres, amount paid, and odometer reading.
- BLE discovery for future adapter testing.

## Accuracy and compatibility
- GPS estimates distance; phone GPS accuracy varies with placement, buildings, trees, and road geometry.
- This build does not yet compute automatic fuel consumption from the ECU.
- For real km/L, use consecutive full-tank fills: odometer distance travelled divided by litres needed to refill at the next full fill.
- BLE scanning only detects BLE advertisements. It does not connect to Honda RoadSync, decode OBD messages, or establish generic ELM327 compatibility.
- Do not connect an adapter until connector pinout, voltage, protocol, and adapter compatibility are verified for the exact 2026 model.

## Open in Android Studio
1. Install Android Studio and Android SDK Platform 35.
2. Open this folder and allow Gradle sync.
3. Connect Android phone with Developer Options and USB debugging enabled.
4. Run the app configuration.
5. Grant Location, Nearby devices, and Notifications permissions.
6. Enable phone GPS. Tap Start Ride Tracking, then Stop and Save Ride.
7. Enter litres, amount and bike odometer reading for each fuel fill-up.

## Known limitations / next milestones
- No compiled APK included; build from Android Studio.
- Source has not yet been compiled or tested on the user's phone or motorcycle.
- A dedicated full-tank-to-full-tank mileage report is the next data/UI milestone.
- ECU integration requires confirmed diagnostic connector/protocol and a compatible adapter.
- Add route map, backup/export, trip recovery after unexpected process termination, and battery-optimized sampling before production release.

## App identity
- Application ID: `com.sriram.bikemileagepro`
- Version: `0.2.0`
- Minimum Android: 8.0 (API 26)
- Target SDK: 35
