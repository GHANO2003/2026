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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.localization.AppLanguage
import com.example.core.models.ExposureToolsConfig
import com.example.core.models.FrameAnalysisResult
import com.example.core.models.ScopeMode
import com.example.ui.components.FalseColorLegendBar
import com.example.ui.components.ScopeCanvas
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
fun ExposureToolsScreen(
    config: ExposureToolsConfig,
    analysis: FrameAnalysisResult,
    language: AppLanguage,
    onUpdateConfig: ((ExposureToolsConfig) -> ExposureToolsConfig) -> Unit,
    onSetScopeMode: (ScopeMode) -> Unit,
    onReset: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) "أدوات تحليل التعريض والسطوع" else "Exposure & Scope Analysis",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                OutlinedButton(onClick = onReset, modifier = Modifier.testTag("exposure_reset_btn")) {
                    Text(if (isArabic) "إعادة ضبط" else "Reset", color = WarningOrange)
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
                Text(if (isArabic) "أجهزة القياس المباشرة" else "Live Waveform & Histograms", style = MaterialTheme.typography.titleMedium, color = SignalCyan)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScopeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = config.scopeMode == mode,
                            onClick = { onSetScopeMode(mode) },
                            label = { Text(if (isArabic) mode.labelAr else mode.labelEn) },
                            modifier = Modifier.testTag("scope_mode_${mode.name}")
                        )
                    }
                }
                ScopeCanvas(
                    scopeMode = if (config.scopeMode == ScopeMode.NONE) ScopeMode.RGB_HISTOGRAM else config.scopeMode,
                    analysis = analysis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Avg IRE: ${"%.1f".format(analysis.averageIre)}%", style = MaterialTheme.typography.labelMedium, color = ActiveGreen)
                    Text("Highlight Clip: ${"%.2f".format(analysis.highlightClippedPercent)}%", style = MaterialTheme.typography.labelMedium, color = RecordingRed)
                    Text("Shadow Clip: ${"%.2f".format(analysis.shadowClippedPercent)}%", style = MaterialTheme.typography.labelMedium, color = SignalCyan)
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
                ToggleRow(
                    title = if (isArabic) "خطوط الحمار الوحشي (Zebra)" else "Zebra Stripes Overlay",
                    checked = config.zebraEnabled,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(zebraEnabled = v) } },
                    tag = "exposure_zebra_switch"
                )
                Text("Zebra Threshold: ${config.zebraThresholdIre}% IRE", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = config.zebraThresholdIre.toFloat(),
                    onValueChange = { v -> onUpdateConfig { it.copy(zebraThresholdIre = v.toInt()) } },
                    valueRange = 50f..100f
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Amber" to 0xFFFF9100.toInt(),
                        "Red" to 0xFFFF1744.toInt(),
                        "Green" to 0xFF00E676.toInt(),
                        "Cyan" to 0xFF38BDF8.toInt()
                    ).forEach { (name, argb) ->
                        FilterChip(
                            selected = config.zebraColorArgb == argb,
                            onClick = { onUpdateConfig { it.copy(zebraColorArgb = argb) } },
                            label = { Text(name) }
                        )
                    }
                }

                ToggleRow(
                    title = if (isArabic) "الألوان الكاذبة (False Color)" else "False Color IRE Mapping",
                    checked = config.falseColorEnabled,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(falseColorEnabled = v) } },
                    tag = "exposure_false_color_switch"
                )
                FalseColorLegendBar()

                ToggleRow(
                    title = if (isArabic) "تحذير قص الإضاءة العالية" else "Highlight Clipping Mask",
                    checked = config.highlightClippingEnabled,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(highlightClippingEnabled = v) } },
                    tag = "exposure_highlight_clip_switch"
                )
                ToggleRow(
                    title = if (isArabic) "تحذير قص الظلال" else "Shadow Clipping Mask",
                    checked = config.shadowClippingEnabled,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(shadowClippingEnabled = v) } },
                    tag = "exposure_shadow_clip_switch"
                )
                ToggleRow(
                    title = if (isArabic) "تنبيهات التعريض على الشاشة" else "Live Exposure Warnings",
                    checked = config.exposureWarningsEnabled,
                    onCheckedChange = { v -> onUpdateConfig { it.copy(exposureWarningsEnabled = v) } },
                    tag = "exposure_warnings_switch"
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text("Overlay Opacity: ${(config.overlayOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = config.overlayOpacity,
                    onValueChange = { v -> onUpdateConfig { it.copy(overlayOpacity = v) } },
                    valueRange = 0.2f..1.0f
                )
            }
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(tag))
    }
}
