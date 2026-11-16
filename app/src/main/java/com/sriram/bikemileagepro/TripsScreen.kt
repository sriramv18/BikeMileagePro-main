package com.sriram.bikemileagepro

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun placeLabel(place: String, lat: Double?, lon: Double?): String =
    place.ifBlank { GeoNames.coords(lat, lon) }

@SuppressLint("MissingPermission")
@Composable
internal fun TripsScreen(
    env: Env, vehicle: Vehicle, trips: List<TripRecord>,
    tripMileage: Map<Long, TripMileage?>, onOpenVehicle: () -> Unit
) {
    val ui = env.ui
    val live = ui.live
    val context = env.context
    var selectedTripId by remember { mutableStateOf<Long?>(null) }
    val selectedTrip = trips.firstOrNull { it.id == selectedTripId }
    val liveVehicleName = remember(live.vehicleId, env.ui.dataVersion) {
        env.db.vehicles().firstOrNull { it.id == live.vehicleId }?.name ?: ""
    }

    // Direct check of native phone Bluetooth connection
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter = bluetoothManager?.adapter
    val activeDevice = remember(ui.dataVersion, ui.obdState) {
        try {
            if (bluetoothAdapter?.isEnabled == true) {
                bluetoothAdapter.bondedDevices?.firstOrNull { device ->
                    try {
                        val method = device.javaClass.getMethod("isConnected")
                        method.invoke(device) as? Boolean == true
                    } catch (_: Exception) {
                        false
                    }
                }
            } else null
        } catch (_: SecurityException) {
            null
        }
    }

    // Automatically connect and pull fuel data as soon as native Bluetooth is connected
    LaunchedEffect(activeDevice) {
        if (activeDevice != null && ui.obdState == ObdState.DISCONNECTED) {
            val deviceName = try { activeDevice.name ?: "Bike Bluetooth" } catch (_: SecurityException) { "Bike Bluetooth" }
            val connection = ElmClassicConnection(context, env.db) { state, msg, _ ->
                ui.obdState = state
                ui.obdMessage = msg
                ui.connectedName = deviceName
                env.bump()
            }
            connection.connect(activeDevice)
        }
    }

    val isBluetoothConnected = activeDevice != null || ui.obdState == ObdState.ECU_READY || ui.obdState == ObdState.ADAPTER_READY

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionTitle("Ride tracking", "Distance, route and speed for ${vehicle.name}.")
        }
        item {
            // Native Bluetooth & Fuel Sync Status
            Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF20384B))) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CONNECTION STATUS", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isBluetoothConnected) StatusChip("BLUETOOTH CONNECTED", Green)
                        else StatusChip("BLUETOOTH DISCONNECTED", Red)
                    }
                    Text(
                        when {
                            bluetoothAdapter?.isEnabled != true -> "Turn on Bluetooth in your phone settings."
                            !isBluetoothConnected -> "Connect to your bike in your phone's Bluetooth settings to enable trip start and fuel sync."
                            else -> "Connected to ${ui.connectedName.ifBlank { "Bike" }} — Fuel consumption synced & ready."
                        },
                        color = Muted, fontSize = 11.sp
                    )
                }
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Color(0xFF254357))) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (live.state) {
                                TripState.IDLE -> "CURRENT TRIP DISTANCE"
                                TripState.RUNNING -> "TRACKING · ${liveVehicleName.uppercase()}"
                                TripState.PAUSED -> "PAUSED · ${liveVehicleName.uppercase()}"
                            },
                            color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f)
                        )
                        if (live.state == TripState.RUNNING) StatusChip("● LIVE", Teal)
                        if (live.state == TripState.PAUSED) StatusChip("❚❚ PAUSED", Orange)
                    }
                    Text(fmt(live.distanceKm, 2), color = Teal, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                    Text("KILOMETRES", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(15.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricPill("SPEED", "${live.speedKmh.toInt()} km/h", Modifier.weight(1f))
                        MetricPill("MAX", "${live.maxSpeed.toInt()} km/h", Modifier.weight(1f))
                        MetricPill("TIME", formatDuration(ui.elapsedMs), Modifier.weight(1f))
                    }
                    if (live.state != TripState.IDLE) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Start: " + if (live.startLat != null) GeoNames.coords(live.startLat, live.startLon) else "waiting for a GPS fix…",
                            color = Muted, fontSize = 11.sp
                        )
                        if (live.lastLat != null) Text("Now: " + GeoNames.coords(live.lastLat, live.lastLon), color = Muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(18.dp))

                    // Start Trip enabled ONLY when phone Bluetooth is connected
                    when (live.state) {
                        TripState.IDLE -> Button(
                            onClick = { env.actions.startTrip(vehicle.id) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isBluetoothConnected,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Teal,
                                contentColor = Ink,
                                disabledContainerColor = Color(0xFF152935)
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(
                                if (!isBluetoothConnected) "CONNECT BLUETOOTH TO START" else "START TRIP",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TripState.RUNNING -> Button(
                            onClick = { env.actions.pauseTrip() }, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Ink)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Text("PAUSE TRIP", fontWeight = FontWeight.Bold)
                        }
                        TripState.PAUSED -> Button(
                            onClick = { env.actions.resumeTrip() }, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("RESUME TRIP", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (live.state != TripState.IDLE) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { env.actions.stopTrip() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Orange)
                            Text("STOP & SAVE TRIP", color = Orange)
                        }
                    }
                }
            }
        }
        item { SectionTitle("Ride history", "Saved trips for ${vehicle.name}. Tap a trip for the route and mileage.") }
        if (trips.isEmpty()) item { EmptyState("No trips recorded yet", "Connect your bike via Bluetooth in phone settings and start a ride.") }
        items(trips, key = { it.id }) { trip ->
            val m = tripMileage[trip.id]
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().clickable { selectedTripId = trip.id }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF1A3A45)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Route, tint = Teal, contentDescription = null)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(trip.date, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            placeLabel(trip.startPlace, trip.startLat, trip.startLon) + "  →  " +
                                placeLabel(trip.endPlace, trip.endLat, trip.endLon),
                            color = Muted, fontSize = 10.sp, maxLines = 2
                        )
                        Text("Max ${trip.maxSpeed.toInt()} km/h · ${trip.durationMinutes} min", color = Muted, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmt(trip.distanceKm, 2) + " km", color = Teal, fontWeight = FontWeight.Bold)
                        if (m != null) {
                            Text(fmt(m.kmPerL, 1) + " km/L", color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(m.source, color = Muted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }

    selectedTrip?.let { trip ->
        TripDetailDialog(env, trip, tripMileage[trip.id], onClose = { selectedTripId = null })
    }
}

@Composable
private fun TripDetailDialog(env: Env, trip: TripRecord, mileage: TripMileage?, onClose: () -> Unit) {
    var startPlace by remember(trip.id) { mutableStateOf(trip.startPlace) }
    var endPlace by remember(trip.id) { mutableStateOf(trip.endPlace) }
    var fuelText by remember(trip.id) { mutableStateOf(trip.fuelUsedLitres?.let { fmt(it, 2) } ?: "") }
    var fuelError by remember(trip.id) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(trip.id) { mutableStateOf(false) }
    val route = remember(trip.id) { RouteCodec.decode(trip.route) }

    LaunchedEffect(trip.id) {
        if ((startPlace.isBlank() && trip.startLat != null && trip.startLon != null) ||
            (endPlace.isBlank() && trip.endLat != null && trip.endLon != null)) {
            val (s, e) = withContext(Dispatchers.IO) {
                val s = if (startPlace.isBlank() && trip.startLat != null && trip.startLon != null)
                    GeoNames.reverse(env.context, trip.startLat, trip.startLon) else null
                val e = if (endPlace.isBlank() && trip.endLat != null && trip.endLon != null)
                    GeoNames.reverse(env.context, trip.endLat, trip.endLon) else null
                s to e
            }
            if (s != null) startPlace = s
            if (e != null) endPlace = e
            if (s != null || e != null) env.db.updateTripPlaces(trip.id, startPlace, endPlace)
        }
    }

    val avgSpeed = if (trip.durationMinutes > 0) trip.distanceKm / (trip.durationMinutes / 60.0) else 0.0

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Panel,
        title = { Text("Trip details", color = White, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(trip.date, color = Muted)
                if (route.isNotEmpty()) {
                    RouteMap(route)
                    Text("● start    ● end  (route preview, no map key needed)", color = Muted, fontSize = 10.sp)
                } else {
                    Text("No route was recorded for this trip.", color = Muted, fontSize = 11.sp)
                }
                Text("From: " + placeLabel(startPlace, trip.startLat, trip.startLon), color = White)
                Text("To: " + placeLabel(endPlace, trip.endLat, trip.endLon), color = White)
                Row {
                    if (trip.startLat != null && trip.startLon != null) {
                        TextButton(onClick = { env.actions.openMap(trip.startLat, trip.startLon, "Trip start") }) {
                            Text("OPEN START IN MAPS", color = Teal, fontSize = 11.sp)
                        }
                    }
                    if (trip.endLat != null && trip.endLon != null) {
                        TextButton(onClick = { env.actions.openMap(trip.endLat, trip.endLon, "Trip end") }) {
                            Text("OPEN END", color = Teal, fontSize = 11.sp)
                        }
                    }
                }
                Text("Distance: " + fmt(trip.distanceKm, 2) + " km", color = White)
                Text("Duration: ${trip.durationMinutes} min · average " + fmt(avgSpeed, 1) + " km/h", color = White)
                Text("Maximum speed: " + fmt(trip.maxSpeed, 1) + " km/h", color = White)
                if (mileage != null) {
                    Text(
                        "Mileage: " + fmt(mileage.kmPerL, 2) + " km/L (" + mileage.source + ")", color = Green, fontWeight = FontWeight.Bold
                    )
                    val litres = trip.distanceKm / mileage.kmPerL
                    Text("Fuel for this trip: " + fmt(litres, 2) + " L" + if (mileage.source == "Actual") "" else " (estimated)", color = Muted, fontSize = 12.sp)
                } else {
                    Text("Mileage: not available yet — add two full-tank fuel entries or enter the litres used below.", color = Muted, fontSize = 12.sp)
                }
                DarkField("Actual fuel used on this trip (litres, optional)", fuelText, { fuelText = it }, KeyboardType.Decimal)
                fuelError?.let { Text(it, color = Red, fontSize = 11.sp) }
                OutlinedButton(onClick = {
                    val trimmed = fuelText.trim()
                    if (trimmed.isEmpty()) {
                        env.db.updateTripFuelUsed(trip.id, null); fuelError = null; env.bump()
                    } else {
                        val v = trimmed.toDoubleOrNull()
                        if (v == null || v <= 0.0) fuelError = "Enter litres greater than zero."
                        else { env.db.updateTripFuelUsed(trip.id, v); fuelError = null; env.bump() }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("SAVE FUEL USED", color = Teal) }
                Text(
                    "Without litres, mileage is an estimate from your full-tank fuel entries; it cannot be measured per trip from GPS alone.",
                    color = Muted, fontSize = 10.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("CLOSE", color = Teal) } },
        dismissButton = { TextButton(onClick = { confirmDelete = true }) { Text("DELETE TRIP", color = Orange) } }
    )

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = Panel,
        title = { Text("Delete this trip?", color = White, fontWeight = FontWeight.Bold) },
        text = { Text("This cannot be undone.", color = Muted) },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                env.db.deleteTrip(trip.id)
                env.bump()
                onClose()
            }) { Text("DELETE", color = Orange) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("CANCEL", color = Teal) } }
    )
}
