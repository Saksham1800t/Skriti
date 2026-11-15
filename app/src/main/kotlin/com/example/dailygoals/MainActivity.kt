package com.example.dailygoals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.dailygoals.ui.AppRoot
import com.example.dailygoals.ui.theme.SkritiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener { splash ->
            splash.view.animate()
                .alpha(0f)
                .setDuration(SPLASH_FADE_MS)
                .withEndAction(splash::remove)
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        intent?.getStringExtra(EXTRA_OPEN_REVIEW)?.let { pendingReview.value = it }
        setContent {
            SkritiTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingReview.value = intent.getStringExtra(EXTRA_OPEN_REVIEW)
    }

    companion object {
        const val EXTRA_OPEN_REVIEW = "open_review"
        private const val SPLASH_FADE_MS = 200L

        /** The week of a review the user asked to open from the notification, until the app has shown it. */
        val pendingReview = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    }
}
