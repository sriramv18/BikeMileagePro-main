package com.sriram.bikemileagepro

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private lateinit var db: TripDatabase
    private lateinit var store: ActiveTripStore
    private lateinit var elm: ElmBleConnection
    private val ui = UiState()
    private val handler = Handler(Looper.getMainLooper())
    private val appPrefs by lazy { getSharedPreferences("app_state", MODE_PRIVATE) }
    private var scanCallback: ScanCallback? = null
    private val scanFound = linkedMapOf<String, ScannedDevice>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshLocationState()
        if (ui.hasLocationPermission && !ui.locationEnabled) ui.showLocationDialog = true
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RideTrackingService.ACTION_UPDATE -> syncLive()
                RideTrackingService.ACTION_FINISHED -> {
                    val saved = intent?.getBooleanExtra(RideTrackingService.EXTRA_SAVED, false) ?: false
                    val km = intent?.getDoubleExtra(RideTrackingService.EXTRA_DISTANCE, 0.0) ?: 0.0
                    Toast.makeText(
                        this@MainActivity,
                        if (saved) "Trip saved (${fmt(km, 2)} km)." else "Trip ended — too short to save (under 20 m).",
                        Toast.LENGTH_LONG
                    ).show()
                    ui.dataVersion++
                    syncLive()
                }
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            syncLive()
            handler.postDelayed(this, 1000L)
        }
    }

    private val stopScanRunnable = Runnable { stopScan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = TripDatabase(this)
        store = ActiveTripStore(this)
        elm = ElmBleConnection(this) { state, message ->
            ui.obdState = state
            ui.obdMessage = message
            if (state == ObdState.DISCONNECTED || state == ObdState.ERROR) ui.connectedName = ""
        }
        db.vehicles() // guarantees at least one vehicle exists
        // If the process died while a trip was running, convert it to PAUSED so it can be resumed or saved.
        val saved = store.load()
        if (saved.state == TripState.RUNNING && !RideTrackingService.isAlive) {
            store.save(store.settleInterrupted(saved), sync = true)
        }
        refreshLocationState()
        requestNeededPermissions()
        syncLive()
        setContent {
            MileageTheme {
                MileageApp(Env(ui, actions, db, this@MainActivity, bump = { ui.dataVersion++ }))
            }
        }
    }

    // ---------------------------------------------------------------- state sync

    private fun syncLive() {
        val t = store.load()
        ui.live = t
        ui.elapsedMs = t.elapsedMillis()
        ui.needsRecovery = t.state != TripState.IDLE && !RideTrackingService.isAlive
    }

    private fun gpsEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        return try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
    }

    private fun refreshLocationState() {
        ui.hasLocationPermission =
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ui.locationEnabled = gpsEnabled()
    }

    private fun requestNeededPermissions() {
        val required = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 31) {
            required += Manifest.permission.BLUETOOTH_SCAN
            required += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= 33) required += Manifest.permission.POST_NOTIFICATIONS
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    // ------------------------------------------------------------------ actions

    private fun sendServiceAction(action: String, foreground: Boolean, vehicleId: Long = -1L) {
        val intent = Intent(this, RideTrackingService::class.java).setAction(action)
        if (vehicleId >= 0L) intent.putExtra(RideTrackingService.EXTRA_VEHICLE_ID, vehicleId)
        if (foreground && Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private val actions = object : AppActions {
        override fun savedVehicleId(): Long = appPrefs.getLong("selected_vehicle", -1L)
        override fun saveSelectedVehicle(id: Long) { appPrefs.edit().putLong("selected_vehicle", id).apply() }

        override fun startTrip(vehicleId: Long) {
            refreshLocationState()
            if (!ui.hasLocationPermission) {
                requestNeededPermissions()
                ui.showLocationDialog = true
                return
            }
            if (!ui.locationEnabled) { ui.showLocationDialog = true; return }
            if (ui.live.state != TripState.IDLE) {
                Toast.makeText(this@MainActivity, "A trip is already in progress.", Toast.LENGTH_SHORT).show()
                return
            }
            sendServiceAction(RideTrackingService.ACTION_START, true, vehicleId)
            Toast.makeText(this@MainActivity, "Trip tracking started", Toast.LENGTH_SHORT).show()
        }

        override fun pauseTrip() = sendServiceAction(RideTrackingService.ACTION_PAUSE, false)
        override fun resumeTrip() {
            refreshLocationState()
            if (!ui.locationEnabled) { ui.showLocationDialog = true; return }
            sendServiceAction(RideTrackingService.ACTION_RESUME, !RideTrackingService.isAlive)
        }
        override fun stopTrip() = sendServiceAction(RideTrackingService.ACTION_STOP, false)

        override fun resumeRecovered() {
            refreshLocationState()
            if (!ui.hasLocationPermission || !ui.locationEnabled) { ui.showLocationDialog = true; return }
            ui.needsRecovery = false
            sendServiceAction(RideTrackingService.ACTION_RESUME, true)
        }
        override fun saveRecovered() {
            ui.needsRecovery = false
            sendServiceAction(RideTrackingService.ACTION_STOP, false)
        }
        override fun discardRecovered() {
            store.clear()
            db.clearActivePoints()
            ui.needsRecovery = false
            syncLive()
            Toast.makeText(this@MainActivity, "Unfinished trip discarded.", Toast.LENGTH_SHORT).show()
        }

        override fun scanBle() = startScan()
        override fun connectBle(device: ScannedDevice) = connectDevice(device)
        override fun disconnectBle() { elm.disconnect(); ui.connectedName = "" }

        override fun requestPermissions() = requestNeededPermissions()
        override fun openLocationSettings() {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        override fun openAppSettings() {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }
        override fun openMap(lat: Double, lon: Double, label: String) {
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")
            try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            catch (_: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, "No maps app installed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------------------------------------------------------------- BLE

    private fun bleAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 31 &&
            (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            requestNeededPermissions()
            ui.scanMessage = "Allow “Nearby devices”, then scan again."
            return false
        }
        if (Build.VERSION.SDK_INT < 31 && !ui.hasLocationPermission) {
            requestNeededPermissions()
            ui.scanMessage = "Allow location permission (required for Bluetooth scanning on this Android version)."
            return false
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (!bleAllowed()) return
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) { ui.scanMessage = "Turn on Bluetooth first."; return }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) { ui.scanMessage = "BLE scanner unavailable."; return }
        stopScan()
        scanFound.clear()
        ui.scanResults = emptyList()
        ui.scanning = true
        ui.scanMessage = "Scanning nearby BLE devices…"
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = addResult(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { addResult(it) } }
            override fun onScanFailed(errorCode: Int) {
                ui.scanning = false
                ui.scanMessage = "Scan failed (error $errorCode)."
            }
        }
        scanCallback = cb
        try {
            scanner.startScan(cb)
            handler.postDelayed(stopScanRunnable, 10_000L)
        } catch (_: SecurityException) {
            ui.scanning = false
            ui.scanMessage = "Bluetooth permission denied."
        }
    }

    @SuppressLint("MissingPermission")
    private fun addResult(r: ScanResult) {
        val name = (try { r.scanRecord?.deviceName ?: r.device.name } catch (_: SecurityException) { null }) ?: ""
        val upper = name.uppercase()
        val likely = listOf("OBD", "ELM", "VLINK", "VGATE", "ICAR", "LELINK", "VIECAR", "CARISTA", "KONNWEI", "OBDLINK", "V-LINK")
            .any { upper.contains(it) }
        scanFound[r.device.address] = ScannedDevice(
            name.ifBlank { "Unnamed BLE device" }, r.device.address, r.rssi, likely, r.device
        )
        ui.scanResults = scanFound.values.sortedWith(compareByDescending<ScannedDevice> { it.likelyObd }.thenByDescending { it.rssi })
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        handler.removeCallbacks(stopScanRunnable)
        val cb = scanCallback
        if (cb != null) {
            try {
                (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeScanner?.stopScan(cb)
            } catch (_: Exception) {}
        }
        scanCallback = null
        if (ui.scanning) {
            ui.scanning = false
            ui.scanMessage = if (scanFound.isEmpty())
                "No BLE devices found. Make sure the adapter is plugged in and is a BLE (not classic Bluetooth) model."
            else "Found ${scanFound.size} device(s). Tap CONNECT on your adapter."
        }
    }

    private fun connectDevice(d: ScannedDevice) {
        if (!bleAllowed()) return
        stopScan()
        ui.connectedName = d.name
        elm.connect(d.device)
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(RideTrackingService.ACTION_UPDATE)
            addAction(RideTrackingService.ACTION_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
        refreshLocationState()
        // First time the app is opened with permission but location switched off: ask to turn it on.
        if (ui.hasLocationPermission && !ui.locationEnabled && !promptedOnce) {
            promptedOnce = true
            ui.showLocationDialog = true
        }
        handler.post(ticker)
    }

    private var promptedOnce = false

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        try { unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
    }

    override fun onDestroy() {
        stopScan()
        if (isFinishing) elm.disconnect()
        try { db.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}

@Composable
private fun MileageTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Teal, onPrimary = Ink, secondary = Orange, background = Ink,
        surface = Panel, onSurface = White, onBackground = White,
        surfaceVariant = Panel2, onSurfaceVariant = Muted
    )
    MaterialTheme(colorScheme = colors, content = content)
}

private enum class Screen(val label: String) {
    HOME("Home"), VEHICLE("Vehicle"), TRIPS("Trips"), FUEL("Fuel"), SETTINGS("Settings")
}

@Composable
private fun MileageApp(env: Env) {
    val ui = env.ui
    val db = env.db
    var selected by remember { mutableStateOf(Screen.HOME) }
    var refresh by remember { mutableIntStateOf(0) }
    var vehicleId by remember { mutableLongStateOf(env.actions.savedVehicleId()) }
    val version = ui.dataVersion + refresh
    val appEnv = remember(env) { Env(env.ui, env.actions, env.db, env.context, bump = { refresh++ }) }

    val vehicles = remember(version) { db.vehicles() }
    val vehicle = vehicles.firstOrNull { it.id == vehicleId } ?: vehicles.first()
    LaunchedEffect(vehicle.id) {
        if (vehicle.id != vehicleId) vehicleId = vehicle.id
        env.actions.saveSelectedVehicle(vehicle.id)
    }

    val trips = remember(version, vehicle.id) { db.trips(vehicle.id) }
    val fuels = remember(version, vehicle.id) { db.fuels(vehicle.id) }
    val intervals = remember(fuels) { MileageCalc.intervals(fuels) }
    val overall = remember(intervals) { MileageCalc.overall(intervals) }
    val tripMileage = remember(trips, intervals, overall) {
        trips.associate { it.id to MileageCalc.tripMileage(it, intervals, overall) }
    }
    val totalKm = remember(version, vehicle.id) { db.totalTripDistance(vehicle.id) }
    val spend = remember(version, vehicle.id) { db.totalFuelSpend(vehicle.id) }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D1C2A), contentColor = Teal) {
                val items = listOf(
                    Screen.HOME to Icons.Default.Dashboard,
                    Screen.VEHICLE to Icons.Default.DirectionsBike,
                    Screen.TRIPS to Icons.Default.Route,
                    Screen.FUEL to Icons.Default.LocalGasStation,
                    Screen.SETTINGS to Icons.Default.SettingsIcon
                )
                items.forEach { (screen, icon) ->
                    NavigationBarItem(
                        selected = selected == screen,
                        onClick = { selected = screen; refresh++ },
                        icon = { Icon(icon, contentDescription = screen.label) },
                        label = { Text(screen.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Teal, selectedTextColor = Teal,
                            indicatorColor = Color(0xFF183D48),
                            unselectedIconColor = Muted, unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TopHeader(selected.label, vehicles, vehicle) { vehicleId = it.id }
            if (!ui.hasLocationPermission || !ui.locationEnabled) LocationBanner(env)
            when (selected) {
                Screen.HOME -> DashboardScreen(
                    appEnv, vehicle, overall, totalKm, spend, fuels, trips, intervals, tripMileage,
                    onOpenTrips = { selected = Screen.TRIPS }, onOpenFuel = { selected = Screen.FUEL }
                )
                Screen.VEHICLE -> VehicleScreen(appEnv, vehicles, vehicle, onSelect = { vehicleId = it })
                Screen.TRIPS -> TripsScreen(appEnv, vehicle, trips, tripMileage, onOpenVehicle = { selected = Screen.VEHICLE })
                Screen.FUEL -> FuelScreen(appEnv, vehicle, fuels, intervals)
                Screen.SETTINGS -> SettingsScreen(appEnv)
            }
        }
    }

    if (ui.showLocationDialog) LocationDialog(env)
    if (ui.needsRecovery) RecoveryDialog(env)
}

@Composable
private fun TopHeader(title: String, vehicles: List<Vehicle>, current: Vehicle, onPick: (Vehicle) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF10283A), Color(0xFF0D1C2A))))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("BIKE MILEAGE PRO", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(title, color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        Box {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF1A3C4A))
                    .clickable { open = true }.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.DirectionsBike, contentDescription = null, tint = Teal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(current.name, color = White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.widthIn(max = 120.dp))
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch vehicle", tint = Teal)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                vehicles.forEach { v ->
                    DropdownMenuItem(
                        text = { Text(if (v.id == current.id) "● ${v.name}" else v.name) },
                        onClick = { open = false; onPick(v) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationBanner(env: Env) {
    val ui = env.ui
    Surface(color = Color(0xFF3A2A12)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Orange, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (!ui.hasLocationPermission) "Location permission is off — trips cannot be tracked."
                else "Location (GPS) is turned off — trips cannot be tracked.",
                color = Orange, fontSize = 12.sp, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { ui.showLocationDialog = true }) { Text("FIX", color = Teal, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun LocationDialog(env: Env) {
    val ui = env.ui
    AlertDialog(
        onDismissRequest = { ui.showLocationDialog = false },
        containerColor = Panel,
        title = { Text("Turn on location", color = White, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (!ui.hasLocationPermission)
                    "Bike Mileage Pro needs location permission to record trip distance, speed and the start/end points. " +
                        "If the permission prompt does not appear, open the app settings and allow Location."
                else
                    "Location (GPS) is switched off on this phone. Turn it on in the system settings, then come back to the app.",
                color = Muted
            )
        },
        confirmButton = {
            TextButton(onClick = {
                ui.showLocationDialog = false
                if (!ui.hasLocationPermission) env.actions.openAppSettings() else env.actions.openLocationSettings()
            }) { Text(if (!ui.hasLocationPermission) "APP SETTINGS" else "OPEN LOCATION SETTINGS", color = Teal) }
        },
        dismissButton = {
            TextButton(onClick = {
                ui.showLocationDialog = false
                if (!ui.hasLocationPermission) env.actions.requestPermissions()
            }) { Text(if (!ui.hasLocationPermission) "ASK AGAIN" else "NOT NOW", color = Muted) }
        }
    )
}

@Composable
private fun RecoveryDialog(env: Env) {
    val t = env.ui.live
    AlertDialog(
        onDismissRequest = {},
        containerColor = Panel,
        title = { Text("Unfinished trip found", color = White, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "The app was closed while a trip was in progress: ${fmt(t.distanceKm, 2)} km, " +
                    "${formatDuration(t.elapsedMillis())} so far. Resume tracking, save it as it is, or discard it. " +
                    "Distance travelled while tracking was not running is not included.",
                color = Muted
            )
        },
        confirmButton = {
            TextButton(onClick = { env.actions.resumeRecovered() }) { Text("RESUME", color = Teal, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { env.actions.saveRecovered() }) { Text("SAVE", color = Teal) }
                TextButton(onClick = { env.actions.discardRecovered() }) { Text("DISCARD", color = Orange) }
            }
        }
    )
}
