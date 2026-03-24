package com.thewalkersoft.linkedin_job_tracker.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.JsonObject
import com.thewalkersoft.linkedin_job_tracker.client.SupabaseClient
import com.thewalkersoft.linkedin_job_tracker.data.JobDatabase
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.data.displayName
import com.thewalkersoft.linkedin_job_tracker.scraper.JobScraper
import com.thewalkersoft.linkedin_job_tracker.sync.OutboxOperation
import com.thewalkersoft.linkedin_job_tracker.sync.OutboxOperationType
import com.thewalkersoft.linkedin_job_tracker.sync.OutboxWorkScheduler
import com.thewalkersoft.linkedin_job_tracker.sync.RealtimeConnectionState
import com.thewalkersoft.linkedin_job_tracker.sync.RealtimeJobEvent
import com.thewalkersoft.linkedin_job_tracker.sync.SupabaseRealtimeManager
import com.thewalkersoft.linkedin_job_tracker.sync.SupabaseRepository
import com.thewalkersoft.linkedin_job_tracker.util.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class JobViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = JobDatabase.getDatabase(application).jobDao()
    private val repository = SupabaseRepository(dao)
    private val preferencesManager = PreferencesManager(application)
    private val realtimeManager = SupabaseRealtimeManager()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow<JobStatus?>(null)
    val statusFilter: StateFlow<JobStatus?> = _statusFilter.asStateFlow()

    private val _isScraping = MutableStateFlow(false)
    val isScraping: StateFlow<Boolean> = _isScraping.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _cloudHealth = MutableStateFlow("Cloud: Offline")
    val cloudHealth: StateFlow<String> = _cloudHealth.asStateFlow()

    /** 0 = hidden, 1 = step-1 dialog, 2 = step-2 dialog. Debug-only. */
    private val _diagnosticsStep = MutableStateFlow(0)
    val diagnosticsStep: StateFlow<Int> = _diagnosticsStep.asStateFlow()

    // All jobs without any filter (for calculating counts)
    val allJobs: StateFlow<List<JobEntity>> = dao.getAllJobs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val jobs: StateFlow<List<JobEntity>> = combine(
        _searchQuery,
        _statusFilter,
        allJobs
    ) { query, selectedStatus, allJobs ->
        val normalizedQuery = query.trim()
        allJobs.filter { job ->
            val matchesQuery = normalizedQuery.isBlank() ||
                job.companyName.contains(normalizedQuery, ignoreCase = true)
            val matchesStatus = selectedStatus == null || job.status == selectedStatus
            matchesQuery && matchesStatus
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onStatusFilterChange(status: JobStatus?) {
        _statusFilter.value = status
    }

    fun clearMessage() {
        _message.value = null
    }

    init {
        OutboxWorkScheduler.schedule(application)

        if (!repository.isConfigured()) {
            _cloudHealth.value = "Cloud: Not Configured"
            _message.value = "Supabase not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY."
        }

        // 1. Warm Room cache from cloud
        viewModelScope.launch {
            val didSync = repository.pullCloudJobsToRoom()
            if (didSync) preferencesManager.saveLastSyncTimeMillis(System.currentTimeMillis())
        }

        // 2. Open WebSocket – UI recomposes on every INSERT/UPDATE/DELETE
        realtimeManager.connect()

        // 3. Mirror realtime connection state into cloudHealth banner
        viewModelScope.launch {
            realtimeManager.connectionState.collect { refreshCloudHealth() }
        }

        // 4. Apply incoming job changes directly into Room
        viewModelScope.launch {
            realtimeManager.jobEvents.collect { processRealtimeEvent(it) }
        }

        // 5. Execute deferred diagnostics reset once active worker becomes idle
        viewModelScope.launch {
            WorkManager.getInstance(application)
                .getWorkInfosForUniqueWorkFlow(OutboxWorkScheduler.WORK_NAME)
                .collect { workInfos ->
                    val isActive = workInfos.any { it.state == WorkInfo.State.RUNNING }
                    if (!isActive && preferencesManager.isPendingDiagnosticsReset()) {
                        executeDiagnosticsResetNow()
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeManager.disconnect()
    }

    // ── Realtime event processing ─────────────────────────────────────────────

    private suspend fun processRealtimeEvent(event: RealtimeJobEvent) {
        try {
            when (event) {
                is RealtimeJobEvent.Insert -> parseJob(event.record)?.let { dao.upsertJob(it) }
                is RealtimeJobEvent.Update -> parseJob(event.record)?.let { dao.upsertJob(it) }
                is RealtimeJobEvent.Delete -> {
                    val id = event.oldRecord.get("id")?.asString
                    if (!id.isNullOrBlank()) dao.deleteJob(id)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Realtime event processing error: ${e.message}")
        }
    }

    private fun parseJob(json: JsonObject): JobEntity? =
        runCatching {
            SupabaseClient.supabaseGson.fromJson(json, JobEntity::class.java)
        }.getOrElse {
            Log.w(TAG, "Failed to parse job from realtime payload: ${it.message}")
            null
        }

    fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            viewModelScope.launch {
                handleSharedLink(sharedText)
            }
        }
    }

    fun saveJob(job: JobEntity) {
        viewModelScope.launch {
            dao.upsertJob(job)
            queueOrPushUpsert(job)
            _message.value = "Saved locally"
        }
    }

    private suspend fun handleSharedLink(rawSharedText: String) {
        val pushed = repository.pushSharedLink(rawSharedText)
        if (!pushed) {
            preferencesManager.enqueueOperation(
                OutboxOperation(
                    type = OutboxOperationType.SHARED_LINK,
                    jobUrl = rawSharedText,
                    sharedUrl = rawSharedText
                )
            )
            OutboxWorkScheduler.kick(getApplication())
        }
        refreshCloudHealth()
        _message.value = "Shared link queued for processing"
    }

    fun scrapeAndSaveJob(url: String) {
        viewModelScope.launch {
            _isScraping.value = true
            try {
                // Check if job already exists
                val existingJob = dao.getJobByUrl(url)
                if (existingJob != null) {
                    _message.value = "Job already saved! Current status: ${existingJob.status.displayName()}"
                    return@launch
                }

                // Scrape all job information at once
                val jobInfo = JobScraper.scrapeJobInfo(url)

                val job = JobEntity(
                    id = UUID.randomUUID().toString(),
                    companyName = jobInfo.companyName,
                    jobUrl = url,
                    jobDescription = jobInfo.description,
                    jobTitle = jobInfo.jobTitle,
                    status = JobStatus.SAVED
                )
                saveJob(job)
            } catch (e: Exception) {
                _message.value = "Failed to save job: ${e.message}"
            } finally {
                _isScraping.value = false
            }
        }
    }

    fun updateJobStatus(job: JobEntity, newStatus: JobStatus) {
        viewModelScope.launch {
            val updatedJob = job.copy(
                status = newStatus,
                lastModified = System.currentTimeMillis()
            )
            dao.upsertJob(updatedJob)
            queueOrPushUpsert(updatedJob)
        }
    }

    fun updateJob(job: JobEntity, companyName: String, jobUrl: String, jobTitle: String, jobDescription: String) {
        viewModelScope.launch {
            val updatedJob = job.copy(
                companyName = companyName,
                jobUrl = jobUrl,
                jobTitle = jobTitle,
                jobDescription = jobDescription,
                lastModified = System.currentTimeMillis()
            )
            dao.upsertJob(updatedJob)
            queueOrPushUpsert(updatedJob)
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            val job = dao.getAllJobsOnce().firstOrNull { it.id == jobId }
            if (job == null) {
                _message.value = "❌ Job not found"
                return@launch
            }
            dao.deleteJob(jobId)
            queueOrPushDelete(job)
        }
    }

    fun restoreJob(job: JobEntity) {
        viewModelScope.launch {
            saveJob(job)
        }
    }

    private suspend fun queueOrPushUpsert(job: JobEntity) {
        val pushed = repository.pushJob(job)
        if (!pushed) {
            preferencesManager.enqueueOperation(
                OutboxOperation(
                    type = OutboxOperationType.UPSERT,
                    jobId = job.id,
                    jobUrl = job.jobUrl,
                    lastModified = job.lastModified
                )
            )
            OutboxWorkScheduler.kick(getApplication())
        } else {
            preferencesManager.saveLastSyncTimeMillis(System.currentTimeMillis())
        }
        refreshCloudHealth()
    }

    private suspend fun queueOrPushDelete(job: JobEntity) {
        val result = repository.pushDelete(job.id)
        val pushed = result == SupabaseRepository.DeletePushResult.SUCCESS ||
            result == SupabaseRepository.DeletePushResult.NOT_FOUND
        if (!pushed) {
            preferencesManager.enqueueOperation(
                OutboxOperation(
                    type = OutboxOperationType.DELETE,
                    jobId = job.id,
                    jobUrl = job.jobUrl,
                    lastModified = System.currentTimeMillis()
                )
            )
            OutboxWorkScheduler.kick(getApplication())
        } else {
            preferencesManager.saveLastSyncTimeMillis(System.currentTimeMillis())
        }
        refreshCloudHealth()
    }

    private fun refreshCloudHealth() {
        val state = realtimeManager.connectionState.value
        val queueSize = preferencesManager.getOutboxOperations().size
        val (rQ, rC, rR) = preferencesManager.getRollingMetricsSummary()
        val lastMs = preferencesManager.getLastSyncTimeMillis()
        val lastLabel = if (lastMs != null)
            SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date(lastMs))
        else "never"
        val stateLabel = when (state) {
            RealtimeConnectionState.CONNECTED    -> "Live ●"
            RealtimeConnectionState.CONNECTING   -> "Connecting…"
            RealtimeConnectionState.DISCONNECTED -> "Offline ○"
            RealtimeConnectionState.ERROR        -> "Error ⚠"
        }
        _cloudHealth.value =
            "Cloud: $stateLabel | Queue: $queueSize | 60m q/c/r: $rQ/$rC/$rR | Last: $lastLabel"
    }

    // ── Diagnostics (debug-only) ──────────────────────────────────────────────

    fun requestDiagnosticsReset() { _diagnosticsStep.value = 1 }

    fun confirmResetQueue() {
        _diagnosticsStep.value = 0
        if (isSyncWorkerActive()) {
            _diagnosticsStep.value = 2
        } else {
            executeDiagnosticsResetNow()
        }
    }

    fun confirmCancelWorker() {
        _diagnosticsStep.value = 0
        WorkManager.getInstance(getApplication())
            .cancelUniqueWork(OutboxWorkScheduler.WORK_NAME)
        executeDiagnosticsResetNow()
        OutboxWorkScheduler.schedule(getApplication())
    }

    fun declineCancelWorker() {
        _diagnosticsStep.value = 0
        if (isSyncWorkerActive()) {
            // Persist deferred flag; WorkManager observer in init will trigger reset once idle
            preferencesManager.setPendingDiagnosticsReset(true)
        } else {
            executeDiagnosticsResetNow()
        }
    }

    fun dismissDiagnosticsReset() { _diagnosticsStep.value = 0 }

    private fun executeDiagnosticsResetNow() {
        preferencesManager.clearMetricsAndOutbox()
        preferencesManager.setPendingDiagnosticsReset(false)
        refreshCloudHealth()
        Log.d(TAG, "Diagnostics reset executed")
    }

    private fun isSyncWorkerActive(): Boolean =
        runCatching {
            WorkManager.getInstance(getApplication())
                .getWorkInfosForUniqueWork(OutboxWorkScheduler.WORK_NAME)
                .get()
                .any { it.state == WorkInfo.State.RUNNING }
        }.getOrDefault(false)

    companion object {
        private const val TAG = "JobViewModel"
    }
}
