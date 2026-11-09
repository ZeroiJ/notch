package com.notch.island.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.notch.island.model.ExpandedContentType
import com.notch.island.model.IslandState
import kotlin.math.abs

/**
 * Callbacks for gesture events dispatched by the gesture detector.
 */
interface IslandGestureListener {
    fun onTap()
    fun onLongPress()
    fun onDragDelta(dragYPx: Float)
    fun onDragEnd(releasedPastThreshold: Boolean, velocityY: Float)
    fun onSwipeUp()
    fun onSwipeInward()
    fun onSwipeOutward()
}

/**
 * Custom pointer input modifier managing continuous drag expansion,
 * upward collapse flicks, horizontal dismiss/restore swipes, and tap/long-press.
 */
fun Modifier.islandGestures(
    currentState: IslandState,
    enabled: Boolean = true,
    listener: IslandGestureListener
): Modifier {
    if (!enabled) return this

    return this.pointerInput(currentState) {
        awaitEachGesture {
            val down: PointerInputChange = awaitFirstDown(requireUnconsumed = false)
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            var totalDragY = 0f
            var totalDragX = 0f
            var isDragging = false
            var isLongPressed = false

            val longPressTimeoutMs = 280L
            val startTime = System.currentTimeMillis()

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                if (change.isConsumed) break

                if (!change.pressed) {
                    // Finger lifted (UP)
                    val duration = System.currentTimeMillis() - startTime
                    val currentVelocity = velocityTracker.calculateVelocity()

                    if (isDragging) {
                        val thresholdPx = 35.dp.toPx()
                        val isOverThreshold = totalDragY >= thresholdPx || currentVelocity.y > 600f
                        listener.onDragEnd(isOverThreshold, currentVelocity.y)
                        change.consume()
                    } else if (!isLongPressed) {
                        // Check if horizontal swipe occurred before drag threshold
                        if (abs(totalDragX) > 40.dp.toPx() && abs(totalDragX) > abs(totalDragY) * 1.5f) {
                            if (totalDragX < 0) {
                                listener.onSwipeInward()
                            } else {
                                listener.onSwipeOutward()
                            }
                            change.consume()
                        } else if (duration < longPressTimeoutMs && abs(totalDragY) < 8.dp.toPx()) {
                            // Clean tap
                            listener.onTap()
                            change.consume()
                        }
                    }
                    break
                }

                // Pointer is moving
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val deltaY = change.position.y - down.position.y
                val deltaX = change.position.x - down.position.x
                totalDragY = deltaY
                totalDragX = deltaX

                val touchSlop = 8.dp.toPx()

                // Check long press when stationary
                if (!isDragging && System.currentTimeMillis() - startTime >= longPressTimeoutMs) {
                    if (abs(totalDragY) < touchSlop && abs(totalDragX) < touchSlop) {
                        isLongPressed = true
                        listener.onLongPress()
                        change.consume()
                    }
                }

                when (currentState) {
                    is IslandState.Collapsed -> {
                        // Downward drag to expand
                        if (deltaY > touchSlop && deltaY > abs(deltaX)) {
                            isDragging = true
                            // Apply subtle rubberband resistance damping (0.75f)
                            listener.onDragDelta(deltaY * 0.75f)
                            change.consume()
                        }
                    }

                    is IslandState.Expanded -> {
                        // Upward swipe to collapse
                        if (deltaY < -touchSlop && abs(deltaY) > abs(deltaX)) {
                            isDragging = true
                            if (deltaY < -40.dp.toPx()) {
                                listener.onSwipeUp()
                                change.consume()
                                break
                            }
                        }
                    }

                    is IslandState.Hidden -> {
                        // Swipe outward to restore
                        if (abs(deltaX) > 30.dp.toPx()) {
                            listener.onSwipeOutward()
                            change.consume()
                            break
                        }
                    }

                    is IslandState.Peek -> {
                        // Swipe up to dismiss peek early
                        if (deltaY < -touchSlop) {
                            listener.onSwipeUp()
                            change.consume()
                            break
                        }
                    }
                }
            }
        }
    }
}
