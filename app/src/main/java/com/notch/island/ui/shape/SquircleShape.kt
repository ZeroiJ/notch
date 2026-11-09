package com.notch.island.ui.shape

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Continuous curvature Superellipse (Squircle, n ≈ 4) shape.
 * Eliminates harsh tangent discontinuities found in standard circular rounded corners,
 * matching Apple's Dynamic Island and Nothing OS organic hardware styling.
 */
class SquircleShape(private val cornerRadius: Dp) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val path = Path().apply {
            val w = size.width
            val h = size.height

            // Smooth cubic Bézier control handles for continuous superellipse curvature
            // c ≈ 0.551915024494 is standard circle; c ≈ 0.67 yields smooth superellipse
            val c = r * 0.65f

            // Start at top edge after top-left corner
            moveTo(r, 0f)

            // Top edge to top-right corner
            lineTo(w - r, 0f)
            cubicTo(
                w - r + c, 0f,
                w, r - c,
                w, r
            )

            // Right edge to bottom-right corner
            lineTo(w, h - r)
            cubicTo(
                w, h - r + c,
                w - r + c, h,
                w - r, h
            )

            // Bottom edge to bottom-left corner
            lineTo(r, h)
            cubicTo(
                r - c, h,
                0f, h - r + c,
                0f, h - r
            )

            // Left edge to top-left corner
            lineTo(0f, r)
            cubicTo(
                0f, r - c,
                r - c, 0f,
                r, 0f
            )

            close()
        }
        return Outline.Generic(path)
    }
}
