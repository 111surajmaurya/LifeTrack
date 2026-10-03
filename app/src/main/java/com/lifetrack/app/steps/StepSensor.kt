package com.lifetrack.app.steps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Fallback when Health Connect isn't available or permitted.
 * TYPE_STEP_COUNTER reports steps since boot and delivers its current value as soon as we subscribe,
 * so one read is enough. Needs ACTIVITY_RECOGNITION on Android 10+.
 */
class StepSensor(context: Context) {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val isAvailable get() = sensor != null

    /** Cumulative count since boot, or null if the sensor didn't answer within 3 s. */
    suspend fun readCumulative(): Long? {
        val s = sensor ?: return null
        return withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(e.values[0].toLong())
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }
    }
}
