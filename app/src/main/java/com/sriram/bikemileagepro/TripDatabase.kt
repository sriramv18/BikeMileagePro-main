package com.sriram.bikemileagepro

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class TripRecord(
    val id: Long, val date: String, val distanceKm: Double,
    val maxSpeed: Double, val durationMinutes: Int
)
data class FuelRecord(
    val id: Long, val date: String, val litres: Double,
    val amount: Double, val odometerKm: Double, val fullTank: Boolean
)
data class MileagePoint(val label: String, val value: Double)

class TripDatabase(context: Context) :
    SQLiteOpenHelper(context, "bike_mileage_pro.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE trips (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date TEXT NOT NULL,
            distance_km REAL NOT NULL,
            max_speed REAL NOT NULL,
            duration_minutes INTEGER NOT NULL DEFAULT 0
        )""")
        db.execSQL("""CREATE TABLE fuel (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date TEXT NOT NULL,
            litres REAL NOT NULL,
            amount REAL NOT NULL,
            odometer_km REAL NOT NULL,
            full_tank INTEGER NOT NULL DEFAULT 1
        )""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE trips ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE fuel ADD COLUMN full_tank INTEGER NOT NULL DEFAULT 1")
        }
    }

    fun saveTrip(date: String, km: Double, maxSpeed: Double, durationMinutes: Int = 0) {
        writableDatabase.insert("trips", null, ContentValues().apply {
            put("date", date); put("distance_km", km); put("max_speed", maxSpeed)
            put("duration_minutes", durationMinutes)
        })
    }

    fun saveFuel(date: String, litres: Double, amount: Double, odometer: Double, fullTank: Boolean) {
        writableDatabase.insert("fuel", null, ContentValues().apply {
            put("date", date); put("litres", litres); put("amount", amount)
            put("odometer_km", odometer); put("full_tank", if (fullTank) 1 else 0)
        })
    }

    fun trips(): List<TripRecord> {
        val out = mutableListOf<TripRecord>()
        readableDatabase.rawQuery(
            "SELECT id,date,distance_km,max_speed,duration_minutes FROM trips ORDER BY id DESC LIMIT 100", null
        ).use { c ->
            while (c.moveToNext()) out += TripRecord(
                c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getInt(4)
            )
        }
        return out
    }

    fun fuels(): List<FuelRecord> {
        val out = mutableListOf<FuelRecord>()
        readableDatabase.rawQuery(
            "SELECT id,date,litres,amount,odometer_km,full_tank FROM fuel ORDER BY odometer_km ASC,id ASC", null
        ).use { c ->
            while (c.moveToNext()) out += FuelRecord(
                c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3),
                c.getDouble(4), c.getInt(5) == 1
            )
        }
        return out
    }

    fun mileagePoints(): List<MileagePoint> {
        val entries = fuels()
        val fullIndices = entries.indices.filter { entries[it].fullTank }
        val points = mutableListOf<MileagePoint>()
        for (pair in 1 until fullIndices.size) {
            val previousIndex = fullIndices[pair - 1]
            val currentIndex = fullIndices[pair]
            val km = entries[currentIndex].odometerKm - entries[previousIndex].odometerKm
            val litres = entries.subList(previousIndex + 1, currentIndex + 1).sumOf { it.litres }
            if (km > 0.0 && litres > 0.0) points += MileagePoint(entries[currentIndex].date.take(10), km / litres)
        }
        return points
    }

    /** Actual interval mileage for a full-tank entry. Includes every partial fill
     * after the previous full tank and the current full-tank fill itself. */
    fun mileageForFuelEntry(fuelId: Long): Double? {
        val entries = fuels()
        val currentIndex = entries.indexOfFirst { it.id == fuelId }
        if (currentIndex < 0 || !entries[currentIndex].fullTank) return null
        val previousIndex = (currentIndex - 1 downTo 0).firstOrNull { entries[it].fullTank } ?: return null
        val km = entries[currentIndex].odometerKm - entries[previousIndex].odometerKm
        val litres = entries.subList(previousIndex + 1, currentIndex + 1).sumOf { it.litres }
        return if (km > 0.0 && litres > 0.0) km / litres else null
    }

    /** Fuel entries are ordered by odometer, and an interval is valid only
     * when the odometer increases and two consecutive full-tank records exist. */
    fun validateFuelEntry(odometerKm: Double, litres: Double, amount: Double): String? {
        if (!odometerKm.isFinite() || odometerKm < 0.0) return "Enter a valid odometer reading."
        if (!litres.isFinite() || litres <= 0.0) return "Fuel quantity must be greater than zero."
        if (!amount.isFinite() || amount < 0.0) return "Enter a valid fuel amount."
        val latest = fuels().lastOrNull()
        if (latest != null && odometerKm <= latest.odometerKm) {
            return "Odometer must be greater than the previous entry (${latest.odometerKm} km)."
        }
        return null
    }

    fun overallMileage(): Double {
        val entries = fuels()
        val fullIndices = entries.indices.filter { entries[it].fullTank }
        var kmTotal = 0.0
        var litresTotal = 0.0
        for (pair in 1 until fullIndices.size) {
            val previousIndex = fullIndices[pair - 1]
            val currentIndex = fullIndices[pair]
            val km = entries[currentIndex].odometerKm - entries[previousIndex].odometerKm
            val litres = entries.subList(previousIndex + 1, currentIndex + 1).sumOf { it.litres }
            if (km > 0.0 && litres > 0.0) {
                kmTotal += km
                litresTotal += litres
            }
        }
        return if (litresTotal > 0.0) kmTotal / litresTotal else 0.0
    }

    fun totalFuelSpend(): Double = fuels().sumOf { it.amount }
    fun totalTripDistance(): Double = trips().sumOf { it.distanceKm }
}
