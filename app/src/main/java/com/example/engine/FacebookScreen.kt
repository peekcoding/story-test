package com.example.engine

import com.example.data.model.ScreenNode
import kotlinx.coroutines.delay

/**
 * Where Facebook's Home feed and Menu are (checked on a real phone, 2026-11-11). Facebook has two layouts:
 * - Menu (three bars) at the top right ("Menu"), or
 * - Menu as the last bottom tab ("Menu, tab 6 of 6", profile picture with three bars).
 * When the feed is scrolled (also right after opening, as Facebook keeps the old scroll position), Facebook
 * hides the top bar and the bottom tabs. One small scroll up (finger down) brings them back.
 *
 * The bottom tabs are visible on every tab, so "Home, tab" does not mean the Home feed is showing. The feed
 * is recognised by the Facebook logo or the "What's on your mind?" box; on the Menu tab neither is shown.
 */
object FacebookScreen {

    fun isHomeNode(n: ScreenNode): Boolean {
        val d = n.contentDescription ?: ""
        val t = n.text ?: ""
        return d.equals("Facebook logo", ignoreCase = true) ||
            d.startsWith("What's on your mind", ignoreCase = true) ||
            t.startsWith("What's on your mind", ignoreCase = true)
    }

    fun isMenuNode(n: ScreenNode): Boolean {
        val d = (n.contentDescription ?: "").trim()
        return d.equals("Menu", ignoreCase = true) || d.startsWith("Menu,", ignoreCase = true)
    }

    fun isHome(handler: AutomationActionHandler): Boolean = handler.findNodes(::isHomeNode).isNotEmpty()

    /**
     * The Menu button. The bottom tab ("Menu, tab N of N") is preferred: the Menu screen also has a "Menu"
     * title at the top that is not a button.
     */
    fun findMenu(handler: AutomationActionHandler): ScreenNode? {
        val nodes = handler.findNodes(::isMenuNode)
        return nodes.firstOrNull { (it.contentDescription ?: "").startsWith("Menu,", ignoreCase = true) }
            ?: nodes.firstOrNull { it.clickable }
            ?: nodes.firstOrNull()
    }

    /** Bring back the hidden top bar and bottom tabs: one small scroll up (finger moves down). */
    suspend fun revealBars(handler: AutomationActionHandler) {
        handler.performVerticalSwipe(50f, 42f, 55f, 300L)
        delay(1_200L)
    }

    /** Find Menu, scrolling up a little to reveal the bars up to [tries] times when it is hidden. */
    suspend fun findMenuRevealing(handler: AutomationActionHandler, tries: Int = 3): ScreenNode? {
        repeat(tries + 1) { attempt ->
            findMenu(handler)?.let { return it }
            if (attempt < tries) revealBars(handler)
        }
        return null
    }

    /** Wait for the Home feed; every few seconds without it, scroll up a little in case the bars are hidden. */
    suspend fun waitForHome(handler: AutomationActionHandler, timeoutMs: Long, isStopRequested: () -> Boolean = { false }): Boolean {
        val start = System.currentTimeMillis()
        var lastReveal = start
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isStopRequested()) return false
            if (isHome(handler)) return true
            if (System.currentTimeMillis() - lastReveal > 4_000L) {
                revealBars(handler)
                lastReveal = System.currentTimeMillis()
                if (isHome(handler)) return true
            }
            delay(500L)
        }
        return false
    }
}
