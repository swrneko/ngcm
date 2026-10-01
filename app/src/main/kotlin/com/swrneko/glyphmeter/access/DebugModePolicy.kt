package com.swrneko.glyphmeter.access

/**
 * Nothing OS 4.0 (Android 16, API 36) dropped the developer-key requirement, so debug mode
 * is needed only on system versions strictly below this level.
 */
const val FIRST_API_WITHOUT_DEBUG_MODE = 36

fun requiresDebugMode(sdkInt: Int): Boolean = sdkInt < FIRST_API_WITHOUT_DEBUG_MODE
