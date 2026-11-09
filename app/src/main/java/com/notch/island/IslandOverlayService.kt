package com.notch.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import com.notch.island.core.CutoutHelper
import com.notch.island.core.CutoutInfo
import com.notch.island.core.OverlayLifecycleOwner
import com.notch.island.model.CollapsedSubContent
import com.notch.island.model.IslandState
import com.notch.island.ui.IslandRootView
import com.notch.island.ui.theme.IslandTheme

/**
 * Foreground Service responsible for hosting and managing the Dynamic Island
 * overlay ComposeView within the Android WindowManager.
 */
class IslandOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayComposeView: ComposeView? = null
    private val overlayLifecycleOwner = OverlayLifecycleOwner()

    private var currentCutoutInfo by mutableStateOf<CutoutInfo?>(null)
    private var islandState by mutableStateOf<IslandState>(
        IslandState.Collapsed(
            leading = CollapsedSubContent.Dot(0xFF30D158),
            trailing = CollapsedSubContent.Text("ACTIVE")
        )
    )

    private lateinit var layoutParams: WindowManager.LayoutParams
    private var systemEventReceiver: com.notch.island.receiver.SystemEventReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        startForegroundServiceWithNotification()
        overlayLifecycleOwner.onCreate()
        initCutoutInfo()
        createAndAttachOverlay()
        overlayLifecycleOwner.onResume()

        systemEventReceiver = com.notch.island.receiver.SystemEventReceiver.register(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        overlayLifecycleOwner.onResume()
        return START_STICKY
    }

    private fun initCutoutInfo() {
        // Initial estimation before WindowInsets callback fires
        currentCutoutInfo = CutoutHelper.resolveCutout(this, null)
    }

    private fun createAndAttachOverlay() {
        layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT

            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT

            // Enable layout directly into cutout safe zones on API 28+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }

        overlayComposeView = ComposeView(this).apply {
            overlayLifecycleOwner.attachToView(this)

            // Listen for window insets to get exact hardware DisplayCutout bounds
            setOnApplyWindowInsetsListener { _, insets ->
                val updated = CutoutHelper.resolveCutout(this@IslandOverlayService, insets)
                currentCutoutInfo = updated
                insets
            }

            setContent {
                val cutout = currentCutoutInfo ?: return@setContent
                IslandTheme {
                    IslandRootView(
                        state = islandState,
                        cutoutInfo = cutout,
                        onStateChange = { newState ->
                            islandState = newState
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(overlayComposeView, layoutParams)
        } catch (e: Exception) {
            android.util.Log.e("IslandOverlayService", "Failed to add overlay window", e)
        }
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

        overlayLifecycleOwner.onPause()
        overlayLifecycleOwner.onDestroy()

        overlayComposeView?.let {
            if (it.isAttachedToWindow) {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    android.util.Log.e("IslandOverlayService", "Failed to remove overlay view", e)
                }
            }
        }
        overlayComposeView = null
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private var instance: IslandOverlayService? = null

        fun isRunning(): Boolean = instance != null

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
        }

        fun updateState(state: IslandState) {
            instance?.islandState = state
        }
    }
}
