package com.notch.island.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Encapsulates spring physics configuration (damping ratio and stiffness)
 * matching Apple's Dynamic Island and the decompiled parameters of The Notch.
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
        return SpringSpec(
            dampingRatio = dampingRatio,
            stiffness = stiffness,
            visibilityThreshold = visibilityThreshold
        )
    }
}

/**
 * Standard spring profiles for the Dynamic Island motion engine.
 */
object IslandSpringPhysics {

    /**
     * Rigid, zero-latency spring for real-time finger drag gestures.
     * Sticks directly to touch displacement without lag.
     */
    val DragTracking = IslandSpringConfig(
        dampingRatio = 0.70f,
        stiffness = 1200f
    )

    /**
     * Playful, bouncy spring with subtle overshoot for peek alerts and quick bubbles.
     */
    val ExpressiveAlert = IslandSpringConfig(
        dampingRatio = 0.50f,
        stiffness = 850f
    )

    /**
     * Fluid capsule-to-card expansion and morphing spring.
     * Snappy emergence with smooth, organic deceleration.
     */
    val ExpansionMorph = IslandSpringConfig(
        dampingRatio = 0.65f,
        stiffness = 650f
    )

    /**
     * Soft, cushioned deceleration when the island collapses back into the camera aperture.
     */
    val IdleTuck = IslandSpringConfig(
        dampingRatio = 0.70f,
        stiffness = 300f
    )

    // Pre-allocated Compose SpringSpecs for Dp and Float dimensions
    val MorphDpSpec: SpringSpec<Dp> = ExpansionMorph.toSpringSpec(visibilityThreshold = 0.5.dp)
    val AlertDpSpec: SpringSpec<Dp> = ExpressiveAlert.toSpringSpec(visibilityThreshold = 0.5.dp)
    val TuckDpSpec: SpringSpec<Dp> = IdleTuck.toSpringSpec(visibilityThreshold = 0.5.dp)
    val DragFloatSpec: SpringSpec<Float> = DragTracking.toSpringSpec(visibilityThreshold = 0.01f)
    val DragOffsetSpec: SpringSpec<IntOffset> = DragTracking.toSpringSpec(visibilityThreshold = IntOffset(1, 1))
}
