package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RunResult
import com.example.data.model.RunStatus
import com.example.data.model.ScriptEntity
import com.example.ui.StoryTestViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderDark
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(
    viewModel: StoryTestViewModel,
    preselectedScriptId: Long? = null
) {
    val context = LocalContext.current
    val scripts by viewModel.scripts.collectAsState()
    val isServiceEnabled by viewModel.isServiceEnabled.collectAsState()
    val liveRunState by viewModel.liveRunState.collectAsState()
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()

    var selectedScript by remember { mutableStateOf<ScriptEntity?>(null) }
    var scriptDropdownExpanded by remember { mutableStateOf(false) }

    var pageNamesText by remember { mutableStateOf("TechTrends\nFitnessDaily\nTravelVibes") }
    var linkText by remember { mutableStateOf("https://pageshare.org/test-story") }

    // Preselect script
    LaunchedEffect(scripts, preselectedScriptId) {
        if (selectedScript == null && scripts.isNotEmpty()) {
            selectedScript = if (preselectedScriptId != null) {
                scripts.find { it.id == preselectedScriptId } ?: scripts.first()
            } else {
                scripts.find { it.isTemplate } ?: scripts.first()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Header
        Text(
            text = "Run Automation",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
        Text(
            text = "Execute hands-free tests across target pages",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )

        // Safety interruption alert banner
        AnimatedVisibility(visible = liveRunState.isPausedNeedsPerson) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("safety_alert_banner"),
                colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.2f)),
                border = androidx.compose.foundation.BorderStroke(2.dp, AmberWarning),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Alert",
                        tint = AmberWarning,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Needs a person",
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning,
                            fontSize = 18.sp
                        )
                        Text(
                            text = liveRunState.needsPersonReason ?: "Security check detected. Automation paused.",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Resolve the security prompt manually on device before continuing.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { viewModel.stopRun() }) {
                        Icon(Icons.Default.Cancel, contentDescription = "Dismiss", tint = CoralRed)
                    }
                }
            }
        }

        // Live execution monitor card
        if (liveRunState.isRunning) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_progress_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(NeonGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "RUNNING LIVE",
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = String.format("%02d:%02d", liveRunState.elapsedSeconds / 60, liveRunState.elapsedSeconds % 60),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Page ${liveRunState.currentPageIndex + 1} of ${liveRunState.totalPages}: ${liveRunState.currentPage}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Step ${liveRunState.currentStepNumber} of ${liveRunState.totalSteps}:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = liveRunState.currentStepName,
                        color = NeonGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    val progress = if (liveRunState.totalSteps > 0) {
                        liveRunState.currentStepNumber.toFloat() / liveRunState.totalSteps.toFloat()
                    } else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NeonGreen,
                        trackColor = DarkSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.stopRun() },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stop_run_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Automation", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5-second countdown active banner
        if (countdownSeconds != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("countdown_banner"),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(2.dp, NeonGreen),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("STARTING RUN IN", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${countdownSeconds}s",
                        color = NeonGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 44.sp
                    )
                    Text("Switch to target screen or let app launch automatically", color = TextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.cancelCountdown() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed)
                    ) {
                        Text("Cancel Countdown")
                    }
                }
            }
        }

        // Configuration Form
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Test Configuration",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                // Script Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = scriptDropdownExpanded,
                    onExpandedChange = { scriptDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedScript?.name ?: "No scripts available",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Choose Script") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = scriptDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("script_selector_field")
                    )
                    ExposedDropdownMenu(
                        expanded = scriptDropdownExpanded,
                        onDismissRequest = { scriptDropdownExpanded = false }
                    ) {
                        scripts.forEach { script ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(script.name, fontWeight = FontWeight.SemiBold)
                                        if (script.description.isNotBlank()) {
                                            Text(script.description, fontSize = 11.sp, color = TextSecondary)
                                        }
                                    }
                                },
                                onClick = {
                                    selectedScript = script
                                    scriptDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Page Names Input
                OutlinedTextField(
                    value = pageNamesText,
                    onValueChange = { pageNamesText = it },
                    label = { Text("Page Names (one per line)") },
                    placeholder = { Text("Page 1\nPage 2\nPage 3") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("page_names_field"),
                    supportingText = {
                        Text("Replaces {{PAGE_NAME}} in script steps once per page in order", color = TextSecondary, fontSize = 11.sp)
                    }
                )

                // Story Link Input
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    label = { Text("Link URL") },
                    placeholder = { Text("https://example.com/promo") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("link_url_field"),
                    supportingText = {
                        Text("Replaces {{LINK}} in script steps", color = TextSecondary, fontSize = 11.sp)
                    }
                )

                // Start in 5 seconds button
                Button(
                    onClick = {
                        selectedScript?.let { script ->
                            viewModel.startCountdownAndRun(script, pageNamesText, linkText, countdownDuration = 5)
                        }
                    },
                    enabled = isServiceEnabled && !liveRunState.isRunning && countdownSeconds == null && selectedScript != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("start_in_5s_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = androidx.compose.ui.graphics.Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start in 5 seconds", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                if (!isServiceEnabled) {
                    Text(
                        text = "Cannot start: Accessibility Service must be enabled in Home settings.",
                        color = CoralRed,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Results Section
        if (liveRunState.results.isNotEmpty()) {
            ResultsSection(
                results = liveRunState.results,
                scriptName = liveRunState.scriptName.ifEmpty { selectedScript?.name ?: "Story Test" },
                onShare = {
                    val report = viewModel.formatResultsAsText(liveRunState.results, liveRunState.scriptName)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Story Test Automation Results")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Results").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                },
                onCopy = {
                    val report = viewModel.formatResultsAsText(liveRunState.results, liveRunState.scriptName)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Story Test Results", report))
                    Toast.makeText(context, "Results copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ResultsSection(
    results: List<RunResult>,
    scriptName: String,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("results_table_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Execution Results",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "${results.count { it.status == RunStatus.OK }} / ${results.size} passed",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Row {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Results", tint = TextPrimary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share Results", tint = NeonGreen, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Results List / Table
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                results.forEach { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = when (result.status) {
                                    RunStatus.OK -> Icons.Default.CheckCircle
                                    RunStatus.FAILED -> Icons.Default.Error
                                    RunStatus.PAUSED_NEEDS_PERSON -> Icons.Default.Warning
                                    RunStatus.CANCELLED -> Icons.Default.Cancel
                                },
                                contentDescription = result.status.name,
                                tint = when (result.status) {
                                    RunStatus.OK -> NeonGreen
                                    RunStatus.FAILED -> CoralRed
                                    RunStatus.PAUSED_NEEDS_PERSON -> AmberWarning
                                    RunStatus.CANCELLED -> TextSecondary
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = result.pageName,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                if (result.failedStepName != null) {
                                    Text(
                                        text = "Failed on: ${result.failedStepName}",
                                        color = CoralRed,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                } else if (result.errorDetails != null) {
                                    Text(
                                        text = result.errorDetails,
                                        color = AmberWarning,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when (result.status) {
                                            RunStatus.OK -> NeonGreen.copy(alpha = 0.2f)
                                            RunStatus.FAILED -> CoralRed.copy(alpha = 0.2f)
                                            RunStatus.PAUSED_NEEDS_PERSON -> AmberWarning.copy(alpha = 0.2f)
                                            RunStatus.CANCELLED -> BorderDark
                                        }
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = result.status.name,
                                    color = when (result.status) {
                                        RunStatus.OK -> NeonGreen
                                        RunStatus.FAILED -> CoralRed
                                        RunStatus.PAUSED_NEEDS_PERSON -> AmberWarning
                                        RunStatus.CANCELLED -> TextSecondary
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format("%.1fs", result.durationSeconds),
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
