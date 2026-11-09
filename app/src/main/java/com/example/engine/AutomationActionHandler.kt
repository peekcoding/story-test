package com.example.engine

import com.example.data.model.ScreenNode

interface AutomationActionHandler {
    fun getRootPackageName(): String?
    fun getCurrentHierarchy(): ScreenNode?
    fun getAllScreenTexts(): List<String>
    fun findNodes(predicate: (ScreenNode) -> Boolean): List<ScreenNode>
    suspend fun launchApp(packageName: String): Boolean
    suspend fun performTapNode(node: ScreenNode): Boolean
    suspend fun performLongPressNode(node: ScreenNode): Boolean
    suspend fun performTapXY(xPercent: Float, yPercent: Float): Boolean
    suspend fun performTypeText(text: String): String?
    suspend fun performTypeLetterByLetter(text: String): Boolean
    suspend fun performPasteOnly(text: String): Boolean
    suspend fun performScroll(down: Boolean): Boolean
    suspend fun scrollListForward(): Boolean
    suspend fun performHorizontalSwipe(startXPercent: Float, endXPercent: Float, yPercent: Float): Boolean
    suspend fun performVerticalSwipe(xPercent: Float, startYPercent: Float, endYPercent: Float, durationMs: Long = 400L): Boolean
    suspend fun dispatchGlobal(action: Int): Boolean
    suspend fun pickNewestVideo(): Boolean
    suspend fun takeScreenshot(name: String): String?
    suspend fun sleep(ms: Long)
    suspend fun prepareModelVideo(modelCode: String): Boolean
    fun hasSeenTextAfter(text: String, afterTimestamp: Long): Boolean
    fun getRecentSeenTexts(): List<com.example.data.model.SeenTextEntry>
}
