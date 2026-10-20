package com.moneymatters.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.moneymatters.app.core.ledger.LedgerDatabaseProvider
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.profile.ProfileStore
import com.moneymatters.app.core.security.BiometricUnlock
import com.moneymatters.app.core.security.PasscodeStore
import com.moneymatters.app.feature.accounts.SampleAccounts
import com.moneymatters.app.feature.accounts.SampleLedgers
import com.moneymatters.app.feature.dashboard.SampleDashboard
import com.moneymatters.app.feature.lock.AppLock
import com.moneymatters.app.feature.lock.LockScreen
import com.moneymatters.app.feature.onboarding.OnboardingSaver
import com.moneymatters.app.feature.onboarding.OnboardingScreen
import com.moneymatters.app.feature.onboarding.OnboardingWizard
import com.moneymatters.app.feature.transactions.SampleTransactions
import com.moneymatters.app.ui.MoneyMattersApp
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SPLASH_EXIT_DURATION_MS = 420L
private const val SPLASH_EXIT_SCALE = 4f

class MainActivity : ComponentActivity() {
    private val onboarding: OnboardingWizard by viewModels()
    private val profiles by lazy { ProfileStore.encrypted(applicationContext) }
    private val passcodes by lazy { PasscodeStore.encrypted(applicationContext) }
    private val appLock: AppLock by viewModels {
        viewModelFactory { initializer { AppLock(passcodes, startLocked = profiles.isSetupComplete() && passcodes.isSet()) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Only runs on a cold process start; a warm resume from the background skips this entirely.
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
                splashScreenView.iconView,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, SPLASH_EXIT_SCALE),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, SPLASH_EXIT_SCALE),
            )
            val fadeOut = ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f)
            AnimatorSet().apply {
                playTogether(scaleUp, fadeOut)
                duration = SPLASH_EXIT_DURATION_MS
                interpolator = AccelerateInterpolator()
                doOnEnd { splashScreenView.remove() }
                start()
            }
        }

        setContent {
            MoneyMattersTheme {
                var setupComplete by remember { mutableStateOf(profiles.isSetupComplete()) }
                val biometric = remember { BiometricUnlock(this) }
                val useBiometric = remember(appLock.lockCount) { passcodes.isBiometricEnabled() && biometric.isAvailable() }
                // Keeps the app's tab and scroll state while the lock screen covers it.
                val saveableState = rememberSaveableStateHolder()
                if (setupComplete && appLock.isLocked) {
                    LockScreen(
                        appLock,
                        onRequestBiometric = if (useBiometric) {
                            { biometric.prompt(onSuccess = appLock::onBiometricSuccess, onFailed = {}) }
                        } else {
                            null
                        },
                    )
                } else if (!setupComplete) {
                    OnboardingScreen(
                        wizard = onboarding,
                        onSave = { form ->
                            val saver = withContext(Dispatchers.IO) {
                                OnboardingSaver(
                                    LedgerRepository(LedgerDatabaseProvider.get(applicationContext)),
                                    passcodes,
                                    profiles,
                                )
                            }
                            saver.save(form)
                        },
                        onFinished = { setupComplete = true },
                    )
                } else {
                    saveableState.SaveableStateProvider("app") {
                        // Sample data until the screens read the ledger.
                        MoneyMattersApp(
                            SampleDashboard.state,
                            SampleAccounts.state,
                            SampleLedgers.byAccountId,
                            SampleTransactions.state,
                            SampleTransactions.byId,
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        // A rotation or theme change stops and restarts the activity; that isn't leaving the app.
        if (!isChangingConfigurations) appLock.onAppBackgrounded()
    }
}
