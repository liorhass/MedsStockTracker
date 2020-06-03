package com.liorhass.android.medsstocktracker.database
// Database migration: https://medium.com/androiddevelopers/understanding-migrations-with-room-f01e04b07929

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

@Database(entities=[Medicine::class, LoggedEvent::class], version=AppDatabase.DATABASE_VERSION, exportSchema=false)
abstract class AppDatabase : RoomDatabase() {

    abstract val medicineDao: MedicinesDao
    abstract val loggedEventDao: LoggedEventsDao

    companion object {
        const val DATABASE_VERSION = 8

        @Volatile
        private var INSTANCE: AppDatabase? = null

        // In the medicines table we need to delete two columns. SQLite doesn't support this directly,
        // so we create a new temp table, copy the old data to the new one, delete the old table and
        // rename the new one.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Timber.i("MIGRATION_7_8.migrate()")
                migrateMedicinesTableV7toV8(database)
                migrateEventsTableV7toV8(database)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java, "medsStockTracker"
                    )
                        .addMigrations(MIGRATION_7_8)
                        .build()
                    INSTANCE = instance
                }
                return instance
            }
        }

        fun migrateMedicinesTableV7toV8(database: SupportSQLiteDatabase) {
            // Create a new table
            database.execSQL(
                """CREATE TABLE medicines_new (
                         _id                               INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                         med_name                          TEXT NOT NULL,
                         n_available_originally            REAL NOT NULL,
                         n_available_update_date_and_time  INTEGER NOT NULL,
                         daily_dose                        REAL NOT NULL,
                         time_of_last_alert                INTEGER NOT NULL)""")

            // Copy the data
            database.execSQL(
                """INSERT INTO medicines_new (
                         _id, med_name, n_available_originally, n_available_update_date_and_time,
                         daily_dose, time_of_last_alert) SELECT
                         _id, med_name, n_available_originally, n_available_update_date_and_time,
                         daily_dose, time_of_last_alert FROM medicines""")

            // Remove the old table
            database.execSQL("DROP TABLE medicines")

            // Rename the new table to its "normal" name
            database.execSQL("ALTER TABLE medicines_new RENAME TO medicines")
        }

        // Have to do this only because the old version had nullable columns
        fun migrateEventsTableV7toV8(database: SupportSQLiteDatabase) {
            // Create a new table
            database.execSQL(
                """CREATE TABLE eventLogs_new (
                         _id             INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                         date_and_time   INTEGER NOT NULL,
                         type            INTEGER NOT NULL,
                         med_name        TEXT NOT NULL,
                         text            TEXT NOT NULL)""")

            // Copy the data
            database.execSQL(
                """INSERT INTO eventLogs_new (
                         _id, date_and_time, type, med_name, text) SELECT
                         _id, date_and_time, type, med_name, text FROM eventLogs""")

            // Remove the old table
            database.execSQL("DROP TABLE eventLogs")

            // Rename the new table to its "normal" name
            database.execSQL("ALTER TABLE eventLogs_new RENAME TO eventLogs")
        }
    }
}