package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.StoryTestViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PortalScreen(
    viewModel: StoryTestViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val portalState by viewModel.portalState.collectAsState()
    val isEnrolling by viewModel.isEnrolling.collectAsState()
    val fieldError by viewModel.enrollmentFieldError.collectAsState()

    var editingAddress by remember { mutableStateOf(false) }
    var addressInput by remember(portalState.portalAddress) { mutableStateOf(portalState.portalAddress) }
    var codeInput by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    // Battery optimization check
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isIgnoringBatteryOptimizations = remember(portalState) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Web Portal",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Device management & remote heartbeat",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            // Status chip
            val statusColor = if (portalState.isRegistered) NeonGreen else AmberWarning
            val statusBg = if (portalState.isRegistered) NeonGreen.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.15f)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(statusBg)
                    .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = if (portalState.isRegistered) "Enrolled" else "Not Registered",
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Portal Address Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Portal Address",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = { editingAddress = !editingAddress },
                        modifier = Modifier.size(28.dp).testTag("edit_portal_address_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit address",
                            tint = NeonGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (editingAddress) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("portal_address_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.updatePortalAddress(addressInput)
                            editingAddress = false
                        })
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            addressInput = portalState.portalAddress
                            editingAddress = false
                        }) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.updatePortalAddress(addressInput)
                                editingAddress = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            modifier = Modifier.testTag("save_portal_address_btn")
                        ) {
                            Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        text = portalState.portalAddress,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Registration Section
        if (!portalState.isRegistered) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Register This Phone",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Enter the 8-character code displayed on the web portal to link this device.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isLetterOrDigit() }.take(8).uppercase()
                            codeInput = filtered
                        },
                        label = { Text("Enrollment code (8 chars)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enrollment_code_input"),
                        singleLine = true,
                        isError = fieldError != null,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (codeInput.length == 8 && !isEnrolling) {
                                viewModel.enrollDevice(codeInput) { success ->
                                    if (success) codeInput = ""
                                }
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            errorBorderColor = CoralRed,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    if (fieldError != null) {
                        Text(
                            text = fieldError ?: "",
                            color = CoralRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("enrollment_error_text")
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.enrollDevice(codeInput) { success ->
                                if (success) codeInput = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("register_phone_button"),
                        enabled = codeInput.length == 8 && !isEnrolling,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            disabledContainerColor = DarkSurfaceVariant
                        )
                    ) {
                        if (isEnrolling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Registering...", color = Color.Black)
                        } else {
                            Text(
                                "Register this phone",
                                color = if (codeInput.length == 8) Color.Black else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // Registered Phone Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = portalState.deviceName ?: "Phone 01",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("portal_device_name")
                            )
                        }

                        IconButton(
                            onClick = { viewModel.triggerManualHeartbeat() },
                            modifier = Modifier.testTag("manual_heartbeat_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Send heartbeat now",
                                tint = NeonGreen
                            )
                        }
                    }

                    if (portalState.deviceUuid != null) {
                        Text(
                            text = "UUID: ${portalState.deviceUuid}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (portalState.registeredSinceTime != null) {
                        Text(
                            text = "Online since: ${portalState.registeredSinceTime}",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = if (portalState.lastHeartbeatTime != null) CoralRed else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Last heartbeat: ${portalState.lastHeartbeatTime ?: "Waiting for first heartbeat..."}",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = { viewModel.signOutPortal() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sign_out_portal_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CoralRed
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CoralRed.copy(alpha = 0.5f))
                    ) {
                        Text("Sign out of portal", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Model Videos Section
        val videoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_VIDEO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                viewModel.reportVideosNow()
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Model Videos",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                videoPermission
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                viewModel.reportVideosNow()
                            } else {
                                permissionLauncher.launch(videoPermission)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        modifier = Modifier.testTag("report_videos_now_btn"),
                        enabled = !portalState.isReportingVideos
                    ) {
                        if (portalState.isReportingVideos) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = NeonGreen,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scanning...", color = TextPrimary, fontSize = 12.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Report videos now", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (portalState.scannedVideos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.6f))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Create Movies/PageShare/<MODEL CODE>/ and put the model's video in it.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.testTag("no_model_folders_hint")
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        portalState.scannedVideos.forEach { video ->
                            val sizeMb = String.format(java.util.Locale.US, "%.1f MB", video.sizeBytes / (1024.0 * 1024.0))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = video.modelCode,
                                                color = NeonGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = sizeMb,
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Text(
                                            text = video.fileName,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Date: ${video.fileDateIso}",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Last report time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last report: ${portalState.lastVideosReportTime ?: "Never reported yet"}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("portal_last_videos_report_time")
                    )
                }

                // Unknown codes warnings from portal
                if (portalState.unknownModelCodes.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.testTag("unknown_model_codes_warnings")
                    ) {
                        portalState.unknownModelCodes.forEach { unknownCode ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Folder $unknownCode is not a model in the portal.",
                                    color = AmberWarning,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // Work Section
        // =====================================================================
        if (portalState.isRegistered) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Work & Campaigns",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (portalState.scriptVersion != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Script v${portalState.scriptVersion}",
                                    color = NeonGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (!portalState.scriptName.isNullOrBlank()) {
                        Text(
                            text = "Script: ${portalState.scriptName}",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    if (portalState.lastWorkCheckTime != null) {
                        Text(
                            text = "Last checked: ${portalState.lastWorkCheckTime}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    if (portalState.postingStatusMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = portalState.postingStatusMessage ?: "",
                                color = if (portalState.isPostingWork) NeonGreen else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Action buttons: Check for work & Post all now
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.checkForWorkNow() },
                            enabled = !portalState.isLoadingWork && !portalState.isPostingWork,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("check_work_btn")
                        ) {
                            if (portalState.isLoadingWork) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NeonGreen)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Check for work")
                        }

                        Button(
                            onClick = { viewModel.postAllWorkNow() },
                            enabled = !portalState.isPostingWork && portalState.workSlots.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("post_all_now_btn")
                        ) {
                            if (portalState.isPostingWork) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Posting...", color = Color.Black, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Post all now", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Tasks grouped by slot
                    if (portalState.workSlots.isEmpty()) {
                        Text(
                            text = "No work queued from portal. Tap 'Check for work' to fetch pending slots.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            for (slot in portalState.workSlots) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Slot ${slot.slotNumber}${if (!slot.accountLabel.isNullOrBlank()) " (${slot.accountLabel})" else ""}",
                                                color = if (slot.slotNumber == 1) NeonGreen else TextSecondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "${slot.tasks.size} task${if (slot.tasks.size == 1) "" else "s"}",
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        for (task in slot.tasks) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = task.pageName.ifEmpty { "Unknown Page" },
                                                        color = TextPrimary,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Model: ${task.modelCode.ifEmpty { "-" }}",
                                                            color = TextSecondary,
                                                            fontSize = 12.sp
                                                        )
                                                        if (task.startsAt != null) {
                                                            Text(
                                                                text = "Starts: ${task.startsAt}",
                                                                color = TextSecondary,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }
                                                    if (!task.localError.isNullOrBlank()) {
                                                        Text(
                                                            text = task.localError,
                                                            color = CoralRed,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }

                                                val statusText = task.localStatus ?: task.status
                                                val statusColor = when (statusText.uppercase()) {
                                                    "OK", "SHARED" -> NeonGreen
                                                    "FAILED" -> CoralRed
                                                    "NEEDS_PERSON" -> AmberWarning
                                                    "RUNNING" -> NeonGreen
                                                    "CANCELLED IN PORTAL", "CANCELLED" -> CoralRed
                                                    "SKIPPED" -> TextSecondary
                                                    else -> TextSecondary
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(statusColor.copy(alpha = 0.15f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (statusText.equals("Cancelled in portal", ignoreCase = true)) "Cancelled in portal" else statusText.uppercase(),
                                                        color = statusColor,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Battery Optimization Setting
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = if (isIgnoringBatteryOptimizations) NeonGreen else AmberWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Background Execution",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (isIgnoringBatteryOptimizations) {
                        "Battery optimization is disabled. Android will allow the portal heartbeat to run reliably in the background."
                    } else {
                        "Android may sleep background services. Please exempt Story Test from battery optimization so heartbeats continue."
                    },
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                if (!isIgnoringBatteryOptimizations) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ignore_battery_optimization_btn")
                    ) {
                        Text("Ignore Battery Optimization", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Last Error Banner if present
        if (!portalState.lastError.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CoralRed.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CoralRed.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = CoralRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Connection Notice",
                            color = CoralRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = portalState.lastError ?: "",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.testTag("portal_last_error_text")
                        )
                    }
                }
            }
        }
    }
}
