package com.sriram.bikemileagepro

import android.content.Context
import android.location.Geocoder
import java.util.Locale
import kotlin.math.roundToInt

/** Compact text encoding for a route: "lat,lon;lat,lon;..." */
object RouteCodec {
    fun encode(points: List<Pair<Double, Double>>, maxPoints: Int = 400): String {
        if (points.isEmpty()) return ""
        val n = minOf(points.size, maxPoints)
        val indices = if (n == 1) listOf(0) else (0 until n).map {
            (it * (points.size - 1).toDouble() / (n - 1)).roundToInt()
        }
        return indices.joinToString(";") {
            String.format(Locale.US, "%.5f,%.5f", points[it].first, points[it].second)
        }
    }

    fun decode(text: String): List<Pair<Double, Double>> {
        if (text.isBlank()) return emptyList()
        return text.split(';').mapNotNull { part ->
            val xy = part.split(',')
            val lat = xy.getOrNull(0)?.toDoubleOrNull()
            val lon = xy.getOrNull(1)?.toDoubleOrNull()
            if (lat != null && lon != null) lat to lon else null
        }
    }
}

object GeoNames {
    fun coords(lat: Double?, lon: Double?): String =
        if (lat == null || lon == null) "Unknown" else String.format(Locale.US, "%.4f, %.4f", lat, lon)

    /** Blocking; call from a background thread. Returns null when no geocoder/network/result. */
    @Suppress("DEPRECATION")
    fun reverse(context: Context, lat: Double, lon: Double): String? = try {
        if (!Geocoder.isPresent()) null else {
            val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            a?.let {
                listOfNotNull(it.subLocality ?: it.thoroughfare ?: it.featureName, it.locality)
                    .distinct().joinToString(", ")
            }?.takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        null
    }
}
