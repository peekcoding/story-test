package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.StoryTestApp
import com.example.data.JsonCodec
import com.example.data.model.CaptureEntity
import com.example.data.model.RunResult
import com.example.data.model.SafetyRuleEntity
import com.example.data.model.ScriptEntity
import com.example.data.model.ScriptStep
import com.example.data.model.StepType
import com.example.service.LiveRunState
import com.example.service.StoryTestService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class StoryTestViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as StoryTestApp).repository
    val portalRepository = (application as StoryTestApp).portalRepository

    val portalState: StateFlow<com.example.portal.PortalDeviceState> = portalRepository.deviceState

    val scripts: StateFlow<List<ScriptEntity>> = repository.scripts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val captures: StateFlow<List<CaptureEntity>> = repository.captures.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val safetyRules: StateFlow<List<SafetyRuleEntity>> = repository.safetyRules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val popupRules: StateFlow<List<com.example.data.model.PopupRuleEntity>> = repository.popupRules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val liveRunState: StateFlow<LiveRunState> = StoryTestService.liveRunState

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    private val _countdownSeconds = MutableStateFlow<Int?>(null)
    val countdownSeconds: StateFlow<Int?> = _countdownSeconds.asStateFlow()

    private var countdownJob: Job? = null

    init {
        checkServiceStatus()
    }

    fun checkServiceStatus() {
        _isServiceEnabled.value = StoryTestService.isServiceRunning()
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // =========================================================================
    // Scripts Operations
    // =========================================================================

    fun saveScript(name: String, description: String, steps: List<ScriptStep>, id: Long = 0, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val savedId = repository.saveScript(name, description, steps, id)
            onComplete(savedId)
        }
    }

    fun deleteScript(id: Long) {
        viewModelScope.launch {
            repository.deleteScript(id)
        }
    }

    fun importScriptFromJson(json: String, name: String? = null, onResult: (Boolean, String) -> Unit) {
        try {
            val steps = JsonCodec.jsonToSteps(json)
            if (steps.isEmpty()) {
                onResult(false, "No valid steps found in JSON")
                return
            }
            viewModelScope.launch {
                val scriptName = name?.ifBlank { "Imported Script" } ?: "Imported Script"
                repository.saveScript(scriptName, "Imported from JSON", steps)
                onResult(true, "Imported ${steps.size} steps successfully")
            }
        } catch (e: Exception) {
            onResult(false, "Invalid JSON: ${e.message}")
        }
    }

    // =========================================================================
    // Run & Batch Runner Operations
    // =========================================================================

    fun startCountdownAndRun(
        script: ScriptEntity,
        pageNamesText: String,
        link: String,
        countdownDuration: Int = 5
    ) {
        countdownJob?.cancel()
        val pages = pageNamesText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (pages.isEmpty()) return

        countdownJob = viewModelScope.launch {
            for (i in countdownDuration downTo 1) {
                _countdownSeconds.value = i
                delay(1000)
            }
            _countdownSeconds.value = null

            val service = StoryTestService.serviceInstance.value
            service?.startBatchRun(script, pages, link)
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        _countdownSeconds.value = null
    }

    fun stopRun() {
        cancelCountdown()
        val service = StoryTestService.serviceInstance.value
        service?.stopBatchRun("Stopped from app")
    }

    fun formatResultsAsText(results: List<RunResult>, scriptName: String): String {
        val sb = StringBuilder()
        sb.appendLine("=== Story Test Results ===")
        sb.appendLine("Script: $scriptName")
        sb.appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        sb.appendLine("---------------------------")
        sb.appendLine(String.format("%-20s | %-10s | %-6s | %s", "Page", "Status", "Sec", "Details"))
        sb.appendLine("-----------------------------------------------------------------")
        for (r in results) {
            val logSuffix = if (!r.logInfo.isNullOrBlank()) " [${r.logInfo}]" else ""
            val detail = when {
                r.failedStepName != null -> "Failed: ${r.failedStepName}$logSuffix"
                r.errorDetails != null -> "${r.errorDetails}$logSuffix"
                else -> "Complete$logSuffix"
            }
            sb.appendLine(String.format("%-20s | %-10s | %-6.1f | %s", r.pageName, r.status.name, r.durationSeconds, detail))
        }
        sb.appendLine("-----------------------------------------------------------------")
        val passed = results.count { it.status.name == "OK" }
        sb.appendLine("Total: ${results.size} | Passed: $passed | Failed/Paused: ${results.size - passed}")
        return sb.toString()
    }

    // =========================================================================
    // Captures Operations
    // =========================================================================

    fun deleteCapture(id: Long) {
        viewModelScope.launch {
            repository.deleteCapture(id)
        }
    }

    fun clearAllCaptures() {
        viewModelScope.launch {
            repository.clearCaptures()
        }
    }

    fun shareCapture(context: Context, capture: CaptureEntity) {
        val filesToShare = ArrayList<Uri>()

        // 1. Screenshot if exists
        capture.screenshotPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                filesToShare.add(uri)
            }
        }

        // 2. Export hierarchy JSON file to cache
        try {
            val jsonFile = File(context.cacheDir, "hierarchy_${capture.id}.json")
            FileOutputStream(jsonFile).use { out ->
                out.write(capture.hierarchyJson.toByteArray())
            }
            val jsonUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", jsonFile)
            filesToShare.add(jsonUri)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (filesToShare.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, filesToShare)
                putExtra(Intent.EXTRA_SUBJECT, "Story Test Screen Capture - ${capture.packageName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Screen Capture").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    // =========================================================================
    // Safety Rules Operations
    // =========================================================================

    fun addSafetyRule(phrase: String) {
        if (phrase.isBlank()) return
        viewModelScope.launch {
            repository.addSafetyRule(phrase)
        }
    }

    fun toggleSafetyRule(rule: SafetyRuleEntity) {
        viewModelScope.launch {
            repository.updateSafetyRule(rule.copy(isEnabled = !rule.isEnabled))
        }
    }

    fun deleteSafetyRule(id: Long) {
        viewModelScope.launch {
            repository.deleteSafetyRule(id)
        }
    }

    // =========================================================================
    // Popup Auto-Dismiss Rules Operations
    // =========================================================================

    fun addPopupRule(whenISee: String, tapText: String) {
        if (whenISee.isBlank() || tapText.isBlank()) return
        viewModelScope.launch {
            repository.addPopupRule(whenISee, tapText)
        }
    }

    fun togglePopupRule(rule: com.example.data.model.PopupRuleEntity) {
        viewModelScope.launch {
            repository.updatePopupRule(rule.copy(isEnabled = !rule.isEnabled))
        }
    }

    fun deletePopupRule(id: Long) {
        viewModelScope.launch {
            repository.deletePopupRule(id)
        }
    }

    // =========================================================================
    // Web Portal Operations
    // =========================================================================

    private val _isEnrolling = MutableStateFlow(false)
    val isEnrolling: StateFlow<Boolean> = _isEnrolling.asStateFlow()

    private val _enrollmentFieldError = MutableStateFlow<String?>(null)
    val enrollmentFieldError: StateFlow<String?> = _enrollmentFieldError.asStateFlow()

    fun updatePortalAddress(newAddress: String) {
        portalRepository.updatePortalAddress(newAddress)
    }

    fun enrollDevice(code: String, onComplete: ((Boolean) -> Unit)? = null) {
        val trimmed = code.trim().uppercase()
        if (trimmed.length != 8) {
            _enrollmentFieldError.value = "Code must be 8 alphanumeric characters"
            return
        }
        _enrollmentFieldError.value = null
        _isEnrolling.value = true

        viewModelScope.launch {
            val result = portalRepository.enroll(trimmed)
            _isEnrolling.value = false
            when (result) {
                is com.example.portal.model.EnrollResult.Success -> {
                    com.example.portal.PortalService.start(getApplication())
                    onComplete?.invoke(true)
                }
                is com.example.portal.model.EnrollResult.ValidationError -> {
                    _enrollmentFieldError.value = result.codeError ?: result.message
                    onComplete?.invoke(false)
                }
                is com.example.portal.model.EnrollResult.RateLimited -> {
                    _enrollmentFieldError.value = "Too many tries, wait a minute."
                    onComplete?.invoke(false)
                }
                is com.example.portal.model.EnrollResult.NetworkError -> {
                    _enrollmentFieldError.value = result.message
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun signOutPortal() {
        portalRepository.signOut()
        com.example.portal.PortalService.stop(getApplication())
    }

    fun triggerManualHeartbeat() {
        viewModelScope.launch {
            val (batteryPct, isCharging) = getBatteryStatus()
            val liveState = StoryTestService.liveRunState.value
            val isRunning = liveState.isRunning
            portalRepository.sendHeartbeat(
                battery = batteryPct,
                charging = isCharging,
                currentTaskUuid = if (isRunning) liveState.taskUuid else null,
                currentStep = if (isRunning) liveState.currentStepName else null
            )
        }
    }

    fun reportVideosNow() {
        viewModelScope.launch {
            portalRepository.scanAndReportVideos()
        }
    }

    fun checkForWorkNow() {
        viewModelScope.launch {
            portalRepository.getWork()
        }
    }

    private var portalPostingJob: Job? = null

    fun postAllWorkNow() {
        val service = StoryTestService.serviceInstance.value
        if (service == null) {
            portalRepository.setPostingWorkState(false, "Accessibility Service is not enabled")
            return
        }

        portalPostingJob?.cancel()
        portalPostingJob = viewModelScope.launch(Dispatchers.Default) {
            runPortalSlot1Work(service)
        }
    }

    fun stopPortalPosting() {
        portalPostingJob?.cancel()
        val service = StoryTestService.serviceInstance.value
        service?.stopBatchRun("Stopped by user")
        portalRepository.setPostingWorkState(false, "Posting cancelled")
    }

    private suspend fun runPortalSlot1Work(service: StoryTestService) {
        portalRepository.setPostingWorkState(true, "Refreshing work...")

        // Initial fetch to get latest tasks and script
        portalRepository.getWork()

        val steps = portalRepository.getCachedScriptSteps()
        if (steps.isEmpty()) {
            portalRepository.setPostingWorkState(false, "No cached portal script found")
            return
        }

        val safetyPhrases = repository.getActiveSafetyPhrases()
        val popupRules = repository.getActivePopupRules()
        val stepRunner = com.example.engine.StepRunner(
            handler = service,
            isStopRequestedProvider = { portalRepository.isStopRequested() }
        )

        val nowIsoParser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        val finishedTaskUuids = mutableSetOf<String>()

        while (currentCoroutineContext().isActive) {
            // 5. Before starting each next task, call GET /api/device/work to refresh task list
            portalRepository.getWork()
            val allSlots = portalRepository.deviceState.value.workSlots
            val slot1 = allSlots.find { it.slotNumber == 1 }

            // Non-slot 1 tasks: mark them skipped per requirements
            for (slot in allSlots) {
                if (slot.slotNumber != 1) {
                    for (task in slot.tasks) {
                        portalRepository.updateTaskLocalStatus(task.uuid, "SKIPPED", "Slot ${slot.slotNumber} is not set up in the app yet")
                    }
                }
            }

            val slot1Tasks = slot1?.tasks ?: emptyList()
            if (slot1Tasks.isEmpty()) {
                portalRepository.setPostingWorkState(false, "Nothing to post")
                StoryTestService.updateLiveTaskState(null, null, false)
                break
            }

            // Find next runnable task in slot 1
            val nowMs = System.currentTimeMillis()
            val task = slot1Tasks.firstOrNull { t ->
                if (finishedTaskUuids.contains(t.uuid)) return@firstOrNull false
                if (t.localStatus == "OK" || t.localStatus == "FAILED" ||
                    t.localStatus == "NEEDS_PERSON" || t.localStatus == "Cancelled in portal" ||
                    t.localStatus == "SKIPPED") {
                    return@firstOrNull false
                }
                true
            }

            if (task == null) {
                portalRepository.setPostingWorkState(false, "Finished processing Slot 1 tasks")
                StoryTestService.updateLiveTaskState(null, null, false)
                break
            }

            // Check starts_at in the future
            if (!task.startsAt.isNullOrBlank()) {
                val startsAtDate = try {
                    nowIsoParser.parse(task.startsAt)
                } catch (e: Exception) {
                    null
                }
                if (startsAtDate != null && startsAtDate.time > nowMs) {
                    portalRepository.updateTaskLocalStatus(task.uuid, "SKIPPED", "Starts at ${task.startsAt}")
                    finishedTaskUuids.add(task.uuid)
                    continue
                }
            }

            // Clear any previous stop request
            portalRepository.clearStopCurrentTask()

            // Update live state for heartbeat: running with this task's uuid
            StoryTestService.updateLiveTaskState(task.uuid, "starting", true)
            portalRepository.setPostingWorkState(true, "Starting task for ${task.pageName}...")
            portalRepository.updateTaskLocalStatus(task.uuid, "RUNNING")

            // a. POST .../start
            val (canStart, startError) = portalRepository.sendTaskStart(task.uuid)
            if (!canStart) {
                portalRepository.updateTaskLocalStatus(task.uuid, "SKIPPED", startError ?: "409 Conflict")
                finishedTaskUuids.add(task.uuid)
                StoryTestService.updateLiveTaskState(null, null, false)
                portalRepository.getWork()
                continue
            }

            // b. Run steps in order
            var taskCancelled = false
            var taskFailed = false
            var failedStepSummary: String? = null
            var errorMsg: String? = null
            var isNeedsPerson = false
            var lastReportStepText = ""
            var lastException: Exception? = null
            var previousStepStartTime = System.currentTimeMillis()

            for ((stepIndex, rawStep) in steps.withIndex()) {
                if (portalRepository.isStopRequested()) {
                    taskCancelled = true
                    errorMsg = "Cancelled in portal"
                    break
                }

                val stepStartTime = System.currentTimeMillis()
                val resolvedStep = com.example.engine.VariableResolver.resolveStep(rawStep, task.pageName, task.link, task.modelCode)
                val summary = resolvedStep.summary()
                StoryTestService.updateLiveTaskState(task.uuid, summary, true)

                if (rawStep.type == StepType.REPORT) {
                    lastReportStepText = resolvedStep.text
                }

                try {
                    stepRunner.executeStep(
                        rawStep = rawStep,
                        pageName = task.pageName,
                        link = task.link,
                        safetyKeywords = safetyPhrases,
                        popupRules = popupRules,
                        previousStepStartTime = previousStepStartTime,
                        modelCode = task.modelCode,
                        onReportStep = { stepText ->
                            portalRepository.sendReportEvents(task.uuid, stepText)
                        },
                        onReportEvent = { stepName, msg ->
                            portalRepository.sendReportEvents(task.uuid, stepName, msg)
                        },
                        isStopRequested = { portalRepository.isStopRequested() },
                        onLog = { /* log */ }
                    )
                } catch (e: com.example.engine.TaskStopRequestedException) {
                    taskCancelled = true
                    errorMsg = "Cancelled in portal"
                    break
                } catch (e: com.example.engine.SafetyInterruptionException) {
                    taskFailed = true
                    isNeedsPerson = true
                    failedStepSummary = summary
                    errorMsg = "Safety check: ${e.matchedKeyword}"
                    lastException = e
                    break
                } catch (e: Exception) {
                    taskFailed = true
                    failedStepSummary = summary
                    errorMsg = e.message ?: "Execution error"
                    lastException = e
                    break
                } finally {
                    previousStepStartTime = stepStartTime
                }
            }

            val recentTexts = service.getRecentSeenTexts().map { it.text }

            if (taskCancelled || portalRepository.isStopRequested()) {
                // Stop requested: do NOT call finish, press Back until Home (max 6 times), mark Cancelled in portal
                finishedTaskUuids.add(task.uuid)
                portalRepository.updateTaskLocalStatus(task.uuid, "Cancelled in portal", "Cancelled in portal")
                StoryTestService.updateLiveTaskState(null, null, false)

                for (backAttempt in 1..6) {
                    val isHome = service.findNodes { n ->
                        (n.contentDescription ?: "").startsWith("Home, tab", ignoreCase = true)
                    }.isNotEmpty()
                    if (isHome) break
                    service.dispatchGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    delay(800L)
                }

                // Refresh work and carry on only with tasks portal returns now
                portalRepository.getWork()
                val refreshedSlot1 = portalRepository.deviceState.value.workSlots.find { it.slotNumber == 1 }?.tasks ?: emptyList()
                if (refreshedSlot1.isEmpty()) {
                    portalRepository.setPostingWorkState(false, "Nothing to post")
                    break
                }
                continue
            }

            if (taskFailed) {
                finishedTaskUuids.add(task.uuid)
                val failureSsPath = (lastException as? com.example.engine.StepExecutionException)?.screenshotPath
                    ?: service.takeScreenshot("failure_${task.uuid}")
                if (failureSsPath != null) {
                    portalRepository.uploadTaskScreenshot(task.uuid, File(failureSsPath))
                }

                service.captureScreenAndStore("Failure on ${task.pageName}: $failedStepSummary")

                val resultType = if (isNeedsPerson) "needs_person" else "failed"
                portalRepository.sendTaskFinish(
                    taskUuid = task.uuid,
                    result = resultType,
                    step = failedStepSummary ?: "unknown_step",
                    error = errorMsg,
                    seenTexts = recentTexts
                )
                portalRepository.updateTaskLocalStatus(task.uuid, if (isNeedsPerson) "NEEDS_PERSON" else "FAILED", errorMsg)
            } else {
                finishedTaskUuids.add(task.uuid)
                portalRepository.sendTaskFinish(
                    taskUuid = task.uuid,
                    result = "shared",
                    step = steps.lastOrNull()?.summary() ?: "finished",
                    error = null,
                    seenTexts = recentTexts
                )
                portalRepository.updateTaskLocalStatus(task.uuid, "OK")
            }

            StoryTestService.updateLiveTaskState(null, null, false)

            delay(3000L)
            service.dispatchGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            delay(1000L)

            // Refresh from /work after every task
            portalRepository.getWork()
        }

        StoryTestService.updateLiveTaskState(null, null, false)
        portalRepository.setPostingWorkState(false, "Finished processing Slot 1 tasks")
        portalRepository.getWork()
    }

    private fun getBatteryStatus(): Pair<Int, Boolean> {
        val app = getApplication<Application>()
        val batteryIntent = app.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        if (batteryIntent != null) {
            val level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            val status = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL

            val batteryPct = if (level >= 0 && scale > 0) {
                ((level.toFloat() / scale.toFloat()) * 100).toInt()
            } else {
                50
            }
            return Pair(batteryPct, isCharging)
        }
        return Pair(100, false)
    }
}
