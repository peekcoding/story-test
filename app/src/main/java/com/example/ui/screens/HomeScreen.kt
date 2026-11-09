package com.example.ui.screens

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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.StoryTestViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderDark
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    viewModel: StoryTestViewModel,
    onNavigateToRun: () -> Unit,
    onNavigateToScripts: () -> Unit,
    onNavigateToCaptures: () -> Unit,
    onNavigateToSafety: () -> Unit,
    onNavigateToPortal: () -> Unit = {}
) {
    val context = LocalContext.current
    val isServiceEnabled by viewModel.isServiceEnabled.collectAsState()
    val scripts by viewModel.scripts.collectAsState()
    val captures by viewModel.captures.collectAsState()
    val safetyRules by viewModel.safetyRules.collectAsState()
    val liveRunState by viewModel.liveRunState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkServiceStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Story Test",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Automated hands-free posting & inspector",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isServiceEnabled) NeonGreen.copy(alpha = 0.2f) else CoralRed.copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isServiceEnabled) "SERVICE ACTIVE" else "SERVICE OFF",
                    color = if (isServiceEnabled) NeonGreen else CoralRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        // Accessibility Service Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("service_status_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isServiceEnabled) NeonGreen.copy(alpha = 0.5f) else AmberWarning.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isServiceEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Service Status",
                        tint = if (isServiceEnabled) NeonGreen else AmberWarning,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isServiceEnabled) "Accessibility Service Enabled" else "Accessibility Service Required",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (isServiceEnabled)
                                "Overlay & gesture automation are ready"
                            else
                                "Enable 'Story Test' in Settings to allow automated gestures and screen inspection",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }

                if (!isServiceEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { viewModel.openAccessibilitySettings(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enable_service_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning, contentColor = androidx.compose.ui.graphics.Color.Black),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Accessibility Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Live execution banner if running
        if (liveRunState.isRunning) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_run_banner"),
                colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.15f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AUTOMATION RUNNING",
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Page: ${liveRunState.currentPage} (Step ${liveRunState.currentStepNumber}/${liveRunState.totalSteps})",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = liveRunState.currentStepName,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = { viewModel.stopRun() },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Stop", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Web Portal Status Card
        val portalState by viewModel.portalState.collectAsState()
        Card(
            onClick = onNavigateToPortal,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_portal_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (portalState.isRegistered) NeonGreen.copy(alpha = 0.15f) else DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Portal",
                            tint = if (portalState.isRegistered) NeonGreen else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Web Portal",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (portalState.isRegistered) NeonGreen else AmberWarning)
                            )
                        }
                        Text(
                            text = if (portalState.isRegistered) {
                                "${portalState.deviceName ?: "Phone"} • Online"
                            } else {
                                "Not registered • Tap to enroll"
                            },
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        if (portalState.lastHeartbeatTime != null) {
                            Text(
                                text = "Last beat: ${portalState.lastHeartbeatTime}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Button(
                    onClick = onNavigateToPortal,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (portalState.isRegistered) DarkSurfaceVariant else NeonGreen
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (portalState.isRegistered) "Manage" else "Enroll",
                        color = if (portalState.isRegistered) TextPrimary else androidx.compose.ui.graphics.Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Inspector Overlay Explainer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Inspector Overlay",
                        tint = NeonGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Floating Inspector & Stop Overlay",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A compact floating pill stays on top of other apps. Tap the Camera button at any time to capture node IDs, text, and screenshot. Tap the Red Stop button to abort runs instantly.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
        }

        // Quick Navigation Grid
        Text(
            text = "Automated Testing Hub",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Run Card
            Card(
                onClick = onNavigateToRun,
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_card_run"),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Run Batch", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    Text("Execute story scripts", color = TextSecondary, fontSize = 12.sp)
                }
            }

            // Scripts Card
            Card(
                onClick = onNavigateToScripts,
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_card_scripts"),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Scripts (${scripts.size})", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    Text("Build & edit steps", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Captures Card
            Card(
                onClick = onNavigateToCaptures,
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_card_captures"),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Captures (${captures.size})", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    Text("Inspect trees & shots", color = TextSecondary, fontSize = 12.sp)
                }
            }

            // Safety Card
            Card(
                onClick = onNavigateToSafety,
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_card_safety"),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Safety (${safetyRules.count { it.isEnabled }})", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    Text("Human guard rules", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
private val CyanAccent = androidx.compose.ui.graphics.Color(0xFF00E5FF)
