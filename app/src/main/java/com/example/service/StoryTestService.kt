package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.data.JsonCodec
import com.example.data.StoryTestRepository
import com.example.data.model.CaptureEntity
import com.example.data.model.NodeBounds
import com.example.data.model.RunResult
import com.example.data.model.RunStatus
import com.example.data.model.ScreenNode
import com.example.data.model.ScriptEntity
import com.example.data.model.ScriptStep
import com.example.engine.AutomationActionHandler
import com.example.engine.SafetyInterruptionException
import com.example.engine.StepExecutionException
import com.example.engine.StepRunner
import com.example.engine.StepTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume

data class LiveRunState(
    val isRunning: Boolean = false,
    val taskUuid: String? = null,
    val isPausedNeedsPerson: Boolean = false,
    val needsPersonReason: String? = null,
    val scriptName: String = "",
    val currentPage: String = "",
    val currentPageIndex: Int = 0,
    val totalPages: Int = 0,
    val currentStepNumber: Int = 0,
    val totalSteps: Int = 0,
    val currentStepName: String = "",
    val elapsedSeconds: Int = 0,
    val results: List<RunResult> = emptyList(),
    val isCompleted: Boolean = false,
    val runLogs: List<String> = emptyList(),
    val recentSeenTexts: List<com.example.data.model.SeenTextEntry> = emptyList(),
    val currentTaskReachedAboutToShare: Boolean = false,
    val isStoppedByPerson: Boolean = false
)

class StoryTestService : AccessibilityService(), AutomationActionHandler {

    companion object {
        private val _serviceInstance = MutableStateFlow<StoryTestService?>(null)
        val serviceInstance: StateFlow<StoryTestService?> = _serviceInstance.asStateFlow()

        private val _liveRunState = MutableStateFlow(LiveRunState())
        val liveRunState: StateFlow<LiveRunState> = _liveRunState.asStateFlow()

        fun isServiceRunning(): Boolean = _serviceInstance.value != null

        fun updateLiveTaskState(taskUuid: String?, currentStep: String?, isRunning: Boolean) {
            _liveRunState.value = _liveRunState.value.copy(
                taskUuid = taskUuid,
                currentStepName = currentStep ?: "",
                isRunning = isRunning
            )
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var batchRunJob: Job? = null
    private var timerJob: Job? = null

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var overlayTvStatus: TextView? = null
    private var overlayIvAlert: ImageView? = null

    private val repository by lazy { StoryTestRepository(AppDatabase.getInstance(this)) }
    private val stepRunner by lazy { StepRunner(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _serviceInstance.value = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupFloatingOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingOverlay()
        batchRunJob?.cancel()
        timerJob?.cancel()
        _serviceInstance.value = null
    }

    private val seenTextMemory = com.example.engine.SeenTextMemory(500)

    override fun hasSeenTextAfter(text: String, afterTimestamp: Long): Boolean {
        return seenTextMemory.hasSeenAfter(text, afterTimestamp)
    }

    override fun getRecentSeenTexts(): List<com.example.data.model.SeenTextEntry> {
        return seenTextMemory.getAllEntries()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()

        // 1. event.text
        event.text?.forEach { charSeq ->
            val str = charSeq?.toString()?.trim()
            if (!str.isNullOrEmpty()) {
                seenTextMemory.record(str, now)
            }
        }

        // 2. event.contentDescription
        event.contentDescription?.toString()?.trim()?.let { desc ->
            if (desc.isNotEmpty()) {
                seenTextMemory.record(desc, now)
            }
        }

        // 3. event.source and all its children
        try {
            val source = event.source
            if (source != null) {
                collectNodeTextsRecursively(source, now)
            }
        } catch (e: Exception) {
            // Node could be recycled or window changing
        }
    }

    private fun collectNodeTextsRecursively(node: AccessibilityNodeInfo, timestamp: Long) {
        node.text?.toString()?.trim()?.let { str ->
            if (str.isNotEmpty()) {
                seenTextMemory.record(str, timestamp)
            }
        }
        node.contentDescription?.toString()?.trim()?.let { desc ->
            if (desc.isNotEmpty()) {
                seenTextMemory.record(desc, timestamp)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodeTextsRecursively(child, timestamp)
        }
    }

    override fun onInterrupt() {
        stopBatchRun("Service interrupted")
    }

    // =========================================================================
    // Floating Draggable Pill Overlay
    // =========================================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingOverlay() {
        if (overlayView != null) return

        try {
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 60
                y = 200
            }

            // Create pill layout programmatically
            val pillLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 16, 24, 16)

                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#EE12141A"))
                    setStroke(2, Color.parseColor("#384252"))
                    cornerRadius = 48f
                }
                elevation = 16f
            }

            // Drag handle / app indicator
            val dragHandle = ImageView(this).apply {
                setImageResource(android.R.drawable.ic_menu_sort_by_size)
                setColorFilter(Color.parseColor("#00E676"))
                setPadding(8, 8, 12, 8)
            }
            pillLayout.addView(dragHandle)

            // Alert indicator (for safety pause)
            overlayIvAlert = ImageView(this).apply {
                setImageResource(android.R.drawable.stat_notify_error)
                setColorFilter(Color.parseColor("#FFD740"))
                visibility = View.GONE
                setPadding(4, 4, 8, 4)
            }
            pillLayout.addView(overlayIvAlert)

            // Status label
            overlayTvStatus = TextView(this).apply {
                text = "StoryBot"
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(0, 0, 16, 0)
            }
            pillLayout.addView(overlayTvStatus)

            // Stop button (red stop)
            val stopBtn = ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_delete)
                setColorFilter(Color.parseColor("#FF5252"))
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(8, 8, 12, 8)
                setOnClickListener {
                    if (_liveRunState.value.isRunning) {
                        stopBatchRun("Cancelled by user via overlay")
                        Toast.makeText(this@StoryTestService, "Automation Stopped", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@StoryTestService, "No active run to stop", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            pillLayout.addView(stopBtn)

            // Touch listener for dragging
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            dragHandle.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(pillLayout, layoutParams)
                        true
                    }
                    else -> false
                }
            }

            overlayView = pillLayout
            windowManager.addView(overlayView, layoutParams)

            // Observe run state for overlay updates
            serviceScope.launch {
                liveRunState.collect { state ->
                    updateOverlayUI(state)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateOverlayUI(state: LiveRunState) {
        Handler(Looper.getMainLooper()).post {
            if (state.isPausedNeedsPerson) {
                overlayIvAlert?.visibility = View.VISIBLE
                overlayTvStatus?.text = "Needs a person!"
                overlayTvStatus?.setTextColor(Color.parseColor("#FFD740"))
            } else if (state.isRunning) {
                overlayIvAlert?.visibility = View.GONE
                overlayTvStatus?.text = "P${state.currentPageIndex + 1}/${state.totalPages} • S${state.currentStepNumber}/${state.totalSteps}"
                overlayTvStatus?.setTextColor(Color.parseColor("#00E676"))
            } else {
                overlayIvAlert?.visibility = View.GONE
                overlayTvStatus?.text = "StoryBot"
                overlayTvStatus?.setTextColor(Color.WHITE)
            }
        }
    }

    private fun removeFloatingOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    // =========================================================================
    // Screen Capture & Hierarchy Inspector
    // =========================================================================

    suspend fun captureScreenAndStore(tag: String = "Manual Capture"): CaptureEntity? = withContext(Dispatchers.IO) {
        val rootNode = getCurrentHierarchy() ?: return@withContext null
        val packageName = getRootPackageName() ?: "unknown"
        val timestamp = System.currentTimeMillis()

        val hierarchyJson = JsonCodec.screenNodeToJson(rootNode)
        val nodeCount = rootNode.flatten().size

        // Capture screenshot
        val screenshotPath = captureScreenshotToFile("capture_$timestamp")

        val capture = CaptureEntity(
            timestamp = timestamp,
            packageName = packageName,
            screenshotPath = screenshotPath,
            hierarchyJson = hierarchyJson,
            nodeCount = nodeCount,
            tag = tag
        )

        val id = repository.saveCapture(capture)
        withContext(Dispatchers.Main) {
            Toast.makeText(this@StoryTestService, "Captured screen ($nodeCount nodes)", Toast.LENGTH_SHORT).show()
        }
        capture.copy(id = id)
    }

    private suspend fun captureScreenshotToFile(fileName: String): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return suspendCancellableCoroutine { cont ->
                try {
                    takeScreenshot(
                        android.view.Display.DEFAULT_DISPLAY,
                        mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshotResult: ScreenshotResult) {
                                serviceScope.launch(Dispatchers.IO) {
                                    try {
                                        val bitmap = Bitmap.wrapHardwareBuffer(
                                            screenshotResult.hardwareBuffer,
                                            screenshotResult.colorSpace
                                        )
                                        screenshotResult.hardwareBuffer.close()

                                        if (bitmap != null) {
                                            val dir = File(filesDir, "captures")
                                            if (!dir.exists()) dir.mkdirs()
                                            val file = File(dir, "$fileName.png")

                                            // Hardware bitmaps must be converted to software before compressing
                                            val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                            FileOutputStream(file).use { out ->
                                                softwareBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                                            }
                                            cont.resume(file.absolutePath)
                                        } else {
                                            cont.resume(null)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        cont.resume(null)
                                    }
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                cont.resume(null)
                            }
                        }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    cont.resume(null)
                }
            }
        }
        return null
    }

    // =========================================================================
    // AutomationActionHandler Implementation
    // =========================================================================

    override fun getRootPackageName(): String? {
        return rootInActiveWindow?.packageName?.toString()
    }

    override fun getCurrentHierarchy(): ScreenNode? {
        val windowRoots = mutableListOf<AccessibilityNodeInfo>()
        try {
            val allWindows = windows
            if (allWindows != null && allWindows.isNotEmpty()) {
                for (w in allWindows) {
                    val root = w.root
                    if (root != null) {
                        windowRoots.add(root)
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to active window
        }

        if (windowRoots.isEmpty()) {
            val fallback = rootInActiveWindow ?: return null
            return buildScreenNode(fallback)
        }

        if (windowRoots.size == 1) {
            return buildScreenNode(windowRoots[0])
        }

        // Virtual composite root containing all window hierarchies
        val children = windowRoots.map { buildScreenNode(it) }
        return ScreenNode(
            id = UUID.randomUUID().toString(),
            className = "android.view.View",
            children = children
        )
    }

    private fun buildScreenNode(node: AccessibilityNodeInfo): ScreenNode {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val children = mutableListOf<ScreenNode>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                children.add(buildScreenNode(child))
            }
        }

        return ScreenNode(
            id = UUID.randomUUID().toString(),
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewIdResourceName = node.viewIdResourceName,
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            clickable = node.isClickable,
            enabled = node.isEnabled,
            focused = node.isFocused,
            scrollable = node.isScrollable,
            visible = node.isVisibleToUser,
            bounds = NodeBounds(rect.left, rect.top, rect.right, rect.bottom),
            children = children
        )
    }

    override fun getAllScreenTexts(): List<String> {
        val root = getCurrentHierarchy() ?: return emptyList()
        val allNodes = root.flatten()
        val texts = mutableListOf<String>()
        for (n in allNodes) {
            n.text?.let { if (it.isNotBlank()) texts.add(it) }
            n.contentDescription?.let { if (it.isNotBlank()) texts.add(it) }
        }
        return texts
    }

    override fun findNodes(predicate: (ScreenNode) -> Boolean): List<ScreenNode> {
        val root = getCurrentHierarchy() ?: return emptyList()
        return root.flatten().filter(predicate)
    }

    override suspend fun launchApp(packageName: String): Boolean = withContext(Dispatchers.Main) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return@withContext false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startActivity(intent)
        true
    }

    override suspend fun performTapNode(node: ScreenNode): Boolean {
        android.util.Log.i("StoryBot", "tap: desc='${node.contentDescription}' text='${node.text}' visible=${node.visible} bounds=${node.bounds.left},${node.bounds.top},${node.bounds.right},${node.bounds.bottom}")
        val nativeNode = findNativeNode(node)
        if (nativeNode != null) {
            if (nativeNode.isClickable) {
                if (nativeNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            } else {
                // When a matched node is not clickable, tap its nearest clickable parent
                var current: AccessibilityNodeInfo? = nativeNode
                var clickableParent: AccessibilityNodeInfo? = null
                while (current != null) {
                    val parent = current.parent
                    if (parent != null && parent.isClickable) {
                        clickableParent = parent
                        break
                    }
                    current = parent
                }
                if (clickableParent != null && clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
        }

        // If there is none, tap the centre of its bounds with a gesture
        val cx = node.bounds.centerX.toFloat()
        val cy = node.bounds.centerY.toFloat()
        return dispatchGestureTap(cx, cy)
    }

    override suspend fun performLongPressNode(node: ScreenNode): Boolean {
        val nativeNode = findNativeNode(node)
        if (nativeNode != null) {
            if (nativeNode.isLongClickable) {
                if (nativeNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                    return true
                }
            } else {
                var current: AccessibilityNodeInfo? = nativeNode
                var clickableParent: AccessibilityNodeInfo? = null
                while (current != null) {
                    val parent = current.parent
                    if (parent != null && parent.isLongClickable) {
                        clickableParent = parent
                        break
                    }
                    current = parent
                }
                if (clickableParent != null && clickableParent.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                    return true
                }
            }
        }

        val cx = node.bounds.centerX.toFloat()
        val cy = node.bounds.centerY.toFloat()
        return dispatchGestureLongPress(cx, cy)
    }

    override suspend fun performTapXY(xPercent: Float, yPercent: Float): Boolean {
        val metrics = getDisplayMetrics()
        val x = (xPercent / 100f) * metrics.widthPixels
        val y = (yPercent / 100f) * metrics.heightPixels
        return dispatchGestureTap(x, y)
    }

    override suspend fun performTypeText(text: String): String? {
        // 1. Find the focused editable node (FOCUS_INPUT). If none, find the first editable node
        // on screen and perform ACTION_CLICK + ACTION_FOCUS on it, wait 300 ms.
        var targetNode: AccessibilityNodeInfo? = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (targetNode == null || !targetNode.isEditable) {
            val firstEditable = findFirstEditableInAllWindows()
            if (firstEditable != null) {
                firstEditable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                firstEditable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                delay(300L)
                val refreshedFocus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                targetNode = if (refreshedFocus != null && refreshedFocus.isEditable) {
                    refreshedFocus
                } else {
                    firstEditable
                }
            }
        }

        if (targetNode == null) {
            return null
        }

        // 2. Clear it: ACTION_SET_TEXT with an empty string, wait 200 ms.
        val clearArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
        delay(200L)

        // 3. Put the text on the clipboard (ClipboardManager.setPrimaryClip), wait 200 ms.
        withContext(Dispatchers.Main) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("StoryTest", text))
        }
        delay(200L)

        // 4. Perform ACTION_PASTE on the field, wait 500 ms.
        targetNode.refresh()
        targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        delay(500L)

        // 5. Re-read the field. If its text contains the wanted text, success.
        targetNode.refresh()
        var currentText = targetNode.text?.toString() ?: ""
        if (!currentText.contains(text, ignoreCase = false)) {
            val activeFocus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (activeFocus != null) {
                currentText = activeFocus.text?.toString() ?: ""
                targetNode = activeFocus
            }
        }
        if (currentText.contains(text, ignoreCase = false)) {
            return "paste"
        }

        // 6. Only if paste failed: fall back to ACTION_SET_TEXT with the full text.
        val setArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val setSuccess = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
        delay(200L)
        targetNode.refresh()
        val finalText = targetNode.text?.toString()
            ?: (rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.text?.toString() ?: "")
        if (finalText.contains(text, ignoreCase = false) || setSuccess) {
            return "set_text"
        }

        return null
    }

    override suspend fun performTypeLetterByLetter(text: String): Boolean {
        // Wait until focused (up to 3s)
        var targetNode: AccessibilityNodeInfo? = null
        val startFocusWait = System.currentTimeMillis()
        while (System.currentTimeMillis() - startFocusWait < 3000L) {
            val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) {
                targetNode = focused
                break
            }
            val firstEditable = findFirstEditableInAllWindows()
            if (firstEditable != null) {
                firstEditable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                firstEditable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                targetNode = firstEditable
                break
            }
            delay(200L)
        }

        if (targetNode == null) return false

        // Clear box: ACTION_SET_TEXT ""
        val clearArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
        delay(200L)

        // Normalized name (line breaks to spaces)
        val normalized = com.example.engine.NameMatcher.normalize(text)

        // Type one character at a time: 150 ms between characters
        for (i in 1..normalized.length) {
            val partial = normalized.substring(0, i)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, partial)
            }
            targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            delay(150L)
        }

        // After last character, wait 1s
        delay(1000L)

        // Add a space and remove it again (300ms apart)
        val spaceArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "$normalized ")
        }
        targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, spaceArgs)
        delay(300L)

        val removeSpaceArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, normalized)
        }
        targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, removeSpaceArgs)
        delay(300L)

        return true
    }

    override suspend fun performPasteOnly(text: String): Boolean {
        var targetNode: AccessibilityNodeInfo? = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (targetNode == null || !targetNode.isEditable) {
            val firstEditable = findFirstEditableInAllWindows()
            if (firstEditable != null) {
                firstEditable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                firstEditable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                delay(300L)
                val refreshedFocus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                targetNode = if (refreshedFocus != null && refreshedFocus.isEditable) refreshedFocus else firstEditable
            }
        }
        if (targetNode == null) return false

        // Clear box
        val clearArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
        delay(200L)

        // Put text on clipboard
        withContext(Dispatchers.Main) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("StoryTest", text))
        }
        delay(200L)

        targetNode.refresh()
        return targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun findFirstEditableInAllWindows(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { root ->
            val found = findFirstEditableNode(root)
            if (found != null) return found
        }
        try {
            for (window in windows) {
                val root = window.root ?: continue
                val found = findFirstEditableNode(root)
                if (found != null) return found
            }
        } catch (e: Exception) {
            // Windows list might not be accessible
        }
        return null
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    override suspend fun performScroll(down: Boolean): Boolean {
        val metrics = getDisplayMetrics()
        val startX = metrics.widthPixels / 2f
        val startY = if (down) metrics.heightPixels * 0.75f else metrics.heightPixels * 0.25f
        val endY = if (down) metrics.heightPixels * 0.25f else metrics.heightPixels * 0.75f
        return dispatchGestureSwipe(startX, startY, startX, endY, 400L)
    }

    override suspend fun performHorizontalSwipe(startXPercent: Float, endXPercent: Float, yPercent: Float): Boolean {
        val metrics = getDisplayMetrics()
        val startX = (startXPercent / 100f) * metrics.widthPixels
        val endX = (endXPercent / 100f) * metrics.widthPixels
        val y = (yPercent / 100f) * metrics.heightPixels
        return dispatchGestureSwipe(startX, y, endX, y, 400L)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        val cls = node.className?.toString() ?: ""
        if (cls.contains("RecyclerView", ignoreCase = true) ||
            cls.contains("ListView", ignoreCase = true) ||
            cls.contains("ScrollView", ignoreCase = true)
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
        }
        return null
    }

    override suspend fun scrollListForward(): Boolean {
        try {
            rootInActiveWindow?.let { root ->
                val scrollable = findScrollableNode(root)
                if (scrollable != null && scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        try {
            val allWindows = windows
            if (allWindows != null) {
                for (w in allWindows) {
                    val root = w.root ?: continue
                    val scrollable = findScrollableNode(root)
                    if (scrollable != null && scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return false
    }

    override suspend fun performVerticalSwipe(
        xPercent: Float,
        startYPercent: Float,
        endYPercent: Float,
        durationMs: Long
    ): Boolean {
        android.util.Log.i("StoryBot", "swipe: x=$xPercent% from $startYPercent% to $endYPercent% (${if (endYPercent > startYPercent) "finger down = scroll up" else "finger up = scroll down"})")
        val metrics = getDisplayMetrics()
        val x = (xPercent / 100f) * metrics.widthPixels
        val startY = (startYPercent / 100f) * metrics.heightPixels
        val endY = (endYPercent / 100f) * metrics.heightPixels
        return dispatchGestureSwipe(x, startY, x, endY, durationMs)
    }

    override suspend fun dispatchGlobal(action: Int): Boolean {
        return performGlobalAction(action)
    }

    override suspend fun pickNewestVideo(): Boolean {
        // Search current screen for media grid or first thumbnail
        val root = getCurrentHierarchy() ?: return false
        val allNodes = root.flatten()

        // 1. Look for image or video thumbnails
        val mediaNodes = allNodes.filter { n ->
            val cls = n.className ?: ""
            val desc = (n.contentDescription ?: "").lowercase()
            val id = (n.viewIdResourceName ?: "").lowercase()

            cls.contains("ImageView", ignoreCase = true) ||
                    desc.contains("video") || desc.contains("photo") || desc.contains("media") ||
                    id.contains("thumbnail") || id.contains("media") || id.contains("gallery")
        }.filter { it.bounds.width > 50 && it.bounds.height > 50 }

        if (mediaNodes.isNotEmpty()) {
            // Sort by top-to-bottom, left-to-right to get newest (first) item in grid
            val firstMedia = mediaNodes.minWithOrNull(compareBy({ it.bounds.top }, { it.bounds.left }))
            if (firstMedia != null) {
                return performTapNode(firstMedia)
            }
        }

        // Fallback: Tap roughly at the upper-middle gallery thumbnail spot (approx 25% X, 35% Y)
        return performTapXY(25f, 35f)
    }

    override suspend fun takeScreenshot(name: String): String? {
        return captureScreenshotToFile(name)
    }

    override suspend fun sleep(ms: Long) {
        delay(ms)
    }

    override suspend fun prepareModelVideo(modelCode: String, storyDate: String?): String? {
        return com.example.portal.ModelVideoScanner(this).prepareModelVideoForStory(modelCode, storyDate)
    }

    // =========================================================================
    // Gesture Helpers
    // =========================================================================

    private suspend fun dispatchGestureTap(x: Float, y: Float): Boolean {
        return suspendCancellableCoroutine { cont ->
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 100)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    cont.resume(false)
                }
            }, null)
        }
    }

    private suspend fun dispatchGestureLongPress(x: Float, y: Float): Boolean {
        return suspendCancellableCoroutine { cont ->
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 800)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    cont.resume(false)
                }
            }, null)
        }
    }

    private suspend fun dispatchGestureSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long): Boolean {
        return suspendCancellableCoroutine { cont ->
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    cont.resume(false)
                }
            }, null)
        }
    }

    private fun findNativeNode(target: ScreenNode): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { root ->
            val found = searchNativeNode(root, target)
            if (found != null) return found
        }
        try {
            val allWindows = windows
            if (allWindows != null) {
                for (w in allWindows) {
                    val root = w.root ?: continue
                    val found = searchNativeNode(root, target)
                    if (found != null) return found
                }
            }
        } catch (e: Exception) {
            // Windows list might not be accessible
        }
        return null
    }

    private fun searchNativeNode(current: AccessibilityNodeInfo, target: ScreenNode): AccessibilityNodeInfo? {
        val rect = Rect()
        current.getBoundsInScreen(rect)

        val matchesBounds = rect.left == target.bounds.left &&
                rect.top == target.bounds.top &&
                rect.right == target.bounds.right &&
                rect.bottom == target.bounds.bottom

        val matchesText = (current.text?.toString() == target.text) ||
                (current.contentDescription?.toString() == target.contentDescription)

        if (matchesBounds || (matchesText && target.text != null)) {
            return current
        }

        for (i in 0 until current.childCount) {
            val child = current.getChild(i)
            if (child != null) {
                val found = searchNativeNode(child, target)
                if (found != null) return found
            }
        }
        return null
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    // =========================================================================
    // Batch Runner Workflow
    // =========================================================================

    fun startBatchRun(
        script: ScriptEntity,
        pageNames: List<String>,
        link: String
    ) {
        batchRunJob?.cancel()
        timerJob?.cancel()

        val steps = JsonCodec.jsonToSteps(script.stepsJson)
        val validPages = pageNames.filter { it.isNotBlank() }

        val taskUuid = UUID.randomUUID().toString()
        _liveRunState.value = LiveRunState(
            isRunning = true,
            taskUuid = taskUuid,
            scriptName = script.name,
            totalPages = validPages.size,
            totalSteps = steps.size,
            currentPage = validPages.firstOrNull() ?: "",
            currentPageIndex = 0,
            currentStepNumber = 0,
            elapsedSeconds = 0,
            results = emptyList(),
            runLogs = listOf("Batch run initialized for ${validPages.size} pages"),
            recentSeenTexts = seenTextMemory.getAllEntries()
        )

        // Elapsed time ticker
        timerJob = serviceScope.launch {
            while (_liveRunState.value.isRunning && !_liveRunState.value.isPausedNeedsPerson) {
                delay(1000)
                _liveRunState.value = _liveRunState.value.copy(
                    elapsedSeconds = _liveRunState.value.elapsedSeconds + 1,
                    recentSeenTexts = seenTextMemory.getAllEntries()
                )
            }
        }

        batchRunJob = serviceScope.launch(Dispatchers.Default) {
            val safetyKeywords = repository.getActiveSafetyPhrases()
            val popupRules = repository.getActivePopupRules()
            val mutableResults = mutableListOf<RunResult>()
            val mutableRunLogs = mutableListOf<String>()

            fun appendRunLog(msg: String) {
                val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val formatted = "[$timeStr] $msg"
                mutableRunLogs.add(formatted)
                _liveRunState.value = _liveRunState.value.copy(
                    runLogs = mutableRunLogs.toList(),
                    recentSeenTexts = seenTextMemory.getAllEntries()
                )
            }

            appendRunLog("Starting batch run for script '${script.name}' (${steps.size} steps)")

            for ((pageIndex, pageName) in validPages.withIndex()) {
                if (!_liveRunState.value.isRunning) break

                _liveRunState.value = _liveRunState.value.copy(
                    currentPage = pageName,
                    currentPageIndex = pageIndex,
                    currentStepNumber = 0,
                    currentStepName = "Starting page: $pageName"
                )
                appendRunLog("--- Starting Page ${pageIndex + 1}/${validPages.size}: $pageName ---")

                val pageStartTime = System.currentTimeMillis()
                var previousStepStartTime = pageStartTime
                var pageFailed = false
                var failedStepName: String? = null
                var errorDetail: String? = null
                var failureCapturePath: String? = null
                val pageLogs = mutableListOf<String>()

                for ((stepIndex, rawStep) in steps.withIndex()) {
                    if (!_liveRunState.value.isRunning) break

                    val stepStartTime = System.currentTimeMillis()
                    _liveRunState.value = _liveRunState.value.copy(
                        currentStepNumber = stepIndex + 1,
                        currentStepName = rawStep.summary()
                    )

                    try {
                        val stepLog = stepRunner.executeStep(
                            rawStep = rawStep,
                            pageName = pageName,
                            link = link,
                            safetyKeywords = safetyKeywords,
                            popupRules = popupRules,
                            previousStepStartTime = previousStepStartTime,
                            onLog = { logMsg ->
                                appendRunLog(logMsg)
                                pageLogs.add(logMsg)
                            }
                        )
                        if (stepLog != null) {
                            pageLogs.add(stepLog)
                            appendRunLog("Step ${stepIndex + 1} ($pageName): $stepLog")
                        }
                    } catch (e: SafetyInterruptionException) {
                        // Needs a person: pause immediately!
                        triggerNeedsPersonAlert(e.matchedKeyword)
                        appendRunLog("ALERT: Needs a person! Triggered by '${e.matchedKeyword}'")
                        val duration = (System.currentTimeMillis() - pageStartTime) / 1000.0
                        val logSummary = if (pageLogs.isNotEmpty()) pageLogs.joinToString(", ") else null
                        mutableResults.add(
                            RunResult(
                                pageName = pageName,
                                status = RunStatus.PAUSED_NEEDS_PERSON,
                                failedStepName = rawStep.summary(),
                                durationSeconds = duration,
                                errorDetails = e.message,
                                logInfo = logSummary
                            )
                        )
                        _liveRunState.value = _liveRunState.value.copy(results = mutableResults.toList())
                        return@launch
                    } catch (e: Exception) {
                        // Step failed: capture screenshot, record failure, proceed to next page
                        pageFailed = true
                        failedStepName = rawStep.summary()
                        errorDetail = e.message ?: "Unknown step failure"
                        appendRunLog("Step ${stepIndex + 1} FAILED on $pageName: $errorDetail")

                        // Take screenshot on failure
                        val failureCapture = captureScreenAndStore("Failure on $pageName: $failedStepName")
                        failureCapturePath = failureCapture?.screenshotPath

                        break
                    } finally {
                        previousStepStartTime = stepStartTime
                    }
                }

                val duration = (System.currentTimeMillis() - pageStartTime) / 1000.0
                val logSummary = if (pageLogs.isNotEmpty()) pageLogs.joinToString(", ") else null
                val result = if (pageFailed) {
                    RunResult(
                        pageName = pageName,
                        status = RunStatus.FAILED,
                        failedStepName = failedStepName,
                        durationSeconds = duration,
                        errorDetails = errorDetail,
                        capturePath = failureCapturePath,
                        logInfo = logSummary
                    )
                } else {
                    RunResult(
                        pageName = pageName,
                        status = RunStatus.OK,
                        durationSeconds = duration,
                        logInfo = logSummary
                    )
                }
                mutableResults.add(result)
                _liveRunState.value = _liveRunState.value.copy(results = mutableResults.toList())
            }

            _liveRunState.value = _liveRunState.value.copy(
                isRunning = false,
                isCompleted = true
            )
            timerJob?.cancel()
        }
    }

    fun stopBatchRun(reason: String = "Stopped by user") {
        batchRunJob?.cancel()
        timerJob?.cancel()
        _liveRunState.value = _liveRunState.value.copy(
            isRunning = false,
            isPausedNeedsPerson = false,
            isStoppedByPerson = true,
            currentStepName = reason
        )
    }

    private fun triggerNeedsPersonAlert(keyword: String) {
        _liveRunState.value = _liveRunState.value.copy(
            isPausedNeedsPerson = true,
            needsPersonReason = "Detected security check: '$keyword'"
        )

        try {
            // Sound notification
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            ringtone?.play()

            // Vibration
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
