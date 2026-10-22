package com.moneymatters.app.core.invest

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CapitalGainsRulesTest {

    @Test fun `the shipped data file parses and gives the listed equity holding period`() {
        val rules = CapitalGainsRules.parse(File("src/main/assets/tax/capital_gains.json").readText())
        assertEquals(HoldingPeriodRule(12), rules.listedEquity(LocalDate.of(2026, 10, 20)))
        assertEquals(HoldingPeriodRule(24), rules.other(LocalDate.of(2026, 10, 20)))
    }

    @Test fun `the rule in force on the sale date is used`() {
        val rules = CapitalGainsRules.parse(
            """{"capitalGains":[
              {"effectiveFrom":"2018-04-01","listedEquityLongTermMonths":12,"otherLongTermMonths":24},
              {"effectiveFrom":"2030-04-01","listedEquityLongTermMonths":18,"otherLongTermMonths":30}]}""",
        )
        assertEquals(12, rules.listedEquity(LocalDate.of(2030, 3, 31)).longTermAfterMonths)
        assertEquals(18, rules.listedEquity(LocalDate.of(2030, 4, 1)).longTermAfterMonths)
        assertEquals(12, rules.listedEquity(LocalDate.of(2010, 1, 1)).longTermAfterMonths) // before the first entry: the earliest applies
    }
}
