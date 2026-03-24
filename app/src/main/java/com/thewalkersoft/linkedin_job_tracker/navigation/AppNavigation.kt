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
    allJobs: List<JobEntity>,
    searchQuery: String,
    statusFilter: JobStatus?,
    isScraping: Boolean,
    message: String?,
    cloudHealth: String,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterChange: (JobStatus?) -> Unit,
    onStatusChange: (JobEntity, JobStatus) -> Unit,
    onDeleteJob: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onEditJob: (JobEntity, String, String, String, String) -> Unit,
    onRestoreJob: (JobEntity) -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
    // Diagnostics – debug-only; default no-ops keep non-debug callers unchanged
    diagnosticsStep: Int = 0,
    onRequestDiagnosticsReset: () -> Unit = {},
    onConfirmDiagnosticsStep1: () -> Unit = {},
    onConfirmCancelWorker: () -> Unit = {},
    onDeclineCancelWorker: () -> Unit = {},
    onDismissDiagnostics: () -> Unit = {}
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
                allJobs = allJobs,
                searchQuery = searchQuery,
                statusFilter = statusFilter,
                isScraping = isScraping,
                message = message,
                cloudHealth = cloudHealth,
                onSearchQueryChange = onSearchQueryChange,
                onStatusFilterChange = onStatusFilterChange,
                onStatusChange = onStatusChange,
                onDeleteJob = onDeleteJob,
                onEditJob = onEditJob,
                onRestoreJob = onRestoreJob,
                onMessageShown = onMessageShown,
                diagnosticsStep = diagnosticsStep,
                onRequestDiagnosticsReset = onRequestDiagnosticsReset,
                onConfirmDiagnosticsStep1 = onConfirmDiagnosticsStep1,
                onConfirmCancelWorker = onConfirmCancelWorker,
                onDeclineCancelWorker = onDeclineCancelWorker,
                onDismissDiagnostics = onDismissDiagnostics,
                onJobClick = { jobId ->
                    navController.navigate(Screen.JobDetails.createRoute(jobId))
                }
            )
        }

        // Job Details Screen
        composable(
            route = Screen.JobDetails.route,
            arguments = listOf(
                navArgument("jobId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: return@composable
            val job = allJobs.firstOrNull { it.id == jobId }

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
                    onEdit = { companyName, jobUrl, jobTitle, jobDescription ->
                        onEditJob(job, companyName, jobUrl, jobTitle, jobDescription)
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
        val sampleJobs = listOf(
            JobEntity(
                id = "sample-1",
                companyName = "Google",
                jobUrl = "https://careers.google.com",
                jobDescription = "Software Engineer",
                status = JobStatus.APPLIED,
                timestamp = System.currentTimeMillis()
            ),
            JobEntity(
                id = "sample-2",
                companyName = "Meta",
                jobUrl = "https://www.metacareers.com/",
                jobDescription = "Product Manager",
                status = JobStatus.SAVED,
                timestamp = System.currentTimeMillis()
            )
        )
        AppNavigation(
            navController = rememberNavController(),
            jobs = sampleJobs,
            allJobs = sampleJobs,
            searchQuery = "",
            statusFilter = null,
            isScraping = false,
            message = null,
            cloudHealth = "Offline",
            onSearchQueryChange = { _ -> },
            onStatusFilterChange = { _ -> },
            onStatusChange = { _, _ -> },
            onDeleteJob = { _ -> },
            onOpenUrl = { _ -> },
            onEditJob = { _, _, _, _, _ -> },
            onRestoreJob = { _ -> },
            onMessageShown = {}
        )
    }
}
