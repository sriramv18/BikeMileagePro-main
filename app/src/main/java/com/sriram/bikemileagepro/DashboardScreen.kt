package com.sriram.bikemileagepro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DashboardScreen(
    env: Env, vehicle: Vehicle, overall: Double, totalKm: Double, spend: Double,
    fuels: List<FuelRecord>, trips: List<TripRecord>, intervals: List<FuelInterval>,
    tripMileage: Map<Long, TripMileage?>, onOpenTrips: () -> Unit, onOpenFuel: () -> Unit
) {
    val ui = env.ui
    val live = ui.live
    var metric by remember { mutableStateOf(ChartMetric.MILEAGE) }
    var period by remember { mutableStateOf(Period.DAY) }
    val buckets = remember(metric, period, intervals, trips, fuels) {
        val all = when (metric) {
            ChartMetric.MILEAGE -> MileageCalc.mileageBuckets(intervals, period)
            ChartMetric.DISTANCE -> MileageCalc.distanceBuckets(trips, period)
            ChartMetric.SPEND -> MileageCalc.spendBuckets(fuels, period)
        }
        all.takeLast(when (period) { Period.DAY -> 10; Period.MONTH -> 12; Period.YEAR -> 6 })
    }
    val mileageByFuel = remember(intervals) { MileageCalc.mileageByFuelId(intervals) }
    val lastOdo = fuels.lastOrNull()?.odometerKm

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your ride, measured.", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        listOf(vehicle.name, vehicle.variant, vehicle.year).filter { it.isNotBlank() }.joinToString(" · "),
                        color = Muted, fontSize = 12.sp
                    )
                }
                when (live.state) {
                    TripState.RUNNING -> StatusChip("● TRACKING", Teal)
                    TripState.PAUSED -> StatusChip("❚❚ PAUSED", Orange)
                    TripState.IDLE -> StatusChip("● READY", Teal)
                }
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(24.dp), color = Panel,
                border = BorderStroke(1.dp, Color(0xFF254357))
            ) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF173B49), Panel))).padding(20.dp)) {
                    Text("OVERALL FUEL MILEAGE", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(if (overall > 0) fmt(overall) else "--", color = Teal, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold)
                        Text(" km/L", Modifier.padding(bottom = 9.dp), color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        if (overall > 0) "Calculated from full-tank entries" else "Add two full-tank entries to calculate mileage",
                        color = Muted, fontSize = 11.sp
                    )
                    Spacer(Modifier.height(15.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricPill("TRIP DISTANCE", fmt(totalKm) + " km", Modifier.weight(1f))
                        MetricPill("LAST ODOMETER", lastOdo?.let { fmt(it, 0) + " km" } ?: "Not set", Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("FUEL SPEND", "₹${spend.toInt()}", "All recorded fill-ups", Modifier.weight(1f), Orange)
                StatCard(
                    "LIVE TRIP", fmt(live.distanceKm, 2) + " km",
                    "${live.speedKmh.toInt()} km/h · ${formatDuration(ui.elapsedMs)}", Modifier.weight(1f), Teal
                )
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFF20384B))) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Trends", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("${metric.label} (${metric.unit}) by ${period.label.lowercase()}", color = Muted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.Timeline, tint = Teal, contentDescription = null)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChartMetric.values().forEach { m ->
                            FilterChip(
                                selected = metric == m, onClick = { metric = m },
                                label = { Text(m.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF1C4951), selectedLabelColor = Teal, labelColor = Muted
                                )
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Period.values().forEach { p ->
                            FilterChip(
                                selected = period == p, onClick = { period = p },
                                label = { Text(p.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF1C4951), selectedLabelColor = Teal, labelColor = Muted
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (buckets.isEmpty()) {
                        Text(
                            when (metric) {
                                ChartMetric.MILEAGE -> "Mileage appears after two full-tank fuel entries."
                                ChartMetric.DISTANCE -> "Distance appears after you save a trip."
                                ChartMetric.SPEND -> "Spend appears after you save a fuel entry."
                            },
                            color = Muted, fontSize = 11.sp
                        )
                    } else {
                        BarChart(buckets, if (metric == ChartMetric.MILEAGE) 1 else 0)
                        Text("${buckets.size} ${period.label.lowercase()}(s) shown · ${metric.unit}", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mileage per fuel top-up", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onOpenFuel) { Text("ALL", color = Teal, fontSize = 11.sp) }
                    }
                    if (fuels.isEmpty()) {
                        Text("No fuel entries yet.", color = Muted, fontSize = 11.sp)
                    }
                    fuels.asReversed().take(8).forEach { f ->
                        val m = mileageByFuel[f.id]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.date.take(10), color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    fmt(f.litres, 2) + " L · " + (if (f.fullTank) "full tank" else "partial fill") +
                                        " · " + fmt(f.odometerKm, 0) + " km",
                                    color = Muted, fontSize = 10.sp
                                )
                            }
                            Text(
                                if (m != null) fmt(m, 2) + " km/L" else if (f.fullTank) "baseline" else "—",
                                color = if (m != null) Green else Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mileage per trip", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onOpenTrips) { Text("ALL", color = Teal, fontSize = 11.sp) }
                    }
                    if (trips.isEmpty()) Text("No trips recorded yet.", color = Muted, fontSize = 11.sp)
                    trips.take(8).forEach { t ->
                        val m = tripMileage[t.id]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.date, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(fmt(t.distanceKm, 2) + " km · " + t.durationMinutes + " min", color = Muted, fontSize = 10.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (m != null) fmt(m.kmPerL, 1) + " km/L" else "—",
                                    color = if (m != null) Green else Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                )
                                if (m != null) Text(m.source, color = Muted, fontSize = 9.sp)
                            }
                        }
                    }
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
                        Text("GPS distance, route, speed and duration", color = Muted, fontSize = 11.sp)
                    }
                    Text("OPEN  →", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
