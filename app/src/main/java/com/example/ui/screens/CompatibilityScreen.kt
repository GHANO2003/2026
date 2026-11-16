package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.localization.AppLanguage
import com.example.core.models.CameraModelId
import com.example.core.models.CompatibilityReport
import com.example.core.models.DiagnosticStatus
import com.example.ui.components.StatusPill
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalPanel
import com.example.ui.theme.CinemaBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CompatibilityScreen(
    report: CompatibilityReport,
    language: AppLanguage,
    onRunDiagnostics: (CameraModelId) -> Unit,
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isArabic) "مركز اختبار التوافق والتشخيص" else "Compatibility & Diagnostic Test Center",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = if (isArabic)
                        "فحص فعلي للعتاد والواجهة — لا يتم وسم أي ميزة كمدعومة دون اختبار ناجح أو قدرة موثقة."
                    else
                        "Honest hardware & protocol probes. Features are never marked SUPPORTED without an actual test or verified capability.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CameraModelId.entries.forEach { m ->
                    FilterChip(
                        selected = report.targetModel == m,
                        onClick = { onRunDiagnostics(m) },
                        label = { Text(m.modelName.removePrefix("Nikon ").removePrefix("Canon ")) },
                        modifier = Modifier.testTag("diag_model_${m.name}")
                    )
                }
            }
        }

        item {
            Button(
                onClick = { onRunDiagnostics(report.targetModel) },
                enabled = !report.isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("diag_run_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen, contentColor = CinemaBlack)
            ) {
                Text(
                    text = if (report.isRunning) "Running Diagnostics..."
                    else if (isArabic) "تشغيل الاختبار الفعلي الآن"
                    else "Run Real Diagnostics for ${report.targetModel.modelName}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        items(report.checks, key = { it.id }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalPanel)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        if (isArabic) item.titleAr else item.titleEn,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (isArabic) item.detailAr else item.detailEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                StatusPill(
                    label = if (isArabic) item.status.labelAr else item.status.labelEn,
                    isActive = item.status == DiagnosticStatus.SUPPORTED,
                    isWarning = item.status == DiagnosticStatus.PARTIALLY_SUPPORTED,
                    isError = item.status == DiagnosticStatus.NOT_SUPPORTED
                )
            }
        }
    }
}
