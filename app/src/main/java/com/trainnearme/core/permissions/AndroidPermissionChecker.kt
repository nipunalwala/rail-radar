package com.trainnearme.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionChecker {

    override fun current(): PermissionStatus {
        val foreground = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        return PermissionStatus(
            // Before Android 13 notifications need no runtime permission.
            notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                granted(Manifest.permission.POST_NOTIFICATIONS),
            foregroundLocation = foreground,
            // Before Android 10 there is no separate background permission.
            backgroundLocation = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                foreground
            } else {
                granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            },
        )
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
