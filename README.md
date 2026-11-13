# SR Mobility Labs — Bike Mileage Pro

React Native app source and GitHub Actions Android debug-APK workflow.

## Build APK on GitHub

1. Create or open your GitHub repository.
2. Upload/commit the contents of this folder to the repository root.
3. Ensure the default branch is `main` (or `master`).
4. Open **Actions** → **SR Mobility Labs - Bike Mileage Pro Android APK**.
5. Select **Run workflow** for a manual build, or push a commit to `main`.
6. When the run succeeds, open the run → **Artifacts** → download `Bike-Mileage-Pro-debug-APK`.
7. Extract the ZIP artifact; it contains `app-debug.apk`.

## What this workflow does

- Checks out the repository.
- Sets up Node.js 22 and Java 17.
- Generates a fresh React Native Android host project on the GitHub runner.
- Copies the app source from `mobile/src`.
- Installs React Navigation dependencies.
- Runs `./gradlew assembleDebug`.
- Uploads the APK as a 14-day workflow artifact.

The Android host is generated during CI, so you do not need to commit a generated `android/` folder for this starter workflow.

## Important scope and limitations

This is a **buildable React Native UI starter**, not a completed migration of every native Android feature. GPS foreground tracking, persistent trip/fuel database, route maps, date picker forms, OBD-II/Bluetooth protocol integration, cloud sync, subscriptions, production signing, iOS host, app icon and splash screen still need implementation and device testing. The current Trips screen includes starter UI/state logic only.

The APK produced by this workflow is a debug build for testing. A Play Store release requires a signed release build and secure signing secrets.

## Repository layout

- `.github/workflows/android-build.yml` — CI build workflow
- `mobile/src/` — React Native screens, services and app entry
- `mobile/tsconfig.json` — TypeScript config
- `docs/` — feature matrix and native integration notes
