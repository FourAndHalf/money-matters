package com.moneymatters.app.feature.onboarding

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.parseRupees
import com.moneymatters.app.core.profile.TaxRegime
import com.moneymatters.app.core.profile.UserProfile
import com.moneymatters.app.feature.accounts.AccountKind
import java.time.DateTimeException
import java.time.LocalDate

/** The wizard's fixed order (plan.html → P01). */
enum class OnboardingStep { Welcome, Profile, Passcode, Accounts, Income, Categories, Goals, Done }

/** [dateOfBirthDigits] holds just the typed digits, DDMMYYYY; the field shows the slashes. */
@Immutable
data class ProfileInfo(val dateOfBirthDigits: String = "", val monthStartDay: String = "1", val taxRegime: TaxRegime = TaxRegime.New)

/** One account being added by the setup wizard, before it's posted to the real ledger. */
@Immutable
data class NewAccountDraft(val name: String = "", val kind: AccountKind = AccountKind.Bank, val openingBalance: String = "")

/** [AccountKind] is a UI grouping; the ledger only knows the five double-entry types. */
fun AccountKind.toLedgerType() = when (this) {
    AccountKind.Bank, AccountKind.Cash, AccountKind.FixedDeposit, AccountKind.Investment -> AccountType.Asset
    AccountKind.CreditCard, AccountKind.Loan -> AccountType.Liability
}

/** Short enough that all six fit one segmented control on a phone. */
fun AccountKind.shortLabel() = when (this) {
    AccountKind.Bank -> "Bank"
    AccountKind.Cash -> "Cash"
    AccountKind.CreditCard -> "Card"
    AccountKind.Loan -> "Loan"
    AccountKind.FixedDeposit -> "FD"
    AccountKind.Investment -> "Invest"
}

@Immutable
data class IncomeProfile(val monthlyTakeHome: String = "", val hasFreelanceIncome: Boolean = false)

@Immutable
data class CategoryChoice(val name: String, val essential: Boolean)

/** plan.html → P01: "Default category tree; mark categories as essential." */
val DefaultCategories = listOf(
    CategoryChoice("Groceries", essential = true),
    CategoryChoice("Bills", essential = true),
    CategoryChoice("Rent / EMI", essential = true),
    CategoryChoice("Transport", essential = true),
    CategoryChoice("Health", essential = true),
    CategoryChoice("Dining", essential = false),
    CategoryChoice("Entertainment", essential = false),
    CategoryChoice("Shopping", essential = false),
    CategoryChoice("Subscriptions", essential = false),
)

@Immutable
data class GoalsSetup(val emergencyFundEnabled: Boolean = true, val croreByThirtyEnabled: Boolean = true)

/** Everything the wizard collects. Nothing is written anywhere until the last step submits it. */
@Immutable
data class OnboardingForm(
    val profile: ProfileInfo = ProfileInfo(),
    val passcode: String = "",
    val passcodeConfirm: String = "",
    val biometricEnabled: Boolean = true,
    val accounts: List<NewAccountDraft> = listOf(NewAccountDraft()),
    val income: IncomeProfile = IncomeProfile(),
    val categories: List<CategoryChoice> = DefaultCategories,
    val goals: GoalsSetup = GoalsSetup(),
)

/** A real past date from DDMMYYYY digits, or null. Rejects impossible dates like 31/02. */
fun parseDateOfBirth(digits: String, today: LocalDate): LocalDate? {
    if (digits.length != 8 || !digits.all(Char::isDigit)) return null
    val date = try {
        LocalDate.of(digits.substring(4).toInt(), digits.substring(2, 4).toInt(), digits.substring(0, 2).toInt())
    } catch (_: DateTimeException) {
        return null
    }
    return date.takeIf { it.year >= 1900 && it.isBefore(today) }
}

/** 1–28 only, so every month has the day. */
fun parseMonthStartDay(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..28 }

fun isValidPasscode(passcode: String) = passcode.length in 4..6 && passcode.all(Char::isDigit)

/** A blank amount means "none"; anything typed must parse to paise. */
fun isValidOptionalAmount(text: String) = text.isBlank() || parseRupees(text) != null

fun OnboardingForm.canAdvance(step: OnboardingStep, today: LocalDate): Boolean = when (step) {
    OnboardingStep.Profile -> parseDateOfBirth(profile.dateOfBirthDigits, today) != null && parseMonthStartDay(profile.monthStartDay) != null
    OnboardingStep.Passcode -> isValidPasscode(passcode) && passcode == passcodeConfirm
    OnboardingStep.Accounts -> accounts.any { it.name.isNotBlank() } && accounts.all { isValidOptionalAmount(it.openingBalance) }
    OnboardingStep.Income -> isValidOptionalAmount(income.monthlyTakeHome)
    else -> true
}

/** Only call once every step validates; throws otherwise. */
fun OnboardingForm.toUserProfile(today: LocalDate): UserProfile = UserProfile(
    dateOfBirth = requireNotNull(parseDateOfBirth(profile.dateOfBirthDigits, today)) { "Invalid date of birth" },
    monthStartDay = requireNotNull(parseMonthStartDay(profile.monthStartDay)) { "Invalid month start day" },
    taxRegime = profile.taxRegime,
    monthlyTakeHome = income.monthlyTakeHome.takeIf { it.isNotBlank() }?.let { requireNotNull(parseRupees(it)) { "Invalid take-home" } },
    hasFreelanceIncome = income.hasFreelanceIncome,
    essentialCategories = categories.filter { it.essential }.map { it.name }.toSet(),
    emergencyFundGoal = goals.emergencyFundEnabled,
    croreByThirtyGoal = goals.croreByThirtyEnabled,
)

/** The opening balance as paise, or null when blank or zero (nothing to post). */
internal fun NewAccountDraft.openingPaise(): Paise? =
    openingBalance.takeIf { it.isNotBlank() }?.let { requireNotNull(parseRupees(it)) { "Invalid opening balance for $name" } }?.takeIf { it.value > 0 }
