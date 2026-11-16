package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.localization.AppLanguage
import com.example.core.localization.ArabicStrings
import com.example.core.localization.EnglishStrings
import com.example.core.localization.LocalAppStrings
import com.example.core.models.CameraModelId
import com.example.core.models.ScopeMode
import com.example.ui.navigation.CameraSelectionRoute
import com.example.ui.navigation.CompatibilityRoute
import com.example.ui.navigation.ConnectionWizardRoute
import com.example.ui.navigation.DashboardRoute
import com.example.ui.navigation.ExposureToolsRoute
import com.example.ui.navigation.FocusToolsRoute
import com.example.ui.navigation.GalleryRoute
import com.example.ui.navigation.LiveMonitorRoute
import com.example.ui.navigation.LutManagerRoute
import com.example.ui.navigation.RemoteControlRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.screens.CameraSelectionScreen
import com.example.ui.screens.CompatibilityScreen
import com.example.ui.screens.ConnectionWizardScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExposureToolsScreen
import com.example.ui.screens.FocusToolsScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.LiveMonitorScreen
import com.example.ui.screens.LutManagerScreen
import com.example.ui.screens.RemoteControlScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.CharcoalBase
import com.example.ui.theme.CinemaBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ProMonitorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProMonitorApp(viewModel: ProMonitorViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hybridState by viewModel.hybridState.collectAsStateWithLifecycle()
    val controlSnapshot by viewModel.controlSnapshot.collectAsStateWithLifecycle()
    val focusConfig by viewModel.focusConfig.collectAsStateWithLifecycle()
    val exposureConfig by viewModel.exposureConfig.collectAsStateWithLifecycle()
    val compositionConfig by viewModel.compositionConfig.collectAsStateWithLifecycle()
    val transformConfig by viewModel.transformConfig.collectAsStateWithLifecycle()
    val lutConfig by viewModel.lutConfig.collectAsStateWithLifecycle()
    val displayFrame by viewModel.displayFrame.collectAsStateWithLifecycle()
    val lutStillPreview by viewModel.lutStillPreview.collectAsStateWithLifecycle()
    val analysisResult by viewModel.analysisResult.collectAsStateWithLifecycle()
    val recordingStatus by viewModel.recordingStatus.collectAsStateWithLifecycle()
    val savedProfiles by viewModel.savedProfiles.collectAsStateWithLifecycle()
    val luts by viewModel.luts.collectAsStateWithLifecycle()
    val recordedMedia by viewModel.recordedMedia.collectAsStateWithLifecycle()
    val diagnosticReport by viewModel.diagnosticReport.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.scanAndConnectHdmi()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.toggleMp4Recording(withAudio = granted)
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val isMonitorScreen = currentDest?.hasRoute<LiveMonitorRoute>() == true
    DisposableEffect(isMonitorScreen, settings.screenAlwaysOn, settings.lockLandscapeInMonitor, transformConfig.orientationLocked) {
        if (activity != null) {
            if (isMonitorScreen && settings.screenAlwaysOn) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            if (isMonitorScreen && (settings.lockLandscapeInMonitor || transformConfig.orientationLocked)) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose { }
    }

    val strings = if (settings.language == AppLanguage.ARABIC) ArabicStrings else EnglishStrings
    val layoutDir = if (settings.language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    val isTopLevelTab = currentDest?.hasRoute<DashboardRoute>() == true ||
        currentDest?.hasRoute<LiveMonitorRoute>() == true ||
        currentDest?.hasRoute<RemoteControlRoute>() == true ||
        currentDest?.hasRoute<ExposureToolsRoute>() == true ||
        currentDest?.hasRoute<GalleryRoute>() == true

    val scanHdmiWithPermission: () -> Unit = {
        val hasCameraPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasCameraPerm) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            viewModel.scanAndConnectHdmi()
        }
    }

    CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalLayoutDirection provides layoutDir
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(CinemaBlack)) {
            val useRail = maxWidth >= 720.dp

            val navItems = listOf(
                Triple(DashboardRoute, strings.navDashboard, Icons.Default.Dashboard),
                Triple(LiveMonitorRoute, strings.navMonitor, Icons.Default.Monitor),
                Triple(RemoteControlRoute, strings.navControl, Icons.Default.SettingsRemote),
                Triple(ExposureToolsRoute, strings.navTools, Icons.Default.Exposure),
                Triple(GalleryRoute, strings.navGallery, Icons.Default.VideoLibrary)
            )

            Scaffold(
                containerColor = CinemaBlack,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (!isMonitorScreen && !isTopLevelTab) {
                        TopAppBar(
                            title = { Text(strings.appName, style = MaterialTheme.typography.titleLarge) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = CharcoalBase,
                                titleContentColor = TextPrimary
                            ),
                            navigationIcon = {
                                IconButton(
                                    onClick = { navController.popBackStack() },
                                    modifier = Modifier.testTag("topbar_back_btn")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    if (!useRail && !(isMonitorScreen && transformConfig.controlsHidden)) {
                        NavigationBar(containerColor = CharcoalBase) {
                            navItems.forEach { (route, label, icon) ->
                                val selected = currentDest?.hasRoute(route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(route) {
                                            popUpTo(DashboardRoute) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ActiveGreen,
                                        selectedTextColor = ActiveGreen,
                                        indicatorColor = ActiveGreen.copy(alpha = 0.16f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("nav_${label.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (useRail && !(isMonitorScreen && transformConfig.controlsHidden)) {
                        NavigationRail(containerColor = CharcoalBase) {
                            navItems.forEach { (route, label, icon) ->
                                val selected = currentDest?.hasRoute(route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(route) {
                                            popUpTo(DashboardRoute) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = DashboardRoute,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    ) {
                        composable<DashboardRoute> {
                            DashboardScreen(
                                hybridState = hybridState,
                                savedProfiles = savedProfiles,
                                onScanUsbHdmi = scanHdmiWithPermission,
                                onOpenMonitor = { navController.navigate(LiveMonitorRoute) },
                                onOpenWizard = { navController.navigate(ConnectionWizardRoute(hybridState.activeModel.name)) },
                                onOpenCameraSelection = { navController.navigate(CameraSelectionRoute) },
                                onOpenCompatibility = {
                                    viewModel.runCompatibilityDiagnostics(hybridState.activeModel)
                                    navController.navigate(CompatibilityRoute)
                                },
                                onOpenLutManager = { navController.navigate(LutManagerRoute) },
                                onOpenSettings = { navController.navigate(SettingsRoute) },
                                onLoadProfile = { viewModel.loadSavedProfile(it) },
                                onDeleteProfile = { viewModel.deleteSavedProfile(it) }
                            )
                        }

                        composable<CameraSelectionRoute> {
                            CameraSelectionScreen(
                                activeModelId = hybridState.activeModel,
                                language = settings.language,
                                onSelectModel = { viewModel.selectModel(it) },
                                onLaunchWizardForModel = { model ->
                                    navController.navigate(ConnectionWizardRoute(model.name))
                                }
                            )
                        }

                        composable<ConnectionWizardRoute> { entry ->
                            val args = entry.toRoute<ConnectionWizardRoute>()
                            val initial = args.initialModelId?.let {
                                runCatching { CameraModelId.valueOf(it) }.getOrNull()
                            } ?: hybridState.activeModel
                            ConnectionWizardScreen(
                                initialModelId = initial,
                                hybridState = hybridState,
                                defaultWifiIp = settings.defaultWifiIp,
                                language = settings.language,
                                onSelectModel = { viewModel.selectModel(it) },
                                onSelectMode = { viewModel.selectConnectionMode(it) },
                                onScanHdmi = scanHdmiWithPermission,
                                onScanUsbControl = { viewModel.scanAndConnectUsbControl() },
                                onConnectWifi = { ip, port, lv -> viewModel.connectWifi(ip, port, lv) },
                                onSaveProfile = { name, model, mode, ip, port ->
                                    viewModel.saveCameraProfile(name, model, mode, ip, port)
                                },
                                onOpenMonitor = { navController.navigate(LiveMonitorRoute) }
                            )
                        }

                        composable<LiveMonitorRoute> {
                            val toolbarList = settings.toolbarActionsCsv.split(",").filter { it.isNotBlank() }
                            LiveMonitorScreen(
                                hybridState = hybridState,
                                displayFrame = displayFrame,
                                analysisResult = analysisResult,
                                focusConfig = focusConfig,
                                exposureConfig = exposureConfig,
                                compositionConfig = compositionConfig,
                                transformConfig = transformConfig,
                                lutConfig = lutConfig,
                                recordingStatus = recordingStatus,
                                toolbarActions = toolbarList,
                                onScanUsbHdmi = scanHdmiWithPermission,
                                onOpenWizard = { navController.navigate(ConnectionWizardRoute(hybridState.activeModel.name)) },
                                onTogglePeaking = { viewModel.updateFocusConfig { it.copy(peakingEnabled = !it.peakingEnabled) } },
                                onToggleZebra = { viewModel.updateExposureConfig { it.copy(zebraEnabled = !it.zebraEnabled) } },
                                onToggleFalseColor = { viewModel.updateExposureConfig { it.copy(falseColorEnabled = !it.falseColorEnabled) } },
                                onCycleScope = {
                                    val modes = ScopeMode.entries
                                    val next = modes[(modes.indexOf(exposureConfig.scopeMode) + 1) % modes.size]
                                    viewModel.setScopeMode(next)
                                },
                                onToggleGuides = { viewModel.updateCompositionConfig { it.copy(ruleOfThirds = !it.ruleOfThirds) } },
                                onToggleLut = { viewModel.updateLutConfig { it.copy(enabled = !it.enabled) } },
                                onCaptureScreenshot = { viewModel.captureScreenshot() },
                                onToggleRecord = {
                                    if (recordingStatus.isRecording) {
                                        viewModel.toggleMp4Recording(false)
                                    } else {
                                        val hasAudio = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasAudio) {
                                            viewModel.toggleMp4Recording(withAudio = true)
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                onTogglePauseRecord = { viewModel.toggleRecordingPause() },
                                onCycleScaleMode = { viewModel.cycleScaleMode() },
                                onRotate90 = { viewModel.rotate90() },
                                onToggleMirror = { viewModel.updateTransformConfig { it.copy(mirrorHorizontal = !it.mirrorHorizontal) } },
                                onToggleFlip = { viewModel.updateTransformConfig { it.copy(flipVertical = !it.flipVertical) } },
                                onToggleHideControls = { viewModel.updateTransformConfig { it.copy(controlsHidden = !it.controlsHidden) } },
                                onToggleOrientationLock = { viewModel.updateTransformConfig { it.copy(orientationLocked = !it.orientationLocked) } },
                                onSetAspectGuide = { viewModel.setAspectRatioGuide(it) },
                                onSetAnamorphic = { viewModel.setAnamorphicPreset(it) },
                                onNavigateFocus = { navController.navigate(FocusToolsRoute) },
                                onNavigateExposure = { navController.navigate(ExposureToolsRoute) },
                                onNavigateRemote = { navController.navigate(RemoteControlRoute) }
                            )
                        }

                        composable<ExposureToolsRoute> {
                            ExposureToolsScreen(
                                config = exposureConfig,
                                analysis = analysisResult,
                                language = settings.language,
                                onUpdateConfig = { viewModel.updateExposureConfig(it) },
                                onSetScopeMode = { viewModel.setScopeMode(it) },
                                onReset = { viewModel.resetExposureTools() }
                            )
                        }

                        composable<FocusToolsRoute> {
                            FocusToolsScreen(
                                config = focusConfig,
                                analysis = analysisResult,
                                language = settings.language,
                                onUpdateConfig = { viewModel.updateFocusConfig(it) },
                                onSetPeakingColor = { viewModel.setPeakingColor(it) },
                                onSetZoomFactor = { viewModel.setZoomFactor(it) },
                                onResetZoomCenter = { viewModel.resetZoomCenter() }
                            )
                        }

                        composable<RemoteControlRoute> {
                            RemoteControlScreen(
                                hybridState = hybridState,
                                snapshot = controlSnapshot,
                                language = settings.language,
                                onRefreshSettings = { viewModel.refreshCameraSettings() },
                                onApplySetting = { key, value -> viewModel.applyCameraSetting(key, value) },
                                onTriggerAf = { viewModel.triggerAutofocus() },
                                onCapturePhoto = { viewModel.capturePhoto() },
                                onToggleCameraVideo = { viewModel.toggleCameraVideoRec(it) },
                                onOpenWizard = { navController.navigate(ConnectionWizardRoute(hybridState.activeModel.name)) }
                            )
                        }

                        composable<LutManagerRoute> {
                            LutManagerScreen(
                                lutConfig = lutConfig,
                                luts = luts,
                                previewBitmap = lutStillPreview,
                                isLiveHardwareFrame = displayFrame != null,
                                language = settings.language,
                                onSelectLut = { viewModel.selectLut(it) },
                                onImportCubeUri = { viewModel.importCubeLut(it) },
                                onDeleteCustomLut = { viewModel.deleteCustomLut(it) },
                                onUpdateConfig = { viewModel.updateLutConfig(it) }
                            )
                        }

                        composable<GalleryRoute> {
                            GalleryScreen(
                                mediaList = recordedMedia,
                                recordingStatus = recordingStatus,
                                language = settings.language,
                                onShareMedia = { media ->
                                    viewModel.buildShareIntent(media)?.let { intent ->
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share Capture"))
                                    }
                                },
                                onDeleteMedia = { viewModel.deleteMedia(it) }
                            )
                        }

                        composable<CompatibilityRoute> {
                            CompatibilityScreen(
                                report = diagnosticReport,
                                language = settings.language,
                                onRunDiagnostics = { viewModel.runCompatibilityDiagnostics(it) }
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                settings = settings,
                                onSetLanguage = { viewModel.setLanguage(it) },
                                onSetScreenAlwaysOn = { viewModel.setScreenAlwaysOn(it) },
                                onSetLockLandscape = { viewModel.setLockLandscape(it) },
                                onSetThermalGuard = { viewModel.setThermalGuard(it) },
                                onSetDefaultWifiIp = { viewModel.setDefaultWifiIp(it) },
                                onToggleToolbarAction = { viewModel.toggleToolbarAction(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}
