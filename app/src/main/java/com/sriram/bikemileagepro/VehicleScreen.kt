package com.sriram.bikemileagepro

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun VehicleScreen(
    env: Env, vehicles: List<Vehicle>, selected: Vehicle, onSelect: (Long) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Edit form is keyed on the selected vehicle so switching vehicle reloads the fields.
    var name by remember(selected.id) { mutableStateOf(selected.name) }
    var year by remember(selected.id) { mutableStateOf(selected.year) }
    var reg by remember(selected.id) { mutableStateOf(selected.reg) }
    var variant by remember(selected.id) { mutableStateOf(selected.variant) }
    var odo by remember(selected.id) { mutableStateOf(selected.odometer) }
    var savedNote by remember(selected.id) { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("Your vehicles", "Each vehicle keeps its own trips and fuel records.")
                }
                Button(
                    onClick = { showAdd = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("ADD", fontWeight = FontWeight.Bold)
                }
            }
        }
        items(vehicles, key = { it.id }) { v ->
            val active = v.id == selected.id
            Surface(
                color = Panel, shape = RoundedCornerShape(20.dp),
                border = BorderStroke(if (active) 2.dp else 1.dp, if (active) Teal else Color(0xFF20384B))
            ) {
                Row(Modifier.fillMaxWidth().clickable { onSelect(v.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DirectionsBike, tint = Teal, modifier = Modifier.size(36.dp), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.name, color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            listOf(v.year, v.variant, v.reg).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "No details yet" },
                            color = Muted, fontSize = 12.sp
                        )
                    }
                    if (active) StatusChip("ACTIVE", Teal) else Text("SELECT", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("EDIT: ${selected.name.uppercase()}", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    DarkField("Vehicle name", name, { name = it })
                    DarkField("Manufacturing year", year, { year = it }, KeyboardType.Number)
                    DarkField("Registration number", reg, { reg = it })
                    DarkField("Variant / colour", variant, { variant = it })
                    DarkField("Current odometer (km)", odo, { odo = it }, KeyboardType.Decimal)
                    savedNote?.let { Text(it, color = Green, fontSize = 12.sp) }
                    Button(
                        onClick = {
                            env.db.updateVehicle(Vehicle(selected.id, name, year, reg, variant, odo))
                            savedNote = "Vehicle details saved."
                            env.bump()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink)
                    ) { Text("SAVE VEHICLE DETAILS", fontWeight = FontWeight.Bold) }
                    if (vehicles.size > 1) {
                        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("DELETE THIS VEHICLE", color = Orange, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item { BluetoothCard(env) }
    }

    if (showAdd) AddVehicleDialog(
        onDismiss = { showAdd = false },
        onAdd = { n, y, r, v, o ->
            val id = env.db.addVehicle(n, y, r, v, o)
            showAdd = false
            env.bump()
            onSelect(id)
        }
    )

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = Panel,
        title = { Text("Delete ${selected.name}?", color = White, fontWeight = FontWeight.Bold) },
        text = { Text("This permanently deletes this vehicle together with all of its trips and fuel records.", color = Muted) },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                val others = vehicles.filter { it.id != selected.id }
                if (env.db.deleteVehicle(selected.id)) {
                    others.firstOrNull()?.let { onSelect(it.id) }
                    env.bump()
                }
            }) { Text("DELETE", color = Orange) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("CANCEL", color = Teal) } }
    )
}

@Composable
private fun AddVehicleDialog(
    onDismiss: () -> Unit, onAdd: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var reg by remember { mutableStateOf("") }
    var variant by remember { mutableStateOf("") }
    var odo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Add vehicle", color = White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkField("Vehicle name", name, { name = it })
                DarkField("Manufacturing year", year, { year = it }, KeyboardType.Number)
                DarkField("Registration number", reg, { reg = it })
                DarkField("Variant / colour", variant, { variant = it })
                DarkField("Current odometer (km)", odo, { odo = it }, KeyboardType.Decimal)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, year, reg, variant, odo) }, enabled = name.isNotBlank()) {
                Text("ADD VEHICLE", color = if (name.isNotBlank()) Teal else Muted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Muted) } }
    )
}

@SuppressLint("MissingPermission")
@Composable
private fun BluetoothCard(env: Env) {
    val context = env.context
    val ui = env.ui
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter = bluetoothManager?.adapter

    // Detect if phone's native Bluetooth is ON and actively connected to a paired device
    val activeDevice = remember(ui.dataVersion) {
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

    // Automatically connect Classic OBD transport when phone Bluetooth is connected to the bike
    LaunchedEffect(activeDevice) {
        if (activeDevice != null && ui.obdState == ObdState.DISCONNECTED) {
            val deviceName = try { activeDevice.name ?: "Bike OBD" } catch (_: SecurityException) { "Bike OBD" }
            val connection = ElmClassicConnection(context, env.db) { state, msg, fuelConsumed ->
                ui.obdState = state
                ui.obdMessage = msg
                ui.connectedName = deviceName
                if (fuelConsumed != null) {
                    ObdFuelStore.latestFuelReading = fuelConsumed
                }
                env.bump()
            }
            connection.connect(activeDevice)
        }
    }

    val connected = ui.obdState == ObdState.ECU_READY || ui.obdState == ObdState.ADAPTER_READY
    val busy = ui.obdState == ObdState.CONNECTING || ui.obdState == ObdState.DISCOVERING

    Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, tint = Teal, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Bike Bluetooth Connection", color = White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                obdChip(ui.obdState)
            }
            Text(
                when {
                    bluetoothAdapter?.isEnabled != true -> "Turn on Bluetooth in your phone settings."
                    activeDevice == null && !connected -> "Connect to your bike in phone Bluetooth settings first."
                    else -> (if (ui.connectedName.isNotBlank()) "${ui.connectedName} — " else "") + ui.obdMessage
                },
                color = if (activeDevice != null || connected) Green else Orange,
                fontSize = 12.sp
            )
            if (connected || busy) {
                OutlinedButton(onClick = { env.actions.disconnectBle() }, modifier = Modifier.fillMaxWidth()) {
                    Text("DISCONNECT", color = Orange)
                }
            }
            Text(
                "Automatically detects your active phone Bluetooth connection to read fuel consumption and enable trip tracking.",
                color = Muted, fontSize = 11.sp
            )
        }
    }
}

@Composable
internal fun obdChip(state: ObdState) {
    when (state) {
        ObdState.ECU_READY -> StatusChip("ECU CONNECTED", Green)
        ObdState.ADAPTER_READY -> StatusChip("ADAPTER ONLY", Orange)
        ObdState.CONNECTING, ObdState.DISCOVERING -> StatusChip("CONNECTING", Teal)
        ObdState.ERROR -> StatusChip("ERROR", Red)
        ObdState.DISCONNECTED -> StatusChip("NOT CONNECTED", Muted)
    }
}
