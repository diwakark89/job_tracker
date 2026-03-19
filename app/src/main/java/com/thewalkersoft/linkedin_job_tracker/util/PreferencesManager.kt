package com.thewalkersoft.linkedin_job_tracker.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun saveLastSyncTime(lastSyncTime: String) {
        sharedPreferences.edit().putString(KEY_LAST_SYNC_TIME, lastSyncTime).apply()
    }

    fun saveLastSyncTimeMillis(timestampMillis: Long) {
        sharedPreferences.edit().putLong(KEY_LAST_SYNC_TIME_MILLIS, timestampMillis).apply()
    }

    fun getLastSyncTimeMillis(): Long? {
        val timestamp = sharedPreferences.getLong(KEY_LAST_SYNC_TIME_MILLIS, -1L)
        return if (timestamp > 0L) timestamp else null
    }

    fun getLastSyncTime(): String {
        return sharedPreferences.getString(KEY_LAST_SYNC_TIME, "Never") ?: "Never"
    }

    companion object {
        private const val PREFERENCES_NAME = "linkedin_job_tracker_prefs"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_LAST_SYNC_TIME_MILLIS = "last_sync_time_millis"
    }
}

