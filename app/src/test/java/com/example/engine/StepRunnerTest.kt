package com.example.engine

import com.example.data.JsonCodec
import com.example.data.model.NodeBounds
import com.example.data.model.ScreenNode
import com.example.data.model.ScriptStep
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

    override fun getRootPackageName(): String? = "com.facebook.katana"

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

    override suspend fun performTapNode(node: ScreenNode): Boolean {
        tappedNodes.add(node)
        return true
    }

    override suspend fun performLongPressNode(node: ScreenNode): Boolean = true
    override suspend fun performTapXY(xPercent: Float, yPercent: Float): Boolean = true
    override suspend fun performTypeText(text: String): Boolean {
        typedTexts.add(text)
        return true
    }
    override suspend fun performScroll(down: Boolean): Boolean = true
    override suspend fun dispatchGlobal(action: Int): Boolean = true
    override suspend fun pickNewestVideo(): Boolean = true
    override suspend fun takeScreenshot(name: String): String? = "/path/to/screenshot.png"
    override suspend fun sleep(ms: Long) {}
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
    fun testJsonCodecRoundTrip() {
        val steps = listOf(
            ScriptStep(type = StepType.LAUNCH_APP, packageName = "com.facebook.katana"),
            ScriptStep(type = StepType.WAIT_TEXT, text = "Create story", timeoutMs = 8000L),
            ScriptStep(type = StepType.TAP_TEXT, text = "Create story", exact = true),
            ScriptStep(type = StepType.PICK_NEWEST_VIDEO),
            ScriptStep(type = StepType.TYPE_TEXT, text = "{{LINK}}"),
            ScriptStep(type = StepType.TAP_XY, xPercent = 25f, yPercent = 75f),
            ScriptStep(type = StepType.SCREENSHOT, screenshotName = "done_{{PAGE_NAME}}")
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
    }
}
