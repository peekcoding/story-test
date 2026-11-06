package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.data.JsonCodec
import com.example.data.model.CaptureEntity
import com.example.data.model.ScreenNode
import com.example.ui.StoryTestViewModel
import com.example.ui.theme.BorderDark
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CapturesScreen(viewModel: StoryTestViewModel) {
    val context = LocalContext.current
    val captures by viewModel.captures.collectAsState()
    var selectedCapture by remember { mutableStateOf<CaptureEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Screen Captures",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "${captures.size} captures saved",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }
            if (captures.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearAllCaptures() }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all", tint = CoralRed)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (captures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No captures recorded yet.", color = TextSecondary)
                    Text("Use the floating overlay button to capture any screen.", color = TextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(captures, key = { it.id }) { capture ->
                    CaptureItemCard(
                        capture = capture,
                        onClick = { selectedCapture = capture },
                        onShare = { viewModel.shareCapture(context, capture) },
                        onDelete = { viewModel.deleteCapture(capture.id) }
                    )
                }
            }
        }
    }

    // Detail Inspection Modal
    if (selectedCapture != null) {
        CaptureDetailDialog(
            capture = selectedCapture!!,
            onDismiss = { selectedCapture = null },
            onShare = { viewModel.shareCapture(context, selectedCapture!!) }
        )
    }
}

@Composable
fun CaptureItemCard(
    capture: CaptureEntity,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(capture.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(capture.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("capture_card_${capture.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail preview
            if (capture.screenshotPath != null && File(capture.screenshotPath).exists()) {
                Image(
                    painter = rememberAsyncImagePainter(File(capture.screenshotPath)),
                    contentDescription = "Screenshot",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = capture.packageName,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = capture.tag.ifEmpty { "Inspector Capture" },
                    color = NeonGreen,
                    fontSize = 12.sp
                )
                Text(
                    text = "$dateStr • ${capture.nodeCount} nodes",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Row {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CoralRed, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun CaptureDetailDialog(
    capture: CaptureEntity,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val rootNode = remember(capture.hierarchyJson) {
        JsonCodec.jsonToScreenNode(capture.hierarchyJson)
    }
    val allNodes = remember(rootNode) { rootNode?.flatten() ?: emptyList() }
    var searchQuery by remember { mutableStateOf("") }

    val filteredNodes = remember(allNodes, searchQuery) {
        if (searchQuery.isBlank()) allNodes
        else {
            val q = searchQuery.lowercase()
            allNodes.filter { n ->
                (n.text ?: "").lowercase().contains(q) ||
                        (n.contentDescription ?: "").lowercase().contains(q) ||
                        (n.viewIdResourceName ?: "").lowercase().contains(q) ||
                        (n.className ?: "").lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Capture Inspection", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(capture.packageName, color = TextSecondary, fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            ) {
                // Screenshot thumbnail if available
                if (capture.screenshotPath != null && File(capture.screenshotPath).exists()) {
                    Image(
                        painter = rememberAsyncImagePainter(File(capture.screenshotPath)),
                        contentDescription = "Screenshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Node filter search
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    placeholder = { Text("Filter nodes by text, viewId, class...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Nodes (${filteredNodes.size} of ${allNodes.size}):",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredNodes) { node ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                if (node.text != null) {
                                    Text("text: \"${node.text}\"", color = NeonGreen, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                                if (node.contentDescription != null) {
                                    Text("desc: \"${node.contentDescription}\"", color = TextPrimary, fontSize = 12.sp)
                                }
                                if (node.viewIdResourceName != null) {
                                    Text("id: ${node.viewIdResourceName}", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${node.className?.substringAfterLast('.') ?: ""} • bounds [${node.bounds.left},${node.bounds.top} to ${node.bounds.right},${node.bounds.bottom}]",
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                    if (node.clickable) {
                                        Text("CLICKABLE", color = NeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Files", color = NeonGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = TextSecondary)
            }
        }
    )
}
