package com.linker.app.util

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * RoleManager.ROLE_BROWSER (API 29+) is the direct "make this app the default browser" system
 * prompt — much more discoverable than sending the user into Settings > Apps > Default apps and
 * hoping they find "Browser app" themselves. Below API 29 (down to this app's minSdk 26) that role
 * API doesn't exist, so the fallback opens the general default-apps settings screen instead, where
 * the same choice is one tap further away.
 */
object DefaultBrowserRole {

    fun isHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) && roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
    }

    fun requestIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            }
        }
        return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }
}
