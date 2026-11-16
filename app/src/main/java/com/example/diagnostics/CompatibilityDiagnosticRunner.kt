package com.example.diagnostics

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.camera.CameraProfileRegistry
import com.example.connection.UnifiedConnectionManager
import com.example.core.models.CameraModelId
import com.example.core.models.CompatibilityReport
import com.example.core.models.ControlChannelState
import com.example.core.models.DiagnosticCheckItem
import com.example.core.models.DiagnosticStatus
import com.example.core.models.VideoSignalState
import com.example.recording.MediaCaptureManager
import com.example.usb.UsbDeviceRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CompatibilityDiagnosticRunner(
    private val context: Context,
    private val connectionManager: UnifiedConnectionManager,
    private val mediaCaptureManager: MediaCaptureManager
) {
    suspend fun runFullSuite(targetModel: CameraModelId): CompatibilityReport = withContext(Dispatchers.IO) {
        val spec = CameraProfileRegistry.getSpec(targetModel)
        val checks = mutableListOf<DiagnosticCheckItem>()

        // 1. USB Host API support
        val usbHostOk = connectionManager.usbManager.isUsbHostSupported()
        checks += DiagnosticCheckItem(
            id = "usb_host",
            titleEn = "Android USB Host Support",
            titleAr = "دعم مضيف USB في أندرويد",
            status = if (usbHostOk) DiagnosticStatus.SUPPORTED else DiagnosticStatus.NOT_SUPPORTED,
            detailEn = if (usbHostOk) "android.hardware.usb.host feature present" else "Device does not advertise USB OTG Host",
            detailAr = if (usbHostOk) "ميزة USB Host مدعومة على الجهاز" else "الجهاز لا يدعم USB OTG Host"
        )

        // 2. UVC Capture Card Detection & Camera2 External
        val devices = connectionManager.usbManager.refreshDevices()
        val uvcUsb = devices.firstOrNull { it.role == UsbDeviceRole.UVC_CAPTURE_CARD }
        val extCamIds = connectionManager.uvcEngine.findExternalCamera2Devices()
        val uvcStatus = when {
            extCamIds.isNotEmpty() || uvcUsb != null -> DiagnosticStatus.SUPPORTED
            usbHostOk -> DiagnosticStatus.NOT_TESTED
            else -> DiagnosticStatus.NOT_SUPPORTED
        }
        checks += DiagnosticCheckItem(
            id = "uvc_detect",
            titleEn = "UVC HDMI Capture Detection",
            titleAr = "كشف بطاقة التقاط HDMI UVC",
            status = uvcStatus,
            detailEn = when {
                extCamIds.isNotEmpty() -> "Camera2 External UVC detected (ID: ${extCamIds.first()})"
                uvcUsb != null -> "USB UVC device detected: ${uvcUsb.displayName}"
                else -> "No UVC capture card currently attached on USB-C OTG"
            },
            detailAr = when {
                extCamIds.isNotEmpty() -> "تم كشف جهاز UVC عبر Camera2"
                uvcUsb != null -> "تم كشف بطاقة UVC: ${uvcUsb.displayName}"
                else -> "لم يتم توصيل بطاقة التقاط UVC حالياً"
            }
        )

        // 3. USB Permission
        val usbPermStatus = when {
            uvcUsb == null && devices.isEmpty() -> DiagnosticStatus.NOT_TESTED
            devices.all { it.hasPermission } -> DiagnosticStatus.SUPPORTED
            else -> DiagnosticStatus.PARTIALLY_SUPPORTED
        }
        checks += DiagnosticCheckItem(
            id = "usb_perm",
            titleEn = "USB Device Permission",
            titleAr = "صلاحية الوصول لأجهزة USB",
            status = usbPermStatus,
            detailEn = if (devices.isEmpty()) "Attach a USB device to test runtime permission" else "${devices.count { it.hasPermission }}/${devices.size} attached devices permitted",
            detailAr = if (devices.isEmpty()) "وصّل جهاز USB لاختبار الصلاحية" else "تم منح الصلاحية لـ ${devices.count { it.hasPermission }} من ${devices.size}"
        )

        // 4. Video Input, Resolution & Frame Rate
        val videoState = connectionManager.hybridState.value.videoSignal
        checks += when (videoState) {
            is VideoSignalState.Active -> DiagnosticCheckItem(
                id = "video_stream",
                titleEn = "Video Input, Resolution & FPS",
                titleAr = "إشارة الفيديو والدقة ومعدل الإطارات",
                status = DiagnosticStatus.SUPPORTED,
                detailEn = "${videoState.width}x${videoState.height} @ ${"%.1f".format(videoState.actualFps)} fps (${videoState.pixelFormat})",
                detailAr = "${videoState.width}x${videoState.height} بمعدل ${"%.1f".format(videoState.actualFps)} إطار/ث"
            )
            is VideoSignalState.Error -> DiagnosticCheckItem(
                id = "video_stream",
                titleEn = "Video Input, Resolution & FPS",
                titleAr = "إشارة الفيديو والدقة ومعدل الإطارات",
                status = DiagnosticStatus.PARTIALLY_SUPPORTED,
                detailEn = videoState.message,
                detailAr = videoState.message
            )
            else -> DiagnosticCheckItem(
                id = "video_stream",
                titleEn = "Video Input, Resolution & FPS",
                titleAr = "إشارة الفيديو والدقة ومعدل الإطارات",
                status = DiagnosticStatus.NOT_TESTED,
                detailEn = "Connect HDMI capture feed to measure real resolution & FPS",
                detailAr = "وصّل مصدر HDMI لقياس الدقة ومعدل الإطارات الفعلي"
            )
        }

        // 5. Wi-Fi Network Interface
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val netCaps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val wifiUp = netCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        checks += DiagnosticCheckItem(
            id = "wifi_link",
            titleEn = "Wi-Fi Transport State",
            titleAr = "حالة اتصال Wi-Fi",
            status = if (wifiUp) DiagnosticStatus.SUPPORTED else DiagnosticStatus.PARTIALLY_SUPPORTED,
            detailEn = if (wifiUp) "Device connected to a Wi-Fi network" else "Connect Android Wi-Fi to camera AP for wireless testing",
            detailAr = if (wifiUp) "الهاتف متصل بشبكة Wi-Fi" else "وصّل الهاتف بشبكة الكاميرا لاختبار التحكم اللاسلكي"
        )

        // 6. Camera Authentication & Remote Control Availability
        val ctrl = connectionManager.hybridState.value.controlChannel
        checks += DiagnosticCheckItem(
            id = "camera_auth",
            titleEn = "Camera Protocol & Remote Control (${spec.modelName})",
            titleAr = "التحقق وبروتوكول التحكم (${spec.modelName})",
            status = when (ctrl) {
                is ControlChannelState.Connected -> DiagnosticStatus.SUPPORTED
                is ControlChannelState.Error -> DiagnosticStatus.PARTIALLY_SUPPORTED
                else -> DiagnosticStatus.NOT_TESTED
            },
            detailEn = when (ctrl) {
                is ControlChannelState.Connected -> "Verified session via ${ctrl.channelType.label} (${ctrl.endpoint})"
                is ControlChannelState.Error -> ctrl.message
                else -> "Run Connection Wizard to verify ${spec.wirelessProtocolName}"
            },
            detailAr = when (ctrl) {
                is ControlChannelState.Connected -> "جلسة نشطة عبر ${ctrl.channelType.label}"
                is ControlChannelState.Error -> ctrl.message
                else -> "استخدم معالج الاتصال لاختبار البروتوكول"
            }
        )

        // 7. Storage Access & MP4 Hardware Encoder
        val freeMb = mediaCaptureManager.queryAvailableStorageMb()
        val hasH264 = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any {
            it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) }
        }
        checks += DiagnosticCheckItem(
            id = "storage_access",
            titleEn = "App Storage & MediaStore Export",
            titleAr = "التخزين وتصدير الصور والفيديو",
            status = if (freeMb > 50) DiagnosticStatus.SUPPORTED else DiagnosticStatus.PARTIALLY_SUPPORTED,
            detailEn = "${freeMb} MB free for MP4 & PNG captures (Zero-permission MediaStore)",
            detailAr = "${freeMb} ميجابايت متاحة للتسجيل واللقطات"
        )
        checks += DiagnosticCheckItem(
            id = "mp4_encoder",
            titleEn = "Hardware H.264 MP4 Recording",
            titleAr = "تشفير وتسجيل MP4 بالعتاد",
            status = if (hasH264) DiagnosticStatus.SUPPORTED else DiagnosticStatus.NOT_SUPPORTED,
            detailEn = if (hasH264) "H.264 AVC MediaCodec encoder available" else "No H.264 encoder reported by device",
            detailAr = if (hasH264) "مشفّر H.264 AVC متاح للتسجيل" else "لا يتوفر مشفّر H.264"
        )

        CompatibilityReport(
            targetModel = targetModel,
            testedAtMs = System.currentTimeMillis(),
            checks = checks,
            isRunning = false
        )
    }
}
