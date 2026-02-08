package com.thewalkersoft.linkedin_job_tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun JobCard(
    job: JobEntity,
    onStatusChange: (JobStatus) -> Unit,
    onJobClick: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showStatusMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onJobClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.companyName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = formatTimestamp(job.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Chip with Dropdown
                Box {
                    StatusChip(
                        status = job.status,
                        onClick = { showStatusMenu = true }
                    )
                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false }
                    ) {
                        JobStatus.entries.forEach { status ->
                            DropdownMenuItem(
                                text = { Text(status.name) },
                                onClick = {
                                    onStatusChange(status)
                                    showStatusMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Description (collapsed or expanded)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = job.jobDescription,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "LinkedIn URL",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenUrl(job.jobUrl) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Open in Browser",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Open link",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(
    status: JobStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ...existing code...
    val containerColor = when (status) {
        JobStatus.OFFER -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobOfferGreen.copy(alpha = 0.35f)
        JobStatus.REJECTED -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobRejectedRed.copy(alpha = 0.35f)
        JobStatus.INTERVIEWING -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobInterviewingYellow.copy(alpha = 0.35f)
        JobStatus.APPLIED -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobAppliedBlue.copy(alpha = 0.35f)
        JobStatus.SAVED -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobSavedGray.copy(alpha = 0.35f)
    }

    val contentColor = Color.White

    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(status.name) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = contentColor
        )
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
