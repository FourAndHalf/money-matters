package com.moneymatters.app.core.profile

import android.content.Context
import android.content.SharedPreferences
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.security.encryptedPrefs
import java.time.LocalDate

private const val PREFS_NAME = "app_profile"
private const val KEY_SETUP_COMPLETE = "setup_complete"
private const val KEY_DATE_OF_BIRTH = "date_of_birth"
private const val KEY_MONTH_START_DAY = "month_start_day"
private const val KEY_TAX_REGIME = "tax_regime"
private const val KEY_MONTHLY_TAKE_HOME = "monthly_take_home_paise"
private const val KEY_FREELANCE = "has_freelance_income"
private const val KEY_ESSENTIAL_CATEGORIES = "essential_categories"
private const val KEY_GOAL_EMERGENCY_FUND = "goal_emergency_fund"
private const val KEY_GOAL_CRORE_BY_THIRTY = "goal_crore_by_thirty"

enum class TaxRegime { New, Old }

/** Everything onboarding (P01) learns about the user that isn't a ledger entry or the passcode. */
data class UserProfile(
    val dateOfBirth: LocalDate,
    val monthStartDay: Int,
    val taxRegime: TaxRegime,
    val monthlyTakeHome: Paise?,
    val hasFreelanceIncome: Boolean,
    val essentialCategories: Set<String>,
    val emergencyFundGoal: Boolean,
    val croreByThirtyGoal: Boolean,
)

class ProfileStore(private val prefs: SharedPreferences) {
    /** True once onboarding has finished; the app shows the wizard until then. */
    fun isSetupComplete(): Boolean = prefs.getBoolean(KEY_SETUP_COMPLETE, false)

    /** Re-saves an already-complete profile from Settings. Setup is complete either way, so this is just [completeSetup] under a name that fits editing, not onboarding. */
    fun save(profile: UserProfile) = completeSetup(profile)

    /** Saves [profile] and marks setup complete in one write. */
    fun completeSetup(profile: UserProfile) {
        val editor = prefs.edit()
        val takeHome = profile.monthlyTakeHome
        if (takeHome != null) editor.putLong(KEY_MONTHLY_TAKE_HOME, takeHome.value) else editor.remove(KEY_MONTHLY_TAKE_HOME)
        editor
            .putString(KEY_DATE_OF_BIRTH, profile.dateOfBirth.toString())
            .putInt(KEY_MONTH_START_DAY, profile.monthStartDay)
            .putString(KEY_TAX_REGIME, profile.taxRegime.name)
            .putBoolean(KEY_FREELANCE, profile.hasFreelanceIncome)
            .putStringSet(KEY_ESSENTIAL_CATEGORIES, profile.essentialCategories)
            .putBoolean(KEY_GOAL_EMERGENCY_FUND, profile.emergencyFundGoal)
            .putBoolean(KEY_GOAL_CRORE_BY_THIRTY, profile.croreByThirtyGoal)
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .commit()
    }

    fun profile(): UserProfile? {
        if (!isSetupComplete()) return null
        return UserProfile(
            dateOfBirth = LocalDate.parse(prefs.getString(KEY_DATE_OF_BIRTH, null)),
            monthStartDay = prefs.getInt(KEY_MONTH_START_DAY, 1),
            taxRegime = TaxRegime.valueOf(prefs.getString(KEY_TAX_REGIME, TaxRegime.New.name)!!),
            monthlyTakeHome = if (prefs.contains(KEY_MONTHLY_TAKE_HOME)) Paise(prefs.getLong(KEY_MONTHLY_TAKE_HOME, 0)) else null,
            hasFreelanceIncome = prefs.getBoolean(KEY_FREELANCE, false),
            essentialCategories = prefs.getStringSet(KEY_ESSENTIAL_CATEGORIES, emptySet()).orEmpty().toSet(),
            emergencyFundGoal = prefs.getBoolean(KEY_GOAL_EMERGENCY_FUND, false),
            croreByThirtyGoal = prefs.getBoolean(KEY_GOAL_CRORE_BY_THIRTY, false),
        )
    }

    companion object {
        /** The real store: an Android Keystore-backed encrypted prefs file. */
        fun encrypted(context: Context) = ProfileStore(encryptedPrefs(context, PREFS_NAME))
    }
}
