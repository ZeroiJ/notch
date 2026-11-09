package com.notch.island

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.notch.island.core.CutoutHelper
import com.notch.island.core.CutoutInfo
import com.notch.island.core.IslandEventManager
import com.notch.island.core.QuickControlsManager
import com.notch.island.core.TimerManager
import com.notch.island.model.CollapsedSubContent
import com.notch.island.model.ExpandedContentType
import com.notch.island.model.IslandState
import com.notch.island.service.IslandAccessibilityService
import com.notch.island.ui.theme.AccentBlue
import com.notch.island.ui.theme.AccentGreen
import com.notch.island.ui.theme.AccentOrange
import com.notch.island.ui.theme.GeistFontFamily
import com.notch.island.ui.theme.IslandTheme
import com.notch.island.ui.theme.NdotFontFamily
import com.notch.island.ui.theme.NothingRed
import com.notch.island.ui.theme.OledBlack
import com.notch.island.ui.theme.TextPrimary
import com.notch.island.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)
    private var hasNotificationListenerPermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)
    private var cutoutInfo by mutableStateOf<CutoutInfo?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            IslandTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = OledBlack
                ) {
                    MainScreen(
                        hasOverlayPermission = hasOverlayPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        hasNotificationListenerPermission = hasNotificationListenerPermission,
                        hasAccessibilityPermission = hasAccessibilityPermission,
                        isServiceRunning = isServiceRunning,
                        cutoutInfo = cutoutInfo,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestNotificationPermission = { requestNotificationPermission() },
                        onRequestNotificationListenerPermission = { requestNotificationListenerPermission() },
                        onRequestAccessibilityPermission = { requestAccessibilityPermission() },
                        onToggleService = {
                            if (isServiceRunning) {
                                IslandOverlayService.stop(this)
                                isServiceRunning = false
                            } else {
                                if (!Settings.canDrawOverlays(this)) {
                                    requestOverlayPermission()
                                } else {
                                    IslandOverlayService.start(this)
                                    isServiceRunning = true
                                }
                            }
                        },
                        onTriggerPeek = {
                            IslandEventManager.postPeek(
                                title = "AirPods Pro Connected",
                                subtitle = "Battery 94% • High Fidelity",
                                trailingText = "94%",
                                durationMs = 3500L
                            )
                        },
                        onTriggerNowPlaying = {
                            IslandOverlayService.updateState(
                                IslandState.Collapsed(
                                    leading = CollapsedSubContent.Dot(0xFF30D158),
                                    trailing = CollapsedSubContent.AudioWave(isPlaying = true)
                                )
                            )
                        },
                        onStartTimer = {
                            TimerManager.startTimer(5)
                        },
                        onTriggerDualIsland = {
                            TimerManager.startTimer(4)
                            IslandEventManager.resolveAndApplyCurrentState()
                        },
                        onTriggerQuickReply = {
                            IslandEventManager.postNotificationAlert(
                                title = "Nothing OS Notification",
                                subtitle = "Hey, swipe down or tap to reply inline!",
                                trailingText = "CHAT",
                                replyPayload = null
                            )
                        },
                        onToggleFlashlight = {
                            QuickControlsManager.toggleFlashlight(this)
                        },
                        onTriggerHide = {
                            IslandOverlayService.updateState(
                                IslandState.Hidden(
                                    lastActiveState = IslandState.Collapsed(
                                        leading = CollapsedSubContent.Dot(0xFF30D158),
                                        trailing = CollapsedSubContent.AudioWave(isPlaying = true)
                                    )
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        cutoutInfo = CutoutHelper.resolveCutout(this, window.decorView.rootWindowInsets)
    }

    private fun checkPermissions() {
        hasOverlayPermission = Settings.canDrawOverlays(this)
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        hasNotificationListenerPermission = androidx.core.app.NotificationManagerCompat
            .getEnabledListenerPackages(this)
            .contains(packageName)
        hasAccessibilityPermission = IslandAccessibilityService.isAccessibilityEnabled(this)
        isServiceRunning = IslandOverlayService.isRunning()
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun requestNotificationListenerPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun MainScreen(
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    hasNotificationListenerPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    isServiceRunning: Boolean,
    cutoutInfo: CutoutInfo?,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestNotificationListenerPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onToggleService: () -> Unit,
    onTriggerPeek: () -> Unit,
    onTriggerNowPlaying: () -> Unit,
    onStartTimer: () -> Unit,
    onTriggerDualIsland: () -> Unit,
    onTriggerQuickReply: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onTriggerHide: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "DYNAMIC NOTCH",
            fontSize = 28.sp,
            fontFamily = NdotFontFamily,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Nothing Phone (4b) Edition • All 6 Features Active",
            fontSize = 12.sp,
            fontFamily = GeistFontFamily,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Service Start / Stop Button
        val canStart = hasOverlayPermission && hasNotificationPermission
        Button(
            onClick = onToggleService,
            enabled = canStart,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isServiceRunning) NothingRed else AccentGreen,
                disabledContainerColor = Color(0xFF2C2C2E)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Icon(
                imageVector = if (isServiceRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isServiceRunning) "Stop Overlay Service" else "Start Dynamic Island Overlay",
                fontWeight = FontWeight.Bold,
                fontFamily = GeistFontFamily,
                fontSize = 15.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live Interactive Feature Test Bench
        if (isServiceRunning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141416))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "NOTHING OS TEST BENCH",
                        fontSize = 14.sp,
                        fontFamily = NdotFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Trigger real-time live activities, dual-island, and quick-replies",
                        fontSize = 12.sp,
                        fontFamily = GeistFontFamily,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onStartTimer,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Schedule, null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("5m Timer", fontSize = 11.sp, fontFamily = NdotFontFamily, color = TextPrimary)
                        }

                        OutlinedButton(
                            onClick = onTriggerNowPlaying,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.GraphicEq, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Music Pill", fontSize = 11.sp, fontFamily = NdotFontFamily, color = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onTriggerDualIsland,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.DynamicFeed, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Dual Island", fontSize = 11.sp, fontFamily = NdotFontFamily, color = TextPrimary)
                        }

                        OutlinedButton(
                            onClick = onTriggerQuickReply,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Message, null, tint = NothingRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Quick Reply", fontSize = 11.sp, fontFamily = NdotFontFamily, color = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onToggleFlashlight,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.FlashlightOn, null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Flashlight", fontSize = 11.sp, fontFamily = NdotFontFamily, color = TextPrimary)
                        }

                        OutlinedButton(
                            onClick = onTriggerPeek,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Notifications, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Peek Alert", fontSize = 11.sp, fontFamily = NdotFontFamily, color = TextPrimary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Hardware Cutout Telemetry Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141416))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "HARDWARE CUTOUT TELEMETRY",
                    fontSize = 13.sp,
                    fontFamily = NdotFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (cutoutInfo != null) {
                    Text(
                        text = "Position: ${cutoutInfo.position} (Auto Nothing Phone Calibrated)",
                        fontSize = 12.sp,
                        fontFamily = GeistFontFamily,
                        color = TextSecondary
                    )
                    Text(
                        text = "Camera Center X: ${cutoutInfo.screenWidthPx / 2}px • Lens Width: 86px (32.8dp)",
                        fontSize = 12.sp,
                        fontFamily = GeistFontFamily,
                        color = TextSecondary
                    )
                    Text(
                        text = "Status Bar Safe Inset: 126px (48dp)",
                        fontSize = 12.sp,
                        fontFamily = GeistFontFamily,
                        color = TextSecondary
                    )
                } else {
                    Text(
                        text = "Locking display cutout metrics...",
                        fontSize = 12.sp,
                        fontFamily = GeistFontFamily,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Permissions Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141416))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "SYSTEM PERMISSIONS",
                    fontSize = 13.sp,
                    fontFamily = NdotFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                PermissionRow(
                    name = "Overlay (SYSTEM_ALERT_WINDOW)",
                    isGranted = hasOverlayPermission,
                    onRequest = onRequestOverlayPermission
                )

                Spacer(modifier = Modifier.height(10.dp))

                PermissionRow(
                    name = "Notifications (POST_NOTIFICATIONS)",
                    isGranted = hasNotificationPermission,
                    onRequest = onRequestNotificationPermission
                )

                Spacer(modifier = Modifier.height(10.dp))

                PermissionRow(
                    name = "Media & Alerts (Notification Access)",
                    isGranted = hasNotificationListenerPermission,
                    onRequest = onRequestNotificationListenerPermission
                )

                Spacer(modifier = Modifier.height(10.dp))

                PermissionRow(
                    name = "Lock Screen Overlay (Accessibility Service)",
                    isGranted = hasAccessibilityPermission,
                    onRequest = onRequestAccessibilityPermission
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PermissionRow(
    name: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 12.sp,
                fontFamily = GeistFontFamily,
                color = if (isGranted) AccentGreen else AccentOrange,
                fontWeight = FontWeight.Medium
            )
        }

        if (isGranted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Granted",
                tint = AccentGreen,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E))
            ) {
                Text(text = "Grant", fontSize = 11.sp, fontFamily = GeistFontFamily, color = TextPrimary)
            }
        }
    }
}
