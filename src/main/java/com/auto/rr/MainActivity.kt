package com.auto.rr

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auto.rr.data.ApiKeyStore
import com.auto.rr.ui.settings.ApiKeySetupScreen
import androidx.compose.ui.Modifier
import com.auto.rr.ui.chart.ChartScreen
import com.auto.rr.ui.theme.AutoRrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) holdSplash() // not again on rotation
        setContent {
            AutoRrTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val key by ApiKeyStore.key.collectAsStateWithLifecycle()
                    if (key.isNullOrBlank()) ApiKeySetupScreen() else ChartScreen()
                }
            }
        }
    }

    /**
     * The splash (system splash on Android 12+, window background before that) is dismissed the
     * moment the first frame is drawn. Compose draws that frame almost instantly, so the splash
     * flashed by unseen. Holding the first draw for a short minimum keeps it on screen long
     * enough to register without delaying anything real.
     */
    private fun holdSplash() {
        val start = SystemClock.uptimeMillis()
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (SystemClock.uptimeMillis() - start < SPLASH_MIN_MS) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        // A pre-draw veto only re-runs when something invalidates the view, so nudge it.
        content.postDelayed({ content.invalidate() }, SPLASH_MIN_MS)
    }

    private companion object {
        const val SPLASH_MIN_MS = 900L
    }
}
