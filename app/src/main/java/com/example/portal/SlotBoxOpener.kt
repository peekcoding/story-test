package com.example.portal

import android.content.Intent
import com.example.service.StoryTestService
import kotlinx.coroutines.delay

/** Opens the Facebook account in one slot. Implemented for SlotBox (ours) and Multi Space. */
interface AccountOpener {
    suspend fun open(service: StoryTestService, slot: Int, onLog: (String) -> Unit = {}): MultiSpaceOpener.Result
}

/**
 * Opens one Facebook account in SlotBox, our own ad-free clone app. SlotBox has an invisible door,
 * com.slotbox.OpenSlotActivity, that starts Facebook in the slot given in the extra "slot" (the slot number
 * is SlotBox's tile number and the portal's slot). No screen tapping is needed, so it is faster and more
 * reliable than driving Multi Space.
 */
class SlotBoxOpener : AccountOpener {

    override suspend fun open(service: StoryTestService, slot: Int, onLog: (String) -> Unit): MultiSpaceOpener.Result {
        val installed = try {
            service.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
        if (!installed) {
            return MultiSpaceOpener.Result.Failed("SlotBox is not installed on this phone")
        }

        onLog("Opening SlotBox slot $slot")
        try {
            service.startActivity(
                Intent()
                    .setClassName(PACKAGE, DOOR)
                    .putExtra("slot", slot)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            return MultiSpaceOpener.Result.Failed("Could not ask SlotBox to open slot $slot: ${e.message}")
        }

        // Facebook's Home screen: the first opening after a restart can take a while.
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 90_000L) {
            delay(500L)
            if (MultiSpaceOpener.isFacebookHome(service)) {
                onLog("SlotBox slot $slot is open")
                return MultiSpaceOpener.Result.Opened
            }
            if (MultiSpaceOpener.isLoggedOut(service)) {
                return MultiSpaceOpener.Result.NeedsPerson("SlotBox slot $slot is logged out. Log it in by hand in SlotBox.")
            }
            if (service.getAllScreenTexts().any { it.startsWith("SlotBox: ") }) {
                val message = service.getAllScreenTexts().first { it.startsWith("SlotBox: ") }.removePrefix("SlotBox: ")
                return MultiSpaceOpener.Result.Failed(message)
            }
        }
        return MultiSpaceOpener.Result.Failed("Facebook did not open in SlotBox slot $slot within 90 s")
    }

    companion object {
        const val PACKAGE = "com.slotbox.app"
        const val DOOR = "com.slotbox.OpenSlotActivity"
    }
}
