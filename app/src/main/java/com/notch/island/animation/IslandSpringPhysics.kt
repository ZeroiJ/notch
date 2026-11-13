package com.notch.island.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Encapsulates spring physics configuration (damping ratio and stiffness)
 * matching Apple's Dynamic Island critically damped harmonic oscillators.
 */
data class IslandSpringConfig(
    val dampingRatio: Float,
    val stiffness: Float
) {
    init {
        require(stiffness > 0f) { "Spring stiffness constant must be positive." }
        require(dampingRatio >= 0f) { "Damping ratio must be non-negative." }
    }

    fun <T> toSpringSpec(visibilityThreshold: T? = null): SpringSpec<T> {
        return spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness,
            visibilityThreshold = visibilityThreshold
        )
    }
}

/**
 * Standard spring profiles for the Dynamic Island motion engine.
 * Pure spring() specs exclusively — zero linear or standard Material easing.
 */
object IslandSpringPhysics {

    /**
     * Organic expansion spring: slight organic overshoot (ζ = 0.78, k = 380).
     */
    val ExpandConfig = IslandSpringConfig(
        dampingRatio = 0.78f,
        stiffness = 380f
    )

    /**
     * Critically damped collapse spring: instant snap, zero bounce (ζ = 1.0, k = 520).
     * Prevents visual oscillation over the physical hardware camera hole.
     */
    val CollapseConfig = IslandSpringConfig(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 520f
    )

    /**
     * Rigid, zero-latency spring for real-time finger drag gestures.
     * Sticks directly to touch displacement without lag.
     */
    val DragTracking = IslandSpringConfig(
        dampingRatio = 0.70f,
        stiffness = 1200f
    )

    /**
     * Playful spring for peek alerts and quick bubbles.
     */
    val ExpressiveAlert = IslandSpringConfig(
        dampingRatio = 0.50f,
        stiffness = 850f
    )

    // Dedicated spring spec builders
    fun <T> expandSizeSpec(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = 0.78f,
        stiffness = 380f,
        visibilityThreshold = visibilityThreshold
    )

    fun <T> collapseSizeSpec(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 520f,
        visibilityThreshold = visibilityThreshold
    )

    val ExpandFloatSpec: SpringSpec<Float> = expandSizeSpec(visibilityThreshold = 0.001f)
    val CollapseFloatSpec: SpringSpec<Float> = collapseSizeSpec(visibilityThreshold = 0.001f)
    val DragFloatSpec: SpringSpec<Float> = DragTracking.toSpringSpec(visibilityThreshold = 0.001f)
    val DragOffsetSpec: SpringSpec<IntOffset> = DragTracking.toSpringSpec(visibilityThreshold = IntOffset(1, 1))

    // Aliases for compatibility
    val ExpansionMorph = ExpandConfig
    val IdleTuck = CollapseConfig
    val MorphDpSpec: SpringSpec<Dp> = expandSizeSpec(visibilityThreshold = 0.5.dp)
    val AlertDpSpec: SpringSpec<Dp> = ExpressiveAlert.toSpringSpec(visibilityThreshold = 0.5.dp)
    val TuckDpSpec: SpringSpec<Dp> = collapseSizeSpec(visibilityThreshold = 0.5.dp)
}
