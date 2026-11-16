package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.localization.AppLanguage
import com.example.core.models.FocusToolsConfig
import com.example.core.models.FocusZoomFactor
import com.example.core.models.FrameAnalysisResult
import com.example.core.models.PeakingColor
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalPanel
import com.example.ui.theme.CinemaBlack
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FocusToolsScreen(
    config: FocusToolsConfig,
    analysis: FrameAnalysisResult,
    language: AppLanguage,
    onUpdateConfig: ((FocusToolsConfig) -> FocusToolsConfig) -> Unit,
    onSetPeakingColor: (PeakingColor) -> Unit,
    onSetZoomFactor: (FocusZoomFactor) -> Unit,
    onResetZoomCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = language == AppLanguage.ARABIC
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = if (isArabic) "أدوات التركيز الاحترافية" else "Focus Assist & Peaking",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
        }

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
                Text(
                    text = "Edge Sharpness Index: ${"%.1f".format(analysis.sharpnessScore)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = ActiveGreen
                )
                LinearProgressIndicator(
                    progress = { (analysis.sharpnessScore / 150f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = ActiveGreen
                )
                ToggleRow(
                    title = if (isArabic) "تفعيل التركيز الذكي (Focus Peaking)" else "Focus Peaking (Sobel Edge Detection)",
                    checked = config.peakingEnabled,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(peakingEnabled = v) } },
                    tag = "focus_peaking_switch"
                )

                Text("Sensitivity: ${(config.peakingSensitivity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = config.peakingSensitivity,
                    onValueChange = { v -> onUpdateConfig { it.copy(peakingSensitivity = v) } },
                    valueRange = 0.1f..0.95f
                )

                Text("Highlight Intensity: ${(config.peakingIntensity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = config.peakingIntensity,
                    onValueChange = { v -> onUpdateConfig { it.copy(peakingIntensity = v) } },
                    valueRange = 0.25f..1.0f
                )

                Text(if (isArabic) "لون تحديد الحواف" else "Peaking Highlight Color", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeakingColor.entries.forEach { c ->
                        FilterChip(
                            selected = config.peakingColor == c,
                            onClick = { onSetPeakingColor(c) },
                            label = { Text(if (isArabic) c.labelAr else c.labelEn) },
                            modifier = Modifier.testTag("peaking_color_${c.name}")
                        )
                    }
                }
            }
        }

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
                Text(if (isArabic) "التكبير الدقيق للتركيز" else "Focus Magnifier (2x / 4x / 8x)", style = MaterialTheme.typography.titleMedium, color = SignalCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusZoomFactor.entries.forEach { z ->
                        FilterChip(
                            selected = config.zoomFactor == z,
                            onClick = { onSetZoomFactor(z) },
                            label = { Text(z.label) },
                            modifier = Modifier.testTag("focus_zoom_${z.name}")
                        )
                    }
                }
                OutlinedButton(onClick = onResetZoomCenter) {
                    Text(if (isArabic) "توسيط نقطة التركيز" else "Center Focus Selection", color = TextPrimary)
                }
                ToggleRow(
                    title = if (isArabic) "مؤشر المنتصف" else "Center Crosshair",
                    checked = config.centerCrosshair,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(centerCrosshair = v) } },
                    tag = "focus_crosshair_switch"
                )
                ToggleRow(
                    title = if (isArabic) "أدلة التركيز" else "Focus Frame Guides",
                    checked = config.focusGuides,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(focusGuides = v) } },
                    tag = "focus_guides_switch"
                )
            }
        }
    }
}
