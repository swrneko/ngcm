package com.swrneko.glyphmeter.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swrneko.glyphmeter.ui.animation.AnimationSettingsRoute
import com.swrneko.glyphmeter.ui.main.MainRoute
import com.swrneko.glyphmeter.ui.onboarding.OnboardingRoute
import com.swrneko.glyphmeter.ui.settings.SettingsRoute
import com.swrneko.glyphmeter.ui.theme.GlyphMeterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlyphMeterTheme {
                val entry: AppEntryViewModel = hiltViewModel()
                val access by entry.access.collectAsStateWithLifecycle()
                GlyphMeterNavHost(
                    access = access,
                    onboarding = { onContinue -> OnboardingRoute(onContinue = onContinue) },
                    main = { onOpenSettings, onOpenOnboarding ->
                        MainRoute(onOpenSettings = onOpenSettings, onOpenOnboarding = onOpenOnboarding)
                    },
                    settings = { onBack, onOpenAnimation -> SettingsRoute(onBack = onBack, onOpenAnimation = onOpenAnimation) },
                    // The animation's id reaches its view model through the navigation arguments.
                    animation = { _, onBack -> AnimationSettingsRoute(onBack = onBack) },
                )
            }
        }
    }
}
