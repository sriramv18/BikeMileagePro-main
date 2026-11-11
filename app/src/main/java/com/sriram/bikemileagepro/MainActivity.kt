package com.sriram.bikemileagepro

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var db: TripDatabase
    private lateinit var content: LinearLayout
    private lateinit var nav: LinearLayout
    private var selectedTab = 0
    private var chartPeriod = "All"
    private var liveDistance = 0.0
    private var liveSpeed = 0.0
    private var liveMinutes = 0
    private var bluetoothStatus = "Not connected — scan only discovers nearby BLE devices"
    private val prefs by lazy { getSharedPreferences("vehicle_profile", MODE_PRIVATE) }

    private val rideReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RideTrackingService.ACTION_UPDATE) {
                liveDistance = intent.getDoubleExtra("distance_km", 0.0)
                liveSpeed = intent.getDoubleExtra("speed_kmh", 0.0)
                liveMinutes = intent.getIntExtra("duration_minutes", 0)
                if (selectedTab == 0 || selectedTab == 2) render()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = TripDatabase(this)
        requestPermissionsIfNeeded()
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 14f, color: Int = 0xFF17212B.toInt(), bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(2), dp(5), dp(2), dp(5))
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(13), dp(16), dp(13))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.WHITE); cornerRadius = dp(16).toFloat()
            setStroke(dp(1), 0xFFE5EAF0.toInt())
        }
    }

    private fun field(hintText: String, value: String = "", decimal: Boolean = true): EditText =
        EditText(this).apply {
            hint = hintText
            setText(value)
            textSize = 15f
            setTextColor(0xFF17212B.toInt())
            setHintTextColor(0xFF82909D.toInt())
            inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        else InputType.TYPE_CLASS_TEXT
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFF7F9FB.toInt()); cornerRadius = dp(10).toFloat()
                setStroke(dp(1), 0xFFDCE3EA.toInt())
            }
        }

    private fun button(label: String, primary: Boolean = true, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(if (primary) Color.WHITE else 0xFF176B87.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (primary) 0xFF176B87.toInt() else 0xFFE8F3F6.toInt()
            )
            setOnClickListener { action() }
        }

    private fun addGap(parent: LinearLayout, height: Int = 10) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun addCard(parent: LinearLayout, title: String, body: LinearLayout.() -> Unit) {
        val c = card()
        c.addView(text(title, 16f, 0xFF17212B.toInt(), true))
        c.body()
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF3F6F8.toInt()) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(17), dp(20), dp(12))
            setBackgroundColor(0xFF102A36.toInt())
        }
        header.addView(text("BIKE MILEAGE PRO", 19f, Color.WHITE, true))
        header.addView(text("Your ride. Your fuel. Your mileage.", 12f, 0xFFB9D0D9.toInt()))
        root.addView(header)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(15), dp(16), dp(12)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = LinearLayout(this).apply {
            setPadding(dp(4), dp(5), dp(4), dp(5)); setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }
        val labels = listOf("Home", "Vehicle", "Trips", "Fuel")
        val icons = listOf("⌂", "▣", "↗", "⛽")
        labels.forEachIndexed { index, label ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(4), dp(5), dp(4), dp(5))
                setOnClickListener { selectedTab = index; render() }
            }
            item.addView(text(icons[index], 19f, if (selectedTab == index) 0xFF176B87.toInt() else 0xFF788692.toInt(), true).apply { gravity = Gravity.CENTER })
            item.addView(text(label, 11f, if (selectedTab == index) 0xFF176B87.toInt() else 0xFF788692.toInt(), selectedTab == index).apply { gravity = Gravity.CENTER })
            nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav)
        setContentView(root)
        when (selectedTab) {
            0 -> dashboard()
            1 -> vehicle()
            2 -> trips()
            3 -> fuel()
        }
    }

    private fun dashboard() {
        content.addView(text("Dashboard", 23f, 0xFF17212B.toInt(), true))
        content.addView(text("Honda H’ness CB350 DLX Pro • 2026", 13f, 0xFF637381.toInt()))
        addGap(content, 10)
        val mileage = db.overallMileage()
        val fuels = db.fuels()
        val trips = db.trips()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun metric(label: String, value: String, color: Int) {
            val c = card()
            c.addView(text(label, 11f, 0xFF64748B.toInt()))
            c.addView(text(value, 22f, color, true))
            top.addView(c, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(7) })
        }
        metric("OVERALL MILEAGE", if (mileage > 0) String.format(Locale.US, "%.1f km/L", mileage) else "— km/L", 0xFF176B87.toInt())
        metric("TOTAL TRIP KM", String.format(Locale.US, "%.1f km", db.totalTripDistance()), 0xFF1C7C54.toInt())
        content.addView(top)
        addGap(content)
        addCard(content, "Fuel & spending") {
            addView(text("Fill-ups recorded: ${fuels.size}", 13f))
            addView(text("Total fuel spend: ₹${String.format(Locale.US, "%.0f", db.totalFuelSpend())}", 13f))
            addView(text("Last odometer: ${fuels.lastOrNull()?.odometerKm?.let { String.format(Locale.US, "%.1f km", it) } ?: "Not entered"}", 13f))
            addView(text("Mileage appears after two full-tank entries with increasing odometer readings.", 11f, 0xFF788692.toInt()))
        }
        addCard(content, "Mileage chart") {
            val tabs = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("All", "Recent 7", "Recent 30").forEach { period ->
                tabs.addView(button(period, chartPeriod == period) { chartPeriod = period; render() },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
            }
            addView(tabs)
            val points = db.mileagePoints().let {
                when (chartPeriod) { "Recent 7" -> it.takeLast(7); "Recent 30" -> it.takeLast(30); else -> it }
            }
            val chart = MileageChartView(this@MainActivity).apply {
                values = points.map { it.label to it.value }
                layoutParams = LinearLayout.LayoutParams(-1, dp(190))
            }
            addView(chart)
        }
        addCard(content, "Current trip") {
            addView(text(String.format(Locale.US, "%.2f km travelled", liveDistance), 23f, 0xFF176B87.toInt(), true))
            addView(text(String.format(Locale.US, "%.0f km/h • %d min", liveSpeed, liveMinutes), 13f, 0xFF64748B.toInt()))
            addView(button("OPEN TRIP TRACKING") { selectedTab = 2; render() })
        }
        addCard(content, "Recent activity") {
            val latestFuel = fuels.lastOrNull()
            addView(text(latestFuel?.let { "Last fuel: ${it.date} • ${it.litres} L • ₹${it.amount}" } ?: "No fuel entries yet", 12f))
            addView(text(trips.firstOrNull()?.let { "Last ride: ${it.date} • ${"%.2f".format(it.distanceKm)} km" } ?: "No rides saved yet", 12f))
        }
    }

    private fun vehicle() {
        content.addView(text("Vehicle details", 23f, 0xFF17212B.toInt(), true))
        content.addView(text("Edit your bike profile. Values are saved on this phone.", 13f, 0xFF637381.toInt()))
        val name = field("Vehicle name", prefs.getString("name", "Honda H’ness CB350 DLX Pro") ?: "", false)
        val year = field("Manufacturing year", prefs.getString("year", "2026") ?: "", false)
        val reg = field("Registration number (optional)", prefs.getString("reg", "") ?: "", false)
        val odo = field("Current odometer reading (km)", prefs.getString("odo", "") ?: "")
        val colour = field("Colour / variant (optional)", prefs.getString("variant", "DLX Pro") ?: "", false)
        addCard(content, "Bike profile") {
            addView(text("Vehicle name")); addView(name)
            addGap(this)
            addView(text("Year")); addView(year)
            addGap(this)
            addView(text("Registration")); addView(reg)
            addGap(this)
            addView(text("Variant / colour")); addView(colour)
            addGap(this)
            addView(text("Current odometer (km)")); addView(odo)
            addView(button("SAVE VEHICLE DETAILS") {
                prefs.edit().putString("name", name.text.toString()).putString("year", year.text.toString())
                    .putString("reg", reg.text.toString()).putString("variant", colour.text.toString())
                    .putString("odo", odo.text.toString()).apply()
                Toast.makeText(this@MainActivity, "Vehicle details saved", Toast.LENGTH_SHORT).show()
            })
        }
        addCard(content, "Bluetooth connection") {
            addView(text(bluetoothStatus, 12f, 0xFF64748B.toInt()))
            addView(button("SCAN FOR BLE DEVICES") { scanBluetooth() })
            addView(text("A scan only lists nearby BLE devices. Live ECU data requires a compatible OBD adapter and a verified protocol for this exact motorcycle.", 11f, 0xFF9A5B13.toInt()))
        }
    }

    private fun trips() {
        content.addView(text("Trip tracking", 23f, 0xFF17212B.toInt(), true))
        content.addView(text("Start GPS tracking before moving. Stop after parking safely.", 13f, 0xFF637381.toInt()))
        addCard(content, "Live ride") {
            addView(text(String.format(Locale.US, "%.2f km", liveDistance), 34f, 0xFF176B87.toInt(), true))
            addView(text(String.format(Locale.US, "%.0f km/h • %d minutes", liveSpeed, liveMinutes), 14f))
            addView(button("START TRIP") { startRide() })
            addView(button("STOP & SAVE TRIP", false) { stopRide() })
            addView(text("GPS distance is an estimate and may differ from the bike odometer. The phone must retain location permission and GPS access.", 11f, 0xFF9A5B13.toInt()))
        }
        addCard(content, "Saved trips") {
            val items = db.trips()
            if (items.isEmpty()) addView(text("No trips saved yet."))
            items.forEach {
                addView(text("${it.date}  •  ${"%.2f".format(it.distanceKm)} km", 14f, 0xFF17212B.toInt(), true))
                addView(text("Max speed ${it.maxSpeed.toInt()} km/h • ${it.durationMinutes} min", 12f, 0xFF64748B.toInt()))
                addView(View(this@MainActivity).apply { setBackgroundColor(0xFFE7EDF1.toInt()) },
                    LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
    }

    private fun fuel() {
        content.addView(text("Fuel & odometer", 23f, 0xFF17212B.toInt(), true))
        content.addView(text("Record every petrol fill-up with the bike's odometer reading.", 13f, 0xFF637381.toInt()))
        val date = field("Date and time (auto)", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()), false)
        val odo = field("Odometer reading (km)")
        val litres = field("Petrol filled (litres)")
        val amount = field("Total amount paid (₹)")
        var fullTank = true
        val full = CheckBox(this).apply {
            text = "Filled tank completely (recommended for accurate mileage)"
            isChecked = true
            setTextColor(0xFF17212B.toInt())
            setOnCheckedChangeListener { _, checked -> fullTank = checked }
        }
        addCard(content, "Add petrol fill-up") {
            addView(text("Fill-up date")); addView(date)
            addView(text("Bike odometer reading")); addView(odo)
            addView(text("Litres filled")); addView(litres)
            addView(text("Amount paid")); addView(amount)
            addView(full)
            addView(button("SAVE FUEL & ODOMETER ENTRY") {
                val o = odo.text.toString().toDoubleOrNull()
                val l = litres.text.toString().toDoubleOrNull()
                val a = amount.text.toString().toDoubleOrNull()
                if (o == null || l == null || a == null || o < 0 || l <= 0 || a < 0) {
                    Toast.makeText(this@MainActivity, "Enter valid odometer, litres and amount", Toast.LENGTH_LONG).show()
                } else {
                    val existing = db.fuels().lastOrNull()
                    if (existing != null && o <= existing.odometerKm) {
                        Toast.makeText(this@MainActivity, "Odometer must be higher than the previous entry (${existing.odometerKm} km).", Toast.LENGTH_LONG).show()
                    } else {
                        val enteredDate = date.text.toString().ifBlank { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()) }
                        db.saveFuel(enteredDate, l, a, o, fullTank)
                        prefs.edit().putString("odo", o.toString()).apply()
                        Toast.makeText(this@MainActivity, "Fuel and odometer saved", Toast.LENGTH_SHORT).show()
                        selectedTab = 0; render()
                    }
                }
            })
        }
        addCard(content, "Fuel history & mileage") {
            val entries = db.fuels()
            if (entries.isEmpty()) addView(text("No fuel entries yet."))
            entries.asReversed().forEach { item ->
                addView(text("${item.date} • ${item.odometerKm} km", 14f, 0xFF17212B.toInt(), true))
                addView(text("${item.litres} L • ₹${item.amount} • ${if (item.fullTank) "Full tank" else "Partial fill"}", 12f, 0xFF64748B.toInt()))
                db.mileageForFuelEntry(item.id)?.let { calculated ->
                    addView(text(String.format(Locale.US, "Calculated: %.2f km/L", calculated), 13f, 0xFF1C7C54.toInt(), true))
                }
                addGap(this, 6)
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
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
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 100)
    }

    private fun startRide() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsIfNeeded()
            Toast.makeText(this, "Allow location permission, then tap Start Trip again.", Toast.LENGTH_LONG).show()
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Please enable phone Location/GPS.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, RideTrackingService::class.java).setAction(RideTrackingService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "Trip tracking started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRide() {
        startService(Intent(this, RideTrackingService::class.java).setAction(RideTrackingService.ACTION_STOP))
        Toast.makeText(this, "Trip stopped and saved if GPS distance exceeded 20 metres.", Toast.LENGTH_SHORT).show()
        content.postDelayed({ if (selectedTab == 2 || selectedTab == 0) render() }, 900)
    }

    @Suppress("MissingPermission")
    private fun scanBluetooth() {
        if (Build.VERSION.SDK_INT >= 31 &&
            (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
             checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissionsIfNeeded()
            bluetoothStatus = "Grant Nearby devices permission and scan again."
            return
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) { bluetoothStatus = "Turn on Bluetooth first."; render(); return }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) { bluetoothStatus = "BLE scanner unavailable."; render(); return }
        val devices = linkedSetOf<String>()
        bluetoothStatus = "Scanning nearby BLE devices for 10 seconds…"
        render()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = try { result.device.name ?: "Unnamed BLE device" } catch (_: SecurityException) { "Protected device" }
                devices.add("$name • ${result.device.address} • ${result.rssi} dBm")
                runOnUiThread {
                    bluetoothStatus = if (devices.isEmpty()) "Scanning…" else devices.take(12).joinToString("\n")
                    if (selectedTab == 1) render()
                }
            }
            override fun onScanFailed(errorCode: Int) {
                runOnUiThread { bluetoothStatus = "Bluetooth scan failed: $errorCode"; if (selectedTab == 1) render() }
            }
        }
        try {
            scanner.startScan(callback)
            window.decorView.postDelayed({
                try { scanner.stopScan(callback) } catch (_: SecurityException) {}
                if (devices.isEmpty()) bluetoothStatus = "No BLE devices found. A powered compatible adapter may be needed."
                else bluetoothStatus = "Found ${devices.size} device(s):\n" + devices.take(12).joinToString("\n")
                if (selectedTab == 1) render()
            }, 10_000L)
        } catch (_: SecurityException) {
            bluetoothStatus = "Bluetooth permission denied."; render()
        }
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
