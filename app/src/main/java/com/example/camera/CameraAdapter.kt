package com.example.camera

import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import com.example.core.models.CameraControlSnapshot
import com.example.core.models.CameraModelId
import com.example.core.models.CameraModelSpec
import com.example.core.models.CameraSettingKey
import com.example.core.models.CommandExecutionResult
import com.example.core.models.ControlChannelState
import kotlinx.coroutines.flow.StateFlow

interface CameraAdapter {
    val spec: CameraModelSpec
    val controlState: StateFlow<ControlChannelState>
    val controlSnapshot: StateFlow<CameraControlSnapshot>

    suspend fun connectWifi(hostIp: String, port: Int): CommandExecutionResult
    suspend fun connectUsbPtp(device: UsbDevice, connection: UsbDeviceConnection): CommandExecutionResult
    suspend fun disconnect()

    suspend fun refreshSettings(): CommandExecutionResult
    suspend fun applySetting(key: CameraSettingKey, value: String): CommandExecutionResult
    suspend fun triggerAutofocus(): CommandExecutionResult
    suspend fun capturePhoto(): CommandExecutionResult
    suspend fun startCameraVideoRecording(): CommandExecutionResult
    suspend fun stopCameraVideoRecording(): CommandExecutionResult

    suspend fun startWifiLiveView(onFrame: (Bitmap, Long) -> Unit): CommandExecutionResult
    suspend fun stopWifiLiveView()

    companion object {
        fun isModelMatch(modelId: CameraModelId, detectedName: String): Boolean {
            return when (modelId) {
                CameraModelId.NIKON_D5600 -> detectedName.contains("D5600", ignoreCase = true)
                CameraModelId.NIKON_D5500 -> detectedName.contains("D5500", ignoreCase = true)
                CameraModelId.CANON_EOS_90D -> detectedName.contains("90D", ignoreCase = true)
            }
        }
    }
}
