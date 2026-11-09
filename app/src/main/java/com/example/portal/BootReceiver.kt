package com.example.portal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.StoryTestApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val app = context.applicationContext as? StoryTestApp
            val repository = app?.portalRepository ?: PortalRepository(context.applicationContext)
            if (repository.isRegistered()) {
                PortalService.start(context.applicationContext)
            }
        }
    }
}
