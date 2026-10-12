package com.lifetrack.app.export

import android.content.Context
import android.net.Uri
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.HabitKind
import com.lifetrack.app.data.Metric
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.TrackingStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Builds a download from what is stored and writes it to [Uri] the user picked in Android's
 * "Save as" screen. Nothing leaves the phone unless the user puts it somewhere that syncs.
 */
object Exporter {

    /** What happened, in words the Settings screen can show as they are. */
    sealed interface Result {
        data class Saved(val rows: Int) : Result
        data object Empty : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun build(context: Context, repo: Repository, kind: ExportKind, range: ExportRange): ExportDoc {
        val today = Dates.today()
        val from = range.from(today)
        val tables = when (kind) {
            ExportKind.Meals -> ExportTables.meals(repo.mealsBetween(from, today).first())

            ExportKind.Habits -> {
                // Archived habits are included when they have something in the window.
                val habits = repo.allHabits.first()
                val values = habits.associate { habit ->
                    habit.id to when (habit.kindType) {
                        HabitKind.TIMED -> repo.habitMillisPerDay(habit.id, from, today).first()
                            .associate { it.date to it.value }
                        HabitKind.COUNT -> repo.checksBetween(habit.id, from, today).first()
                            .associate { it.date to it.count.toDouble() }
                    }
                }
                val shown = habits.filter { !it.archived || values[it.id].orEmpty().values.any { v -> v > 0 } }
                ExportTables.habits(shown, values, from, today, TrackingStart.date(context), today)
            }

            ExportKind.Steps -> {
                val metrics = listOf(Metric.Steps, Metric.Distance, Metric.Burn, Metric.ActiveMinutes)
                    .associateWith { m -> repo.metricSeries(m, from, today).first().associate { it.date to it.value } }
                ExportTables.steps(metrics, from, today)
            }
        }
        return ExportDoc("LifeTrack - ${kind.label}", ExportTables.subtitle(range, today), tables)
    }

    suspend fun export(
        context: Context,
        repo: Repository,
        kind: ExportKind,
        range: ExportRange,
        format: ExportFormat,
        target: Uri
    ): Result = withContext(Dispatchers.IO) {
        try {
            val doc = build(context, repo, kind, range)
            if (doc.empty) {
                // The picker already made an empty file; don't leave it behind.
                runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, target) }
                return@withContext Result.Empty
            }
            val out = context.contentResolver.openOutputStream(target, "wt")
                ?: return@withContext Result.Failed("That location can't be written to.")
            out.use {
                when (format) {
                    ExportFormat.Csv -> CsvWriter.write(doc, it)
                    ExportFormat.Excel -> XlsxWriter.write(doc, it)
                    ExportFormat.Pdf -> PdfWriter.write(doc, it)
                }
            }
            Result.Saved(doc.tables.first().rows.size)
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
