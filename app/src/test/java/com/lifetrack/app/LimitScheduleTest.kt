package com.lifetrack.app

import com.lifetrack.app.screentime.LimitSchedule
import org.junit.Assert.assertEquals
import org.junit.Test

/** When the limit watcher next has to look - the whole point being: not every few seconds. */
class LimitScheduleTest {

    private val min = 60_000L

    @Test fun `sleeps until the soonest limit could run out`() {
        val left = LimitSchedule.remaining(
            mapOf("yt" to 30, "ig" to 30),
            mapOf("yt" to 10 * min, "ig" to 22 * min)
        )
        assertEquals(mapOf("yt" to 20 * min, "ig" to 8 * min), left)
        assertEquals(8 * min, LimitSchedule.nextDelay(left.values))
    }

    @Test fun `an untouched app counts its whole limit`() {
        assertEquals(mapOf("yt" to 30 * min), LimitSchedule.remaining(mapOf("yt" to 30), emptyMap()))
    }

    @Test fun `an app over its limit means looking every few seconds`() {
        assertEquals(LimitSchedule.OVER_LIMIT_MS, LimitSchedule.nextDelay(listOf(20 * min, 0L)))
        assertEquals(LimitSchedule.OVER_LIMIT_MS, LimitSchedule.nextDelay(listOf(-5_000L)))
    }

    @Test fun `never sleeps past the safety cap or below the floor`() {
        assertEquals(LimitSchedule.MAX_MS, LimitSchedule.nextDelay(listOf(60 * min)))
        assertEquals(LimitSchedule.MIN_MS, LimitSchedule.nextDelay(listOf(300L)))
        assertEquals(LimitSchedule.MAX_MS, LimitSchedule.nextDelay(emptyList()))
    }
}
