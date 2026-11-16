BikeMileagePro Updated Kotlin Files
===================================

Replace the following files inside your Android project folder:
app/src/main/java/com/sriram/bikemileagepro/

1. Dashboard.kt
   - Adds Summary and Charts tab switching.
   - Displays Total Ride KM and Running Time per vehicle.

2. FuelScreen.kt
   - Adds 'Auto-read fuel from Bluetooth OBD' toggle alongside manual entry.
   - Automatically fills fuel readings synced via Bluetooth.

3. VehicleScreen.kt
   - Removes internal BLE device scanning completely.
   - Automatically detects active phone native Bluetooth connections.

4. TripsScreen.kt
   - Enables 'START TRIP' only when native phone Bluetooth is connected.
   - Automatically connects and syncs fuel consumption upon Bluetooth connection.

5. ElmClassicConnection.kt
   - Handles Classic Bluetooth (SPP/RFCOMM) OBD connections paired in phone settings.
   - Includes ObdFuelStore so live fuel readings compile cleanly without missing UI properties.
   - Auto-registers connected Bluetooth device as a vehicle profile if not already present.
