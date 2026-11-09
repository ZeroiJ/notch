package com.notch.island.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notch.island.animation.IslandDimensions
import com.notch.island.animation.IslandSpringPhysics
import com.notch.island.core.CutoutInfo
import com.notch.island.core.IslandEventManager
import com.notch.island.core.QuickControlsManager
import com.notch.island.core.TimerManager
import com.notch.island.model.CollapsedSubContent
import com.notch.island.model.ExpandedContentType
import com.notch.island.model.IslandState
import com.notch.island.model.MediaTrack
import com.notch.island.model.QuickReplyPayload
import com.notch.island.model.TimerData
import com.notch.island.ui.gesture.IslandGestureListener
import com.notch.island.ui.gesture.islandGestures
import com.notch.island.ui.shape.SquircleShape
import com.notch.island.ui.theme.AccentGreen
import com.notch.island.ui.theme.AccentOrange
import com.notch.island.ui.theme.GeistFontFamily
import com.notch.island.ui.theme.IslandBorder
import com.notch.island.ui.theme.NdotFontFamily
import com.notch.island.ui.theme.NothingCard
import com.notch.island.ui.theme.NothingDarkSurface
import com.notch.island.ui.theme.NothingMuted
import com.notch.island.ui.theme.NothingRed
import com.notch.island.ui.theme.OledBlack
import com.notch.island.ui.theme.TextPrimary
import com.notch.island.ui.theme.TextSecondary
import java.util.Locale

/**
 * Continuous fluid spring-animated Dynamic Island container with Nothing OS styling.
 */
@Composable
fun IslandContainer(
    state: IslandState,
    cutout: CutoutInfo,
    onStateChange: (IslandState) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    // Interactive downward drag displacement
    var dragExtensionYPx by remember { mutableFloatStateOf(0f) }
    val dragExtensionYDp = with(density) { dragExtensionYPx.toDp() }

    // Resolve target resting geometry
    val targetBounds = remember(state, cutout) {
        IslandDimensions.resolve(state, cutout)
    }

    // Select spring physics profile according to state context
    val currentSpringSpec = remember(state) {
        when (state) {
            is IslandState.Peek -> IslandSpringPhysics.AlertDpSpec
            is IslandState.Expanded -> IslandSpringPhysics.MorphDpSpec
            is IslandState.Hidden -> IslandSpringPhysics.TuckDpSpec
            is IslandState.Collapsed -> IslandSpringPhysics.MorphDpSpec
        }
    }

    // Continuous animated dimensions
    val animatedWidth by animateDpAsState(
        targetValue = targetBounds.width,
        animationSpec = currentSpringSpec,
        label = "IslandWidth"
    )

    val animatedHeight by animateDpAsState(
        targetValue = targetBounds.height + dragExtensionYDp,
        animationSpec = if (dragExtensionYPx > 0f) IslandSpringPhysics.DragTracking.toSpringSpec() else currentSpringSpec,
        label = "IslandHeight"
    )

    val animatedCornerRadius by animateDpAsState(
        targetValue = targetBounds.cornerRadius,
        animationSpec = currentSpringSpec,
        label = "IslandCornerRadius"
    )

    val animatedElevation by animateDpAsState(
        targetValue = targetBounds.elevation,
        animationSpec = currentSpringSpec,
        label = "IslandElevation"
    )

    val animatedAlpha by animateFloatAsState(
        targetValue = targetBounds.alpha,
        animationSpec = IslandSpringPhysics.ExpansionMorph.toSpringSpec(),
        label = "IslandAlpha"
    )

    // Gestures listener implementation
    val gestureListener = remember(state) {
        object : IslandGestureListener {
            override fun onTap() {
                when (state) {
                    is IslandState.Collapsed -> {
                        // Expand active session
                        val currentTimer = TimerManager.timerState.value
                        if (currentTimer != null) {
                            IslandEventManager.expandTimer()
                        } else {
                            IslandEventManager.expandActiveMedia()
                        }
                    }
                    is IslandState.Peek -> {
                        IslandEventManager.expandActiveNotification()
                    }
                    is IslandState.Expanded -> {
                        // Collapse
                        IslandEventManager.resolveAndApplyCurrentState()
                    }
                    is IslandState.Hidden -> {
                        onStateChange(state.lastActiveState ?: IslandState.Collapsed())
                    }
                }
            }

            override fun onLongPress() {
                if (state is IslandState.Collapsed || state is IslandState.Peek) {
                    val currentTimer = TimerManager.timerState.value
                    if (currentTimer != null) {
                        IslandEventManager.expandTimer()
                    } else {
                        IslandEventManager.expandActiveMedia()
                    }
                }
            }

            override fun onDragDelta(dragYPx: Float) {
                dragExtensionYPx = dragYPx
            }

            override fun onDragEnd(releasedPastThreshold: Boolean, velocityY: Float) {
                dragExtensionYPx = 0f
                if (releasedPastThreshold) {
                    val currentTimer = TimerManager.timerState.value
                    if (currentTimer != null) {
                        IslandEventManager.expandTimer()
                    } else {
                        IslandEventManager.expandActiveMedia()
                    }
                }
            }

            override fun onSwipeUp() {
                dragExtensionYPx = 0f
                if (state is IslandState.Expanded || state is IslandState.Peek) {
                    IslandEventManager.resolveAndApplyCurrentState()
                }
            }

            override fun onSwipeInward() {
                onStateChange(IslandState.Hidden(lastActiveState = state))
            }

            override fun onSwipeOutward() {
                if (state is IslandState.Hidden) {
                    onStateChange(state.lastActiveState ?: IslandState.Collapsed())
                }
            }
        }
    }

    // Outer layout with pillGestureRoom (14dp invisible touch slop)
    val gestureSlopPadding = 14.dp
    Box(
        modifier = modifier
            .padding(top = targetBounds.topPadding)
            .padding(horizontal = gestureSlopPadding, vertical = 6.dp)
            .islandGestures(currentState = state, listener = gestureListener),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Main Dynamic Island Body
            val squircleShape = SquircleShape(animatedCornerRadius)
            Box(
                modifier = Modifier
                    .width(animatedWidth)
                    .height(animatedHeight)
                    .alpha(animatedAlpha)
                    .shadow(elevation = animatedElevation, shape = squircleShape)
                    .clip(squircleShape)
                    .background(OledBlack)
                    .border(width = 0.5.dp, color = IslandBorder, shape = squircleShape),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    is IslandState.Collapsed -> {
                        CompactPillContent(state = state, cutout = cutout)
                    }
                    is IslandState.Peek -> {
                        PeekBannerContent(state = state)
                    }
                    is IslandState.Expanded -> {
                        ExpandedInteractiveCardContent(
                            state = state,
                            context = context,
                            onCollapse = { gestureListener.onSwipeUp() }
                        )
                    }
                    is IslandState.Hidden -> {}
                }
            }

            // Feature 6: Multi-Activity Secondary Detached Bubble
            if (state is IslandState.Collapsed && state.secondaryBubble != null) {
                Spacer(modifier = Modifier.width(8.dp))
                SecondaryDetachedBubble(
                    subContent = state.secondaryBubble,
                    onClick = {
                        // Tapping detached bubble expands secondary activity (e.g. Timer)
                        IslandEventManager.expandTimer()
                    }
                )
            }
        }
    }
}

@Composable
private fun CompactPillContent(
    state: IslandState.Collapsed,
    cutout: CutoutInfo
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Leading content
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val leading = state.leading) {
                is CollapsedSubContent.Dot -> {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(leading.color))
                    )
                }
                is CollapsedSubContent.Icon -> {
                    Icon(
                        imageVector = leading.vector ?: Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(14.dp)
                    )
                }
                is CollapsedSubContent.Text -> {
                    Text(
                        text = leading.text,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = NdotFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                else -> {}
            }
        }

        // Camera Cutout clearance spacer
        Spacer(modifier = Modifier.weight(1f))

        // Trailing content
        Box(
            modifier = Modifier.wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            when (val trailing = state.trailing) {
                is CollapsedSubContent.Dot -> {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(trailing.color))
                    )
                }
                is CollapsedSubContent.AudioWave -> {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
                is CollapsedSubContent.Text -> {
                    Text(
                        text = trailing.text,
                        color = AccentOrange,
                        fontSize = 11.sp,
                        fontFamily = NdotFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun SecondaryDetachedBubble(
    subContent: CollapsedSubContent,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(37.dp)
            .shadow(elevation = 6.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(OledBlack)
            .border(width = 0.5.dp, color = IslandBorder, shape = CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when (subContent) {
            is CollapsedSubContent.AudioWave -> {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(15.dp)
                )
            }
            is CollapsedSubContent.Text -> {
                Text(
                    text = subContent.text,
                    color = AccentOrange,
                    fontSize = 10.sp,
                    fontFamily = NdotFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun PeekBannerContent(
    state: IslandState.Peek
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AccentGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = state.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = GeistFontFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.subtitle != null) {
                    Text(
                        text = state.subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = GeistFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (state.trailingText != null) {
            Text(
                text = state.trailingText,
                color = AccentGreen,
                fontSize = 12.sp,
                fontFamily = NdotFontFamily,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ExpandedInteractiveCardContent(
    state: IslandState.Expanded,
    context: Context,
    onCollapse: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp)
    ) {
        when (state.type) {
            ExpandedContentType.TIMER -> {
                // Feature 3: Live Countdown Timer Expanded Card
                ExpandedTimerContent(
                    timerData = state.payload as? TimerData ?: TimerData(300_000L, 240_000L, isRunning = true),
                    onCollapse = onCollapse
                )
            }

            ExpandedContentType.NOTIFICATION -> {
                // Feature 5: Inline Messaging Quick-Reply Card
                ExpandedNotificationReplyContent(
                    replyPayload = state.payload as? QuickReplyPayload,
                    title = state.title,
                    subtitle = state.subtitle,
                    context = context,
                    onCollapse = onCollapse
                )
            }

            else -> {
                // Default Media Player Expanded Card
                ExpandedMediaContent(
                    mediaTrack = state.payload as? MediaTrack,
                    title = state.title,
                    subtitle = state.subtitle
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Feature 4: Nothing OS Quick Controls Toolbar at bottom of every expanded card
        QuickControlsToolbar(context = context)
    }
}

@Composable
private fun ExpandedMediaContent(
    mediaTrack: MediaTrack?,
    title: String,
    subtitle: String?
) {
    var isPlaying by remember(mediaTrack?.isPlaying) { mutableStateOf(mediaTrack?.isPlaying ?: true) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NothingDarkSurface),
                contentAlignment = Alignment.Center
            ) {
                if (mediaTrack?.albumArt != null) {
                    androidx.compose.foundation.Image(
                        bitmap = mediaTrack.albumArt.asImageBitmap(),
                        contentDescription = "Album Art",
                        modifier = Modifier.size(46.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = mediaTrack?.title ?: title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = GeistFontFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitleText = mediaTrack?.artist ?: subtitle
                if (subtitleText != null) {
                    Text(
                        text = subtitleText,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontFamily = GeistFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(20.dp)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    val progressFraction = if (mediaTrack != null && mediaTrack.durationMs > 0) {
        (mediaTrack.positionMs.toFloat() / mediaTrack.durationMs).coerceIn(0f, 1f)
    } else {
        0.58f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(Color(0xFF2C2C2E))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progressFraction)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    val posStr = formatMillis(mediaTrack?.positionMs ?: 134000L)
    val remStr = if (mediaTrack != null && mediaTrack.durationMs > 0) {
        "-" + formatMillis(mediaTrack.durationMs - mediaTrack.positionMs)
    } else {
        "-1:38"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = posStr, fontSize = 11.sp, fontFamily = NdotFontFamily, color = TextSecondary)
        Text(text = remStr, fontSize = 11.sp, fontFamily = NdotFontFamily, color = TextSecondary)
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { mediaTrack?.prevAction?.invoke() }) {
            Icon(Icons.Rounded.FastRewind, "Previous", tint = TextPrimary, modifier = Modifier.size(24.dp))
        }

        IconButton(
            onClick = {
                if (isPlaying) {
                    mediaTrack?.pauseAction?.invoke()
                } else {
                    mediaTrack?.playAction?.invoke()
                }
                isPlaying = !isPlaying
            },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = OledBlack,
                modifier = Modifier.size(26.dp)
            )
        }

        IconButton(onClick = { mediaTrack?.nextAction?.invoke() }) {
            Icon(Icons.Rounded.FastForward, "Next", tint = TextPrimary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ExpandedTimerContent(
    timerData: TimerData,
    onCollapse: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = timerData.formattedRemaining,
            fontSize = 36.sp,
            fontFamily = NdotFontFamily,
            fontWeight = FontWeight.Bold,
            color = AccentOrange
        )

        Text(
            text = if (timerData.isPaused) "PAUSED" else "COUNTDOWN ACTIVE",
            fontSize = 11.sp,
            fontFamily = NdotFontFamily,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color(0xFF2C2C2E))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(timerData.progress)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(AccentOrange)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { TimerManager.addOneMinute() },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+1 min", fontFamily = NdotFontFamily, fontSize = 11.sp, color = TextPrimary)
            }

            IconButton(
                onClick = {
                    if (timerData.isRunning) {
                        TimerManager.pauseTimer()
                    } else {
                        TimerManager.resumeTimer()
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentOrange)
            ) {
                Icon(
                    imageVector = if (timerData.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Toggle",
                    tint = OledBlack,
                    modifier = Modifier.size(24.dp)
                )
            }

            OutlinedButton(
                onClick = {
                    TimerManager.resetTimer()
                    onCollapse()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Stop", fontFamily = NdotFontFamily, fontSize = 11.sp, color = NothingRed)
            }
        }
    }
}

@Composable
private fun ExpandedNotificationReplyContent(
    replyPayload: QuickReplyPayload?,
    title: String,
    subtitle: String?,
    context: Context,
    onCollapse: () -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    var hasSent by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(NothingRed.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Notifications, null, tint = NothingRed, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = replyPayload?.sender ?: title,
                    fontSize = 14.sp,
                    fontFamily = GeistFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = replyPayload?.message ?: subtitle ?: "",
                    fontSize = 12.sp,
                    fontFamily = GeistFontFamily,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (replyPayload != null && !hasSent) {
            // Inline reply text field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(0.5.dp, IslandBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontFamily = GeistFontFamily),
                    cursorBrush = SolidColor(NothingRed),
                    decorationBox = { innerTextField ->
                        if (replyText.isEmpty()) {
                            Text(
                                text = "Reply to ${replyPayload.sender}...",
                                color = NothingMuted,
                                fontSize = 12.sp,
                                fontFamily = GeistFontFamily
                            )
                        }
                        innerTextField()
                    }
                )

                IconButton(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            val success = replyPayload.sendReply(context, replyText)
                            if (success) {
                                hasSent = true
                                onCollapse()
                            }
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Rounded.Send, "Send", tint = NothingRed, modifier = Modifier.size(18.dp))
                }
            }
        } else if (hasSent) {
            Text(
                text = "✓ Reply Sent",
                fontSize = 12.sp,
                fontFamily = NdotFontFamily,
                color = AccentGreen,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun QuickControlsToolbar(
    context: Context
) {
    val isTorchOn by QuickControlsManager.isTorchOn.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF121212))
            .border(0.5.dp, IslandBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flashlight Button
        IconButton(
            onClick = { QuickControlsManager.toggleFlashlight(context) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FlashlightOn,
                contentDescription = "Flashlight",
                tint = if (isTorchOn) NothingRed else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Ringer Profile Button
        IconButton(
            onClick = { QuickControlsManager.toggleRingerMode(context) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.VolumeUp,
                contentDescription = "Ringer",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Notification Shade Pull Button
        IconButton(
            onClick = { QuickControlsManager.openNotificationShade() },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = "Shade",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Lock Screen Button
        IconButton(
            onClick = { QuickControlsManager.lockScreen() },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = "Lock",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatMillis(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
