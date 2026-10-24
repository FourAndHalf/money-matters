package com.moneymatters.app.feature.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.feature.transactions.plainRupees
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.profile.ProfileStore
import com.moneymatters.app.core.profile.UserProfile
import com.moneymatters.app.core.security.LockTimeout
import com.moneymatters.app.core.security.PasscodeStore
import com.moneymatters.app.feature.books.displayName
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import com.moneymatters.app.feature.accounts.addAccountWithOpening
import com.moneymatters.app.feature.onboarding.NewAccountDraft
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Edits already-saved profile, security, account/category and preference data (plan.html → P21). */
class SettingsViewModel(
    private val repo: LedgerRepository,
    private val profiles: ProfileStore,
    private val passcodes: PasscodeStore,
    val prefs: SettingsPrefs,
    val ai: AiSettingsStore,
    private val hashing: CoroutineDispatcher = Dispatchers.Default,
    /** Runs after the app lock is switched on or off, so the home-screen widget can hide or show its amounts. */
    private val onLockChanged: () -> Unit = {},
) : ViewModel() {

    var profileForm by mutableStateOf(requireNotNull(profiles.profile()) { "Settings is only reachable after setup" })
    var isSavingProfile by mutableStateOf(false)
        private set
    var profileSaveFailed by mutableStateOf(false)
        private set

    var biometricEnabled by mutableStateOf(passcodes.isBiometricEnabled())
        private set
    var lockTimeout by mutableStateOf(passcodes.lockTimeout())
        private set
    var lockEnabled by mutableStateOf(passcodes.isLockEnabled())
        private set

    var currentPasscode by mutableStateOf("")
    var newPasscode by mutableStateOf("")
    var confirmPasscode by mutableStateOf("")
    var passcodeChangeFailed by mutableStateOf(false)
        private set
    var passcodeChanged by mutableStateOf(false)
        private set

    val snapshot: StateFlow<LedgerSnapshot?> =
        repo.snapshot().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveProfile() {
        viewModelScope.launch {
            isSavingProfile = true
            profileSaveFailed = false
            try {
                profiles.save(profileForm)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                profileSaveFailed = true
            } finally {
                isSavingProfile = false
            }
        }
    }

    fun updateLockEnabled(enabled: Boolean) {
        passcodes.setLockEnabled(enabled)
        lockEnabled = enabled
        onLockChanged()
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        passcodes.setBiometricEnabled(enabled)
        biometricEnabled = enabled
    }

    fun updateLockTimeout(timeout: LockTimeout) {
        passcodes.setLockTimeout(timeout)
        lockTimeout = timeout
    }

    /** Verifies [currentPasscode] before replacing it, keeping the existing biometric and timeout settings. Hashing is slow, so this runs off the main thread. */
    fun changePasscode() {
        viewModelScope.launch {
            passcodeChangeFailed = false
            passcodeChanged = false
            val valid = newPasscode.length >= 4 && newPasscode == confirmPasscode &&
                withContext(hashing) { passcodes.verify(currentPasscode) }
            if (!valid) {
                passcodeChangeFailed = true
                return@launch
            }
            withContext(hashing) { passcodes.save(newPasscode, biometricEnabled, lockTimeout) }
            currentPasscode = ""
            newPasscode = ""
            confirmPasscode = ""
            passcodeChanged = true
        }
    }

    var newAccount by mutableStateOf(NewAccountDraft())
    var newAccountError by mutableStateOf<String?>(null)
        private set
    var isSavingAccount by mutableStateOf(false)
        private set

    fun resetNewAccount() { newAccount = NewAccountDraft(); newAccountError = null }

    /** Adds the account from [newAccount] with its opening balance; [onSaved] runs once it is in the books. */
    fun saveNewAccount(onSaved: () -> Unit) {
        if (isSavingAccount) return
        viewModelScope.launch {
            isSavingAccount = true
            newAccountError = null
            try {
                addAccountWithOpening(repo, newAccount, LocalDate.now())
                resetNewAccount()
                onSaved()
            } catch (e: IllegalArgumentException) {
                newAccountError = e.message
            } finally {
                isSavingAccount = false
            }
        }
    }

    /** A credit card's limit and statement day being changed; null when no card is open for editing. */
    var cardEdit by mutableStateOf<com.moneymatters.app.feature.accounts.CardEditForm?>(null)
    var cardEditError by mutableStateOf<String?>(null)
        private set

    fun startEditCard(accountId: String, name: String, limit: com.moneymatters.app.core.money.Paise?, statementDay: Int?) {
        cardEditError = null
        cardEdit = com.moneymatters.app.feature.accounts.CardEditForm(accountId, name, limit?.plainRupees().orEmpty(), statementDay?.toString().orEmpty())
    }

    fun cancelEditCard() { cardEdit = null; cardEditError = null }

    fun saveCardEdit(onSaved: () -> Unit) {
        val form = cardEdit ?: return
        val limit = form.limit ?: return
        val day = form.day ?: return
        if (isSavingAccount) return
        viewModelScope.launch {
            isSavingAccount = true
            cardEditError = null
            try {
                repo.updateCreditCardDetails(form.accountId, limit, day)
                cardEdit = null
                onSaved()
            } catch (e: IllegalArgumentException) {
                cardEditError = e.message
            } finally {
                isSavingAccount = false
            }
        }
    }

    fun addAccount(name: String, type: AccountType, kind: AccountKind? = null) {
        viewModelScope.launch { repo.createAccount(name, type, kind = kind) }
    }

    /**
     * Renames an expense category, keeping its `Expenses:` prefix and its essential flag; [onDone] gets null on success or the
     * reason it was refused. Entries follow the rename because they reference the account by id.
     */
    fun renameCategory(account: AccountEntity, newName: String, onDone: (String?) -> Unit) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) { onDone("Give it a name"); return }
        viewModelScope.launch {
            try {
                val prefix = if (':' in account.name) account.name.substringBefore(':') + ":" else ""
                repo.renameAccount(account.id, prefix + trimmed)
                val old = account.displayName()
                val essentials = profileForm.essentialCategories
                if (old in essentials && old != trimmed) {
                    profileForm = profileForm.copy(essentialCategories = essentials - old + trimmed)
                    profiles.save(profileForm)
                }
                onDone(null)
            } catch (e: IllegalArgumentException) {
                onDone(e.message)
            }
        }
    }

    /** Deletes an account added by mistake; [onDone] gets null on success or why it was refused (see [LedgerRepository.deleteAccount]). */
    fun deleteAccount(id: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                repo.deleteAccount(id)
                onDone(null)
            } catch (e: IllegalArgumentException) {
                onDone(e.message)
            } catch (e: IllegalStateException) {
                onDone(e.message)
            }
        }
    }

    fun archiveAccount(id: String) {
        viewModelScope.launch { repo.archiveAccount(id) }
    }

    fun unarchiveAccount(id: String) {
        viewModelScope.launch { repo.unarchiveAccount(id) }
    }

    fun setInNetWorth(id: String, inNetWorth: Boolean) {
        viewModelScope.launch { repo.setInNetWorth(id, inNetWorth) }
    }

    fun toggleEssential(category: String) {
        val current = profileForm.essentialCategories
        profileForm = profileForm.copy(essentialCategories = if (category in current) current - category else current + category)
        profiles.save(profileForm)
    }

    suspend fun backup(context: Context, destination: Uri) = backupDatabase(context, repo, destination)

    suspend fun exportCsv(): String = exportCsv(repo)
}
