package com.sriram.bikemileagepro

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Foreground GPS trip service: start / pause / resume / stop.
 *
 * All trip state lives in [ActiveTripStore] (and route points in SQLite), never only in memory, so:
 *  - a repeated START or STOP cannot create a second trip or a second saved row,
 *  - if Android kills the process the trip can be resumed or saved when the app is reopened,
 *  - the trip uid is UNIQUE in the database, so even a replayed save cannot insert twice.
 */
class RideTrackingService : Service(), LocationListener {
    companion object {
        const val ACTION_START = "com.sriram.bikemileagepro.START"
        const val ACTION_PAUSE = "com.sriram.bikemileagepro.PAUSE"
        const val ACTION_RESUME = "com.sriram.bikemileagepro.RESUME"
        const val ACTION_STOP = "com.sriram.bikemileagepro.STOP"
        const val ACTION_UPDATE = "com.sriram.bikemileagepro.UPDATE"
        const val ACTION_FINISHED = "com.sriram.bikemileagepro.FINISHED"
        const val EXTRA_VEHICLE_ID = "vehicle_id"
        const val EXTRA_SAVED = "saved"
        const val EXTRA_DISTANCE = "distance_km"
        private const val CHANNEL_ID = "bike_ride_tracking"
        private const val NOTIFICATION_ID = 41

        /** True while a service instance exists in this process. */
        @Volatile var isAlive = false
    }

    private lateinit var locationManager: LocationManager
    private lateinit var store: ActiveTripStore
    private val db by lazy { TripDatabase(applicationContext) }
    private var previous: Location? = null
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        isAlive = true
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        store = ActiveTripStore(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Ride tracking", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopListening()
        isAlive = false
        try { db.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(intent?.getLongExtra(EXTRA_VEHICLE_ID, -1L) ?: -1L)
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> finishRide()
            null -> restoreAfterRestart()   // START_STICKY redelivery after the process was killed
        }
        return START_STICKY
    }

    // ------------------------------------------------------------ notification

    private fun servicePi(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, RideTrackingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun notification(trip: ActiveTrip): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        val paused = trip.state == TripState.PAUSED
        val text = String.format(Locale.US, "%s · %.2f km", if (paused) "Paused" else "Tracking", trip.distanceKm)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggle = if (paused)
            Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_play), "Resume", servicePi(ACTION_RESUME, 2)).build()
        else
            Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Pause", servicePi(ACTION_PAUSE, 1)).build()
        val stop = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Stop & save", servicePi(ACTION_STOP, 3)
        ).build()
        return builder.setContentTitle("Bike Mileage Pro")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(open)
            .addAction(toggle)
            .addAction(stop)
            .setOngoing(true)
            .build()
    }

    private fun enterForeground(trip: ActiveTrip) {
        val n = notification(trip)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun refreshNotification(trip: ActiveTrip) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(trip))
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Called when a foreground start was requested but there is nothing valid to track. */
    private fun abortStart() {
        try {
            enterForeground(ActiveTrip(state = TripState.PAUSED))
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    private fun ping() {
        sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName))
    }

    // ---------------------------------------------------------------- lifecycle

    private fun begin(vehicleId: Long) {
        val existing = store.load()
        if (existing.state != TripState.IDLE) {
            // Duplicate START (double tap, notification replay): keep the existing trip.
            try {
                enterForeground(existing)
                if (existing.state == TripState.RUNNING) startListening()
            } catch (_: Exception) { stopSelf() }
            ping()
            return
        }
        if (!hasLocationPermission() || vehicleId < 0L) { abortStart(); return }
        val now = System.currentTimeMillis()
        val trip = ActiveTrip(
            uid = UUID.randomUUID().toString(), vehicleId = vehicleId, state = TripState.RUNNING,
            startedAt = now, segmentStart = now, lastFixAt = now
        )
        db.clearActivePoints()
        store.save(trip, sync = true)
        try {
            enterForeground(trip)
            if (!startListening()) { pauseInternal(trip) }
        } catch (_: Exception) {
            store.clear(); db.clearActivePoints(); stopSelf(); return
        }
        ping()
    }

    private fun pause() {
        val trip = store.load()
        if (trip.state != TripState.RUNNING) return
        pauseInternal(trip)
        ping()
    }

    private fun pauseInternal(trip: ActiveTrip) {
        stopListening()
        val now = System.currentTimeMillis()
        val paused = trip.copy(
            state = TripState.PAUSED, activeMillis = trip.elapsedMillis(now), segmentStart = 0L, speedKmh = 0.0
        )
        store.save(paused, sync = true)
        refreshNotification(paused)
    }

    private fun resume() {
        val trip = store.load()
        if (trip.state == TripState.IDLE) { abortStart(); return }
        val now = System.currentTimeMillis()
        val running = if (trip.state == TripState.PAUSED)
            trip.copy(state = TripState.RUNNING, segmentStart = now, lastFixAt = now) else trip
        store.save(running, sync = true)
        try {
            enterForeground(running)
            if (!startListening()) { pauseInternal(running) }
        } catch (_: Exception) { stopSelf(); return }
        ping()
    }

    private fun restoreAfterRestart() {
        val trip = store.load()
        when (trip.state) {
            TripState.IDLE -> stopSelf()
            TripState.PAUSED -> try { enterForeground(trip) } catch (_: Exception) { stopSelf() }
            TripState.RUNNING -> {
                // Count moving time only up to the last GPS fix, then continue as a new segment.
                val settled = store.settleInterrupted(trip)
                val now = System.currentTimeMillis()
                val running = settled.copy(state = TripState.RUNNING, segmentStart = now, lastFixAt = now)
                store.save(running, sync = true)
                try {
                    enterForeground(running)
                    if (!startListening()) pauseInternal(running)
                } catch (_: Exception) { stopSelf() }
            }
        }
        ping()
    }

    private fun startListening(): Boolean {
        if (listening) return true
        if (!hasLocationPermission()) return false
        previous = null   // never bridge a gap (pause / restart) with a straight line
        return try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this, Looper.getMainLooper())
            listening = true
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false   // no GPS provider on this device
        }
    }

    private fun stopListening() {
        if (!listening) return
        try { locationManager.removeUpdates(this) } catch (_: SecurityException) {}
        listening = false
    }

    // ----------------------------------------------------------------- locations

    override fun onLocationChanged(location: Location) {
        val trip = store.load()
        if (trip.state != TripState.RUNNING) return
        // Reject poor GPS fixes before using them as the next distance anchor.
        if (!location.hasAccuracy() || location.accuracy > 35f) return

        var distance = trip.distanceKm
        var added = previous == null
        val old = previous
        if (old != null) {
            val elapsedSeconds = ((location.elapsedRealtimeNanos - old.elapsedRealtimeNanos) / 1e9).coerceAtLeast(0.001)
            val segment = old.distanceTo(location)
            val plausibleSpeed = segment / elapsedSeconds
            // Ignore GPS spikes: at most 250 m between fixes and 70 m/s.
            if (segment in 2f..250f && plausibleSpeed <= 70.0) {
                distance += segment / 1000.0
                added = true
            }
        }
        previous = location
        val speed = if (location.hasSpeed()) location.speed * 3.6 else 0.0
        val max = if (speed in 0.0..250.0 && speed > trip.maxSpeed) speed else trip.maxSpeed
        val updated = trip.copy(
            distanceKm = distance, maxSpeed = max, speedKmh = speed,
            lastFixAt = System.currentTimeMillis(),
            startLat = trip.startLat ?: location.latitude, startLon = trip.startLon ?: location.longitude,
            lastLat = location.latitude, lastLon = location.longitude
        )
        store.save(updated)
        if (added) db.addActivePoint(trip.uid, location.latitude, location.longitude)
        refreshNotification(updated)
        ping()
    }

    // ------------------------------------------------------------------- finish

    private fun finishRide() {
        val trip = store.load()
        if (trip.state == TripState.IDLE) {
            // Duplicate STOP: nothing to save, never create a second row.
            sendBroadcast(Intent(ACTION_FINISHED).setPackage(packageName).putExtra(EXTRA_SAVED, false))
            stopForegroundAndSelf()
            return
        }
        stopListening()
        val now = System.currentTimeMillis()
        val minutes = Math.round(trip.elapsedMillis(now) / 60000.0).toInt()
        var saved = false
        if (trip.distanceKm > 0.02) {
            val points = db.activePoints(trip.uid)
            val startLat = trip.startLat ?: points.firstOrNull()?.first
            val startLon = trip.startLon ?: points.firstOrNull()?.second
            val endLat = trip.lastLat ?: points.lastOrNull()?.first
            val endLon = trip.lastLon ?: points.lastOrNull()?.second
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(trip.startedAt))
            saved = db.saveTrip(
                NewTrip(
                    trip.uid, trip.vehicleId, date, trip.distanceKm, trip.maxSpeed, minutes,
                    startLat, startLon, endLat, endLon, RouteCodec.encode(points)
                )
            )
        }
        // Only clear the active trip after the row is written (the unique uid makes a replay harmless).
        store.clear()
        db.clearActivePoints()
        sendBroadcast(
            Intent(ACTION_FINISHED).setPackage(packageName)
                .putExtra(EXTRA_SAVED, saved).putExtra(EXTRA_DISTANCE, trip.distanceKm)
        )
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            // GPS is a required trip prerequisite. Stop & save rather than silently tracking without it.
            finishRide()
        }
    }
    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
