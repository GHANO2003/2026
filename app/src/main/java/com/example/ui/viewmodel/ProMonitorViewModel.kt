package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.analysis.CubeLutData
import com.example.analysis.CubeLutEngine
import com.example.analysis.ImageAnalysisEngine
import com.example.camera.CameraProfileRegistry
import com.example.connection.UnifiedConnectionManager
import com.example.core.localization.AppLanguage
import com.example.core.models.AnamorphicPreset
import com.example.core.models.AspectRatioGuide
import com.example.core.models.CameraControlSnapshot
import com.example.core.models.CameraModelId
import com.example.core.models.CameraSettingKey
import com.example.core.models.CommandExecutionResult
import com.example.core.models.CompatibilityReport
import com.example.core.models.CompositionConfig
import com.example.core.models.ConnectionMode
import com.example.core.models.ExposureToolsConfig
import com.example.core.models.FocusToolsConfig
import com.example.core.models.FocusZoomFactor
import com.example.core.models.FrameAnalysisResult
import com.example.core.models.HybridConnectionState
import com.example.core.models.LutConfig
import com.example.core.models.MonitorTransformConfig
import com.example.core.models.PeakingColor
import com.example.core.models.RecordingStatus
import com.example.core.models.ScopeMode
import com.example.core.models.VideoScaleMode
import com.example.data.local.LutPresetEntity
import com.example.data.local.ProMonitorDatabase
import com.example.data.local.RecordedMediaEntity
import com.example.data.local.SavedCameraProfileEntity
import com.example.data.local.SettingsPreferences
import com.example.data.local.UserSettings
import com.example.data.repository.ProMonitorRepository
import com.example.diagnostics.CompatibilityDiagnosticRunner
import com.example.recording.MediaCaptureManager
import com.example.usb.UsbDiscoveredDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProMonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ProMonitorDatabase.getInstance(application)
    private val repository = ProMonitorRepository(
        database.cameraProfileDao(),
        database.lutPresetDao(),
        database.recordedMediaDao()
    )
    private val settingsPrefs = SettingsPreferences(application)
    private val connectionManager = UnifiedConnectionManager(application, viewModelScope)
    private val analysisEngine = ImageAnalysisEngine()
    private val lutEngine = CubeLutEngine()
    private val mediaCapture = MediaCaptureManager(application, viewModelScope)
    private val diagnosticRunner = CompatibilityDiagnosticRunner(application, connectionManager, mediaCapture)

    val settings: StateFlow<UserSettings> = settingsPrefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val hybridState: StateFlow<HybridConnectionState> = connectionManager.hybridState
    val controlSnapshot: StateFlow<CameraControlSnapshot> = connectionManager.controlSnapshot
    val usbDevices: StateFlow<List<UsbDiscoveredDevice>> = connectionManager.usbDevices
    val recordingStatus: StateFlow<RecordingStatus> = mediaCapture.recordingStatus

    val savedProfiles: StateFlow<List<SavedCameraProfileEntity>> = repository.savedProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val luts: StateFlow<List<LutPresetEntity>> = repository.luts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recordedMedia: StateFlow<List<RecordedMediaEntity>> = repository.recordedMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _focusConfig = MutableStateFlow(FocusToolsConfig())
    val focusConfig: StateFlow<FocusToolsConfig> = _focusConfig.asStateFlow()

    private val _exposureConfig = MutableStateFlow(ExposureToolsConfig())
    val exposureConfig: StateFlow<ExposureToolsConfig> = _exposureConfig.asStateFlow()

    private val _compositionConfig = MutableStateFlow(CompositionConfig())
    val compositionConfig: StateFlow<CompositionConfig> = _compositionConfig.asStateFlow()

    private val _transformConfig = MutableStateFlow(MonitorTransformConfig())
    val transformConfig: StateFlow<MonitorTransformConfig> = _transformConfig.asStateFlow()

    private val _lutConfig = MutableStateFlow(LutConfig())
    val lutConfig: StateFlow<LutConfig> = _lutConfig.asStateFlow()

    private val _displayFrame = MutableStateFlow<Bitmap?>(null)
    val displayFrame: StateFlow<Bitmap?> = _displayFrame.asStateFlow()

    private val _lutStillPreview = MutableStateFlow<Bitmap?>(null)
    val lutStillPreview: StateFlow<Bitmap?> = _lutStillPreview.asStateFlow()

    private val _analysisResult = MutableStateFlow(FrameAnalysisResult())
    val analysisResult: StateFlow<FrameAnalysisResult> = _analysisResult.asStateFlow()

    private val _diagnosticReport = MutableStateFlow(
        CompatibilityReport(targetModel = CameraModelId.NIKON_D5600)
    )
    val diagnosticReport: StateFlow<CompatibilityReport> = _diagnosticReport.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val customCubeCache = mutableMapOf<String, CubeLutData>()
    private var referenceStillBitmap: Bitmap? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureBuiltInLuts()
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            referenceStillBitmap = BitmapFactory.decodeResource(application.resources, R.drawable.img_hero_cinema_rig, opts)
            refreshLutStillPreview()
        }

        viewModelScope.launch {
            connectionManager.latestFrame.collectLatest { rawFrame ->
                if (rawFrame == null) {
                    _displayFrame.value = null
                    return@collectLatest
                }
                val lutCfg = _lutConfig.value
                val activeLut = resolveActiveLut(lutCfg.activeLutId)
                val graded = lutEngine.applyPreviewTransform(rawFrame, lutCfg, activeLut)
                _displayFrame.value = graded

                if (mediaCapture.recordingStatus.value.isRecording) {
                    mediaCapture.feedVideoFrame(graded)
                }

                val analysis = analysisEngine.analyzeFrame(
                    frame = graded,
                    focusConfig = _focusConfig.value,
                    exposureConfig = _exposureConfig.value,
                    isFromHardware = true
                )
                _analysisResult.value = analysis
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun selectModel(modelId: CameraModelId) {
        connectionManager.selectModel(modelId)
    }

    fun selectConnectionMode(mode: ConnectionMode) {
        connectionManager.selectMode(mode)
    }

    fun scanAndConnectHdmi() {
        _statusBanner.value = connectionManager.scanAndConnectHdmi()
    }

    fun scanAndConnectUsbControl() {
        _statusBanner.value = connectionManager.scanAndConnectUsbControl()
    }

    fun stopVideoFeed() {
        connectionManager.stopVideoSignal()
    }

    fun connectWifi(hostIp: String, port: Int, startWifiLiveView: Boolean) {
        viewModelScope.launch {
            val res = connectionManager.connectWifiControl(hostIp, port, startWifiLiveView)
            _statusBanner.value = when (res) {
                is CommandExecutionResult.Confirmed -> res.message
                is CommandExecutionResult.Failed -> res.reason
            }
        }
    }

    fun disconnectControl() {
        viewModelScope.launch {
            connectionManager.disconnectControl()
            _statusBanner.value = "Camera control disconnected"
        }
    }

    fun saveCameraProfile(profileName: String, modelId: CameraModelId, mode: ConnectionMode, wifiIp: String, wifiPort: Int) {
        val spec = CameraProfileRegistry.getSpec(modelId)
        viewModelScope.launch {
            repository.saveProfile(
                SavedCameraProfileEntity(
                    profileName = profileName.ifBlank { spec.modelName },
                    modelId = modelId.name,
                    brand = spec.brand.displayName,
                    preferredMode = mode.name,
                    wifiIpAddress = wifiIp,
                    wifiPort = wifiPort,
                    notes = spec.wirelessProtocolName,
                    lastConnectedMs = System.currentTimeMillis()
                )
            )
            _statusBanner.value = "Saved profile: ${profileName.ifBlank { spec.modelName }}"
        }
    }

    fun loadSavedProfile(profile: SavedCameraProfileEntity) {
        val model = runCatching { CameraModelId.valueOf(profile.modelId) }.getOrDefault(CameraModelId.NIKON_D5600)
        val mode = runCatching { ConnectionMode.valueOf(profile.preferredMode) }.getOrDefault(ConnectionMode.HDMI_CAPTURE)
        selectModel(model)
        selectConnectionMode(mode)
        _statusBanner.value = "Loaded ${profile.profileName} (${mode.labelEn})"
    }

    fun deleteSavedProfile(id: Long) {
        viewModelScope.launch { repository.deleteProfile(id) }
    }

    // Remote Camera Control
    fun refreshCameraSettings() {
        viewModelScope.launch {
            val res = connectionManager.refreshCameraSettings()
            if (res is CommandExecutionResult.Failed) _statusBanner.value = res.reason
        }
    }

    fun applyCameraSetting(key: CameraSettingKey, value: String) {
        viewModelScope.launch {
            val res = connectionManager.applyCameraSetting(key, value)
            _statusBanner.value = when (res) {
                is CommandExecutionResult.Confirmed -> res.message
                is CommandExecutionResult.Failed -> res.reason
            }
        }
    }

    fun triggerAutofocus() {
        viewModelScope.launch {
            val res = connectionManager.triggerAf()
            _statusBanner.value = when (res) {
                is CommandExecutionResult.Confirmed -> res.message
                is CommandExecutionResult.Failed -> res.reason
            }
        }
    }

    fun capturePhoto() {
        viewModelScope.launch {
            val res = connectionManager.capturePhoto()
            _statusBanner.value = when (res) {
                is CommandExecutionResult.Confirmed -> res.message
                is CommandExecutionResult.Failed -> res.reason
            }
        }
    }

    fun toggleCameraVideoRec(currentlyRecording: Boolean) {
        viewModelScope.launch {
            val res = if (currentlyRecording) connectionManager.stopCameraVideo() else connectionManager.startCameraVideo()
            _statusBanner.value = when (res) {
                is CommandExecutionResult.Confirmed -> res.message
                is CommandExecutionResult.Failed -> res.reason
            }
        }
    }

    // Focus Tools
    fun updateFocusConfig(transform: (FocusToolsConfig) -> FocusToolsConfig) {
        _focusConfig.value = transform(_focusConfig.value)
        reanalyzeCurrentFrame()
    }

    fun setPeakingColor(color: PeakingColor) = updateFocusConfig { it.copy(peakingColor = color) }
    fun setZoomFactor(factor: FocusZoomFactor) = updateFocusConfig { it.copy(zoomFactor = factor) }
    fun resetZoomCenter() = updateFocusConfig { it.copy(zoomCenterX = 0.5f, zoomCenterY = 0.5f) }

    // Exposure Tools
    fun updateExposureConfig(transform: (ExposureToolsConfig) -> ExposureToolsConfig) {
        _exposureConfig.value = transform(_exposureConfig.value)
        reanalyzeCurrentFrame()
    }

    fun setScopeMode(mode: ScopeMode) = updateExposureConfig { it.copy(scopeMode = mode) }
    fun resetExposureTools() = updateExposureConfig { ExposureToolsConfig() }

    // Composition Tools
    fun updateCompositionConfig(transform: (CompositionConfig) -> CompositionConfig) {
        _compositionConfig.value = transform(_compositionConfig.value)
    }

    fun setAspectRatioGuide(guide: AspectRatioGuide) = updateCompositionConfig { it.copy(aspectRatioGuide = guide) }
    fun setAnamorphicPreset(preset: AnamorphicPreset) = updateCompositionConfig { it.copy(anamorphicPreset = preset) }

    // Monitor Transform
    fun updateTransformConfig(transform: (MonitorTransformConfig) -> MonitorTransformConfig) {
        _transformConfig.value = transform(_transformConfig.value)
    }

    fun cycleScaleMode() {
        val modes = VideoScaleMode.entries
        val next = modes[(modes.indexOf(_transformConfig.value.scaleMode) + 1) % modes.size]
        updateTransformConfig { it.copy(scaleMode = next) }
    }

    fun rotate90() {
        updateTransformConfig { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    }

    // LUT Manager
    fun updateLutConfig(transform: (LutConfig) -> LutConfig) {
        _lutConfig.value = transform(_lutConfig.value)
        refreshLutStillPreview()
    }

    fun selectLut(preset: LutPresetEntity) {
        updateLutConfig {
            it.copy(
                enabled = true,
                activeLutId = preset.id,
                activeLutName = preset.name
            )
        }
    }

    fun importCubeLut(uri: Uri) {
        viewModelScope.launch {
            val id = "custom_${System.currentTimeMillis()}"
            lutEngine.loadCubeFromUri(getApplication(), uri, id).fold(
                onSuccess = { parsed ->
                    customCubeCache[id] = parsed
                    lutEngine.registerExternalLut(parsed)
                    repository.saveCustomLut(
                        LutPresetEntity(
                            id = id,
                            name = parsed.title,
                            sourceUri = uri.toString(),
                            cubeSize = parsed.size,
                            isBuiltIn = false,
                            createdAtMs = System.currentTimeMillis()
                        )
                    )
                    updateLutConfig {
                        it.copy(enabled = true, activeLutId = id, activeLutName = parsed.title)
                    }
                    _statusBanner.value = "Imported ${parsed.title} (${parsed.size}x${parsed.size}x${parsed.size})"
                },
                onFailure = { err ->
                    _statusBanner.value = "LUT import failed: ${err.message}"
                }
            )
        }
    }

    fun deleteCustomLut(id: String) {
        viewModelScope.launch {
            repository.deleteCustomLut(id)
            if (_lutConfig.value.activeLutId == id) {
                updateLutConfig { it.copy(enabled = false, activeLutId = null, activeLutName = "Rec.709 Neutral") }
            }
        }
    }

    private fun resolveActiveLut(id: String?): CubeLutData? {
        if (id == null) return null
        return if (id.startsWith("builtin_")) {
            lutEngine.getOrGenerateBuiltInLut(id)
        } else {
            customCubeCache[id]
        }
    }

    private fun refreshLutStillPreview() {
        viewModelScope.launch {
            val src = _displayFrame.value ?: referenceStillBitmap ?: return@launch
            val cfg = _lutConfig.value
            val lut = resolveActiveLut(cfg.activeLutId)
            _lutStillPreview.value = lutEngine.applyPreviewTransform(src, cfg, lut)
        }
    }

    private fun reanalyzeCurrentFrame() {
        val frame = _displayFrame.value ?: return
        viewModelScope.launch {
            _analysisResult.value = analysisEngine.analyzeFrame(
                frame = frame,
                focusConfig = _focusConfig.value,
                exposureConfig = _exposureConfig.value,
                isFromHardware = true
            )
        }
    }

    // Recording & Screenshots
    fun captureScreenshot() {
        val frame = _displayFrame.value
        if (frame == null) {
            _statusBanner.value = "Cannot capture frame: no active video signal"
            return
        }
        viewModelScope.launch {
            val modelName = CameraProfileRegistry.getSpec(hybridState.value.activeModel).modelName
            mediaCapture.saveScreenshot(frame, modelName).fold(
                onSuccess = { entry ->
                    repository.recordMediaEntry(entry)
                    _statusBanner.value = "Saved frame grab: ${entry.title}"
                },
                onFailure = { err ->
                    _statusBanner.value = "Screenshot failed: ${err.message}"
                }
            )
        }
    }

    fun toggleMp4Recording(withAudio: Boolean) {
        val rec = recordingStatus.value
        if (rec.isRecording) {
            viewModelScope.launch {
                val modelName = CameraProfileRegistry.getSpec(hybridState.value.activeModel).modelName
                val saved = mediaCapture.stopMp4Recording(modelName)
                if (saved != null) {
                    repository.recordMediaEntry(saved)
                    _statusBanner.value = "Saved MP4: ${saved.title} (${saved.durationSeconds}s)"
                } else {
                    _statusBanner.value = "Recording stopped (no video frames received)"
                }
            }
        } else {
            val frame = _displayFrame.value
            if (frame == null) {
                _statusBanner.value = "Connect HDMI UVC video feed before recording MP4"
                return
            }
            val started = mediaCapture.startMp4Recording(frame.width, frame.height, withAudio)
            if (!started) {
                _statusBanner.value = mediaCapture.recordingStatus.value.lastError ?: "Failed to start MP4 recording"
            }
        }
    }

    fun toggleRecordingPause() {
        mediaCapture.togglePause()
    }

    fun buildShareIntent(media: RecordedMediaEntity): Intent? = mediaCapture.buildShareIntent(media)

    fun deleteMedia(media: RecordedMediaEntity) {
        viewModelScope.launch { repository.deleteMedia(media) }
    }

    // Diagnostics
    fun runCompatibilityDiagnostics(modelId: CameraModelId = hybridState.value.activeModel) {
        viewModelScope.launch {
            _diagnosticReport.value = _diagnosticReport.value.copy(targetModel = modelId, isRunning = true)
            _diagnosticReport.value = diagnosticRunner.runFullSuite(modelId)
        }
    }

    // Settings
    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsPrefs.setLanguage(language) }
    }

    fun setScreenAlwaysOn(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setScreenAlwaysOn(enabled) }
    }

    fun setLockLandscape(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setLockLandscape(enabled) }
    }

    fun setThermalGuard(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setThermalGuard(enabled) }
    }

    fun setDefaultWifiIp(ip: String) {
        viewModelScope.launch { settingsPrefs.setDefaultWifiIp(ip) }
    }

    fun toggleToolbarAction(actionId: String) {
        viewModelScope.launch {
            val current = settings.value.toolbarActionsCsv.split(",").filter { it.isNotBlank() }.toMutableList()
            if (current.contains(actionId)) {
                if (current.size > 2) current.remove(actionId)
            } else {
                current.add(actionId)
            }
            settingsPrefs.setToolbarActions(current)
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectionManager.release()
    }
}
