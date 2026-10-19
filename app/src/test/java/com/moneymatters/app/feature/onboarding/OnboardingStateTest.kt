package com.moneymatters.app.feature.onboarding

import com.moneymatters.app.core.money.rupees
import com.moneymatters.app.core.profile.TaxRegime
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {

    private val today = LocalDate.of(2026, 10, 20)

    @Test fun `parses a real past date of birth from digits`() {
        assertEquals(LocalDate.of(1998, 8, 15), parseDateOfBirth("15081998", today))
        assertEquals(LocalDate.of(2000, 2, 29), parseDateOfBirth("29022000", today))
    }

    @Test fun `rejects impossible, future, partial or ancient dates`() {
        listOf("31022000", "29022001", "00011990", "15131990", "21102026", "01012030", "1508199", "01011899", "ab081998").forEach {
            assertNull(it, parseDateOfBirth(it, today))
        }
    }

    @Test fun `month start day is 1 to 28`() {
        assertEquals(1, parseMonthStartDay("1"))
        assertEquals(28, parseMonthStartDay("28"))
        assertNull(parseMonthStartDay("0"))
        assertNull(parseMonthStartDay("29"))
        assertNull(parseMonthStartDay(""))
    }

    @Test fun `each step gates on its own fields`() {
        val form = OnboardingForm()
        assertTrue(form.canAdvance(OnboardingStep.Welcome, today))
        assertFalse(form.canAdvance(OnboardingStep.Profile, today))
        assertTrue(form.copy(profile = ProfileInfo("15081998")).canAdvance(OnboardingStep.Profile, today))
        assertFalse(form.copy(profile = ProfileInfo("15081998", monthStartDay = "31")).canAdvance(OnboardingStep.Profile, today))

        assertFalse(form.copy(passcode = "123", passcodeConfirm = "123").canAdvance(OnboardingStep.Passcode, today))
        assertFalse(form.copy(passcode = "1234", passcodeConfirm = "1235").canAdvance(OnboardingStep.Passcode, today))
        assertTrue(form.copy(passcode = "1234", passcodeConfirm = "1234").canAdvance(OnboardingStep.Passcode, today))

        assertFalse(form.canAdvance(OnboardingStep.Accounts, today))
        assertTrue(form.copy(accounts = listOf(NewAccountDraft("HDFC"))).canAdvance(OnboardingStep.Accounts, today))
        assertFalse(form.copy(accounts = listOf(NewAccountDraft("HDFC", openingBalance = "12.345"))).canAdvance(OnboardingStep.Accounts, today))

        assertTrue(form.canAdvance(OnboardingStep.Income, today))
        assertFalse(form.copy(income = IncomeProfile("lots")).canAdvance(OnboardingStep.Income, today))
    }

    @Test fun `builds the stored profile from the form`() {
        val form = OnboardingForm(
            profile = ProfileInfo("15081998", "5", TaxRegime.Old),
            income = IncomeProfile("92,000", hasFreelanceIncome = true),
            goals = GoalsSetup(emergencyFundEnabled = true, croreByThirtyEnabled = false),
        )
        val profile = form.toUserProfile(today)
        assertEquals(LocalDate.of(1998, 8, 15), profile.dateOfBirth)
        assertEquals(5, profile.monthStartDay)
        assertEquals(TaxRegime.Old, profile.taxRegime)
        assertEquals(92_000.rupees, profile.monthlyTakeHome)
        assertEquals(setOf("Groceries", "Bills", "Rent / EMI", "Transport", "Health"), profile.essentialCategories)
        assertFalse(profile.croreByThirtyGoal)
        assertNull(form.copy(income = IncomeProfile("")).toUserProfile(today).monthlyTakeHome)
    }
}
