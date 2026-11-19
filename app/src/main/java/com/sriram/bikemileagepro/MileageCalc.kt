package com.sriram.bikemileagepro

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One full-tank to full-tank interval used for a mileage (km/L) calculation. */
data class FuelInterval(
    val fuelId: Long,
    val startDate: String,
    val endDate: String,
    val km: Double,
    val litres: Double
) {
    val kmPerL: Double get() = km / litres
}

data class ChartBucket(val label: String, val value: Double)

enum class Period(val label: String) { DAY("Day"), MONTH("Month"), YEAR("Year") }

enum class ChartDisplay(val label: String) { BAR("Bar"), LINE("Line") }

enum class ChartMetric(val label: String, val unit: String) {
    MILEAGE("Mileage", "km/L"), DISTANCE("Distance", "km"), SPEND("Fuel spend", "₹"), TRIPS("Trips", "rides")
}

/** source is "Actual", "Interval est." or "Average est." */
data class TripMileage(val kmPerL: Double, val source: String)

data class DailyActivitySummary(
    val date: String,
    val distanceKm: Double,
    val mileageKmPerL: Double?,
    val fuelSpend: Double,
    val tripsStarted: Int,
    val tripsEnded: Int
)

/** Pure calculation helpers (no Android dependencies). */
object MileageCalc {

    /**
     * Full-tank method: mileage = distance between two consecutive FULL fills divided by the
     * litres filled after the first one up to and including the second one (partial fills in
     * between are added to the litres).
     */
    fun intervals(fuels: List<FuelRecord>): List<FuelInterval> {
        // Sort by odometer, not entry date: riders may enter an old receipt later.
        // Ignore corrupt rows rather than allowing NaN/negative values into dashboard totals.
        val sorted = fuels.filter {
            it.odometerKm.isFinite() && it.odometerKm >= 0.0 &&
                it.litres.isFinite() && it.litres > 0.0
        }.sortedWith(compareBy<FuelRecord>({ it.odometerKm }, { it.id }))
        val out = mutableListOf<FuelInterval>()
        var prevFull = -1
        for (i in sorted.indices) {
            if (!sorted[i].fullTank) continue
            if (prevFull >= 0) {
                val km = sorted[i].odometerKm - sorted[prevFull].odometerKm
                val litres = (prevFull + 1..i).sumOf { sorted[it].litres }
                if (km > 0.0 && litres > 0.0 && litres.isFinite()) {
                    out += FuelInterval(sorted[i].id, sorted[prevFull].date, sorted[i].date, km, litres)
                }
            }
            prevFull = i
        }
        return out
    }

    fun overall(intervals: List<FuelInterval>): Double {
        val km = intervals.sumOf { it.km }
        val litres = intervals.sumOf { it.litres }
        return if (litres > 0.0) km / litres else 0.0
    }

    fun mileageByFuelId(intervals: List<FuelInterval>): Map<Long, Double> =
        intervals.associate { it.fuelId to it.kmPerL }

    /**
     * Mileage for one trip. Uses the litres the rider entered for the trip when present,
     * otherwise the mileage of the full-tank interval the trip happened in, otherwise the
     * overall average. Only the first case is a real measurement for that trip.
     */
    fun tripMileage(trip: TripRecord, intervals: List<FuelInterval>, overall: Double): TripMileage? {
        val used = trip.fuelUsedLitres
        if (used != null && used > 0.0 && trip.distanceKm > 0.0) {
            return TripMileage(trip.distanceKm / used, "Actual")
        }
        val interval = intervals.lastOrNull { it.startDate <= trip.date && trip.date <= it.endDate }
        if (interval != null) return TripMileage(interval.kmPerL, "Interval est.")
        if (overall > 0.0) return TripMileage(overall, "Average est.")
        return null
    }

    /** Live preview of what saving a new fuel entry would calculate (null if not calculable). */
    fun previewNewEntry(
        existing: List<FuelRecord>, odometer: Double, litres: Double, full: Boolean
    ): FuelInterval? {
        if (!full || litres <= 0.0 || odometer <= 0.0) return null
        val probe = FuelRecord(-1L, "~", litres, 0.0, odometer, true)
        return intervals(existing + probe).firstOrNull { it.fuelId == -1L }
    }

    private fun key(date: String, period: Period): String = when (period) {
        Period.DAY -> date.take(10)
        Period.MONTH -> date.take(7)
        Period.YEAR -> date.take(4)
    }

    private fun label(key: String, period: Period): String = when (period) {
        Period.DAY -> if (key.length >= 10) key.substring(5) else key
        else -> key
    }

    fun mileageBuckets(intervals: List<FuelInterval>, period: Period): List<ChartBucket> =
        intervals.groupBy { key(it.endDate, period) }.toSortedMap().map { (k, v) ->
            ChartBucket(label(k, period), v.sumOf { it.km } / v.sumOf { it.litres })
        }

    fun distanceBuckets(trips: List<TripRecord>, period: Period): List<ChartBucket> =
        trips.groupBy { key(it.date, period) }.toSortedMap().map { (k, v) ->
            ChartBucket(label(k, period), v.sumOf { it.distanceKm })
        }

    fun spendBuckets(fuels: List<FuelRecord>, period: Period): List<ChartBucket> =
        fuels.groupBy { key(it.date, period) }.toSortedMap().map { (k, v) ->
            ChartBucket(label(k, period), v.sumOf { it.amount })
        }

    fun tripBuckets(trips: List<TripRecord>, period: Period): List<ChartBucket> =
        trips.filter { it.distanceKm.isFinite() && it.distanceKm >= 0.0 }
            .groupBy { key(it.date, period) }.toSortedMap().map { (k, v) ->
                ChartBucket(label(k, period), v.size.toDouble())
            }

    /** Daily figures use trip start date for distance and calculate completion date from duration. */
    fun dailySummary(
        date: String,
        trips: List<TripRecord>,
        fuels: List<FuelRecord>,
        intervals: List<FuelInterval>
    ): DailyActivitySummary {
        val dayTrips = trips.filter { it.date.take(10) == date }
        val ended = trips.count { trip ->
            val start = runCatching {
                LocalDateTime.parse(trip.date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            }.getOrNull()
            val endDay = start?.plusMinutes(trip.durationMinutes.toLong())?.toLocalDate()?.toString()
            endDay == date
        }
        val dayIntervals = intervals.filter { it.endDate.take(10) == date }
        val km = dayIntervals.sumOf { it.km }
        val litres = dayIntervals.sumOf { it.litres }
        return DailyActivitySummary(
            date = date,
            distanceKm = dayTrips.sumOf { it.distanceKm },
            mileageKmPerL = if (litres > 0.0) km / litres else null,
            fuelSpend = fuels.filter { it.date.take(10) == date }.sumOf { it.amount },
            tripsStarted = dayTrips.size,
            tripsEnded = ended
        )
    }

    fun lastDays(referenceDate: String, count: Int): List<String> {
        val base = runCatching { LocalDate.parse(referenceDate) }.getOrElse { LocalDate.now() }
        return (count - 1 downTo 0).map { base.minusDays(it.toLong()).toString() }
    }
}
