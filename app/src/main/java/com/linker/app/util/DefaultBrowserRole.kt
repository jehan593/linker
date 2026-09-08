package com.linker.app.util

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * On API 29+ uses the system "make this the default browser" prompt — much friendlier than
 * pointing the user into Settings. Below that (down to minSdk 26) it opens the general
 * default-apps settings screen instead.
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
