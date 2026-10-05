package com.lifetrack.app.reminders

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The one bit of state [AlarmService] and [AlarmActivity] share: which reminder is ringing.
 *
 * The activity is launched by a full-screen intent, so it has no binding to the service. It
 * watches this instead and closes itself the moment it goes null - which covers the alarm being
 * dismissed from the shade, ringing itself out, or the service being killed. When a second
 * alarm takes over, the label and snooze come along so the open screen can switch to it.
 */
object AlarmState {
    data class Ringing(val id: Long, val label: String, val snoozeMinutes: Int)

    val ringing = MutableStateFlow<Ringing?>(null)
}
