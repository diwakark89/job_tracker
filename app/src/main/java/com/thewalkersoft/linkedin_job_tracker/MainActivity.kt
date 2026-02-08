package com.thewalkersoft.linkedin_job_tracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.navigation.AppNavigation
import com.thewalkersoft.linkedin_job_tracker.ui.screens.JobListScreen
import com.thewalkersoft.linkedin_job_tracker.ui.theme.LinkedIn_Job_TrackerTheme
import com.thewalkersoft.linkedin_job_tracker.viewmodel.JobViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: JobViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle shared intent
        viewModel.handleIntent(intent)

        setContent {
            LinkedIn_Job_TrackerTheme {
                val navController = rememberNavController()
                val jobs by viewModel.jobs.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()
                val statusFilter by viewModel.statusFilter.collectAsState()
                val isScraping by viewModel.isScraping.collectAsState()
                val message by viewModel.message.collectAsState()

                AppNavigation(
                    navController = navController,
                    jobs = jobs,
                    searchQuery = searchQuery,
                    statusFilter = statusFilter,
                    isScraping = isScraping,
                    message = message,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onStatusFilterChange = viewModel::onStatusFilterChange,
                    onExportCsv = viewModel::exportJobsToCsv,
                    onImportCsv = viewModel::importJobsFromCsv,
                    onStatusChange = { job, status -> viewModel.updateJobStatus(job, status) },
                    onDeleteJob = viewModel::deleteJob,
                    onOpenUrl = ::openUrl,
                    onEditJob = { job, companyName, jobUrl, jobDescription ->
                        viewModel.updateJob(job, companyName, jobUrl, jobDescription)
                    },
                    onRestoreJob = viewModel::restoreJob,
                    onMessageShown = viewModel::clearMessage,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleIntent(intent)
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}

@Preview(showBackground = true)
@Composable
private fun JobListScreenPreview() {
    LinkedIn_Job_TrackerTheme {
        val sampleJobs = listOf(
            JobEntity(
                id = 1,
                companyName = "Contoso",
                jobUrl = "https://www.linkedin.com/jobs/view/123456",
                jobDescription = "Sample job description for preview.",
                status = JobStatus.INTERVIEWING,
                timestamp = System.currentTimeMillis()
            ),
            JobEntity(
                id = 2,
                companyName = "Fabrikam",
                jobUrl = "https://www.linkedin.com/jobs/view/654321",
                jobDescription = "Another sample description for preview.",
                status = JobStatus.SAVED,
                timestamp = System.currentTimeMillis() - 86_400_000
            ),
            JobEntity(
                id = 3,
                companyName = "Fabrikam",
                jobUrl = "https://www.linkedin.com/jobs/view/654324",
                jobDescription = "Another sample description for preview.",
                status = JobStatus.OFFER,
                timestamp = System.currentTimeMillis() - 86_400_000
            ),
            JobEntity(
                id = 4,
                companyName = "Fabrikam",
                jobUrl = "https://www.linkedin.com/jobs/view/654325",
                jobDescription = "Another sample description for preview.",
                status = JobStatus.APPLIED,
                timestamp = System.currentTimeMillis() - 86_400_000
            ),
            JobEntity(
                id = 5,
                companyName = "Fabrikam",
                jobUrl = "https://www.linkedin.com/jobs/view/654321",
                jobDescription = "Another sample description for preview.",
                status = JobStatus.REJECTED,
                timestamp = System.currentTimeMillis() - 86_400_000
            )
        )

        JobListScreen(
            jobs = sampleJobs,
            searchQuery = "",
            statusFilter = null,
            isScraping = false,
            message = null,
            onSearchQueryChange = {},
            onStatusFilterChange = {},
            onExportCsv = {},
            onImportCsv = {},
            onStatusChange = { _, _ -> },
            onDeleteJob = {},
            onOpenUrl = {},
            onEditJob = { _, _, _, _ -> },
            modifier = Modifier.fillMaxSize()
        )
    }
}
