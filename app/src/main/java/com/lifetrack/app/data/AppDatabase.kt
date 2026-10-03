package com.lifetrack.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Meal::class, FoodItem::class, Settings::class, Habit::class, Session::class,
        HabitCheck::class, Reminder::class, TrackedApp::class, AppUsageDay::class,
        DailyMetric::class, StepDay::class, RoutineItem::class, RoutinePlan::class,
        RoutineLog::class, HourlySteps::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun foodDao(): FoodDao
    abstract fun settingsDao(): SettingsDao
    abstract fun habitDao(): HabitDao
    abstract fun reminderDao(): ReminderDao
    abstract fun trackedAppDao(): TrackedAppDao
    abstract fun metricDao(): MetricDao
    abstract fun stepDao(): StepDao
    abstract fun routineDao(): RoutineDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Migrations.V3_TO_V4.forEach(db::execSQL)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Migrations.V4_TO_V5.forEach(db::execSQL)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Migrations.V5_TO_V6.forEach(db::execSQL)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Migrations.V6_TO_V7.forEach(db::execSQL)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Migrations.V7_TO_V8.forEach(db::execSQL)
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Migrations.V8_TO_V9.forEach(db::execSQL)
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Migrations.V9_TO_V10.forEach(db::execSQL)
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "lifetrack.db"
            )
                .addMigrations(
                    MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
                )
                // Last resort if someone is coming from a build older than v3.
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
