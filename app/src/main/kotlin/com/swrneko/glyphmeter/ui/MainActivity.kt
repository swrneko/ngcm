package com.swrneko.glyphmeter.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.swrneko.glyphmeter.ui.onboarding.OnboardingRoute
import com.swrneko.glyphmeter.ui.theme.GlyphMeterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlyphMeterTheme {
                // The settings screen does not exist yet, so there is nowhere to continue to.
                OnboardingRoute(onContinue = {})
            }
        }
    }
}
