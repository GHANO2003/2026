package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.localization.AppLanguage
import com.example.core.localization.LocalAppStrings
import com.example.core.models.LutConfig
import com.example.data.local.LutPresetEntity
import com.example.ui.components.PreviewOnlyBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalPanel
import com.example.ui.theme.CinemaBlack
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LutManagerScreen(
    lutConfig: LutConfig,
    luts: List<LutPresetEntity>,
    previewBitmap: Bitmap?,
    isLiveHardwareFrame: Boolean,
    language: AppLanguage,
    onSelectLut: (LutPresetEntity) -> Unit,
    onImportCubeUri: (Uri) -> Unit,
    onDeleteCustomLut: (String) -> Unit,
    onUpdateConfig: ((LutConfig) -> LutConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val isArabic = language == AppLanguage.ARABIC
    val cubePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportCubeUri(uri)
    }

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
                Text(
                    text = if (isArabic) "مدير الألوان والمعاينة LUT" else "3D LUT & Color Preview",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Button(
                    onClick = { cubePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.height(48.dp).testTag("lut_import_cube_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalCyan, contentColor = CinemaBlack)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isArabic) "استيراد .cube" else "Import .cube")
                }
            }
        }

        item {
            PreviewOnlyBanner(text = strings.previewOnlyBanner)
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalPanel)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "LUT preview frame",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                StatusPill(
                    label = if (isLiveHardwareFrame) "LIVE INPUT PREVIEW" else "REFERENCE STILL PREVIEW (NO LIVE HDMI)",
                    isActive = isLiveHardwareFrame,
                    isWarning = !isLiveHardwareFrame,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )
            }
        }

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
                ToggleRow(
                    title = if (isArabic) "تفعيل LUT على شاشة المراقبة" else "Enable LUT Preview",
                    checked = lutConfig.enabled,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(enabled = v) } },
                    tag = "lut_enabled_switch"
                )
                ToggleRow(
                    title = if (isArabic) "مقارنة قبل / بعد (Split)" else "Before / After Split Comparison",
                    checked = lutConfig.splitCompareEnabled,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(splitCompareEnabled = v) } },
                    tag = "lut_split_switch"
                )
                if (lutConfig.splitCompareEnabled) {
                    Text("Split Wipe: ${(lutConfig.splitPosition * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Slider(
                        value = lutConfig.splitPosition,
                        onValueChange = { v -> onUpdateConfig { it.copy(splitPosition = v) } },
                        valueRange = 0.1f..0.9f
                    )
                }

                Text("LUT Intensity: ${(lutConfig.intensity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = lutConfig.intensity,
                    onValueChange = { v -> onUpdateConfig { it.copy(intensity = v) } },
                    valueRange = 0f..1f
                )

                Text("Preview Brightness: ${"%.2f".format(lutConfig.brightness)}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = lutConfig.brightness,
                    onValueChange = { v -> onUpdateConfig { it.copy(brightness = v) } },
                    valueRange = -0.35f..0.35f
                )

                Text("Preview Contrast: ${"%.2f".format(lutConfig.contrast)}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = lutConfig.contrast,
                    onValueChange = { v -> onUpdateConfig { it.copy(contrast = v) } },
                    valueRange = 0.6f..1.6f
                )

                Text("Preview Saturation: ${"%.2f".format(lutConfig.saturation)}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = lutConfig.saturation,
                    onValueChange = { v -> onUpdateConfig { it.copy(saturation = v) } },
                    valueRange = 0f..1.8f
                )

                OutlinedButton(
                    onClick = {
                        onUpdateConfig {
                            it.copy(brightness = 0f, contrast = 1f, saturation = 1f, sharpness = 0f, intensity = 1f)
                        }
                    }
                ) {
                    Text(if (isArabic) "إعادة ضبط المعاينة" else "Reset Preview Grading", color = TextPrimary)
                }
            }
        }

        item {
            Text(
                text = if (isArabic) "مكتبة ملفات LUT" else "LUT Library",
                style = MaterialTheme.typography.titleMedium,
                color = ActiveGreen
            )
        }

        items(luts, key = { it.id }) { preset ->
            val selected = lutConfig.activeLutId == preset.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CharcoalPanel)
                    .border(1.dp, if (selected) ActiveGreen else CharcoalBorder, RoundedCornerShape(12.dp))
                    .clickable { onSelectLut(preset) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(preset.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "${preset.cubeSize}x${preset.cubeSize}x${preset.cubeSize} • ${if (preset.isBuiltIn) "Built-in" else "Imported .cube"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
                if (selected) StatusPill("ACTIVE", isActive = true)
                if (!preset.isBuiltIn) {
                    IconButton(
                        onClick = { onDeleteCustomLut(preset.id) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete custom LUT", tint = TextSecondary)
                    }
                }
            }
        }
    }
}
