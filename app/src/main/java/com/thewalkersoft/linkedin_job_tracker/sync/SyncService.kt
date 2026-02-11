package com.thewalkersoft.linkedin_job_tracker.sync

import android.util.Log
import com.thewalkersoft.linkedin_job_tracker.client.RetrofitClient
import com.thewalkersoft.linkedin_job_tracker.data.JobDao
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity

/**
 * Service to handle bi-directional synchronization between local database and Google Sheets.
 *
 * Sync Rules:
 * 1. Use ID + URL combination for uniqueness
 * 2. If both have same status → App data takes precedence
 * 3. If different status → Most recent modification wins (lastModified timestamp)
 * 4. Missing in app → Add from sheet
 * 5. Missing in sheet → Upload to sheet
 */
class SyncService(private val dao: JobDao) {

    data class SyncResult(
        val uploaded: Int = 0,
        val downloaded: Int = 0,
        val updated: Int = 0,
        val conflicts: Int = 0
    )

    /**
     * Perform bi-directional sync between app and Google Sheets
     */
    suspend fun performBidirectionalSync(): SyncResult {
        var uploaded = 0
        var downloaded = 0
        var updated = 0
        var conflicts = 0

        try {
            // Step 1: Get data from both sources
            val localJobs = dao.getAllJobsOnce()
            val sheetJobs = RetrofitClient.instance.downloadJobs()

            Log.d("SyncService", "Starting sync: ${localJobs.size} local jobs, ${sheetJobs.size} sheet jobs")

            // Step 2: Create maps for efficient lookup using URL as unique key
            val localJobMap = localJobs.associateBy { it.jobUrl }
            val sheetJobMap = sheetJobs.associateBy { it.jobUrl }

            // Step 3: Process jobs from Google Sheets
            for (sheetJob in sheetJobs) {
                val localJob = localJobMap[sheetJob.jobUrl]

                if (localJob == null) {
                    // Job exists in sheet but not in app → Download to app
                    dao.upsertJob(sheetJob)
                    downloaded++
                    Log.d("SyncService", "Downloaded: ${sheetJob.companyName} from sheet")
                } else {
                    // Job exists in both → Check for conflicts
                    val conflict = resolveConflict(localJob, sheetJob)
                    when (conflict) {
                        ConflictResolution.UPDATE_LOCAL -> {
                            dao.upsertJob(sheetJob)
                            updated++
                            Log.d("SyncService", "Updated local: ${sheetJob.companyName} (sheet was newer)")
                        }
                        ConflictResolution.UPDATE_SHEET -> {
                            val response = RetrofitClient.instance.updateJob(localJob)
                            if (response.isSuccessful) {
                                updated++
                                Log.d("SyncService", "Updated sheet: ${localJob.companyName} (app was newer)")
                            } else {
                                Log.w("SyncService", "Failed to update sheet for ${localJob.companyName}: ${response.code()}")
                            }
                        }
                        ConflictResolution.UPDATE_BOTH -> {
                            // App takes precedence when status is same
                            val response = RetrofitClient.instance.updateJob(localJob)
                            if (response.isSuccessful) {
                                updated++
                                conflicts++
                                Log.d("SyncService", "Conflict resolved: ${localJob.companyName} (app took precedence)")
                            } else {
                                Log.w("SyncService", "Failed to resolve conflict for ${localJob.companyName}: ${response.code()}")
                            }
                        }
                        ConflictResolution.NO_CHANGE -> {
                            // Data is identical, no action needed
                            Log.d("SyncService", "No change needed: ${localJob.companyName}")
                        }
                    }
                }
            }

            // Step 4: Upload jobs that exist only in app
            for (localJob in localJobs) {
                if (!sheetJobMap.containsKey(localJob.jobUrl)) {
                    val response = RetrofitClient.instance.uploadJob(localJob)
                    if (response.isSuccessful) {
                        uploaded++
                        Log.d("SyncService", "Uploaded: ${localJob.companyName} to sheet")
                    } else {
                        Log.w("SyncService", "Failed to upload ${localJob.companyName}: ${response.code()}")
                    }
                }
            }

            Log.d("SyncService", "Sync completed: ⬆️ $uploaded uploaded, ⬇️ $downloaded downloaded, 🔄 $updated updated, ⚠️ $conflicts conflicts")
            return SyncResult(uploaded, downloaded, updated, conflicts)

        } catch (e: Exception) {
            Log.e("SyncService", "Sync failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * Resolve conflicts between local and sheet data
     */
    private fun resolveConflict(localJob: JobEntity, sheetJob: JobEntity): ConflictResolution {
        // Check if data is identical
        if (localJob.companyName == sheetJob.companyName &&
            localJob.jobDescription == sheetJob.jobDescription &&
            localJob.status == sheetJob.status) {
            return ConflictResolution.NO_CHANGE
        }

        // If status is the same, app takes precedence (update sheet)
        if (localJob.status == sheetJob.status) {
            return ConflictResolution.UPDATE_BOTH // App precedence
        }

        // If status is different, use timestamp to determine which is newer
        // Note: Sheet jobs may not have lastModified, so we use a fallback
        val localModified = localJob.lastModified
        val sheetModified = sheetJob.lastModified

        return if (localModified > sheetModified) {
            // Local is newer → Update sheet
            ConflictResolution.UPDATE_SHEET
        } else if (sheetModified > localModified) {
            // Sheet is newer → Update local
            ConflictResolution.UPDATE_LOCAL
        } else {
            // Timestamps are equal, app takes precedence
            ConflictResolution.UPDATE_BOTH
        }
    }

    enum class ConflictResolution {
        UPDATE_LOCAL,   // Update local database from sheet
        UPDATE_SHEET,   // Update sheet from local database
        UPDATE_BOTH,    // Update both (app takes precedence)
        NO_CHANGE       // No update needed
    }
}

