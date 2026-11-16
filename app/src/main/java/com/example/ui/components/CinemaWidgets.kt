package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.models.AspectRatioGuide
import com.example.core.models.CompositionConfig
import com.example.core.models.FrameAnalysisResult
import com.example.core.models.ScopeMode
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.ActiveGreenDim
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalPanel
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.RecordingRedDim
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange
import com.example.ui.theme.WarningOrangeDim

@Composable
fun StatusPill(
    label: String,
    isActive: Boolean,
    isWarning: Boolean = false,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when {
        isError -> RecordingRedDim to RecordingRed
        isWarning -> WarningOrangeDim to WarningOrange
        isActive -> ActiveGreenDim to ActiveGreen
        else -> CharcoalElevated to TextSecondary
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(fg)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
fun PreviewOnlyBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = WarningOrangeDim,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = WarningOrange,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = WarningOrange
            )
        }
    }
}

@Composable
fun ScopeCanvas(
    scopeMode: ScopeMode,
    analysis: FrameAnalysisResult,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE6080A0D))
            .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        if (!analysis.isFromHardwareFrame) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Awaiting live frame for ${scopeMode.labelEn}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }
            return
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // IRE grid lines (25%, 50%, 75%)
            listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = Offset(0f, h * frac),
                    end = Offset(w, h * frac),
                    strokeWidth = 1f
                )
            }

            when (scopeMode) {
                ScopeMode.LUMA_HISTOGRAM -> {
                    drawHistogramCurve(analysis.lumaHistogram, Color.White.copy(alpha = 0.85f), w, h)
                }
                ScopeMode.RGB_HISTOGRAM -> {
                    drawHistogramCurve(analysis.rHistogram, RecordingRed.copy(alpha = 0.75f), w, h)
                    drawHistogramCurve(analysis.gHistogram, ActiveGreen.copy(alpha = 0.75f), w, h)
                    drawHistogramCurve(analysis.bHistogram, SignalCyan.copy(alpha = 0.75f), w, h)
                }
                ScopeMode.WAVEFORM -> {
                    drawWaveformMatrix(analysis.waveformColumns, ActiveGreen, 0f, w, h)
                }
                ScopeMode.RGB_PARADE -> {
                    val segW = w / 3f
                    drawWaveformMatrix(analysis.paradeR, RecordingRed, 0f, segW, h)
                    drawWaveformMatrix(analysis.paradeG, ActiveGreen, segW, segW, h)
                    drawWaveformMatrix(analysis.paradeB, SignalCyan, segW * 2f, segW, h)
                }
                ScopeMode.NONE -> Unit
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHistogramCurve(
    bins: IntArray,
    color: Color,
    w: Float,
    h: Float
) {
    if (bins.isEmpty()) return
    val maxVal = bins.maxOrNull()?.coerceAtLeast(1) ?: 1
    val path = Path().apply {
        moveTo(0f, h)
        for (i in bins.indices) {
            val x = (i / 255f) * w
            val y = h - (bins[i].toFloat() / maxVal) * h * 0.92f
            lineTo(x, y)
        }
        lineTo(w, h)
        close()
    }
    drawPath(path, color = color.copy(alpha = 0.25f))
    drawPath(path, color = color, style = Stroke(width = 2f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveformMatrix(
    cols: Array<IntArray>,
    color: Color,
    startX: Float,
    width: Float,
    height: Float
) {
    if (cols.isEmpty()) return
    val colCount = cols.size
    val rowCount = cols[0].size
    val cellW = width / colCount
    val cellH = height / rowCount
    for (c in 0 until colCount) {
        val column = cols[c]
        for (r in 0 until rowCount) {
            val count = column[r]
            if (count > 0) {
                val alpha = (count / 12f).coerceIn(0.18f, 0.95f)
                drawRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(startX + c * cellW, r * cellH),
                    size = Size(cellW + 0.5f, cellH + 0.5f)
                )
            }
        }
    }
}

@Composable
fun CompositionGuidesOverlay(
    config: CompositionConfig,
    centerCrosshairFromFocus: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val lineColor = Color.White.copy(alpha = 0.55f)

        if (config.ruleOfThirds) {
            drawLine(lineColor, Offset(w / 3f, 0f), Offset(w / 3f, h), 1.5f)
            drawLine(lineColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), 1.5f)
            drawLine(lineColor, Offset(0f, h / 3f), Offset(w, h / 3f), 1.5f)
            drawLine(lineColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), 1.5f)
        }

        if (config.goldenRatio) {
            val phi1 = 0.382f
            val phi2 = 0.618f
            val gold = WarningOrange.copy(alpha = 0.65f)
            drawLine(gold, Offset(w * phi1, 0f), Offset(w * phi1, h), 1.5f)
            drawLine(gold, Offset(w * phi2, 0f), Offset(w * phi2, h), 1.5f)
            drawLine(gold, Offset(0f, h * phi1), Offset(w, h * phi1), 1.5f)
            drawLine(gold, Offset(0f, h * phi2), Offset(w, h * phi2), 1.5f)
        }

        if (config.safeAreas) {
            val actionPadX = w * 0.05f
            val actionPadY = h * 0.05f
            drawRect(
                color = SignalCyan.copy(alpha = 0.6f),
                topLeft = Offset(actionPadX, actionPadY),
                size = Size(w - 2 * actionPadX, h - 2 * actionPadY),
                style = Stroke(width = 1.5f)
            )
            val titlePadX = w * 0.10f
            val titlePadY = h * 0.10f
            drawRect(
                color = ActiveGreen.copy(alpha = 0.6f),
                topLeft = Offset(titlePadX, titlePadY),
                size = Size(w - 2 * titlePadX, h - 2 * titlePadY),
                style = Stroke(width = 1.5f)
            )
        }

        if (config.customFramingMarginPercent > 0) {
            val m = config.customFramingMarginPercent / 100f
            drawRect(
                color = WarningOrange.copy(alpha = 0.75f),
                topLeft = Offset(w * m, h * m),
                size = Size(w * (1f - 2f * m), h * (1f - 2f * m)),
                style = Stroke(width = 2f)
            )
        }

        val targetRatio = config.aspectRatioGuide.ratio
        if (targetRatio != null && config.aspectRatioGuide != AspectRatioGuide.NONE) {
            val currentRatio = w / h.coerceAtLeast(1f)
            if (currentRatio < targetRatio) {
                val guideH = w / targetRatio
                val bar = (h - guideH) / 2f
                drawRect(Color.Black.copy(alpha = 0.55f), Offset(0f, 0f), Size(w, bar))
                drawRect(Color.Black.copy(alpha = 0.55f), Offset(0f, h - bar), Size(w, bar))
                drawRect(Color.White.copy(alpha = 0.8f), Offset(0f, bar), Size(w, guideH), style = Stroke(2f))
            } else {
                val guideW = h * targetRatio
                val bar = (w - guideW) / 2f
                drawRect(Color.Black.copy(alpha = 0.55f), Offset(0f, 0f), Size(bar, h))
                drawRect(Color.Black.copy(alpha = 0.55f), Offset(w - bar, 0f), Size(bar, h))
                drawRect(Color.White.copy(alpha = 0.8f), Offset(bar, 0f), Size(guideW, h), style = Stroke(2f))
            }
        }

        if (config.centerCross || centerCrosshairFromFocus) {
            val cx = w / 2f
            val cy = h / 2f
            val arm = 18f
            drawLine(ActiveGreen, Offset(cx - arm, cy), Offset(cx + arm, cy), 2.5f)
            drawLine(ActiveGreen, Offset(cx, cy - arm), Offset(cx, cy + arm), 2.5f)
        }
    }
}

@Composable
fun FalseColorLegendBar(modifier: Modifier = Modifier) {
    val stops = listOf(
        "0%" to Color(0xFF6A1B9A),
        "15%" to Color(0xFF1E88E5),
        "35%" to Color(0xFF00ACC1),
        "44%" to Color(0xFF00E676),
        "58%" to Color(0xFFF48FB1),
        "75%" to Color(0xFFFDD835),
        "88%" to Color(0xFFFB8C00),
        "98%" to Color(0xFFFF1744)
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CharcoalPanel)
            .padding(8.dp)
    ) {
        Text("False Color IRE Scale", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            stops.forEach { (ire, color) ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(ire, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
