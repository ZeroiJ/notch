package com.notch.island.model

import java.util.Locale

/**
 * State of an active live countdown timer in the Dynamic Island.
 */
data class TimerData(
    val totalDurationMs: Long,
    val remainingMs: Long,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false
) {
    val progress: Float
        get() = if (totalDurationMs > 0) {
            (remainingMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
        } else {
            0f
        }

    val formattedRemaining: String
        get() {
            val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }

    val compactText: String
        get() {
            val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return if (minutes > 0) "${minutes}m" else "${seconds}s"
        }
}
