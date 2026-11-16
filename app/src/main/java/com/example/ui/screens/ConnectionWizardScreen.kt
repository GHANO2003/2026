package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.camera.CameraProfileRegistry
import com.example.core.localization.AppLanguage
import com.example.core.models.CameraBrand
import com.example.core.models.CameraModelId
import com.example.core.models.ConnectionMode
import com.example.core.models.ControlChannelState
import com.example.core.models.HybridConnectionState
import com.example.core.models.SetupIllustrationType
import com.example.core.models.VideoSignalState
import com.example.ui.components.StatusPill
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalPanel
import com.example.ui.theme.CinemaBlack
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ConnectionWizardScreen(
    initialModelId: CameraModelId,
    hybridState: HybridConnectionState,
    defaultWifiIp: String,
    language: AppLanguage,
    onSelectModel: (CameraModelId) -> Unit,
    onSelectMode: (ConnectionMode) -> Unit,
    onScanHdmi: () -> Unit,
    onScanUsbControl: () -> Unit,
    onConnectWifi: (String, Int, Boolean) -> Unit,
    onSaveProfile: (String, CameraModelId, ConnectionMode, String, Int) -> Unit,
    onOpenMonitor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = language == AppLanguage.ARABIC
    var selectedBrand by remember(initialModelId) { mutableStateOf(initialModelId.brand) }
    var selectedModel by remember(initialModelId) { mutableStateOf(initialModelId) }
    var selectedMode by remember { mutableStateOf(hybridState.selectedMode) }
    var wifiIp by remember(selectedModel) {
        mutableStateOf(
            if (selectedModel == CameraModelId.CANON_EOS_90D) "192.168.1.2" else defaultWifiIp
        )
    }
    var wifiPort by remember(selectedModel) {
        mutableIntStateOf(if (selectedModel == CameraModelId.CANON_EOS_90D) 8080 else 15740)
    }
    var profileName by remember(selectedModel) { mutableStateOf("${selectedModel.modelName} Rig") }

    val spec = CameraProfileRegistry.getSpec(selectedModel)
    val guide = spec.setupGuides.firstOrNull {
        it.mode == selectedMode || (selectedMode == ConnectionMode.HYBRID && it.mode == ConnectionMode.HDMI_CAPTURE)
    } ?: spec.setupGuides.first()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (isArabic) "معالج إعداد وتوصيل الكاميرا" else "Camera Connection Wizard",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
        }

        // Step 1 & 2: Brand & Model
        item {
            WizardSectionCard(title = if (isArabic) "1. اختر الشركة والطراز" else "1. Select Brand & Camera Model") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CameraBrand.entries.forEach { brand ->
                        FilterChip(
                            selected = selectedBrand == brand,
                            onClick = {
                                selectedBrand = brand
                                val firstForBrand = CameraProfileRegistry.allModels.first { it.brand == brand }.id
                                selectedModel = firstForBrand
                                onSelectModel(firstForBrand)
                            },
                            label = { Text(brand.displayName) },
                            modifier = Modifier.testTag("wizard_brand_${brand.name}")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CameraProfileRegistry.allModels.filter { it.brand == selectedBrand }.forEach { m ->
                        FilterChip(
                            selected = selectedModel == m.id,
                            onClick = {
                                selectedModel = m.id
                                onSelectModel(m.id)
                                wifiIp = if (m.id == CameraModelId.CANON_EOS_90D) "192.168.1.2" else "192.168.1.1"
                                wifiPort = if (m.id == CameraModelId.CANON_EOS_90D) 8080 else 15740
                                profileName = "${m.modelName} Rig"
                            },
                            label = { Text(m.modelName) },
                            modifier = Modifier.testTag("wizard_model_${m.id.name}")
                        )
                    }
                }
            }
        }

        // Step 3: Connection Mode
        item {
            WizardSectionCard(title = if (isArabic) "2. وضع التوصيل (HDMI / Wi-Fi / هجين)" else "2. Connection Mode") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConnectionMode.entries.forEach { mode ->
                        val active = selectedMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) ActiveGreen.copy(alpha = 0.14f) else CinemaBlack)
                                .border(1.dp, if (active) ActiveGreen else CharcoalBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedMode = mode
                                    onSelectMode(mode)
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (isArabic) mode.labelAr else mode.labelEn, color = TextPrimary)
                            if (active) StatusPill("SELECTED", isActive = true)
                        }
                    }
                }
            }
        }

        // Step 4: Illustrated Setup Guide
        item {
            WizardSectionCard(title = if (isArabic) guide.titleAr else guide.titleEn) {
                val imgRes = if (guide.imageResType == SetupIllustrationType.HDMI_OTG) {
                    R.drawable.img_guide_hdmi_otg
                } else {
                    R.drawable.img_guide_wifi_dslr
                }
                Image(
                    painter = painterResource(id = imgRes),
                    contentDescription = guide.titleEn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(10.dp))
                val steps = if (isArabic) guide.stepsAr else guide.stepsEn
                steps.forEachIndexed { idx, step ->
                    Text(
                        text = "${idx + 1}. $step",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        }

        // Step 5: Real Connection Test
        item {
            WizardSectionCard(title = if (isArabic) "3. اختبار الاتصال الفعلي والقدرات" else "3. Test Live Hardware & Protocol Connection") {
                if (selectedMode == ConnectionMode.WIFI_WIRELESS || selectedMode == ConnectionMode.HYBRID) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = wifiIp,
                            onValueChange = { wifiIp = it },
                            label = { Text("Camera IP") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = wifiPort.toString(),
                            onValueChange = { wifiPort = it.toIntOrNull() ?: wifiPort },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedMode == ConnectionMode.HDMI_CAPTURE || selectedMode == ConnectionMode.HYBRID) {
                        Button(
                            onClick = onScanHdmi,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("wizard_test_hdmi_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen, contentColor = CinemaBlack)
                        ) {
                            Text(if (isArabic) "فحص HDMI UVC" else "Test HDMI UVC")
                        }
                    }
                    if (selectedMode == ConnectionMode.USB_PTP) {
                        Button(
                            onClick = onScanUsbControl,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("wizard_test_usb_ptp_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = SignalCyan, contentColor = CinemaBlack)
                        ) {
                            Text(if (isArabic) "فحص USB PTP" else "Test USB PTP")
                        }
                    }
                    if (selectedMode == ConnectionMode.WIFI_WIRELESS || selectedMode == ConnectionMode.HYBRID) {
                        OutlinedButton(
                            onClick = {
                                onConnectWifi(
                                    wifiIp.trim(),
                                    wifiPort,
                                    selectedMode == ConnectionMode.WIFI_WIRELESS
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("wizard_test_wifi_btn")
                        ) {
                            Text(if (isArabic) "اختبار Wi-Fi" else "Test Wi-Fi Control", color = TextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Video Status: ${
                        when (val v = hybridState.videoSignal) {
                            is VideoSignalState.Active -> "LOCKED ${v.width}x${v.height} @ ${"%.1f".format(v.actualFps)}fps (${v.pixelFormat})"
                            is VideoSignalState.Error -> "ERROR: ${v.message}"
                            is VideoSignalState.Connecting -> "Connecting to ${v.deviceName}..."
                            is VideoSignalState.Disconnected -> v.reason
                        }
                    }",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hybridState.videoSignal is VideoSignalState.Active) ActiveGreen else TextSecondary
                )
                Text(
                    text = "Control Status: ${
                        when (val c = hybridState.controlChannel) {
                            is ControlChannelState.Connected -> "CONNECTED (${c.channelType.label} • ${c.endpoint})"
                            is ControlChannelState.Connecting -> "Connecting to ${c.endpoint}..."
                            is ControlChannelState.Error -> "ERROR: ${c.message}"
                            ControlChannelState.Disconnected -> "Not connected"
                        }
                    }",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hybridState.controlChannel is ControlChannelState.Connected) SignalCyan else TextSecondary
                )
            }
        }

        // Step 6: Save Camera Profile
        item {
            WizardSectionCard(title = if (isArabic) "4. حفظ ملف الكاميرا" else "4. Save Camera Profile") {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text(if (isArabic) "اسم الملف" else "Profile Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onSaveProfile(profileName, selectedModel, selectedMode, wifiIp, wifiPort) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("wizard_save_profile_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = SignalCyan, contentColor = CinemaBlack)
                    ) {
                        Text(if (isArabic) "حفظ الملف" else "Save Profile")
                    }
                    OutlinedButton(
                        onClick = onOpenMonitor,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("wizard_open_monitor_btn")
                    ) {
                        Text(if (isArabic) "فتح المراقب" else "Open Monitor", color = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CharcoalPanel)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = ActiveGreen)
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}
