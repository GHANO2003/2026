package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Serializable
data object CameraSelectionRoute

@Serializable
data class ConnectionWizardRoute(val initialModelId: String? = null)

@Serializable
data object LiveMonitorRoute

@Serializable
data object ExposureToolsRoute

@Serializable
data object FocusToolsRoute

@Serializable
data object RemoteControlRoute

@Serializable
data object LutManagerRoute

@Serializable
data object GalleryRoute

@Serializable
data object CompatibilityRoute

@Serializable
data object SettingsRoute
