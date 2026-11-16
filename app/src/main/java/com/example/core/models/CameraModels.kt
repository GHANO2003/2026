package com.example.core.models

enum class CameraBrand(val displayName: String) {
    NIKON("Nikon"),
    CANON("Canon")
}

enum class CameraModelId(val brand: CameraBrand, val modelName: String) {
    NIKON_D5600(CameraBrand.NIKON, "Nikon D5600"),
    NIKON_D5500(CameraBrand.NIKON, "Nikon D5500"),
    CANON_EOS_90D(CameraBrand.CANON, "Canon EOS 90D")
}

enum class ConnectionMode(val labelEn: String, val labelAr: String) {
    HDMI_CAPTURE("HDMI Capture (UVC)", "التقاط HDMI (UVC)"),
    WIFI_WIRELESS("Wi-Fi Wireless", "اتصال لاسلكي Wi-Fi"),
    USB_PTP("USB Camera Control", "تحكم بالكاميرا عبر USB"),
    HYBRID("Hybrid (HDMI + Control)", "هجين (HDMI + تحكم)")
}

enum class CameraSettingKey(val labelEn: String, val labelAr: String) {
    ISO("ISO", "حساسية الضوء ISO"),
    SHUTTER_SPEED("Shutter Speed", "سرعة الغالق"),
    APERTURE("Aperture (f/)", "فتحة العدسة"),
    WHITE_BALANCE("White Balance", "توازن البياض"),
    EXPOSURE_COMP("Exposure Comp", "تعويض التعريض")
}

enum class CapabilitySupport {
    CONFIRMED,
    LIMITED,
    UNSUPPORTED
}

data class CameraCapabilityRow(
    val featureEn: String,
    val featureAr: String,
    val support: CapabilitySupport,
    val noteEn: String,
    val noteAr: String
)

data class CameraSetupGuide(
    val mode: ConnectionMode,
    val titleEn: String,
    val titleAr: String,
    val stepsEn: List<String>,
    val stepsAr: List<String>,
    val imageResType: SetupIllustrationType
)

enum class SetupIllustrationType {
    HDMI_OTG,
    WIFI_DSLR
}

data class CameraModelSpec(
    val id: CameraModelId,
    val brand: CameraBrand,
    val modelName: String,
    val usbVendorId: Int,
    val sensorSummary: String,
    val hdmiOutputSpec: String,
    val wirelessProtocolName: String,
    val wirelessLimitationNoteEn: String,
    val wirelessLimitationNoteAr: String,
    val supportedControlSettings: Set<CameraSettingKey>,
    val supportsRemotePhoto: Boolean,
    val supportsRemoteVideoToggle: Boolean,
    val supportsWifiLiveView: Boolean,
    val supportsCleanHdmi: Boolean,
    val capabilityTable: List<CameraCapabilityRow>,
    val setupGuides: List<CameraSetupGuide>
)

data class CameraControlSnapshot(
    val iso: String = "--",
    val shutterSpeed: String = "--",
    val aperture: String = "--",
    val whiteBalance: String = "--",
    val exposureComp: String = "--",
    val availableIso: List<String> = emptyList(),
    val availableShutter: List<String> = emptyList(),
    val availableAperture: List<String> = emptyList(),
    val availableWb: List<String> = emptyList(),
    val availableExpComp: List<String> = emptyList(),
    val isRecordingVideoOnCamera: Boolean = false,
    val lastConfirmedAtMs: Long = 0L,
    val lastCommandError: String? = null,
    val isExecutingCommand: Boolean = false
)

sealed interface CommandExecutionResult {
    data class Confirmed(val message: String) : CommandExecutionResult
    data class Failed(val reason: String) : CommandExecutionResult
}
