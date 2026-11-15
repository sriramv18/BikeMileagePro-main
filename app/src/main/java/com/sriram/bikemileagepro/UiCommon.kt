package com.sriram.bikemileagepro

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

internal val Ink = Color(0xFF08131F)
internal val Panel = Color(0xFF122334)
internal val Panel2 = Color(0xFF182D40)
internal val Teal = Color(0xFF36D6C3)
internal val Muted = Color(0xFF91A5B8)
internal val White = Color(0xFFF4F8FC)
internal val Orange = Color(0xFFFFB86B)
internal val Green = Color(0xFF64D6A2)
internal val Red = Color(0xFFFF8E8E)

internal data class ScannedDevice(
    val name: String, val address: String, val rssi: Int,
    val likelyObd: Boolean, val device: BluetoothDevice
)

/** Everything the UI reads from the Activity. All fields are Compose state. */
internal class UiState {
    var live by mutableStateOf(ActiveTrip())
    var elapsedMs by mutableLongStateOf(0L)
    var needsRecovery by mutableStateOf(false)
    var dataVersion by mutableIntStateOf(0)
    var hasLocationPermission by mutableStateOf(false)
    var locationEnabled by mutableStateOf(true)
    var showLocationDialog by mutableStateOf(false)
    var obdState by mutableStateOf(ObdState.DISCONNECTED)
    var obdMessage by mutableStateOf("Not connected")
    var connectedName by mutableStateOf("")
    var scanning by mutableStateOf(false)
    var scanMessage by mutableStateOf("Not scanned")
    var scanResults by mutableStateOf<List<ScannedDevice>>(emptyList())
}

internal interface AppActions {
    fun savedVehicleId(): Long
    fun saveSelectedVehicle(id: Long)
    fun startTrip(vehicleId: Long)
    fun pauseTrip()
    fun resumeTrip()
    fun stopTrip()
    fun resumeRecovered()
    fun saveRecovered()
    fun discardRecovered()
    fun scanBle()
    fun connectBle(device: ScannedDevice)
    fun disconnectBle()
    fun requestPermissions()
    fun openLocationSettings()
    fun openAppSettings()
    fun openMap(lat: Double, lon: Double, label: String)
}

internal class Env(
    val ui: UiState,
    val actions: AppActions,
    val db: TripDatabase,
    val context: Context,
    val bump: () -> Unit
)

internal fun fmt(value: Double, decimals: Int = 1): String =
    String.format(Locale.US, "%.${decimals}f", value)

internal fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

@Composable
internal fun MetricPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF102838)).padding(12.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun StatCard(title: String, value: String, detail: String, modifier: Modifier, accent: Color) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF20384B))) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            Text(value, color = accent, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
internal fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
internal fun EmptyState(title: String, subtitle: String) {
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
internal fun StatusChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun DarkField(label: String, value: String, onValue: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text) {
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

/** Simple bar chart built from layout primitives (no custom text drawing). */
@Composable
internal fun BarChart(buckets: List<ChartBucket>, decimals: Int) {
    if (buckets.isEmpty()) return
    val maxValue = max(buckets.maxOf { it.value }, 1e-9)
    Row(
        Modifier.fillMaxWidth().height(170.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        buckets.forEach { b ->
            val frac = (b.value / maxValue).toFloat().coerceIn(0f, 1f)
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(fmt(b.value, decimals), color = Muted, fontSize = 8.sp, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier.fillMaxWidth().height(max(3f, 110f * frac).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Teal)
                )
                Spacer(Modifier.height(3.dp))
                Text(b.label, color = Muted, fontSize = 8.sp, maxLines = 1)
            }
        }
    }
}

/** Offline route preview drawn on a Canvas. No map key or network needed. */
@Composable
internal fun RouteMap(points: List<Pair<Double, Double>>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0D1C2A))) {
        if (points.isEmpty()) return@Canvas
        val minLat = points.minOf { it.first }; val maxLat = points.maxOf { it.first }
        val minLon = points.minOf { it.second }; val maxLon = points.maxOf { it.second }
        val lonScale = cos(Math.toRadians((minLat + maxLat) / 2.0))
        val w = max((maxLon - minLon) * lonScale, 1e-6)
        val h = max(maxLat - minLat, 1e-6)
        val pad = 18.dp.toPx()
        val scale = min((size.width - 2 * pad) / w, (size.height - 2 * pad) / h)
        val ox = (size.width - w * scale) / 2.0
        val oy = (size.height - h * scale) / 2.0
        fun pt(p: Pair<Double, Double>) = Offset(
            (ox + (p.second - minLon) * lonScale * scale).toFloat(),
            (oy + (maxLat - p.first) * scale).toFloat()
        )
        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val o = pt(p)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, Teal, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawCircle(Green, radius = 7.dp.toPx(), center = pt(points.first()))
        drawCircle(Orange, radius = 7.dp.toPx(), center = pt(points.last()))
    }
}
