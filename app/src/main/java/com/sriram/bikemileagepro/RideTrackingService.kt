package com.sriram.bikemileagepro

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
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

class RideTrackingService : Service(), LocationListener {
    companion object {
        const val ACTION_START = "com.sriram.bikemileagepro.START"
        const val ACTION_STOP = "com.sriram.bikemileagepro.STOP"
        const val ACTION_UPDATE = "com.sriram.bikemileagepro.UPDATE"
        private const val CHANNEL_ID = "bike_ride_tracking"
        private const val NOTIFICATION_ID = 41
    }

    private lateinit var locationManager: LocationManager
    private var previous: Location? = null
    private var distanceKm = 0.0
    private var maxSpeed = 0.0
    private var startedAt = 0L
    private var started = false

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Ride tracking", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin()
            ACTION_STOP -> finishRide()
        }
        return START_NOT_STICKY
    }

    private fun notification(message: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder.setContentTitle("Bike Mileage Pro")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(started).build()
    }

    private fun begin() {
        if (started) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return
        }
        distanceKm = 0.0
        maxSpeed = 0.0
        previous = null
        startedAt = System.currentTimeMillis()
        started = true
        startForeground(NOTIFICATION_ID, notification("Ride tracking is active"))
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, this, Looper.getMainLooper())
        } catch (_: SecurityException) {
            started = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!started) return
        // Reject poor GPS fixes before using them as the next distance anchor.
        // This prevents a low-quality point from creating a large jump on the next update.
        if (!location.hasAccuracy() || location.accuracy > 35f) return

        val old = previous
        if (old != null) {
            val elapsedSeconds = ((location.time - old.time).coerceAtLeast(1L)) / 1000.0
            val segment = old.distanceTo(location)
            val plausibleSpeed = segment / elapsedSeconds
            // Ignore GPS spikes: at most 250 m between 1-second-ish fixes and 70 m/s.
            if (segment in 2f..250f && plausibleSpeed <= 70.0) {
                distanceKm += segment / 1000.0
            }
        }
        previous = location
        val speed = if (location.hasSpeed()) location.speed * 3.6 else 0.0
        if (speed in 0.0..250.0 && speed > maxSpeed) maxSpeed = speed
        val elapsed = ((System.currentTimeMillis() - startedAt) / 60000L).toInt()
        sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).apply {
            putExtra("distance_km", distanceKm)
            putExtra("speed_kmh", speed)
            putExtra("max_speed", maxSpeed)
            putExtra("duration_minutes", elapsed)
        })
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification(String.format(Locale.US, "Trip %.2f km", distanceKm)))
    }

    private fun finishRide() {
        if (started) {
            locationManager.removeUpdates(this)
            started = false
            if (distanceKm > 0.02) {
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val minutes = ((System.currentTimeMillis() - startedAt) / 60000L).toInt()
                TripDatabase(this).use { it.saveTrip(date, distanceKm, maxSpeed, minutes) }
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
