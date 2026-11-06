package com.example.data.model

import java.util.UUID

enum class StepType(val displayName: String, val description: String) {
    LAUNCH_APP("Launch App", "Launches the specified app package"),
    TAP_TEXT("Tap Text", "Finds and taps a node with text"),
    TAP_DESC("Tap Description", "Finds and taps a node with content description"),
    TAP_ID("Tap View ID", "Finds and taps a node with viewId resource name"),
    TAP_XY("Tap Coordinates (%)", "Taps screen position by percentage (X%, Y%)"),
    LONG_PRESS_TEXT("Long Press Text", "Long presses node with text"),
    TYPE_TEXT("Type Text", "Types text into the currently focused input field"),
    WAIT_TEXT("Wait for Text", "Waits until matching text appears on screen"),
    WAIT_DESC("Wait for Description", "Waits until matching content description appears"),
    SCROLL_DOWN("Scroll Down", "Performs a scroll down gesture"),
    SCROLL_UP("Scroll Up", "Performs a scroll up gesture"),
    BACK("Press Back", "Performs system Back navigation"),
    HOME("Press Home", "Performs system Home navigation"),
    PICK_NEWEST_VIDEO("Pick Newest Video", "Taps the first media item in gallery/picker"),
    SLEEP("Sleep / Wait", "Pauses execution for specified milliseconds"),
    SCREENSHOT("Take Screenshot", "Captures screenshot and stores capture")
}

data class ScriptStep(
    val id: String = UUID.randomUUID().toString(),
    val type: StepType,
    val text: String = "",
    val exact: Boolean = false,
    val index: Int = 0,
    val viewId: String = "",
    val xPercent: Float = 50f,
    val yPercent: Float = 50f,
    val timeoutMs: Long = 10000L,
    val sleepMs: Long = 1000L,
    val screenshotName: String = "",
    val packageName: String = ""
) {
    fun summary(): String {
        return when (type) {
            StepType.LAUNCH_APP -> "launchApp($packageName)"
            StepType.TAP_TEXT -> "tapText(\"$text\", exact=$exact, index=$index)"
            StepType.TAP_DESC -> "tapDesc(\"$text\", exact=$exact, index=$index)"
            StepType.TAP_ID -> "tapId(\"$viewId\")"
            StepType.TAP_XY -> "tapXY(${xPercent}%, ${yPercent}%)"
            StepType.LONG_PRESS_TEXT -> "longPressText(\"$text\")"
            StepType.TYPE_TEXT -> "typeText(\"$text\")"
            StepType.WAIT_TEXT -> "waitText(\"$text\", ${timeoutMs}ms)"
            StepType.WAIT_DESC -> "waitDesc(\"$text\", ${timeoutMs}ms)"
            StepType.SCROLL_DOWN -> "scrollDown()"
            StepType.SCROLL_UP -> "scrollUp()"
            StepType.BACK -> "back()"
            StepType.HOME -> "home()"
            StepType.PICK_NEWEST_VIDEO -> "pickNewestVideo()"
            StepType.SLEEP -> "sleep(${sleepMs}ms)"
            StepType.SCREENSHOT -> "screenshot(\"$screenshotName\")"
        }
    }
}
