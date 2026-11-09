package com.notch.island.core

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.DisplayCutout
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Physical position classification for device camera punch-holes / notches.
 */
enum class CutoutPosition {
    CENTER,
    LEFT,
    RIGHT,
    NONE
}

/**
 * Encapsulates the resolved hardware cutout layout metrics and safe boundaries.
 */
data class CutoutInfo(
    val hasCutout: Boolean,
    val boundingRect: Rect,
    val position: CutoutPosition,
    val safeInsetTopPx: Int,
    val statusBarHeightPx: Int,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val density: Float
) {
    val widthDp: Dp get() = (boundingRect.width() / density).dp
    val heightDp: Dp get() = (boundingRect.height() / density).dp
    val centerXOffsetDp: Dp get() {
        val screenCenterPx = screenWidthPx / 2f
        val offsetPx = boundingRect.exactCenterX() - screenCenterPx
        return (offsetPx / density).dp
    }
}

/**
 * Utility to extract DisplayCutout boundaries and safe insets with robust fallbacks.
 */
object CutoutHelper {

    fun resolveCutout(context: Context, windowInsets: WindowInsets?): CutoutInfo {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density

        val (screenWidth, screenHeight) = getScreenDimensions(windowManager, displayMetrics)
        val statusBarHeight = getStatusBarHeight(context)

        var hasCutout = false
        var cutoutRect = Rect()
        var safeTop = statusBarHeight

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && windowInsets != null) {
            val displayCutout: DisplayCutout? = windowInsets.displayCutout
            if (displayCutout != null) {
                safeTop = displayCutout.safeInsetTop.coerceAtLeast(statusBarHeight)
                val rects = displayCutout.boundingRects

                // Find the primary top cutout if multiple are reported
                val topCutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    displayCutout.boundingRectTop.takeIf { !it.isEmpty }
                        ?: rects.firstOrNull { it.top <= safeTop }
                } else {
                    rects.firstOrNull { it.top <= safeTop }
                }

                if (topCutout != null && !topCutout.isEmpty) {
                    hasCutout = true
                    cutoutRect = Rect(topCutout)
                }
            }
        }

        // If no hardware cutout was reported by WindowInsets, use device-specific calibration
        if (!hasCutout || cutoutRect.isEmpty) {
            if (isNothingDevice()) {
                hasCutout = true
                // Nothing Phone (4b) exact hardware punch hole calibration:
                // 1080x2344 display, 420dpi (2.625 density), cutout at Rect(497, 0, 583, 126)
                val punchWidthPx = (33 * density).toInt()
                val left = (screenWidth - punchWidthPx) / 2
                val bottom = (48 * density).toInt()
                cutoutRect = Rect(left, 0, left + punchWidthPx, bottom)
                safeTop = bottom
            } else {
                val fallbackHeightPx = statusBarHeight.coerceAtLeast((28 * density).toInt())
                val fallbackWidthPx = (110 * density).toInt() // Standard pill width (~110dp)
                val left = (screenWidth - fallbackWidthPx) / 2
                cutoutRect = Rect(left, 0, left + fallbackWidthPx, fallbackHeightPx)
            }
        }

        val position = determinePosition(cutoutRect, screenWidth, hasCutout)

        return CutoutInfo(
            hasCutout = hasCutout,
            boundingRect = cutoutRect,
            position = position,
            safeInsetTopPx = safeTop,
            statusBarHeightPx = statusBarHeight,
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight,
            density = density
        )
    }

    private fun isNothingDevice(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val model = Build.MODEL.lowercase()
        return m.contains("nothing") || brand.contains("nothing") ||
                model.contains("a009") || model.contains("a063") ||
                model.contains("a065") || model.contains("supercontra")
    }

    private fun determinePosition(rect: Rect, screenWidth: Int, hasCutout: Boolean): CutoutPosition {
        if (!hasCutout) return CutoutPosition.CENTER

        val centerX = rect.exactCenterX()
        val screenCenter = screenWidth / 2f
        val tolerance = 30f // px tolerance for center punch-hole

        return when {
            kotlin.math.abs(centerX - screenCenter) <= tolerance -> CutoutPosition.CENTER
            centerX < screenCenter -> CutoutPosition.LEFT
            else -> CutoutPosition.RIGHT
        }
    }

    private fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            (24 * context.resources.displayMetrics.density).toInt()
        }
    }

    private fun getScreenDimensions(windowManager: WindowManager, displayMetrics: DisplayMetrics): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }
}
