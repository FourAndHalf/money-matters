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
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.moneymatters.app.feature.accounts.SampleAccounts
import com.moneymatters.app.feature.accounts.SampleLedgers
import com.moneymatters.app.feature.dashboard.SampleDashboard
import com.moneymatters.app.feature.transactions.SampleTransactions
import com.moneymatters.app.ui.MoneyMattersApp
import com.moneymatters.app.ui.theme.MoneyMattersTheme

private const val SPLASH_EXIT_DURATION_MS = 420L
private const val SPLASH_EXIT_SCALE = 4f

class MainActivity : ComponentActivity() {
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
                // Sample data until the ledger exists.
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
