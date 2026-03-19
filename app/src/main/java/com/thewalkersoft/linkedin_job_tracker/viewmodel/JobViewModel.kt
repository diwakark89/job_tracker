package com.thewalkersoft.linkedin_job_tracker.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thewalkersoft.linkedin_job_tracker.client.RetrofitClient
import com.thewalkersoft.linkedin_job_tracker.data.JobDatabase
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.data.parseJobStatus
import com.thewalkersoft.linkedin_job_tracker.scraper.JobScraper
import com.thewalkersoft.linkedin_job_tracker.sync.SyncService
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

class JobViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = JobDatabase.getDatabase(application).jobDao()
    private val syncService = SyncService(dao)
    private val preferencesManager = PreferencesManager(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow<JobStatus?>(null)
    val statusFilter: StateFlow<JobStatus?> = _statusFilter.asStateFlow()

    private val _isScraping = MutableStateFlow(false)
    val isScraping: StateFlow<Boolean> = _isScraping.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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

    fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            parseAndScrapeLinkedInJob(sharedText)
        }
    }

    /**
     * Generate the next available ID by finding the max ID from both local DB and Google Sheets
     */
    private suspend fun getNextJobId(): Long {
        try {
            // Get max ID from local database
            val localMaxId = dao.getMaxId() ?: 0L

            // Get max ID from Google Sheets
            val sheetMaxId = try {
                val sheetJobs = RetrofitClient.instance.downloadJobs()
                sheetJobs.maxOfOrNull { it.id } ?: 0L
            } catch (e: Exception) {
                Log.w("NextJobId", "Failed to get max ID from sheet: ${e.message}")
                0L
            }

            // Return the next ID after the maximum
            return maxOf(localMaxId, sheetMaxId) + 1L
        } catch (e: Exception) {
            Log.e("NextJobId", "Error calculating next ID: ${e.message}")
            // Fallback: just get local max and add 1
            return (dao.getMaxId() ?: 0L) + 1L
        }
    }

    fun saveAndSyncJob(job: JobEntity) {
        viewModelScope.launch {
            // 1. Save locally for instant UI feedback
            dao.upsertJob(job)

            // 2. Sync to Google Sheets
            try {
                Log.d("Sync", "📤 Uploading job to Google Sheets:")
                Log.d("Sync", "   Company: ${job.companyName}")
                Log.d("Sync", "   Job Title: '${job.jobTitle}'")
                Log.d("Sync", "   URL: ${job.jobUrl}")

                val response = RetrofitClient.instance.uploadJob(job)
                if (response.isSuccessful) {
                    Log.d("Sync", "✅ Successfully uploaded job '${job.companyName}' to Google Sheets")
                    _message.value = "✅ Job synced to Google Sheets successfully!"
                    updateSyncTimestamp()
                } else {
                    Log.w("Sync", "⚠️ Upload response: ${response.code()} - ${response.message()}")
                    _message.value = "⚠️ Job saved locally, but sync returned: ${response.message()}"
                }
            } catch (e: Exception) {
                Log.e("Sync", "❌ Sync failed: ${e.message}", e)
                _message.value = "⚠️ Job saved locally, but failed to sync: ${e.message}"
            }
        }
    }

    fun syncFromSheet() {
        viewModelScope.launch {
            _isScraping.value = true // Reuse the scraper spinner for sync feedback
            try {
                val syncResult = syncService.performBidirectionalSync()

                updateSyncTimestamp() // Success!

                // Create detailed sync message
                val message = buildString {
                    append("✅ Sync completed!\n")
                    if (syncResult.uploaded > 0) append("⬆️ ${syncResult.uploaded} uploaded\n")
                    if (syncResult.downloaded > 0) append("⬇️ ${syncResult.downloaded} downloaded\n")
                    if (syncResult.updated > 0) append("🔄 ${syncResult.updated} updated\n")
                    if (syncResult.conflicts > 0) append("⚠️ ${syncResult.conflicts} conflicts resolved (app took precedence)")
                }

                Log.d("Sync", message)
                _message.value = message.trim()
            } catch (e: Exception) {
                Log.e("Sync", "Sync failed: ${e.message}", e)
                _message.value = "❌ Sync failed: ${e.message ?: "Unknown error"}"
            } finally {
                _isScraping.value = false
            }
        }
    }

    private val _lastSyncTime = MutableStateFlow(getInitialLastSyncTime())
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    private fun getInitialLastSyncTime(): String {
        val timestamp = preferencesManager.getLastSyncTimeMillis()
        return if (timestamp != null) {
            formatSyncTime(timestamp)
        } else {
            preferencesManager.getLastSyncTime()
        }
    }

    private fun formatSyncTime(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return formatter.format(Date(timestampMillis))
    }

    private fun updateSyncTimestamp() {
        val now = System.currentTimeMillis()
        val formattedTime = formatSyncTime(now)
        _lastSyncTime.value = formattedTime
        // Save both readable and epoch values for robust restore across restarts.
        preferencesManager.saveLastSyncTime(formattedTime)
        preferencesManager.saveLastSyncTimeMillis(now)
    }

    private fun parseAndScrapeLinkedInJob(text: String) {
        // Parse text like: "Check out this job at [Company]: [Link]"
        // or just a LinkedIn URL
        val urlRegex = Regex("(https?://[^\\s]+)")
        val url = urlRegex.find(text)?.value ?: return

        scrapeAndSaveJob(url)
    }

    fun scrapeAndSaveJob(url: String) {
        viewModelScope.launch {
            _isScraping.value = true
            try {
                // Check if job already exists
                val existingJob = dao.getJobByUrl(url)
                if (existingJob != null) {
                    _message.value = "Job already saved! Current status: ${existingJob.status.name.replace("_", " ")}"
                    return@launch
                }

                // Scrape all job information at once
                val jobInfo = JobScraper.scrapeJobInfo(url)

                // Generate next available ID
                val nextId = getNextJobId()

                val job = JobEntity(
                    id = nextId,
                    companyName = jobInfo.companyName,
                    jobUrl = url,
                    jobDescription = jobInfo.description,
                    jobTitle = jobInfo.jobTitle,
                    status = JobStatus.SAVED
                )
                saveAndSyncJob(job)
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

            // Sync status change to Google Sheets automatically
            try {
                val response = RetrofitClient.instance.updateJob(updatedJob)
                if (response.isSuccessful) {
                    Log.d("Sync", "✅ Status updated to ${newStatus.name} and synced to Google Sheets for ${job.companyName}")
                    _message.value = "✅ Status updated and synced to Google Sheets"
                    updateSyncTimestamp()
                } else {
                    Log.w("Sync", "⚠️ Status updated locally but sync response: ${response.code()}")
                    _message.value = "⚠️ Status updated locally but sync failed"
                }
            } catch (e: Exception) {
                Log.w("Sync", "⚠️ Status updated locally but failed to sync: ${e.message}")
                _message.value = "⚠️ Status updated locally but sync to Google Sheets failed"
                // Status is already saved locally, so it's not a critical failure
            }
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

            // Sync job changes to Google Sheets
            try {
                val response = RetrofitClient.instance.updateJob(updatedJob)
                if (response.isSuccessful) {
                    Log.d("Sync", "✅ Job details updated and synced to Google Sheets for ${companyName}")
                    _message.value = "✅ Job updated and synced to Google Sheets"
                    updateSyncTimestamp()
                } else {
                    Log.w("Sync", "⚠️ Job updated locally but sync response: ${response.code()}")
                    _message.value = "⚠️ Job updated locally but sync returned: ${response.message()}"
                }
            } catch (e: Exception) {
                Log.w("Sync", "⚠️ Job updated locally but failed to sync: ${e.message}")
                _message.value = "⚠️ Job updated locally but sync to Google Sheets failed"
            }
        }
    }

    fun deleteJob(jobId: Long) {
        viewModelScope.launch {
            Log.d("DeleteJob", "🗑️ Starting deletion process for jobId: $jobId")

            // Get the job before deleting it to have its data for sync
            val job = dao.getAllJobsOnce().firstOrNull { it.id == jobId }

            if (job == null) {
                Log.e("DeleteJob", "❌ Job not found in database with id: $jobId")
                _message.value = "❌ Job not found"
                return@launch
            }

            Log.d("DeleteJob", "📋 Found job: ${job.companyName} (URL: ${job.jobUrl})")

            // Delete locally
            dao.deleteJob(jobId)
            Log.d("DeleteJob", "✅ Job deleted from local database")

            // Sync deletion to Google Sheets
            try {
                Log.d("DeleteJob", "☁️ Attempting to sync deletion to Google Sheets...")
                val response = RetrofitClient.instance.deleteJob(job)
                Log.d("DeleteJob", "📡 Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful) {
                    val body = response.body()
                    val result = body?.result?.lowercase(Locale.US)
                    if (result == "success") {
                        Log.d("DeleteJob", "✅ Job deleted from Google Sheets: ${job.companyName}")
                        Log.d("DeleteJob", "Response body: $body")
                        _message.value = "✅ Job deleted and synced to Google Sheets"
                        updateSyncTimestamp()
                    } else {
                        Log.w("DeleteJob", "⚠️ Delete call succeeded but script returned: ${body?.message}")
                        Log.w("DeleteJob", "Response body: $body")
                        _message.value = "⚠️ Job deleted locally but Google Sheets returned: ${body?.message ?: "Unknown error"}"
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.w("DeleteJob", "⚠️ Job deleted locally but sync response: ${response.code()}")
                    Log.w("DeleteJob", "Error body: $errorBody")
                    _message.value = "⚠️ Job deleted locally but sync returned: ${response.message()}"
                }
            } catch (e: Exception) {
                Log.e("DeleteJob", "⚠️ Job deleted locally but failed to sync: ${e.message}", e)
                e.printStackTrace()
                _message.value = "⚠️ Job deleted locally but sync to Google Sheets failed: ${e.message}"
            }
        }
    }

    fun restoreJob(job: JobEntity) {
        viewModelScope.launch {
            saveAndSyncJob(job)
        }
    }
}
