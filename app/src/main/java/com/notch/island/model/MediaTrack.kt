package com.notch.island.model

import android.graphics.Bitmap

/**
 * Representation of an active audio playback session.
 */
data class MediaTrack(
    val title: String,
    val artist: String,
    val packageName: String,
    val isPlaying: Boolean = true,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val albumArt: Bitmap? = null,
    val playAction: (() -> Unit)? = null,
    val pauseAction: (() -> Unit)? = null,
    val nextAction: (() -> Unit)? = null,
    val prevAction: (() -> Unit)? = null,
    val seekAction: ((Long) -> Unit)? = null
)
