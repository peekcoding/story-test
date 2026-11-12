package com.example.portal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.service.StoryTestService
import kotlinx.coroutines.delay

/**
 * The "Test phones" button in the portal: open the accounts app (Multi Space) so a person can see the phone
 * works, check what posting needs, and report the result. Never runs while the phone is posting.
 */
class PhoneTester(
    private val context: Context,
    private val repository: PortalRepository,
    private val runner: WorkRunner,
) {

    suspend fun run(uuid: String) {
        val useSlotBox = repository.accountsApp() == "slotbox"
        val appName = if (useSlotBox) "SlotBox" else "Multi Space"
        val appPackage = if (useSlotBox) SlotBoxOpener.PACKAGE else MultiSpaceOpener.PACKAGE
        val service = StoryTestService.serviceInstance.value

        val checks = mutableMapOf<String, Any?>(
            "accessibility" to (service != null),
            "auto_post" to repository.isAutoPostEnabled(),
            "accounts_app" to appName,
            "accounts_app_installed" to (context.packageManager.getLaunchIntentForPackage(appPackage) != null),
            "video_permission" to hasVideoPermission(),
            "battery_unrestricted" to isBatteryUnrestricted(),
            "posting_now" to runner.isRunning,
            "android" to Build.VERSION.RELEASE,
        )

        if (runner.isRunning) {
            repository.reportCommandResult(uuid, true, "Posting stories right now, so $appName was not opened. The phone works.", checks)
            return
        }

        val problems = mutableListOf<String>()
        var opened = false

        if (checks["accounts_app_installed"] != true) {
            problems += "$appName is not installed"
        } else if (service != null) {
            opened = service.launchApp(appPackage) && waitForApp(service, appPackage)
            if (!opened) problems += "$appName did not open"
        } else {
            // Without the accessibility service StoryBot can not tap anything; still try to open the app to show it.
            opened = try {
                context.startActivity(context.packageManager.getLaunchIntentForPackage(appPackage)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: Exception) {
                false
            }
        }
        checks["opened"] = opened

        if (service == null) problems += "Accessibility is OFF for StoryBot (Settings → Accessibility → StoryBot → On), so it can not post"
        if (checks["auto_post"] != true) problems += "\"Post automatically\" is OFF in StoryBot"
        if (checks["video_permission"] != true) problems += "StoryBot may not read videos (Settings → Model Videos → Report videos now → Allow)"
        if (checks["battery_unrestricted"] != true) problems += "Battery saving may stop StoryBot in the background (allow it to run without restrictions)"

        val ok = opened && service != null && checks["auto_post"] == true
        val message = if (problems.isEmpty()) {
            "Opened $appName. Ready to post."
        } else {
            (if (opened) "Opened $appName. " else "") + problems.joinToString(". ") + "."
        }
        android.util.Log.i("StoryBot", "Phone test: $message")
        repository.reportCommandResult(uuid, ok, message, checks)
    }

    private suspend fun waitForApp(service: StoryTestService, appPackage: String): Boolean {
        repeat(16) {
            if (service.getRootPackageName() == appPackage) return true
            delay(500L)
        }
        return false
    }

    private fun hasVideoPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_VIDEO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isBatteryUnrestricted(): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }
}
