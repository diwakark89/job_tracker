package com.thewalkersoft.linkedin_job_tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val companyName: String,
    val jobUrl: String,
    val jobDescription: String,
    val jobTitle: String = "", // Job title from LinkedIn
    val status: JobStatus = JobStatus.SAVED,
    val timestamp: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis() // Track last modification for sync
)

enum class JobStatus {
    SAVED,
    APPLIED,
    INTERVIEWING,
    OFFER,
    RESUME_REJECTED,
    INTERVIEW_REJECTED
}

fun JobStatus.displayName(): String {
    return when (this) {
        JobStatus.RESUME_REJECTED -> "RESUME-REJECTED"
        JobStatus.INTERVIEW_REJECTED -> "INTERVIEW-REJECTED"
        else -> name.replace("_", " ")
    }
}

fun parseJobStatus(value: String): JobStatus {
    val normalized = value.trim().uppercase().replace("-", "_")
    return when (normalized) {
        "REJECTED" -> JobStatus.RESUME_REJECTED
        else -> runCatching { JobStatus.valueOf(normalized) }.getOrDefault(JobStatus.SAVED)
    }
}
