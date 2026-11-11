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
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var distanceText: TextView
    private lateinit var speedText: TextView
    private lateinit var bluetoothText: TextView
    private lateinit var historyText: TextView
    private lateinit var db: TripDatabase

    private val rideReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RideTrackingService.ACTION_UPDATE) {
                distanceText.text = String.format(Locale.US, "%.2f km", intent.getDoubleExtra("distance_km", 0.0))
                speedText.text = String.format(Locale.US, "%.0f km/h", intent.getDoubleExtra("speed_kmh", 0.0))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = TripDatabase(this)
        buildScreen()
        requestPermissionsIfNeeded()
        refreshHistory()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 36, 32, 32)
            setBackgroundColor(0xFF101820.toInt())
        }
        fun text(value: String, size: Float, color: Int = 0xFFFFFFFF.toInt()) =
            TextView(this).apply { this.text = value; textSize = size; setTextColor(color); setPadding(0, 9, 0, 9) }
        fun button(value: String, action: () -> Unit) =
            Button(this).apply { text = value; setOnClickListener { action() } }

        page.addView(text("BIKE MILEAGE PRO", 25f))
        page.addView(text("2026 Honda H’ness CB350 DLX Pro", 15f, 0xFFB8C7D9.toInt()))
        page.addView(text("TRIP DISTANCE", 13f, 0xFF9FB3C8.toInt()))
        distanceText = text("0.00 km", 36f); page.addView(distanceText)
        page.addView(text("LIVE GPS SPEED", 13f, 0xFF9FB3C8.toInt()))
        speedText = text("0 km/h", 27f); page.addView(speedText)
        page.addView(button("START RIDE TRACKING") { startRide() })
        page.addView(button("STOP AND SAVE RIDE") { stopRide() })
        page.addView(text("GPS tracking uses a persistent notification. Securely mount the phone and never operate it while riding.", 12f, 0xFFFFD166.toInt()))

        page.addView(text("FUEL LOG", 20f))
        page.addView(text("Record every full-tank fill and the bike odometer reading.", 12f, 0xFFB8C7D9.toInt()))
        val litres = EditText(this).apply { hint = "Litres filled"; inputType = 8194; setTextColor(-1) }
        val amount = EditText(this).apply { hint = "Amount paid (₹)"; inputType = 8194; setTextColor(-1) }
        val odo = EditText(this).apply { hint = "Bike odometer reading (km)"; inputType = 8194; setTextColor(-1) }
        page.addView(litres); page.addView(amount); page.addView(odo)
        page.addView(button("SAVE FUEL FILL-UP") {
            val l = litres.text.toString().toDoubleOrNull()
            val a = amount.text.toString().toDoubleOrNull()
            val o = odo.text.toString().toDoubleOrNull()
            if (l != null && a != null && o != null && l > 0 && a >= 0 && o >= 0) {
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                db.saveFuel(date, l, a, o)
                Toast.makeText(this, "Fuel fill-up saved", Toast.LENGTH_SHORT).show()
                litres.text.clear(); amount.text.clear(); odo.text.clear()
                refreshHistory()
            } else Toast.makeText(this, "Enter valid fuel, amount and odometer values", Toast.LENGTH_SHORT).show()
        })

        page.addView(text("BLUETOOTH DEVICE DISCOVERY", 20f))
        bluetoothText = text("No adapter selected. Scan to see nearby BLE devices.", 12f, 0xFFB8C7D9.toInt())
        page.addView(bluetoothText)
        page.addView(button("SCAN NEARBY BLE DEVICES") { scanBluetooth() })
        page.addView(text("BLE scanning discovers nearby advertisements only. It does not prove Honda ECU compatibility or read fuel data.", 12f, 0xFFFFD166.toInt()))

        page.addView(text("TRIP & FUEL HISTORY", 20f))
        historyText = text("No saved records yet.", 12f, 0xFFB8C7D9.toInt())
        page.addView(historyText)
        page.addView(button("REFRESH HISTORY") { refreshHistory() })
        page.addView(text("GPS estimates distance; it cannot measure petrol use. True km/L needs consecutive full-tank records or validated ECU fuel-rate data.", 12f, 0xFFFFD166.toInt()))
        scroll.addView(page)
        setContentView(scroll)
    }

    private fun requestPermissionsIfNeeded() {
        val required = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
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
            Toast.makeText(this, "Allow location permission, then tap Start again.", Toast.LENGTH_LONG).show()
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Please enable phone Location/GPS.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, RideTrackingService::class.java).setAction(RideTrackingService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "Ride tracking started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRide() {
        startService(Intent(this, RideTrackingService::class.java).setAction(RideTrackingService.ACTION_STOP))
        Toast.makeText(this, "Ride stopped. Saved if distance exceeded 20 metres.", Toast.LENGTH_SHORT).show()
        distanceText.postDelayed({ refreshHistory() }, 700)
    }

    @Suppress("MissingPermission")
    private fun scanBluetooth() {
        if (Build.VERSION.SDK_INT >= 31 &&
            (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
             checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissionsIfNeeded()
            bluetoothText.text = "Grant nearby-device permission and scan again."
            return
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) { bluetoothText.text = "Turn on Bluetooth first."; return }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) { bluetoothText.text = "BLE scanner unavailable."; return }
        val devices = linkedSetOf<String>()
        bluetoothText.text = "Scanning for 10 seconds…"
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = try { result.device.name ?: "Unnamed BLE device" } catch (_: SecurityException) { "Protected device" }
                devices.add("$name | ${result.device.address} | ${result.rssi} dBm")
                runOnUiThread { bluetoothText.text = "Nearby BLE devices:\\n" + devices.take(15).joinToString("\\n") }
            }
            override fun onScanFailed(errorCode: Int) {
                runOnUiThread { bluetoothText.text = "Scan failed: $errorCode" }
            }
        }
        try {
            scanner.startScan(callback)
            bluetoothText.postDelayed({
                try { scanner.stopScan(callback) } catch (_: SecurityException) {}
                if (devices.isEmpty()) bluetoothText.text = "No BLE advertisements found. A powered OBD adapter is needed for a useful test."
            }, 10_000L)
        } catch (_: SecurityException) { bluetoothText.text = "Bluetooth permission denied." }
    }

    private fun refreshHistory() {
        val trips = db.trips()
        val fuels = db.fuels()
        val tripLines = if (trips.isEmpty()) "No saved rides yet." else trips.joinToString("\\n") {
            "${it.date} • ${String.format(Locale.US, "%.2f km", it.distanceKm)} • max ${it.maxSpeed.toInt()} km/h"
        }
        val fuelLines = if (fuels.isEmpty()) "No fuel entries yet." else fuels.joinToString("\\n") {
            "${it.date} • ${it.litres} L • ₹${it.amount} • odometer ${it.odometerKm} km"
        }
        historyText.text = "RIDES\\n$tripLines\\n\\nFUEL ENTRIES\\n$fuelLines"
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
