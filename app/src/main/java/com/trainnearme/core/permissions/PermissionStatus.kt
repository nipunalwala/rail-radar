package com.trainnearme.core.permissions

/** Which of the permissions the app depends on are currently granted. */
data class PermissionStatus(
    val notifications: Boolean,
    val foregroundLocation: Boolean,
    val backgroundLocation: Boolean,
) {
    val all: Boolean get() = notifications && foregroundLocation && backgroundLocation
}

interface PermissionChecker {
    fun current(): PermissionStatus
}

enum class AlertStatus {
    /** Automatic alerts will fire. */
    ON,

    /** Switched off in settings. */
    OFF,
    NEEDS_LOCATION,
    NEEDS_BACKGROUND_LOCATION,
    NEEDS_NOTIFICATIONS,
}

/** Whether automatic alerts can work, and if not, the first thing that is missing. */
fun alertStatus(alertsEnabled: Boolean, permissions: PermissionStatus): AlertStatus = when {
    !alertsEnabled -> AlertStatus.OFF
    !permissions.foregroundLocation -> AlertStatus.NEEDS_LOCATION
    !permissions.backgroundLocation -> AlertStatus.NEEDS_BACKGROUND_LOCATION
    !permissions.notifications -> AlertStatus.NEEDS_NOTIFICATIONS
    else -> AlertStatus.ON
}
