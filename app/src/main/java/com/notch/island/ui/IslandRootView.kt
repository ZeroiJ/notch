package com.notch.island.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.notch.island.core.CutoutInfo
import com.notch.island.model.IslandState

/**
 * Root Composable for the Dynamic Island overlay in WindowManager.
 */
@Composable
fun IslandRootView(
    state: IslandState,
    cutoutInfo: CutoutInfo,
    onStateChange: (IslandState) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        IslandContainer(
            state = state,
            cutout = cutoutInfo,
            onStateChange = onStateChange
        )
    }
}
