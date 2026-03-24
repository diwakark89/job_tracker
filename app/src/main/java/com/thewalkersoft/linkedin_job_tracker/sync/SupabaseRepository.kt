package com.thewalkersoft.linkedin_job_tracker.sync

import android.util.Log
import com.thewalkersoft.linkedin_job_tracker.client.SupabaseClient
import com.thewalkersoft.linkedin_job_tracker.data.JobDao
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.service.SharedLinkRequest

class SupabaseRepository(
    private val dao: JobDao
) {

    data class PullResult(
        val success: Boolean,
        val inserted: Int = 0,
        val updatedFromRemote: Int = 0,
        val uploaded: Int = 0,
        val preservedLocal: Int = 0,
        val failedPush: Int = 0
    )

    fun isConfigured(): Boolean = SupabaseClient.isCloudConfigured()

    suspend fun pushJob(job: JobEntity): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            val response = SupabaseClient.instance.upsertJob(listOf(job))
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                Log.w(
                    "SupabaseRepository",
                    "pushJob failed: code=${response.code()} body=$errorBody"
                )
            }
            response.isSuccessful
        }.getOrElse {
            Log.w("SupabaseRepository", "pushJob failed: ${it.message}")
            false
        }
    }

    suspend fun pushDelete(jobId: String): DeletePushResult {
        if (!isConfigured()) return DeletePushResult.RETRY
        return runCatching {
            val response = SupabaseClient.instance.deleteJobById("eq.$jobId")
            when {
                response.isSuccessful -> DeletePushResult.SUCCESS
                response.code() == 404 -> DeletePushResult.NOT_FOUND
                else -> DeletePushResult.RETRY
            }
        }.getOrElse {
            Log.w("SupabaseRepository", "pushDelete failed: ${it.message}")
            DeletePushResult.RETRY
        }
    }

    suspend fun pushSharedLink(rawUrl: String): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            val response = SupabaseClient.instance.insertSharedLink(
                listOf(SharedLinkRequest(url = rawUrl))
            )
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                Log.w(
                    "SupabaseRepository",
                    "pushSharedLink failed: code=${response.code()} body=$errorBody"
                )
            }
            response.isSuccessful
        }.getOrElse {
            Log.w("SupabaseRepository", "pushSharedLink failed: ${it.message}")
            false
        }
    }

    suspend fun pullCloudJobsToRoom(): PullResult {
        if (!isConfigured()) return PullResult(success = false)
        return runCatching {
            val remoteJobs = SupabaseClient.instance.getJobs()
            val localJobs = dao.getAllJobsOnce()
            val remoteJobsByUrl = remoteJobs.associateBy { it.jobUrl }
            val localJobsByUrl = localJobs.associateBy { it.jobUrl }

            var inserted = 0
            var updatedFromRemote = 0
            var uploaded = 0
            var preservedLocal = 0
            var failedPush = 0

            remoteJobs.forEach { remoteJob ->
                val localJob = localJobsByUrl[remoteJob.jobUrl] ?: dao.getJobByUrl(remoteJob.jobUrl)
                when {
                    localJob == null -> {
                        dao.upsertJob(remoteJob)
                        inserted++
                    }

                    remoteJob.lastModified > localJob.lastModified -> {
                        // Keep the local primary key for the same business record (jobUrl).
                        dao.upsertJob(remoteJob.copy(id = localJob.id))
                        updatedFromRemote++
                    }

                    else -> {
                        // Local record wins on ties and when local is newer.
                        if (localJob.lastModified > remoteJob.lastModified) {
                            val response = SupabaseClient.instance.upsertJob(listOf(localJob))
                            if (response.isSuccessful) {
                                uploaded++
                            } else {
                                failedPush++
                                val errorBody = response.errorBody()?.string().orEmpty()
                                Log.w(
                                    "SupabaseRepository",
                                    "pullCloudJobsToRoom push(local newer) failed: code=${response.code()} body=$errorBody"
                                )
                            }
                        } else {
                            preservedLocal++
                        }
                    }
                }
            }

            // Upload jobs that exist only in local storage.
            localJobs.forEach { localJob ->
                if (!remoteJobsByUrl.containsKey(localJob.jobUrl)) {
                    val response = SupabaseClient.instance.upsertJob(listOf(localJob))
                    if (response.isSuccessful) {
                        uploaded++
                    } else {
                        failedPush++
                        val errorBody = response.errorBody()?.string().orEmpty()
                        Log.w(
                            "SupabaseRepository",
                            "pullCloudJobsToRoom push(local missing remotely) failed: code=${response.code()} body=$errorBody"
                        )
                    }
                }
            }

            Log.d(
                "SupabaseRepository",
                "pullCloudJobsToRoom: inserted=$inserted updatedFromRemote=$updatedFromRemote uploaded=$uploaded preservedLocal=$preservedLocal failedPush=$failedPush"
            )
            PullResult(
                success = true,
                inserted = inserted,
                updatedFromRemote = updatedFromRemote,
                uploaded = uploaded,
                preservedLocal = preservedLocal,
                failedPush = failedPush
            )
        }.getOrElse {
            Log.w("SupabaseRepository", "pullCloudJobsToRoom failed: ${it.message}")
            PullResult(success = false)
        }
    }

    enum class DeletePushResult {
        SUCCESS,
        NOT_FOUND,
        RETRY
    }
}

