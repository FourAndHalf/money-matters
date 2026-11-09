package com.moneymatters.app.core.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenerMoodTest {
    private fun universe() = listOf(
        metrics("UP", (1..260).map { 100.0 + it }, volume = 200_000),
        metrics("DOWN", (1..260).map { 400.0 - it }, volume = 200_000),
        metrics("FLAT", List(260) { 100.0 }, volume = 10),
    )

    @Test fun conditionsAreAndedAndMissingMetricsDoNotMatch() {
        val u = universe()
        assertEquals(listOf("UP"), runScreen(u, listOf(ScreenCondition(ScreenMetric.Ret1y, ScreenOp.Above, 10.0))).map { it.symbol })
        assertEquals(listOf("UP"), runScreen(u, listOf(ScreenCondition(ScreenMetric.Ret1y, ScreenOp.Above, -100.0), ScreenCondition(ScreenMetric.Rsi, ScreenOp.Above, 90.0))).map { it.symbol })
        val short = listOf(metrics("NEW", List(10) { 100.0 }))
        assertTrue(runScreen(short, listOf(ScreenCondition(ScreenMetric.Ret1y, ScreenOp.Below, 1000.0))).isEmpty())
    }

    @Test fun conditionsRoundTripAndDropRetiredLines() {
        val c = listOf(ScreenCondition(ScreenMetric.Rsi, ScreenOp.Below, 30.0), ScreenCondition(ScreenMetric.Turnover, ScreenOp.Above, 500.0))
        assertEquals(c, decodeConditions(encodeConditions(c)))
        assertEquals(1, decodeConditions("rsi|lt|30.0\ngone|gt|1\nbroken").size)
    }

    @Test fun presetsRunOnRealisticData() {
        PresetScreens.forEach { runScreen(universe(), it.conditions) } // none throws
        val nearHigh = PresetScreens.first { it.name == "Near 52-week high" }
        val near = runScreen(universe(), nearHigh.conditions.dropLast(1)).map { it.symbol }
        assertTrue("UP" in near)
        assertFalse("DOWN" in near)
    }

    @Test fun scorecardRanksAgainstTheMarketAndKeepsUnknownsUnknown() {
        val u = (1..40).map { metrics("S$it", (1..260).map { d -> 100.0 + d * (it - 20) / 10.0 + 1000 }) } + universe()
        val up = u.first { it.symbol == "UP" }
        val card = scorecardFor(up, u)
        assertTrue(card.cards.first { it.title == "Performance" }.score!! > 5)
        assertEquals(Verdict.Unknown, scorecardFor(metrics("NEW", List(10) { 100.0 }), listOf(metrics("NEW", List(10) { 100.0 }))).cards.first { it.title == "Performance" }.verdict)
    }

    @Test fun redFlagsComeFromSeriesFallsAndThinTrading() {
        val bz = metrics("BAD", List(30) { 100.0 }, volume = 10, series = "BZ")
        val titles = redFlagsFor(bz).map { it.title }
        assertTrue("Not in the normal series" in titles)
        assertTrue("Thinly traded" in titles)
        assertTrue(redFlagsFor(metrics("OK", List(30) { 100.0 })).none { it.title == "Not in the normal series" })
    }

    @Test fun zonesFollowTheCutOffs() {
        assertEquals(MoodZone.ExtremeFear, moodZone(23.9))
        assertEquals(MoodZone.Fear, moodZone(30.0))
        assertEquals(MoodZone.Greed, moodZone(50.0))
        assertEquals(MoodZone.Greed, moodZone(70.0))
        assertEquals(MoodZone.ExtremeGreed, moodZone(70.1))
    }

    @Test fun zScoreOfTheMeanIsFiftyAndExtremesPinToTheEnds() {
        val window = (1..40).map { it.toDouble() }
        assertEquals(50.0, zScore100(window, window.average())!!, 0.5)
        assertTrue(zScore100(window + 500.0, 500.0)!! > 95)
        assertNull(zScore100(listOf(1.0, 2.0), 2.0))
        assertEquals(50.0, zScore100(List(30) { 5.0 }, 5.0)!!, 1e-9)
    }

    @Test fun moodIsBuiltFromWhatHistoryAllowsAndNamesTheRest() {
        // 70 days, ten stocks that rise on most days then a rally day: breadth + strength + momentum all computable.
        val all = (1..10).flatMap { s -> rows("S$s", (1..70).map { d -> 100.0 + d + if (d % 7 == 0) -3 else 0 }) }
        val reading = moodReading(all)!!
        assertNotNull(reading.score)
        assertEquals(70, reading.daysOfHistory)
        assertEquals(3, reading.factors.count { it.score != null })
        assertEquals(3, reading.factors.count { it.score == null && it.note!!.contains("licensed feed") || it.note?.contains("gold") == true })
        assertFalse(reading.zone == null)
    }

    @Test fun moodWithOneDayOfDataScoresNothingRatherThanInventingIt() {
        val reading = moodReading(rows("A", listOf(100.0)))!!
        assertNull(reading.score)
        assertNull(reading.zone)
    }
}
