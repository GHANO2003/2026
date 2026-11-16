package com.example.canon

import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import com.example.camera.CameraAdapter
import com.example.camera.CameraProfileRegistry
import com.example.core.models.CameraControlSnapshot
import com.example.core.models.CameraSettingKey
import com.example.core.models.CommandExecutionResult
import com.example.core.models.ControlChannelState
import com.example.core.models.ControlChannelType
import com.example.nikon.UsbPtpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CanonEos90dAdapter(
    private val scope: CoroutineScope
) : CameraAdapter {

    override val spec = CameraProfileRegistry.canonEos90d

    private val ccapi = CanonCcapiClient()
    private val usbPtp = UsbPtpClient()

    private val _controlState = MutableStateFlow<ControlChannelState>(ControlChannelState.Disconnected)
    override val controlState: StateFlow<ControlChannelState> = _controlState.asStateFlow()

    private val _controlSnapshot = MutableStateFlow(
        CameraControlSnapshot(
            availableIso = listOf("auto", "100", "200", "400", "800", "1600", "3200", "6400", "12800"),
            availableShutter = listOf("1/4000", "1/2000", "1/1000", "1/500", "1/250", "1/125", "1/60", "1/50", "1/30"),
            availableAperture = listOf("1.8", "2.8", "4.0", "5.6", "8.0", "11", "16"),
            availableWb = listOf("auto", "daylight", "shade", "cloudy", "tungsten", "whitefluorescent"),
            availableExpComp = listOf("-2.0", "-1.0", "-0.3", "0.0", "+0.3", "+1.0", "+2.0")
        )
    )
    override val controlSnapshot: StateFlow<CameraControlSnapshot> = _controlSnapshot.asStateFlow()

    private var liveViewJob: Job? = null

    override suspend fun connectWifi(hostIp: String, port: Int): CommandExecutionResult {
        _controlState.value = ControlChannelState.Connecting("$hostIp:$port")
        return ccapi.connect(hostIp, port).fold(
            onSuccess = { detectedName ->
                _controlState.value = ControlChannelState.Connected(
                    channelType = ControlChannelType.CANON_CCAPI,
                    cameraName = detectedName,
                    endpoint = "$hostIp:$port"
                )
                refreshSettings()
                CommandExecutionResult.Confirmed("Connected to $detectedName via Canon CCAPI")
            },
            onFailure = { err ->
                val msg = err.message ?: "Failed connecting to Canon CCAPI at $hostIp:$port"
                _controlState.value = ControlChannelState.Error(msg)
                CommandExecutionResult.Failed(msg)
            }
        )
    }

    override suspend fun connectUsbPtp(device: UsbDevice, connection: UsbDeviceConnection): CommandExecutionResult {
        _controlState.value = ControlChannelState.Connecting("USB PTP")
        return usbPtp.open(device, connection).fold(
            onSuccess = {
                _controlState.value = ControlChannelState.Connected(
                    channelType = ControlChannelType.USB_PTP,
                    cameraName = device.productName ?: spec.modelName,
                    endpoint = "USB ${device.deviceName}"
                )
                CommandExecutionResult.Confirmed("USB PTP opened on ${device.productName ?: spec.modelName}")
            },
            onFailure = { err ->
                val msg = err.message ?: "USB PTP failed"
                _controlState.value = ControlChannelState.Error(msg)
                CommandExecutionResult.Failed(msg)
            }
        )
    }

    override suspend fun disconnect() {
        stopWifiLiveView()
        ccapi.disconnect()
        usbPtp.close()
        _controlState.value = ControlChannelState.Disconnected
    }

    override suspend fun refreshSettings(): CommandExecutionResult {
        if (!ccapi.isConnected && !usbPtp.isConnected) {
            return CommandExecutionResult.Failed("Canon EOS 90D control channel not connected")
        }
        if (ccapi.isConnected) {
            return ccapi.getShootingSettings().fold(
                onSuccess = { json ->
                    _controlSnapshot.value = _controlSnapshot.value.copy(
                        iso = json.optJSONObject("iso")?.optString("value", "--") ?: "--",
                        shutterSpeed = json.optJSONObject("tv")?.optString("value", "--") ?: "--",
                        aperture = json.optJSONObject("av")?.optString("value", "--") ?: "--",
                        whiteBalance = json.optJSONObject("wb")?.optString("value", "--") ?: "--",
                        exposureComp = json.optJSONObject("exposure")?.optString("value", "--") ?: "--",
                        lastConfirmedAtMs = System.currentTimeMillis(),
                        lastCommandError = null
                    )
                    CommandExecutionResult.Confirmed("Read live CCAPI settings from Canon EOS 90D")
                },
                onFailure = { err ->
                    val msg = err.message ?: "Failed reading CCAPI settings"
                    _controlSnapshot.value = _controlSnapshot.value.copy(lastCommandError = msg)
                    CommandExecutionResult.Failed(msg)
                }
            )
        }
        return CommandExecutionResult.Confirmed("USB PTP active")
    }

    override suspend fun applySetting(key: CameraSettingKey, value: String): CommandExecutionResult {
        if (!ccapi.isConnected) {
            return CommandExecutionResult.Failed("Connect Canon EOS 90D via CCAPI Wi-Fi to change ${key.labelEn}")
        }
        _controlSnapshot.value = _controlSnapshot.value.copy(isExecutingCommand = true, lastCommandError = null)
        val endpoint = when (key) {
            CameraSettingKey.ISO -> "iso"
            CameraSettingKey.SHUTTER_SPEED -> "tv"
            CameraSettingKey.APERTURE -> "av"
            CameraSettingKey.WHITE_BALANCE -> "wb"
            CameraSettingKey.EXPOSURE_COMP -> "exposure"
        }
        return ccapi.putSetting(endpoint, value).fold(
            onSuccess = {
                val updated = when (key) {
                    CameraSettingKey.ISO -> _controlSnapshot.value.copy(iso = value)
                    CameraSettingKey.SHUTTER_SPEED -> _controlSnapshot.value.copy(shutterSpeed = value)
                    CameraSettingKey.APERTURE -> _controlSnapshot.value.copy(aperture = value)
                    CameraSettingKey.WHITE_BALANCE -> _controlSnapshot.value.copy(whiteBalance = value)
                    CameraSettingKey.EXPOSURE_COMP -> _controlSnapshot.value.copy(exposureComp = value)
                }
                _controlSnapshot.value = updated.copy(
                    isExecutingCommand = false,
                    lastConfirmedAtMs = System.currentTimeMillis(),
                    lastCommandError = null
                )
                CommandExecutionResult.Confirmed("Confirmed ${key.labelEn} = $value")
            },
            onFailure = { err ->
                val msg = err.message ?: "Command rejected"
                _controlSnapshot.value = _controlSnapshot.value.copy(
                    isExecutingCommand = false,
                    lastCommandError = msg
                )
                CommandExecutionResult.Failed(msg)
            }
        )
    }

    override suspend fun triggerAutofocus(): CommandExecutionResult {
        return if (ccapi.isConnected) {
            ccapi.triggerShutter(af = true).fold(
                onSuccess = { CommandExecutionResult.Confirmed("AF + Shutter sequence confirmed") },
                onFailure = { CommandExecutionResult.Failed(it.message ?: "AF failed") }
            )
        } else {
            CommandExecutionResult.Failed("Canon CCAPI not connected")
        }
    }

    override suspend fun capturePhoto(): CommandExecutionResult {
        if (ccapi.isConnected) {
            return ccapi.triggerShutter(af = true).fold(
                onSuccess = {
                    _controlSnapshot.value = _controlSnapshot.value.copy(lastConfirmedAtMs = System.currentTimeMillis())
                    CommandExecutionResult.Confirmed("Photo capture confirmed by Canon EOS 90D")
                },
                onFailure = { CommandExecutionResult.Failed(it.message ?: "Capture failed") }
            )
        }
        if (usbPtp.isConnected) {
            val code = usbPtp.sendCommand(UsbPtpClient.OP_INITIATE_CAPTURE, intArrayOf(0, 0)).first
            return if (code == UsbPtpClient.RESP_OK) {
                CommandExecutionResult.Confirmed("USB PTP capture confirmed")
            } else {
                CommandExecutionResult.Failed("USB PTP capture rejected (0x${code.toString(16)})")
            }
        }
        return CommandExecutionResult.Failed("Canon EOS 90D is not connected")
    }

    override suspend fun startCameraVideoRecording(): CommandExecutionResult {
        if (!ccapi.isConnected) return CommandExecutionResult.Failed("Connect Canon EOS 90D CCAPI to control camera recording")
        return ccapi.setMovieRecording("start").fold(
            onSuccess = {
                _controlSnapshot.value = _controlSnapshot.value.copy(isRecordingVideoOnCamera = true)
                CommandExecutionResult.Confirmed("Camera internal movie recording STARTED")
            },
            onFailure = { CommandExecutionResult.Failed(it.message ?: "Movie start rejected") }
        )
    }

    override suspend fun stopCameraVideoRecording(): CommandExecutionResult {
        if (!ccapi.isConnected) return CommandExecutionResult.Failed("Connect Canon EOS 90D CCAPI to control camera recording")
        return ccapi.setMovieRecording("stop").fold(
            onSuccess = {
                _controlSnapshot.value = _controlSnapshot.value.copy(isRecordingVideoOnCamera = false)
                CommandExecutionResult.Confirmed("Camera internal movie recording STOPPED")
            },
            onFailure = { CommandExecutionResult.Failed(it.message ?: "Movie stop rejected") }
        )
    }

    override suspend fun startWifiLiveView(onFrame: (Bitmap, Long) -> Unit): CommandExecutionResult {
        if (!ccapi.isConnected) return CommandExecutionResult.Failed("Connect Canon CCAPI first")
        return ccapi.startLiveView().fold(
            onSuccess = {
                liveViewJob?.cancel()
                liveViewJob = scope.launch(Dispatchers.IO) {
                    while (isActive && ccapi.isConnected) {
                        val t0 = System.nanoTime()
                        val bmp = ccapi.fetchLiveViewFrame()
                        if (bmp != null) {
                            onFrame(bmp, (System.nanoTime() - t0) / 1_000_000L)
                        }
                        delay(45L)
                    }
                }
                CommandExecutionResult.Confirmed("Canon EOS 90D CCAPI Live View started")
            },
            onFailure = { CommandExecutionResult.Failed(it.message ?: "Failed starting Live View") }
        )
    }

    override suspend fun stopWifiLiveView() {
        liveViewJob?.cancel()
        liveViewJob = null
    }
}
