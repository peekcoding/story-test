package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.JsonCodec
import com.example.data.model.ScriptEntity
import com.example.data.model.ScriptStep
import com.example.data.model.StepType
import com.example.ui.StoryTestViewModel
import com.example.ui.theme.BorderDark
import com.example.ui.theme.CoralRed
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptsScreen(
    viewModel: StoryTestViewModel,
    onRunScript: (ScriptEntity) -> Unit
) {
    val context = LocalContext.current
    val scripts by viewModel.scripts.collectAsState()

    var editingScript by remember { mutableStateOf<ScriptEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Scripts",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Automation step workflows",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                }
                OutlinedButton(
                    onClick = { showImportDialog = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = "Import", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import JSON")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (scripts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No scripts yet. Create one or import JSON.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(scripts, key = { it.id }) { script ->
                        ScriptItemCard(
                            script = script,
                            onEdit = { editingScript = script },
                            onRun = { onRunScript(script) },
                            onDelete = { viewModel.deleteScript(script.id) },
                            onExport = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Script JSON", script.stepsJson))
                                Toast.makeText(context, "Copied script JSON to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { isCreatingNew = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_script_fab"),
            containerColor = NeonGreen,
            contentColor = androidx.compose.ui.graphics.Color.Black
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Script")
        }
    }

    // Edit Script Dialog / Sheet
    if (editingScript != null) {
        val s = editingScript!!
        ScriptEditorDialog(
            initialName = s.name,
            initialDescription = s.description,
            initialSteps = JsonCodec.jsonToSteps(s.stepsJson),
            onDismiss = { editingScript = null },
            onSave = { name, desc, steps ->
                viewModel.saveScript(name, desc, steps, id = s.id)
                editingScript = null
            }
        )
    }

    // New Script Dialog
    if (isCreatingNew) {
        ScriptEditorDialog(
            initialName = "New Automation Script",
            initialDescription = "Runs sequential gestures and text input",
            initialSteps = listOf(
                ScriptStep(type = StepType.LAUNCH_APP, packageName = "com.facebook.katana"),
                ScriptStep(type = StepType.WAIT_TEXT, text = "Create story", exact = false),
                ScriptStep(type = StepType.TAP_TEXT, text = "Create story", exact = false)
            ),
            onDismiss = { isCreatingNew = false },
            onSave = { name, desc, steps ->
                viewModel.saveScript(name, desc, steps, id = 0)
                isCreatingNew = false
            }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        var importText by remember { mutableStateOf("") }
        var scriptName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Script JSON", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = scriptName,
                        onValueChange = { scriptName = it },
                        label = { Text("Script Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text("Paste JSON array of steps") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importScriptFromJson(importText, scriptName) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) showImportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = androidx.compose.ui.graphics.Color.Black)
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun ScriptItemCard(
    script: ScriptEntity,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val steps = remember(script.stepsJson) { JsonCodec.jsonToSteps(script.stepsJson) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = script.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    if (script.isTemplate) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(NeonGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("TEMPLATE", color = NeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = "${steps.size} steps",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            if (script.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = script.description,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Step previews
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                steps.take(4).forEachIndexed { i, step ->
                    Text(
                        text = "${i + 1}. ${step.summary()}",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
                if (steps.size > 4) {
                    Text(
                        text = "+ ${steps.size - 4} more steps...",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExport) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy JSON", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CoralRed, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onRun,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = androidx.compose.ui.graphics.Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorDialog(
    initialName: String,
    initialDescription: String,
    initialSteps: List<ScriptStep>,
    onDismiss: () -> Unit,
    onSave: (String, String, List<ScriptStep>) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var steps by remember { mutableStateOf(initialSteps) }
    var editingStepIndex by remember { mutableStateOf<Int?>(null) }
    var isAddingStep by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Script", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Script Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Steps (${steps.size})", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Button(
                        onClick = { isAddingStep = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = androidx.compose.ui.graphics.Color.Black),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Step", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(steps.size) { index ->
                        val step = steps[index]
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${step.type.displayName}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = step.summary(),
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }

                                Row {
                                    // Move up
                                    if (index > 0) {
                                        IconButton(
                                            onClick = {
                                                val mutable = steps.toMutableList()
                                                val item = mutable.removeAt(index)
                                                mutable.add(index - 1, item)
                                                steps = mutable
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    // Move down
                                    if (index < steps.size - 1) {
                                        IconButton(
                                            onClick = {
                                                val mutable = steps.toMutableList()
                                                val item = mutable.removeAt(index)
                                                mutable.add(index + 1, item)
                                                steps = mutable
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    // Edit
                                    IconButton(
                                        onClick = { editingStepIndex = index },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Step", tint = TextPrimary, modifier = Modifier.size(16.dp))
                                    }
                                    // Delete
                                    IconButton(
                                        onClick = {
                                            val mutable = steps.toMutableList()
                                            mutable.removeAt(index)
                                            steps = mutable
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Step", tint = CoralRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, description, steps) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = androidx.compose.ui.graphics.Color.Black)
            ) {
                Text("Save Script", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )

    // Step Edit Dialog
    if (editingStepIndex != null) {
        val idx = editingStepIndex!!
        StepDetailEditDialog(
            initialStep = steps[idx],
            onDismiss = { editingStepIndex = null },
            onConfirm = { updated ->
                val mutable = steps.toMutableList()
                mutable[idx] = updated
                steps = mutable
                editingStepIndex = null
            }
        )
    }

    // Add Step Dialog
    if (isAddingStep) {
        StepDetailEditDialog(
            initialStep = ScriptStep(type = StepType.TAP_TEXT, text = ""),
            onDismiss = { isAddingStep = false },
            onConfirm = { newStep ->
                steps = steps + newStep
                isAddingStep = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepDetailEditDialog(
    initialStep: ScriptStep,
    onDismiss: () -> Unit,
    onConfirm: (ScriptStep) -> Unit
) {
    var type by remember { mutableStateOf(initialStep.type) }
    var text by remember { mutableStateOf(initialStep.text) }
    var exact by remember { mutableStateOf(initialStep.exact) }
    var index by remember { mutableStateOf(initialStep.index.toString()) }
    var viewId by remember { mutableStateOf(initialStep.viewId) }
    var xPercent by remember { mutableStateOf(initialStep.xPercent.toString()) }
    var yPercent by remember { mutableStateOf(initialStep.yPercent.toString()) }
    var timeoutMs by remember { mutableStateOf(initialStep.timeoutMs.toString()) }
    var sleepMs by remember { mutableStateOf(initialStep.sleepMs.toString()) }
    var packageName by remember { mutableStateOf(initialStep.packageName) }
    var screenshotName by remember { mutableStateOf(initialStep.screenshotName) }
    var maxSwipes by remember { mutableStateOf(initialStep.maxSwipes.toString()) }

    var typeMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Step", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Type selector dropdown
                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = type.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Step Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        StepType.values().forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st.displayName) },
                                onClick = {
                                    type = st
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Dynamic inputs based on step type
                when (type) {
                    StepType.LAUNCH_APP -> {
                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it },
                            label = { Text("Package Name (e.g. com.facebook.katana)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    StepType.TAP_TEXT, StepType.WAIT_TEXT, StepType.LONG_PRESS_TEXT -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Target Text") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Exact Match", color = TextSecondary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(checked = exact, onCheckedChange = { exact = it })
                        }
                    }

                    StepType.TAP_DESC, StepType.WAIT_DESC -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Content Description") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    StepType.TAP_ID -> {
                        OutlinedTextField(
                            value = viewId,
                            onValueChange = { viewId = it },
                            label = { Text("View ID Resource Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    StepType.TAP_XY -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = xPercent,
                                onValueChange = { xPercent = it },
                                label = { Text("X % (0-100)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = yPercent,
                                onValueChange = { yPercent = it },
                                label = { Text("Y % (0-100)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    StepType.TYPE_TEXT -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Text to Type") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Variable helper chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Insert:", color = TextSecondary, fontSize = 12.sp)
                            OutlinedButton(
                                onClick = { text += "{{PAGE_NAME}}" },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("{{PAGE_NAME}}", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { text += "{{LINK}}" },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("{{LINK}}", fontSize = 11.sp)
                            }
                        }
                    }

                    StepType.SLEEP -> {
                        OutlinedTextField(
                            value = sleepMs,
                            onValueChange = { sleepMs = it },
                            label = { Text("Duration (ms)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    StepType.SCREENSHOT -> {
                        OutlinedTextField(
                            value = screenshotName,
                            onValueChange = { screenshotName = it },
                            label = { Text("Screenshot Name / Tag") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    StepType.PICK_NEWEST_VIDEO, StepType.SCROLL_DOWN, StepType.SCROLL_UP, StepType.BACK, StepType.HOME -> {
                        Text(
                            text = type.description,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    StepType.SWIPE_UNTIL_TEXT -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Target Text") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Exact Match", color = TextSecondary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(checked = exact, onCheckedChange = { exact = it })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = yPercent,
                                onValueChange = { yPercent = it },
                                label = { Text("Y % (0-100)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxSwipes,
                                onValueChange = { maxSwipes = it },
                                label = { Text("Max Swipes (default 5)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    StepType.WAIT_SEEN -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Seen Text to Wait For (e.g. Story shared)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    StepType.SWITCH_PAGE -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Target Page Name") },
                            placeholder = { Text("{{PAGE_NAME}} or Page Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Insert:", color = TextSecondary, fontSize = 12.sp)
                            OutlinedButton(
                                onClick = { text += "{{PAGE_NAME}}" },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("{{PAGE_NAME}}", fontSize = 11.sp)
                            }
                        }
                    }

                    StepType.REPORT -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Report Event Text (e.g. opening_facebook)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    StepType.PREPARE_MODEL_VIDEO -> {
                        Text(
                            text = "Prepares the newest video from Movies/PageShare/{{MODEL_CODE}}/ into DCIM/PageShareStory/",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // Timeout config for nodes/wait
                if (type == StepType.TAP_TEXT || type == StepType.TAP_DESC || type == StepType.TAP_ID ||
                    type == StepType.WAIT_TEXT || type == StepType.WAIT_DESC || type == StepType.LONG_PRESS_TEXT ||
                    type == StepType.WAIT_SEEN || type == StepType.SWITCH_PAGE) {
                    OutlinedTextField(
                        value = timeoutMs,
                        onValueChange = { timeoutMs = it },
                        label = { Text(if (type == StepType.SWITCH_PAGE) "Timeout (ms, default 60000)" else "Timeout (ms, default 10000)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val step = initialStep.copy(
                        type = type,
                        text = text,
                        exact = exact,
                        index = index.toIntOrNull() ?: 0,
                        viewId = viewId,
                        xPercent = xPercent.toFloatOrNull() ?: 50f,
                        yPercent = yPercent.toFloatOrNull() ?: 50f,
                        timeoutMs = timeoutMs.toLongOrNull() ?: 10000L,
                        sleepMs = sleepMs.toLongOrNull() ?: 1000L,
                        packageName = packageName,
                        screenshotName = screenshotName,
                        maxSwipes = maxSwipes.toIntOrNull() ?: 5
                    )
                    onConfirm(step)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = androidx.compose.ui.graphics.Color.Black)
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
