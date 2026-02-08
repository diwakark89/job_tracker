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
    val status: JobStatus = JobStatus.SAVED,
    val timestamp: Long = System.currentTimeMillis()
)

enum class JobStatus {
    SAVED,
    APPLIED,
    INTERVIEWING,
    OFFER,
    REJECTED
}

