package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.camera.CameraProfileRegistry
import com.example.core.localization.AppLanguage
import com.example.core.models.CameraModelId
import com.example.core.models.CapabilitySupport
import com.example.ui.components.StatusPill
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalPanel
import com.example.ui.theme.CinemaBlack
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningOrange

@Composable
fun CameraSelectionScreen(
    activeModelId: CameraModelId,
    language: AppLanguage,
    onSelectModel: (CameraModelId) -> Unit,
    onLaunchWizardForModel: (CameraModelId) -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = language == AppLanguage.ARABIC
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack)
            .testTag("camera_selection_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = if (isArabic) "ملفات الكاميرات المدعومة" else "Dedicated Camera Profiles",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isArabic)
                        "معمارية محوّلات مستقلة لكل طراز (Nikon / Canon) تعرض فقط الأوامر المؤكدة"
                    else
                        "Modular per-model adapters exposing verified wireless, USB PTP, and HDMI capabilities.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        items(CameraProfileRegistry.allModels, key = { it.id.name }) { spec ->
            val isSelected = spec.id == activeModelId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CharcoalPanel)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) ActiveGreen else CharcoalBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelectModel(spec.id) }
                    .testTag("camera_card_${spec.id.name}")
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(spec.modelName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text(spec.sensorSummary, style = MaterialTheme.typography.bodyMedium, color = SignalCyan)
                    }
                    StatusPill(
                        label = if (isSelected) (if (isArabic) "نشط" else "ACTIVE") else spec.brand.displayName.uppercase(),
                        isActive = isSelected
                    )
                }

                Text("HDMI: ${spec.hdmiOutputSpec}", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                Text("Wireless: ${spec.wirelessProtocolName}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)

                Text(
                    text = if (isArabic) spec.wirelessLimitationNoteAr else spec.wirelessLimitationNoteEn,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WarningOrange
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalElevated)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    spec.capabilityTable.forEach { row ->
                        val (icon, tint) = when (row.support) {
                            CapabilitySupport.CONFIRMED -> Icons.Default.CheckCircle to ActiveGreen
                            CapabilitySupport.LIMITED -> Icons.Default.WarningAmber to WarningOrange
                            CapabilitySupport.UNSUPPORTED -> Icons.Default.ErrorOutline to RecordingRed
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    if (isArabic) row.featureAr else row.featureEn,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    if (isArabic) row.noteAr else row.noteEn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onSelectModel(spec.id)
                        onLaunchWizardForModel(spec.id)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("wizard_btn_${spec.id.name}"),
                    colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen, contentColor = CinemaBlack)
                ) {
                    Text(
                        if (isArabic) "إعداد وتوصيل ${spec.modelName}" else "Configure & Connect ${spec.modelName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
