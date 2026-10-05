package com.lifetrack.app

import com.lifetrack.app.reminders.SnoozeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What survives a reboot mid-snooze, and how a routine snooze is written down. */
class SnoozeStoreTest {
    private val now = 1_800_000_000_000L

    @Test fun `a snooze still ahead is re-armed`() {
        assertTrue(SnoozeStore.stillDue(now + 60_000, now))
    }

    @Test fun `one that came due during a quick restart still rings`() {
        assertTrue(SnoozeStore.stillDue(now - 60_000, now))
        assertTrue(SnoozeStore.stillDue(now - SnoozeStore.STALE_MS, now))
    }

    @Test fun `an hours-old one is dropped`() {
        assertFalse(SnoozeStore.stillDue(now - SnoozeStore.STALE_MS - 1, now))
        assertFalse(SnoozeStore.stillDue(now - 3 * 3_600_000L, now))
    }

    @Test fun `a routine snooze round-trips its time and day`() {
        assertEquals(now to "2026-10-05", SnoozeStore.decode(SnoozeStore.encode(now, "2026-10-05")))
    }

    @Test fun `garbage decodes to nothing`() {
        assertNull(SnoozeStore.decode(""))
        assertNull(SnoozeStore.decode("abc|2026-10-05"))
        assertNull(SnoozeStore.decode("123"))
        assertNull(SnoozeStore.decode("123|"))
    }
}
