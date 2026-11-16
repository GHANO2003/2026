package com.example.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import com.example.uvc.ParsedUvcDescriptors
import com.example.uvc.UvcDescriptorParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UsbDeviceRole {
    UVC_CAPTURE_CARD,
    NIKON_PTP_CAMERA,
    CANON_PTP_CAMERA,
    OTHER_USB_DEVICE
}

data class UsbDiscoveredDevice(
    val device: UsbDevice,
    val role: UsbDeviceRole,
    val displayName: String,
    val vendorId: Int,
    val productId: Int,
    val hasPermission: Boolean,
    val parsedUvc: ParsedUvcDescriptors? = null
)

class UsbCameraHostManager(private val context: Context) {
    companion object {
        const val ACTION_USB_PERMISSION = "com.aistudio.promonitor360.USB_PERMISSION"
        const val VENDOR_NIKON = 0x04B0
        const val VENDOR_CANON = 0x04A9
        const val USB_CLASS_VIDEO = 14
    }

    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _discoveredDevices = MutableStateFlow<List<UsbDiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<UsbDiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _lastEventMessage = MutableStateFlow<String?>(null)
    val lastEventMessage: StateFlow<String?> = _lastEventMessage.asStateFlow()

    private var onPermissionResultCallback: ((UsbDevice, Boolean) -> Unit)? = null
    private var onDeviceDetachedCallback: ((UsbDevice) -> Unit)? = null
    private var onDeviceAttachedCallback: ((UsbDevice) -> Unit)? = null
    private var isRegistered = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    refreshDevices()
                    if (device != null) {
                        _lastEventMessage.value = if (granted) {
                            "USB permission granted for ${device.productName ?: device.deviceName}"
                        } else {
                            "USB permission denied for ${device.productName ?: device.deviceName}"
                        }
                        onPermissionResultCallback?.invoke(device, granted)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    refreshDevices()
                    if (device != null) {
                        _lastEventMessage.value = "USB attached: ${device.productName ?: device.deviceName}"
                        onDeviceAttachedCallback?.invoke(device)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    refreshDevices()
                    if (device != null) {
                        _lastEventMessage.value = "USB detached: ${device.productName ?: device.deviceName}"
                        onDeviceDetachedCallback?.invoke(device)
                    }
                }
            }
        }
    }

    fun isUsbHostSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST) && usbManager != null
    }

    fun register(
        onAttached: (UsbDevice) -> Unit,
        onDetached: (UsbDevice) -> Unit,
        onPermissionResult: (UsbDevice, Boolean) -> Unit
    ) {
        onDeviceAttachedCallback = onAttached
        onDeviceDetachedCallback = onDetached
        onPermissionResultCallback = onPermissionResult

        if (!isRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(usbReceiver, filter)
            }
            isRegistered = true
        }
        refreshDevices()
    }

    fun unregister() {
        if (isRegistered) {
            runCatching { context.unregisterReceiver(usbReceiver) }
            isRegistered = false
        }
    }

    fun refreshDevices(): List<UsbDiscoveredDevice> {
        val manager = usbManager ?: run {
            _discoveredDevices.value = emptyList()
            return emptyList()
        }
        val list = manager.deviceList.values.map { dev ->
            val role = classifyDevice(dev)
            val hasPerm = manager.hasPermission(dev)
            val parsedUvc = if (role == UsbDeviceRole.UVC_CAPTURE_CARD && hasPerm) {
                openConnection(dev)?.let { conn ->
                    try {
                        UvcDescriptorParser.parse(conn.rawDescriptors)
                    } finally {
                        conn.close()
                    }
                }
            } else null

            val title = buildString {
                if (!dev.manufacturerName.isNullOrBlank()) {
                    append(dev.manufacturerName).append(" ")
                }
                append(dev.productName ?: "USB ${dev.vendorId.toHex()}:${dev.productId.toHex()}")
            }.trim()

            UsbDiscoveredDevice(
                device = dev,
                role = role,
                displayName = title,
                vendorId = dev.vendorId,
                productId = dev.productId,
                hasPermission = hasPerm,
                parsedUvc = parsedUvc
            )
        }
        _discoveredDevices.value = list
        return list
    }

    fun requestPermission(device: UsbDevice) {
        val manager = usbManager ?: return
        if (manager.hasPermission(device)) {
            onPermissionResultCallback?.invoke(device, true)
            return
        }
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, device.deviceId, intent, flags)
        manager.requestPermission(device, pendingIntent)
    }

    fun openConnection(device: UsbDevice): UsbDeviceConnection? {
        val manager = usbManager ?: return null
        if (!manager.hasPermission(device)) return null
        return manager.openDevice(device)
    }

    private fun classifyDevice(device: UsbDevice): UsbDeviceRole {
        var hasVideo = device.deviceClass == USB_CLASS_VIDEO
        var hasStillImage = device.deviceClass == UsbConstants.USB_CLASS_STILL_IMAGE
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == USB_CLASS_VIDEO) hasVideo = true
            if (intf.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) hasStillImage = true
        }
        return when {
            hasVideo -> UsbDeviceRole.UVC_CAPTURE_CARD
            device.vendorId == VENDOR_NIKON && hasStillImage -> UsbDeviceRole.NIKON_PTP_CAMERA
            device.vendorId == VENDOR_CANON && hasStillImage -> UsbDeviceRole.CANON_PTP_CAMERA
            device.vendorId == VENDOR_NIKON -> UsbDeviceRole.NIKON_PTP_CAMERA
            device.vendorId == VENDOR_CANON -> UsbDeviceRole.CANON_PTP_CAMERA
            else -> UsbDeviceRole.OTHER_USB_DEVICE
        }
    }

    private fun Int.toHex(): String = "0x" + toString(16).uppercase().padStart(4, '0')
}
