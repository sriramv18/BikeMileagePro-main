# Bike Mileage Pro

React Native pilot application and GitHub Actions workflow for building a standalone Android release APK.

## Build on GitHub

1. Upload the **contents** of this ZIP to the root of your GitHub repository.
2. Push to `main`/`master`, or open **Actions → Bike Mileage Pro Android APK → Run workflow**.
3. Wait for the workflow to finish successfully.
4. Open the run's **Artifacts** section and download `Bike-Mileage-Pro-release-APK`.
5. Extract the artifact ZIP and install `app-release.apk` on your Android phone.

## Important fix for “Unable to load script”

The earlier workflow built `assembleDebug`. React Native debug APKs normally expect Metro to serve the JavaScript bundle. This updated workflow builds `:app:assembleRelease`, which bundles `index.android.bundle` into the APK. The installed release APK should open without Metro, a computer, USB debugging, or `adb reverse`.

The workflow also sets the launcher label to **Bike Mileage Pro** and configures an adaptive launcher icon.

## Scope

This is the Bike Mileage Pro React Native UI starter previously supplied, not a complete migration of all native Bike Mileage Pro functionality. GPS background tracking, persistent trip/fuel storage, route maps, OBD-II/Bluetooth integration, cloud sync, subscriptions and full production QA require separate implementation and testing.

The release APK generated here is for device testing. It is not yet a Play Store-signed production package. Add secure upload-keystore signing before Play Store publication. Do not commit keystore files or passwords.


## Trip-wise and overall estimated mileage

The mileage calculation service is included in `mobile/src/services/tripMileage.ts`.

- Each completed trip can store a snapshot of the vehicle's reference mileage and fuel price.
- Per-trip estimated fuel = trip distance ÷ reference mileage.
- Per-trip estimated fuel cost = estimated fuel × saved fuel price.
- Overall estimated mileage is weighted: total distance of trips with estimates ÷ total estimated fuel for those trips.
- Saved snapshots are not recalculated when the vehicle's reference mileage or fuel price changes later.

**Important limitation:** this ZIP is still a UI starter. GPS tracking, completed-trip persistence, vehicle reference-mileage setup, fuel-price entry and connecting the calculation service to the live trip-completion flow are not yet implemented. The calculation logic is ready, but live trip and dashboard figures will not populate until those data flows are completed. Estimated mileage is not actual measured fuel efficiency.


## GitHub Actions manifest parsing fix

The release workflow now sets `android:icon` and `android:roundIcon` through an XML parser, making the update idempotent and preventing duplicate attributes. Adaptive icon XML is placed only under `mipmap-anydpi-v26`, where Android expects it.
