package com.notch.island.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility Service tier that elevates the Dynamic Island overlay to
 * WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY (2032).
 *
 * This provides:
 * 1. Overlay persistence across Lock Screen, AOD, and System UI pulldown.
 * 2. Execution of system actions (Notification Shade pull, Quick Settings, Lock Screen).
 */
class IslandAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event processing if needed
    }

    override fun onInterrupt() {
        // Interrupted callback
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    companion object {
        var instance: IslandAccessibilityService? = null
            private set

        fun isAccessibilityEnabled(context: Context): Boolean {
            val serviceName = "${context.packageName}/${IslandAccessibilityService::class.java.canonicalName}"
            val accessibilityEnabled = try {
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED
                )
            } catch (e: Settings.SettingNotFoundException) {
                0
            }
            if (accessibilityEnabled == 1) {
                val settingValue = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                return settingValue.contains(serviceName) || settingValue.contains(context.packageName)
            }
            return false
        }

        fun openNotificationShade(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false
        }

        fun openQuickSettings(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) ?: false
        }

        fun lockScreen(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
            } else {
                false
            }
        }
    }
}
