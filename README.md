# Bike Mileage Pro v1.0.4 — Dashboard daily activity analytics

## Dashboard chart upgrade
- Added a selected-day activity panel at the top of the Charts tab.
- Date selector opens the Android calendar picker and remembers the selected date.
- Shows daily trip distance, mileage from completed full-tank intervals, fuel spend, trip start count and trip completion count.
- Added `Trips` as a chart metric, available alongside Mileage, Distance and Fuel Spend in Day / Month / Year views.
- Trip completion day is calculated from the saved trip start timestamp plus saved duration. Trip distance is grouped by the trip start date.
- Existing Bar / Line chart style preferences remain saved across app restarts.
- Added daily summary data calculation helpers.

## Notes on the figures
- Daily mileage is shown only when a full-tank interval ends on the selected day; otherwise it shows `--`.
- Distance is grouped by trip start date.
- A saved trip stores its start timestamp and duration, not a separately persisted end timestamp. The completion date shown is therefore derived from start + duration.
- Fuel spend uses fuel entries recorded on the selected date.
- Trip chart counts saved trip records by their start date.

## Release
- Version: 1.0.4
- Version code: 15
- Native Kotlin + Jetpack Compose; existing database schema retained.

## Verification
Source changes and ZIP structure are checked. Android Gradle compilation and device testing must be performed through GitHub Actions and on the user's phone; no successful build is claimed here.
