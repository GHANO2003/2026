package com.example.connection

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.os.BatteryManager
import com.example.camera.CameraAdapter
import com.example.canon.CanonEos90dAdapter
import com.example.core.models.CameraControlSnapshot
import com.example.core.models.CameraModelId
import com.example.core.models.CameraSettingKey
import com.example.core.models.CommandExecutionResult
import com.example.core.models.ConnectionMode
import com.example.core.models.ControlChannelState
import com.example.core.models.HybridConnectionState
import com.example.core.models.VideoSignalState
import com.example.core.models.VideoSourceType
import com.example.nikon.NikonD5500Adapter
import com.example.nikon.NikonD5600Adapter
import com.example.usb.UsbCameraHostManager
import com.example.usb.UsbDeviceRole
import com.example.usb.UsbDiscoveredDevice
import com.example.uvc.UvcCaptureEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class UnifiedConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    val usbManager = UsbCameraHostManager(context)
    val uvcEngine = UvcCaptureEngine(context, scope)

    private val adapters: Map<CameraModelId, CameraAdapter> = mapOf(
        CameraModelId.NIKON_D5600 to NikonD5600Adapter(scope),
        CameraModelId.NIKON_D5500 to NikonD5500Adapter(scope),
        CameraModelId.CANON_EOS_90D to CanonEos90dAdapter(scope)
    )

    private val _hybridState = MutableStateFlow(HybridConnectionState())
    val hybridState: StateFlow<HybridConnectionState> = _hybridState.asStateFlow()

    private val _controlSnapshot = MutableStateFlow(CameraControlSnapshot())
    val controlSnapshot: StateFlow<CameraControlSnapshot> = _controlSnapshot.asStateFlow()

    val latestFrame: StateFlow<Bitmap?> = uvcEngine.latestFrame
    val usbDevices: StateFlow<List<UsbDiscoveredDevice>> = usbManager.discoveredDevices

    private var activeAdapter: CameraAdapter = adapters.getValue(CameraModelId.NIKON_D5600)
    private var pendingUsbActionRole: UsbDeviceRole? = null

    init {
        usbManager.register(
            onAttached = { device ->
                scope.launch {
                    delay(350L)
                    autoHandleAttachedUsb(device)
                }
            },
            onDetached = { device ->
                val currentVideo = _hybridState.value.videoSignal
                if (currentVideo is VideoSignalState.Active &&
                    currentVideo.sourceType == VideoSourceType.UVC_USB_BULK
                ) {
                    uvcEngine.stopCapture()
                    uvcEngine.setSignalDisconnected("USB capture device detached (${device.productName ?: device.deviceName})")
                }
            },
            onPermissionResult = { device, granted ->
                if (granted) {
                    _hybridState.value = _hybridState.value.copy(usbPermissionPending = false)
                    scope.launch { openPermittedUsbDevice(device) }
                } else {
                    _hybridState.value = _hybridState.value.copy(usbPermissionPending = false)
                    uvcEngine.setSignalDisconnected("USB permission was denied by user")
                }
            }
        )

        scope.launch {
            uvcEngine.signalState.collectLatest { sig ->
                _hybridState.value = _hybridState.value.copy(videoSignal = sig)
            }
        }

        scope.launch {
            usbManager.discoveredDevices.collectLatest { list ->
                val summary = if (list.isEmpty()) null else list.joinToString(" • ") { it.displayName }
                _hybridState.value = _hybridState.value.copy(detectedUsbSummary = summary)
            }
        }

        // Observe active adapter states
        scope.launch {
            while (isActive) {
                _hybridState.value = _hybridState.value.copy(
                    controlChannel = activeAdapter.controlState.value
                )
                _controlSnapshot.value = activeAdapter.controlSnapshot.value
                delay(200L)
            }
        }

        // Thermal & battery monitor loop
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                val batteryIntent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
                _hybridState.value = _hybridState.value.copy(
                    thermalCelsius = tempTenths / 10f,
                    batteryPercent = (level * 100) / scale
                )
                delay(4000L)
            }
        }
    }

    fun selectModel(modelId: CameraModelId) {
        if (_hybridState.value.activeModel == modelId) return
        scope.launch {
            activeAdapter.disconnect()
            activeAdapter = adapters.getValue(modelId)
            _hybridState.value = _hybridState.value.copy(
                activeModel = modelId,
                controlChannel = ControlChannelState.Disconnected
            )
        }
    }

    fun selectMode(mode: ConnectionMode) {
        _hybridState.value = _hybridState.value.copy(selectedMode = mode)
    }

    fun scanAndConnectHdmi(): String {
        val extCameras = uvcEngine.findExternalCamera2Devices()
        if (extCameras.isNotEmpty()) {
            val started = uvcEngine.startCamera2ExternalCapture(extCameras.first())
            if (started) {
                return "Started External UVC Camera2 stream (${extCameras.first()})"
            } else {
                _hybridState.value = _hybridState.value.copy(cameraPermissionRequired = true)
                return "Camera permission required to open External UVC device"
            }
        }

        val devices = usbManager.refreshDevices()
        val uvcDev = devices.firstOrNull { it.role == UsbDeviceRole.UVC_CAPTURE_CARD }
        if (uvcDev != null) {
            pendingUsbActionRole = UsbDeviceRole.UVC_CAPTURE_CARD
            if (uvcDev.hasPermission) {
                scope.launch { openPermittedUsbDevice(uvcDev.device) }
                return "Connecting to ${uvcDev.displayName}..."
            } else {
                _hybridState.value = _hybridState.value.copy(usbPermissionPending = true)
                usbManager.requestPermission(uvcDev.device)
                return "Requesting USB permission for ${uvcDev.displayName}"
            }
        }

        uvcEngine.setSignalDisconnected("No UVC HDMI capture card detected on USB-C OTG")
        return "No UVC capture hardware detected"
    }

    fun scanAndConnectUsbControl(): String {
        val devices = usbManager.refreshDevices()
        val camDev = devices.firstOrNull {
            it.role == UsbDeviceRole.NIKON_PTP_CAMERA || it.role == UsbDeviceRole.CANON_PTP_CAMERA
        } ?: return "No Nikon or Canon PTP USB camera detected on USB-C OTG"

        pendingUsbActionRole = camDev.role
        return if (camDev.hasPermission) {
            scope.launch { openPermittedUsbDevice(camDev.device) }
            "Opening USB PTP on ${camDev.displayName}"
        } else {
            _hybridState.value = _hybridState.value.copy(usbPermissionPending = true)
            usbManager.requestPermission(camDev.device)
            "Requesting USB permission for ${camDev.displayName}"
        }
    }

    private suspend fun autoHandleAttachedUsb(device: UsbDevice) {
        val list = usbManager.refreshDevices()
        val item = list.firstOrNull { it.device.deviceId == device.deviceId } ?: return
        if (item.role == UsbDeviceRole.UVC_CAPTURE_CARD ||
            item.role == UsbDeviceRole.NIKON_PTP_CAMERA ||
            item.role == UsbDeviceRole.CANON_PTP_CAMERA
        ) {
            if (item.hasPermission) {
                openPermittedUsbDevice(device)
            } else {
                usbManager.requestPermission(device)
            }
        }
    }

    private suspend fun openPermittedUsbDevice(device: UsbDevice) {
        val list = usbManager.refreshDevices()
        val item = list.firstOrNull { it.device.deviceId == device.deviceId } ?: return
        val conn = usbManager.openConnection(device) ?: return

        when (item.role) {
            UsbDeviceRole.UVC_CAPTURE_CARD -> {
                val extCameras = uvcEngine.findExternalCamera2Devices()
                if (extCameras.isNotEmpty() && uvcEngine.startCamera2ExternalCapture(extCameras.first())) {
                    conn.close()
                } else {
                    uvcEngine.startUsbHostBulkCapture(item, conn)
                }
            }
            UsbDeviceRole.NIKON_PTP_CAMERA, UsbDeviceRole.CANON_PTP_CAMERA -> {
                activeAdapter.connectUsbPtp(device, conn)
            }
            UsbDeviceRole.OTHER_USB_DEVICE -> conn.close()
        }
    }

    suspend fun connectWifiControl(hostIp: String, port: Int, alsoStartWifiLiveView: Boolean): CommandExecutionResult {
        val res = activeAdapter.connectWifi(hostIp, port)
        if (res is CommandExecutionResult.Confirmed && alsoStartWifiLiveView &&
            _hybridState.value.selectedMode == ConnectionMode.WIFI_WIRELESS
        ) {
            activeAdapter.startWifiLiveView { bmp, latency ->
                uvcEngine.publishExternalBitmapFrame(
                    bitmap = bmp,
                    sourceType = VideoSourceType.WIFI_LIVE_VIEW,
                    deviceName = activeAdapter.spec.modelName,
                    pixelFormat = "JPEG",
                    latencyMs = latency
                )
            }
        }
        return res
    }

    suspend fun disconnectControl() {
        activeAdapter.disconnect()
    }

    fun stopVideoSignal() {
        uvcEngine.stopCapture()
        uvcEngine.setSignalDisconnected("Video monitoring stopped")
    }

    suspend fun refreshCameraSettings() = activeAdapter.refreshSettings()
    suspend fun applyCameraSetting(key: CameraSettingKey, value: String) = activeAdapter.applySetting(key, value)
    suspend fun triggerAf() = activeAdapter.triggerAutofocus()
    suspend fun capturePhoto() = activeAdapter.capturePhoto()
    suspend fun startCameraVideo() = activeAdapter.startCameraVideoRecording()
    suspend fun stopCameraVideo() = activeAdapter.stopCameraVideoRecording()

    fun release() {
        uvcEngine.stopCapture()
        usbManager.unregister()
    }
}
