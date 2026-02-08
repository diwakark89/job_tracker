package com.thewalkersoft.linkedin_job_tracker.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY timestamp DESC")
    fun getAllJobs(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs ORDER BY timestamp DESC")
    suspend fun getAllJobsOnce(): List<JobEntity>

    @Query("SELECT * FROM jobs WHERE companyName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchJobsByCompany(query: String): Flow<List<JobEntity>>

    @Upsert
    suspend fun upsertJob(job: JobEntity)

    @Query("DELETE FROM jobs WHERE id = :jobId")
    suspend fun deleteJob(jobId: Long)

    @Query("SELECT * FROM jobs WHERE jobUrl = :url LIMIT 1")
    suspend fun getJobByUrl(url: String): JobEntity?
}
