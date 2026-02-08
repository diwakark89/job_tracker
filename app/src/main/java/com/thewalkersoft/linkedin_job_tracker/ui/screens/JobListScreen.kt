package com.thewalkersoft.linkedin_job_tracker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.ui.components.EditJobDialog
import com.thewalkersoft.linkedin_job_tracker.ui.components.JobCard
import com.thewalkersoft.linkedin_job_tracker.ui.components.LoadingOverlay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListScreen(
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
    onRestoreJob: (JobEntity) -> Unit = {},
    onMessageShown: () -> Unit = {},
    onJobClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var isStatusMenuOpen by remember { mutableStateOf(false) }
    var isMoreMenuOpen by remember { mutableStateOf(false) }
    var pendingDeleteJob by remember { mutableStateOf<JobEntity?>(null) }
    var editingJob by remember { mutableStateOf<JobEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val statusOptions = remember { listOf<JobStatus?>(null) + JobStatus.entries }

    // CSV Export/Import Launchers
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let(onExportCsv)
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onImportCsv)
    }

    // Show message when it's available
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            onMessageShown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onSearch = { isSearchActive = false },
                            expanded = isSearchActive,
                            onExpandedChange = { isSearchActive = it },
                            placeholder = {
                                Text(
                                    text = if (statusFilter == null) {
                                        "Search by company name"
                                    } else {
                                        "Search in ${statusFilter.name.replace("_", " ")}".lowercase()
                                            .replaceFirstChar { it.uppercase() }
                                    }
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            trailingIcon = {
                                Row {
                                    // Filter Icon
                                    Box {
                                        IconButton(onClick = { isStatusMenuOpen = true }) {
                                            Icon(
                                                imageVector = Icons.Default.Menu,
                                                contentDescription = "Filter by status",
                                                tint = if (statusFilter != null) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = isStatusMenuOpen,
                                            onDismissRequest = { isStatusMenuOpen = false }
                                        ) {
                                            statusOptions.forEach { status ->
                                                val label = status?.name?.replace("_", " ") ?: "All Statuses"
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        onStatusFilterChange(status)
                                                        isStatusMenuOpen = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // More Options Icon
                                    Box {
                                        IconButton(onClick = { isMoreMenuOpen = true }) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "More options"
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = isMoreMenuOpen,
                                            onDismissRequest = { isMoreMenuOpen = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("📤 Export CSV") },
                                                onClick = {
                                                    exportCsvLauncher.launch("linkedin_jobs_export.csv")
                                                    isMoreMenuOpen = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("📥 Import CSV") },
                                                onClick = {
                                                    importCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "*/*"))
                                                    isMoreMenuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    },
                    expanded = isSearchActive,
                    onExpandedChange = { isSearchActive = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Search suggestions can be added here if needed
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            if (jobs.isEmpty() && searchQuery.isEmpty() && statusFilter == null) {
                EmptyState(modifier = Modifier.padding(paddingValues))
            } else if (jobs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    val statusLabel = statusFilter?.name?.replace("_", " ")
                    val messageText = if (statusLabel == null) {
                        "No jobs found for \"$searchQuery\""
                    } else {
                        "No jobs found for \"$searchQuery\" in $statusLabel"
                    }
                    Text(
                        text = messageText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(jobs, key = { it.id }) { job ->
                        SwipeToDismissBox(
                            job = job,
                            onRequestDelete = { pendingDeleteJob = job },
                            onStatusChange = { status -> onStatusChange(job, status) },
                            onOpenUrl = onOpenUrl,
                            onJobClick = { onJobClick(job.id) }
                        )
                    }
                }
            }
        }

        if (pendingDeleteJob != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteJob = null },
                title = { Text("Delete job?") },
                text = { Text("This will remove the job from your tracker.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val job = pendingDeleteJob
                            if (job != null) {
                                onDeleteJob(job.id)
                                coroutineScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Job deleted",
                                        actionLabel = "Undo",
                                        withDismissAction = true
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onRestoreJob(job)
                                    }
                                }
                            }
                            pendingDeleteJob = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteJob = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Edit Job Dialog
        editingJob?.let { job ->
            EditJobDialog(
                job = job,
                onDismiss = { editingJob = null },
                onSave = { companyName, jobUrl, jobDescription ->
                    onEditJob(job, companyName, jobUrl, jobDescription)
                    editingJob = null
                }
            )
        }

        LoadingOverlay(isLoading = isScraping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissBox(
    job: JobEntity,
    onRequestDelete: () -> Unit,
    onStatusChange: (JobStatus) -> Unit,
    onOpenUrl: (String) -> Unit,
    onJobClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onRequestDelete()
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        JobCard(
            job = job,
            onStatusChange = onStatusChange,
            onOpenUrl = onOpenUrl,
            onJobClick = onJobClick
        )
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "📋",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Jobs Tracked Yet",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Share a LinkedIn job posting to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
