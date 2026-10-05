package com.lifetrack.app.data

/**
 * Schema upgrades as plain SQL, kept apart from Room so unit tests can replay the exact
 * same statements against a real SQLite file and check that nothing logged gets lost.
 *
 * The DDL here must match, character for character, what Room generates for the entities;
 * Room compares the live schema on open and refuses to start if they differ.
 * `MigrationTest` guards both halves of that.
 */
object Migrations {

    /**
     * v3 -> v4:
     *  - `sessions.minutes` becomes `sessions.seconds` (old rows multiply by 60),
     *  - `meals` gains slot / quantity / portion / serving / foodItemId,
     *  - `food_items`, the searchable catalogue, is created empty for ensureSeeded() to fill.
     */
    val V3_TO_V4: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `sessions_new` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`activityId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
            "`seconds` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL)",
        "INSERT INTO `sessions_new` (`id`, `activityId`, `date`, `seconds`, `endedAt`) " +
            "SELECT `id`, `activityId`, `date`, `minutes` * 60, `endedAt` FROM `sessions`",
        "DROP TABLE `sessions`",
        "ALTER TABLE `sessions_new` RENAME TO `sessions`",
        "CREATE INDEX IF NOT EXISTS `index_sessions_activityId` ON `sessions` (`activityId`)",
        "CREATE INDEX IF NOT EXISTS `index_sessions_date` ON `sessions` (`date`)",

        "CREATE TABLE IF NOT EXISTS `meals_new` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`date` TEXT NOT NULL, `name` TEXT NOT NULL, `kcal` INTEGER NOT NULL, " +
            "`slot` TEXT NOT NULL, `qty` REAL NOT NULL, `portion` TEXT NOT NULL, " +
            "`serving` TEXT NOT NULL, `foodItemId` INTEGER, `createdAt` INTEGER NOT NULL)",
        "INSERT INTO `meals_new` " +
            "(`id`, `date`, `name`, `kcal`, `slot`, `qty`, `portion`, `serving`, `foodItemId`, `createdAt`) " +
            "SELECT `id`, `date`, `name`, `kcal`, 'Snacks', 1.0, 'Medium', 'Serve', NULL, `createdAt` FROM `meals`",
        "DROP TABLE `meals`",
        "ALTER TABLE `meals_new` RENAME TO `meals`",
        "CREATE INDEX IF NOT EXISTS `index_meals_date` ON `meals` (`date`)",

        "CREATE TABLE IF NOT EXISTS `food_items` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `kcal` INTEGER NOT NULL, `serving` TEXT NOT NULL, " +
            "`category` TEXT NOT NULL, `custom` INTEGER NOT NULL, " +
            "`useCount` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_food_items_name` ON `food_items` (`name`)"
    )

    /**
     * v4 -> v5, the "habit tracker" release:
     *  - `activities` becomes `habits`, gaining a kind (timed or counted), an emoji, an accent
     *    and a `seeded` flag so the starter habits can be archived but never deleted,
     *  - `sessions.seconds` becomes `sessions.millis` and `activityId` becomes `habitId`,
     *  - `habit_checks` records counted habits day by day, which is what the calendar reads,
     *  - reminders can now ring as alarms, so they carry mode / ringSeconds / snooze / vibrate,
     *  - settings gain the active meal slot, per-meal calorie targets and the item warning limit,
     *  - `app_usage_days` and `daily_metrics` give screen time and activity real history to chart.
     */
    val V4_TO_V5: List<String> = listOf(
        // ---- habits ----
        "CREATE TABLE IF NOT EXISTS `habits` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `kind` TEXT NOT NULL, `dailyGoalMin` INTEGER NOT NULL, " +
            "`dailyTarget` INTEGER NOT NULL, `emoji` TEXT NOT NULL, `accent` TEXT NOT NULL, " +
            "`seeded` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
        "INSERT INTO `habits` " +
            "(`id`, `name`, `kind`, `dailyGoalMin`, `dailyTarget`, `emoji`, `accent`, `seeded`, `archived`, `sortOrder`) " +
            "SELECT `id`, `name`, 'TIMED', `dailyGoalMin`, 1, '⏱', 'leaf', 1, 0, `sortOrder` FROM `activities`",
        "DROP TABLE `activities`",

        // ---- sessions: seconds -> millis, activityId -> habitId ----
        "CREATE TABLE IF NOT EXISTS `sessions_new` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`habitId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
            "`millis` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL)",
        "INSERT INTO `sessions_new` (`id`, `habitId`, `date`, `millis`, `endedAt`) " +
            "SELECT `id`, `activityId`, `date`, `seconds` * 1000, `endedAt` FROM `sessions`",
        "DROP TABLE `sessions`",
        "ALTER TABLE `sessions_new` RENAME TO `sessions`",
        "CREATE INDEX IF NOT EXISTS `index_sessions_habitId` ON `sessions` (`habitId`)",
        "CREATE INDEX IF NOT EXISTS `index_sessions_date` ON `sessions` (`date`)",

        // ---- counted habits ----
        "CREATE TABLE IF NOT EXISTS `habit_checks` (" +
            "`habitId` INTEGER NOT NULL, `date` TEXT NOT NULL, `count` INTEGER NOT NULL, " +
            "PRIMARY KEY(`habitId`, `date`))",
        "CREATE INDEX IF NOT EXISTS `index_habit_checks_date` ON `habit_checks` (`date`)",

        // ---- reminders can ring ----
        "CREATE TABLE IF NOT EXISTS `reminders_new` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`label` TEXT NOT NULL, `habitId` INTEGER, `hour` INTEGER NOT NULL, " +
            "`minute` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, " +
            "`mode` TEXT NOT NULL, `ringSeconds` INTEGER NOT NULL, `snoozeMinutes` INTEGER NOT NULL, " +
            "`vibrate` INTEGER NOT NULL)",
        "INSERT INTO `reminders_new` " +
            "(`id`, `label`, `habitId`, `hour`, `minute`, `daysMask`, `enabled`, `mode`, `ringSeconds`, `snoozeMinutes`, `vibrate`) " +
            "SELECT `id`, `label`, `activityId`, `hour`, `minute`, `daysMask`, `enabled`, " +
            "'NOTIFICATION', 20, 5, 1 FROM `reminders`",
        "DROP TABLE `reminders`",
        "ALTER TABLE `reminders_new` RENAME TO `reminders`",

        // ---- settings ----
        "CREATE TABLE IF NOT EXISTS `settings_new` (" +
            "`id` INTEGER PRIMARY KEY NOT NULL, `calorieGoal` INTEGER NOT NULL, " +
            "`runningHabitId` INTEGER, `runningSince` INTEGER, `stepGoal` INTEGER NOT NULL, " +
            "`activeSlot` TEXT NOT NULL, `itemWarnKcal` INTEGER NOT NULL, " +
            "`breakfastGoal` INTEGER NOT NULL, `lunchGoal` INTEGER NOT NULL, " +
            "`snacksGoal` INTEGER NOT NULL, `dinnerGoal` INTEGER NOT NULL, " +
            "`distanceGoalKm` REAL NOT NULL, `burnGoalKcal` INTEGER NOT NULL)",
        "INSERT INTO `settings_new` " +
            "(`id`, `calorieGoal`, `runningHabitId`, `runningSince`, `stepGoal`, `activeSlot`, " +
            "`itemWarnKcal`, `breakfastGoal`, `lunchGoal`, `snacksGoal`, `dinnerGoal`, " +
            "`distanceGoalKm`, `burnGoalKcal`) " +
            "SELECT `id`, `calorieGoal`, `runningActivityId`, `runningSince`, `stepGoal`, 'Snacks', " +
            "300, CAST(`calorieGoal` * 0.25 AS INTEGER), CAST(`calorieGoal` * 0.35 AS INTEGER), " +
            "CAST(`calorieGoal` * 0.10 AS INTEGER), CAST(`calorieGoal` * 0.30 AS INTEGER), " +
            "5.0, 400 FROM `settings`",
        "DROP TABLE `settings`",
        "ALTER TABLE `settings_new` RENAME TO `settings`",

        // ---- tracked apps: seeded ones can't be deleted ----
        "CREATE TABLE IF NOT EXISTS `tracked_apps_new` (" +
            "`packageName` TEXT PRIMARY KEY NOT NULL, `label` TEXT NOT NULL, " +
            "`dailyLimitMin` INTEGER NOT NULL, `seeded` INTEGER NOT NULL)",
        "INSERT INTO `tracked_apps_new` (`packageName`, `label`, `dailyLimitMin`, `seeded`) " +
            "SELECT `packageName`, `label`, `dailyLimitMin`, 1 FROM `tracked_apps`",
        "DROP TABLE `tracked_apps`",
        "ALTER TABLE `tracked_apps_new` RENAME TO `tracked_apps`",

        // ---- history tables ----
        "CREATE TABLE IF NOT EXISTS `app_usage_days` (" +
            "`date` TEXT NOT NULL, `packageName` TEXT NOT NULL, `minutes` INTEGER NOT NULL, " +
            "PRIMARY KEY(`date`, `packageName`))",
        "CREATE INDEX IF NOT EXISTS `index_app_usage_days_date` ON `app_usage_days` (`date`)",

        "CREATE TABLE IF NOT EXISTS `daily_metrics` (" +
            "`date` TEXT NOT NULL, `metric` TEXT NOT NULL, `value` REAL NOT NULL, " +
            "`source` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`, `metric`))",
        "CREATE INDEX IF NOT EXISTS `index_daily_metrics_date` ON `daily_metrics` (`date`)",
        "CREATE INDEX IF NOT EXISTS `index_daily_metrics_metric` ON `daily_metrics` (`metric`)"
    )

    /**
     * v5 -> v6, protein:
     *  - `food_items.protein` and `meals.protein` carry grams alongside the calories,
     *  - `settings.proteinGoal` is the daily target.
     *
     * Three plain ALTERs, so nothing is copied and nothing can be lost. Existing rows land on
     * 0 g: `meals` stays honest (we cannot know what was in a meal logged before protein
     * existed), while `food_items` is topped up from [FoodSeed] by name on the next launch -
     * see `Repository.ensureSeeded`. The DEFAULTs are declared on the entities too
     * (`@ColumnInfo(defaultValue = ...)`), so a fresh install and an upgraded database end up
     * with byte-identical DDL rather than merely compatible DDL.
     */
    val V5_TO_V6: List<String> = listOf(
        "ALTER TABLE `food_items` ADD COLUMN `protein` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `meals` ADD COLUMN `protein` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `settings` ADD COLUMN `proteinGoal` INTEGER NOT NULL DEFAULT 60"
    )

    /**
     * v6 -> v7, "measured instead of guessed":
     *  - `food_items` and `meals` gain fibre, vitamin A, vitamin C, iron and calcium,
     *  - `protein` widens from INTEGER to REAL, because the catalogue now comes from USDA
     *    FoodData Central and a roti really is 4.5 g, not 4 or 5,
     *  - `settings` gains a fibre goal and the seed version that drives catalogue refreshes.
     *
     * Widening a column means rebuilding the table - SQLite cannot retype one in place - so
     * these two are copied rather than ALTERed. Every existing row survives: an old integer
     * protein becomes the same number with a decimal point, and the micronutrients start at 0
     * because a meal logged before v7 has no record of what was in it. New columns on
     * `settings` are plain ALTERs, so a running timer and every goal survive untouched.
     */
    val V6_TO_V7: List<String> = listOf(
        // ---- food_items: retype protein, add the micronutrients ----
        "CREATE TABLE IF NOT EXISTS `food_items_new` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `kcal` INTEGER NOT NULL, `serving` TEXT NOT NULL, " +
            "`category` TEXT NOT NULL, `custom` INTEGER NOT NULL, " +
            "`useCount` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, " +
            "`protein` REAL NOT NULL, `fiber` REAL NOT NULL, `vitA` REAL NOT NULL, " +
            "`vitC` REAL NOT NULL, `iron` REAL NOT NULL, `calcium` REAL NOT NULL)",
        "INSERT INTO `food_items_new` " +
            "(`id`, `name`, `kcal`, `serving`, `category`, `custom`, `useCount`, `lastUsedAt`, " +
            "`protein`, `fiber`, `vitA`, `vitC`, `iron`, `calcium`) " +
            "SELECT `id`, `name`, `kcal`, `serving`, `category`, `custom`, `useCount`, " +
            "`lastUsedAt`, CAST(`protein` AS REAL), 0.0, 0.0, 0.0, 0.0, 0.0 FROM `food_items`",
        "DROP TABLE `food_items`",
        "ALTER TABLE `food_items_new` RENAME TO `food_items`",
        "CREATE INDEX IF NOT EXISTS `index_food_items_name` ON `food_items` (`name`)",

        // ---- meals: the same, and history keeps the numbers it was logged with ----
        "CREATE TABLE IF NOT EXISTS `meals_new` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`date` TEXT NOT NULL, `name` TEXT NOT NULL, `kcal` INTEGER NOT NULL, " +
            "`slot` TEXT NOT NULL, `qty` REAL NOT NULL, `portion` TEXT NOT NULL, " +
            "`serving` TEXT NOT NULL, `foodItemId` INTEGER, `createdAt` INTEGER NOT NULL, " +
            "`protein` REAL NOT NULL, `fiber` REAL NOT NULL, `vitA` REAL NOT NULL, " +
            "`vitC` REAL NOT NULL, `iron` REAL NOT NULL, `calcium` REAL NOT NULL)",
        "INSERT INTO `meals_new` " +
            "(`id`, `date`, `name`, `kcal`, `slot`, `qty`, `portion`, `serving`, `foodItemId`, " +
            "`createdAt`, `protein`, `fiber`, `vitA`, `vitC`, `iron`, `calcium`) " +
            "SELECT `id`, `date`, `name`, `kcal`, `slot`, `qty`, `portion`, `serving`, " +
            "`foodItemId`, `createdAt`, CAST(`protein` AS REAL), 0.0, 0.0, 0.0, 0.0, 0.0 FROM `meals`",
        "DROP TABLE `meals`",
        "ALTER TABLE `meals_new` RENAME TO `meals`",
        "CREATE INDEX IF NOT EXISTS `index_meals_date` ON `meals` (`date`)",

        // ---- settings ----
        "ALTER TABLE `settings` ADD COLUMN `fiberGoal` INTEGER NOT NULL DEFAULT 30",
        "ALTER TABLE `settings` ADD COLUMN `seedVersion` INTEGER NOT NULL DEFAULT 0"
    )

    /**
     * v7 -> v8, the body profile: height, weight, age, sex and activity level, so the calorie
     * and protein goals can be calculated instead of guessed at.
     *
     * All five are optional and default to "not answered" - 0 for the numbers, Unspecified for
     * sex, Moderate for activity. Nothing in the app requires them; leaving them empty simply
     * means the goals stay whatever you typed. Five plain ALTERs, so no table is rebuilt.
     */
    val V7_TO_V8: List<String> = listOf(
        "ALTER TABLE `settings` ADD COLUMN `heightCm` REAL NOT NULL DEFAULT 0",
        "ALTER TABLE `settings` ADD COLUMN `weightKg` REAL NOT NULL DEFAULT 0",
        "ALTER TABLE `settings` ADD COLUMN `age` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `settings` ADD COLUMN `sex` TEXT NOT NULL DEFAULT 'Unspecified'",
        "ALTER TABLE `settings` ADD COLUMN `activityLevel` TEXT NOT NULL DEFAULT 'Moderate'"
    )

    /**
     * v8 -> v9: `autoGoals`, the switch that keeps the calorie, protein and fibre goals in step
     * with the body profile.
     *
     * Defaults to 1 (on) so that filling in a profile is enough to get calculated goals. That
     * is safe for existing installs precisely because it only does anything once a profile
     * exists, and v8 left every profile empty.
     */
    val V8_TO_V9: List<String> = listOf(
        "ALTER TABLE `settings` ADD COLUMN `autoGoals` INTEGER NOT NULL DEFAULT 1"
    )

    /**
     * v9 -> v10, the daily routine and steps by the hour:
     *  - `routine_items` holds the fixed day (wake-up, gym, meals, walk, reading, planning),
     *  - `routine_plans` holds per-day changes made the night before,
     *  - `routine_logs` holds what was done or missed, which is what the Routine tab scores,
     *  - `hourly_steps` holds steps per clock hour, so a day shows *when* you walked,
     * Four new tables, all starting empty; nothing existing is touched.
     */
    val V9_TO_V10: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `routine_items` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `key` TEXT NOT NULL, " +
            "`title` TEXT NOT NULL, `emoji` TEXT NOT NULL, `mode` TEXT NOT NULL, " +
            "`hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, " +
            "`kind` TEXT NOT NULL, `slot` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_routine_items_key` ON `routine_items` (`key`)",
        "CREATE TABLE IF NOT EXISTS `routine_plans` (" +
            "`date` TEXT NOT NULL, `itemId` INTEGER NOT NULL, `hour` INTEGER NOT NULL, " +
            "`minute` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, PRIMARY KEY(`date`, `itemId`))",
        "CREATE TABLE IF NOT EXISTS `routine_logs` (" +
            "`date` TEXT NOT NULL, `itemId` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
            "`at` INTEGER NOT NULL, PRIMARY KEY(`date`, `itemId`))",
        "CREATE TABLE IF NOT EXISTS `hourly_steps` (" +
            "`date` TEXT NOT NULL, `hour` INTEGER NOT NULL, `steps` REAL NOT NULL, " +
            "`source` TEXT NOT NULL, PRIMARY KEY(`date`, `hour`))"
    )

    /**
     * v11: the burn goal's default drops from 400 to 250 kcal. Burn used to be the phone's all-day
     * "active calories" (~3x the walk); it is now the walk itself, so 400 meant ~22,000 steps.
     * Only the untouched default moves - a goal the user typed in stays as it is. No schema change.
     */
    val V10_TO_V11: List<String> = listOf(
        "UPDATE `settings` SET `burnGoalKcal` = 250 WHERE `burnGoalKcal` = 400"
    )
}
