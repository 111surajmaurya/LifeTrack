package com.lifetrack.app.screentime

/**
 * When [LimitWatchService] next needs to look, so it only wakes when a limit could matter.
 *
 * Every tracked app only adds time while it is in front, and only one app is in front at a
 * time. So if the app with the least time left has R ms to go, nothing can reach its limit for
 * at least R ms - whatever is opened, switched between or closed in the meantime - and it is
 * safe to sleep that long. Only once some app is already over does that stop being true:
 * opening it again has to be noticed quickly, so it falls back to [OVER_LIMIT_MS].
 */
object LimitSchedule {
    /** Looking more often than this buys nothing; it is also the overshoot at a limit. */
    const val MIN_MS = 2_000L

    /** With an app over its limit, how quickly reopening it is caught. */
    const val OVER_LIMIT_MS = 5_000L

    /**
     * Never sleep longer than this, whatever the numbers say: a safety net for midnight, a
     * clock change, or usage the OS reports late.
     */
    const val MAX_MS = 15 * 60_000L

    /** Milliseconds left per tracked app with a limit: limit minus what was used today. */
    fun remaining(limitsMin: Map<String, Int>, usedMs: Map<String, Long>): Map<String, Long> =
        limitsMin.mapValues { (pkg, min) -> min * 60_000L - (usedMs[pkg] ?: 0L) }

    /** How long to wait before the next look, given [remaining] from the last read. */
    fun nextDelay(remaining: Collection<Long>): Long = when {
        remaining.isEmpty() -> MAX_MS
        remaining.any { it <= 0L } -> OVER_LIMIT_MS
        else -> remaining.min().coerceIn(MIN_MS, MAX_MS)
    }
}
