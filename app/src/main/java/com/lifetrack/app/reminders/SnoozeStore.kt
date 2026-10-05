package com.lifetrack.app.reminders

import android.content.Context

/**
 * Pending snoozes, written down so a reboot does not eat them. AlarmManager forgets everything
 * on reboot, and a snooze has no row of its own to be rebuilt from like the recurring alarms.
 *
 * Kept in preferences rather than a table: a handful of short-lived entries, each cleared by the
 * snooze firing (or the reminder being deleted). The schedulers' rescheduleAll re-arms them.
 */
object SnoozeStore {
    private const val PREFS = "snoozes"
    private const val REMINDER = "r:"
    private const val ROUTINE = "t:"

    /**
     * A snooze that came due while the phone was off still rings if it is this recent - a
     * restart mid-snooze shouldn't cost the alarm - but an hours-old one is just dropped.
     */
    const val STALE_MS = 10 * 60_000L

    data class RoutineSnooze(val itemId: Long, val at: Long, val date: String)

    fun saveReminder(context: Context, id: Long, at: Long) =
        prefs(context).edit().putString(REMINDER + id, at.toString()).apply()

    fun saveRoutine(context: Context, itemId: Long, at: Long, date: String) =
        prefs(context).edit().putString(ROUTINE + itemId, encode(at, date)).apply()

    fun clearReminder(context: Context, id: Long) =
        prefs(context).edit().remove(REMINDER + id).apply()

    fun clearRoutine(context: Context, itemId: Long) =
        prefs(context).edit().remove(ROUTINE + itemId).apply()

    /** Reminder id to snooze time, with anything stale pruned on the way. */
    fun reminders(context: Context, now: Long = System.currentTimeMillis()): Map<Long, Long> =
        entries(context, REMINDER, now) { id, v -> v.toLongOrNull()?.let { id to it } }.toMap()

    fun routines(context: Context, now: Long = System.currentTimeMillis()): List<RoutineSnooze> =
        entries(context, ROUTINE, now) { id, v -> decode(v)?.let { (at, date) -> RoutineSnooze(id, at, date) } }

    private fun <T> entries(context: Context, prefix: String, now: Long, parse: (Long, String) -> T?): List<T> {
        val prefs = prefs(context)
        val out = mutableListOf<T>()
        val drop = mutableListOf<String>()
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith(prefix)) return@forEach
            val id = key.removePrefix(prefix).toLongOrNull()
            val raw = value as? String
            val at = raw?.substringBefore('|')?.toLongOrNull()
            if (id == null || raw == null || at == null || !stillDue(at, now)) drop += key
            else parse(id, raw)?.let { out += it } ?: run { drop += key }
        }
        if (drop.isNotEmpty()) prefs.edit().apply { drop.forEach { remove(it) } }.apply()
        return out
    }

    /** Worth re-arming: in the future, or missed by less than [STALE_MS]. */
    fun stillDue(at: Long, now: Long): Boolean = at >= now - STALE_MS

    fun encode(at: Long, date: String): String = "$at|$date"

    fun decode(raw: String): Pair<Long, String>? {
        val at = raw.substringBefore('|', "").toLongOrNull() ?: return null
        val date = raw.substringAfter('|', "").takeIf { it.isNotBlank() } ?: return null
        return at to date
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
