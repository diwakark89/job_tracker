package com.thewalkersoft.linkedin_job_tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [JobEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class JobDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao

    companion object {
        @Volatile
        private var INSTANCE: JobDatabase? = null

        // Migration from version 1 to version 2: Add lastModified column
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add lastModified column with default value = timestamp
                database.execSQL(
                    "ALTER TABLE jobs ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0"
                )
                // Update existing rows to have lastModified = timestamp
                database.execSQL(
                    "UPDATE jobs SET lastModified = timestamp WHERE lastModified = 0"
                )
            }
        }

        fun getDatabase(context: Context): JobDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JobDatabase::class.java,
                    "job_database"
                )
                .addMigrations(MIGRATION_1_2) // Add proper migration instead of destructive
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromJobStatus(status: JobStatus): String {
        return status.name
    }

    @TypeConverter
    fun toJobStatus(value: String): JobStatus {
        return parseJobStatus(value)
    }
}
