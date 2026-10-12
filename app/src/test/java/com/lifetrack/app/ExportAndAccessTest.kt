package com.lifetrack.app

import com.lifetrack.app.data.Habit
import com.lifetrack.app.data.HabitDays
import com.lifetrack.app.data.HabitKind
import com.lifetrack.app.data.Meal
import com.lifetrack.app.data.Metric
import com.lifetrack.app.data.Retention
import com.lifetrack.app.data.Slot
import com.lifetrack.app.export.CsvWriter
import com.lifetrack.app.export.ExportDoc
import com.lifetrack.app.export.ExportFormat
import com.lifetrack.app.export.ExportKind
import com.lifetrack.app.export.ExportRange
import com.lifetrack.app.export.ExportTable
import com.lifetrack.app.export.ExportTables
import com.lifetrack.app.export.XlsxWriter
import com.lifetrack.app.ui.settings.Access
import com.lifetrack.app.ui.settings.AccessState
import com.lifetrack.app.ui.settings.Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Missed habit days, features that lock without access, and the shape of every download. */
class ExportAndAccessTest {

    // ------------------------------------------------------------------ missed habit days

    @Test fun `a day is only missed once it is over`() {
        val today = "2026-10-12"
        assertTrue(HabitDays.missed("2026-10-11", met = false, firstDay = "2026-10-01", today = today))
        assertFalse(HabitDays.missed(today, met = false, firstDay = "2026-10-01", today = today))
        assertFalse(HabitDays.missed("2026-10-11", met = true, firstDay = "2026-10-01", today = today))
    }

    @Test fun `days before a habit existed are never missed`() {
        val added = Habit(name = "Read", createdOn = "2026-10-10")
        val first = HabitDays.firstDay(added, trackingStart = "2026-09-01")
        assertEquals("2026-10-10", first)
        assertFalse(HabitDays.missed("2026-10-09", met = false, firstDay = first, today = "2026-10-12"))
        assertTrue(HabitDays.missed("2026-10-10", met = false, firstDay = first, today = "2026-10-12"))
    }

    @Test fun `starter habits count from the tracking start`() {
        val seeded = Habit(name = "Gym", seeded = true)   // createdOn = ""
        assertEquals("2026-09-01", HabitDays.firstDay(seeded, trackingStart = "2026-09-01"))
    }

    // ------------------------------------------------------------------ features

    private fun states(vararg on: Access): Map<Access, AccessState> =
        Access.entries.associateWith { if (it in on) AccessState.ON else AccessState.OFF }

    @Test fun `nothing is locked before access has been read`() {
        assertNull(Feature.ScreenTime.missing(emptyMap()))
        assertFalse(Feature.ScreenTime.locked(emptyMap()))
    }

    @Test fun `screen time needs usage access and nothing else`() {
        assertTrue(Feature.ScreenTime.locked(states()))
        assertFalse(Feature.ScreenTime.locked(states(Access.UsageAccess)))
        assertEquals("Usage access", Feature.ScreenTime.needs(Feature.ScreenTime.missing(states())!!))
    }

    @Test fun `alarms need notifications and exact alarms, not the optional extras`() {
        assertEquals(
            listOf(Access.ExactAlarms),
            Feature.Alarms.missing(states(Access.Notifications, Access.FullScreen, Access.Battery))
        )
        assertFalse(Feature.Alarms.locked(states(Access.Notifications, Access.ExactAlarms)))
        assertEquals("Notifications and Exact alarms", Feature.Alarms.needs(listOf(Access.Notifications, Access.ExactAlarms)))
    }

    @Test fun `steps work from either source`() {
        assertTrue(Feature.Steps.locked(states()))
        assertFalse(Feature.Steps.locked(states(Access.PhysicalActivity)))
        assertFalse(Feature.Steps.locked(states(Access.HealthConnect)))
        // Health Connect missing from the phone doesn't matter once the sensor is on.
        val noHc = states(Access.PhysicalActivity) + (Access.HealthConnect to AccessState.UNAVAILABLE)
        assertFalse(Feature.Steps.locked(noHc))
        assertEquals("Health Connect or Physical activity", Feature.Steps.needs(Feature.Steps.missing(states())!!))
    }

    @Test fun `optional access is needed by no feature`() {
        assertTrue(Feature.needing(Access.FullScreen).isEmpty())
        assertTrue(Feature.needing(Access.Battery).isEmpty())
        assertTrue(Feature.needing(Access.HealthBackground).isEmpty())
        assertEquals(listOf(Feature.ScreenTime, Feature.LimitBlocking), Feature.needing(Access.UsageAccess))
    }

    @Test fun `every access explains itself`() {
        Access.entries.forEach {
            assertTrue("${it.name} why", it.why.length > 40)
            assertTrue("${it.name} without", it.without.isNotBlank())
        }
    }

    // ------------------------------------------------------------------ retention & ranges

    @Test fun `data is kept for twelve months and the longest download covers all of it`() {
        assertEquals(365L, Retention.DAYS)
        assertEquals(Retention.DAYS.toInt(), ExportRange.Year.days)
        assertEquals("2026-10-06", ExportRange.Week.from("2026-10-12"))
        assertEquals("2025-10-12", Retention.cutoff("2026-10-12"))
    }

    @Test fun `file names say what, when and which format`() {
        assertEquals(
            "lifetrack-meals-2026-10-06-to-2026-10-12.xlsx",
            ExportTables.fileName(ExportKind.Meals, ExportRange.Week, ExportFormat.Excel, "2026-10-12")
        )
        assertEquals(ExportFormat.Pdf, ExportFormat.forFileName("lifetrack-steps-x.pdf"))
    }

    // ------------------------------------------------------------------ tables

    @Test fun `meals export each item and a total per day by meal`() {
        val meals = listOf(
            Meal(date = "2026-10-11", name = "Roti", kcal = 240, slot = Slot.Lunch.name, qty = 2f, protein = 9f, fiber = 4.5f),
            Meal(date = "2026-10-11", name = "Poha", kcal = 250, slot = Slot.Breakfast.name, protein = 5f),
            Meal(date = "2026-10-12", name = "Dal", kcal = 180, slot = Slot.Dinner.name, protein = 10f)
        )
        val (items, days) = ExportTables.meals(meals)
        assertEquals(3, items.rows.size)
        assertEquals("Breakfast", items.rows[0][1])   // breakfast sorts before lunch on the same day
        assertEquals(12, items.headers.size)
        assertEquals(listOf<Any>("2026-10-11", 250, 240, 0, 0, 490, 14.0, 4.5, 2), days.rows[0])
        assertEquals(180, days.rows[1][5])
    }

    @Test fun `habits export done, missed and today's day in progress`() {
        val gym = Habit(id = 1, name = "Gym", kind = HabitKind.TIMED.name, dailyGoalMin = 30, createdOn = "2026-10-10")
        val water = Habit(id = 2, name = "Water", kind = HabitKind.COUNT.name, dailyTarget = 8)
        val values = mapOf(
            1L to mapOf("2026-10-10" to 31 * 60_000.0, "2026-10-12" to 5 * 60_000.0),
            2L to mapOf("2026-10-11" to 8.0)
        )
        val (daily, summary) = ExportTables.habits(
            listOf(gym, water), values, from = "2026-10-09", to = "2026-10-12",
            trackingStart = "2026-10-01", today = "2026-10-12"
        )
        val gymRows = daily.rows.filter { it[1] == "Gym" }
        // Nothing before the day Gym was added.
        assertEquals(listOf("2026-10-10", "2026-10-11", "2026-10-12"), gymRows.map { it[0] })
        assertEquals(listOf("Done", "Missed", "In progress"), gymRows.map { it[5] })
        assertEquals(31.0, gymRows[0][3])
        val waterRows = daily.rows.filter { it[1] == "Water" }
        assertEquals(listOf("Missed", "Missed", "Done", "In progress"), waterRows.map { it[5] })

        val gymSummary = summary.rows.first { it[0] == "Gym" }
        assertEquals(listOf<Any>("Gym", "30 min a day", 1, 1, 50L, 36.0, "min"), gymSummary)
    }

    @Test fun `steps export one line per recorded day and the period's totals`() {
        val metrics = mapOf(
            Metric.Steps to mapOf("2026-10-10" to 8000.0, "2026-10-11" to 12000.4, "2026-09-01" to 5.0),
            Metric.Distance to mapOf("2026-10-11" to 9123.0)
        )
        val (days, summary) = ExportTables.steps(metrics, "2026-10-06", "2026-10-12")
        assertEquals(listOf("2026-10-10", "2026-10-11"), days.rows.map { it[0] })   // out-of-range day dropped
        assertEquals(listOf<Any>("2026-10-11", 12000L, 9.12, 0L, 0L), days.rows[1])
        assertEquals(20000L, summary.rows.first { it[0] == "Total steps" }[1])
        assertEquals("2026-10-11 (12000 steps)", summary.rows.first { it[0] == "Best day" }[1])
    }

    // ------------------------------------------------------------------ writers

    private val doc = ExportDoc(
        "LifeTrack - Meals", "Last 7 days",
        listOf(
            ExportTable("Meals", listOf("Food", "Calories"), listOf(listOf<Any>("Dal, \"tadka\"", 180), listOf<Any>("Roti & ghee <2>", 4.5))),
            ExportTable("Daily totals", listOf("Date", "Total"), listOf(listOf<Any>("2026-10-12", 184.5)))
        )
    )

    @Test fun `csv quotes what needs quoting and starts with a byte-order mark`() {
        assertEquals("plain", CsvWriter.escape("plain"))
        assertEquals("\"a,b\"", CsvWriter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""))

        val out = ByteArrayOutputStream().also { CsvWriter.write(doc, it) }.toString(Charsets.UTF_8.name())
        assertTrue(out.startsWith("﻿LifeTrack - Meals\r\n"))
        assertTrue(out.contains("\"Dal, \"\"tadka\"\"\",180\r\n"))
        assertTrue(out.contains("Roti & ghee <2>,4.5\r\n"))
    }

    @Test fun `xlsx column letters and sheet names follow Excel's rules`() {
        assertEquals(listOf("A", "Z", "AA", "AZ", "BA"), listOf(0, 25, 26, 51, 52).map(XlsxWriter::column))
        assertEquals(
            listOf("Meals", "meals (2)", "a b"),
            XlsxWriter.sheetNames(listOf("Meals", "meals", "a/b"))
        )
        assertEquals(31, XlsxWriter.sheetNames(listOf("x".repeat(40))).single().length)
    }

    @Test fun `xlsx is a zip of well-formed parts with numbers kept as numbers`() {
        val bytes = ByteArrayOutputStream().also { XlsxWriter.write(doc, it) }.toByteArray()
        val parts = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { parts[it.name] = zip.readBytes() }
        }
        assertEquals(
            setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
                "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml"),
            parts.keys
        )
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        parts.values.forEach { parser.parse(ByteArrayInputStream(it)) }   // throws if malformed

        val sheet1 = parts.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
        assertTrue(sheet1.contains("""<c r="B2"><v>180</v></c>"""))
        assertTrue(sheet1.contains("Roti &amp; ghee &lt;2&gt;"))
        assertTrue(sheet1.contains("""<c r="A1" t="inlineStr" s="1">"""))
        val workbook = parts.getValue("xl/workbook.xml").toString(Charsets.UTF_8)
        assertTrue(workbook.contains("""name="Daily totals""""))
    }
}
