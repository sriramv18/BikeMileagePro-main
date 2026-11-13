# GitHub Actions

The workflow generates the React Native Android host in the CI runner, overlays `mobile/src`, installs navigation dependencies and builds a debug APK. If the React Native Community CLI changes its initialization flags or the generated Android template changes, update the `Generate React Native Android host` step accordingly.
