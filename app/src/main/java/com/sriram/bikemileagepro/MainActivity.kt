package com.sriram.bikemileagepro

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF08131F)
private val Panel = Color(0xFF122334)
private val Panel2 = Color(0xFF182D40)
private val Teal = Color(0xFF36D6C3)
private val Muted = Color(0xFF91A5B8)
private val White = Color(0xFFF4F8FC)
private val Orange = Color(0xFFFFB86B)
private val Green = Color(0xFF64D6A2)

class MainActivity : ComponentActivity() {
    private lateinit var db: TripDatabase
    private var liveDistance by mutableDoubleStateOf(0.0)
    private var liveSpeed by mutableDoubleStateOf(0.0)
    private var liveMinutes by mutableIntStateOf(0)
    private var bluetoothStatus by mutableStateOf("Not scanned")
    private val prefs by lazy { getSharedPreferences("vehicle_profile", MODE_PRIVATE) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val rideReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RideTrackingService.ACTION_UPDATE) {
                liveDistance = intent.getDoubleExtra("distance_km", 0.0)
                liveSpeed = intent.getDoubleExtra("speed_kmh", 0.0)
                liveMinutes = intent.getIntExtra("duration_minutes", 0)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = TripDatabase(this)
        requestNeededPermissions()
        setContent {
            MileageTheme {
                MileageApp(
                    db = db,
                    getVehicle = { key, fallback -> prefs.getString(key, fallback) ?: fallback },
                    saveVehicle = { values ->
                        prefs.edit().apply { values.forEach { (k, v) -> putString(k, v) } }.apply()
                    },
                    liveDistance = liveDistance,
                    liveSpeed = liveSpeed,
                    liveMinutes = liveMinutes,
                    bluetoothStatus = bluetoothStatus,
                    onStartRide = ::startRide,
                    onStopRide = ::stopRide,
                    onScanBluetooth = ::scanBluetooth
                )
            }
        }
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

    private fun startRide() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestNeededPermissions()
            Toast.makeText(this, "Allow location permission, then start again.", Toast.LENGTH_LONG).show()
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Please enable phone GPS/location.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, RideTrackingService::class.java).setAction(RideTrackingService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "Trip tracking started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRide() {
        startService(Intent(this, RideTrackingService::class.java).setAction(RideTrackingService.ACTION_STOP))
        Toast.makeText(this, "Trip stopped and saved.", Toast.LENGTH_SHORT).show()
    }

    @Suppress("MissingPermission")
    private fun scanBluetooth() {
        if (Build.VERSION.SDK_INT >= 31 &&
            (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
             checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            requestNeededPermissions()
            bluetoothStatus = "Grant Nearby devices permission and scan again."
            return
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            bluetoothStatus = "Turn on Bluetooth first."
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) { bluetoothStatus = "BLE scanner unavailable."; return }
        val found = linkedSetOf<String>()
        bluetoothStatus = "Scanning nearby BLE devices…"
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = try { result.device.name ?: "Unnamed BLE device" } catch (_: SecurityException) { "Protected device" }
                found.add("$name • ${result.device.address} • ${result.rssi} dBm")
                bluetoothStatus = found.take(10).joinToString("\n")
            }
            override fun onScanFailed(errorCode: Int) { bluetoothStatus = "Scan failed: $errorCode" }
        }
        try {
            scanner.startScan(callback)
            window.decorView.postDelayed({
                try { scanner.stopScan(callback) } catch (_: SecurityException) {}
                bluetoothStatus = if (found.isEmpty()) "No BLE devices found. A powered compatible adapter may be needed."
                                  else "Found ${found.size} device(s):\n" + found.take(10).joinToString("\n")
            }, 10_000L)
        } catch (_: SecurityException) { bluetoothStatus = "Bluetooth permission denied." }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(RideTrackingService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rideReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(rideReceiver, filter)
    }
    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(rideReceiver) } catch (_: IllegalArgumentException) {}
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
    HOME("Home"), VEHICLE("Vehicle"), TRIPS("Trips"), FUEL("Fuel")
}

@Composable
private fun MileageApp(
    db: TripDatabase,
    getVehicle: (String, String) -> String,
    saveVehicle: (Map<String, String>) -> Unit,
    liveDistance: Double, liveSpeed: Double, liveMinutes: Int,
    bluetoothStatus: String, onStartRide: () -> Unit, onStopRide: () -> Unit,
    onScanBluetooth: () -> Unit
) {
    var selected by remember { mutableStateOf(Screen.HOME) }
    var refresh by remember { mutableIntStateOf(0) }
    var chartRange by remember { mutableStateOf("ALL") }
    val trips = remember(refresh) { db.trips() }
    val fuels = remember(refresh) { db.fuels() }
    val mileage = remember(refresh) { db.overallMileage() }
    val mileagePoints = remember(refresh) { db.mileagePoints() }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D1C2A), contentColor = Teal) {
                val items = listOf(
                    Screen.HOME to Icons.Default.Dashboard,
                    Screen.VEHICLE to Icons.Default.DirectionsBike,
                    Screen.TRIPS to Icons.Default.Route,
                    Screen.FUEL to Icons.Default.LocalGasStation
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
            TopHeader(selected)
            when (selected) {
                Screen.HOME -> DashboardScreen(
                    mileage, db.totalTripDistance(), db.totalFuelSpend(), fuels.lastOrNull()?.odometerKm,
                    mileagePoints, chartRange, { chartRange = it }, liveDistance, liveSpeed, liveMinutes,
                    vehicleName = getVehicle("name", "My motorcycle"),
                    vehicleYear = getVehicle("year", ""), vehicleVariant = getVehicle("variant", ""),
                    onOpenTrips = { selected = Screen.TRIPS }
                )
                Screen.VEHICLE -> VehicleScreen(getVehicle, saveVehicle, bluetoothStatus, onScanBluetooth)
                Screen.TRIPS -> TripsScreen(trips, liveDistance, liveSpeed, liveMinutes, onStartRide, onStopRide)
                Screen.FUEL -> FuelScreen(fuels, onSave = { date, odo, litres, amount, full ->
                    val validation = db.validateFuelEntry(odo, litres, amount)
                    if (validation != null) return@FuelScreen validation
                    db.saveFuel(date, litres, amount, odo, full)
                    refresh++
                    null
                })
            }
        }
    }
}

@Composable
private fun TopHeader(screen: Screen) {
    Row(
        Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF10283A), Color(0xFF0D1C2A))))
            .padding(horizontal = 20.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("BIKE MILEAGE PRO", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(screen.label, color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF1A3C4A)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.DirectionsBike, contentDescription = null, tint = Teal)
        }
    }
}

@Composable
private fun DashboardScreen(
    mileage: Double, totalKm: Double, spend: Double, lastOdo: Double?,
    points: List<MileagePoint>, range: String, onRange: (String) -> Unit,
    liveDistance: Double, liveSpeed: Double, liveMinutes: Int,
    vehicleName: String, vehicleYear: String, vehicleVariant: String,
    onOpenTrips: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your ride, measured.", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(listOf(vehicleName, vehicleVariant, vehicleYear).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 12.sp)
                }
                Surface(color = Color(0xFF153B3B), shape = RoundedCornerShape(50)) {
                    Text("● READY", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Panel,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF254357))
            ) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF173B49), Panel))).padding(20.dp)) {
                    Text("OVERALL FUEL MILEAGE", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(if (mileage > 0) String.format(Locale.US, "%.1f", mileage) else "--",
                            color = Teal, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold)
                        Text(" km/L", Modifier.padding(bottom = 9.dp), color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(if (mileage > 0) "Calculated from full-tank entries" else "Add two full-tank entries to calculate mileage",
                        color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(15.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricPill("TRIP DISTANCE", String.format(Locale.US, "%.1f km", totalKm), Modifier.weight(1f))
                        MetricPill("LAST ODOMETER", lastOdo?.let { String.format(Locale.US, "%.0f km", it) } ?: "Not set", Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("FUEL SPEND", "₹${spend.toInt()}", "All recorded fill-ups", Modifier.weight(1f), Orange)
                StatCard("LIVE TRIP", String.format(Locale.US, "%.2f km", liveDistance), "${liveSpeed.toInt()} km/h · $liveMinutes min", Modifier.weight(1f), Teal)
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF20384B))) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Mileage trend", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Full-tank calculation history", color = Muted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.Timeline, tint = Teal, contentDescription = null)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL", "7", "30").forEach { value ->
                            FilterChip(
                                selected = range == value, onClick = { onRange(value) },
                                label = { Text(if (value == "ALL") "All" else "$value entries", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF1C4951), selectedLabelColor = Teal,
                                    labelColor = Muted
                                )
                            )
                        }
                    }
                    val shown = when (range) { "7" -> points.takeLast(7); "30" -> points.takeLast(30); else -> points }
                    MileageLineChart(shown.map { it.value })
                    if (shown.isEmpty()) Text("Your mileage chart will appear after two full-tank records.", color = Muted, fontSize = 11.sp)
                    else Text("${shown.size} mileage record(s) · km/L", color = Muted, fontSize = 10.sp)
                }
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().clickable { onOpenTrips() }.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Route, tint = Teal, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Track your next ride", color = White, fontWeight = FontWeight.Bold)
                        Text("GPS distance, speed and duration", color = Muted, fontSize = 11.sp)
                    }
                    Text("OPEN  →", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF102838)).padding(12.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatCard(title: String, value: String, detail: String, modifier: Modifier, accent: Color) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF20384B))) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            Text(value, color = accent, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MileageLineChart(values: List<Double>) {
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val left = 12.dp.toPx(); val right = size.width - 10.dp.toPx()
        val top = 10.dp.toPx(); val bottom = size.height - 12.dp.toPx()
        val grid = Color(0xFF294052)
        for (i in 0..3) {
            val y = top + (bottom - top) * i / 3f
            drawLine(grid, androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(right, y), 1.dp.toPx())
        }
        if (values.isNotEmpty()) {
            val maxValue = maxOf(10.0, values.maxOrNull()!! * 1.2).toFloat()
            val pts = values.mapIndexed { index, v ->
                val x = if (values.size == 1) (left + right) / 2f else left + (right-left) * index / (values.size-1).toFloat()
                val y = bottom - (v.toFloat()/maxValue)*(bottom-top)
                androidx.compose.ui.geometry.Offset(x,y)
            }
            val path = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x,p.y) else lineTo(p.x,p.y) } }
            drawPath(path, Teal, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            pts.forEach { drawCircle(Teal, radius = 4.dp.toPx(), center = it) }
        }
    }
}

@Composable
private fun VehicleScreen(
    getVehicle: (String, String) -> String,
    saveVehicle: (Map<String, String>) -> Unit,
    bluetoothStatus: String,
    onScanBluetooth: () -> Unit
) {
    var name by remember { mutableStateOf(getVehicle("name", "Honda H’ness CB350 DLX Pro")) }
    var year by remember { mutableStateOf(getVehicle("year", "2026")) }
    var reg by remember { mutableStateOf(getVehicle("reg", "")) }
    var variant by remember { mutableStateOf(getVehicle("variant", "DLX Pro")) }
    var odo by remember { mutableStateOf(getVehicle("odo", "")) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionTitle("Your motorcycle", "Keep your vehicle information up to date.")
            Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsBike, tint = Teal, modifier = Modifier.size(40.dp), contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(name, color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("$year · $variant", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("VEHICLE PROFILE", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    DarkField("Vehicle name", name, { name = it })
                    DarkField("Manufacturing year", year, { year = it }, KeyboardType.Number)
                    DarkField("Registration number", reg, { reg = it })
                    DarkField("Variant / colour", variant, { variant = it })
                    DarkField("Current odometer (km)", odo, { odo = it }, KeyboardType.Decimal)
                    Button(onClick = {
                        saveVehicle(mapOf("name" to name, "year" to year, "reg" to reg, "variant" to variant, "odo" to odo))
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink)) {
                        Text("SAVE VEHICLE DETAILS", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, tint = Teal, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Bluetooth discovery", color = White, fontWeight = FontWeight.Bold)
                    }
                    Text(bluetoothStatus, color = Muted, fontSize = 12.sp)
                    OutlinedButton(onClick = onScanBluetooth, modifier = Modifier.fillMaxWidth()) {
                        Text("SCAN NEARBY BLE DEVICES", color = Teal)
                    }
                    Text("BLE scanning discovers nearby devices only. Reading live ECU values requires a compatible diagnostic adapter and verified protocol for this exact bike.", color = Orange, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun TripsScreen(
    trips: List<TripRecord>, liveDistance: Double, liveSpeed: Double, liveMinutes: Int,
    onStart: () -> Unit, onStop: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionTitle("Ride tracking", "GPS-based trip distance and speed estimate.")
            Surface(
                color = Panel, shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF254357))
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("CURRENT TRIP DISTANCE", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Text(String.format(Locale.US, "%.2f", liveDistance), color = Teal, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                    Text("KILOMETRES", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(15.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricPill("CURRENT SPEED", "${liveSpeed.toInt()} km/h", Modifier.weight(1f))
                        MetricPill("DURATION", "$liveMinutes min", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("START TRIP", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Stop, contentDescription = null, tint = Orange)
                        Text("STOP & SAVE TRIP", color = Orange)
                    }
                    Text("Keep GPS enabled. Tracking runs in a foreground service while the screen is off.", color = Muted, fontSize = 10.sp)
                }
            }
        }
        item { SectionTitle("Ride history", "Your most recent saved trips.") }
        if (trips.isEmpty()) item { EmptyState("No trips recorded yet", "Start a ride to build your daily travel history.") }
        items(trips, key = { it.id }) { trip ->
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF1A3A45)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Route, tint = Teal, contentDescription = null)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(trip.date, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Max ${trip.maxSpeed.toInt()} km/h · ${trip.durationMinutes} min", color = Muted, fontSize = 11.sp)
                    }
                    Text(String.format(Locale.US, "%.2f km", trip.distanceKm), color = Teal, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun dbMileageForEntry(entries: List<FuelRecord>, fuelId: Long): Double? {
    val current = entries.indexOfFirst { it.id == fuelId }
    if (current < 0 || !entries[current].fullTank) return null
    val previous = (current - 1 downTo 0).firstOrNull { entries[it].fullTank } ?: return null
    val km = entries[current].odometerKm - entries[previous].odometerKm
    val litres = entries.subList(previous + 1, current + 1).sumOf { it.litres }
    return if (km > 0.0 && litres > 0.0) km / litres else null
}

@Composable
private fun FuelScreen(
    fuels: List<FuelRecord>,
    onSave: (String, Double, Double, Double, Boolean) -> String?
) {
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())) }
    var odo by remember { mutableStateOf("") }
    var litres by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var fullTank by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionTitle("Fuel & odometer", "Log every fill-up for reliable mileage reports.")
            Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("NEW FILL-UP", color = Teal, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                    DarkField("Date and time", date, { date = it })
                    DarkField("Odometer reading (km)", odo, { odo = it }, KeyboardType.Decimal)
                    DarkField("Petrol filled (litres)", litres, { litres = it }, KeyboardType.Decimal)
                    DarkField("Total amount paid (₹)", amount, { amount = it }, KeyboardType.Decimal)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = fullTank, onCheckedChange = { fullTank = it }, colors = CheckboxDefaults.colors(checkedColor = Teal))
                        Text("Full tank (recommended)", color = White, fontSize = 13.sp)
                    }
                    error?.let { Text(it, color = Color(0xFFFF8E8E), fontSize = 12.sp) }
                    savedMessage?.let { Text(it, color = Green, fontSize = 12.sp) }
                    Button(onClick = {
                        val o = odo.toDoubleOrNull()
                        val l = litres.toDoubleOrNull()
                        val a = amount.toDoubleOrNull()
                        if (o == null || l == null || a == null || o < 0 || l <= 0 || a < 0) {
                            error = "Enter a valid odometer, litres and amount."
                            savedMessage = null
                        } else {
                            error = onSave(date, o, l, a, fullTank)
                            savedMessage = if (error == null) "Fuel entry saved successfully." else null
                            if (error == null) { odo = ""; litres = ""; amount = "" }
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("SAVE FUEL ENTRY", fontWeight = FontWeight.Bold)
                    }
                    Text("Mileage is calculated between consecutive full-tank entries. Partial fills are recorded but not used as standalone mileage intervals.", color = Muted, fontSize = 10.sp)
                }
            }
        }
        item { SectionTitle("Fill-up history", "${fuels.size} recorded entries") }
        if (fuels.isEmpty()) item { EmptyState("No fuel entries yet", "Add your last petrol fill-up and odometer reading.") }
        items(fuels.asReversed(), key = { it.id }) { fuel ->
            val intervalMileage = if (fuel.fullTank) {
                // Database calculation includes partial fill-ups between full tanks.
                fuels.firstOrNull { it.id == fuel.id }?.let { dbMileageForEntry(fuels, it.id) }
            } else null
            Surface(color = Panel, shape = RoundedCornerShape(17.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fuel.date, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${fuel.odometerKm} km odometer · ${if (fuel.fullTank) "Full tank" else "Partial fill"}", color = Muted, fontSize = 11.sp)
                        }
                        Text(String.format(Locale.US, "₹%.0f", fuel.amount), color = Orange, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(String.format(Locale.US, "%.2f L filled", fuel.litres), color = White, fontSize = 12.sp)
                    if (intervalMileage != null) {
                        Text(String.format(Locale.US, "Actual mileage interval: %.2f km/L", intervalMileage),
                            color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Speed, tint = Teal, contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DarkField(label: String, value: String, onValue: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Muted, fontSize = 11.sp)
        OutlinedTextField(
            value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(),
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Teal, unfocusedBorderColor = Color(0xFF365065),
                focusedTextColor = White, unfocusedTextColor = White,
                cursorColor = Teal, focusedLabelColor = Teal,
                unfocusedContainerColor = Color(0xFF0D1C2A),
                focusedContainerColor = Color(0xFF0D1C2A)
            )
        )
    }
}
