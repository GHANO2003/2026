package com.example.nikon

import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import com.example.camera.CameraAdapter
import com.example.core.models.CameraControlSnapshot
import com.example.core.models.CameraModelSpec
import com.example.core.models.CameraSettingKey
import com.example.core.models.CommandExecutionResult
import com.example.core.models.ControlChannelState
import com.example.core.models.ControlChannelType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

open class BaseNikonPtpAdapter(
    override val spec: CameraModelSpec,
    private val scope: CoroutineScope
) : CameraAdapter {

    private val ptpIp = PtpIpClient()
    private val usbPtp = UsbPtpClient()

    private val _controlState = MutableStateFlow<ControlChannelState>(ControlChannelState.Disconnected)
    override val controlState: StateFlow<ControlChannelState> = _controlState.asStateFlow()

    private val _controlSnapshot = MutableStateFlow(
        CameraControlSnapshot(
            availableIso = listOf("100", "200", "400", "800", "1600", "3200", "6400", "12800", "25600"),
            availableShutter = listOf("1/4000", "1/2000", "1/1000", "1/500", "1/250", "1/125", "1/60", "1/50", "1/30", "1/15"),
            availableAperture = listOf("f/1.8", "f/2.8", "f/3.5", "f/4.0", "f/5.6", "f/8.0", "f/11", "f/16"),
            availableWb = listOf("Auto", "Daylight", "Cloudy", "Tungsten", "Fluorescent", "Flash"),
            availableExpComp = listOf("-2.0", "-1.0", "-0.7", "-0.3", "0.0", "+0.3", "+0.7", "+1.0", "+2.0")
        )
    )
    override val controlSnapshot: StateFlow<CameraControlSnapshot> = _controlSnapshot.asStateFlow()

    private var liveViewJob: Job? = null

    override suspend fun connectWifi(hostIp: String, port: Int): CommandExecutionResult {
        _controlState.value = ControlChannelState.Connecting("$hostIp:$port")
        val result = ptpIp.connect(hostIp, port)
        return result.fold(
            onSuccess = {
                _controlState.value = ControlChannelState.Connected(
                    channelType = ControlChannelType.NIKON_PTPIP,
                    cameraName = spec.modelName,
                    endpoint = "$hostIp:$port"
                )
                refreshSettings()
                CommandExecutionResult.Confirmed("Connected to ${spec.modelName} via PTP/IP ($hostIp:$port)")
            },
            onFailure = { err ->
                val msg = err.message ?: "Failed to connect to ${spec.modelName} PTP/IP"
                _controlState.value = ControlChannelState.Error(msg)
                CommandExecutionResult.Failed(msg)
            }
        )
    }

    override suspend fun connectUsbPtp(device: UsbDevice, connection: UsbDeviceConnection): CommandExecutionResult {
        _controlState.value = ControlChannelState.Connecting("USB OTG")
        return usbPtp.open(device, connection).fold(
            onSuccess = {
                _controlState.value = ControlChannelState.Connected(
                    channelType = ControlChannelType.USB_PTP,
                    cameraName = device.productName ?: spec.modelName,
                    endpoint = "USB ${device.deviceName}"
                )
                refreshSettings()
                CommandExecutionResult.Confirmed("USB PTP session opened on ${device.productName ?: spec.modelName}")
            },
            onFailure = { err ->
                val msg = err.message ?: "USB PTP connection failed"
                _controlState.value = ControlChannelState.Error(msg)
                CommandExecutionResult.Failed(msg)
            }
        )
    }

    override suspend fun disconnect() {
        stopWifiLiveView()
        ptpIp.disconnect()
        usbPtp.close()
        _controlState.value = ControlChannelState.Disconnected
    }

    override suspend fun refreshSettings(): CommandExecutionResult {
        if (!isChannelOpen()) {
            return CommandExecutionResult.Failed("Camera control channel is not connected")
        }
        return runCatching {
            val isoRaw = readPropUInt16(UsbPtpClient.PROP_EXPOSURE_INDEX_ISO)
            val wbRaw = readPropUInt16(UsbPtpClient.PROP_WHITE_BALANCE)
            val fRaw = readPropUInt16(UsbPtpClient.PROP_F_NUMBER)
            val shutterRaw = readPropUInt32(UsbPtpClient.PROP_EXPOSURE_TIME)
            val expRaw = readPropInt16(UsbPtpClient.PROP_EXPOSURE_BIAS_COMP)

            _controlSnapshot.value = _controlSnapshot.value.copy(
                iso = isoRaw?.toString() ?: _controlSnapshot.value.iso,
                whiteBalance = wbRaw?.let { decodeNikonWb(it) } ?: _controlSnapshot.value.whiteBalance,
                aperture = fRaw?.let { "f/${it / 100.0}" } ?: _controlSnapshot.value.aperture,
                shutterSpeed = shutterRaw?.let { decodeExposureTime(it) } ?: _controlSnapshot.value.shutterSpeed,
                exposureComp = expRaw?.let { "${if (it > 0) "+" else ""}${it / 1000.0}" } ?: _controlSnapshot.value.exposureComp,
                lastConfirmedAtMs = System.currentTimeMillis(),
                lastCommandError = null
            )
            CommandExecutionResult.Confirmed("Settings read from ${spec.modelName}")
        }.getOrElse { err ->
            val msg = err.message ?: "Failed reading camera properties"
            _controlSnapshot.value = _controlSnapshot.value.copy(lastCommandError = msg)
            CommandExecutionResult.Failed(msg)
        }
    }

    override suspend fun applySetting(key: CameraSettingKey, value: String): CommandExecutionResult {
        if (!isChannelOpen()) {
            return CommandExecutionResult.Failed("Not connected to ${spec.modelName}. Connect via USB PTP or Wi-Fi PTP/IP first.")
        }
        _controlSnapshot.value = _controlSnapshot.value.copy(isExecutingCommand = true, lastCommandError = null)
        val result = runCatching {
            val (propCode, encodedBytes) = encodeNikonSetting(key, value)
            val respCode = executeSetProp(propCode, encodedBytes)
            if (respCode != PtpIpClient.RESP_OK) {
                error("Camera rejected ${key.labelEn}=$value (PTP 0x${respCode.toString(16)})")
            }
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
        }.getOrElse { err ->
            val msg = err.message ?: "Command failed"
            _controlSnapshot.value = _controlSnapshot.value.copy(
                isExecutingCommand = false,
                lastCommandError = msg
            )
            CommandExecutionResult.Failed(msg)
        }
        return result
    }

    override suspend fun triggerAutofocus(): CommandExecutionResult {
        if (!isChannelOpen()) return CommandExecutionResult.Failed("Camera not connected")
        return CommandExecutionResult.Failed("${spec.modelName} executes AF on half-press or during Live View InitiateCapture")
    }

    override suspend fun capturePhoto(): CommandExecutionResult {
        if (!isChannelOpen()) return CommandExecutionResult.Failed("Camera not connected")
        _controlSnapshot.value = _controlSnapshot.value.copy(isExecutingCommand = true, lastCommandError = null)
        return runCatching {
            val code = if (ptpIp.isConnected) {
                ptpIp.executeCommand(PtpIpClient.OP_INITIATE_CAPTURE, intArrayOf(0, 0)).responseCode
            } else {
                usbPtp.sendCommand(UsbPtpClient.OP_INITIATE_CAPTURE, intArrayOf(0, 0)).first
            }
            _controlSnapshot.value = _controlSnapshot.value.copy(isExecutingCommand = false)
            if (code == PtpIpClient.RESP_OK) {
                _controlSnapshot.value = _controlSnapshot.value.copy(lastConfirmedAtMs = System.currentTimeMillis())
                CommandExecutionResult.Confirmed("Shutter release confirmed by ${spec.modelName}")
            } else {
                val msg = "Capture rejected (PTP 0x${code.toString(16)})"
                _controlSnapshot.value = _controlSnapshot.value.copy(lastCommandError = msg)
                CommandExecutionResult.Failed(msg)
            }
        }.getOrElse { err ->
            val msg = err.message ?: "Capture failed"
            _controlSnapshot.value = _controlSnapshot.value.copy(isExecutingCommand = false, lastCommandError = msg)
            CommandExecutionResult.Failed(msg)
        }
    }

    override suspend fun startCameraVideoRecording(): CommandExecutionResult {
        return CommandExecutionResult.Failed("${spec.modelName} does not expose remote video recording start over PTP/IP or SnapBridge. Use camera REC button and monitor via HDMI.")
    }

    override suspend fun stopCameraVideoRecording(): CommandExecutionResult {
        return CommandExecutionResult.Failed("${spec.modelName} does not expose remote video recording stop over PTP/IP or SnapBridge.")
    }

    override suspend fun startWifiLiveView(onFrame: (Bitmap, Long) -> Unit): CommandExecutionResult {
        if (!ptpIp.isConnected) {
            return CommandExecutionResult.Failed("Connect Nikon Wi-Fi PTP/IP first to stream Live View")
        }
        val startResp = ptpIp.executeCommand(PtpIpClient.OP_NIKON_START_LIVE_VIEW)
        if (startResp.responseCode != PtpIpClient.RESP_OK) {
            return CommandExecutionResult.Failed("Camera refused Live View start (PTP 0x${startResp.responseCode.toString(16)})")
        }
        liveViewJob?.cancel()
        liveViewJob = scope.launch(Dispatchers.IO) {
            while (isActive && ptpIp.isConnected) {
                val t0 = System.nanoTime()
                val frame = runCatching { ptpIp.fetchLiveViewFrame() }.getOrNull()
                if (frame != null) {
                    val latency = (System.nanoTime() - t0) / 1_000_000L
                    onFrame(frame, latency)
                }
                delay(40L)
            }
        }
        return CommandExecutionResult.Confirmed("Nikon Wi-Fi Live View started")
    }

    override suspend fun stopWifiLiveView() {
        liveViewJob?.cancel()
        liveViewJob = null
        if (ptpIp.isConnected) {
            runCatching { ptpIp.executeCommand(PtpIpClient.OP_NIKON_END_LIVE_VIEW) }
        }
    }

    private fun isChannelOpen(): Boolean = ptpIp.isConnected || usbPtp.isConnected

    private suspend fun readPropUInt16(propCode: Int): Int? {
        val bytes = readPropBytes(propCode) ?: return null
        if (bytes.size < 2) return null
        return ByteBuffer.wrap(bytes, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
    }

    private suspend fun readPropInt16(propCode: Int): Int? {
        val bytes = readPropBytes(propCode) ?: return null
        if (bytes.size < 2) return null
        return ByteBuffer.wrap(bytes, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
    }

    private suspend fun readPropUInt32(propCode: Int): Int? {
        val bytes = readPropBytes(propCode) ?: return null
        if (bytes.size < 4) return null
        return ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private suspend fun readPropBytes(propCode: Int): ByteArray? {
        return if (ptpIp.isConnected) {
            val r = ptpIp.executeCommand(PtpIpClient.OP_GET_DEVICE_PROP_VALUE, intArrayOf(propCode))
            if (r.responseCode == PtpIpClient.RESP_OK) r.data else null
        } else {
            val r = usbPtp.sendCommand(UsbPtpClient.OP_GET_DEVICE_PROP_VALUE, intArrayOf(propCode))
            if (r.first == UsbPtpClient.RESP_OK) r.second else null
        }
    }

    private suspend fun executeSetProp(propCode: Int, data: ByteArray): Int {
        return if (ptpIp.isConnected) {
            ptpIp.executeCommand(PtpIpClient.OP_SET_DEVICE_PROP_VALUE, intArrayOf(propCode), data).responseCode
        } else {
            usbPtp.sendCommand(UsbPtpClient.OP_SET_DEVICE_PROP_VALUE, intArrayOf(propCode), data).first
        }
    }

    private fun encodeNikonSetting(key: CameraSettingKey, value: String): Pair<Int, ByteArray> {
        return when (key) {
            CameraSettingKey.ISO -> {
                val v = value.toIntOrNull() ?: 100
                UsbPtpClient.PROP_EXPOSURE_INDEX_ISO to le16(v)
            }
            CameraSettingKey.APERTURE -> {
                val f = value.removePrefix("f/").toDoubleOrNull() ?: 5.6
                UsbPtpClient.PROP_F_NUMBER to le16((f * 100).toInt())
            }
            CameraSettingKey.SHUTTER_SPEED -> {
                val units100us = if (value.startsWith("1/")) {
                    val denom = value.removePrefix("1/").toDoubleOrNull() ?: 60.0
                    (10_000.0 / denom).toInt()
                } else {
                    ((value.toDoubleOrNull() ?: 1.0) * 10_000).toInt()
                }
                UsbPtpClient.PROP_EXPOSURE_TIME to le32(units100us)
            }
            CameraSettingKey.WHITE_BALANCE -> {
                val code = when (value) {
                    "Auto" -> 2
                    "Daylight" -> 4
                    "Fluorescent" -> 5
                    "Tungsten" -> 6
                    "Flash" -> 7
                    else -> 2
                }
                UsbPtpClient.PROP_WHITE_BALANCE to le16(code)
            }
            CameraSettingKey.EXPOSURE_COMP -> {
                val ev = ((value.toDoubleOrNull() ?: 0.0) * 1000).toInt()
                UsbPtpClient.PROP_EXPOSURE_BIAS_COMP to le16(ev)
            }
        }
    }

    private fun le16(v: Int): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()
    private fun le32(v: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun decodeNikonWb(code: Int): String = when (code) {
        2 -> "Auto"
        4 -> "Daylight"
        5 -> "Fluorescent"
        6 -> "Tungsten"
        7 -> "Flash"
        else -> "Preset ($code)"
    }

    private fun decodeExposureTime(units100us: Int): String {
        if (units100us <= 0) return "--"
        return if (units100us < 10_000) {
            "1/${(10_000 / units100us).coerceAtLeast(1)}"
        } else {
            "${units100us / 10_000.0}s"
        }
    }
}
