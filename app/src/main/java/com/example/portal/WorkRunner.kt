package com.example.portal

import com.example.data.StoryTestRepository
import com.example.data.model.StepType
import com.example.service.StoryTestService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Posts the portal's work: the "Post all now" button and the automatic start in [PortalService] both use
 * this, so posting keeps going when the app screen is closed. Only one run at a time.
 */
class WorkRunner(
    private val repository: StoryTestRepository,
    private val portalRepository: PortalRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val multiSpace = MultiSpaceOpener()

    /** Accounts that could not be opened are left alone for a while, so a broken one is not retried every 20 s. */
    private val slotCooldownUntil = mutableMapOf<Int, Long>()

    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Start posting unless a run is already going. Returns false when it could not start
     * (already running, or the accessibility service is off).
     */
    fun start(reason: String): Boolean {
        if (isRunning) return false
        val service = StoryTestService.serviceInstance.value
        if (service == null) {
            portalRepository.setPostingWorkState(false, "Accessibility Service is not enabled")
            return false
        }
        job = scope.launch {
            try {
                portalRepository.setPostingWorkState(true, if (reason == "auto") "Started automatically" else "Starting...")
                runAllWork(service)
            } finally {
                StoryTestService.updateLiveTaskState(null, null, false)
                if (portalRepository.deviceState.value.isPostingWork) {
                    portalRepository.setPostingWorkState(false, portalRepository.deviceState.value.postingStatusMessage)
                }
            }
        }
        return true
    }

    fun stop(message: String = "Posting cancelled") {
        job?.cancel()
        StoryTestService.serviceInstance.value?.stopBatchRun("Stopped by user")
        portalRepository.setPostingWorkState(false, message)
    }

    private suspend fun runAllWork(service: StoryTestService) {
        val useMultiSpace = portalRepository.useMultiSpace()
        // The Multi Space account (slot) whose Facebook is open right now; null = none yet.
        var openSlot: Int? = null

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

        val finishedTaskUuids = mutableSetOf<String>()

        while (currentCoroutineContext().isActive) {
            // 5. Before starting each next task, call GET /api/device/work to refresh task list
            portalRepository.getWork()
            val allSlots = portalRepository.deviceState.value.workSlots.sortedBy { it.slotNumber }

            // With Multi Space every account (slot) is posted, one account at a time. Without it only slot 1,
            // the normal Facebook app, can be posted.
            if (!useMultiSpace) {
                for (slot in allSlots) {
                    if (slot.slotNumber != 1) {
                        for (task in slot.tasks) {
                            portalRepository.updateTaskLocalStatus(task.uuid, "SKIPPED", "Slot ${slot.slotNumber} needs Multi Space (turn it on in the app)")
                        }
                    }
                }
            }
            val nowForCooldown = System.currentTimeMillis()
            val postableTasks = allSlots
                .filter { useMultiSpace || it.slotNumber == 1 }
                .filter { (slotCooldownUntil[it.slotNumber] ?: 0L) < nowForCooldown }
                .flatMap { it.tasks }
            if (postableTasks.isEmpty()) {
                portalRepository.setPostingWorkState(false, "Nothing to post")
                StoryTestService.updateLiveTaskState(null, null, false)
                break
            }

            // Find the next task that may run now. Scheduled tasks (starts_at in the future, by the portal's clock)
            // are not skipped: they are left for a later run, and the runner reports when the next one starts.
            val portalNowMs = portalRepository.portalNowMillis()
            val openTasks = postableTasks.filter { t -> !finishedTaskUuids.contains(t.uuid) }
            val task = openTasks.sortedBy { if (it.slotNumber == openSlot) -1 else it.slotNumber }.firstOrNull { t ->
                val startsAtMs = PortalTime.parseMillis(t.startsAt)
                startsAtMs == null || startsAtMs <= portalNowMs
            }

            if (task == null) {
                val nextStart = openTasks.mapNotNull { PortalTime.parseMillis(it.startsAt) }.minOrNull()
                for (t in openTasks) {
                    PortalTime.parseMillis(t.startsAt)?.let { ms ->
                        portalRepository.updateTaskLocalStatus(t.uuid, "WAITING", "Starts at ${PortalTime.formatLocal(ms)}")
                    }
                }
                val message = if (nextStart != null) "Waiting: next page starts at ${PortalTime.formatLocal(nextStart)}" else "All pages done"
                portalRepository.setPostingWorkState(false, message)
                StoryTestService.updateLiveTaskState(null, null, false)
                break
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

            // Open this task's account in Multi Space when another one (or none) is open, or when Facebook is
            // no longer on its Home screen.
            if (useMultiSpace && (openSlot != task.slotNumber || !MultiSpaceOpener.isFacebookHome(service))) {
                portalRepository.setPostingWorkState(true, "Opening account ${task.slotNumber} (${task.accountLabel ?: ""})...")
                portalRepository.sendReportEvents(task.uuid, "opening_account", "Multi Space account ${task.slotNumber}")
                StoryTestService.updateLiveTaskState(task.uuid, "opening_account", true)
                val opened = multiSpace.open(service, task.slotNumber)
                if (opened !is MultiSpaceOpener.Result.Opened) {
                    val needsPerson = opened is MultiSpaceOpener.Result.NeedsPerson
                    val reason = when (opened) {
                        is MultiSpaceOpener.Result.Failed -> opened.message
                        is MultiSpaceOpener.Result.NeedsPerson -> opened.message
                        else -> "Could not open the account"
                    }
                    portalRepository.sendTaskFinish(
                        taskUuid = task.uuid,
                        result = if (needsPerson) "needs_person" else "failed",
                        step = "opening_account",
                        error = reason,
                        seenTexts = service.getRecentSeenTexts().map { it.text }
                    )
                    portalRepository.updateTaskLocalStatus(task.uuid, if (needsPerson) "NEEDS_PERSON" else "FAILED", reason)
                    finishedTaskUuids.add(task.uuid)
                    // Leave this account's other pages for 10 minutes and carry on with the next account.
                    slotCooldownUntil[task.slotNumber] = System.currentTimeMillis() + 10 * 60_000L
                    openSlot = null
                    StoryTestService.updateLiveTaskState(null, null, false)
                    continue
                }
                openSlot = task.slotNumber
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
                if (useMultiSpace && rawStep.type == StepType.LAUNCH_APP) {
                    continue
                }
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
            // Stay inside Facebook for the next page: go back to Facebook's Home screen, never to the phone's home.
            returnToFacebookHome(service)

            // Refresh from /work after every task
            portalRepository.getWork()
        }

        StoryTestService.updateLiveTaskState(null, null, false)
        portalRepository.setPostingWorkState(false, "Finished all pages")
        portalRepository.getWork()
    }

    /**
     * Press Back (at most 6 times) until Facebook's Home screen is showing. Does nothing when it already is,
     * so Facebook is never closed by an extra Back.
     */
    private suspend fun returnToFacebookHome(service: StoryTestService) {
        repeat(6) {
            val onHome = service.findNodes { n ->
                (n.contentDescription ?: "").startsWith("Home, tab", ignoreCase = true)
            }.isNotEmpty()
            if (onHome) return
            service.dispatchGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            delay(700L)
        }
    }
}
