package com.sriram.bikemileagepro

import android.content.Context

enum class TripState { IDLE, RUNNING, PAUSED }

/** The single in-progress trip. Persisted so it survives process death and app restarts. */
data class ActiveTrip(
    val uid: String = "",
    val vehicleId: Long = -1L,
    val state: TripState = TripState.IDLE,
    val startedAt: Long = 0L,
    val activeMillis: Long = 0L,      // moving time accumulated from finished running segments
    val segmentStart: Long = 0L,      // wall-clock start of the current running segment (0 if paused)
    val lastFixAt: Long = 0L,         // wall-clock time of the last accepted GPS fix
    val distanceKm: Double = 0.0,
    val maxSpeed: Double = 0.0,
    val speedKmh: Double = 0.0,
    val startLat: Double? = null,
    val startLon: Double? = null,
    val lastLat: Double? = null,
    val lastLon: Double? = null
) {
    fun elapsedMillis(now: Long = System.currentTimeMillis()): Long =
        activeMillis + if (state == TripState.RUNNING && segmentStart > 0L) (now - segmentStart).coerceAtLeast(0L) else 0L
}

class ActiveTripStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("active_trip", Context.MODE_PRIVATE)

    private fun readDouble(key: String): Double =
        java.lang.Double.longBitsToDouble(prefs.getLong(key, java.lang.Double.doubleToRawLongBits(0.0)))

    private fun nullable(key: String): Double? =
        if (prefs.getBoolean("has_$key", false)) readDouble(key) else null

    fun load(): ActiveTrip {
        val state = try {
            TripState.valueOf(prefs.getString("state", TripState.IDLE.name) ?: TripState.IDLE.name)
        } catch (_: IllegalArgumentException) {
            TripState.IDLE
        }
        if (state == TripState.IDLE) return ActiveTrip()
        return ActiveTrip(
            uid = prefs.getString("uid", "") ?: "",
            vehicleId = prefs.getLong("vehicle_id", -1L),
            state = state,
            startedAt = prefs.getLong("started_at", 0L),
            activeMillis = prefs.getLong("active_millis", 0L),
            segmentStart = prefs.getLong("segment_start", 0L),
            lastFixAt = prefs.getLong("last_fix_at", 0L),
            distanceKm = readDouble("distance_km"),
            maxSpeed = readDouble("max_speed"),
            speedKmh = readDouble("speed_kmh"),
            startLat = nullable("start_lat"), startLon = nullable("start_lon"),
            lastLat = nullable("last_lat"), lastLon = nullable("last_lon")
        )
    }

    /** sync=true uses commit() for state transitions; false uses apply() for frequent GPS updates. */
    fun save(t: ActiveTrip, sync: Boolean = false) {
        val e = prefs.edit()
        e.putString("state", t.state.name)
        e.putString("uid", t.uid)
        e.putLong("vehicle_id", t.vehicleId)
        e.putLong("started_at", t.startedAt)
        e.putLong("active_millis", t.activeMillis)
        e.putLong("segment_start", t.segmentStart)
        e.putLong("last_fix_at", t.lastFixAt)
        putD(e, "distance_km", t.distanceKm)
        putD(e, "max_speed", t.maxSpeed)
        putD(e, "speed_kmh", t.speedKmh)
        putN(e, "start_lat", t.startLat); putN(e, "start_lon", t.startLon)
        putN(e, "last_lat", t.lastLat); putN(e, "last_lon", t.lastLon)
        if (sync) e.commit() else e.apply()
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    /**
     * The process died while a trip was RUNNING. Convert it to PAUSED, counting moving time only
     * up to the last GPS fix so the dead time is not added to the duration.
     */
    fun settleInterrupted(t: ActiveTrip): ActiveTrip {
        if (t.state != TripState.RUNNING) return t
        val counted = if (t.lastFixAt > t.segmentStart && t.segmentStart > 0L) t.lastFixAt - t.segmentStart else 0L
        return t.copy(state = TripState.PAUSED, activeMillis = t.activeMillis + counted, segmentStart = 0L, speedKmh = 0.0)
    }

    private fun putD(e: android.content.SharedPreferences.Editor, key: String, v: Double) {
        e.putLong(key, java.lang.Double.doubleToRawLongBits(v))
    }

    private fun putN(e: android.content.SharedPreferences.Editor, key: String, v: Double?) {
        e.putBoolean("has_$key", v != null)
        e.putLong(key, java.lang.Double.doubleToRawLongBits(v ?: 0.0))
    }
}
