package com.example.portal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.StoryTestApp
import com.example.service.StoryTestService
import com.example.portal.model.HeartbeatResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PortalService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var heartbeatJob: Job? = null
    private lateinit var portalRepository: PortalRepository

    companion object {
        private const val CHANNEL_ID = "portal_service_channel"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_STOP = "com.example.portal.STOP_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, PortalService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PortalService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        portalRepository = (application as StoryTestApp).portalRepository
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startHeartbeatLoop()
        startAutoPostLoop()
        startVideoReportingLoop()
        registerMediaObserver()

        return START_STICKY
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                if (!portalRepository.isRegistered()) {
                    // Token deleted or unregistered -> stop heartbeat & service
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }

                val (battery, isCharging) = getBatteryStatus()
                val liveState = StoryTestService.liveRunState.value
                val isRunning = liveState.isRunning && !liveState.taskUuid.isNullOrBlank()
                val taskUuid = if (isRunning) liveState.taskUuid else null
                val stepName = if (isRunning) liveState.currentStepName else null

                val result = portalRepository.sendHeartbeat(
                    battery = battery,
                    charging = isCharging,
                    currentTaskUuid = taskUuid,
                    currentStep = stepName
                )

                if (result is HeartbeatResult.Unauthorized) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }

                // Heartbeat interval: 10s while running a task, 30s when idle
                val delayMs = if (isRunning) 10_000L else 30_000L
                delay(delayMs)
            }
        }
    }

    private var autoPostJob: Job? = null

    /**
     * Starts posting by itself when the portal has pages for this phone that may start now (campaign started,
     * or its scheduled time reached by the portal's clock). Checks every 20 seconds while idle, and wakes up
     * exactly at the next scheduled start. Does nothing while a run is going or when "Post automatically" is off.
     */
    private fun startAutoPostLoop() {
        autoPostJob?.cancel()
        autoPostJob = serviceScope.launch {
            delay(5_000L)
            while (isActive) {
                var waitMs = 20_000L
                val runner = (application as StoryTestApp).workRunner
                if (portalRepository.isRegistered() && portalRepository.isAutoPostEnabled() && !runner.isRunning &&
                    StoryTestService.serviceInstance.value != null
                ) {
                    val result = portalRepository.getWork()
                    if (result is com.example.portal.model.GetWorkResult.Success) {
                        val nowMs = portalRepository.portalNowMillis()
                        val slot1Tasks = result.response.slots.filter { it.slotNumber == 1 }.flatMap { it.tasks }
                        val startsAt = slot1Tasks.map { PortalTime.parseMillis(it.startsAt) }
                        if (startsAt.any { it == null || it <= nowMs }) {
                            runner.start("auto")
                        } else {
                            startsAt.filterNotNull().minOrNull()?.let { next ->
                                waitMs = (next - nowMs + 1_000L).coerceIn(1_000L, 20_000L)
                            }
                        }
                    }
                }
                delay(waitMs)
            }
        }
    }

    private var videoReportingJob: Job? = null
    private var mediaDebounceJob: Job? = null
    private var mediaObserver: android.database.ContentObserver? = null

    private fun startVideoReportingLoop() {
        videoReportingJob?.cancel()
        videoReportingJob = serviceScope.launch {
            while (isActive) {
                if (!portalRepository.isRegistered()) break

                // Report videos immediately on start / every 15 minutes
                portalRepository.scanAndReportVideos()

                // Every 15 minutes (15 * 60 * 1000 = 900_000 ms)
                delay(15 * 60 * 1000L)
            }
        }
    }

    private fun registerMediaObserver() {
        if (mediaObserver != null) return
        mediaObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                // 10 seconds after MediaStore reports a change
                mediaDebounceJob?.cancel()
                mediaDebounceJob = serviceScope.launch {
                    delay(10_000L)
                    if (isActive && portalRepository.isRegistered()) {
                        portalRepository.scanAndReportVideos()
                    }
                }
            }
        }

        try {
            val videoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL)
            } else {
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            contentResolver.registerContentObserver(videoUri, true, mediaObserver!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getBatteryStatus(): Pair<Int, Boolean> {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val batteryPct = if (level >= 0 && scale > 0) {
                ((level.toFloat() / scale.toFloat()) * 100).toInt()
            } else {
                50
            }
            return Pair(batteryPct, isCharging)
        }
        return Pair(100, false)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Portal Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors device connection and sends heartbeats to web portal"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Story Test")
            .setContentText("Story Test is connected to the portal")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
