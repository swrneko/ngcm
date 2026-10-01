package com.swrneko.glyphmeter.access

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Without `rikka.shizuku.ShizukuProvider` in the merged manifest Shizuku never hands the app its
 * binder, `Shizuku.pingBinder()` stays false and the Shizuku route silently cannot work.
 * Checked against the manifest Robolectric loads, which is the merged one of the app.
 */
@RunWith(RobolectricTestRunner::class)
class ShizukuProviderManifestTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun the_shizuku_provider_is_declared_the_way_shizuku_expects() {
        val info = context.packageManager.resolveContentProvider("${context.packageName}.shizuku", 0)

        assertNotNull("ShizukuProvider is missing from the manifest", info)
        assertEquals("rikka.shizuku.ShizukuProvider", info!!.name)
        assertTrue("Shizuku reaches the provider from its own process", info.exported)
        assertTrue(info.enabled)
        assertEquals(false, info.multiprocess)
        assertEquals("android.permission.INTERACT_ACROSS_USERS_FULL", info.readPermission)
        assertEquals("android.permission.INTERACT_ACROSS_USERS_FULL", info.writePermission)
    }

    @Test
    fun the_app_asks_for_the_shizuku_api_permission() {
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()

        assertTrue(requested.contains("moe.shizuku.manager.permission.API_V23"))
    }
}
