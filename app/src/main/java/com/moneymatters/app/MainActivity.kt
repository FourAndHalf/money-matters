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
import androidx.compose.runtime.setValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.moneymatters.app.core.ledger.LedgerDatabaseProvider
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.profile.ProfileStore
import com.moneymatters.app.core.security.PasscodeStore
import com.moneymatters.app.feature.accounts.SampleAccounts
import com.moneymatters.app.feature.accounts.SampleLedgers
import com.moneymatters.app.feature.dashboard.SampleDashboard
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
                val profiles = remember { ProfileStore.encrypted(applicationContext) }
                var setupComplete by remember { mutableStateOf(profiles.isSetupComplete()) }
                if (!setupComplete) {
                    OnboardingScreen(
                        wizard = onboarding,
                        onSave = { form ->
                            val saver = withContext(Dispatchers.IO) {
                                OnboardingSaver(
                                    LedgerRepository(LedgerDatabaseProvider.get(applicationContext)),
                                    PasscodeStore.encrypted(applicationContext),
                                    profiles,
                                )
                            }
                            saver.save(form)
                        },
                        onFinished = { setupComplete = true },
                    )
                } else {
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
