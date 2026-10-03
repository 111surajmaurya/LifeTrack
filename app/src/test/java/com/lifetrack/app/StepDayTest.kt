package com.lifetrack.app

import com.lifetrack.app.data.StepDay
import org.junit.Assert.assertEquals
import org.junit.Test

class StepDayTest {
    @Test fun `steps are latest minus baseline`() {
        assertEquals(1500L, StepDay("2026-09-09", baseline = 10_000, latest = 11_500).steps)
    }

    @Test fun `after a reboot accumulated steps are kept`() {
        // Walked 1500, phone rebooted, sensor restarted at 0 and reached 300
        val d = StepDay("2026-09-09", baseline = 0, latest = 300, accumulated = 1500)
        assertEquals(1800L, d.steps)
    }

    @Test fun `negative differences never go below zero`() {
        assertEquals(0L, StepDay("2026-09-09", baseline = 500, latest = 100).steps)
    }
}
