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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.localization.AppLanguage
import com.example.core.localization.LocalAppStrings
import com.example.data.local.UserSettings
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalPanel
import com.example.ui.theme.CinemaBlack
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetScreenAlwaysOn: (Boolean) -> Unit,
    onSetLockLandscape: (Boolean) -> Unit,
    onSetThermalGuard: (Boolean) -> Unit,
    onSetDefaultWifiIp: (String) -> Unit,
    onToggleToolbarAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val isArabic = settings.language == AppLanguage.ARABIC
    var ipInput by remember(settings.defaultWifiIp) { mutableStateOf(settings.defaultWifiIp) }
    val activeToolbar = settings.toolbarActionsCsv.split(",").filter { it.isNotBlank() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = strings.settings,
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
                Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium, color = SignalCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { onSetLanguage(lang) },
                            label = { Text(lang.displayName) },
                            modifier = Modifier.testTag("settings_lang_${lang.code}")
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
                ToggleRow(
                    title = strings.screenAlwaysOn,
                    checked = settings.screenAlwaysOn,
                    onCheckedChange = onSetScreenAlwaysOn,
                    tag = "settings_keep_awake"
                )
                ToggleRow(
                    title = strings.orientationLock,
                    checked = settings.lockLandscapeInMonitor,
                    onCheckedChange = onSetLockLandscape,
                    tag = "settings_landscape_lock"
                )
                ToggleRow(
                    title = strings.thermalMonitor,
                    checked = settings.thermalGuardEnabled,
                    onCheckedChange = onSetThermalGuard,
                    tag = "settings_thermal_guard"
                )
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
                Text(
                    text = if (isArabic) "تخصيص شريط أدوات المراقب" else "Customizable Monitor Toolbar",
                    style = MaterialTheme.typography.titleMedium,
                    color = SignalCyan
                )
                Text(
                    text = if (isArabic) "اختر الأزرار السريعة الظاهرة أسفل الشاشة" else "Toggle quick buttons displayed in the Live Monitor bar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("PEAKING", "ZEBRA", "FALSE_COLOR", "WAVEFORM").forEach { id ->
                        FilterChip(
                            selected = activeToolbar.contains(id),
                            onClick = { onToggleToolbarAction(id) },
                            label = { Text(id.replace("_", " ")) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("GUIDES", "LUT", "RECORD").forEach { id ->
                        FilterChip(
                            selected = activeToolbar.contains(id),
                            onClick = { onToggleToolbarAction(id) },
                            label = { Text(id) }
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
                Text(if (isArabic) "عنوان IP الافتراضي للكاميرا" else "Default Camera Wi-Fi IP", style = MaterialTheme.typography.titleMedium, color = SignalCyan)
                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Button(onClick = { onSetDefaultWifiIp(ipInput.trim()) }) {
                    Text(if (isArabic) "حفظ الإعداد" else "Save IP")
                }
            }
        }
    }
}
