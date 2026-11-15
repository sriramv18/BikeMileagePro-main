package com.sriram.bikemileagepro

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Vehicle(
    val id: Long, val name: String, val year: String, val reg: String,
    val variant: String, val odometer: String
)

data class TripRecord(
    val id: Long, val uid: String, val vehicleId: Long, val date: String,
    val distanceKm: Double, val maxSpeed: Double, val durationMinutes: Int,
    val startLat: Double?, val startLon: Double?, val endLat: Double?, val endLon: Double?,
    val startPlace: String, val endPlace: String, val route: String, val fuelUsedLitres: Double?
)

data class NewTrip(
    val uid: String, val vehicleId: Long, val date: String,
    val distanceKm: Double, val maxSpeed: Double, val durationMinutes: Int,
    val startLat: Double?, val startLon: Double?, val endLat: Double?, val endLon: Double?,
    val route: String
)

data class FuelRecord(
    val id: Long, val date: String, val litres: Double,
    val amount: Double, val odometerKm: Double, val fullTank: Boolean
)

/**
 * Schema v3 adds: vehicles table, vehicle_id on trips and fuel, trip uid (unique, used for
 * duplicate-save protection), start/end coordinates, place names, route, actual fuel used,
 * and the active_points table that backs crash-safe route recording.
 */
class TripDatabase(private val appContext: Context) :
    SQLiteOpenHelper(appContext, "bike_mileage_pro.db", null, 3) {

    private companion object {
        const val VEHICLES_SQL = """CREATE TABLE IF NOT EXISTS vehicles (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            year TEXT NOT NULL DEFAULT '',
            reg TEXT NOT NULL DEFAULT '',
            variant TEXT NOT NULL DEFAULT '',
            odo TEXT NOT NULL DEFAULT ''
        )"""
        const val POINTS_SQL = """CREATE TABLE IF NOT EXISTS active_points (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            uid TEXT NOT NULL,
            lat REAL NOT NULL,
            lon REAL NOT NULL
        )"""
        const val TRIP_COLS =
            "id,COALESCE(uid,''),vehicle_id,date,distance_km,max_speed,duration_minutes," +
            "start_lat,start_lon,end_lat,end_lon,COALESCE(start_place,''),COALESCE(end_place,'')," +
            "COALESCE(route,''),fuel_used"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(VEHICLES_SQL)
        db.execSQL("""CREATE TABLE trips (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date TEXT NOT NULL,
            distance_km REAL NOT NULL,
            max_speed REAL NOT NULL,
            duration_minutes INTEGER NOT NULL DEFAULT 0,
            uid TEXT,
            vehicle_id INTEGER NOT NULL DEFAULT 1,
            start_lat REAL, start_lon REAL, end_lat REAL, end_lon REAL,
            start_place TEXT NOT NULL DEFAULT '',
            end_place TEXT NOT NULL DEFAULT '',
            route TEXT NOT NULL DEFAULT '',
            fuel_used REAL
        )""")
        db.execSQL("""CREATE TABLE fuel (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date TEXT NOT NULL,
            litres REAL NOT NULL,
            amount REAL NOT NULL,
            odometer_km REAL NOT NULL,
            full_tank INTEGER NOT NULL DEFAULT 1,
            vehicle_id INTEGER NOT NULL DEFAULT 1
        )""")
        db.execSQL(POINTS_SQL)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_trips_uid ON trips(uid)")
        insertVehicle(db, "My motorcycle", "", "", "", "")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE trips ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE fuel ADD COLUMN full_tank INTEGER NOT NULL DEFAULT 1")
        }
        if (oldVersion < 3) {
            db.execSQL(VEHICLES_SQL)
            db.execSQL(POINTS_SQL)
            // The old single profile lived in SharedPreferences; it becomes vehicle #1.
            val p = appContext.getSharedPreferences("vehicle_profile", Context.MODE_PRIVATE)
            val firstId = insertVehicle(
                db,
                p.getString("name", null)?.takeIf { it.isNotBlank() } ?: "My motorcycle",
                p.getString("year", "") ?: "", p.getString("reg", "") ?: "",
                p.getString("variant", "") ?: "", p.getString("odo", "") ?: ""
            )
            db.execSQL("ALTER TABLE trips ADD COLUMN uid TEXT")
            db.execSQL("ALTER TABLE trips ADD COLUMN vehicle_id INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE trips ADD COLUMN start_lat REAL")
            db.execSQL("ALTER TABLE trips ADD COLUMN start_lon REAL")
            db.execSQL("ALTER TABLE trips ADD COLUMN end_lat REAL")
            db.execSQL("ALTER TABLE trips ADD COLUMN end_lon REAL")
            db.execSQL("ALTER TABLE trips ADD COLUMN start_place TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE trips ADD COLUMN end_place TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE trips ADD COLUMN route TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE trips ADD COLUMN fuel_used REAL")
            db.execSQL("ALTER TABLE fuel ADD COLUMN vehicle_id INTEGER NOT NULL DEFAULT 1")
            db.execSQL("UPDATE trips SET vehicle_id = $firstId")
            db.execSQL("UPDATE fuel SET vehicle_id = $firstId")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_trips_uid ON trips(uid)")
        }
    }

    private fun insertVehicle(
        db: SQLiteDatabase, name: String, year: String, reg: String, variant: String, odo: String
    ): Long = db.insert("vehicles", null, ContentValues().apply {
        put("name", name); put("year", year); put("reg", reg); put("variant", variant); put("odo", odo)
    })

    // ---------------------------------------------------------------- vehicles

    fun vehicles(): List<Vehicle> {
        val out = mutableListOf<Vehicle>()
        readableDatabase.rawQuery("SELECT id,name,year,reg,variant,odo FROM vehicles ORDER BY id ASC", null).use { c ->
            while (c.moveToNext()) out += Vehicle(
                c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5)
            )
        }
        if (out.isEmpty()) {
            val id = insertVehicle(writableDatabase, "My motorcycle", "", "", "", "")
            out += Vehicle(id, "My motorcycle", "", "", "", "")
        }
        return out
    }

    fun addVehicle(name: String, year: String, reg: String, variant: String, odo: String): Long =
        insertVehicle(writableDatabase, name.trim().ifBlank { "My motorcycle" }, year.trim(), reg.trim(), variant.trim(), odo.trim())

    fun updateVehicle(v: Vehicle) {
        writableDatabase.update("vehicles", ContentValues().apply {
            put("name", v.name.trim().ifBlank { "My motorcycle" }); put("year", v.year.trim())
            put("reg", v.reg.trim()); put("variant", v.variant.trim()); put("odo", v.odometer.trim())
        }, "id = ?", arrayOf(v.id.toString()))
    }

    /** Deletes the vehicle together with its trips and fuel records. Refuses to delete the last vehicle. */
    fun deleteVehicle(id: Long): Boolean {
        val db = writableDatabase
        val count = db.rawQuery("SELECT COUNT(*) FROM vehicles", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        if (count <= 1) return false
        db.beginTransaction()
        try {
            db.delete("trips", "vehicle_id = ?", arrayOf(id.toString()))
            db.delete("fuel", "vehicle_id = ?", arrayOf(id.toString()))
            db.delete("vehicles", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    // ------------------------------------------------------------------- trips

    /** Returns false when a trip with the same uid already exists (duplicate save ignored). */
    fun saveTrip(t: NewTrip): Boolean {
        val id = writableDatabase.insertWithOnConflict("trips", null, ContentValues().apply {
            put("uid", t.uid); put("vehicle_id", t.vehicleId); put("date", t.date)
            put("distance_km", t.distanceKm); put("max_speed", t.maxSpeed)
            put("duration_minutes", t.durationMinutes)
            t.startLat?.let { put("start_lat", it) }; t.startLon?.let { put("start_lon", it) }
            t.endLat?.let { put("end_lat", it) }; t.endLon?.let { put("end_lon", it) }
            put("route", t.route)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        return id != -1L
    }

    fun trips(vehicleId: Long, limit: Int = 300): List<TripRecord> {
        val out = mutableListOf<TripRecord>()
        readableDatabase.rawQuery(
            "SELECT $TRIP_COLS FROM trips WHERE vehicle_id = ? ORDER BY id DESC LIMIT $limit",
            arrayOf(vehicleId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                fun d(i: Int): Double? = if (c.isNull(i)) null else c.getDouble(i)
                out += TripRecord(
                    c.getLong(0), c.getString(1), c.getLong(2), c.getString(3),
                    c.getDouble(4), c.getDouble(5), c.getInt(6),
                    d(7), d(8), d(9), d(10), c.getString(11), c.getString(12), c.getString(13), d(14)
                )
            }
        }
        return out
    }

    fun deleteTrip(id: Long) {
        writableDatabase.delete("trips", "id = ?", arrayOf(id.toString()))
    }

    fun updateTripPlaces(id: Long, startPlace: String, endPlace: String) {
        writableDatabase.update("trips", ContentValues().apply {
            put("start_place", startPlace); put("end_place", endPlace)
        }, "id = ?", arrayOf(id.toString()))
    }

    fun updateTripFuelUsed(id: Long, litres: Double?) {
        writableDatabase.update("trips", ContentValues().apply {
            if (litres == null) putNull("fuel_used") else put("fuel_used", litres)
        }, "id = ?", arrayOf(id.toString()))
    }

    fun totalTripDistance(vehicleId: Long): Double =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(distance_km), 0) FROM trips WHERE vehicle_id = ?", arrayOf(vehicleId.toString())
        ).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }

    // ---------------------------------------------------- active route recording

    fun addActivePoint(uid: String, lat: Double, lon: Double) {
        writableDatabase.insert("active_points", null, ContentValues().apply {
            put("uid", uid); put("lat", lat); put("lon", lon)
        })
    }

    fun activePoints(uid: String): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        readableDatabase.rawQuery(
            "SELECT lat,lon FROM active_points WHERE uid = ? ORDER BY id ASC", arrayOf(uid)
        ).use { c -> while (c.moveToNext()) out += c.getDouble(0) to c.getDouble(1) }
        return out
    }

    fun clearActivePoints() {
        writableDatabase.delete("active_points", null, null)
    }

    // -------------------------------------------------------------------- fuel

    fun fuels(vehicleId: Long): List<FuelRecord> {
        val out = mutableListOf<FuelRecord>()
        readableDatabase.rawQuery(
            "SELECT id,date,litres,amount,odometer_km,full_tank FROM fuel WHERE vehicle_id = ? " +
                "ORDER BY odometer_km ASC,id ASC", arrayOf(vehicleId.toString())
        ).use { c ->
            while (c.moveToNext()) out += FuelRecord(
                c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3),
                c.getDouble(4), c.getInt(5) == 1
            )
        }
        return out
    }

    fun validateFuelEntry(vehicleId: Long, litres: Double, amount: Double, odometer: Double): String? {
        if (!litres.isFinite() || litres <= 0.0) return "Fuel quantity must be greater than zero."
        if (!amount.isFinite() || amount < 0.0) return "Enter a valid fuel amount."
        if (!odometer.isFinite() || odometer < 0.0) return "Enter a valid odometer reading."
        val latest = fuels(vehicleId).lastOrNull()
        if (latest != null && odometer <= latest.odometerKm) {
            return "Odometer must be greater than the previous entry (${latest.odometerKm} km)."
        }
        return null
    }

    fun saveFuel(vehicleId: Long, date: String, litres: Double, amount: Double, odometer: Double, fullTank: Boolean) {
        writableDatabase.insert("fuel", null, ContentValues().apply {
            put("vehicle_id", vehicleId); put("date", date); put("litres", litres)
            put("amount", amount); put("odometer_km", odometer); put("full_tank", if (fullTank) 1 else 0)
        })
    }

    fun deleteFuel(id: Long) {
        writableDatabase.delete("fuel", "id = ?", arrayOf(id.toString()))
    }

    fun totalFuelSpend(vehicleId: Long): Double =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(amount), 0) FROM fuel WHERE vehicle_id = ?", arrayOf(vehicleId.toString())
        ).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
}
