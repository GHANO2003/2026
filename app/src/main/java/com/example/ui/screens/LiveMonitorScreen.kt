package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.camera.CameraProfileRegistry
import com.example.core.localization.LocalAppStrings
import com.example.core.models.AnamorphicPreset
import com.example.core.models.AspectRatioGuide
import com.example.core.models.CompositionConfig
import com.example.core.models.ControlChannelState
import com.example.core.models.ExposureToolsConfig
import com.example.core.models.FocusToolsConfig
import com.example.core.models.FrameAnalysisResult
import com.example.core.models.HybridConnectionState
import com.example.core.models.LutConfig
import com.example.core.models.MonitorTransformConfig
import com.example.core.models.RecordingStatus
import com.example.core.models.ScopeMode
import com.example.core.models.VideoScaleMode
import com.example.core.models.VideoSignalState
import com.example.ui.components.CompositionGuidesOverlay
import com.example.ui.components.ScopeCanvas
import com.example.ui.components.StatusPill
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalPanel
import com.example.ui.theme.CinemaBlack
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

@Composable
fun LiveMonitorScreen(
    hybridState: HybridConnectionState,
    displayFrame: Bitmap?,
    analysisResult: FrameAnalysisResult,
    focusConfig: FocusToolsConfig,
    exposureConfig: ExposureToolsConfig,
    compositionConfig: CompositionConfig,
    transformConfig: MonitorTransformConfig,
    lutConfig: LutConfig,
    recordingStatus: RecordingStatus,
    toolbarActions: List<String>,
    onScanUsbHdmi: () -> Unit,
    onOpenWizard: () -> Unit,
    onTogglePeaking: () -> Unit,
    onToggleZebra: () -> Unit,
    onToggleFalseColor: () -> Unit,
    onCycleScope: () -> Unit,
    onToggleGuides: () -> Unit,
    onToggleLut: () -> Unit,
    onCaptureScreenshot: () -> Unit,
    onToggleRecord: () -> Unit,
    onTogglePauseRecord: () -> Unit,
    onCycleScaleMode: () -> Unit,
    onRotate90: () -> Unit,
    onToggleMirror: () -> Unit,
    onToggleFlip: () -> Unit,
    onToggleHideControls: () -> Unit,
    onToggleOrientationLock: () -> Unit,
    onSetAspectGuide: (AspectRatioGuide) -> Unit,
    onSetAnamorphic: (AnamorphicPreset) -> Unit,
    onNavigateFocus: () -> Unit,
    onNavigateExposure: () -> Unit,
    onNavigateRemote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val activeSpec = CameraProfileRegistry.getSpec(hybridState.activeModel)
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var showGuideDrawer by remember { mutableStateOf(false) }

    val totalScale = pinchScale * focusConfig.zoomFactor.factor
    val contentScale = when (transformConfig.scaleMode) {
        VideoScaleMode.FIT -> ContentScale.Fit
        VideoScaleMode.FILL, VideoScaleMode.FULLSCREEN -> ContentScale.Crop
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    pinchScale = (pinchScale * zoom).coerceIn(1f, 8f)
                    if (pinchScale > 1.02f) {
                        panX += pan.x
                        panY += pan.y
                    } else {
                        panX = 0f
                        panY = 0f
                    }
                }
            }
    ) {
        // Main Incoming Video Canvas
        if (displayFrame != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = totalScale *
                            compositionConfig.anamorphicPreset.squeezeX *
                            (if (transformConfig.mirrorHorizontal) -1f else 1f)
                        scaleY = totalScale * (if (transformConfig.flipVertical) -1f else 1f)
                        rotationZ = transformConfig.rotationDegrees.toFloat()
                        translationX = panX
                        translationY = panY
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = displayFrame.asImageBitmap(),
                    contentDescription = "Live camera video feed",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )

                analysisResult.exposureOverlay?.let { expBmp ->
                    Image(
                        bitmap = expBmp.asImageBitmap(),
                        contentDescription = "Exposure analysis overlay",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale
                    )
                }

                analysisResult.peakingOverlay?.let { peakBmp ->
                    Image(
                        bitmap = peakBmp.asImageBitmap(),
                        contentDescription = "Focus peaking overlay",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale
                    )
                }
            }
        } else {
            // Honest No-Signal Viewfinder (Never fakes video)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusPill(
                    label = when (val v = hybridState.videoSignal) {
                        is VideoSignalState.Error -> "CAPTURE ERROR"
                        is VideoSignalState.Connecting -> "LOCKING SIGNAL"
                        else -> strings.noSignal
                    },
                    isActive = false,
                    isError = hybridState.videoSignal is VideoSignalState.Error,
                    isWarning = hybridState.videoSignal is VideoSignalState.Connecting
                )
                Text(
                    text = when (val v = hybridState.videoSignal) {
                        is VideoSignalState.Disconnected -> v.reason
                        is VideoSignalState.Error -> v.message
                        is VideoSignalState.Connecting -> "Negotiating UVC stream with ${v.deviceName}..."
                        is VideoSignalState.Active -> ""
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onScanUsbHdmi,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("monitor_scan_usb_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen, contentColor = CinemaBlack)
                    ) {
                        Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.scanUsbHdmi)
                    }
                    OutlinedButton(
                        onClick = onOpenWizard,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("monitor_open_wizard_btn")
                    ) {
                        Text(strings.openWizard, color = TextPrimary)
                    }
                }
            }
        }

        // Composition Guides Overlay
        CompositionGuidesOverlay(
            config = compositionConfig,
            centerCrosshairFromFocus = focusConfig.centerCrosshair,
            modifier = Modifier.fillMaxSize()
        )

        // Top Telemetry HUD
        if (!transformConfig.controlsHidden) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(activeSpec.modelName, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    when (val v = hybridState.videoSignal) {
                        is VideoSignalState.Active -> {
                            StatusPill("${v.width}x${v.height} ${"%.1f".format(v.actualFps)}fps", isActive = true)
                            StatusPill("${v.sourceType.label} • ${v.latencyMs}ms", isActive = true)
                        }
                        else -> StatusPill(hybridState.selectedMode.labelEn, isActive = false)
                    }
                    if (hybridState.controlChannel is ControlChannelState.Connected) {
                        StatusPill("CTRL OK", isActive = true)
                    }
                    if (lutConfig.enabled) {
                        StatusPill("LUT: ${lutConfig.activeLutName}", isActive = false, isWarning = true)
                    }
                    if (analysisResult.isFromHardwareFrame && exposureConfig.exposureWarningsEnabled &&
                        analysisResult.highlightClippedPercent > 3.5f
                    ) {
                        StatusPill("CLIP ${"%.1f".format(analysisResult.highlightClippedPercent)}%", isActive = false, isError = true)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (recordingStatus.isRecording) {
                        StatusPill(
                            label = "REC ${formatDuration(recordingStatus.elapsedSeconds)}",
                            isActive = false,
                            isError = true
                        )
                        IconButton(
                            onClick = onTogglePauseRecord,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                if (recordingStatus.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Pause/Resume Recording",
                                tint = WarningOrange
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleHideControls,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("monitor_hide_controls_btn")
                    ) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = "Hide Controls", tint = TextPrimary)
                    }
                }
            }
        } else {
            IconButton(
                onClick = onToggleHideControls,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .minimumInteractiveComponentSize()
                    .testTag("monitor_show_controls_btn")
            ) {
                Icon(Icons.Default.Visibility, contentDescription = "Show Controls", tint = TextPrimary)
            }
        }

        // Live Scope Window (bottom-left floating)
        if (exposureConfig.scopeMode != ScopeMode.NONE && !transformConfig.controlsHidden) {
            ScopeCanvas(
                scopeMode = exposureConfig.scopeMode,
                analysis = analysisResult,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 84.dp)
                    .width(230.dp)
                    .height(128.dp)
            )
        }

        // Composition Guide Drawer
        if (showGuideDrawer && !transformConfig.controlsHidden) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 78.dp)
                    .fillMaxWidth()
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp)),
                color = CharcoalPanel.copy(alpha = 0.95f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Framing Guides & Anamorphic De-Squeeze", style = MaterialTheme.typography.labelLarge, color = SignalCyan)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AspectRatioGuide.entries.forEach { g ->
                            FilterChip(
                                selected = compositionConfig.aspectRatioGuide == g,
                                onClick = { onSetAspectGuide(g) },
                                label = { Text(g.label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AnamorphicPreset.entries.forEach { p ->
                            FilterChip(
                                selected = compositionConfig.anamorphicPreset == p,
                                onClick = { onSetAnamorphic(p) },
                                label = { Text("Desq ${p.label}") }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Customizable Monitor Toolbar
        if (!transformConfig.controlsHidden) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (toolbarActions.contains("PEAKING")) {
                    ToolbarToggleButton("PEAK", focusConfig.peakingEnabled, ActiveGreen, Icons.Default.CenterFocusStrong, onTogglePeaking, "tb_peaking")
                }
                if (toolbarActions.contains("ZEBRA")) {
                    ToolbarToggleButton("ZEBRA", exposureConfig.zebraEnabled, WarningOrange, Icons.Default.Exposure, onToggleZebra, "tb_zebra")
                }
                if (toolbarActions.contains("FALSE_COLOR")) {
                    ToolbarToggleButton("FALSE", exposureConfig.falseColorEnabled, SignalCyan, Icons.Default.ColorLens, onToggleFalseColor, "tb_false_color")
                }
                if (toolbarActions.contains("WAVEFORM")) {
                    ToolbarToggleButton("SCOPE", exposureConfig.scopeMode != ScopeMode.NONE, ActiveGreen, Icons.Default.Tune, onCycleScope, "tb_scope")
                }
                if (toolbarActions.contains("GUIDES")) {
                    ToolbarToggleButton("GUIDE", compositionConfig.ruleOfThirds || showGuideDrawer, SignalCyan, Icons.Default.GridOn, {
                        onToggleGuides()
                        showGuideDrawer = !showGuideDrawer
                    }, "tb_guides")
                }
                if (toolbarActions.contains("LUT")) {
                    ToolbarToggleButton("LUT", lutConfig.enabled, WarningOrange, Icons.Default.ColorLens, onToggleLut, "tb_lut")
                }

                ToolbarToggleButton(transformConfig.scaleMode.labelEn, false, TextPrimary, Icons.Default.AspectRatio, onCycleScaleMode, "tb_scale")
                ToolbarToggleButton("${transformConfig.rotationDegrees}°", transformConfig.rotationDegrees != 0, SignalCyan, Icons.Default.RotateRight, onRotate90, "tb_rotate")
                ToolbarToggleButton("MIR", transformConfig.mirrorHorizontal, SignalCyan, Icons.Default.Flip, onToggleMirror, "tb_mirror")
                ToolbarToggleButton("FLIP", transformConfig.flipVertical, SignalCyan, Icons.Default.Flip, onToggleFlip, "tb_flip")
                ToolbarToggleButton("LOCK", transformConfig.orientationLocked, WarningOrange, Icons.Default.ScreenLockRotation, onToggleOrientationLock, "tb_lock")

                ToolbarToggleButton("FOCUS", false, ActiveGreen, Icons.Default.CenterFocusStrong, onNavigateFocus, "tb_nav_focus")
                ToolbarToggleButton("EXP", false, WarningOrange, Icons.Default.Exposure, onNavigateExposure, "tb_nav_exp")
                ToolbarToggleButton("CTRL", false, SignalCyan, Icons.Default.Tune, onNavigateRemote, "tb_nav_ctrl")

                IconButton(
                    onClick = onCaptureScreenshot,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CharcoalPanel)
                        .minimumInteractiveComponentSize()
                        .testTag("tb_screenshot")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Capture Screenshot", tint = TextPrimary)
                }

                Button(
                    onClick = onToggleRecord,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("tb_record_mp4"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recordingStatus.isRecording) RecordingRed else CharcoalPanel,
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        if (recordingStatus.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = if (recordingStatus.isRecording) Color.White else RecordingRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (recordingStatus.isRecording) strings.stopRec else strings.startRec, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ToolbarToggleButton(
    label: String,
    active: Boolean,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .height(48.dp)
            .testTag(tag),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (active) accent.copy(alpha = 0.22f) else CharcoalPanel,
            contentColor = if (active) accent else TextPrimary
        )
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatDuration(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return "%02d:%02d".format(m, s)
}
