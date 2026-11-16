package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.camera.CameraProfileRegistry
import com.example.core.localization.LocalAppStrings
import com.example.core.models.ControlChannelState
import com.example.core.models.HybridConnectionState
import com.example.core.models.VideoSignalState
import com.example.data.local.SavedCameraProfileEntity
import com.example.ui.components.StatusPill
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalPanel
import com.example.ui.theme.CinemaBlack
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

@Composable
fun DashboardScreen(
    hybridState: HybridConnectionState,
    savedProfiles: List<SavedCameraProfileEntity>,
    onScanUsbHdmi: () -> Unit,
    onOpenMonitor: () -> Unit,
    onOpenWizard: () -> Unit,
    onOpenCameraSelection: () -> Unit,
    onOpenCompatibility: () -> Unit,
    onOpenLutManager: () -> Unit,
    onOpenSettings: () -> Unit,
    onLoadProfile: (SavedCameraProfileEntity) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val activeSpec = CameraProfileRegistry.getSpec(hybridState.activeModel)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack)
            .testTag("dashboard_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(18.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_cinema_rig),
                    contentDescription = "ProMonitor 360 Cinema Rig",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.88f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            label = "${strings.videoChannel}: ${
                                when (val v = hybridState.videoSignal) {
                                    is VideoSignalState.Active -> "${v.width}x${v.height} ${"%.0f".format(v.actualFps)}fps"
                                    is VideoSignalState.Connecting -> "CONNECTING"
                                    is VideoSignalState.Error -> "ERROR"
                                    is VideoSignalState.Disconnected -> strings.noSignal
                                }
                            }",
                            isActive = hybridState.videoSignal is VideoSignalState.Active,
                            isError = hybridState.videoSignal is VideoSignalState.Error
                        )
                        StatusPill(
                            label = "${strings.controlChannel}: ${
                                when (hybridState.controlChannel) {
                                    is ControlChannelState.Connected -> "READY"
                                    is ControlChannelState.Connecting -> "SYNCING"
                                    is ControlChannelState.Error -> "ERR"
                                    ControlChannelState.Disconnected -> "IDLE"
                                }
                            }",
                            isActive = hybridState.controlChannel is ControlChannelState.Connected,
                            isError = hybridState.controlChannel is ControlChannelState.Error
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "${activeSpec.modelName} • ${hybridState.selectedMode.labelEn}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SignalCyan
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenMonitor,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("dashboard_launch_monitor_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen, contentColor = CinemaBlack)
                ) {
                    Icon(Icons.Default.Monitor, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.openMonitor, style = MaterialTheme.typography.titleMedium)
                }

                OutlinedButton(
                    onClick = onScanUsbHdmi,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("dashboard_scan_usb_btn")
                ) {
                    Icon(Icons.Default.Usb, contentDescription = null, tint = SignalCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.scanUsbHdmi, color = TextPrimary)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalPanel),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ACTIVE RIG TELEMETRY", style = MaterialTheme.typography.labelMedium, color = SignalCyan)
                    Text(
                        text = "Model: ${activeSpec.modelName} (${activeSpec.hdmiOutputSpec})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "USB Bus: ${hybridState.detectedUsbSummary ?: "No USB-C OTG device attached"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "Thermal: ${"%.1f".format(hybridState.thermalCelsius)}°C • Battery: ${hybridState.batteryPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hybridState.thermalCelsius > 42f) WarningOrange else TextSecondary
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardTile(
                        title = strings.openWizard,
                        subtitle = "HDMI / Wi-Fi / Hybrid",
                        icon = Icons.Default.CastConnected,
                        accent = ActiveGreen,
                        tag = "tile_wizard",
                        onClick = onOpenWizard,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardTile(
                        title = strings.cameraSelection,
                        subtitle = "D5600 • D5500 • 90D",
                        icon = Icons.Default.CameraAlt,
                        accent = SignalCyan,
                        tag = "tile_cameras",
                        onClick = onOpenCameraSelection,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardTile(
                        title = strings.compatibilityCenter,
                        subtitle = "USB / UVC / Protocol Test",
                        icon = Icons.Default.HealthAndSafety,
                        accent = WarningOrange,
                        tag = "tile_diagnostics",
                        onClick = onOpenCompatibility,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardTile(
                        title = strings.lutManager,
                        subtitle = ".cube 3D LUTs & Compare",
                        icon = Icons.Default.ColorLens,
                        accent = SignalCyan,
                        tag = "tile_luts",
                        onClick = onOpenLutManager,
                        modifier = Modifier.weight(1f)
                    )
                }
                DashboardTile(
                    title = strings.settings,
                    subtitle = "English / العربية RTL • Keep Awake • Toolbar",
                    icon = Icons.Default.Settings,
                    accent = TextSecondary,
                    tag = "tile_settings",
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (savedProfiles.isNotEmpty()) {
            item {
                Text(
                    text = strings.savedProfiles.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
            }
            items(savedProfiles, key = { it.id }) { profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalPanel)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                        .clickable { onLoadProfile(profile) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SettingsRemote, contentDescription = null, tint = ActiveGreen)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.profileName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            "${profile.preferredMode} • ${profile.wifiIpAddress}:${profile.wifiPort}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = { onDeleteProfile(profile.id) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete profile", tint = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CharcoalPanel)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag(tag)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CharcoalElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
    }
}
