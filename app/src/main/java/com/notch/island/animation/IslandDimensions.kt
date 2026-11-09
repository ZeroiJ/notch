package com.notch.island.animation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notch.island.core.CutoutInfo
import com.notch.island.model.ExpandedContentType
import com.notch.island.model.IslandState

/**
 * Geometric target configuration for the Dynamic Island capsule.
 */
data class IslandTargetBounds(
    val width: Dp,
    val height: Dp,
    val cornerRadius: Dp,
    val elevation: Dp,
    val alpha: Float,
    val topPadding: Dp
)

object IslandDimensions {

    /**
     * Resolves target geometry based on the active state and device cutout telemetry.
     */
    fun resolve(state: IslandState, cutout: CutoutInfo): IslandTargetBounds {
        val screenWidthDp = (cutout.screenWidthPx / cutout.density).dp
        val topCutoutInsetDp = (cutout.boundingRect.top / cutout.density).dp

        return when (state) {
            is IslandState.Hidden -> {
                IslandTargetBounds(
                    width = cutout.widthDp,
                    height = cutout.heightDp,
                    cornerRadius = cutout.heightDp / 2,
                    elevation = 0.dp,
                    alpha = 0f,
                    topPadding = topCutoutInsetDp
                )
            }

            is IslandState.Collapsed -> {
                // Sleek tailored pill breadth comfortably hugging the punch hole without excess vertical fatness
                // Camera lens is ~25dp diameter, centered at Y=24.5dp.
                // 35dp pill height with 7dp top padding centers the pill with ~5.3dp padding on all sides.
                val baseWidth = maxOf(cutout.widthDp + 40.dp, 118.dp)
                val resolvedWidth = state.customPillWidthDp?.dp ?: baseWidth
                val resolvedHeight = 35.dp

                IslandTargetBounds(
                    width = resolvedWidth,
                    height = resolvedHeight,
                    cornerRadius = resolvedHeight / 2,
                    elevation = 4.dp,
                    alpha = 1f,
                    topPadding = 7.dp
                )
            }

            is IslandState.Peek -> {
                val resolvedWidth = minOf(screenWidthDp * 0.92f, 370.dp)
                val resolvedHeight = 44.dp

                IslandTargetBounds(
                    width = resolvedWidth,
                    height = resolvedHeight,
                    cornerRadius = resolvedHeight / 2,
                    elevation = 10.dp,
                    alpha = 1f,
                    topPadding = 5.dp
                )
            }

            is IslandState.Expanded -> {
                val resolvedWidth = minOf(screenWidthDp * 0.94f, 400.dp)
                val resolvedHeight = when (state.type) {
                    ExpandedContentType.MEDIA -> 190.dp
                    ExpandedContentType.CALL -> 140.dp
                    ExpandedContentType.NOTIFICATION -> 155.dp
                    ExpandedContentType.TIMER -> 160.dp
                    ExpandedContentType.GENERIC -> 165.dp
                }

                IslandTargetBounds(
                    width = resolvedWidth,
                    height = resolvedHeight,
                    cornerRadius = 44.dp, // Apple HIG 44pt standard squircle radius
                    elevation = 16.dp,
                    alpha = 1f,
                    topPadding = topCutoutInsetDp
                )
            }
        }
    }
}
