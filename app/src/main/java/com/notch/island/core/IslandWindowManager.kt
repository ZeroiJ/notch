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
import androidx.compose.ui.unit.dp
import com.notch.island.animation.IslandDimensions
import com.notch.island.model.CollapsedSubContent
import com.notch.island.model.ExpandedContentType
import com.notch.island.model.IslandState
import com.notch.island.ui.IslandRootView
import com.notch.island.ui.theme.IslandTheme

/**
 * Centralized WindowManager coordinator managing overlay view hierarchy,
 * dynamic boundary resizing, outside touch detection, and accessibility token binding.
 */
object IslandWindowManager {

    private const val TAG = "IslandWindowManager"

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
     * Attaches the dynamic island overlay window.
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

        val params = WindowManager.LayoutParams().apply {
            type = if (isAccessibility) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
            format = PixelFormat.TRANSLUCENT

            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        layoutParams = params

        val cutout = currentCutoutInfo ?: CutoutHelper.resolveCutout(context, null)
        calculateAndApplyWindowDimensions(params, islandState, cutout)

        val composeView = ComposeView(context).apply {
            overlayLifecycleOwner.attachToView(this)

            // Listen for window insets to get exact hardware DisplayCutout bounds
            setOnApplyWindowInsetsListener { _, insets ->
                val updated = CutoutHelper.resolveCutout(context, insets)
                currentCutoutInfo = updated
                calculateAndApplyWindowDimensions(params, islandState, updated)
                if (isAttached && isAttachedToWindow) {
                    try {
                        windowManager?.updateViewLayout(this, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update layout from insets", e)
                    }
                }
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
            Log.d(TAG, "Dynamic Island overlay window successfully attached (type=${params.type})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay window to WindowManager", e)
            isAttached = false
        }
    }

    /**
     * Updates the current state and triggers dynamic WindowManager geometry adjustment.
     */
    @Synchronized
    fun updateState(newState: IslandState) {
        islandState = newState
        val view = overlayComposeView ?: return
        val params = layoutParams ?: return
        val cutout = currentCutoutInfo ?: return
        val wm = windowManager ?: return

        calculateAndApplyWindowDimensions(params, newState, cutout)

        if (isAttached && view.isAttachedToWindow) {
            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update window layout for state $newState", e)
            }
        }
    }

    private fun calculateAndApplyWindowDimensions(
        params: WindowManager.LayoutParams,
        state: IslandState,
        cutout: CutoutInfo
    ) {
        val density = cutout.density
        val screenWidthDp = (cutout.screenWidthPx / density).dp

        when (state) {
            is IslandState.Hidden -> {
                params.width = 1
                params.height = 1
                params.y = 0
            }

            is IslandState.Collapsed -> {
                // Sized to exactly encapsulate camera punch hole and status pill
                val baseWidthDp = maxOf(cutout.widthDp + 46.dp, 136.dp)
                val secondaryBonus = if (state.secondaryBubble != null) 58.dp else 0.dp
                val contentWidthDp = (state.customPillWidthDp?.dp ?: baseWidthDp) + secondaryBonus
                // Add 28dp gesture slop room (14dp left + 14dp right)
                val windowWidthDp = contentWidthDp + 28.dp
                val windowHeightDp = maxOf(cutout.heightDp + 18.dp, 56.dp)

                params.width = (windowWidthDp.value * density).toInt()
                params.height = (windowHeightDp.value * density).toInt()
                params.y = 0
            }

            is IslandState.Peek -> {
                val bannerWidthDp = minOf(screenWidthDp * 0.94f, 380.dp) + 28.dp
                val bannerHeightDp = maxOf(cutout.heightDp + 26.dp, 68.dp)

                params.width = (bannerWidthDp.value * density).toInt()
                params.height = (bannerHeightDp.value * density).toInt()
                params.y = 0
            }

            is IslandState.Expanded -> {
                val cardWidthDp = minOf(screenWidthDp * 0.96f, 400.dp) + 16.dp
                val cardHeightDp = when (state.type) {
                    ExpandedContentType.MEDIA -> 230.dp
                    ExpandedContentType.TIMER -> 210.dp
                    ExpandedContentType.NOTIFICATION -> 200.dp
                    ExpandedContentType.CALL -> 170.dp
                    ExpandedContentType.GENERIC -> 200.dp
                }

                params.width = (cardWidthDp.value * density).toInt()
                params.height = (cardHeightDp.value * density).toInt()
                params.y = 0
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
