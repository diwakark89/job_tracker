package com.thewalkersoft.linkedin_job_tracker.data

import com.google.gson.annotations.SerializedName
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "jobs",
    indices = [Index(value = ["jobUrl"], unique = true)]
)
data class JobEntity(
    @PrimaryKey
    val id: String,
    val companyName: String,
    val jobUrl: String,
    val jobDescription: String,
    val jobTitle: String = "",
    val status: JobStatus = JobStatus.SAVED,
    /** Creation time in epoch millis. Serialised as "timestamp" for Phase 1 Supabase compat. */
    @SerializedName("timestamp")
    val createdAt: Long = System.currentTimeMillis(),
    /** Last-modified time in epoch millis. Serialised as "lastModified" for Phase 1 Supabase compat. */
    @SerializedName("lastModified")
    val updatedAt: Long = System.currentTimeMillis(),
    @SerializedName("is_deleted")
    val isDeleted: Boolean = false,
    val matchScore: Int? = null,
    val language: String = "English",
    val prepNotes: String? = null,
    val sourcePlatform: String? = null,
    val filterReason: String? = null
)

enum class JobStatus {
    SAVED,
    APPLIED,
    INTERVIEW,
    INTERVIEWING,
    OFFER,
    RESUME_REJECTED,
    INTERVIEW_REJECTED
}

fun JobStatus.displayName(): String {
    return when (this) {
        JobStatus.SAVED -> "Saved"
        JobStatus.APPLIED -> "Applied"
        JobStatus.INTERVIEW -> "Interview"
        JobStatus.INTERVIEWING -> "Interviewing"
        JobStatus.OFFER -> "Offer"
        JobStatus.RESUME_REJECTED -> "Resume-Rejected"
        JobStatus.INTERVIEW_REJECTED -> "Interview-Rejected"
    }
}

fun parseJobStatus(value: String): JobStatus {
    val normalized = value.trim().uppercase().replace("-", "_").replace(" ", "_")
    return when (normalized) {
        "REJECTED" -> JobStatus.RESUME_REJECTED
        else -> runCatching { JobStatus.valueOf(normalized) }.getOrDefault(JobStatus.SAVED)
    }
}
