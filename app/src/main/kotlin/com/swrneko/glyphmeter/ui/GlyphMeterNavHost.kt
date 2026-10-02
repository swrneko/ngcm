package com.swrneko.glyphmeter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.swrneko.glyphmeter.access.GlyphAccessState
import com.swrneko.glyphmeter.ui.animation.AnimationSettingsViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val ANIMATION = "animation/{${AnimationSettingsViewModel.PRESET_ID}}"

    fun animation(presetId: String): String = "animation/$presetId"

    /** A phone whose Glyph access already works goes straight to the main screen. */
    fun startFor(access: GlyphAccessState): String = when (access) {
        GlyphAccessState.WORKING, GlyphAccessState.MANAGED_BY_APP -> MAIN
        else -> ONBOARDING
    }
}

/**
 * Navigation between the screens. Screens come in as slots so the graph can be
 * exercised without the dependency injection graph.
 *
 * While [access] is still being checked nothing is navigated, so the first screen is the
 * right one and never flashes the wrong one.
 */
@Composable
fun GlyphMeterNavHost(
    access: GlyphAccessState,
    onboarding: @Composable (onContinue: () -> Unit) -> Unit,
    main: @Composable (onOpenSettings: () -> Unit, onOpenOnboarding: () -> Unit) -> Unit,
    settings: @Composable (onBack: () -> Unit, onOpenAnimation: (presetId: String) -> Unit) -> Unit,
    animation: @Composable (presetId: String, onBack: () -> Unit) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    if (access == GlyphAccessState.CHECKING) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.testTag("nav_checking"))
        }
        return
    }

    NavHost(navController = navController, startDestination = Routes.startFor(access)) {
        composable(Routes.ONBOARDING) {
            onboarding {
                // Continuing means setup is finished: start the main screen from a clean stack.
                navController.navigate(Routes.MAIN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
        composable(Routes.MAIN) {
            main(
                { navController.navigate(Routes.SETTINGS) },
                { navController.navigate(Routes.ONBOARDING) },
            )
        }
        composable(Routes.SETTINGS) {
            settings({ navController.popBackStack() }, { navController.navigate(Routes.animation(it)) })
        }
        composable(Routes.ANIMATION) { entry ->
            val presetId = entry.arguments?.getString(AnimationSettingsViewModel.PRESET_ID).orEmpty()
            animation(presetId) { navController.popBackStack() }
        }
    }
}
