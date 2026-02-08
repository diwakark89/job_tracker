package com.thewalkersoft.linkedin_job_tracker.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thewalkersoft.linkedin_job_tracker.data.JobDatabase
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.scraper.JobScraper
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow<JobStatus?>(null)
    val statusFilter: StateFlow<JobStatus?> = _statusFilter.asStateFlow()

    private val _isScraping = MutableStateFlow(false)
    val isScraping: StateFlow<Boolean> = _isScraping.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val jobs: StateFlow<List<JobEntity>> = combine(
        _searchQuery,
        _statusFilter,
        dao.getAllJobs()
    ) { query, selectedStatus, allJobs ->
        allJobs.filter { job ->
            val matchesQuery = query.isBlank() || job.companyName.contains(query, ignoreCase = true)
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

    private fun parseAndScrapeLinkedInJob(text: String) {
        // Parse text like: "Check out this job at [Company]: [Link]"
        // or just a LinkedIn URL
        val urlRegex = Regex("(https?://[^\\s]+)")
        val url = urlRegex.find(text)?.value ?: return

        // Try to extract company name from the text
        val companyRegex = Regex("at ([^:]+):|company/([^/\\s]+)")
        val companyMatch = companyRegex.find(text)
        val companyName = companyMatch?.groupValues?.getOrNull(1)?.trim()
            ?: companyMatch?.groupValues?.getOrNull(2)?.trim()
            ?: extractCompanyFromUrl(url)

        scrapeAndSaveJob(url, companyName)
    }

    private fun extractCompanyFromUrl(url: String): String {
        // Try to extract company from LinkedIn URL pattern
        val companyRegex = Regex("/company/([^/?]+)")
        return companyRegex.find(url)?.groupValues?.getOrNull(1) ?: "Unknown Company"
    }

    fun scrapeAndSaveJob(url: String, companyName: String) {
        viewModelScope.launch {
            _isScraping.value = true
            try {
                // Check if job already exists
                val existingJob = dao.getJobByUrl(url)
                if (existingJob != null) {
                    _message.value = "Job already saved! Current status: ${existingJob.status.name.replace("_", " ")}"
                    return@launch
                }

                val description = JobScraper.scrapeJobDescription(url)
                val job = JobEntity(
                    companyName = companyName,
                    jobUrl = url,
                    jobDescription = description,
                    status = JobStatus.SAVED
                )
                dao.upsertJob(job)
                _message.value = "Job saved successfully!"
            } catch (e: Exception) {
                _message.value = "Failed to save job: ${e.message}"
            } finally {
                _isScraping.value = false
            }
        }
    }

    fun updateJobStatus(job: JobEntity, newStatus: JobStatus) {
        viewModelScope.launch {
            dao.upsertJob(job.copy(status = newStatus))
        }
    }

    fun updateJob(job: JobEntity, companyName: String, jobUrl: String, jobDescription: String) {
        viewModelScope.launch {
            dao.upsertJob(
                job.copy(
                    companyName = companyName,
                    jobUrl = jobUrl,
                    jobDescription = jobDescription
                )
            )
        }
    }

    fun deleteJob(jobId: Long) {
        viewModelScope.launch {
            dao.deleteJob(jobId)
        }
    }

    fun restoreJob(job: JobEntity) {
        viewModelScope.launch {
            dao.upsertJob(job)
        }
    }

    fun exportJobsToCsv(uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            try {
                val jobs = dao.getAllJobsOnce()
                val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)

                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.writer().use { writer ->
                        // Write CSV header
                        writer.appendLine("companyName,jobUrl,jobDescription,status,timestamp")
                        // Write each job as a CSV row
                        jobs.forEach { job ->
                            val formattedDate = dateFormat.format(Date(job.timestamp))
                            val row = listOf(
                                escapeCsv(job.companyName),
                                escapeCsv(job.jobUrl),
                                escapeCsv(job.jobDescription),
                                escapeCsv(job.status.name),
                                escapeCsv(formattedDate)
                            ).joinToString(",")
                            writer.appendLine(row)
                        }
                    }
                }
                _message.value = "✅ Exported ${jobs.size} job(s) to CSV"
            } catch (e: Exception) {
                _message.value = "❌ Export failed: ${e.message}"
            }
        }
    }

    fun importJobsFromCsv(uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            try {
                var importedCount = 0
                var skippedCount = 0
                val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
                val legacyDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val legacyDateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                resolver.openInputStream(uri)?.use { inputStream ->
                    val lines = inputStream.bufferedReader().readLines()
                    // Skip header and empty lines
                    lines.drop(1).forEach { line ->
                        if (line.isBlank()) return@forEach

                        val fields = parseCsvLine(line)
                        if (fields.size < 4) {
                            skippedCount++
                            return@forEach
                        }

                        val companyName = fields.getOrNull(0).orEmpty().trim()
                        val jobUrl = fields.getOrNull(1).orEmpty().trim()
                        val jobDescription = fields.getOrNull(2).orEmpty()
                        val statusText = fields.getOrNull(3).orEmpty().trim()
                        val timestampText = fields.getOrNull(4).orEmpty().trim()

                        if (companyName.isBlank() || jobUrl.isBlank()) {
                            skippedCount++
                            return@forEach
                        }

                        val status = runCatching { JobStatus.valueOf(statusText) }
                            .getOrNull() ?: JobStatus.SAVED

                        // Parse timestamp: new format -> legacy date -> legacy date-time -> unix
                        val timestamp = try {
                            dateFormat.parse(timestampText)?.time ?: System.currentTimeMillis()
                        } catch (e: Exception) {
                            try {
                                legacyDateFormat.parse(timestampText)?.time ?: System.currentTimeMillis()
                            } catch (e2: Exception) {
                                try {
                                    legacyDateTimeFormat.parse(timestampText)?.time ?: System.currentTimeMillis()
                                } catch (e3: Exception) {
                                    timestampText.toLongOrNull() ?: System.currentTimeMillis()
                                }
                            }
                        }

                        // Check if job already exists
                        val existing = dao.getJobByUrl(jobUrl)
                        val job = JobEntity(
                            id = existing?.id ?: 0,
                            companyName = companyName,
                            jobUrl = jobUrl,
                            jobDescription = jobDescription,
                            status = status,
                            timestamp = timestamp
                        )
                        dao.upsertJob(job)
                        importedCount++
                    }
                }
                _message.value = if (skippedCount > 0) {
                    "✅ Imported $importedCount job(s), skipped $skippedCount"
                } else {
                    "✅ Imported $importedCount job(s) from CSV"
                }
            } catch (e: Exception) {
                _message.value = "❌ Import failed: ${e.message}"
            }
        }
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(",") || value.contains("\"") ||
                          value.contains("\n") || value.contains("\r")
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    val nextIsQuote = i + 1 < line.length && line[i + 1] == '"'
                    if (nextIsQuote) {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
