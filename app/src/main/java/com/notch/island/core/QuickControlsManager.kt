package com.notch.island.core

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import com.notch.island.service.IslandAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages system hardware controls accessible directly from the Dynamic Island:
 * Camera Torch / Flashlight, Ringer Sound Profile, and System UI shade shortcuts.
 */
object QuickControlsManager {

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    fun toggleFlashlight(context: Context) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            val newState = !_isTorchOn.value
            cameraManager.setTorchMode(cameraId, newState)
            _isTorchOn.value = newState
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleRingerMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val newMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        try {
            audioManager.ringerMode = newMode
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openNotificationShade(): Boolean {
        return IslandAccessibilityService.openNotificationShade()
    }

    fun openQuickSettings(): Boolean {
        return IslandAccessibilityService.openQuickSettings()
    }

    fun lockScreen(): Boolean {
        return IslandAccessibilityService.lockScreen()
    }
}
