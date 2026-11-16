package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.camera.CameraProfileRegistry
import com.example.core.localization.AppLanguage
import com.example.core.models.CameraControlSnapshot
import com.example.core.models.CameraSettingKey
import com.example.core.models.ControlChannelState
import com.example.core.models.HybridConnectionState
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
fun RemoteControlScreen(
    hybridState: HybridConnectionState,
    snapshot: CameraControlSnapshot,
    language: AppLanguage,
    onRefreshSettings: () -> Unit,
    onApplySetting: (CameraSettingKey, String) -> Unit,
    onTriggerAf: () -> Unit,
    onCapturePhoto: () -> Unit,
    onToggleCameraVideo: (Boolean) -> Unit,
    onOpenWizard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = language == AppLanguage.ARABIC
    val spec = CameraProfileRegistry.getSpec(hybridState.activeModel)
    val isConnected = hybridState.controlChannel is ControlChannelState.Connected

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(spec.modelName, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text(spec.wirelessProtocolName, style = MaterialTheme.typography.bodyMedium, color = SignalCyan)
                }
                StatusPill(
                    label = if (isConnected) "CONNECTED" else "DISCONNECTED",
                    isActive = isConnected,
                    isError = hybridState.controlChannel is ControlChannelState.Error
                )
            }
        }

        if (!isConnected) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CharcoalPanel)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isArabic)
                            "التحكم الحقيقي يتطلب اتصال الكاميرا عبر Wi-Fi أو USB PTP. يتم تحديث القيم فقط بعد تأكيد الكاميرا."
                        else
                            "Real camera control requires an active Wi-Fi or USB PTP session. Settings update only after camera confirmation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Button(
                        onClick = onOpenWizard,
                        modifier = Modifier.height(48.dp).testTag("remote_open_wizard_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen, contentColor = CinemaBlack)
                    ) {
                        Text(if (isArabic) "فتح معالج الاتصال" else "Connect Camera via Wizard")
                    }
                }
            }
        }

        if (snapshot.lastCommandError != null) {
            item {
                Text(
                    text = "Camera Error: ${snapshot.lastCommandError}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RecordingRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RecordingRed.copy(alpha = 0.14f))
                        .padding(12.dp)
                )
            }
        }

        // Primary Shutter & Video Actions (model-confirmed only)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CharcoalPanel)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onCapturePhoto,
                        enabled = spec.supportsRemotePhoto,
                        modifier = Modifier.weight(1f).height(50.dp).testTag("remote_shutter_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen, contentColor = CinemaBlack)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isArabic) "التقاط" else "Shutter")
                    }
                    OutlinedButton(
                        onClick = onTriggerAf,
                        modifier = Modifier.weight(1f).height(50.dp).testTag("remote_af_btn")
                    ) {
                        Icon(Icons.Default.CenterFocusWeak, contentDescription = null, tint = SignalCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AF", color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = onRefreshSettings,
                        modifier = Modifier.height(50.dp).testTag("remote_refresh_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh camera settings", tint = TextPrimary)
                    }
                }

                if (spec.supportsRemoteVideoToggle) {
                    Button(
                        onClick = { onToggleCameraVideo(snapshot.isRecordingVideoOnCamera) },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("remote_movie_rec_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (snapshot.isRecordingVideoOnCamera) RecordingRed else CharcoalBorder,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            if (snapshot.isRecordingVideoOnCamera) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = if (snapshot.isRecordingVideoOnCamera) TextPrimary else RecordingRed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (snapshot.isRecordingVideoOnCamera) "Stop Camera Movie REC" else "Start Camera Movie REC")
                    }
                } else {
                    Text(
                        text = if (isArabic)
                            "ملاحظة: ${spec.modelName} لا تدعم بدء تسجيل الفيديو عن بُعد عبر Wi-Fi/PTP. استخدم زر التسجيل بالكاميرا."
                        else
                            "Note: ${spec.modelName} firmware does not expose remote movie record start/stop over Wi-Fi/PTP.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarningOrange
                    )
                }
            }
        }

        // Confirmed Exposure Settings Controls
        item {
            SettingSelectorCard(
                title = CameraSettingKey.ISO.labelEn,
                currentValue = snapshot.iso,
                options = snapshot.availableIso,
                onSelect = { onApplySetting(CameraSettingKey.ISO, it) },
                tagPrefix = "ctrl_iso"
            )
        }
        item {
            SettingSelectorCard(
                title = CameraSettingKey.SHUTTER_SPEED.labelEn,
                currentValue = snapshot.shutterSpeed,
                options = snapshot.availableShutter,
                onSelect = { onApplySetting(CameraSettingKey.SHUTTER_SPEED, it) },
                tagPrefix = "ctrl_shutter"
            )
        }
        item {
            SettingSelectorCard(
                title = CameraSettingKey.APERTURE.labelEn,
                currentValue = snapshot.aperture,
                options = snapshot.availableAperture,
                onSelect = { onApplySetting(CameraSettingKey.APERTURE, it) },
                tagPrefix = "ctrl_aperture"
            )
        }
        item {
            SettingSelectorCard(
                title = CameraSettingKey.WHITE_BALANCE.labelEn,
                currentValue = snapshot.whiteBalance,
                options = snapshot.availableWb,
                onSelect = { onApplySetting(CameraSettingKey.WHITE_BALANCE, it) },
                tagPrefix = "ctrl_wb"
            )
        }
        item {
            SettingSelectorCard(
                title = CameraSettingKey.EXPOSURE_COMP.labelEn,
                currentValue = snapshot.exposureComp,
                options = snapshot.availableExpComp,
                onSelect = { onApplySetting(CameraSettingKey.EXPOSURE_COMP, it) },
                tagPrefix = "ctrl_ev"
            )
        }
    }
}

@Composable
private fun SettingSelectorCard(
    title: String,
    currentValue: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    tagPrefix: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CharcoalPanel)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(currentValue, style = MaterialTheme.typography.labelLarge, color = ActiveGreen)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { opt ->
                FilterChip(
                    selected = currentValue == opt,
                    onClick = { onSelect(opt) },
                    label = { Text(opt) },
                    modifier = Modifier.testTag("${tagPrefix}_$opt")
                )
            }
        }
    }
}
