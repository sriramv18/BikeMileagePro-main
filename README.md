# Bike Mileage Pro — Native Android v1.0.2 development update

This package retains the native Kotlin + Jetpack Compose application and corrects the launcher icon manifest declaration and stale GitHub Actions version check. It adds an ELM BLE GATT transport for common Nordic UART / HM-10 UUID layouts.

## Included
- Launcher icon resources and adaptive icon support.
- Manifest declares `android:icon` and `android:roundIcon`.
- Version `1.0.2`, versionCode `12`.
- GitHub Actions checks versionName `1.0.2`.
- New BLE GATT transport class for common ELM BLE UART layouts.
- Existing trip, fuel, dashboard and GPS source retained.

## Still not implemented in this package
- UI selection and Connect/Disconnect controls are not yet wired to the new BLE transport.
- Multi-vehicle database migration and vehicle manager are not implemented; the existing profile is still single-vehicle.
- GPS service still needs pause/resume, durable active-trip recovery and duplicate-save protection.
- OBD PID polling/decoding is not included; adapter transport does not prove the motorcycle ECU supports standard OBD-II PIDs.
- Screen-locked/background tracking requires physical-device testing.

## Verification
ZIP integrity, manifest icon declarations and workflow version check were verified. No Android SDK/Gradle build or physical-device test was available. This is a development update, not a completed feature release.
