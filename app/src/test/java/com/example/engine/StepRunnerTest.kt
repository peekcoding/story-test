package com.example.engine

import com.example.data.JsonCodec
import com.example.data.model.NodeBounds
import com.example.data.model.PopupRuleEntity
import com.example.data.model.ScreenNode
import com.example.data.model.ScriptStep
import com.example.data.model.SeenTextEntry
import com.example.data.model.StepType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeAutomationActionHandler : AutomationActionHandler {
    var nodesToReturn = mutableListOf<ScreenNode>()
    var allTexts = mutableListOf<String>()
    var launchedPackages = mutableListOf<String>()
    var tappedNodes = mutableListOf<ScreenNode>()
    var typedTexts = mutableListOf<String>()
    var pollCount = 0
    var injectNodeAtPoll = -1
    var nodeToInject: ScreenNode? = null
    var swipeCount = 0
    var lastSwipeParams: Triple<Float, Float, Float>? = null
    val seenMemory = mutableListOf<SeenTextEntry>()

    var rootPackageNameToReturn: String? = "com.facebook.katana"
    override fun getRootPackageName(): String? = rootPackageNameToReturn

    override fun getCurrentHierarchy(): ScreenNode? {
        return ScreenNode(children = nodesToReturn)
    }

    override fun getAllScreenTexts(): List<String> = allTexts

    override fun findNodes(predicate: (ScreenNode) -> Boolean): List<ScreenNode> {
        pollCount++
        if (injectNodeAtPoll > 0 && pollCount >= injectNodeAtPoll && nodeToInject != null) {
            nodesToReturn.add(nodeToInject!!)
            nodeToInject = null
        }
        return nodesToReturn.filter(predicate)
    }

    override suspend fun launchApp(packageName: String): Boolean {
        launchedPackages.add(packageName)
        return true
    }

    var onNodeTapped: ((ScreenNode) -> Unit)? = null
    override suspend fun performTapNode(node: ScreenNode): Boolean {
        tappedNodes.add(node)
        onNodeTapped?.invoke(node)
        return true
    }

    override suspend fun performLongPressNode(node: ScreenNode): Boolean = true
    override suspend fun performTapXY(xPercent: Float, yPercent: Float): Boolean = true
    var typeTextResultToReturn: String? = "paste"
    override suspend fun performTypeText(text: String): String? {
        typedTexts.add(text)
        return typeTextResultToReturn
    }

    var typedLetterByLetter = mutableListOf<String>()
    override suspend fun performTypeLetterByLetter(text: String): Boolean {
        typedLetterByLetter.add(text)
        return true
    }

    var pastedTexts = mutableListOf<String>()
    override suspend fun performPasteOnly(text: String): Boolean {
        pastedTexts.add(text)
        return true
    }
    var scrollListForwardCount = 0
    var scrollListForwardResult = false
    override suspend fun scrollListForward(): Boolean {
        scrollListForwardCount++
        return scrollListForwardResult
    }
    override suspend fun performScroll(down: Boolean): Boolean = true
    override suspend fun performHorizontalSwipe(startXPercent: Float, endXPercent: Float, yPercent: Float): Boolean {
        swipeCount++
        lastSwipeParams = Triple(startXPercent, endXPercent, yPercent)
        return true
    }
    var verticalSwipeCount = 0
    var lastVerticalSwipeParams: Triple<Float, Float, Float>? = null
    var lastVerticalSwipeDuration: Long? = null
    override suspend fun performVerticalSwipe(
        xPercent: Float,
        startYPercent: Float,
        endYPercent: Float,
        durationMs: Long
    ): Boolean {
        verticalSwipeCount++
        lastVerticalSwipeParams = Triple(xPercent, startYPercent, endYPercent)
        lastVerticalSwipeDuration = durationMs
        return true
    }
    var dispatchedGlobals = mutableListOf<Int>()
    override suspend fun dispatchGlobal(action: Int): Boolean {
        dispatchedGlobals.add(action)
        return true
    }
    override suspend fun pickNewestVideo(): Boolean = true
    override suspend fun takeScreenshot(name: String): String? = "/path/to/screenshot.png"
    override suspend fun sleep(ms: Long) {}

    var prepareModelVideoResult: Boolean = true
    var preparedModelCodes = mutableListOf<String>()
    override suspend fun prepareModelVideo(modelCode: String): Boolean {
        preparedModelCodes.add(modelCode)
        return prepareModelVideoResult
    }

    override fun hasSeenTextAfter(text: String, afterTimestamp: Long): Boolean {
        val query = text.trim().lowercase()
        return seenMemory.any { it.timestamp >= afterTimestamp && it.text.lowercase().contains(query) }
    }

    override fun getRecentSeenTexts(): List<SeenTextEntry> = seenMemory
}

class StepRunnerTest {

    @Test
    fun testVariableReplacement() {
        val template = "Posting on {{PAGE_NAME}} with link {{LINK}}"
        val resolved = VariableResolver.resolve(
            template,
            pageName = "MyCoolPage",
            link = "https://story.link/123"
        )
        assertEquals("Posting on MyCoolPage with link https://story.link/123", resolved)

        val rawStep = ScriptStep(
            type = StepType.TYPE_TEXT,
            text = "{{LINK}}",
            viewId = "id/{{PAGE_NAME}}",
            screenshotName = "shot_{{PAGE_NAME}}"
        )
        val resolvedStep = VariableResolver.resolveStep(rawStep, "SportsHub", "https://promo.url")
        assertEquals("https://promo.url", resolvedStep.text)
        assertEquals("id/SportsHub", resolvedStep.viewId)
        assertEquals("shot_SportsHub", resolvedStep.screenshotName)
    }

    @Test
    fun testRetryUntilNodeFound() = runTest {
        val handler = FakeAutomationActionHandler()
        // Inject matching node on poll #3
        handler.injectNodeAtPoll = 3
        handler.nodeToInject = ScreenNode(
            text = "Create story",
            bounds = NodeBounds(100, 100, 300, 200),
            clickable = true
        )

        val runner = StepRunner(handler, retryIntervalMs = 20L)
        val step = ScriptStep(
            type = StepType.TAP_TEXT,
            text = "Create story",
            timeoutMs = 1000L
        )

        runner.executeStep(step, "TestPage", "https://link.com", emptyList())

        assertTrue("Should have polled multiple times", handler.pollCount >= 3)
        assertEquals(1, handler.tappedNodes.size)
        assertEquals("Create story", handler.tappedNodes.first().text)
    }

    @Test
    fun testTimeoutThrowsException() = runTest {
        val handler = FakeAutomationActionHandler()
        val runner = StepRunner(handler, retryIntervalMs = 20L)
        val step = ScriptStep(
            type = StepType.TAP_TEXT,
            text = "NonExistentButton",
            timeoutMs = 80L
        )

        try {
            runner.executeStep(step, "TestPage", "https://link.com", emptyList())
            fail("Expected StepTimeoutException was not thrown")
        } catch (e: StepTimeoutException) {
            assertTrue(e.message?.contains("timed out") == true)
        }
    }

    @Test
    fun testSafetyDetectionInterruptsRun() = runTest {
        val handler = FakeAutomationActionHandler()
        handler.allTexts.add("We detected unusual activity. Confirm it's you to continue.")

        val runner = StepRunner(handler, retryIntervalMs = 20L)
        val step = ScriptStep(
            type = StepType.WAIT_TEXT,
            text = "Next",
            timeoutMs = 500L
        )

        try {
            runner.executeStep(
                step,
                "TestPage",
                "https://link.com",
                listOf("Confirm it's you", "captcha", "security check")
            )
            fail("Expected SafetyInterruptionException was not thrown")
        } catch (e: SafetyInterruptionException) {
            assertEquals("Confirm it's you", e.matchedKeyword)
            assertTrue(e.message?.contains("Needs a person") == true)
        }
    }

    @Test
    fun testSwipeUntilTextFoundImmediately() = runTest {
        val handler = FakeAutomationActionHandler()
        handler.nodesToReturn.add(
            ScreenNode(text = "Add to story", bounds = NodeBounds(10, 10, 200, 100))
        )
        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.SWIPE_UNTIL_TEXT,
            text = "Add to story",
            yPercent = 60f,
            maxSwipes = 5
        )

        runner.executeStep(step, "Page1", "https://link", emptyList())
        assertEquals(0, handler.swipeCount)
    }

    @Test
    fun testSwipeUntilTextFoundAfterSwiping() = runTest {
        val handler = FakeAutomationActionHandler()
        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.SWIPE_UNTIL_TEXT,
            text = "Add link",
            yPercent = 70f,
            maxSwipes = 4
        )

        // Simulate node appearing after 2 swipes
        handler.injectNodeAtPoll = 3
        handler.nodeToInject = ScreenNode(text = "Add link", bounds = NodeBounds(50, 50, 150, 150))

        runner.executeStep(step, "Page1", "https://link", emptyList())
        assertEquals(2, handler.swipeCount)
        assertEquals(92f, handler.lastSwipeParams?.first)
        assertEquals(10f, handler.lastSwipeParams?.second)
        assertEquals(70f, handler.lastSwipeParams?.third)
    }

    @Test
    fun testSwipeUntilTextMatchesContentDescription() = runTest {
        val handler = FakeAutomationActionHandler()
        handler.nodesToReturn.add(
            ScreenNode(contentDescription = "Link option", bounds = NodeBounds(10, 10, 200, 100))
        )
        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.SWIPE_UNTIL_TEXT,
            text = "Link",
            exact = false,
            yPercent = 60f,
            maxSwipes = 5
        )

        runner.executeStep(step, "Page1", "https://link", emptyList())
        assertEquals(0, handler.swipeCount)
    }

    @Test
    fun testSwipeUntilTextFailsAfterMaxSwipes() = runTest {
        val handler = FakeAutomationActionHandler()
        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.SWIPE_UNTIL_TEXT,
            text = "NonExistentLinkOption",
            yPercent = 50f,
            maxSwipes = 3
        )

        try {
            runner.executeStep(step, "Page1", "https://link", emptyList())
            fail("Expected StepExecutionException was not thrown")
        } catch (e: StepExecutionException) {
            assertEquals(3, handler.swipeCount)
            assertTrue(e.message?.contains("Text not found after swiping") == true)
        }
    }

    @Test
    fun testJsonCodecRoundTrip() {
        val steps = listOf(
            ScriptStep(type = StepType.LAUNCH_APP, packageName = "com.facebook.katana"),
            ScriptStep(type = StepType.WAIT_TEXT, text = "Create story", timeoutMs = 8000L),
            ScriptStep(type = StepType.TAP_TEXT, text = "Create story", exact = true),
            ScriptStep(type = StepType.PICK_NEWEST_VIDEO),
            ScriptStep(type = StepType.TYPE_TEXT, text = "{{LINK}}"),
            ScriptStep(type = StepType.TAP_XY, xPercent = 25f, yPercent = 75f),
            ScriptStep(type = StepType.SCREENSHOT, screenshotName = "done_{{PAGE_NAME}}"),
            ScriptStep(type = StepType.SWIPE_UNTIL_TEXT, text = "See more", yPercent = 65f, maxSwipes = 3),
            ScriptStep(type = StepType.WAIT_SEEN, text = "Story shared", timeoutMs = 5000L)
        )

        val json = JsonCodec.stepsToJson(steps)
        val restored = JsonCodec.jsonToSteps(json)

        assertEquals(steps.size, restored.size)
        assertEquals(StepType.LAUNCH_APP, restored[0].type)
        assertEquals("com.facebook.katana", restored[0].packageName)
        assertEquals(StepType.WAIT_TEXT, restored[1].type)
        assertEquals(8000L, restored[1].timeoutMs)
        assertEquals(StepType.TAP_TEXT, restored[2].type)
        assertTrue(restored[2].exact)
        assertEquals(StepType.PICK_NEWEST_VIDEO, restored[3].type)
        assertEquals(StepType.TYPE_TEXT, restored[4].type)
        assertEquals("{{LINK}}", restored[4].text)
        assertEquals(25f, restored[5].xPercent, 0.01f)
        assertEquals(75f, restored[5].yPercent, 0.01f)
        assertEquals(StepType.SWIPE_UNTIL_TEXT, restored[7].type)
        assertEquals("See more", restored[7].text)
        assertEquals(65f, restored[7].yPercent, 0.01f)
        assertEquals(3, restored[7].maxSwipes)
        assertEquals(StepType.WAIT_SEEN, restored[8].type)
        assertEquals("Story shared", restored[8].text)
        assertEquals(5000L, restored[8].timeoutMs)
    }

    @Test
    fun testUnknownStepTypeThrowsDescriptiveError() {
        val badJson = """
            [
                {"type": "NON_EXISTENT_STEP_TYPE", "text": "foo"}
            ]
        """.trimIndent()

        try {
            JsonCodec.jsonToSteps(badJson)
            fail("Expected IllegalArgumentException for unknown step type")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unknown step NON_EXISTENT_STEP_TYPE, update the app") == true)
        }
    }

    @Test
    fun testTypeTextViaPasteReturnsPasteLog() = runTest {
        val handler = FakeAutomationActionHandler()
        handler.typeTextResultToReturn = "paste"
        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.TYPE_TEXT,
            text = "https://my-story-link.com"
        )

        val log = runner.executeStep(step, "Page1", "https://my-story-link.com", emptyList())
        assertEquals("typeText: paste", log)
        assertEquals(listOf("https://my-story-link.com"), handler.typedTexts)
    }

    @Test
    fun testTypeTextViaSetTextReturnsSetTextLog() = runTest {
        val handler = FakeAutomationActionHandler()
        handler.typeTextResultToReturn = "set_text"
        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.TYPE_TEXT,
            text = "fallback text"
        )

        val log = runner.executeStep(step, "Page1", "https://link", emptyList())
        assertEquals("typeText: set_text", log)
        assertEquals(listOf("fallback text"), handler.typedTexts)
    }

    @Test
    fun testTypeTextFailsWhenHandlerReturnsNull() = runTest {
        val handler = FakeAutomationActionHandler()
        handler.typeTextResultToReturn = null
        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.TYPE_TEXT,
            text = "will fail"
        )

        try {
            runner.executeStep(step, "Page1", "https://link", emptyList())
            fail("Expected StepExecutionException was not thrown")
        } catch (e: StepExecutionException) {
            assertTrue(e.message?.contains("Failed to type text") == true)
        }
    }

    @Test
    fun testWaitSeenPassesWhenInMemoryAfterPreviousStep() = runTest {
        val handler = FakeAutomationActionHandler()
        val prevStepTime = 1000L
        // Add seen text recorded after prevStepTime
        handler.seenMemory.add(SeenTextEntry(text = "Your Story was shared!", timestamp = 1200L))

        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.WAIT_SEEN,
            text = "Story was shared",
            timeoutMs = 2000L
        )

        var loggedMessage = ""
        val result = runner.executeStep(
            rawStep = step,
            pageName = "P1",
            link = "link",
            safetyKeywords = emptyList(),
            previousStepStartTime = prevStepTime,
            onLog = { loggedMessage = it }
        )

        assertEquals("waitSeen: in memory", result)
        assertTrue(loggedMessage.contains("detected in recent events"))
    }

    @Test
    fun testWaitSeenPassesWhenOnScreen() = runTest {
        val handler = FakeAutomationActionHandler()
        handler.nodesToReturn.add(
            ScreenNode(text = "Story shared successfully", bounds = NodeBounds(10, 10, 200, 50))
        )

        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.WAIT_SEEN,
            text = "Story shared",
            timeoutMs = 1000L
        )

        var loggedMessage = ""
        val result = runner.executeStep(
            rawStep = step,
            pageName = "P1",
            link = "link",
            safetyKeywords = emptyList(),
            onLog = { loggedMessage = it }
        )

        assertEquals("waitSeen: on screen", result)
        assertTrue(loggedMessage.contains("visible on screen"))
    }

    @Test
    fun testWaitSeenFailsAfterTimeoutWithNeverSawMessage() = runTest {
        val handler = FakeAutomationActionHandler()
        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.WAIT_SEEN,
            text = "Story shared",
            timeoutMs = 100L
        )

        try {
            runner.executeStep(step, "P1", "link", emptyList())
            fail("Expected StepExecutionException was not thrown")
        } catch (e: StepExecutionException) {
            assertTrue(e.message?.contains("Never saw: Story shared") == true)
        }
    }

    @Test
    fun testPopupRuleDismissalBeforeStep() = runTest {
        val handler = FakeAutomationActionHandler()
        // Add popup dialog to screen
        handler.nodesToReturn.add(ScreenNode(text = "Easily add to your story", bounds = NodeBounds(10, 10, 200, 50)))
        val notNowButton = ScreenNode(text = "Not now", bounds = NodeBounds(50, 60, 150, 100), clickable = true)
        handler.nodesToReturn.add(notNowButton)

        val rules = listOf(
            PopupRuleEntity(whenISee = "Easily add to your story", tapText = "Not now", isEnabled = true)
        )

        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.SLEEP,
            sleepMs = 50L
        )

        var popupDismissedLog = ""
        runner.executeStep(
            rawStep = step,
            pageName = "P1",
            link = "link",
            safetyKeywords = emptyList(),
            popupRules = rules,
            onLog = { popupDismissedLog = it }
        )

        assertTrue(handler.tappedNodes.contains(notNowButton))
        assertTrue(popupDismissedLog.contains("Popup closed: When I see \"Easily add to your story\" -> Tap \"Not now\""))
    }

    @Test
    fun testNameMatcherCurlyQuotesAndStrictEquality() {
        // Curly single quotes: ’ ‘ ʼ should match straight apostrophe '
        assertTrue(NameMatcher.matches("Leonor’s USA Fans", "Leonor's USA Fans"))
        assertTrue(NameMatcher.matches("Leonor‘s USA Fans", "Leonor's USA Fans"))
        assertTrue(NameMatcher.matches("Leonorʼs USA Fans", "Leonor's USA Fans"))

        // Multiple spaces and case insensitivity
        assertTrue(NameMatcher.matches("  Leonor   USA  Fans  ", "LEONOR USA FANS"))

        // Strict equality: "Leonor USA Fans" must NOT match "Leonor USA Fans 2"
        org.junit.Assert.assertFalse(NameMatcher.matches("Leonor USA Fans 2", "Leonor USA Fans"))
        org.junit.Assert.assertFalse(NameMatcher.matches("Leonor USA Fans", "Leonor USA Fans 2"))
        org.junit.Assert.assertFalse(NameMatcher.matches("Fans", "Leonor USA Fans"))
    }

    private fun createProfileRow(name: String, notifications: String? = null): ScreenNode {
        val desc = if (notifications != null) "$name, $notifications notifications" else name
        return ScreenNode(
            contentDescription = desc,
            clickable = true,
            children = listOf(
                ScreenNode(text = name)
            )
        )
    }

    @Test
    fun testLaunchAppSkipsWhenAlreadyInForeground() = runTest {
        val handler = FakeAutomationActionHandler()
        handler.nodesToReturn.add(ScreenNode(packageName = "com.facebook.katana"))

        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.LAUNCH_APP,
            packageName = "com.facebook.katana"
        )

        var logMsg = ""
        val result = runner.executeStep(
            rawStep = step,
            pageName = "P1",
            link = "link",
            safetyKeywords = emptyList(),
            onLog = { logMsg = it }
        )

        assertEquals("launchApp: skipped (already open)", result)
        assertEquals(0, handler.launchedPackages.size)
        assertTrue(logMsg.contains("already open"))
    }

    @Test
    fun testLaunchAppLaunchesWhenDifferentAppInForeground() = runTest {
        val handler = FakeAutomationActionHandler()
        handler.rootPackageNameToReturn = "com.android.launcher"

        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.LAUNCH_APP,
            packageName = "com.facebook.katana"
        )

        val result = runner.executeStep(
            rawStep = step,
            pageName = "P1",
            link = "link",
            safetyKeywords = emptyList()
        )

        assertEquals("launchApp: com.facebook.katana", result)
        assertEquals(1, handler.launchedPackages.size)
        assertEquals("com.facebook.katana", handler.launchedPackages.first())
    }

    @Test
    fun testSwitchPageAlreadyOnTargetPage() = runTest {
        val handler = FakeAutomationActionHandler()
        // 1. Home node
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Home, tab 1 of 5"))
        // 2. Menu button
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Menu"))
        // 3. Active page in menu
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Leonor’s USA Fans, see your profile"))

        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.SWITCH_PAGE,
            text = "Leonor's USA Fans",
            timeoutMs = 5000L
        )

        var loggedMessages = mutableListOf<String>()
        val result = runner.executeStep(
            rawStep = step,
            pageName = "Leonor's USA Fans",
            link = "link",
            safetyKeywords = emptyList(),
            onLog = { loggedMessages.add(it) }
        )

        assertEquals("Already on Leonor's USA Fans", result)
        assertTrue(handler.dispatchedGlobals.contains(1)) // Back dispatched
    }

    @Test
    fun testSwitchPageNotFoundWithViewAllCountLogsAndError() = runTest {
        val handler = FakeAutomationActionHandler()
        // 1. Home node
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Home, tab 1 of 5"))
        // 2. Menu button
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Menu"))
        // 3. Active page in menu (different page)
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Active Profile, see your profile"))
        // 4. Open profile switcher
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Open profile switcher"))
        // 5. Switcher Dismiss button
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Dismiss"))
        // 6. View all button with (44)
        handler.nodesToReturn.add(ScreenNode(text = "View all (44)", contentDescription = "View all (44)", clickable = true))
        // 7. Full list header
        handler.nodesToReturn.add(ScreenNode(text = "Your profiles & Pages"))
        // 8. Some profile rows
        handler.nodesToReturn.add(createProfileRow("Page 1"))
        handler.nodesToReturn.add(createProfileRow("Page 2"))

        var reportedEventStep = ""
        var reportedEventMessage: String? = null

        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.SWITCH_PAGE,
            text = "Non-Existent Page",
            timeoutMs = 5000L
        )

        val loggedMessages = mutableListOf<String>()
        try {
            runner.executeStep(
                rawStep = step,
                pageName = "Non-Existent Page",
                link = "link",
                safetyKeywords = emptyList(),
                onReportEvent = { stepName, msg ->
                    reportedEventStep = stepName
                    reportedEventMessage = msg
                    false
                },
                onLog = { loggedMessages.add(it) }
            )
            fail("Expected StepExecutionException was not thrown")
        } catch (e: StepExecutionException) {
            assertTrue(e.message?.contains("Page not found in profile list: Non-Existent Page (saw 2 of 44 profiles)") == true)
        }

        assertTrue(loggedMessages.any { it.contains("View all' says there are 44 profiles") || it.contains("44") })
        assertTrue(loggedMessages.any { it.contains("Tapping 'View all'...") })
        assertEquals("SWITCH_PAGE", reportedEventStep)
        assertTrue(reportedEventMessage?.startsWith("Not found after") == true)
    }

    @Test
    fun testSwitchPageFoundInFirstListImmediately() = runTest {
        val handler = FakeAutomationActionHandler()
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Home, tab 1 of 5"))
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Menu"))
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Active Profile, see your profile"))
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Open profile switcher"))
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Dismiss"))

        val targetNode = createProfileRow("Target Page 40")
        handler.nodesToReturn.add(targetNode)

        handler.onNodeTapped = { tapped ->
            if (tapped.contentDescription?.contains("Target Page 40") == true || tapped.text == "Target Page 40") {
                handler.nodesToReturn.removeAll { it.contentDescription?.contains("see your profile") == true }
                handler.nodesToReturn.add(ScreenNode(contentDescription = "Target Page 40, see your profile"))
            }
        }

        var reportedEventStep = ""
        var reportedEventMessage: String? = null

        val runner = StepRunner(handler)
        val step = ScriptStep(
            type = StepType.SWITCH_PAGE,
            text = "Target Page 40",
            timeoutMs = 5000L
        )

        val loggedMessages = mutableListOf<String>()
        val result = runner.executeStep(
            rawStep = step,
            pageName = "Target Page 40",
            link = "link",
            safetyKeywords = emptyList(),
            onReportEvent = { stepName, msg ->
                reportedEventStep = stepName
                reportedEventMessage = msg
                false
            },
            onLog = { loggedMessages.add(it) }
        )

        assertEquals("Switched to Target Page 40", result)
        assertEquals("SWITCH_PAGE", reportedEventStep)
        assertTrue(reportedEventMessage?.startsWith("Found in first list") == true)
        assertTrue(loggedMessages.any { it.contains("Found in first list") })
    }

    @Test
    fun testSwitchPageFoundAfterViewAllWithScrolls() = runTest {
        val handler = FakeAutomationActionHandler()
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Home, tab 1 of 5"))
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Menu"))
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Other Page, see your profile"))
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Open profile switcher"))
        handler.nodesToReturn.add(ScreenNode(contentDescription = "Dismiss"))
        handler.nodesToReturn.add(ScreenNode(text = "View all (10)", contentDescription = "View all (10)", clickable = true))
        handler.nodesToReturn.add(createProfileRow("Initial Sheet Page 1"))

        var reportedEventStep = ""
        var reportedEventMessage: String? = null

        handler.onNodeTapped = { tapped ->
            if (tapped.text?.contains("View all") == true || tapped.contentDescription?.contains("View all") == true) {
                // When View all tapped, show full list screen
                handler.nodesToReturn.clear()
                handler.nodesToReturn.add(ScreenNode(contentDescription = "Home, tab 1 of 5"))
                handler.nodesToReturn.add(ScreenNode(text = "Your profiles & Pages"))
                handler.nodesToReturn.add(createProfileRow("Full List Page 1"))
            } else if (tapped.contentDescription?.contains("Target Full List Page") == true || tapped.text == "Target Full List Page") {
                handler.nodesToReturn.clear()
                handler.nodesToReturn.add(ScreenNode(contentDescription = "Home, tab 1 of 5"))
                handler.nodesToReturn.add(ScreenNode(contentDescription = "Menu"))
                handler.nodesToReturn.add(ScreenNode(contentDescription = "Target Full List Page, see your profile"))
            }
        }

        // When scrolled, inject target page
        val customHandler = object : AutomationActionHandler by handler {
            override suspend fun performVerticalSwipe(
                xPercent: Float,
                startYPercent: Float,
                endYPercent: Float,
                durationMs: Long
            ): Boolean {
                handler.performVerticalSwipe(xPercent, startYPercent, endYPercent, durationMs)
                handler.nodesToReturn.add(createProfileRow("Target Full List Page"))
                return true
            }
        }

        val runner = StepRunner(customHandler)
        val step = ScriptStep(
            type = StepType.SWITCH_PAGE,
            text = "Target Full List Page",
            timeoutMs = 5000L
        )

        val loggedMessages = mutableListOf<String>()
        val result = runner.executeStep(
            rawStep = step,
            pageName = "Target Full List Page",
            link = "link",
            safetyKeywords = emptyList(),
            onReportEvent = { stepName, msg ->
                reportedEventStep = stepName
                reportedEventMessage = msg
                false
            },
            onLog = { loggedMessages.add(it) }
        )

        assertEquals("Switched to Target Full List Page", result)
        assertEquals("SWITCH_PAGE", reportedEventStep)
        assertTrue(reportedEventMessage?.startsWith("Found after View all, 1 scrolls") == true)
        assertTrue(loggedMessages.any { it.contains("Found after View all, 1 scrolls") })
    }

    @Test
    fun testTaskStopRequestedInterruptsWaitingLoop() = runTest {
        val handler = FakeAutomationActionHandler()
        var stopFlag = false

        val runner = StepRunner(
            handler = handler,
            isStopRequestedProvider = { stopFlag }
        )

        val step = ScriptStep(
            type = StepType.WAIT_SEEN,
            text = "NonExistentKeyword",
            timeoutMs = 10000L
        )

        // Request stop immediately
        stopFlag = true

        try {
            runner.executeStep(
                rawStep = step,
                pageName = "TestPage",
                link = "link",
                safetyKeywords = emptyList()
            )
            fail("Expected TaskStopRequestedException was not thrown")
        } catch (e: TaskStopRequestedException) {
            assertTrue(e.message?.contains("Task stop requested") == true || e.message?.contains("WAIT_SEEN") == true)
        }
    }
}
