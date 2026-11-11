package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.portal.model.PortalTask
import com.example.ui.StoryTestViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * StoryBot home: is the phone ready, what is it posting right now, and today's pages. Everything else
 * (registration, accounts app, videos, popup and safety words) is in Settings.
 */
@Composable
fun HomeScreen(
    viewModel: StoryTestViewModel,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val isServiceEnabled by viewModel.isServiceEnabled.collectAsState()
    val portalState by viewModel.portalState.collectAsState()
    val liveRunState by viewModel.liveRunState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkServiceStatus()
    }

    val allTasks = portalState.workSlots.flatMap { it.tasks }
    val currentTask = allTasks.firstOrNull { it.uuid == liveRunState.taskUuid }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("StoryBot", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = portalState.deviceName ?: "Not connected to the portal",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            val ready = portalState.isRegistered && isServiceEnabled
            StatusChip(
                text = when {
                    !portalState.isRegistered -> "Not connected"
                    !isServiceEnabled -> "Needs setup"
                    else -> "Online"
                },
                color = if (ready) NeonGreen else AmberWarning
            )
        }

        // Setup checklist: only shown while something is missing
        if (!isServiceEnabled || !portalState.isRegistered) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.10f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Finish setup", color = AmberWarning, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (!isServiceEnabled) {
                        Text("Turn on StoryBot in Accessibility so it can tap for you.", color = TextPrimary, fontSize = 13.sp)
                        Button(
                            onClick = { viewModel.openAccessibilitySettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                            modifier = Modifier.fillMaxWidth().testTag("open_accessibility_btn")
                        ) { Text("Open Accessibility", color = Color.Black, fontWeight = FontWeight.Bold) }
                    }
                    if (!portalState.isRegistered) {
                        Text("Register this phone with the code from the portal.", color = TextPrimary, fontSize = 13.sp)
                        Button(
                            onClick = onOpenSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                            modifier = Modifier.fillMaxWidth().testTag("go_register_btn")
                        ) { Text("Register in Settings", color = Color.Black, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        // Posting
        if (portalState.isRegistered) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (portalState.isPostingWork) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NeonGreen)
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = if (portalState.isPostingWork) "Posting" else "Waiting for work",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (currentTask != null && liveRunState.isRunning) {
                        Column {
                            Text(
                                text = "Slot ${currentTask.slotNumber} · ${currentTask.pageName}",
                                color = NeonGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            if (liveRunState.currentStepName.isNotBlank()) {
                                Text(friendlyStep(liveRunState.currentStepName), color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    } else if (!portalState.postingStatusMessage.isNullOrBlank()) {
                        Text(portalState.postingStatusMessage ?: "", color = TextSecondary, fontSize = 13.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Post automatically", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Starts by itself when a campaign starts in the portal.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = portalState.autoPostEnabled,
                            onCheckedChange = { viewModel.setAutoPost(it) },
                            modifier = Modifier.testTag("auto_post_switch")
                        )
                    }

                    if (portalState.isPostingWork) {
                        OutlinedButton(
                            onClick = { viewModel.stopPortalPosting() },
                            modifier = Modifier.fillMaxWidth().testTag("stop_posting_btn")
                        ) { Text("Stop", color = CoralRed, fontWeight = FontWeight.Bold) }
                    } else {
                        Button(
                            onClick = { viewModel.postAllWorkNow() },
                            enabled = isServiceEnabled,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            modifier = Modifier.fillMaxWidth().testTag("post_now_btn")
                        ) { Text("Check and post now", color = Color.Black, fontWeight = FontWeight.Bold) }
                    }

                    if (portalState.pendingReports > 0) {
                        Text(
                            "${portalState.pendingReports} report(s) waiting for internet. They are sent automatically.",
                            color = AmberWarning,
                            fontSize = 12.sp
                        )
                    }
                    if (!portalState.lastError.isNullOrBlank()) {
                        Text(portalState.lastError ?: "", color = CoralRed, fontSize = 12.sp)
                    }
                }
            }

            // Today's pages
            Text("Pages", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (allTasks.isEmpty()) {
                Text(
                    "Nothing to post right now. Start a campaign in the portal.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            } else {
                for (slot in portalState.workSlots.sortedBy { it.slotNumber }) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Slot ${slot.slotNumber}${if (!slot.accountLabel.isNullOrBlank()) " · ${slot.accountLabel}" else ""}",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            for (task in slot.tasks) {
                                TaskRow(task)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun TaskRow(task: PortalTask) {
    val (label, color) = friendlyStatus(task)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(task.pageName.ifEmpty { "Page" }, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val detail = task.localError ?: (if (task.modelCode.isNotBlank()) "Video: ${task.modelCode}" else null)
            if (detail != null) {
                Text(detail, color = if (task.localError != null) CoralRed else TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Short, plain status for a page. */
private fun friendlyStatus(task: PortalTask): Pair<String, Color> {
    val status = (task.localStatus ?: task.status).uppercase()
    return when {
        status == "OK" || status == "SHARED" -> "Shared" to NeonGreen
        status == "RUNNING" -> "Posting" to NeonGreen
        status == "FAILED" -> "Failed" to CoralRed
        status == "NEEDS_PERSON" -> "Needs you" to AmberWarning
        status.startsWith("CANCELLED") -> "Cancelled" to CoralRed
        status == "WAITING" -> "Scheduled" to TextSecondary
        status == "SKIPPED" -> "Skipped" to TextSecondary
        else -> "Waiting" to TextSecondary
    }
}

/** "adding_link" -> "Adding link". Script step summaries are shown as they are. */
private fun friendlyStep(step: String): String =
    if (step.contains('(')) step else step.replace('_', ' ').replaceFirstChar { it.uppercase() }
