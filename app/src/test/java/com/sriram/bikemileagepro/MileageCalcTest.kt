package com.sriram.bikemileagepro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MileageCalcTest {
    private fun fuel(id: Long, date: String, litres: Double, odo: Double, full: Boolean) =
        FuelRecord(id, date, litres, litres * 100.0, odo, full)

    private fun trip(date: String, km: Double, fuelUsed: Double? = null) = TripRecord(
        1L, "uid", 1L, date, km, 60.0, 30, null, null, null, null, "", "", "", fuelUsed
    )

    @Test fun fullToFullGivesKmPerLitre() {
        val list = MileageCalc.intervals(listOf(
            fuel(1, "2026-11-01 08:00", 10.0, 1000.0, true),
            fuel(2, "2026-11-10 08:00", 4.0, 1150.0, true)
        ))
        assertEquals(1, list.size)
        assertEquals(37.5, list[0].kmPerL, 1e-9)
        assertEquals(2L, list[0].fuelId)
    }

    @Test fun partialFillsAreAddedToNextFullTank() {
        val list = MileageCalc.intervals(listOf(
            fuel(1, "2026-11-01 08:00", 10.0, 1000.0, true),
            fuel(2, "2026-11-05 08:00", 2.0, 1080.0, false),
            fuel(3, "2026-11-10 08:00", 2.0, 1150.0, true)
        ))
        assertEquals(1, list.size)
        assertEquals(150.0, list[0].km, 1e-9)
        assertEquals(4.0, list[0].litres, 1e-9)
        assertEquals(37.5, list[0].kmPerL, 1e-9)
    }

    @Test fun singleFullTankIsOnlyABaseline() {
        assertTrue(MileageCalc.intervals(listOf(fuel(1, "2026-11-01 08:00", 10.0, 1000.0, true))).isEmpty())
        assertEquals(0.0, MileageCalc.overall(emptyList()), 0.0)
    }

    @Test fun overallIsDistanceWeighted() {
        val a = FuelInterval(2, "a", "b", 150.0, 4.0)
        val b = FuelInterval(3, "b", "c", 100.0, 3.0)
        assertEquals(250.0 / 7.0, MileageCalc.overall(listOf(a, b)), 1e-9)
    }

    @Test fun previewMatchesWhatSavingWouldCalculate() {
        val existing = listOf(fuel(1, "2026-11-01 08:00", 10.0, 1000.0, true))
        val p = MileageCalc.previewNewEntry(existing, 1150.0, 4.0, true)
        assertNotNull(p)
        assertEquals(37.5, p!!.kmPerL, 1e-9)
        assertNull(MileageCalc.previewNewEntry(existing, 1150.0, 4.0, false))
    }

    @Test fun tripMileageSources() {
        val interval = FuelInterval(2, "2026-11-01 08:00", "2026-11-10 08:00", 150.0, 4.0)
        assertEquals("Interval est.", MileageCalc.tripMileage(trip("2026-11-05 09:00", 20.0), listOf(interval), 30.0)!!.source)
        assertEquals("Average est.", MileageCalc.tripMileage(trip("2026-12-01 09:00", 20.0), listOf(interval), 30.0)!!.source)
        val actual = MileageCalc.tripMileage(trip("2026-11-05 09:00", 80.0, fuelUsed = 2.0), listOf(interval), 30.0)!!
        assertEquals("Actual", actual.source)
        assertEquals(40.0, actual.kmPerL, 1e-9)
        assertNull(MileageCalc.tripMileage(trip("2026-11-05 09:00", 20.0), emptyList(), 0.0))
    }

    @Test fun monthBucketsGroupAndWeight() {
        val intervals = listOf(
            FuelInterval(2, "2026-11-01 08:00", "2026-11-10 08:00", 150.0, 4.0),
            FuelInterval(3, "2026-11-10 08:00", "2026-11-20 08:00", 100.0, 3.0),
            FuelInterval(4, "2026-12-01 08:00", "2026-12-05 08:00", 90.0, 2.0)
        )
        val month = MileageCalc.mileageBuckets(intervals, Period.MONTH)
        assertEquals(listOf("2026-11", "2026-12"), month.map { it.label })
        assertEquals(250.0 / 7.0, month[0].value, 1e-9)
        assertEquals(45.0, month[1].value, 1e-9)
        assertEquals(3, MileageCalc.mileageBuckets(intervals, Period.DAY).size)
        assertEquals(1, MileageCalc.mileageBuckets(intervals, Period.YEAR).size)
    }

    @Test fun distanceBucketsSumTripsPerDay() {
        val trips = listOf(trip("2026-11-05 09:00", 10.0), trip("2026-11-05 18:00", 5.0), trip("2026-11-06 09:00", 7.0))
        val day = MileageCalc.distanceBuckets(trips, Period.DAY)
        assertEquals(listOf(15.0, 7.0), day.map { it.value })
    }

    @Test fun routeCodecRoundTripAndDownsample() {
        val pts = (0 until 1000).map { 13.0 + it * 0.0001 to 80.0 + it * 0.0001 }
        val decoded = RouteCodec.decode(RouteCodec.encode(pts, 400))
        assertEquals(400, decoded.size)
        assertEquals(13.0, decoded.first().first, 1e-5)
        assertEquals(13.0 + 999 * 0.0001, decoded.last().first, 1e-5)
        assertEquals(1, RouteCodec.decode(RouteCodec.encode(listOf(13.1 to 80.2))).size)
        assertTrue(RouteCodec.decode("").isEmpty())
    }
}
