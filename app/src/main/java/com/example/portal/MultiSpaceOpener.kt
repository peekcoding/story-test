package com.example.portal

import com.example.data.model.ScreenNode
import com.example.service.StoryTestService
import kotlinx.coroutines.delay

/**
 * Opens one Facebook account (a "slot") inside the Multi Space app (M2).
 *
 * Read from the real app (com.dualspacepro.multispace): the home grid has one tile per Facebook copy, with
 * the copy's number in a TextView with id ":id/tvUserId" and its name in ":id/tvAppName"; the tappable area is
 * the view ":id/vClick" over the tile. Tapping it shows "Opening <name> ..." and then Facebook, which looks the
 * same as the normal app (Home tab, Menu, Create story) but reports the Multi Space package. Launching Multi
 * Space again brings back the grid. The slot number in the portal is the tile number.
 */
class MultiSpaceOpener(private val packageName: String = PACKAGE) {

    sealed class Result {
        object Opened : Result()
        data class Failed(val message: String) : Result()

        /** The copy is logged out or shows a check: a person must help (never bypassed). */
        data class NeedsPerson(val message: String) : Result()
    }

    suspend fun open(service: StoryTestService, slot: Int, onLog: (String) -> Unit = {}): Result {
        onLog("Opening Multi Space account $slot")
        if (!service.launchApp(packageName)) {
            return Result.Failed("Multi Space is not installed on this phone")
        }

        // 1. The grid of accounts (slow phones: up to 20 s).
        if (!waitFor(20_000L) { tiles(service).isNotEmpty() }) {
            return Result.Failed("Multi Space home screen did not show its accounts")
        }

        // 2. The tile with this number; scroll the grid if it is further down.
        var tile = findTile(service, slot)
        var scrolls = 0
        while (tile == null && scrolls < 6) {
            scrolls++
            if (!service.scrollListForward()) {
                service.performVerticalSwipe(50f, 75f, 40f, 700L)
            }
            delay(1_000L)
            tile = findTile(service, slot)
        }
        if (tile == null) {
            val numbers = tiles(service).mapNotNull { it.text?.trim() }.joinToString(", ")
            return Result.Failed("Account $slot is not in Multi Space (accounts there: $numbers)")
        }

        val name = tileName(service, tile)
        onLog("Tapping Multi Space account $slot${if (name != null) " ($name)" else ""}")
        val target = clickAreaFor(service, tile) ?: tile
        if (!service.performTapNode(target)) {
            return Result.Failed("Could not tap Multi Space account $slot")
        }

        // 3. Facebook's Home screen (opening a copy can take long on a slow phone or network).
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 90_000L) {
            if (isFacebookHome(service)) {
                onLog("Account $slot is open")
                return Result.Opened
            }
            if (isLoggedOut(service)) {
                return Result.NeedsPerson("Account $slot${if (name != null) " ($name)" else ""} is logged out in Multi Space. Log it in by hand.")
            }
            delay(500L)
        }
        return Result.Failed("Facebook did not open for Multi Space account $slot within 90 s")
    }

    private fun tiles(service: StoryTestService): List<ScreenNode> =
        service.findNodes { (it.viewIdResourceName ?: "").endsWith(":id/tvUserId") }

    private fun findTile(service: StoryTestService, slot: Int): ScreenNode? =
        tiles(service).firstOrNull { it.text?.trim() == slot.toString() }

    /** The name shown under the tile, e.g. "Aqib Jav". */
    private fun tileName(service: StoryTestService, tile: ScreenNode): String? {
        val names = service.findNodes { (it.viewIdResourceName ?: "").endsWith(":id/tvAppName") }
        return names.minByOrNull { name ->
            val dx = name.bounds.centerX - tile.bounds.centerX
            val dy = name.bounds.centerY - tile.bounds.centerY
            if (dy < 0) Int.MAX_VALUE else dx * dx + dy * dy
        }?.text?.trim()
    }

    /** The tappable ":id/vClick" view that covers the tile's number. */
    private fun clickAreaFor(service: StoryTestService, tile: ScreenNode): ScreenNode? =
        service.findNodes { (it.viewIdResourceName ?: "").endsWith(":id/vClick") }
            .firstOrNull { it.bounds.contains(tile.bounds.centerX, tile.bounds.centerY) }

    private suspend fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition()) return true
            delay(500L)
        }
        return false
    }

    companion object {
        const val PACKAGE = "com.dualspacepro.multispace"

        fun isFacebookHome(service: StoryTestService): Boolean =
            service.findNodes { (it.contentDescription ?: "").startsWith("Home, tab", ignoreCase = true) }.isNotEmpty()

        /** Facebook's log-in screen inside the copy. */
        fun isLoggedOut(service: StoryTestService): Boolean {
            val texts = service.getAllScreenTexts()
            return texts.any { it.equals("Log in", ignoreCase = true) } &&
                texts.any { it.contains("Create new account", ignoreCase = true) || it.contains("Forgot password", ignoreCase = true) }
        }
    }
}
