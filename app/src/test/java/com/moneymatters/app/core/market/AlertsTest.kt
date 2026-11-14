package com.moneymatters.app.core.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsTest {
    private fun rule(kind: AlertKind, threshold: Double? = null, last: Boolean = false, active: Boolean = true) =
        AlertRuleEntity("r", "ABC", kind, threshold, active, last, 0)

    private val flat = metrics("ABC", List(80) { 100.0 })

    @Test fun priceRulesCompareTheLastClose() {
        assertTrue(conditionHolds(rule(AlertKind.PriceAbove, 99.0), flat)!!)
        assertFalse(conditionHolds(rule(AlertKind.PriceAbove, 101.0), flat)!!)
        assertTrue(conditionHolds(rule(AlertKind.PriceBelow, 101.0), flat)!!)
    }

    @Test fun ruleFiresOnlyWhenTheConditionTurnsTrue() {
        val m = mapOf("ABC" to flat)
        val first = evaluateAlerts(listOf(rule(AlertKind.PriceAbove, 99.0, last = false)), m, now = 5)
        assertEquals(1, first.events.size)
        assertTrue(first.rules.single().lastState)
        assertTrue(first.events.single().message.contains("ABC rose above ₹99.00"))
        // Still above the next day: remembered as true, so no second alert.
        val again = evaluateAlerts(listOf(rule(AlertKind.PriceAbove, 99.0, last = true)), m, now = 6)
        assertTrue(again.events.isEmpty())
        assertTrue(again.rules.isEmpty())
    }

    @Test fun ruleRearmsAfterTheConditionClears() {
        val m = mapOf("ABC" to flat)
        val cleared = evaluateAlerts(listOf(rule(AlertKind.PriceAbove, 150.0, last = true)), m, now = 1)
        assertTrue(cleared.events.isEmpty())
        assertFalse(cleared.rules.single().lastState)
    }

    @Test fun inactiveRulesAndMissingDataAreLeftAlone() {
        val m = mapOf("ABC" to flat)
        assertTrue(evaluateAlerts(listOf(rule(AlertKind.PriceAbove, 1.0, active = false)), m, 1).events.isEmpty())
        // 80 days is not enough for a 52-week-high call, so the rule is untouched rather than guessed.
        val short = metrics("ABC", List(40) { 100.0 })
        assertNull(conditionHolds(rule(AlertKind.SmaCrossUp), short))
        assertTrue(evaluateAlerts(listOf(rule(AlertKind.SmaCrossUp)), mapOf("ABC" to short), 1).rules.isEmpty())
        assertTrue(evaluateAlerts(listOf(rule(AlertKind.PriceAbove, 1.0)), emptyMap(), 1).events.isEmpty())
    }

    @Test fun newHighNeedsToBeatTheYearBeforeToday() {
        val climbing = metrics("ABC", (1..120).map { 100.0 + it })
        assertTrue(conditionHolds(rule(AlertKind.NewHigh), climbing)!!)
        val fell = metrics("ABC", (1..120).map { 100.0 + it } + listOf(150.0))
        assertFalse(conditionHolds(rule(AlertKind.NewHigh), fell)!!)
        assertFalse(conditionHolds(rule(AlertKind.NewLow), climbing)!!)
    }

    @Test fun oneDayMoveUsesAbsoluteChange() {
        val drop = metrics("ABC", List(30) { 100.0 } + listOf(90.0))
        assertTrue(conditionHolds(rule(AlertKind.MoveOneDay, 5.0), drop)!!)
        assertFalse(conditionHolds(rule(AlertKind.MoveOneDay, 15.0), drop)!!)
    }
}
