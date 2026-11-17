package com.sriram.bikemileagepro

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsScreen(env: Env) {
    val prefs = remember { env.context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var mapKey by remember { mutableStateOf(prefs.getString("google_maps_api_key", "") ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var metric by remember {
        mutableStateOf(ChartMetric.entries.firstOrNull { it.name == prefs.getString("chart_metric", "MILEAGE") } ?: ChartMetric.MILEAGE)
    }
    var period by remember {
        mutableStateOf(Period.entries.firstOrNull { it.name == prefs.getString("chart_period", "DAY") } ?: Period.DAY)
    }
    var display by remember {
        mutableStateOf(ChartDisplay.entries.firstOrNull { it.name == prefs.getString("chart_display", "BAR") } ?: ChartDisplay.BAR)
    }
    var saved by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionTitle("Settings", "Configure maps, dashboard charts and fuel preferences.")
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("MAP CONFIGURATION", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    OutlinedTextField(
                        value = mapKey,
                        onValueChange = { mapKey = it; saved = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Google Maps API key") },
                        singleLine = true,
                        visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White, unfocusedTextColor = White,
                            focusedBorderColor = Teal, unfocusedBorderColor = Muted
                        )
                    )
                    Text(
                        "The current trip route preview is drawn offline and does not require an API key. This setting stores your key for a future Google Maps SDK integration; it does not activate live Google Maps tiles yet. Restrict the key to this Android package and signing certificate in Google Cloud.",
                        color = Muted, fontSize = 11.sp
                    )
                    Button(
                        onClick = {
                            prefs.edit().putString("google_maps_api_key", mapKey.trim()).apply()
                            saved = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("SAVE MAP KEY") }
                    if (saved) Text("Map key setting saved on this device.", color = Green, fontSize = 11.sp)
                }
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DASHBOARD CHART DEFAULTS", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Default chart metric", color = Muted, fontSize = 12.sp)
                    PrefDropdown(metric.label, ChartMetric.entries.map { it.label }) { label ->
                        metric = ChartMetric.entries.first { it.label == label }
                        prefs.edit().putString("chart_metric", metric.name).apply()
                    }
                    Text("Default time range", color = Muted, fontSize = 12.sp)
                    PrefDropdown(period.label, Period.entries.map { it.label }) { label ->
                        period = Period.entries.first { it.label == label }
                        prefs.edit().putString("chart_period", period.name).apply()
                    }
                    Text("Chart style", color = Muted, fontSize = 12.sp)
                    PrefDropdown(display.label, ChartDisplay.entries.map { it.label }) { label ->
                        display = ChartDisplay.entries.first { it.label == label }
                        prefs.edit().putString("chart_display", display.name).apply()
                    }
                    Text("These defaults are saved and applied the next time you open the dashboard.", color = Muted, fontSize = 10.sp)
                }
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("FUEL & DATA", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    val auto = prefs.getBoolean("fuel_auto_read", false)
                    Text("Bluetooth fuel auto-read: ${if (auto) "Enabled" else "Disabled"}", color = White, fontSize = 13.sp)
                    Text("Fuel entries are stored in the local SQLite database per vehicle. Dashboard mileage is calculated after two full-tank entries with increasing odometer readings; partial fills are included between those full tanks.", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun PrefDropdown(value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(value)
            Spacer(Modifier.width(8.dp))
            Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
    }
}
