package com.sriram.bikemileagepro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun FuelScreen(env: Env, vehicle: Vehicle, fuels: List<FuelRecord>, intervals: List<FuelInterval>) {
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())) }
    var odo by remember(vehicle.id) { mutableStateOf("") }
    var litres by remember(vehicle.id) { mutableStateOf("") }
    var amount by remember(vehicle.id) { mutableStateOf("") }
    var fullTank by remember { mutableStateOf(true) }
    val fuelPrefs = remember { env.context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE) }
    var isAutoRead by remember {
        mutableStateOf(fuelPrefs.getBoolean("fuel_auto_read", false))
    }
    var error by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    val mileageByFuel = remember(intervals) { MileageCalc.mileageByFuelId(intervals) }
    val lastOdo = fuels.lastOrNull()?.odometerKm

    // Auto-populate from Bluetooth OBD if enabled and synced
    LaunchedEffect(isAutoRead, ObdFuelStore.latestFuelReading) {
        if (isAutoRead && ObdFuelStore.latestFuelReading.isNotBlank()) {
            litres = ObdFuelStore.latestFuelReading
        }
    }

    val o = odo.replace(',', '.').toDoubleOrNull()
    val l = litres.replace(',', '.').toDoubleOrNull()
    val a = amount.replace(',', '.').toDoubleOrNull()
    val preview = if (o != null && l != null) MileageCalc.previewNewEntry(fuels, o, l, fullTank) else null

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionTitle("Fuel & odometer", "Log every fill-up for ${vehicle.name}.")
            Spacer(Modifier.height(10.dp))
            Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("NEW FILL-UP", color = Teal, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp)

                    // Bluetooth Auto-read Option Toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isAutoRead,
                            onCheckedChange = {
                                isAutoRead = it
                                fuelPrefs.edit().putBoolean("fuel_auto_read", it).apply()
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Teal)
                        )
                        Text(
                            if (ObdFuelStore.latestFuelReading.isNotBlank())
                                "Auto-read fuel from Bluetooth (${ObdFuelStore.latestFuelReading} available)"
                            else
                                "Auto-read fuel from Bluetooth OBD",
                            color = White, fontSize = 13.sp
                        )
                    }

                    DarkField("Date and time (yyyy-MM-dd HH:mm)", date, { date = it })
                    DarkField(
                        "Odometer reading (km)" + if (lastOdo != null) " — last: " + fmt(lastOdo, 0) else "",
                        odo, { odo = it }, KeyboardType.Decimal
                    )
                    DarkField("Petrol filled (litres)", litres, { litres = it }, KeyboardType.Decimal)
                    DarkField("Total amount paid (₹)", amount, { amount = it }, KeyboardType.Decimal)

                    if (l != null && l > 0.0 && a != null && a > 0.0) {
                        Text("Price per litre: ₹" + fmt(a / l, 2), color = Muted, fontSize = 12.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = fullTank, onCheckedChange = { fullTank = it }, colors = CheckboxDefaults.colors(checkedColor = Teal))
                        Text("Tank filled to full", color = White, fontSize = 13.sp)
                    }

                    // Live calculation preview
                    Surface(color = Color(0xFF0F2333), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("MILEAGE PREVIEW", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                            if (preview != null) {
                                Text(fmt(preview.kmPerL, 2) + " km/L", color = Green, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text(
                                    fmt(preview.km, 1) + " km ÷ " + fmt(preview.litres, 2) + " L (includes any partial fills since the previous full tank)",
                                    color = Muted, fontSize = 10.sp
                                )
                            } else {
                                Text(
                                    when {
                                        !fullTank -> "Partial fill: litres are added to the next full-tank calculation."
                                        intervals.isEmpty() && fuels.none { it.fullTank } ->
                                            "First full tank is the baseline. Mileage starts from your next full-tank entry."
                                        else -> "Enter odometer and litres for a full tank to see the mileage."
                                    },
                                    color = Muted, fontSize = 11.sp
                                )
                            }
                        }
                    }

                    error?.let { Text(it, color = Red, fontSize = 12.sp) }
                    savedMessage?.let { Text(it, color = Green, fontSize = 12.sp) }

                    Button(onClick = {
                        if (o == null || l == null || a == null || o < 0 || l <= 0 || a < 0) {
                            error = "Enter a valid odometer, litres and amount."
                            savedMessage = null
                        } else {
                            val problem = env.db.validateFuelEntry(vehicle.id, l, a, o)
                            if (problem != null) {
                                error = problem; savedMessage = null
                            } else {
                                val savedId = env.db.saveFuel(vehicle.id, date.trim(), l, a, o, fullTank)
                                if (savedId <= 0L) {
                                    error = "Fuel entry could not be saved. Please try again."
                                    savedMessage = null
                                } else {
                                    error = null
                                    savedMessage = "Fuel entry saved permanently for ${vehicle.name}."
                                    odo = ""; litres = ""; amount = ""
                                    // Keep the Auto-read preference selected; it is stored in app settings.
                                    date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                                    env.bump()
                                }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("SAVE FUEL ENTRY", fontWeight = FontWeight.Bold)
                    }

                    Text(
                        "Mileage = distance between two consecutive full-tank fills ÷ litres filled since the earlier one. " +
                            "Fill the tank to the same level each time for an accurate result.",
                        color = Muted, fontSize = 10.sp
                    )
                }
            }
        }
        item { SectionTitle("Fill-up history", "${fuels.size} recorded entries") }
        if (fuels.isEmpty()) item { EmptyState("No fuel entries yet", "Add your last petrol fill-up and odometer reading.") }
        items(fuels.asReversed(), key = { it.id }) { fuel ->
            val m = mileageByFuel[fuel.id]
            val interval = intervals.firstOrNull { it.fuelId == fuel.id }
            Surface(color = Panel, shape = RoundedCornerShape(17.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fuel.date, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                fmt(fuel.odometerKm, 0) + " km odometer · " + if (fuel.fullTank) "Full tank" else "Partial fill",
                                color = Muted, fontSize = 11.sp
                            )
                        }
                        Text("₹" + fmt(fuel.amount, 0), color = Orange, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(fmt(fuel.litres, 2) + " L filled", color = White, fontSize = 12.sp)
                    if (m != null && interval != null) {
                        Text(
                            "Mileage: " + fmt(m, 2) + " km/L  (" + fmt(interval.km, 1) + " km ÷ " + fmt(interval.litres, 2) + " L)",
                            color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp
                        )
                    } else if (fuel.fullTank) {
                        Text("Baseline full tank — mileage starts from the next full tank.", color = Muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = { env.db.deleteFuel(fuel.id); env.bump() }) {
                        Text("DELETE ENTRY", color = Orange, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
