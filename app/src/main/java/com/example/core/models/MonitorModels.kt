package com.example.core.models

import android.graphics.Bitmap

enum class VideoSourceType(val label: String) {
    NONE("No Signal"),
    UVC_CAMERA2("HDMI UVC (Camera2)"),
    UVC_USB_BULK("HDMI UVC (USB Host)"),
    WIFI_LIVE_VIEW("Wi-Fi Live View")
}

enum class ControlChannelType(val label: String) {
    NONE("Disconnected"),
    CANON_CCAPI("Canon CCAPI Wi-Fi"),
    NIKON_PTPIP("Nikon PTP/IP Wi-Fi"),
    USB_PTP("USB PTP Control")
}

data class VideoFormatSpec(
    val pixelFormat: String,
    val width: Int,
    val height: Int,
    val supportedFps: List<Int>
) {
    val label: String get() = "${width}x${height} $pixelFormat"
}

sealed interface VideoSignalState {
    data class Disconnected(val reason: String) : VideoSignalState
    data class Connecting(val deviceName: String) : VideoSignalState
    data class Active(
        val sourceType: VideoSourceType,
        val deviceName: String,
        val width: Int,
        val height: Int,
        val actualFps: Float,
        val pixelFormat: String,
        val latencyMs: Long,
        val supportedFormats: List<VideoFormatSpec> = emptyList()
    ) : VideoSignalState
    data class Error(val message: String, val recoverable: Boolean = true) : VideoSignalState
}

sealed interface ControlChannelState {
    data object Disconnected : ControlChannelState
    data class Connecting(val endpoint: String) : ControlChannelState
    data class Connected(
        val channelType: ControlChannelType,
        val cameraName: String,
        val endpoint: String,
        val firmwareVersion: String? = null
    ) : ControlChannelState
    data class Error(val message: String) : ControlChannelState
}

data class HybridConnectionState(
    val activeModel: CameraModelId = CameraModelId.NIKON_D5600,
    val selectedMode: ConnectionMode = ConnectionMode.HDMI_CAPTURE,
    val videoSignal: VideoSignalState = VideoSignalState.Disconnected("Connect a UVC HDMI capture card via USB-C OTG"),
    val controlChannel: ControlChannelState = ControlChannelState.Disconnected,
    val detectedUsbSummary: String? = null,
    val usbPermissionPending: Boolean = false,
    val cameraPermissionRequired: Boolean = false,
    val thermalCelsius: Float = 0f,
    val batteryPercent: Int = 100
)

enum class PeakingColor(val argb: Int, val labelEn: String, val labelAr: String) {
    RED(0xFFFF1744.toInt(), "Red", "أحمر"),
    GREEN(0xFF00E676.toInt(), "Green", "أخضر"),
    BLUE(0xFF38BDF8.toInt(), "Blue", "أزرق"),
    WHITE(0xFFFFFFFF.toInt(), "White", "أبيض")
}

enum class FocusZoomFactor(val factor: Float, val label: String) {
    X1(1f, "1x"),
    X2(2f, "2x"),
    X4(4f, "4x"),
    X8(8f, "8x")
}

data class FocusToolsConfig(
    val peakingEnabled: Boolean = false,
    val peakingSensitivity: Float = 0.45f,
    val peakingIntensity: Float = 0.85f,
    val peakingColor: PeakingColor = PeakingColor.RED,
    val zoomFactor: FocusZoomFactor = FocusZoomFactor.X1,
    val zoomCenterX: Float = 0.5f,
    val zoomCenterY: Float = 0.5f,
    val centerCrosshair: Boolean = false,
    val focusGuides: Boolean = false,
    val sharpnessReadoutEnabled: Boolean = true
)

enum class ScopeMode(val labelEn: String, val labelAr: String) {
    NONE("Off", "إيقاف"),
    RGB_HISTOGRAM("RGB Histogram", "هيستوغرام RGB"),
    LUMA_HISTOGRAM("Luma Histogram", "هيستوغرام السطوع"),
    WAVEFORM("Luma Waveform", "موجة الإضاءة"),
    RGB_PARADE("RGB Parade", "استعراض RGB")
}

data class ExposureToolsConfig(
    val scopeMode: ScopeMode = ScopeMode.NONE,
    val zebraEnabled: Boolean = false,
    val zebraThresholdIre: Int = 85,
    val zebraColorArgb: Int = 0xFFFF9100.toInt(),
    val falseColorEnabled: Boolean = false,
    val highlightClippingEnabled: Boolean = false,
    val shadowClippingEnabled: Boolean = false,
    val highlightThreshold: Int = 248,
    val shadowThreshold: Int = 8,
    val overlayOpacity: Float = 0.82f,
    val exposureWarningsEnabled: Boolean = true
)

enum class AspectRatioGuide(val ratio: Float?, val label: String) {
    NONE(null, "Off"),
    RATIO_16_9(16f / 9f, "16:9"),
    RATIO_4_3(4f / 3f, "4:3"),
    RATIO_235_1(2.35f, "2.35:1"),
    RATIO_1_1(1f, "1:1"),
    RATIO_9_16(9f / 16f, "9:16")
}

enum class AnamorphicPreset(val squeezeX: Float, val label: String) {
    OFF(1.0f, "1.0x"),
    X1_33(1.33f, "1.33x"),
    X1_5(1.5f, "1.5x"),
    X1_8(1.8f, "1.8x"),
    X2_0(2.0f, "2.0x")
}

data class CompositionConfig(
    val ruleOfThirds: Boolean = false,
    val goldenRatio: Boolean = false,
    val centerCross: Boolean = false,
    val aspectRatioGuide: AspectRatioGuide = AspectRatioGuide.NONE,
    val safeAreas: Boolean = false,
    val customFramingMarginPercent: Int = 0,
    val anamorphicPreset: AnamorphicPreset = AnamorphicPreset.OFF
)

enum class VideoScaleMode(val labelEn: String, val labelAr: String) {
    FIT("Fit", "ملاءمة"),
    FILL("Fill", "ملء"),
    FULLSCREEN("Full", "كامل")
}

data class MonitorTransformConfig(
    val scaleMode: VideoScaleMode = VideoScaleMode.FIT,
    val rotationDegrees: Int = 0,
    val mirrorHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val orientationLocked: Boolean = false,
    val controlsHidden: Boolean = false
)

data class LutConfig(
    val enabled: Boolean = false,
    val activeLutId: String? = null,
    val activeLutName: String = "Rec.709 Neutral",
    val intensity: Float = 1.0f,
    val splitCompareEnabled: Boolean = false,
    val splitPosition: Float = 0.5f,
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val sharpness: Float = 0.0f
)

data class FrameAnalysisResult(
    val lumaHistogram: IntArray = IntArray(256),
    val rHistogram: IntArray = IntArray(256),
    val gHistogram: IntArray = IntArray(256),
    val bHistogram: IntArray = IntArray(256),
    val waveformColumns: Array<IntArray> = emptyArray(),
    val paradeR: Array<IntArray> = emptyArray(),
    val paradeG: Array<IntArray> = emptyArray(),
    val paradeB: Array<IntArray> = emptyArray(),
    val peakingOverlay: Bitmap? = null,
    val exposureOverlay: Bitmap? = null,
    val highlightClippedPercent: Float = 0f,
    val shadowClippedPercent: Float = 0f,
    val averageIre: Float = 0f,
    val sharpnessScore: Float = 0f,
    val isFromHardwareFrame: Boolean = false
)

data class RecordingStatus(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val audioEnabled: Boolean = false,
    val outputPath: String? = null,
    val availableStorageMb: Long = 0L,
    val lastError: String? = null
)
