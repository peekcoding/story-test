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

    private val workRunner = (application as StoryTestApp).workRunner

    fun postAllWorkNow() {
        workRunner.start("button")
    }

    fun stopPortalPosting() {
        workRunner.stop()
    }

    fun setAutoPost(enabled: Boolean) {
        portalRepository.setAutoPostEnabled(enabled)
    }

    fun setAccountsApp(value: String) {
        portalRepository.setAccountsApp(value)
    }

    fun setUseMultiSpace(enabled: Boolean) {
        portalRepository.setUseMultiSpace(enabled)
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
