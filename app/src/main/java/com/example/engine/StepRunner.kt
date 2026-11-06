package com.example.engine

import com.example.data.model.ScreenNode
import com.example.data.model.ScriptStep
import com.example.data.model.StepType
import kotlinx.coroutines.delay

class SafetyInterruptionException(val matchedKeyword: String) :
    Exception("Safety check triggered: detected '$matchedKeyword'. Run stopped - Needs a person.")

class StepTimeoutException(val stepName: String, val timeoutMs: Long) :
    Exception("Step '$stepName' timed out after ${timeoutMs}ms")

class StepExecutionException(val stepName: String, message: String) :
    Exception("Step '$stepName' failed: $message")

class StepRunner(
    private val handler: AutomationActionHandler,
    private val retryIntervalMs: Long = 300L
) {

    /**
     * Executes a single step with variable resolution and safety evaluation.
     */
    suspend fun executeStep(
        rawStep: ScriptStep,
        pageName: String,
        link: String,
        safetyKeywords: List<String>
    ) {
        val step = VariableResolver.resolveStep(rawStep, pageName, link)
        val stepSummary = step.summary()

        when (step.type) {
            StepType.LAUNCH_APP -> {
                checkSafetyKeywords(safetyKeywords)
                val success = handler.launchApp(step.packageName)
                if (!success) {
                    throw StepExecutionException(stepSummary, "Failed to launch package: ${step.packageName}")
                }
            }

            StepType.TAP_TEXT -> {
                val node = waitForNode(step.timeoutMs, safetyKeywords, stepSummary) { n ->
                    val text = n.text ?: ""
                    if (step.exact) {
                        text.equals(step.text, ignoreCase = false)
                    } else {
                        text.contains(step.text, ignoreCase = true)
                    }
                }
                val clicked = handler.performTapNode(node)
                if (!clicked) {
                    throw StepExecutionException(stepSummary, "Could not tap node with text '${step.text}'")
                }
            }

            StepType.TAP_DESC -> {
                val node = waitForNode(step.timeoutMs, safetyKeywords, stepSummary) { n ->
                    val desc = n.contentDescription ?: ""
                    if (step.exact) {
                        desc.equals(step.text, ignoreCase = false)
                    } else {
                        desc.contains(step.text, ignoreCase = true)
                    }
                }
                val clicked = handler.performTapNode(node)
                if (!clicked) {
                    throw StepExecutionException(stepSummary, "Could not tap node with desc '${step.text}'")
                }
            }

            StepType.TAP_ID -> {
                val node = waitForNode(step.timeoutMs, safetyKeywords, stepSummary) { n ->
                    val id = n.viewIdResourceName ?: ""
                    id.endsWith(step.viewId) || id.equals(step.viewId, ignoreCase = true)
                }
                val clicked = handler.performTapNode(node)
                if (!clicked) {
                    throw StepExecutionException(stepSummary, "Could not tap node with id '${step.viewId}'")
                }
            }

            StepType.TAP_XY -> {
                checkSafetyKeywords(safetyKeywords)
                val tapped = handler.performTapXY(step.xPercent, step.yPercent)
                if (!tapped) {
                    throw StepExecutionException(stepSummary, "Could not tap coordinates ${step.xPercent}%, ${step.yPercent}%")
                }
            }

            StepType.LONG_PRESS_TEXT -> {
                val node = waitForNode(step.timeoutMs, safetyKeywords, stepSummary) { n ->
                    val text = n.text ?: ""
                    if (step.exact) {
                        text.equals(step.text, ignoreCase = false)
                    } else {
                        text.contains(step.text, ignoreCase = true)
                    }
                }
                val pressed = handler.performLongPressNode(node)
                if (!pressed) {
                    throw StepExecutionException(stepSummary, "Could not long press node with text '${step.text}'")
                }
            }

            StepType.TYPE_TEXT -> {
                checkSafetyKeywords(safetyKeywords)
                val typed = handler.performTypeText(step.text)
                if (!typed) {
                    throw StepExecutionException(stepSummary, "Failed to type text '${step.text}' into focused field")
                }
            }

            StepType.WAIT_TEXT -> {
                waitForNode(step.timeoutMs, safetyKeywords, stepSummary) { n ->
                    val text = n.text ?: ""
                    if (step.exact) {
                        text.equals(step.text, ignoreCase = false)
                    } else {
                        text.contains(step.text, ignoreCase = true)
                    }
                }
            }

            StepType.WAIT_DESC -> {
                waitForNode(step.timeoutMs, safetyKeywords, stepSummary) { n ->
                    val desc = n.contentDescription ?: ""
                    if (step.exact) {
                        desc.equals(step.text, ignoreCase = false)
                    } else {
                        desc.contains(step.text, ignoreCase = true)
                    }
                }
            }

            StepType.SCROLL_DOWN -> {
                checkSafetyKeywords(safetyKeywords)
                handler.performScroll(down = true)
            }

            StepType.SCROLL_UP -> {
                checkSafetyKeywords(safetyKeywords)
                handler.performScroll(down = false)
            }

            StepType.BACK -> {
                checkSafetyKeywords(safetyKeywords)
                // 1 = GLOBAL_ACTION_BACK
                handler.dispatchGlobal(1)
            }

            StepType.HOME -> {
                checkSafetyKeywords(safetyKeywords)
                // 2 = GLOBAL_ACTION_HOME
                handler.dispatchGlobal(2)
            }

            StepType.PICK_NEWEST_VIDEO -> {
                checkSafetyKeywords(safetyKeywords)
                val picked = handler.pickNewestVideo()
                if (!picked) {
                    throw StepExecutionException(stepSummary, "Could not find or tap media item in picker")
                }
            }

            StepType.SLEEP -> {
                checkSafetyKeywords(safetyKeywords)
                handler.sleep(step.sleepMs)
                checkSafetyKeywords(safetyKeywords)
            }

            StepType.SCREENSHOT -> {
                checkSafetyKeywords(safetyKeywords)
                handler.takeScreenshot(step.screenshotName.ifEmpty { "screenshot" })
            }
        }
    }

    /**
     * Continuously scans for a matching node every [retryIntervalMs] until [timeoutMs] expires.
     * Evaluates safety keywords before each poll.
     */
    suspend fun waitForNode(
        timeoutMs: Long,
        safetyKeywords: List<String>,
        stepSummary: String,
        predicate: (ScreenNode) -> Boolean
    ): ScreenNode {
        val startTime = System.currentTimeMillis()
        while (true) {
            checkSafetyKeywords(safetyKeywords)

            val matching = handler.findNodes(predicate)
            if (matching.isNotEmpty()) {
                return matching.first()
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= timeoutMs) {
                throw StepTimeoutException(stepSummary, timeoutMs)
            }

            delay(retryIntervalMs)
        }
    }

    /**
     * Inspects active screen texts against safety keywords.
     * Throws SafetyInterruptionException if any trigger is detected.
     */
    fun checkSafetyKeywords(keywords: List<String>) {
        if (keywords.isEmpty()) return
        val screenTexts = handler.getAllScreenTexts()
        for (screenText in screenTexts) {
            val lowerScreen = screenText.lowercase()
            for (keyword in keywords) {
                val lowerKeyword = keyword.trim().lowercase()
                if (lowerKeyword.isNotEmpty() && lowerScreen.contains(lowerKeyword)) {
                    throw SafetyInterruptionException(keyword)
                }
            }
        }
    }
}
