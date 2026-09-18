package com.swrneko.glyphmeter.access

import javax.inject.Inject

/**
 * Fallback for users with no computer to hand.
 *
 * Shizuku is started once over wireless debugging from the phone itself, but it has to
 * be restarted after every reboot, which is why the adb grant is the primary route.
 *
 * dev.rikka.shizuku:api 13.1.5 (the version pinned by this project) does not expose a
 * public way to run a shell command through the Shizuku binder: `Shizuku.newProcess` is
 * `private static` and `ShizukuRemoteProcess` has no public constructor, confirmed by
 * inspecting the published aar. Reaching for either through reflection would be exactly
 * the kind of silent, version-fragile workaround this writer must not invent. Until the
 * library ships a public API for it (or this app switches to a different mechanism, e.g.
 * a bundled Shizuku user service), this writer stays permanently unavailable and the app
 * relies solely on [SecureSettingsDebugModeWriter] plus the one-time adb grant.
 */
class ShizukuDebugModeWriter @Inject constructor() : DebugModeWriter {

    override val isAvailable: Boolean = false

    override fun isDebugModeOn(): Boolean = false

    override fun enableDebugMode(): Boolean = false
}
