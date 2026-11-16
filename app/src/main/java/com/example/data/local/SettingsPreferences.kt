package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.localization.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "promonitor_settings")

data class UserSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val screenAlwaysOn: Boolean = true,
    val lockLandscapeInMonitor: Boolean = false,
    val thermalGuardEnabled: Boolean = true,
    val thermalWarningCelsius: Float = 42.0f,
    val defaultWifiIp: String = "192.168.1.1",
    val toolbarActionsCsv: String = "PEAKING,ZEBRA,FALSE_COLOR,WAVEFORM,GUIDES,LUT,RECORD"
)

class SettingsPreferences(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val SCREEN_ALWAYS_ON = booleanPreferencesKey("screen_always_on")
        val LOCK_LANDSCAPE = booleanPreferencesKey("lock_landscape_monitor")
        val THERMAL_GUARD = booleanPreferencesKey("thermal_guard")
        val THERMAL_THRESHOLD = floatPreferencesKey("thermal_threshold_c")
        val DEFAULT_WIFI_IP = stringPreferencesKey("default_wifi_ip")
        val TOOLBAR_ACTIONS = stringPreferencesKey("toolbar_actions_csv")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val langCode = prefs[Keys.LANGUAGE] ?: AppLanguage.ENGLISH.code
        UserSettings(
            language = if (langCode == AppLanguage.ARABIC.code) AppLanguage.ARABIC else AppLanguage.ENGLISH,
            screenAlwaysOn = prefs[Keys.SCREEN_ALWAYS_ON] ?: true,
            lockLandscapeInMonitor = prefs[Keys.LOCK_LANDSCAPE] ?: false,
            thermalGuardEnabled = prefs[Keys.THERMAL_GUARD] ?: true,
            thermalWarningCelsius = prefs[Keys.THERMAL_THRESHOLD] ?: 42.0f,
            defaultWifiIp = prefs[Keys.DEFAULT_WIFI_IP] ?: "192.168.1.1",
            toolbarActionsCsv = prefs[Keys.TOOLBAR_ACTIONS] ?: "PEAKING,ZEBRA,FALSE_COLOR,WAVEFORM,GUIDES,LUT,RECORD"
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setScreenAlwaysOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SCREEN_ALWAYS_ON] = enabled }
    }

    suspend fun setLockLandscape(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOCK_LANDSCAPE] = enabled }
    }

    suspend fun setThermalGuard(enabled: Boolean) {
        context.dataStore.edit { it[Keys.THERMAL_GUARD] = enabled }
    }

    suspend fun setThermalThreshold(celsius: Float) {
        context.dataStore.edit { it[Keys.THERMAL_THRESHOLD] = celsius }
    }

    suspend fun setDefaultWifiIp(ip: String) {
        context.dataStore.edit { it[Keys.DEFAULT_WIFI_IP] = ip }
    }

    suspend fun setToolbarActions(actions: List<String>) {
        context.dataStore.edit { it[Keys.TOOLBAR_ACTIONS] = actions.joinToString(",") }
    }
}
