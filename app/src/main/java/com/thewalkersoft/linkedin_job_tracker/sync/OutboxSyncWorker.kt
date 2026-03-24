package com.thewalkersoft.linkedin_job_tracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.thewalkersoft.linkedin_job_tracker.data.JobDatabase
import com.thewalkersoft.linkedin_job_tracker.util.PreferencesManager

class OutboxSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dao = JobDatabase.getDatabase(applicationContext).jobDao()
        val preferences = PreferencesManager(applicationContext)
        val repository = SupabaseRepository(dao)

        preferences.compactOutbox()

        val operations = preferences.getOutboxOperations()
        operations.forEach { operation ->
            when (operation.type) {
                OutboxOperationType.UPSERT -> {
                    val job = dao.getJobByUrl(operation.jobUrl)
                    if (job != null && repository.pushJob(job)) {
                        preferences.acknowledgeOperation(operation.key)
                    }
                }

                OutboxOperationType.DELETE -> {
                    val result = operation.jobId?.let { repository.pushDelete(it) }
                    if (result == SupabaseRepository.DeletePushResult.SUCCESS || result == SupabaseRepository.DeletePushResult.NOT_FOUND) {
                        // Idempotent delete replay: missing remote rows are treated as terminal success.
                        preferences.acknowledgeOperation(operation.key)
                    }
                }

                OutboxOperationType.SHARED_LINK -> {
                    val shared = operation.sharedUrl
                    if (!shared.isNullOrBlank() && repository.pushSharedLink(shared)) {
                        preferences.acknowledgeOperation(operation.key)
                    }
                }
            }
        }

        repository.pullCloudJobsToRoom()
        return Result.success()
    }
}

