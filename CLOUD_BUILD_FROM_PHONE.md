# Build the APK without a laptop

This project includes a GitHub Actions workflow at `.github/workflows/build-apk.yml`.

1. Sign in to GitHub in your Android phone browser and create a new empty repository (for example `BikeMileagePro`).
2. Upload the contents of this project ZIP to the repository, keeping the `.github/workflows/build-apk.yml` path intact. If the mobile upload UI hides dot-folders, use GitHub's web editor to create the workflow file at that exact path.
3. Open the repository's **Actions** tab.
4. Select **Build Bike Mileage Pro APK** and tap **Run workflow** (or wait for the first push build).
5. Open the completed workflow run and download the `BikeMileagePro-debug-apk` artifact.
6. Extract the downloaded artifact ZIP on your phone. Tap `app-debug.apk` to install. Android may ask you to allow installation from that browser/file manager.

This produces a debug APK for testing, not a Play Store release. It is not signed with a private release key.

Important: this workflow has not been executed in this environment. The build must complete successfully in GitHub Actions before an APK exists.
