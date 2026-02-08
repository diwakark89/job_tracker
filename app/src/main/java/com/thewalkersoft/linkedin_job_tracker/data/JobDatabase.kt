package com.thewalkersoft.linkedin_job_tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(entities = [JobEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class JobDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao

    companion object {
        @Volatile
        private var INSTANCE: JobDatabase? = null

        fun getDatabase(context: Context): JobDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JobDatabase::class.java,
                    "job_database"
                ).build()
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
        return JobStatus.valueOf(value)
    }
}

