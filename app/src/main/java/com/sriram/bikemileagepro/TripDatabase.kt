package com.sriram.bikemileagepro

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class TripRecord(val id: Long, val date: String, val distanceKm: Double, val maxSpeed: Double)
data class FuelRecord(val id: Long, val date: String, val litres: Double, val amount: Double, val odometerKm: Double)

class TripDatabase(context: Context) : SQLiteOpenHelper(context, "bike_mileage_pro.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE trips (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, distance_km REAL NOT NULL, max_speed REAL NOT NULL)")
        db.execSQL("CREATE TABLE fuel (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, litres REAL NOT NULL, amount REAL NOT NULL, odometer_km REAL NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun saveTrip(date: String, km: Double, maxSpeed: Double) {
        writableDatabase.insert("trips", null, ContentValues().apply {
            put("date", date); put("distance_km", km); put("max_speed", maxSpeed)
        })
    }
    fun saveFuel(date: String, litres: Double, amount: Double, odometer: Double) {
        writableDatabase.insert("fuel", null, ContentValues().apply {
            put("date", date); put("litres", litres); put("amount", amount); put("odometer_km", odometer)
        })
    }
    fun trips(): List<TripRecord> {
        val out = mutableListOf<TripRecord>()
        readableDatabase.rawQuery("SELECT id,date,distance_km,max_speed FROM trips ORDER BY id DESC LIMIT 20", null).use { c ->
            while (c.moveToNext()) out += TripRecord(c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3))
        }
        return out
    }
    fun fuels(): List<FuelRecord> {
        val out = mutableListOf<FuelRecord>()
        readableDatabase.rawQuery("SELECT id,date,litres,amount,odometer_km FROM fuel ORDER BY id DESC LIMIT 20", null).use { c ->
            while (c.moveToNext()) out += FuelRecord(c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getDouble(4))
        }
        return out
    }
}
