package com.thewalkersoft.linkedin_job_tracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.ui.screens.JobDetailsMissingScreen
import com.thewalkersoft.linkedin_job_tracker.ui.screens.JobDetailsScreen
import com.thewalkersoft.linkedin_job_tracker.ui.screens.JobListScreen
import com.thewalkersoft.linkedin_job_tracker.ui.theme.LinkedIn_Job_TrackerTheme

@Composable
fun AppNavigation(
    navController: NavHostController,
    jobs: List<JobEntity>,
    searchQuery: String,
    statusFilter: JobStatus?,
    isScraping: Boolean,
    message: String?,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterChange: (JobStatus?) -> Unit,
    onExportCsv: (android.net.Uri) -> Unit,
    onImportCsv: (android.net.Uri) -> Unit,
    onStatusChange: (JobEntity, JobStatus) -> Unit,
    onDeleteJob: (Long) -> Unit,
    onOpenUrl: (String) -> Unit,
    onEditJob: (JobEntity, String, String, String) -> Unit,
    onRestoreJob: (JobEntity) -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.JobList.route,
        modifier = modifier
    ) {
        // Job List Screen
        composable(Screen.JobList.route) {
            JobListScreen(
                jobs = jobs,
                searchQuery = searchQuery,
                statusFilter = statusFilter,
                isScraping = isScraping,
                message = message,
                onSearchQueryChange = onSearchQueryChange,
                onStatusFilterChange = onStatusFilterChange,
                onExportCsv = onExportCsv,
                onImportCsv = onImportCsv,
                onStatusChange = onStatusChange,
                onDeleteJob = onDeleteJob,
                onEditJob = onEditJob,
                onRestoreJob = onRestoreJob,
                onMessageShown = onMessageShown,
                onJobClick = { jobId ->
                    navController.navigate(Screen.JobDetails.createRoute(jobId))
                }
            )
        }

        // Job Details Screen
        composable(
            route = Screen.JobDetails.route,
            arguments = listOf(
                navArgument("jobId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getLong("jobId") ?: return@composable
            val job = jobs.firstOrNull { it.id == jobId }

            if (job == null) {
                JobDetailsMissingScreen(
                    onNavigateBack = { navController.navigateUp() }
                )
            } else {
                JobDetailsScreen(
                    job = job,
                    onNavigateBack = { navController.navigateUp() },
                    onStatusChange = { newStatus ->
                        onStatusChange(job, newStatus)
                    },
                    onOpenUrl = onOpenUrl,
                    onEdit = { companyName, jobUrl, jobDescription ->
                        onEditJob(job, companyName, jobUrl, jobDescription)
                    },
                    onDelete = {
                        onDeleteJob(job.id)
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun AppNavigationPreview() {
    LinkedIn_Job_TrackerTheme {
        AppNavigation(
            navController = rememberNavController(),
            jobs = listOf(
                JobEntity(
                    id = 1,
                    companyName = "Google",
                    jobUrl = "https://careers.google.com",
                    jobDescription = "Software Engineer",
                    status = JobStatus.APPLIED,
                    timestamp = System.currentTimeMillis()
                ),
                JobEntity(
                    id = 2,
                    companyName = "Meta",
                    jobUrl = "https://www.metacareers.com/",
                    jobDescription = "Product Manager",
                    status = JobStatus.SAVED,
                    timestamp = System.currentTimeMillis()
                )
            ),
            searchQuery = "",
            statusFilter = null,
            isScraping = false,
            message = null,
            onSearchQueryChange = { _ -> },
            onStatusFilterChange = { _ -> },
            onExportCsv = { _ -> },
            onImportCsv = { _ -> },
            onStatusChange = { _, _ -> },
            onDeleteJob = { _ -> },
            onOpenUrl = { _ -> },
            onEditJob = { _, _, _, _ -> },
            onRestoreJob = { _ -> },
            onMessageShown = {}
        )
    }
}
