package com.notch.island.model

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Types of content hosted in the Expanded interactive card.
 */
enum class ExpandedContentType {
    MEDIA,
    NOTIFICATION,
    TIMER,
    CALL,
    GENERIC
}

/**
 * Visual pill sub-elements for Compact mode and the minimal secondary bubble.
 */
sealed interface CollapsedSubContent {
    data object Empty : CollapsedSubContent
    data class Dot(val color: Long = 0xFF4CAF50) : CollapsedSubContent
    data class Icon(val iconRes: Int? = null, val vector: ImageVector? = null) : CollapsedSubContent
    data class Text(val text: String) : CollapsedSubContent
    data class AudioWave(val isPlaying: Boolean = true) : CollapsedSubContent
}

/**
 * Core state machine for the Dynamic Island overlay.
 */
sealed interface IslandState {

    /**
     * Completely hidden / tucked into the camera punch-hole (e.g. after swipe-in dismiss).
     */
    data class Hidden(
        val lastActiveState: IslandState? = null
    ) : IslandState

    /**
     * Compact pill state wrapped closely around the camera punch-hole cutout.
     * Supports dual-island secondary detached bubble when secondary != null.
     */
    data class Collapsed(
        val leading: CollapsedSubContent = CollapsedSubContent.Empty,
        val trailing: CollapsedSubContent = CollapsedSubContent.Empty,
        val secondaryBubble: CollapsedSubContent? = null,
        val customPillWidthDp: Float? = null
    ) : IslandState

    /**
     * Brief informational banner that springs open and auto-collapses.
     */
    data class Peek(
        val title: String,
        val subtitle: String? = null,
        val iconRes: Int? = null,
        val trailingText: String? = null,
        val durationMs: Long = 3000L,
        val id: String = System.currentTimeMillis().toString()
    ) : IslandState

    /**
     * Large interactive modal card with gestures and action buttons.
     */
    data class Expanded(
        val type: ExpandedContentType,
        val title: String,
        val subtitle: String? = null,
        val payload: Any? = null
    ) : IslandState
}

/**
 * Intent actions dispatched into the Island state reducer.
 */
sealed interface IslandIntent {
    data object Expand : IslandIntent
    data object Collapse : IslandIntent
    data object DismissToHidden : IslandIntent
    data object RestoreFromHidden : IslandIntent
    data class ShowPeek(val peek: IslandState.Peek) : IslandIntent
    data class SwitchToExpanded(val expanded: IslandState.Expanded) : IslandIntent
}
