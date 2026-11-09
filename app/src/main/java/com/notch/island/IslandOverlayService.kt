package com.notch.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.notch.island.core.IslandWindowManager
import com.notch.island.model.IslandState

/**
 * Foreground Service responsible for keeping the app process prioritized
 * and hosting the Dynamic Island overlay window when accessibility service is not active.
 */
class IslandOverlayService : Service() {

    private var systemEventReceiver: com.notch.island.receiver.SystemEventReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        startForegroundServiceWithNotification()
        IslandWindowManager.attachWindow(this, isAccessibility = false)
        systemEventReceiver = com.notch.island.receiver.SystemEventReceiver.register(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!IslandWindowManager.isAttached) {
            IslandWindowManager.attachWindow(this, isAccessibility = false)
        }
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "dynamic_island_overlay_channel"
        val channelName = getString(R.string.overlay_notification_channel)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.overlay_notification_channel_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_content))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        com.notch.island.receiver.SystemEventReceiver.unregister(this, systemEventReceiver)
        systemEventReceiver = null

        IslandWindowManager.detachWindow()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private var instance: IslandOverlayService? = null

        fun isRunning(): Boolean = instance != null || IslandWindowManager.isAttached

        fun start(context: Context) {
            val intent = Intent(context, IslandOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, IslandOverlayService::class.java)
            context.stopService(intent)
            IslandWindowManager.detachWindow()
        }

        fun updateState(state: IslandState) {
            IslandWindowManager.updateState(state)
        }
    }
}
