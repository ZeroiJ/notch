package com.notch.island.core

import com.notch.island.IslandOverlayService
import com.notch.island.model.CollapsedSubContent
import com.notch.island.model.ExpandedContentType
import com.notch.island.model.IslandState
import com.notch.island.model.MediaTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Priority coordinator resolving competing system events,
 * live media sessions, notifications, and hardware status updates.
 */
object IslandEventManager {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _currentMediaTrack = MutableStateFlow<MediaTrack?>(null)
    val currentMediaTrack: StateFlow<MediaTrack?> = _currentMediaTrack.asStateFlow()

    private val _activeTimer = MutableStateFlow<com.notch.island.model.TimerData?>(null)
    val activeTimer: StateFlow<com.notch.island.model.TimerData?> = _activeTimer.asStateFlow()

    private val _activePeek = MutableStateFlow<IslandState.Peek?>(null)
    val activePeek: StateFlow<IslandState.Peek?> = _activePeek.asStateFlow()

    private val _activeReplyPayload = MutableStateFlow<com.notch.island.model.QuickReplyPayload?>(null)
    val activeReplyPayload: StateFlow<com.notch.island.model.QuickReplyPayload?> = _activeReplyPayload.asStateFlow()

    private var peekDismissJob: Job? = null

    fun postNotificationAlert(
        title: String,
        subtitle: String? = null,
        trailingText: String? = null,
        replyPayload: com.notch.island.model.QuickReplyPayload? = null,
        durationMs: Long = 4000L
    ) {
        _activeReplyPayload.value = replyPayload
        postPeek(title, subtitle, trailingText, durationMs)
    }

    /**
     * Expands the active notification with quick reply if available.
     */
    fun expandActiveNotification() {
        val payload = _activeReplyPayload.value
        val peek = _activePeek.value
        IslandOverlayService.updateState(
            IslandState.Expanded(
                type = ExpandedContentType.NOTIFICATION,
                title = payload?.sender ?: peek?.title ?: "Notification",
                subtitle = payload?.message ?: peek?.subtitle,
                payload = payload
            )
        )
    }

    fun onTimerUpdated(timer: com.notch.island.model.TimerData?) {
        _activeTimer.value = timer
        if (_activePeek.value == null) {
            resolveAndApplyCurrentState()
        }
    }

    /**
     * Posts a high-priority Peek banner (charger plugged, silent switch, notification).
     */
    fun postPeek(
        title: String,
        subtitle: String? = null,
        trailingText: String? = null,
        durationMs: Long = 3200L
    ) {
        val peek = IslandState.Peek(
            title = title,
            subtitle = subtitle,
            trailingText = trailingText,
            durationMs = durationMs
        )
        _activePeek.value = peek
        IslandOverlayService.updateState(peek)

        peekDismissJob?.cancel()
        peekDismissJob = scope.launch {
            delay(durationMs)
            _activePeek.value = null
            resolveAndApplyCurrentState()
        }
    }

    /**
     * Updates ongoing media playback state (Spotify, YouTube Music, etc.).
     */
    fun updateMediaTrack(track: MediaTrack?) {
        _currentMediaTrack.value = track

        // Only update overlay state if not currently showing a higher-priority Peek alert
        if (_activePeek.value == null) {
            resolveAndApplyCurrentState()
        }
    }

    /**
     * Re-evaluates active priorities and applies the corresponding IslandState.
     */
    fun resolveAndApplyCurrentState() {
        val peek = _activePeek.value
        if (peek != null) {
            IslandOverlayService.updateState(peek)
            return
        }

        val media = _currentMediaTrack.value
        val timer = _activeTimer.value
        val isMediaPlaying = media != null && media.isPlaying

        // Concurrent Dual Island: Both Media and Timer are active
        if (isMediaPlaying && timer != null) {
            IslandOverlayService.updateState(
                IslandState.Collapsed(
                    leading = CollapsedSubContent.Dot(0xFF30D158),
                    trailing = CollapsedSubContent.AudioWave(isPlaying = true),
                    secondaryBubble = CollapsedSubContent.Text(timer.compactText)
                )
            )
            return
        }

        // Active Countdown Timer Only
        if (timer != null) {
            IslandOverlayService.updateState(
                IslandState.Collapsed(
                    leading = CollapsedSubContent.Dot(0xFFFF9F0A),
                    trailing = CollapsedSubContent.Text(timer.formattedRemaining)
                )
            )
            return
        }

        // Active Media Only
        if (isMediaPlaying) {
            IslandOverlayService.updateState(
                IslandState.Collapsed(
                    leading = CollapsedSubContent.Dot(0xFF30D158),
                    trailing = CollapsedSubContent.AudioWave(isPlaying = true)
                )
            )
            return
        }

        // Default resting state
        IslandOverlayService.updateState(
            IslandState.Collapsed(
                leading = CollapsedSubContent.Dot(0xFF30D158),
                trailing = CollapsedSubContent.Text("ACTIVE")
            )
        )
    }

    /**
     * Opens the expanded card corresponding to active timer.
     */
    fun expandTimer() {
        val timer = _activeTimer.value ?: return
        IslandOverlayService.updateState(
            IslandState.Expanded(
                type = ExpandedContentType.TIMER,
                title = "Timer",
                subtitle = "Countdown running",
                payload = timer
            )
        )
    }

    /**
     * Opens the expanded card corresponding to active media.
     */
    fun expandActiveMedia() {
        val media = _currentMediaTrack.value ?: return
        IslandOverlayService.updateState(
            IslandState.Expanded(
                type = ExpandedContentType.MEDIA,
                title = media.title,
                subtitle = "${media.artist} • ${media.packageName.substringAfterLast('.')}",
                payload = media
            )
        )
    }
}
