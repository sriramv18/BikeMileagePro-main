# GitHub Actions — standalone Android APK

Push the repository to `main`/`master`, or run **Actions → Bike Mileage Pro Android APK → Run workflow**.

Download the `Bike-Mileage-Pro-release-APK` artifact from the successful run and extract `app-release.apk`.

This workflow builds the **release** variant. React Native's Android Gradle plugin creates and packages `index.android.bundle` for release builds; the installed APK therefore does not require Metro or a USB connection. A debug APK is intentionally not used for this standalone-install test.

The workflow creates an unsigned release APK suitable for device testing. For Play Store distribution, configure a private upload keystore and signing secrets, then sign the release build. Never commit keystore files or passwords.
