package com.lifetrack.app.reminders

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The one bit of state [AlarmService] and [AlarmActivity] share: which reminder is ringing.
 *
 * The activity is launched by a full-screen intent, so it has no binding to the service. It
 * watches this instead and closes itself the moment the id stops matching - which covers the
 * alarm being dismissed from the shade, ringing itself out, or the service being killed.
 */
object AlarmState {
    val ringing = MutableStateFlow<Long?>(null)
}
