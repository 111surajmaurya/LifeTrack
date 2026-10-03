package com.lifetrack.app.data

/**
 * How long LifeTrack keeps each kind of data point.
 *
 * Everything is stored on the phone, in one SQLite file, and nothing is uploaded anywhere.
 * A day of tracking is a few kilobytes, so six months of everything is comfortably under
 * a megabyte - the limit exists to stop the charts and the database growing without end,
 * not to save space.
 *
 * **Tuning this:** change [DAYS] and the next launch prunes anything older on a background
 * thread. Raising it does not bring back what has already been pruned, so raise it before
 * you need the history, not after. See the "Data retention" section of the README.
 */
object Retention {

    /** Six months. Long enough to see a season change in the monthly charts. */
    const val DAYS: Long = 180

    /** Oldest date worth keeping, as an ISO yyyy-MM-dd string. */
    fun cutoff(today: String = Dates.today()): String = Dates.shift(today, -DAYS)

    /** What a prune actually removed, so the caller can log or show it. */
    data class Pruned(
        val meals: Int = 0,
        val sessions: Int = 0,
        val checks: Int = 0,
        val appUsage: Int = 0,
        val metrics: Int = 0,
        val stepDays: Int = 0,
        val routine: Int = 0,
        val hourlySteps: Int = 0
    ) {
        val total get() = meals + sessions + checks + appUsage + metrics + stepDays + routine + hourlySteps
        val any get() = total > 0
    }
}
