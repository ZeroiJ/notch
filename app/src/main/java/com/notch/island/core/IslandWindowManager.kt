package com.notch.island.core

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.notch.island.model.CollapsedSubContent
import com.notch.island.model.IslandState
import com.notch.island.ui.IslandRootView
import com.notch.island.ui.theme.IslandTheme

/**
 * High-performance 120Hz WindowManager coordinator with static container sizing.
 *
 * Implements Zero-Bounds-Animation architecture:
 * 1. Creates a static, invisible container (MATCH_PARENT x 800px) with FLAG_HARDWARE_ACCELERATED.
 * 2. NEVER animates or modifies width, height, x, or y coordinates during morphing frames.
 * 3. Toggles FLAG_WATCH_OUTSIDE_TOUCH only upon state change completion.
 */
object IslandWindowManager {

    private const val TAG = "IslandWindowManager"
    private const val STATIC_CONTAINER_HEIGHT_PX = 800

    private var windowManager: WindowManager? = null
    private var overlayComposeView: ComposeView? = null
    private val overlayLifecycleOwner = OverlayLifecycleOwner()
    private var currentHostContext: Context? = null
    private var isAccessibilityHost: Boolean = false

    var currentCutoutInfo by mutableStateOf<CutoutInfo?>(null)
        private set

    var islandState by mutableStateOf<IslandState>(
        IslandState.Collapsed(
            leading = CollapsedSubContent.Dot(0xFF30D158),
            trailing = CollapsedSubContent.Text("ACTIVE")
        )
    )
        private set

    private var layoutParams: WindowManager.LayoutParams? = null
    var isAttached: Boolean = false
        private set

    /**
     * Attaches the static Dynamic Island overlay window.
     * Prefers Accessibility Service token (2032) when available, falls back to Application Overlay (2038).
     */
    @Synchronized
    fun attachWindow(context: Context, isAccessibility: Boolean = false) {
        if (isAttached && isAccessibilityHost && !isAccessibility) {
            // Already attached with higher priority accessibility host
            return
        }

        if (isAttached) {
            detachWindow()
        }

        currentHostContext = context
        isAccessibilityHost = isAccessibility
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        currentCutoutInfo = CutoutHelper.resolveCutout(context, null)

        overlayLifecycleOwner.onCreate()

        // Static WindowManager container: MATCH_PARENT width, static 800px height, GPU accelerated
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            STATIC_CONTAINER_HEIGHT_PX,
            if (isAccessibility) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, // CRITICAL FOR 120HZ
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            // Watch outside touches when starting in expanded or peek state
            if (islandState is IslandState.Expanded || islandState is IslandState.Peek) {
                flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            }
        }
        layoutParams = params

        val composeView = ComposeView(context).apply {
            overlayLifecycleOwner.attachToView(this)

            // Listen for window insets to get exact hardware DisplayCutout bounds
            setOnApplyWindowInsetsListener { _, insets ->
                val updated = CutoutHelper.resolveCutout(context, insets)
                currentCutoutInfo = updated
                insets
            }

            // Outside touch interception: tapping outside collapses expanded/peek cards
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    if (islandState is IslandState.Expanded || islandState is IslandState.Peek) {
                        IslandEventManager.resolveAndApplyCurrentState()
                        true
                    } else false
                } else false
            }

            setContent {
                val activeCutout = currentCutoutInfo ?: return@setContent
                IslandTheme {
                    IslandRootView(
                        state = islandState,
                        cutoutInfo = activeCutout,
                        onStateChange = { newState ->
                            updateState(newState)
                        }
                    )
                }
            }
        }
        overlayComposeView = composeView

        try {
            windowManager?.addView(composeView, params)
            isAttached = true
            overlayLifecycleOwner.onResume()
            Log.d(TAG, "Static Dynamic Island overlay attached (MATCH_PARENT x $STATIC_CONTAINER_HEIGHT_PX, type=${params.type})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay window to WindowManager", e)
            isAttached = false
        }
    }

    /**
     * Updates the current state without modifying window geometry.
     * Only updates WindowManager flags (toggling FLAG_WATCH_OUTSIDE_TOUCH)
     * when completing a state change, never animating width/height/x/y coordinates.
     */
    @Synchronized
    fun updateState(newState: IslandState) {
        val oldState = islandState
        islandState = newState

        val view = overlayComposeView ?: return
        val params = layoutParams ?: return
        val wm = windowManager ?: return

        val wasOutsideTouch = (params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH) != 0
        val shouldWatchOutside = newState is IslandState.Expanded || newState is IslandState.Peek

        if (wasOutsideTouch != shouldWatchOutside) {
            if (shouldWatchOutside) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
            }

            if (isAttached && view.isAttachedToWindow) {
                try {
                    wm.updateViewLayout(view, params)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update WindowManager flags for state $newState", e)
                }
            }
        }
    }

    /**
     * Safely detaches the overlay window and releases lifecycle resources.
     */
    @Synchronized
    fun detachWindow() {
        overlayLifecycleOwner.onPause()
        overlayLifecycleOwner.onDestroy()

        overlayComposeView?.let { view ->
            if (view.isAttachedToWindow) {
                try {
                    windowManager?.removeView(view)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to remove overlay view", e)
                }
            }
        }
        overlayComposeView = null
        layoutParams = null
        windowManager = null
        currentHostContext = null
        isAttached = false
        isAccessibilityHost = false
    }
}
