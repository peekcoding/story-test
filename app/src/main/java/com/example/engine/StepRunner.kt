package com.example.engine

import com.example.data.model.PopupRuleEntity
import com.example.data.model.ScreenNode
import com.example.data.model.ScriptStep
import com.example.data.model.StepType
import kotlinx.coroutines.delay

class SafetyInterruptionException(val matchedKeyword: String) :
    Exception("Safety check triggered: detected '$matchedKeyword'. Run stopped - Needs a person.")

class StepTimeoutException(val stepName: String, val timeoutMs: Long) :
    Exception("Step '$stepName' timed out after ${timeoutMs}ms")

class StepExecutionException(
    val stepName: String,
    message: String,
    val isSwitchPageFailure: Boolean = false,
    val screenshotPath: String? = null
) : Exception("Step '$stepName' failed: $message")

class TaskStopRequestedException(val stepText: String) :
    Exception("Task stop requested by portal response at step '$stepText'")

class StepRunner(
    private val handler: AutomationActionHandler,
    private val retryIntervalMs: Long = 300L,
    private val isStopRequestedProvider: (() -> Boolean)? = null
) {

    /**
     * Executes a single step with variable resolution, popup auto-dismissal,
     * and safety evaluation.
     */
    suspend fun executeStep(
        rawStep: ScriptStep,
        pageName: String,
        link: String,
        safetyKeywords: List<String>,
        popupRules: List<PopupRuleEntity> = emptyList(),
        previousStepStartTime: Long = 0L,
        modelCode: String = "",
        onReportStep: (suspend (stepText: String) -> Boolean)? = null,
        onReportEvent: (suspend (stepText: String, message: String?) -> Boolean)? = null,
        isStopRequested: (() -> Boolean)? = null,
        onLog: (String) -> Unit = {}
    ): String? {
        val checkStop = isStopRequested ?: isStopRequestedProvider ?: { false }
        if (checkStop()) {
            throw TaskStopRequestedException("Task stop requested before step")
        }

        val reportEvent: suspend (String, String?) -> Boolean = { stepText, msg ->
            if (onReportEvent != null) {
                onReportEvent(stepText, msg)
            } else if (onReportStep != null) {
                onReportStep(stepText)
            } else false
        }

        val step = VariableResolver.resolveStep(rawStep, pageName, link, modelCode)
        val stepSummary = step.summary()

        // Check safety and popup rules before every step
        checkSafetyKeywords(safetyKeywords)
        checkAndDismissPopups(popupRules, onLog)

        when (step.type) {
            StepType.LAUNCH_APP -> {
                // Look at every window, not only the active one: the Story Test overlay can be the active window
                // while Facebook is open underneath. Relaunching would restart Facebook (and on phones with app
                // clones, ask which Facebook to open).
                val alreadyOpen = handler.findNodes { n -> (n.packageName ?: "").equals(step.packageName, ignoreCase = true) }.isNotEmpty()
                if (alreadyOpen) {
                    onLog("App '${step.packageName}' already open, skipping launch")
                    return "launchApp: skipped (already open)"
                }
                val success = handler.launchApp(step.packageName)
                if (!success) {
                    throw StepExecutionException(stepSummary, "Failed to launch package: ${step.packageName}")
                }
                return "launchApp: ${step.packageName}"
            }

            StepType.TAP_TEXT -> {
                val node = waitForNode(step.timeoutMs, safetyKeywords, popupRules, stepSummary, onLog, checkStop) { n ->
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
                return null
            }

            StepType.TAP_DESC -> {
                val node = waitForNode(step.timeoutMs, safetyKeywords, popupRules, stepSummary, onLog, checkStop) { n ->
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
                return null
            }

            StepType.TAP_ID -> {
                val node = waitForNode(step.timeoutMs, safetyKeywords, popupRules, stepSummary, onLog, checkStop) { n ->
                    val id = n.viewIdResourceName ?: ""
                    id.endsWith(step.viewId) || id.equals(step.viewId, ignoreCase = true)
                }
                val clicked = handler.performTapNode(node)
                if (!clicked) {
                    throw StepExecutionException(stepSummary, "Could not tap node with id '${step.viewId}'")
                }
                return null
            }

            StepType.TAP_XY -> {
                val tapped = handler.performTapXY(step.xPercent, step.yPercent)
                if (!tapped) {
                    throw StepExecutionException(stepSummary, "Could not tap coordinates ${step.xPercent}%, ${step.yPercent}%")
                }
                return null
            }

            StepType.LONG_PRESS_TEXT -> {
                val node = waitForNode(step.timeoutMs, safetyKeywords, popupRules, stepSummary, onLog, checkStop) { n ->
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
                return null
            }

            StepType.TYPE_TEXT -> {
                val method = handler.performTypeText(step.text)
                if (method == null) {
                    throw StepExecutionException(stepSummary, "Failed to type text '${step.text}' into focused field")
                }
                onLog("typeText: $method for '${step.text}'")
                return "typeText: $method"
            }

            StepType.WAIT_TEXT -> {
                waitForNode(step.timeoutMs, safetyKeywords, popupRules, stepSummary, onLog, checkStop) { n ->
                    val text = n.text ?: ""
                    if (step.exact) {
                        text.equals(step.text, ignoreCase = false)
                    } else {
                        text.contains(step.text, ignoreCase = true)
                    }
                }
                return null
            }

            StepType.WAIT_DESC -> {
                waitForNode(step.timeoutMs, safetyKeywords, popupRules, stepSummary, onLog, checkStop) { n ->
                    val desc = n.contentDescription ?: ""
                    if (step.exact) {
                        desc.equals(step.text, ignoreCase = false)
                    } else {
                        desc.contains(step.text, ignoreCase = true)
                    }
                }
                return null
            }

            StepType.SCROLL_DOWN -> {
                handler.performScroll(down = true)
                return null
            }

            StepType.SCROLL_UP -> {
                handler.performScroll(down = false)
                return null
            }

            StepType.BACK -> {
                // 1 = GLOBAL_ACTION_BACK
                handler.dispatchGlobal(1)
                return null
            }

            StepType.HOME -> {
                // 2 = GLOBAL_ACTION_HOME
                handler.dispatchGlobal(2)
                return null
            }

            StepType.PICK_NEWEST_VIDEO -> {
                val picked = handler.pickNewestVideo()
                if (!picked) {
                    throw StepExecutionException(stepSummary, "Could not find or tap media item in picker")
                }
                return null
            }

            StepType.SLEEP -> {
                val sleepEnd = System.currentTimeMillis() + step.sleepMs
                while (System.currentTimeMillis() < sleepEnd) {
                    if (checkStop()) {
                        throw TaskStopRequestedException("Stop requested during SLEEP")
                    }
                    val remaining = sleepEnd - System.currentTimeMillis()
                    delay(minOf(300L, remaining))
                }
                checkSafetyKeywords(safetyKeywords)
                checkAndDismissPopups(popupRules, onLog)
                return null
            }

            StepType.SCREENSHOT -> {
                handler.takeScreenshot(step.screenshotName.ifEmpty { "screenshot" })
                return null
            }

            StepType.SWIPE_UNTIL_TEXT -> {
                val nodePredicate: (ScreenNode) -> Boolean = { n ->
                    val text = n.text ?: ""
                    val desc = n.contentDescription ?: ""
                    if (step.exact) {
                        text.equals(step.text, ignoreCase = false) || desc.equals(step.text, ignoreCase = false)
                    } else {
                        text.contains(step.text, ignoreCase = true) || desc.contains(step.text, ignoreCase = true)
                    }
                }

                var found = handler.findNodes(nodePredicate).isNotEmpty()
                var swipesDone = 0
                val maxSwipes = if (step.maxSwipes > 0) step.maxSwipes else 5

                while (!found && swipesDone < maxSwipes) {
                    if (checkStop()) {
                        throw TaskStopRequestedException("Stop requested during SWIPE_UNTIL_TEXT")
                    }
                    checkSafetyKeywords(safetyKeywords)
                    checkAndDismissPopups(popupRules, onLog)
                    handler.performHorizontalSwipe(92f, 10f, step.yPercent)
                    val swipeSleepUntil = System.currentTimeMillis() + 800L
                    while (System.currentTimeMillis() < swipeSleepUntil) {
                        if (checkStop()) throw TaskStopRequestedException("Stop requested during SWIPE_UNTIL_TEXT wait")
                        val rem = swipeSleepUntil - System.currentTimeMillis()
                        delay(minOf(300L, rem))
                    }
                    checkSafetyKeywords(safetyKeywords)
                    checkAndDismissPopups(popupRules, onLog)
                    found = handler.findNodes(nodePredicate).isNotEmpty()
                    swipesDone++
                }

                if (!found) {
                    throw StepExecutionException(stepSummary, "Text not found after swiping")
                }
                return null
            }

            StepType.WAIT_SEEN -> {
                val wanted = step.text.trim()
                val timeout = if (step.timeoutMs > 0) step.timeoutMs else 10000L
                val startTime = System.currentTimeMillis()
                val checkAfterTime = if (previousStepStartTime > 0L) previousStepStartTime else startTime

                while (true) {
                    if (checkStop()) {
                        throw TaskStopRequestedException("Stop requested during WAIT_SEEN")
                    }
                    checkSafetyKeywords(safetyKeywords)
                    checkAndDismissPopups(popupRules, onLog)

                    // 1. Check if memory has a string containing text seen AFTER previous step started
                    if (handler.hasSeenTextAfter(wanted, checkAfterTime)) {
                        val logMsg = "waitSeen passed: '$wanted' (detected in recent events)"
                        onLog(logMsg)
                        return "waitSeen: in memory"
                    }

                    // 2. Check if current screen shows it
                    val visible = handler.findNodes { n ->
                        val t = n.text ?: ""
                        val d = n.contentDescription ?: ""
                        t.contains(wanted, ignoreCase = true) || d.contains(wanted, ignoreCase = true)
                    }
                    if (visible.isNotEmpty()) {
                        val logMsg = "waitSeen passed: '$wanted' (visible on screen)"
                        onLog(logMsg)
                        return "waitSeen: on screen"
                    }

                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed >= timeout) {
                        throw StepExecutionException(stepSummary, "Never saw: ${step.text}")
                    }

                    delay(300L)
                }
            }

            StepType.SWITCH_PAGE -> {
                val targetPage = step.text.trim()
                if (targetPage.isEmpty()) {
                    throw StepExecutionException(stepSummary, "Target page name is empty")
                }

                onLog("Executing SWITCH_PAGE for target: '$targetPage'")

                val isHomePredicate: (ScreenNode) -> Boolean = FacebookScreen::isHomeNode

                // Helper to wait for a condition every 300ms (no fixed sleeps)
                suspend fun waitForCondition(
                    timeoutMs: Long,
                    label: String,
                    condition: suspend () -> Boolean
                ): Boolean {
                    val start = System.currentTimeMillis()
                    while (System.currentTimeMillis() - start < timeoutMs) {
                        if (checkStop()) {
                            throw TaskStopRequestedException("Stop requested during condition '$label'")
                        }
                        checkSafetyKeywords(safetyKeywords)
                        checkInternetConnection(onLog, checkStop)
                        checkAndDismissPopups(popupRules, onLog)
                        if (condition()) return true
                        delay(300L)
                    }
                    return false
                }

                // Helper to capture failure snapshot and build failure exception
                suspend fun failSwitchPage(reason: String): Nothing {
                    onLog("SWITCH_PAGE FAILED: $reason")
                    // Save a capture (node tree JSON + screenshot)
                    val captureTag = "Failure on $targetPage: $reason"
                    val ssPath = handler.takeScreenshot("switch_fail_${System.currentTimeMillis()}")
                    val recentTexts = handler.getRecentSeenTexts().takeLast(20).map { it.text }
                    val seenSummary = if (recentTexts.isNotEmpty()) " | Last seen texts: [${recentTexts.joinToString(", ")}]" else ""
                    val fullError = "$reason$seenSummary"
                    throw StepExecutionException(
                        stepName = stepSummary,
                        message = fullError,
                        isSwitchPageFailure = true,
                        screenshotPath = ssPath
                    )
                }

                // 1. Wait up to 30 s for the Home screen (contentDescription starting "Home, tab").
                var lastReveal = System.currentTimeMillis()
                val homeReady = waitForCondition(30000L, "Home screen") {
                    if (handler.findNodes(isHomePredicate).isNotEmpty()) return@waitForCondition true
                    // The bars may only be hidden because the feed is scrolled: scroll a little down, then up.
                    if (System.currentTimeMillis() - lastReveal > 4_000L) {
                        FacebookScreen.revealBars(handler)
                        lastReveal = System.currentTimeMillis()
                    }
                    handler.findNodes(isHomePredicate).isNotEmpty()
                }
                if (!homeReady) {
                    failSwitchPage("Home screen not ready after 30s")
                }

                // 2. Tap "Menu". Wait up to 20 s for ", see your profile". If the active name matches the target: press Back and finish ("Already on <page>").
                // Menu is at the top right ("Menu") or the last bottom tab ("Menu, tab 6 of 6"); both bars hide
                // when the feed is scrolled, so reveal them if Menu is not visible.
                val menuNode = FacebookScreen.findMenuRevealing(handler, tries = 3)
                    ?: failSwitchPage("Menu button not found (looked at the top bar and the bottom tabs)")
                onLog("Menu found: '${menuNode.contentDescription}'")
                handler.performTapNode(menuNode)

                var activePage = ""
                val profileReady = waitForCondition(20000L, ", see your profile in Menu") {
                    val matching = handler.findNodes { n ->
                        (n.contentDescription ?: "").endsWith(", see your profile", ignoreCase = true)
                    }
                    if (matching.isNotEmpty()) {
                        val raw = matching.first().contentDescription ?: ""
                        val idx = raw.lastIndexOf(", see your profile", ignoreCase = true)
                        activePage = if (idx >= 0) raw.substring(0, idx).trim() else raw.trim()
                        true
                    } else false
                }
                if (!profileReady) {
                    failSwitchPage("Active profile in menu not found after 20s")
                }
                onLog("Current active profile in Menu: '$activePage'")

                if (NameMatcher.matches(activePage, targetPage)) {
                    handler.dispatchGlobal(1) // Back
                    waitForCondition(10000L, "Home screen after Back") { handler.findNodes(isHomePredicate).isNotEmpty() }
                    val msg = "Already on $targetPage"
                    onLog(msg)
                    return msg
                }

                // 3. Tap "Open profile switcher". Wait up to 20 s for "Dismiss" and at least one profile row.
                val switcherButton = handler.findNodes { n ->
                    (n.contentDescription ?: "").startsWith("Open profile switcher", ignoreCase = true)
                }.firstOrNull() ?: run {
                    val found = waitForCondition(5000L, "Open profile switcher button") {
                        handler.findNodes { (it.contentDescription ?: "").startsWith("Open profile switcher", ignoreCase = true) }.isNotEmpty()
                    }
                    if (!found) failSwitchPage("Open profile switcher button not found")
                    handler.findNodes { (it.contentDescription ?: "").startsWith("Open profile switcher", ignoreCase = true) }.first()
                }
                handler.performTapNode(switcherButton)

                val isDismissPredicate: (ScreenNode) -> Boolean = { n ->
                    val d = n.contentDescription ?: ""
                    val t = n.text ?: ""
                    d.equals("Dismiss", ignoreCase = true) || t.equals("Dismiss", ignoreCase = true)
                }

                /**
                 * Profile rows in the switcher sheet and in "Your profiles & Pages" are CLICKABLE nodes whose
                 * contentDescription is "<Name>" or "<Name>, <n> notifications", with a TEXT child holding the
                 * name. Only those count as profiles; "200 notifications", "Page" and titles never do.
                 */
                val notificationsSuffix = Regex(""",\s*[\d.,]+\+?\s*notifications?.*$""", RegexOption.IGNORE_CASE)

                fun isControlLabel(desc: String): Boolean {
                    val d = desc.trim()
                    return d.equals("Dismiss", true) || d.equals("Close", true) || d.equals("Menu", true) ||
                        d.startsWith("View all", true) || d.startsWith("See all", true) ||
                        d.startsWith("NavigationBar", true) || d.startsWith("Open profile switcher", true) ||
                        d.contains("see your profile", true) || d.contains("switch into your", true) ||
                        d.equals("Create Facebook Page", true) || d.equals("Go to Accounts Center", true)
                }

                /** Real profile names on screen (normalised), each with the clickable row to tap. */
                fun visibleProfiles(): List<Pair<String, ScreenNode>> {
                    return handler.findNodes { n -> n.clickable && !n.contentDescription.isNullOrBlank() }
                        .mapNotNull { row ->
                            val desc = row.contentDescription ?: return@mapNotNull null
                            if (isControlLabel(desc)) return@mapNotNull null
                            val name = NameMatcher.normalize(desc.replace(notificationsSuffix, ""))
                            if (name.isEmpty()) return@mapNotNull null
                            val hasNameText = row.flatten().any { child ->
                                child !== row && !child.text.isNullOrBlank() && NameMatcher.normalize(child.text!!).equals(name, ignoreCase = true)
                            }
                            if (hasNameText) name to row else null
                        }
                        .distinctBy { it.first.lowercase() }
                }

                fun visibleProfileNames(): Set<String> = visibleProfiles().map { it.first.lowercase() }.toSet()

                /** The row to tap for the target page: a profile row whose name matches, or a text node that matches. */
                fun findTargetRowNode(): ScreenNode? {
                    visibleProfiles().firstOrNull { NameMatcher.matches(it.first, targetPage) }?.let { return it.second }
                    return handler.findNodes { n -> NameMatcher.matches(n.text, targetPage) }.firstOrNull()
                }

                fun isLoading(): Boolean {
                    return handler.findNodes { n ->
                        (n.className ?: "").contains("ProgressBar", ignoreCase = true) ||
                            (n.contentDescription ?: "").contains("Loading", ignoreCase = true) ||
                            (n.text ?: "").contains("Loading", ignoreCase = true)
                    }.isNotEmpty()
                }

                var loadingWaits = 0

                /**
                 * Scroll the list one step without skipping rows, then wait for new rows (slow internet:
                 * up to 15 s, or up to 60 s while a loading indicator is shown).
                 * Returns true when the list actually scrolled (false at the end of the list).
                 */
                suspend fun scrollOneStep(): Boolean {
                    val before = visibleProfileNames()
                    var scrolled = handler.scrollListForward()
                    if (!scrolled) {
                        handler.performVerticalSwipe(50f, 75f, 40f, 900L)
                    }
                    val start = System.currentTimeMillis()
                    var changed = false
                    var sawLoading = false
                    var loops = 0
                    while (true) {
                        if (checkStop()) throw TaskStopRequestedException("Stop requested while scrolling the profile list")
                        checkSafetyKeywords(safetyKeywords)
                        checkInternetConnection(onLog, checkStop)
                        checkAndDismissPopups(popupRules, onLog)
                        if (findTargetRowNode() != null) return true
                        val now = visibleProfileNames()
                        if (now.isNotEmpty() && now != before) {
                            changed = true
                            break
                        }
                        loops++
                        val elapsed = System.currentTimeMillis() - start
                        val loading = isLoading()
                        if (loading && (elapsed < 60000L && loops < 200)) {
                            if (!sawLoading) {
                                sawLoading = true
                                loadingWaits++
                                onLog("Waiting for list to load...")
                            }
                        } else if (elapsed >= 15000L || loops >= 50) {
                            break
                        }
                        delay(300L)
                    }
                    return scrolled || changed
                }

                fun findViewAllNode(): ScreenNode? {
                    return handler.findNodes { n ->
                        val t = n.text ?: ""
                        val d = n.contentDescription ?: ""
                        t.startsWith("View all", ignoreCase = true) || d.startsWith("View all", ignoreCase = true) ||
                            t.startsWith("See all", ignoreCase = true) || d.startsWith("See all", ignoreCase = true)
                    }.firstOrNull { it.clickable } ?: handler.findNodes { n ->
                        (n.text ?: "").startsWith("View all", ignoreCase = true) || (n.contentDescription ?: "").startsWith("View all", ignoreCase = true)
                    }.firstOrNull()
                }

                val sheetReady = waitForCondition(20000L, "Dismiss button and profile rows in switcher sheet") {
                    handler.findNodes(isDismissPredicate).isNotEmpty() && visibleProfiles().isNotEmpty()
                }
                if (!sheetReady) {
                    failSwitchPage("Switcher sheet not ready after 20s")
                }

                var targetRowNode: ScreenNode? = null
                var viewAllExpectedCount: Int? = null
                var foundMethodReport: String? = null
                val allSeenProfileNames = mutableSetOf<String>()
                var fullListScrolls = 0

                allSeenProfileNames.addAll(visibleProfileNames())

                // 4. Look at the visible rows of the sheet.
                val initialSheetMatch = findTargetRowNode()
                if (initialSheetMatch != null) {
                    onLog("Target page row '$targetPage' found immediately in sheet")
                    targetRowNode = initialSheetMatch
                    foundMethodReport = "Found in first list"
                } else {
                    // 5. Scroll the sheet (up to 4 steps) until the target or "View all" shows.
                    var viewAllNode = findViewAllNode()
                    var sheetScrolls = 0
                    while (viewAllNode == null && sheetScrolls < 4) {
                        sheetScrolls++
                        onLog("Scrolling sheet step $sheetScrolls/4...")
                        scrollOneStep()
                        allSeenProfileNames.addAll(visibleProfileNames())
                        val sheetMatch = findTargetRowNode()
                        if (sheetMatch != null) {
                            onLog("Target page row '$targetPage' found after scrolling sheet")
                            targetRowNode = sheetMatch
                            foundMethodReport = "Found in first list"
                            break
                        }
                        viewAllNode = findViewAllNode()
                    }

                    if (targetRowNode == null && viewAllNode != null) {
                        val rawText = (viewAllNode.text ?: "") + " " + (viewAllNode.contentDescription ?: "")
                        Regex("""\(\s*(\d+)\s*\)""").find(rawText)?.groupValues?.get(1)?.toIntOrNull()?.let { count ->
                            viewAllExpectedCount = count
                            onLog("'View all' says there are $count profiles")
                        }
                        onLog("Tapping 'View all'...")
                        handler.performTapNode(viewAllNode)

                        // Wait up to 30 s for "Your profiles & Pages" with at least one profile row (slow internet).
                        val fullListReady = waitForCondition(30000L, "Your profiles & Pages with at least one row") {
                            val hasTitle = handler.findNodes { n ->
                                (n.text ?: "").contains("Your profiles & Pages", ignoreCase = true) ||
                                    (n.contentDescription ?: "").contains("Your profiles & Pages", ignoreCase = true)
                            }.isNotEmpty()
                            hasTitle && visibleProfiles().isNotEmpty()
                        }
                        if (!fullListReady) {
                            failSwitchPage("Full list screen 'Your profiles & Pages' not ready after 30s")
                        }

                        // 6. Full list: look, scroll one step, look again, until found or the real end of the list.
                        allSeenProfileNames.addAll(visibleProfileNames())
                        val initialFullListMatch = findTargetRowNode()
                        if (initialFullListMatch != null) {
                            targetRowNode = initialFullListMatch
                            foundMethodReport = "Found after View all, 0 scrolls"
                        } else {
                            var stepsWithoutNewNames = 0
                            while (fullListScrolls < 80) {
                                fullListScrolls++
                                val prevSize = allSeenProfileNames.size
                                val couldScroll = scrollOneStep()
                                allSeenProfileNames.addAll(visibleProfileNames())

                                val match = findTargetRowNode()
                                if (match != null) {
                                    targetRowNode = match
                                    foundMethodReport = "Found after View all, $fullListScrolls scrolls"
                                    break
                                }

                                if (allSeenProfileNames.size == prevSize) stepsWithoutNewNames++ else stepsWithoutNewNames = 0
                                onLog("Full list step $fullListScrolls: ${allSeenProfileNames.size} profiles seen, could scroll=$couldScroll")

                                // End of the list: the list can not scroll further AND no new names came for 4 steps.
                                if (stepsWithoutNewNames >= 4 && !couldScroll) {
                                    onLog("Reached the end of the profile list.")
                                    break
                                }
                                // Safety: nothing new for 8 steps in a row, whatever the scroll result.
                                if (stepsWithoutNewNames >= 8) {
                                    onLog("No new profiles for 8 steps, stopping.")
                                    break
                                }
                            }
                        }
                    }
                }

                if (foundMethodReport != null) {
                    val total = viewAllExpectedCount?.let { " of $it" } ?: ""
                    val loadNote = if (loadingWaits > 0) ", waited for loading $loadingWaits times" else ""
                    foundMethodReport = "$foundMethodReport (${allSeenProfileNames.size}$total profiles seen$loadNote)"
                    onLog("Target page row '$targetPage': $foundMethodReport")
                }

                // 7. Not found
                if (targetRowNode == null) {
                    val notFoundMsg = "Not found after $fullListScrolls scrolls (${allSeenProfileNames.size} profiles seen)"
                    onLog(notFoundMsg)
                    reportEvent("SWITCH_PAGE", notFoundMsg)

                    // Tap "Close" (or press Back) until on the Home screen
                    for (i in 0..5) {
                        if (handler.findNodes(isHomePredicate).isNotEmpty()) break
                        val closeBtn = handler.findNodes { n ->
                            val d = n.contentDescription ?: ""
                            val t = n.text ?: ""
                            d.equals("Close", ignoreCase = true) || t.equals("Close", ignoreCase = true)
                        }.firstOrNull()
                        if (closeBtn != null) {
                            handler.performTapNode(closeBtn)
                        } else {
                            handler.dispatchGlobal(1) // Back
                        }
                        delay(500L)
                    }

                    val ssPath = handler.takeScreenshot("switch_fail_${System.currentTimeMillis()}")
                    val seenCount = allSeenProfileNames.size
                    val totalN = if (viewAllExpectedCount != null) "$viewAllExpectedCount" else "$seenCount"
                    throw StepExecutionException(
                        stepName = stepSummary,
                        message = "Page not found in profile list: $targetPage (saw $seenCount of $totalN profiles)",
                        isSwitchPageFailure = true,
                        screenshotPath = ssPath
                    )
                }

                // Report in the run log and as REPORT how it was found
                if (foundMethodReport != null) {
                    onLog(foundMethodReport)
                    val stopReq = reportEvent("SWITCH_PAGE", foundMethodReport)
                    if (stopReq) {
                        throw TaskStopRequestedException("Stop requested by portal response at SWITCH_PAGE ($foundMethodReport)")
                    }
                }

                // 8. After tapping the row: wait up to 10 s for "Switching to" (optional), then up to 90 s for Home.
                onLog("Tapping target row for '$targetPage'...")
                handler.performTapNode(targetRowNode)

                val observedSwitching = waitForCondition(10000L, "Switching to message") {
                    handler.findNodes { n ->
                        val t = n.text ?: ""
                        val d = n.contentDescription ?: ""
                        t.contains("Switching to", ignoreCase = true) || d.contains("Switching to", ignoreCase = true)
                    }.isNotEmpty()
                }
                if (observedSwitching) {
                    onLog("Observed 'Switching to' message")
                }

                val homeReturned = waitForCondition(90000L, "Home screen after switch") {
                    handler.findNodes(isHomePredicate).isNotEmpty()
                }
                if (!homeReturned) {
                    failSwitchPage("Home screen did not return within 90s after tapping profile")
                }

                // 9. Check: tap "Menu", wait up to 20 s for ", see your profile", compare with target, press Back.
                val checkMenu = FacebookScreen.findMenuRevealing(handler, tries = 3)
                    ?: failSwitchPage("Menu button not found for the check after switching")
                handler.performTapNode(checkMenu)

                var verifiedActive = ""
                val checkReady = waitForCondition(20000L, "Active profile for verification") {
                    val matching = handler.findNodes { n ->
                        (n.contentDescription ?: "").endsWith(", see your profile", ignoreCase = true)
                    }
                    if (matching.isNotEmpty()) {
                        val raw = matching.first().contentDescription ?: ""
                        val idx = raw.lastIndexOf(", see your profile", ignoreCase = true)
                        verifiedActive = if (idx >= 0) raw.substring(0, idx).trim() else raw.trim()
                        true
                    } else false
                }

                // Close menu
                handler.dispatchGlobal(1) // Back
                delay(500L)

                if (!checkReady || !NameMatcher.matches(verifiedActive, targetPage)) {
                    failSwitchPage("Switched to wrong page: $verifiedActive")
                }

                val resultMsg = "Switched to $targetPage"
                onLog(resultMsg)
                return resultMsg
            }

            StepType.REPORT -> {
                onLog("REPORT: ${step.text}")
                val shouldStop = reportEvent(step.text, null)
                if (shouldStop) {
                    throw TaskStopRequestedException(step.text)
                }
                return "report: ${step.text}"
            }

            StepType.PREPARE_MODEL_VIDEO -> {
                onLog("Preparing newest model video for model '$modelCode'...")
                val success = handler.prepareModelVideo(modelCode)
                if (!success) {
                    throw StepExecutionException(stepSummary, "No video in Movies/PageShare/$modelCode/")
                }
                onLog("Prepared newest model video in DCIM/PageShareStory/")
                return "prepareModelVideo: success"
            }
        }
    }

    /**
     * Checks active popup rules. If a rule's "When I see" text is on screen,
     * taps the node matching its "Tap" text, waits 1s, and writes to log.
     */
    suspend fun checkAndDismissPopups(
        popupRules: List<PopupRuleEntity>,
        onLog: (String) -> Unit
    ) {
        if (popupRules.isEmpty()) return
        for (rule in popupRules) {
            if (!rule.isEnabled || rule.whenISee.isBlank() || rule.tapText.isBlank()) continue
            val trigger = rule.whenISee.trim()
            val matchesSee = handler.findNodes { n ->
                val t = n.text ?: ""
                val d = n.contentDescription ?: ""
                t.contains(trigger, ignoreCase = true) || d.contains(trigger, ignoreCase = true)
            }

            if (matchesSee.isNotEmpty()) {
                val tapTarget = rule.tapText.trim()
                val matchesTap = handler.findNodes { n ->
                    val t = n.text ?: ""
                    val d = n.contentDescription ?: ""
                    t.equals(tapTarget, ignoreCase = true) || d.equals(tapTarget, ignoreCase = true) ||
                            t.contains(tapTarget, ignoreCase = true) || d.contains(tapTarget, ignoreCase = true)
                }

                if (matchesTap.isNotEmpty()) {
                    val clicked = handler.performTapNode(matchesTap.first())
                    if (clicked) {
                        delay(1000L)
                        val logMsg = "Popup closed: When I see \"${rule.whenISee}\" -> Tap \"${rule.tapText}\""
                        onLog(logMsg)
                    }
                }
            }
        }
    }

    /**
     * Continuously scans for a matching node every [retryIntervalMs] until [timeoutMs] expires.
     * Evaluates safety keywords and popup rules on every poll.
     */
    suspend fun waitForNode(
        timeoutMs: Long,
        safetyKeywords: List<String>,
        popupRules: List<PopupRuleEntity>,
        stepSummary: String,
        onLog: (String) -> Unit,
        checkStop: () -> Boolean = { false },
        predicate: (ScreenNode) -> Boolean
    ): ScreenNode {
        val startTime = System.currentTimeMillis()
        while (true) {
            if (checkStop()) {
                throw TaskStopRequestedException("Stop requested during waitForNode ($stepSummary)")
            }
            checkSafetyKeywords(safetyKeywords)
            checkInternetConnection(onLog, checkStop)
            checkAndDismissPopups(popupRules, onLog)

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

    /**
     * If "No internet connection" or "Couldn't refresh" is on screen,
     * wait up to 2 minutes (120,000 ms) for it to go before failing.
     */
    suspend fun checkInternetConnection(onLog: (String) -> Unit, checkStop: () -> Boolean = { false }) {
        val internetErrors = listOf("No internet connection", "Couldn't refresh")
        fun hasInternetError(): Boolean {
            val texts = handler.getAllScreenTexts()
            return texts.any { text ->
                internetErrors.any { err -> text.contains(err, ignoreCase = true) }
            }
        }

        if (hasInternetError()) {
            onLog("Waiting for internet: detected connection error on screen...")
            val waitStart = System.currentTimeMillis()
            while (hasInternetError()) {
                if (checkStop()) {
                    throw TaskStopRequestedException("Stop requested during internet wait")
                }
                if (System.currentTimeMillis() - waitStart > 120000L) {
                    throw StepExecutionException("Internet Connection", "No internet connection after waiting 2 minutes")
                }
                delay(300L)
            }
            onLog("Internet connection restored, continuing execution")
        }
    }
}
