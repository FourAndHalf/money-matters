package com.moneymatters.app.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * The wizard's step and answers. A [ViewModel] so a rotation or theme change mid-setup keeps
 * what was typed, without ever putting the passcode into saved instance state.
 */
class OnboardingWizard : ViewModel() {
    var step by mutableStateOf(OnboardingStep.Welcome)
        private set
    var form by mutableStateOf(OnboardingForm())
    var isSaving by mutableStateOf(false)
        private set
    var saveFailed by mutableStateOf(false)
        private set

    val canGoBack get() = step != OnboardingStep.Welcome && step != OnboardingStep.Done && !isSaving

    fun canAdvance(today: LocalDate) = !isSaving && form.canAdvance(step, today)

    fun back() {
        if (canGoBack) step = OnboardingStep.entries[step.ordinal - 1]
    }

    /** Leaving Goals submits everything through [save]; Done hands over to [onFinished]. */
    fun next(today: LocalDate, save: suspend (OnboardingForm) -> Unit, onFinished: () -> Unit) {
        if (!canAdvance(today)) return
        when (step) {
            OnboardingStep.Goals -> {
                isSaving = true
                saveFailed = false
                viewModelScope.launch {
                    try {
                        save(form)
                        form = form.copy(passcode = "", passcodeConfirm = "")
                        step = OnboardingStep.Done
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        saveFailed = true
                    } finally {
                        isSaving = false
                    }
                }
            }
            OnboardingStep.Done -> onFinished()
            else -> step = OnboardingStep.entries[step.ordinal + 1]
        }
    }
}
