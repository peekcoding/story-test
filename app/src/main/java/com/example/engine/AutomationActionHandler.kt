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
    suspend fun performTypeText(text: String): Boolean
    suspend fun performScroll(down: Boolean): Boolean
    suspend fun dispatchGlobal(action: Int): Boolean
    suspend fun pickNewestVideo(): Boolean
    suspend fun takeScreenshot(name: String): String?
    suspend fun sleep(ms: Long)
}
