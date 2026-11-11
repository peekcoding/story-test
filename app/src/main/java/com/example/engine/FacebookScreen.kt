package com.example.engine

import com.example.data.model.ScreenNode
import kotlinx.coroutines.delay

/**
 * Where Facebook's Home and Menu are. Facebook has two layouts:
 * - Menu (three bars) at the top right, or
 * - Menu as the last bottom tab ("Menu, tab 6 of 6", with the profile picture and three bars).
 * When the feed is scrolled, Facebook hides the top bar and the bottom tab bar. Scrolling a little down and
 * then up brings them back.
 */
object FacebookScreen {

    fun isHomeNode(n: ScreenNode): Boolean {
        val d = n.contentDescription ?: ""
        return d.startsWith("Home, tab", ignoreCase = true) || d.equals("Facebook logo", ignoreCase = true)
    }

    fun isMenuNode(n: ScreenNode): Boolean {
        val d = (n.contentDescription ?: "").trim()
        // "Menu" at the top right, "Menu, tab 6 of 6" in the bottom bar.
        return d.equals("Menu", ignoreCase = true) || d.startsWith("Menu,", ignoreCase = true)
    }

    fun isHome(handler: AutomationActionHandler): Boolean = handler.findNodes(::isHomeNode).isNotEmpty()

    fun findMenu(handler: AutomationActionHandler): ScreenNode? =
        handler.findNodes(::isMenuNode).firstOrNull { it.clickable } ?: handler.findNodes(::isMenuNode).firstOrNull()

    /** Bring back the hidden top bar and bottom tabs: scroll the feed a little down, then up. */
    suspend fun revealBars(handler: AutomationActionHandler) {
        handler.performVerticalSwipe(50f, 60f, 48f, 350L) // finger up: feed moves down a little
        delay(600L)
        handler.performVerticalSwipe(50f, 40f, 65f, 350L) // finger down: feed moves up, bars come back
        delay(1_000L)
    }

    /** Find Menu, revealing the bars up to [tries] times when it is hidden. */
    suspend fun findMenuRevealing(handler: AutomationActionHandler, tries: Int = 3): ScreenNode? {
        repeat(tries + 1) { attempt ->
            findMenu(handler)?.let { return it }
            if (attempt < tries) revealBars(handler)
        }
        return null
    }

    /**
     * Wait for Facebook's Home screen. Every few seconds without it, reveal the bars in case they are only
     * hidden by scrolling.
     */
    suspend fun waitForHome(handler: AutomationActionHandler, timeoutMs: Long, isStopRequested: () -> Boolean = { false }): Boolean {
        val start = System.currentTimeMillis()
        var lastReveal = start
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isStopRequested()) return false
            if (isHome(handler)) return true
            if (System.currentTimeMillis() - lastReveal > 4_000L && handler.findNodes { (it.packageName ?: "").isNotEmpty() }.isNotEmpty()) {
                revealBars(handler)
                lastReveal = System.currentTimeMillis()
                if (isHome(handler)) return true
            }
            delay(500L)
        }
        return false
    }
}
