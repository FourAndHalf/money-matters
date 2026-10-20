package com.moneymatters.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.profile.TaxRegime
import com.moneymatters.app.core.security.LockTimeout
import com.moneymatters.app.feature.accounts.AccountKind
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.InnerPanel
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.SegmentedControl
import com.moneymatters.app.ui.components.Toggle
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.LocalDate

private const val AMOUNT_ERROR = "Enter an amount like 12,500.50"

/**
 * Onboarding & setup (P01). A linear wizard: nothing is written until leaving the Goals step,
 * when [onSave] commits the whole [OnboardingForm] (see [OnboardingSaver]). [onSave] defaults to
 * a no-op so the screen can be previewed and tested without a real database.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    wizard: OnboardingWizard = remember { OnboardingWizard() },
    onSave: suspend (OnboardingForm) -> Unit = {},
    today: () -> LocalDate = LocalDate::now,
) {
    val form = wizard.form
    val step = wizard.step
    BackHandler(enabled = wizard.canGoBack) { wizard.back() }

    Column(modifier.fillMaxSize().background(MoneyTheme.colors.canvas).safeDrawingPadding()) {
        OnboardingProgress(step)
        Box(Modifier.weight(1f).padding(horizontal = MoneyTheme.spacing.margin)) {
            when (step) {
                OnboardingStep.Welcome -> WelcomeStep()
                OnboardingStep.Profile -> ProfileStep(form.profile, today()) { wizard.form = form.copy(profile = it) }
                OnboardingStep.Passcode -> PasscodeStep(
                    form.passcode, form.passcodeConfirm, form.biometricEnabled, form.lockTimeout,
                    { wizard.form = form.copy(passcode = it) },
                    { wizard.form = form.copy(passcodeConfirm = it) },
                    { wizard.form = form.copy(biometricEnabled = it) },
                ) { wizard.form = form.copy(lockTimeout = it) }
                OnboardingStep.Accounts -> AccountsStep(form.accounts) { wizard.form = form.copy(accounts = it) }
                OnboardingStep.Income -> IncomeStep(form.income) { wizard.form = form.copy(income = it) }
                OnboardingStep.Categories -> CategoriesStep(form.categories) { wizard.form = form.copy(categories = it) }
                OnboardingStep.Goals -> GoalsStep(form.goals) { wizard.form = form.copy(goals = it) }
                OnboardingStep.Done -> DoneStep()
            }
        }
        if (wizard.saveFailed) {
            Text(
                "Couldn't save your setup. It's safe to try again.",
                style = MoneyTheme.type.bodySm,
                color = MoneyTheme.colors.negative,
                modifier = Modifier.padding(horizontal = MoneyTheme.spacing.margin),
            )
        }
        OnboardingFooter(
            step = step,
            showBack = wizard.canGoBack,
            canAdvance = wizard.canAdvance(today()),
            isSaving = wizard.isSaving,
            onBack = wizard::back,
            onNext = { wizard.next(today(), onSave, onFinished) },
        )
    }
}

@Composable
private fun OnboardingProgress(step: OnboardingStep) {
    val steps = OnboardingStep.entries
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MoneyTheme.spacing.margin, vertical = MoneyTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        steps.forEach { s ->
            val done = s.ordinal <= step.ordinal
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (done) MoneyTheme.colors.anchor else MoneyTheme.colors.surfaceHigh),
            )
        }
    }
}

@Composable
private fun OnboardingFooter(step: OnboardingStep, showBack: Boolean, canAdvance: Boolean, isSaving: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(MoneyTheme.spacing.margin),
        horizontalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.sm),
    ) {
        if (showBack) {
            Pill("Back", onBack, modifier = Modifier.weight(1f).height(48.dp))
        }
        Pill(
            text = when {
                isSaving -> "Saving…"
                step == OnboardingStep.Goals -> "Finish setup"
                step == OnboardingStep.Done -> "Get started"
                else -> "Next"
            },
            onClick = { if (canAdvance) onNext() },
            modifier = Modifier.weight(2f).height(48.dp),
            style = if (canAdvance) PillStyle.Primary else PillStyle.Secondary,
            height = 48.dp,
        )
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Text(title, style = MoneyTheme.type.headlineMd, color = MoneyTheme.colors.ink)
    Text(subtitle, style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 4.dp, bottom = MoneyTheme.spacing.lg))
}

@Composable
private fun WelcomeStep() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Welcome to Money Matters", style = MoneyTheme.type.headlineMd, color = MoneyTheme.colors.ink)
        Text(
            "A few quick steps: your profile, a passcode, your accounts, and your goals. Takes about two minutes.",
            style = MoneyTheme.type.bodySm,
            color = MoneyTheme.colors.muted,
            modifier = Modifier.padding(top = MoneyTheme.spacing.sm),
        )
    }
}

/** Shows DDMMYYYY digits as DD/MM/YYYY, so the number keyboard (which often has no "/") is enough. */
private object DateDigitsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = buildString {
            digits.forEachIndexed { i, c ->
                if (i == 2 || i == 4) append('/')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                else -> offset + 2
            }.coerceAtMost(shown.length)

            override fun transformedToOriginal(offset: Int) = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }.coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

@Composable
private fun ProfileStep(profile: ProfileInfo, today: LocalDate, onChange: (ProfileInfo) -> Unit) {
    val dobDigits = profile.dateOfBirthDigits
    Column(Modifier.verticalScroll(rememberScrollState())) {
        StepTitle("Your profile", "Used for the ₹1Cr-by-30 goal and to know when your month starts.")
        FormField(
            "Date of birth", dobDigits, { onChange(profile.copy(dateOfBirthDigits = it.filter(Char::isDigit).take(8))) },
            placeholder = "DD/MM/YYYY", keyboardType = KeyboardType.Number, visualTransformation = DateDigitsTransformation,
            error = if (dobDigits.length == 8 && parseDateOfBirth(dobDigits, today) == null) "Not a valid past date" else null,
            testTag = "dob-field",
        )
        Spacer(Modifier.height(MoneyTheme.spacing.md))
        FormField(
            "Month start day (payday)", profile.monthStartDay, { onChange(profile.copy(monthStartDay = it.filter(Char::isDigit).take(2))) },
            keyboardType = KeyboardType.Number,
            error = if (profile.monthStartDay.isNotEmpty() && parseMonthStartDay(profile.monthStartDay) == null) "Pick a day from 1 to 28" else null,
            testTag = "payday-field",
        )
        Spacer(Modifier.height(MoneyTheme.spacing.md))
        Text("Tax regime", style = MoneyTheme.type.labelRegular, color = MoneyTheme.colors.muted)
        Spacer(Modifier.height(6.dp))
        SegmentedControl(TaxRegime.entries, profile.taxRegime, { onChange(profile.copy(taxRegime = it)) }, { it.name })
    }
}

@Composable
private fun PasscodeStep(
    passcode: String,
    confirm: String,
    biometricEnabled: Boolean,
    lockTimeout: LockTimeout,
    onPasscodeChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onLockTimeoutChange: (LockTimeout) -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        StepTitle("Set a passcode", "4 to 6 digits. You'll use this (or biometrics) every time you open the app.")
        FormField(
            "Passcode", passcode, { onPasscodeChange(it.filter(Char::isDigit).take(6)) },
            keyboardType = KeyboardType.NumberPassword, visualTransformation = PasswordVisualTransformation(), testTag = "passcode-field",
        )
        Spacer(Modifier.height(MoneyTheme.spacing.md))
        FormField(
            "Confirm passcode", confirm, { onConfirmChange(it.filter(Char::isDigit).take(6)) },
            keyboardType = KeyboardType.NumberPassword, visualTransformation = PasswordVisualTransformation(),
            error = if (confirm.isNotEmpty() && confirm != passcode) "Doesn't match" else null,
            testTag = "passcode-confirm-field",
        )
        Spacer(Modifier.height(MoneyTheme.spacing.md))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Also enable biometric unlock", style = MoneyTheme.type.labelRegular, color = MoneyTheme.colors.ink)
            }
            Toggle(biometricEnabled, onBiometricChange)
        }
        Spacer(Modifier.height(MoneyTheme.spacing.md))
        Text("Lock after leaving the app", style = MoneyTheme.type.labelRegular, color = MoneyTheme.colors.muted)
        Spacer(Modifier.height(6.dp))
        SegmentedControl(LockTimeout.entries, lockTimeout, onLockTimeoutChange, { it.label() })
    }
}

private fun LockTimeout.label() = when (this) {
    LockTimeout.Immediately -> "Immediately"
    LockTimeout.OneMinute -> "1 min"
    LockTimeout.FiveMinutes -> "5 min"
}

@Composable
private fun AccountsStep(accounts: List<NewAccountDraft>, onChange: (List<NewAccountDraft>) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StepTitle("Your accounts", "Banks, cash, cards, loans, FDs — whatever you track. Add an opening balance if you have one.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.sm)) {
            items(accounts.size) { index ->
                AccountDraftCard(
                    draft = accounts[index],
                    onUpdate = { updated -> onChange(accounts.toMutableList().also { it[index] = updated }) },
                    onRemove = if (accounts.size > 1) { { onChange(accounts.toMutableList().also { it.removeAt(index) }) } } else null,
                )
            }
            item {
                Pill("+ Add another account", { onChange(accounts + NewAccountDraft()) }, modifier = Modifier.fillMaxWidth().height(44.dp))
            }
        }
    }
}

@Composable
private fun AccountDraftCard(draft: NewAccountDraft, onUpdate: (NewAccountDraft) -> Unit, onRemove: (() -> Unit)?) {
    val owed = draft.kind.toLedgerType() == AccountType.Liability
    InnerPanel {
        FormField("Account name", draft.name, { onUpdate(draft.copy(name = it)) }, placeholder = "e.g. HDFC Bank", testTag = "account-name-field")
        Spacer(Modifier.height(MoneyTheme.spacing.sm))
        SegmentedControl(AccountKind.entries, draft.kind, { onUpdate(draft.copy(kind = it)) }, { it.shortLabel() })
        Spacer(Modifier.height(MoneyTheme.spacing.sm))
        FormField(
            if (owed) "Amount owed today (₹)" else "Opening balance (₹)", draft.openingBalance, { onUpdate(draft.copy(openingBalance = it)) },
            placeholder = "0", keyboardType = KeyboardType.Decimal,
            error = if (isValidOptionalAmount(draft.openingBalance)) null else AMOUNT_ERROR,
            testTag = "opening-balance-field",
        )
        if (onRemove != null) {
            Spacer(Modifier.height(MoneyTheme.spacing.sm))
            Pill("Remove", onRemove, style = PillStyle.Destructive)
        }
    }
}

@Composable
private fun IncomeStep(income: IncomeProfile, onChange: (IncomeProfile) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        StepTitle("Income profile", "A rough monthly take-home is enough for now; you can refine this later.")
        FormField(
            "Monthly take-home (₹)", income.monthlyTakeHome, { onChange(income.copy(monthlyTakeHome = it)) },
            keyboardType = KeyboardType.Decimal,
            error = if (isValidOptionalAmount(income.monthlyTakeHome)) null else AMOUNT_ERROR,
            testTag = "income-field",
        )
        Spacer(Modifier.height(MoneyTheme.spacing.md))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("I also have freelance income", style = MoneyTheme.type.labelRegular, color = MoneyTheme.colors.ink)
            }
            Toggle(income.hasFreelanceIncome, { onChange(income.copy(hasFreelanceIncome = it)) })
        }
    }
}

@Composable
private fun CategoriesStep(categories: List<CategoryChoice>, onChange: (List<CategoryChoice>) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StepTitle("Categories", "Mark the essentials — these are what \"leeway\" protects first.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(categories.size) { index ->
                val category = categories[index]
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(category.name, style = MoneyTheme.type.labelRegular, color = MoneyTheme.colors.ink, modifier = Modifier.weight(1f))
                    Toggle(category.essential, { checked -> onChange(categories.toMutableList().also { it[index] = category.copy(essential = checked) }) })
                }
            }
        }
    }
}

@Composable
private fun GoalsStep(goals: GoalsSetup, onChange: (GoalsSetup) -> Unit) {
    Column {
        StepTitle("Goals", "Two defaults to start with; add more any time from Goals.")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Emergency fund: ₹5L", style = MoneyTheme.type.labelRegular, color = MoneyTheme.colors.ink)
            }
            Toggle(goals.emergencyFundEnabled, { onChange(goals.copy(emergencyFundEnabled = it)) })
        }
        Spacer(Modifier.height(MoneyTheme.spacing.sm))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("₹1Cr by 30", style = MoneyTheme.type.labelRegular, color = MoneyTheme.colors.ink)
            }
            Toggle(goals.croreByThirtyEnabled, { onChange(goals.copy(croreByThirtyEnabled = it)) })
        }
    }
}

@Composable
private fun DoneStep() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("You're all set", style = MoneyTheme.type.headlineMd, color = MoneyTheme.colors.ink)
        Text(
            "Your accounts are in the ledger and your passcode is set. Let's get started.",
            style = MoneyTheme.type.bodySm,
            color = MoneyTheme.colors.muted,
            modifier = Modifier.padding(top = MoneyTheme.spacing.sm),
        )
    }
}
