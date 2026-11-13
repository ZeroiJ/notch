package com.notch.island.ui.shape

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Continuous curvature Superellipse (Squircle, n ≈ 4) shape with zero-allocation path caching.
 * Eliminates harsh tangent discontinuities found in standard circular rounded corners,
 * matching Apple's Dynamic Island and Nothing OS organic hardware styling.
 *
 * Caches the computed Path and Outline in memory so that GPU-accelerated graphicsLayer
 * scaling runs at 120Hz without per-frame path recalculation.
 */
class SquircleShape(private val cornerRadius: Dp = 32.dp) : Shape {

    private val path = Path()
    private var cachedSize: Size = Size.Unspecified
    private var cachedRadiusPx: Float = -1f
    private var cachedOutline: Outline? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)

        // Only run the heavy math if the container's physical bounds change
        if (size == cachedSize && r == cachedRadiusPx && !size.isUnspecified) {
            return cachedOutline ?: Outline.Generic(path)
        }

        path.reset()
        cachedSize = size
        cachedRadiusPx = r

        val w = size.width
        val h = size.height

        // Smooth cubic Bézier control handles for continuous superellipse curvature
        // c ≈ 0.551915024494 is standard circle; c ≈ 0.65 yields smooth superellipse
        val c = r * 0.65f

        // Start at top edge after top-left corner
        path.moveTo(r, 0f)

        // Top edge to top-right corner
        path.lineTo(w - r, 0f)
        path.cubicTo(
            w - r + c, 0f,
            w, r - c,
            w, r
        )

        // Right edge to bottom-right corner
        path.lineTo(w, h - r)
        path.cubicTo(
            w, h - r + c,
            w - r + c, h,
            w - r, h
        )

        // Bottom edge to bottom-left corner
        path.lineTo(r, h)
        path.cubicTo(
            r - c, h,
            0f, h - r + c,
            0f, h - r
        )

        // Left edge to top-left corner
        path.lineTo(0f, r)
        path.cubicTo(
            0f, r - c,
            r - c, 0f,
            r, 0f
        )

        path.close()
        val outline = Outline.Generic(path)
        cachedOutline = outline
        return outline
    }
}
