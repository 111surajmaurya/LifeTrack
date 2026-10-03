package com.lifetrack.app

import com.lifetrack.app.data.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.sql.Connection
import java.sql.DriverManager

/**
 * Replays each schema upgrade on a real SQLite file shaped like the version it starts from.
 *
 * Two things can go wrong with a Room migration and both are silent until the app launches on
 * a phone that already has data: the SQL can be invalid, and the resulting schema can differ
 * from what Room generates for the entities (Room then refuses to open the database at all).
 * These tests cover the first directly and the second by asserting the exact column list.
 */
class MigrationTest {

    @get:Rule val temp = TemporaryFolder()

    // ------------------------------------------------------------------ helpers

    private fun db(name: String): Connection =
        DriverManager.getConnection("jdbc:sqlite:${temp.newFile(name).absolutePath}")

    private fun Connection.exec(vararg sql: String) =
        createStatement().use { st -> sql.forEach { st.executeUpdate(it) } }

    private fun Connection.run(statements: List<String>) =
        createStatement().use { st -> statements.forEach { st.executeUpdate(it) } }

    private fun Connection.query(sql: String): List<List<Any?>> =
        createStatement().executeQuery(sql).use { rs ->
            val cols = rs.metaData.columnCount
            buildList { while (rs.next()) add((1..cols).map { rs.getObject(it) }) }
        }

    private fun Connection.columns(table: String): List<String> =
        query("PRAGMA table_info(`$table`)").map { it[1] as String }

    private fun Connection.types(table: String): List<String> =
        query("PRAGMA table_info(`$table`)").map { it[2] as String }

    private fun Connection.tables(): List<String> =
        query("SELECT name FROM sqlite_master WHERE type='table'").map { it[0] as String }

    private fun Connection.indices(): List<String> =
        query("SELECT name FROM sqlite_master WHERE type='index'").mapNotNull { it[0] as? String }

    private fun Connection.count(table: String): Long =
        (query("SELECT COUNT(*) FROM `$table`")[0][0] as Number).toLong()

    // ------------------------------------------------------------------ v3 fixtures

    /** The schema as it shipped in v3, plus a little data to carry across. */
    private fun v3(): Connection = db("v3.db").apply {
        exec(
            "CREATE TABLE `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`activityId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "`minutes` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL)",
            "CREATE TABLE `meals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, `name` TEXT NOT NULL, `kcal` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
            "INSERT INTO sessions VALUES (1, 7, '2026-09-08', 45, 1000)",
            "INSERT INTO sessions VALUES (2, 7, '2026-09-09', 1, 2000)",
            "INSERT INTO meals VALUES (1, '2026-09-09', 'Roti', 100, 3000)",
            "INSERT INTO meals VALUES (2, '2026-09-09', 'Daal', 180, 4000)"
        )
    }

    /** The schema as it shipped in v4 (i.e. v3 already migrated), plus the tables v3 lacked. */
    private fun v4(): Connection = db("v4.db").apply {
        exec(
            "CREATE TABLE `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`activityId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "`seconds` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL)",
            "CREATE TABLE `activities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `dailyGoalMin` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
            "CREATE TABLE `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`label` TEXT NOT NULL, `activityId` INTEGER, `hour` INTEGER NOT NULL, " +
                "`minute` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)",
            "CREATE TABLE `settings` (`id` INTEGER PRIMARY KEY NOT NULL, " +
                "`calorieGoal` INTEGER NOT NULL, `runningActivityId` INTEGER, `runningSince` INTEGER, " +
                "`stepGoal` INTEGER NOT NULL)",
            "CREATE TABLE `tracked_apps` (`packageName` TEXT PRIMARY KEY NOT NULL, " +
                "`label` TEXT NOT NULL, `dailyLimitMin` INTEGER NOT NULL)",
            "CREATE TABLE `meals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, `name` TEXT NOT NULL, `kcal` INTEGER NOT NULL, " +
                "`slot` TEXT NOT NULL, `qty` REAL NOT NULL, `portion` TEXT NOT NULL, " +
                "`serving` TEXT NOT NULL, `foodItemId` INTEGER, `createdAt` INTEGER NOT NULL)",
            "INSERT INTO activities VALUES (1, 'Gym', 45, 0)",
            "INSERT INTO activities VALUES (2, 'Reading', 30, 1)",
            "INSERT INTO sessions VALUES (1, 1, '2026-09-09', 2700, 1000)",
            "INSERT INTO sessions VALUES (2, 1, '2026-09-09', 40, 2000)",
            "INSERT INTO reminders VALUES (1, 'Gym', 1, 18, 0, 31, 1)",
            "INSERT INTO settings VALUES (1, 2000, 1, 555, 8000)",
            "INSERT INTO tracked_apps VALUES ('com.android.chrome', 'Chrome', 60)",
            "INSERT INTO meals VALUES (1, '2026-09-09', 'Roti', 100, 'Lunch', 2.0, 'Medium', 'Piece', NULL, 3000)"
        )
    }

    /** The schema as it shipped in v5, with a day of food already logged. */
    private fun v5(): Connection = db("v5.db").apply {
        exec(
            "CREATE TABLE `meals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, `name` TEXT NOT NULL, `kcal` INTEGER NOT NULL, " +
                "`slot` TEXT NOT NULL, `qty` REAL NOT NULL, `portion` TEXT NOT NULL, " +
                "`serving` TEXT NOT NULL, `foodItemId` INTEGER, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE `food_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `kcal` INTEGER NOT NULL, `serving` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, `custom` INTEGER NOT NULL, " +
                "`useCount` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL)",
            "CREATE TABLE `settings` (`id` INTEGER PRIMARY KEY NOT NULL, " +
                "`calorieGoal` INTEGER NOT NULL, `runningHabitId` INTEGER, `runningSince` INTEGER, " +
                "`stepGoal` INTEGER NOT NULL, `activeSlot` TEXT NOT NULL, " +
                "`itemWarnKcal` INTEGER NOT NULL, `breakfastGoal` INTEGER NOT NULL, " +
                "`lunchGoal` INTEGER NOT NULL, `snacksGoal` INTEGER NOT NULL, " +
                "`dinnerGoal` INTEGER NOT NULL, `distanceGoalKm` REAL NOT NULL, " +
                "`burnGoalKcal` INTEGER NOT NULL)",
            "INSERT INTO meals VALUES (1, '2026-09-09', 'Roti', 200, 'Lunch', 2.0, 'Medium', 'Piece', 1, 3000)",
            "INSERT INTO food_items VALUES (1, 'Roti', 100, 'Piece', 'Roti & bread', 0, 4, 900)",
            "INSERT INTO food_items VALUES (2, 'Mom''s kheer', 180, 'Bowl', 'Other', 1, 1, 950)",
            "INSERT INTO settings VALUES (1, 2000, NULL, NULL, 8000, 'Lunch', 300, 500, 700, 200, 600, 5.0, 400)"
        )
    }

    /** The schema as it shipped in v6: protein present, still an integer, no micronutrients. */
    private fun v6(): Connection = db("v6.db").apply {
        exec(
            "CREATE TABLE `meals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, `name` TEXT NOT NULL, `kcal` INTEGER NOT NULL, " +
                "`slot` TEXT NOT NULL, `qty` REAL NOT NULL, `portion` TEXT NOT NULL, " +
                "`serving` TEXT NOT NULL, `foodItemId` INTEGER, `createdAt` INTEGER NOT NULL, " +
                "`protein` INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE `food_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `kcal` INTEGER NOT NULL, `serving` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, `custom` INTEGER NOT NULL, " +
                "`useCount` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, " +
                "`protein` INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE `settings` (`id` INTEGER PRIMARY KEY NOT NULL, " +
                "`calorieGoal` INTEGER NOT NULL, `runningHabitId` INTEGER, `runningSince` INTEGER, " +
                "`stepGoal` INTEGER NOT NULL, `activeSlot` TEXT NOT NULL, " +
                "`itemWarnKcal` INTEGER NOT NULL, `breakfastGoal` INTEGER NOT NULL, " +
                "`lunchGoal` INTEGER NOT NULL, `snacksGoal` INTEGER NOT NULL, " +
                "`dinnerGoal` INTEGER NOT NULL, `distanceGoalKm` REAL NOT NULL, " +
                "`burnGoalKcal` INTEGER NOT NULL, `proteinGoal` INTEGER NOT NULL DEFAULT 60)",
            "INSERT INTO meals VALUES (1, '2026-09-09', 'Roti', 200, 'Lunch', 2.0, 'Medium', 'Piece', 1, 3000, 6)",
            "INSERT INTO meals VALUES (2, '2026-09-09', 'Daal', 180, 'Dinner', 1.0, 'Medium', 'Bowl', 2, 3100, 9)",
            "INSERT INTO food_items VALUES (1, 'Roti', 100, 'Piece', 'Roti & bread', 0, 4, 900, 3)",
            "INSERT INTO food_items VALUES (2, 'Mom''s kheer', 180, 'Bowl', 'Other', 1, 1, 950, 5)",
            "INSERT INTO settings VALUES (1, 2000, 7, 555, 8000, 'Lunch', 300, 500, 700, 200, 600, 5.0, 400, 70)"
        )
    }

    /** The schema as it shipped in v7: REAL protein, micronutrients, fibre goal, seed version. */
    private fun v7(): Connection = db("v7.db").apply {
        exec(
            "CREATE TABLE `settings` (`id` INTEGER PRIMARY KEY NOT NULL, " +
                "`calorieGoal` INTEGER NOT NULL, `runningHabitId` INTEGER, `runningSince` INTEGER, " +
                "`stepGoal` INTEGER NOT NULL, `activeSlot` TEXT NOT NULL, " +
                "`itemWarnKcal` INTEGER NOT NULL, `breakfastGoal` INTEGER NOT NULL, " +
                "`lunchGoal` INTEGER NOT NULL, `snacksGoal` INTEGER NOT NULL, " +
                "`dinnerGoal` INTEGER NOT NULL, `distanceGoalKm` REAL NOT NULL, " +
                "`burnGoalKcal` INTEGER NOT NULL, `proteinGoal` INTEGER NOT NULL DEFAULT 60, " +
                "`fiberGoal` INTEGER NOT NULL DEFAULT 30, `seedVersion` INTEGER NOT NULL DEFAULT 0)",
            "INSERT INTO settings VALUES (1, 2200, 3, 999, 9000, 'Dinner', 350, 550, 770, 220, 660, " +
                "6.0, 450, 75, 35, 2)"
        )
    }

    // ------------------------------------------------------------------ v3 -> v4

    @Test fun `v3 to v4 runs clean and leaves no scratch tables`() {
        v3().use { c ->
            c.run(Migrations.V3_TO_V4)
            val t = c.tables()
            assertTrue("sessions" in t && "meals" in t && "food_items" in t)
            assertFalse("sessions_new" in t || "meals_new" in t)
        }
    }

    @Test fun `v3 to v4 turns minutes into the same number of seconds`() {
        v3().use { c ->
            c.run(Migrations.V3_TO_V4)
            val rows = c.query("SELECT id, activityId, date, seconds FROM sessions ORDER BY id")
            assertEquals(2, rows.size)
            assertEquals(45 * 60L, (rows[0][3] as Number).toLong())
            assertEquals(60L, (rows[1][3] as Number).toLong())
            assertEquals(7L, (rows[0][1] as Number).toLong())
            assertEquals("2026-09-08", rows[0][2])
        }
    }

    @Test fun `v3 to v4 keeps meals and defaults the new columns`() {
        v3().use { c ->
            c.run(Migrations.V3_TO_V4)
            val rows = c.query("SELECT name, kcal, slot, qty, portion, serving, foodItemId FROM meals ORDER BY id")
            assertEquals(2, rows.size)
            assertEquals("Roti", rows[0][0])
            assertEquals("Snacks", rows[0][2])
            assertEquals(1.0, (rows[0][3] as Number).toDouble(), 1e-6)
            assertEquals("Medium", rows[0][4])
            assertNull(rows[0][6])
        }
    }

    // ------------------------------------------------------------------ v4 -> v5

    @Test fun `v4 to v5 runs clean and leaves no scratch tables`() {
        v4().use { c ->
            c.run(Migrations.V4_TO_V5)
            val t = c.tables()
            listOf("habits", "sessions", "habit_checks", "reminders", "settings",
                "tracked_apps", "app_usage_days", "daily_metrics", "meals")
                .forEach { assertTrue("missing $it", it in t) }
            assertFalse("activities" in t)
            listOf("sessions_new", "reminders_new", "settings_new", "tracked_apps_new")
                .forEach { assertFalse("$it left behind", it in t) }
        }
    }

    /** The bug this release fixes: a stopwatch is only honest if it is stored at full precision. */
    @Test fun `v4 to v5 turns seconds into milliseconds`() {
        v4().use { c ->
            c.run(Migrations.V4_TO_V5)
            val rows = c.query("SELECT habitId, millis FROM sessions ORDER BY id")
            assertEquals(2, rows.size)
            assertEquals(1L, (rows[0][0] as Number).toLong())
            assertEquals(2_700_000L, (rows[0][1] as Number).toLong())
            assertEquals(40_000L, (rows[1][1] as Number).toLong())
        }
    }

    @Test fun `v4 to v5 turns activities into seeded timed habits`() {
        v4().use { c ->
            c.run(Migrations.V4_TO_V5)
            val rows = c.query("SELECT id, name, kind, dailyGoalMin, seeded, archived FROM habits ORDER BY id")
            assertEquals(2, rows.size)
            assertEquals("Gym", rows[0][1])
            assertEquals("TIMED", rows[0][2])
            assertEquals(45L, (rows[0][3] as Number).toLong())
            assertEquals(1L, (rows[0][4] as Number).toLong())   // seeded, so it archives not deletes
            assertEquals(0L, (rows[0][5] as Number).toLong())
        }
    }

    @Test fun `v4 to v5 keeps reminders and defaults them to notifications`() {
        v4().use { c ->
            c.run(Migrations.V4_TO_V5)
            val rows = c.query("SELECT label, habitId, hour, minute, daysMask, enabled, mode, ringSeconds, snoozeMinutes, vibrate FROM reminders")
            assertEquals(1, rows.size)
            assertEquals("Gym", rows[0][0])
            assertEquals(1L, (rows[0][1] as Number).toLong())   // activityId became habitId
            assertEquals(18L, (rows[0][2] as Number).toLong())
            assertEquals("NOTIFICATION", rows[0][6])            // existing ones do not start ringing
            assertEquals(20L, (rows[0][7] as Number).toLong())
            assertEquals(5L, (rows[0][8] as Number).toLong())
        }
    }

    @Test fun `v4 to v5 splits the daily calorie goal across the meals`() {
        v4().use { c ->
            c.run(Migrations.V4_TO_V5)
            val r = c.query("SELECT calorieGoal, runningHabitId, runningSince, activeSlot, itemWarnKcal, breakfastGoal, lunchGoal, snacksGoal, dinnerGoal FROM settings")[0]
            assertEquals(2000L, (r[0] as Number).toLong())
            assertEquals(1L, (r[1] as Number).toLong())         // a running timer survives the upgrade
            assertEquals(555L, (r[2] as Number).toLong())
            assertEquals("Snacks", r[3])
            assertEquals(300L, (r[4] as Number).toLong())
            assertEquals(500L, (r[5] as Number).toLong())       // 25%
            assertEquals(700L, (r[6] as Number).toLong())       // 35%
            assertEquals(200L, (r[7] as Number).toLong())       // 10%
            assertEquals(600L, (r[8] as Number).toLong())       // 30%
        }
    }

    @Test fun `v4 to v5 marks existing tracked apps as seeded so they cannot be deleted`() {
        v4().use { c ->
            c.run(Migrations.V4_TO_V5)
            val r = c.query("SELECT packageName, label, dailyLimitMin, seeded FROM tracked_apps")[0]
            assertEquals("com.android.chrome", r[0])
            assertEquals(1L, (r[3] as Number).toLong())
        }
    }

    @Test fun `v4 to v5 leaves meals untouched`() {
        v4().use { c ->
            c.run(Migrations.V4_TO_V5)
            val r = c.query("SELECT name, kcal, slot, qty FROM meals")[0]
            assertEquals("Roti", r[0])
            assertEquals("Lunch", r[2])
            assertEquals(2.0, (r[3] as Number).toDouble(), 1e-6)
        }
    }

    @Test fun `v4 to v5 creates the history tables empty and writable`() {
        v4().use { c ->
            c.run(Migrations.V4_TO_V5)
            assertEquals(0L, c.count("app_usage_days"))
            assertEquals(0L, c.count("daily_metrics"))
            assertEquals(0L, c.count("habit_checks"))
            c.exec(
                "INSERT INTO app_usage_days VALUES ('2026-09-10', 'com.android.chrome', 42)",
                "INSERT INTO daily_metrics VALUES ('2026-09-10', 'STEPS', 8123.0, 'HEALTH_CONNECT', 1)",
                "INSERT INTO habit_checks VALUES (1, '2026-09-10', 5)"
            )
            assertEquals(42L, (c.query("SELECT minutes FROM app_usage_days")[0][0] as Number).toLong())
            assertEquals(8123.0, (c.query("SELECT value FROM daily_metrics")[0][0] as Number).toDouble(), 1e-6)
            assertEquals(5L, (c.query("SELECT count FROM habit_checks")[0][0] as Number).toLong())
        }
    }

    @Test fun `v4 to v5 creates every index Room expects`() {
        v4().use { c ->
            c.run(Migrations.V4_TO_V5)
            val idx = c.indices()
            listOf(
                "index_sessions_habitId", "index_sessions_date", "index_habit_checks_date",
                "index_app_usage_days_date", "index_daily_metrics_date", "index_daily_metrics_metric"
            ).forEach { assertTrue("missing $it", it in idx) }
        }
    }

    // ------------------------------------------------------------------ v5 -> v6

    @Test fun `v5 to v6 adds protein without disturbing what is already logged`() {
        v5().use { c ->
            c.run(Migrations.V5_TO_V6)
            val r = c.query("SELECT name, kcal, slot, qty, protein FROM meals")[0]
            assertEquals("Roti", r[0])
            assertEquals(200L, (r[1] as Number).toLong())
            assertEquals("Lunch", r[2])
            assertEquals(2.0, (r[3] as Number).toDouble(), 1e-6)
            // Nobody can know what was in a meal logged before protein was tracked, so 0 g is
            // the only honest answer - the alternative is inventing grams the user never ate.
            assertEquals(0L, (r[4] as Number).toLong())
        }
    }

    @Test fun `v5 to v6 leaves the catalogue in place for the seeder to top up`() {
        v5().use { c ->
            c.run(Migrations.V5_TO_V6)
            assertEquals(2L, c.count("food_items"))
            val rows = c.query("SELECT name, kcal, custom, protein FROM food_items ORDER BY id")
            assertEquals("Roti", rows[0][0])
            assertEquals(100L, (rows[0][1] as Number).toLong())
            assertEquals(0L, (rows[0][3] as Number).toLong())
            assertEquals("Mom's kheer", rows[1][0])   // a custom food survives untouched
            assertEquals(1L, (rows[1][2] as Number).toLong())
        }
    }

    @Test fun `v5 to v6 gives everyone the default protein goal and keeps the rest`() {
        v5().use { c ->
            c.run(Migrations.V5_TO_V6)
            val r = c.query("SELECT calorieGoal, activeSlot, lunchGoal, burnGoalKcal, proteinGoal FROM settings")[0]
            assertEquals(2000L, (r[0] as Number).toLong())
            assertEquals("Lunch", r[1])
            assertEquals(700L, (r[2] as Number).toLong())
            assertEquals(400L, (r[3] as Number).toLong())
            assertEquals(60L, (r[4] as Number).toLong())
        }
    }

    /** The new columns have to accept writes, not just exist. */
    @Test fun `v6 stores grams once something is logged with them`() {
        v5().use { c ->
            c.run(Migrations.V5_TO_V6)
            c.exec(
                "INSERT INTO meals (`date`, `name`, `kcal`, `slot`, `qty`, `portion`, " +
                    "`serving`, `foodItemId`, `createdAt`, `protein`) " +
                    "VALUES ('2026-09-10', 'Daal', 270, 'Dinner', 1.0, 'Large', 'Bowl', NULL, 4000, 15)",
                "UPDATE food_items SET protein = 3 WHERE name = 'Roti'",
                "UPDATE settings SET proteinGoal = 120 WHERE id = 1"
            )
            assertEquals(15L, (c.query("SELECT protein FROM meals WHERE name = 'Daal'")[0][0] as Number).toLong())
            assertEquals(3L, (c.query("SELECT protein FROM food_items WHERE name = 'Roti'")[0][0] as Number).toLong())
            assertEquals(120L, (c.query("SELECT proteinGoal FROM settings")[0][0] as Number).toLong())
            // The day's total is what the tab and the charts actually read.
            assertEquals(15L, (c.query("SELECT SUM(protein) FROM meals WHERE date = '2026-09-10'")[0][0] as Number).toLong())
        }
    }

    // ------------------------------------------------------------------ v6 -> v7

    @Test fun `v6 to v7 runs clean and leaves no scratch tables`() {
        v6().use { c ->
            c.run(Migrations.V6_TO_V7)
            val t = c.tables()
            assertTrue("meals" in t && "food_items" in t && "settings" in t)
            assertFalse("meals_new" in t || "food_items_new" in t)
            assertTrue("index_meals_date" in c.indices())
            assertTrue("index_food_items_name" in c.indices())
        }
    }

    /** The table is rebuilt to retype protein, so this is the one that could lose your history. */
    @Test fun `v6 to v7 keeps every logged meal through the rebuild`() {
        v6().use { c ->
            c.run(Migrations.V6_TO_V7)
            assertEquals(2L, c.count("meals"))
            val rows = c.query(
                "SELECT id, date, name, kcal, slot, qty, portion, serving, foodItemId, " +
                    "createdAt, protein, fiber, vitA FROM meals ORDER BY id")
            assertEquals(1L, (rows[0][0] as Number).toLong())
            assertEquals("Roti", rows[0][2])
            assertEquals(200L, (rows[0][3] as Number).toLong())
            assertEquals("Lunch", rows[0][4])
            assertEquals(2.0, (rows[0][5] as Number).toDouble(), 1e-6)
            assertEquals(1L, (rows[0][8] as Number).toLong())      // foodItemId survives
            assertEquals(3000L, (rows[0][9] as Number).toLong())
            // 6 g of protein is now 6.0 g, not 0 and not 60
            assertEquals(6.0, (rows[0][10] as Number).toDouble(), 1e-6)
            assertEquals(9.0, (rows[1][10] as Number).toDouble(), 1e-6)
            // nothing knows what fibre was in a meal logged before v7
            assertEquals(0.0, (rows[0][11] as Number).toDouble(), 1e-6)
            assertEquals(0.0, (rows[0][12] as Number).toDouble(), 1e-6)
        }
    }

    @Test fun `v6 to v7 keeps the catalogue, custom foods included`() {
        v6().use { c ->
            c.run(Migrations.V6_TO_V7)
            assertEquals(2L, c.count("food_items"))
            val rows = c.query("SELECT name, kcal, custom, useCount, lastUsedAt, protein, fiber FROM food_items ORDER BY id")
            assertEquals("Roti", rows[0][0])
            assertEquals(4L, (rows[0][3] as Number).toLong())      // use counts survive
            assertEquals(900L, (rows[0][4] as Number).toLong())
            assertEquals(3.0, (rows[0][5] as Number).toDouble(), 1e-6)
            assertEquals("Mom's kheer", rows[1][0])
            assertEquals(1L, (rows[1][2] as Number).toLong())      // still marked custom
            assertEquals(0.0, (rows[1][6] as Number).toDouble(), 1e-6)
        }
    }

    @Test fun `v6 to v7 adds the fibre goal and the seed version without disturbing settings`() {
        v6().use { c ->
            c.run(Migrations.V6_TO_V7)
            val r = c.query(
                "SELECT calorieGoal, runningHabitId, runningSince, activeSlot, proteinGoal, " +
                    "fiberGoal, seedVersion FROM settings")[0]
            assertEquals(2000L, (r[0] as Number).toLong())
            assertEquals(7L, (r[1] as Number).toLong())            // a running timer survives
            assertEquals(555L, (r[2] as Number).toLong())
            assertEquals("Lunch", r[3])
            assertEquals(70L, (r[4] as Number).toLong())           // an edited protein goal survives
            assertEquals(30L, (r[5] as Number).toLong())
            // 0, so the first launch after upgrading refreshes the catalogue
            assertEquals(0L, (r[6] as Number).toLong())
        }
    }

    /** Protein is REAL now, so a roti's 4.5 g has to survive a round trip as 4.5. */
    @Test fun `v7 stores fractional grams rather than rounding them away`() {
        v6().use { c ->
            c.run(Migrations.V6_TO_V7)
            c.exec(
                "INSERT INTO meals (`date`, `name`, `kcal`, `slot`, `qty`, `portion`, `serving`, " +
                    "`foodItemId`, `createdAt`, `protein`, `fiber`, `vitA`, `vitC`, `iron`, `calcium`) " +
                    "VALUES ('2026-09-10', 'Roti', 119, 'Lunch', 1.0, 'Medium', 'Piece', 1, 4000, " +
                    "4.5, 2.0, 0.0, 0.0, 1.2, 37.2)",
                "UPDATE food_items SET protein = 4.5, fiber = 2.0, calcium = 37.2 WHERE name = 'Roti'"
            )
            val m = c.query("SELECT protein, fiber, iron, calcium FROM meals WHERE date = '2026-09-10'")[0]
            assertEquals(4.5, (m[0] as Number).toDouble(), 1e-6)
            assertEquals(2.0, (m[1] as Number).toDouble(), 1e-6)
            assertEquals(1.2, (m[2] as Number).toDouble(), 1e-6)
            assertEquals(37.2, (m[3] as Number).toDouble(), 1e-6)
            val f = c.query("SELECT protein, fiber FROM food_items WHERE name = 'Roti'")[0]
            assertEquals(4.5, (f[0] as Number).toDouble(), 1e-6)
        }
    }

    // ------------------------------------------------------------------ v7 -> v8

    @Test fun `v7 to v8 adds the body profile without disturbing any goal`() {
        v7().use { c ->
            c.run(Migrations.V7_TO_V8)
            val r = c.query(
                "SELECT calorieGoal, runningHabitId, runningSince, activeSlot, proteinGoal, " +
                    "fiberGoal, seedVersion, heightCm, weightKg, age, sex, activityLevel FROM settings")[0]
            assertEquals(2200L, (r[0] as Number).toLong())
            assertEquals(3L, (r[1] as Number).toLong())        // a running timer survives
            assertEquals(999L, (r[2] as Number).toLong())
            assertEquals("Dinner", r[3])
            assertEquals(75L, (r[4] as Number).toLong())       // edited goals survive
            assertEquals(35L, (r[5] as Number).toLong())
            assertEquals(2L, (r[6] as Number).toLong())        // and the catalogue is not re-seeded
            // the profile starts empty, which is what makes it optional
            assertEquals(0.0, (r[7] as Number).toDouble(), 1e-6)
            assertEquals(0.0, (r[8] as Number).toDouble(), 1e-6)
            assertEquals(0L, (r[9] as Number).toLong())
            assertEquals("Unspecified", r[10])
            assertEquals("Moderate", r[11])
        }
    }

    @Test fun `v8 stores a body profile once one is entered`() {
        v7().use { c ->
            c.run(Migrations.V7_TO_V8)
            c.exec(
                "UPDATE settings SET heightCm = 178.5, weightKg = 74.2, age = 31, " +
                    "sex = 'Male', activityLevel = 'Active' WHERE id = 1"
            )
            val r = c.query("SELECT heightCm, weightKg, age, sex, activityLevel FROM settings")[0]
            assertEquals(178.5, (r[0] as Number).toDouble(), 1e-6)
            assertEquals(74.2, (r[1] as Number).toDouble(), 1e-6)
            assertEquals(31L, (r[2] as Number).toLong())
            assertEquals("Male", r[3])
            assertEquals("Active", r[4])
        }
    }

    @Test fun `v8 settings have exactly the columns the entity declares`() {
        v7().use { c ->
            c.run(Migrations.V7_TO_V8)
            assertEquals(
                listOf("id", "calorieGoal", "runningHabitId", "runningSince", "stepGoal", "activeSlot",
                    "itemWarnKcal", "breakfastGoal", "lunchGoal", "snacksGoal", "dinnerGoal",
                    "distanceGoalKm", "burnGoalKcal", "proteinGoal", "fiberGoal", "seedVersion",
                    "heightCm", "weightKg", "age", "sex", "activityLevel"),
                c.columns("settings")
            )
        }
    }

    // ------------------------------------------------------------------ v8 -> v9

    @Test fun `v8 to v9 turns automatic goals on without touching anything else`() {
        v7().use { c ->
            c.run(Migrations.V7_TO_V8)
            c.run(Migrations.V8_TO_V9)
            val r = c.query("SELECT calorieGoal, proteinGoal, fiberGoal, autoGoals FROM settings")[0]
            assertEquals(2200L, (r[0] as Number).toLong())
            assertEquals(75L, (r[1] as Number).toLong())
            assertEquals(35L, (r[2] as Number).toLong())
            // On by default is safe: it only does anything once a profile exists, and v8 left
            // every profile empty.
            assertEquals(1L, (r[3] as Number).toLong())
        }
    }

    @Test fun `v9 settings have exactly the columns the entity declares`() {
        v7().use { c ->
            c.run(Migrations.V7_TO_V8)
            c.run(Migrations.V8_TO_V9)
            assertEquals(
                listOf("id", "calorieGoal", "runningHabitId", "runningSince", "stepGoal", "activeSlot",
                    "itemWarnKcal", "breakfastGoal", "lunchGoal", "snacksGoal", "dinnerGoal",
                    "distanceGoalKm", "burnGoalKcal", "proteinGoal", "fiberGoal", "seedVersion",
                    "heightCm", "weightKg", "age", "sex", "activityLevel", "autoGoals"),
                c.columns("settings")
            )
        }
    }

    // ------------------------------------------------------------------ v9 -> v10

    private fun v9(): Connection = v7().apply {
        run(Migrations.V7_TO_V8)
        run(Migrations.V8_TO_V9)
    }

    @Test fun `v9 to v10 adds the routine and hourly step tables, empty`() {
        v9().use { c ->
            val settingsBefore = c.query("SELECT * FROM settings")
            c.run(Migrations.V9_TO_V10)
            listOf("routine_items", "routine_plans", "routine_logs", "hourly_steps").forEach {
                assertTrue("$it missing", it in c.tables())
                assertEquals(0L, c.count(it))
            }
            assertEquals(settingsBefore, c.query("SELECT * FROM settings"))
            assertTrue("index_routine_items_key" in c.indices())
        }
    }

    /** Room compares these to the entities on open; a mismatch is a crash on launch. */
    @Test fun `v10 tables have exactly the columns the entities declare`() {
        v9().use { c ->
            c.run(Migrations.V9_TO_V10)
            assertEquals(
                listOf("id", "key", "title", "emoji", "mode", "hour", "minute", "enabled", "kind", "slot", "sortOrder"),
                c.columns("routine_items")
            )
            assertEquals(listOf("date", "itemId", "hour", "minute", "enabled"), c.columns("routine_plans"))
            assertEquals(listOf("date", "itemId", "status", "at"), c.columns("routine_logs"))
            assertEquals(listOf("date", "hour", "steps", "source"), c.columns("hourly_steps"))
            assertEquals(listOf("TEXT", "INTEGER", "REAL", "TEXT"), c.types("hourly_steps"))
            // Settings is not touched by this migration.
            assertEquals(22, c.columns("settings").size)
        }
    }

    @Test fun `v10 keys a routine item by its seed key and a log by day and item`() {
        v9().use { c ->
            c.run(Migrations.V9_TO_V10)
            c.exec(
                "INSERT INTO routine_items (key, title, emoji, mode, hour, minute, enabled, kind, slot, sortOrder) " +
                    "VALUES ('wake', 'Wake up', 'x', 'ALARM', 7, 0, 1, 'WAKE', '', 0)",
                "INSERT OR IGNORE INTO routine_items (key, title, emoji, mode, hour, minute, enabled, kind, slot, sortOrder) " +
                    "VALUES ('wake', 'Again', 'x', 'ALARM', 8, 0, 1, 'WAKE', '', 0)",
                "INSERT OR REPLACE INTO routine_logs VALUES ('2026-10-03', 1, 'MISSED', 1)",
                "INSERT OR REPLACE INTO routine_logs VALUES ('2026-10-03', 1, 'DONE', 2)"
            )
            assertEquals(1L, c.count("routine_items"))
            assertEquals("Wake up", c.query("SELECT title FROM routine_items")[0][0])
            assertEquals(listOf(listOf<Any?>("DONE")), c.query("SELECT status FROM routine_logs"))
        }
    }

    // ------------------------------------------------------------------ end to end

    /** Someone upgrading from v3 skips straight past v4, so the two must chain. */
    @Test fun `v3 chains through v4 to v5 without losing anything`() {
        v3().use { c ->
            c.run(Migrations.V3_TO_V4)
            // v4 added tables that a v3 database has never seen; Room's own v4 schema has them,
            // so stand them up here exactly as the v4 fixture does before the second hop.
            c.exec(
                "CREATE TABLE `activities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `dailyGoalMin` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
                "CREATE TABLE `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`label` TEXT NOT NULL, `activityId` INTEGER, `hour` INTEGER NOT NULL, " +
                    "`minute` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)",
                "CREATE TABLE `settings` (`id` INTEGER PRIMARY KEY NOT NULL, " +
                    "`calorieGoal` INTEGER NOT NULL, `runningActivityId` INTEGER, `runningSince` INTEGER, " +
                    "`stepGoal` INTEGER NOT NULL)",
                "CREATE TABLE `tracked_apps` (`packageName` TEXT PRIMARY KEY NOT NULL, " +
                    "`label` TEXT NOT NULL, `dailyLimitMin` INTEGER NOT NULL)",
                "INSERT INTO activities VALUES (7, 'Gym', 45, 0)",
                "INSERT INTO settings VALUES (1, 2000, NULL, NULL, 8000)"
            )
            c.run(Migrations.V4_TO_V5)

            // 45 minutes in v3 -> 2700 seconds in v4 -> 2,700,000 ms in v5.
            val millis = c.query("SELECT millis FROM sessions ORDER BY id").map { (it[0] as Number).toLong() }
            assertEquals(listOf(2_700_000L, 60_000L), millis)
            assertEquals(2L, c.count("meals"))
            assertEquals("Gym", c.query("SELECT name FROM habits")[0][0])

            // ...and straight on through v6 and v7, which is where anyone on an old build
            // lands today. Four hops, one launch, and the two meals from v3 are still there.
            c.run(Migrations.V5_TO_V6)
            c.run(Migrations.V6_TO_V7)
            c.run(Migrations.V7_TO_V8)
            c.run(Migrations.V8_TO_V9)
            c.run(Migrations.V9_TO_V10)
            assertEquals(2L, c.count("meals"))
            assertEquals(0.0, (c.query("SELECT SUM(protein) FROM meals")[0][0] as Number).toDouble(), 1e-6)
            assertEquals(60L, (c.query("SELECT proteinGoal FROM settings")[0][0] as Number).toLong())
            assertEquals(30L, (c.query("SELECT fiberGoal FROM settings")[0][0] as Number).toLong())
            assertEquals("Unspecified", c.query("SELECT sex FROM settings")[0][0])
            assertEquals(1L, (c.query("SELECT autoGoals FROM settings")[0][0] as Number).toLong())
            assertEquals("Gym", c.query("SELECT name FROM habits")[0][0])
            assertEquals(listOf(2_700_000L, 60_000L),
                c.query("SELECT millis FROM sessions ORDER BY id").map { (it[0] as Number).toLong() })
        }
    }

    /** Guards the column lists Room validates against; a typo here is a crash on launch. */
    @Test fun `v5 tables have exactly the columns the entities declare`() {
        v4().use { c ->
            c.run(Migrations.V4_TO_V5)
            assertEquals(
                listOf("id", "name", "kind", "dailyGoalMin", "dailyTarget", "emoji", "accent", "seeded", "archived", "sortOrder"),
                c.columns("habits")
            )
            assertEquals(listOf("id", "habitId", "date", "millis", "endedAt"), c.columns("sessions"))
            assertEquals(listOf("habitId", "date", "count"), c.columns("habit_checks"))
            assertEquals(
                listOf("id", "label", "habitId", "hour", "minute", "daysMask", "enabled", "mode", "ringSeconds", "snoozeMinutes", "vibrate"),
                c.columns("reminders")
            )
            assertEquals(
                listOf("id", "calorieGoal", "runningHabitId", "runningSince", "stepGoal", "activeSlot",
                    "itemWarnKcal", "breakfastGoal", "lunchGoal", "snacksGoal", "dinnerGoal",
                    "distanceGoalKm", "burnGoalKcal"),
                c.columns("settings")
            )
            assertEquals(listOf("packageName", "label", "dailyLimitMin", "seeded"), c.columns("tracked_apps"))
            assertEquals(listOf("date", "packageName", "minutes"), c.columns("app_usage_days"))
            assertEquals(listOf("date", "metric", "value", "source", "updatedAt"), c.columns("daily_metrics"))
        }
    }

    /** The v7 column lists, which are what Room compares against the entities on open. */
    @Test fun `v7 tables have exactly the columns the entities declare`() {
        v6().use { c ->
            c.run(Migrations.V6_TO_V7)
            assertEquals(
                listOf("id", "date", "name", "kcal", "slot", "qty", "portion", "serving",
                    "foodItemId", "createdAt", "protein", "fiber", "vitA", "vitC", "iron", "calcium"),
                c.columns("meals")
            )
            assertEquals(
                listOf("id", "name", "kcal", "serving", "category", "custom", "useCount",
                    "lastUsedAt", "protein", "fiber", "vitA", "vitC", "iron", "calcium"),
                c.columns("food_items")
            )
            assertEquals(
                listOf("id", "calorieGoal", "runningHabitId", "runningSince", "stepGoal", "activeSlot",
                    "itemWarnKcal", "breakfastGoal", "lunchGoal", "snacksGoal", "dinnerGoal",
                    "distanceGoalKm", "burnGoalKcal", "proteinGoal", "fiberGoal", "seedVersion"),
                c.columns("settings")
            )
        }
    }

    /**
     * The column *types*, not just the names. Room compares both when it opens the database,
     * and a REAL where it wants an INTEGER is the same crash as a missing column. These lists
     * are copied from what Room generates for the v7 entities.
     */
    @Test fun `v7 column types match what Room generates`() {
        v6().use { c ->
            c.run(Migrations.V6_TO_V7)
            assertEquals(
                listOf("INTEGER", "TEXT", "TEXT", "INTEGER", "TEXT", "REAL", "TEXT", "TEXT",
                    "INTEGER", "INTEGER", "REAL", "REAL", "REAL", "REAL", "REAL", "REAL"),
                c.types("meals")
            )
            assertEquals(
                listOf("INTEGER", "TEXT", "INTEGER", "TEXT", "TEXT", "INTEGER", "INTEGER",
                    "INTEGER", "REAL", "REAL", "REAL", "REAL", "REAL", "REAL"),
                c.types("food_items")
            )
        }
    }

    /** Widening a column means the type has to actually change, not just the values. */
    @Test fun `v7 retypes protein from INTEGER to REAL`() {
        v6().use { c ->
            val before = c.query("PRAGMA table_info(`meals`)").first { it[1] == "protein" }[2]
            assertEquals("INTEGER", before)
            c.run(Migrations.V6_TO_V7)
            listOf("meals", "food_items").forEach { table ->
                val after = c.query("PRAGMA table_info(`$table`)").first { it[1] == "protein" }[2]
                assertEquals("$table.protein", "REAL", after)
            }
        }
    }

    /** Same guard for v6: an ALTER appends, so protein must be declared last on the entity too. */
    @Test fun `v6 tables have exactly the columns the entities declare`() {
        v5().use { c ->
            c.run(Migrations.V5_TO_V6)
            assertEquals(
                listOf("id", "date", "name", "kcal", "slot", "qty", "portion", "serving",
                    "foodItemId", "createdAt", "protein"),
                c.columns("meals")
            )
            assertEquals(
                listOf("id", "name", "kcal", "serving", "category", "custom", "useCount",
                    "lastUsedAt", "protein"),
                c.columns("food_items")
            )
            assertEquals(
                listOf("id", "calorieGoal", "runningHabitId", "runningSince", "stepGoal", "activeSlot",
                    "itemWarnKcal", "breakfastGoal", "lunchGoal", "snacksGoal", "dinnerGoal",
                    "distanceGoalKm", "burnGoalKcal", "proteinGoal"),
                c.columns("settings")
            )
        }
    }
}
