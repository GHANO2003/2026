package com.example.camera

import com.example.core.models.CameraBrand
import com.example.core.models.CameraCapabilityRow
import com.example.core.models.CameraModelId
import com.example.core.models.CameraModelSpec
import com.example.core.models.CameraSettingKey
import com.example.core.models.CameraSetupGuide
import com.example.core.models.CapabilitySupport
import com.example.core.models.ConnectionMode
import com.example.core.models.SetupIllustrationType

object CameraProfileRegistry {

    val nikonD5600 = CameraModelSpec(
        id = CameraModelId.NIKON_D5600,
        brand = CameraBrand.NIKON,
        modelName = "Nikon D5600",
        usbVendorId = 0x04B0,
        sensorSummary = "24.2 MP APS-C CMOS • EXPEED 4 • Type-C Mini HDMI",
        hdmiOutputSpec = "1080p @ 60/50/30/25/24 fps (8-bit 4:2:2 via Mini HDMI)",
        wirelessProtocolName = "SnapBridge (BLE + Wi-Fi AP PTP/IP :15740)",
        wirelessLimitationNoteEn = "Nikon D5600 SnapBridge restricts third-party Wi-Fi video recording and full parameter changes during BLE pairing. Enable camera Wi-Fi AP mode for PTP/IP photo/Live View or use HDMI Capture for 1080p60 low-latency monitoring.",
        wirelessLimitationNoteAr = "يقيد نظام SnapBridge في Nikon D5600 تسجيل الفيديو اللاسلكي وبعض الإعدادات لتطبيقات الطرف الثالث. فعّل وضع Wi-Fi AP للتحكم عبر PTP/IP أو استخدم التقاط HDMI للمراقبة الفورية 1080p60.",
        supportedControlSettings = setOf(
            CameraSettingKey.ISO,
            CameraSettingKey.SHUTTER_SPEED,
            CameraSettingKey.APERTURE,
            CameraSettingKey.WHITE_BALANCE,
            CameraSettingKey.EXPOSURE_COMP
        ),
        supportsRemotePhoto = true,
        supportsRemoteVideoToggle = false,
        supportsWifiLiveView = true,
        supportsCleanHdmi = true,
        capabilityTable = listOf(
            CameraCapabilityRow("HDMI Live Monitoring (1080p60)", "مراقبة HDMI المباشرة (1080p60)", CapabilitySupport.CONFIRMED, "Via Mini-HDMI + UVC capture card", "عبر كابل Mini-HDMI وبطاقة التقاط UVC"),
            CameraCapabilityRow("USB PTP Remote Capture & Exposure", "التحكم والتعريض عبر USB PTP", CapabilitySupport.CONFIRMED, "ISO, Tv, Av, WB, Exp Comp & Shutter", "ISO والغالق والفتحة وتوازن البياض"),
            CameraCapabilityRow("Wi-Fi PTP/IP Photo & Live View", "التقاط وصورة مباشرة عبر Wi-Fi PTP/IP", CapabilitySupport.LIMITED, "Requires camera Wi-Fi AP enabled (not BLE-only)", "يتطلب تفعيل نقطة اتصال الكاميرا (ليس BLE فقط)"),
            CameraCapabilityRow("Remote Video Rec Start/Stop", "بدء/إيقاف تسجيل الفيديو عن بعد", CapabilitySupport.UNSUPPORTED, "D5600 firmware does not expose movie record opcode over Wi-Fi/PTP", "نظام D5600 لا يتيح أمر بدء تسجيل الفيديو خارجياً")
        ),
        setupGuides = listOf(
            CameraSetupGuide(
                mode = ConnectionMode.HDMI_CAPTURE,
                titleEn = "Nikon D5600 HDMI + USB-C Capture Setup",
                titleAr = "إعداد التوصيل HDMI + USB-C لكاميرا Nikon D5600",
                stepsEn = listOf(
                    "Connect a Type-C Mini-HDMI cable to the Nikon D5600 HDMI port.",
                    "Plug the HDMI cable into your UVC USB HDMI capture card.",
                    "Connect the capture card to your Android device via USB-C OTG.",
                    "On Nikon D5600: Setup Menu -> HDMI -> Output Resolution -> 1080p (progressive).",
                    "Switch camera to Live View (Lv) and press INFO to hide OSD overlays for clean feed."
                ),
                stepsAr = listOf(
                    "وصّل كابل Mini-HDMI بمنفذ HDMI في كاميرا Nikon D5600.",
                    "وصّل الطرف الآخر ببطاقة التقاط الفيديو HDMI UVC.",
                    "وصّل بطاقة الالتقاط بهاتف Android عبر منفذ USB-C OTG.",
                    "من قائمة الكاميرا: Setup Menu -> HDMI -> Output Resolution -> 1080p.",
                    "شغّل Live View (Lv) واضغط INFO لإخفاء البيانات والحصول على صورة نظيفة."
                ),
                imageResType = SetupIllustrationType.HDMI_OTG
            ),
            CameraSetupGuide(
                mode = ConnectionMode.WIFI_WIRELESS,
                titleEn = "Nikon D5600 SnapBridge & Wi-Fi AP Pairing",
                titleAr = "إعداد الاقتران اللاسلكي و SnapBridge لكاميرا Nikon D5600",
                stepsEn = listOf(
                    "Open Setup Menu on D5600 -> Connect to smart device -> Start.",
                    "If SnapBridge BLE holds Wi-Fi off, switch to Camera Wi-Fi menu -> Establish Wi-Fi connection.",
                    "Join the Nikon_D5600_XXXX Wi-Fi network in Android Wi-Fi settings.",
                    "Verify gateway IP (typically 192.168.1.1, PTP/IP port 15740) and tap Test Connection."
                ),
                stepsAr = listOf(
                    "افتح قائمة Setup Menu في D5600 ثم اختر Connect to smart device.",
                    "قم بتفعيل نقطة اتصال Wi-Fi في الكاميرا لتوليد شبكة Nikon_D5600.",
                    "اتصل بشبكة الكاميرا من إعدادات الواي فاي في هاتف Android.",
                    "تحقق من عنوان IP (غالباً 192.168.1.1 المنفذ 15740) ثم اضغط اختبار الاتصال."
                ),
                imageResType = SetupIllustrationType.WIFI_DSLR
            )
        )
    )

    val nikonD5500 = CameraModelSpec(
        id = CameraModelId.NIKON_D5500,
        brand = CameraBrand.NIKON,
        modelName = "Nikon D5500",
        usbVendorId = 0x04B0,
        sensorSummary = "24.2 MP APS-C CMOS • EXPEED 4 • Built-in 802.11n Wi-Fi",
        hdmiOutputSpec = "1080p @ 60/50/30/25/24 fps (Mini HDMI)",
        wirelessProtocolName = "Wireless Mobile Utility / PTP-IP (:15740)",
        wirelessLimitationNoteEn = "Nikon D5500 uses built-in Wi-Fi (WMU / PTP-IP on 192.168.1.1:15740) without SnapBridge BLE. Wi-Fi supports remote shutter & low-fps JPEG Live View; remote movie start/stop is unavailable over Wi-Fi.",
        wirelessLimitationNoteAr = "تستخدم Nikon D5500 شبكة Wi-Fi مدمجة متوافقة مع WMU وبروتوكول PTP/IP بدون SnapBridge. يدعم الاتصال اللاسلكي التقاط الصور والمعاينة، ولا يدعم تشغيل تسجيل الفيديو لاسلكياً.",
        supportedControlSettings = setOf(
            CameraSettingKey.ISO,
            CameraSettingKey.SHUTTER_SPEED,
            CameraSettingKey.APERTURE,
            CameraSettingKey.WHITE_BALANCE,
            CameraSettingKey.EXPOSURE_COMP
        ),
        supportsRemotePhoto = true,
        supportsRemoteVideoToggle = false,
        supportsWifiLiveView = true,
        supportsCleanHdmi = true,
        capabilityTable = listOf(
            CameraCapabilityRow("HDMI Live Monitoring (1080p60)", "مراقبة HDMI المباشرة (1080p60)", CapabilitySupport.CONFIRMED, "Via Mini-HDMI + UVC capture card in landscape", "عبر Mini-HDMI وبطاقة UVC بوضع العرض الأفقي"),
            CameraCapabilityRow("Built-in Wi-Fi PTP/IP (WMU)", "اتصال Wi-Fi المدمج (WMU / PTP-IP)", CapabilitySupport.CONFIRMED, "Connects to 192.168.1.1:15740 for photo & Live View", "يتصل عبر 192.168.1.1:15740 للتصوير والمعاينة"),
            CameraCapabilityRow("USB PTP Exposure Controls", "التحكم بالتعريض عبر USB PTP", CapabilitySupport.CONFIRMED, "ISO, Shutter, Aperture, WB, Exp Comp", "ISO وسرعة الغالق والفتحة وتوازن البياض"),
            CameraCapabilityRow("Remote Video Rec Start/Stop", "تشغيل تسجيل الفيديو عن بعد", CapabilitySupport.UNSUPPORTED, "Not supported by Nikon D5500 wireless protocol", "غير مدعوم في بروتوكول D5500 اللاسلكي")
        ),
        setupGuides = listOf(
            CameraSetupGuide(
                mode = ConnectionMode.HDMI_CAPTURE,
                titleEn = "Nikon D5500 HDMI External Monitor Setup",
                titleAr = "إعداد مراقب HDMI لكاميرا Nikon D5500",
                stepsEn = listOf(
                    "Attach Mini-HDMI cable from D5500 to your USB UVC capture card.",
                    "Connect UVC capture card to Android via USB-C OTG.",
                    "In D5500 Setup Menu -> HDMI -> Output resolution -> 1080p.",
                    "Press Lv button to enter Live View and press INFO until frame markers clear."
                ),
                stepsAr = listOf(
                    "وصّل كابل Mini-HDMI من كاميرا D5500 إلى بطاقة التقاط UVC.",
                    "وصّل البطاقة بهاتف Android عبر منفذ USB-C OTG.",
                    "من قائمة Setup Menu -> HDMI اختر دقة الإخراج 1080p.",
                    "اضغط زر Lv لتشغيل Live View ثم INFO لإخفاء المعلومات."
                ),
                imageResType = SetupIllustrationType.HDMI_OTG
            ),
            CameraSetupGuide(
                mode = ConnectionMode.WIFI_WIRELESS,
                titleEn = "Nikon D5500 Built-in Wi-Fi (WMU) Setup",
                titleAr = "إعداد الواي فاي المدمج (WMU) لكاميرا Nikon D5500",
                stepsEn = listOf(
                    "On Nikon D5500: Setup Menu -> Wi-Fi -> Network connection -> Enable.",
                    "Wait for the camera SSID (Nikon_WU2_XXXX) to broadcast.",
                    "Connect Android Wi-Fi to the Nikon SSID (no password by default unless configured).",
                    "Keep IP at 192.168.1.1 (port 15740) and tap Test Connection."
                ),
                stepsAr = listOf(
                    "في كاميرا D5500: افتح Setup Menu -> Wi-Fi -> Network connection -> Enable.",
                    "انتظر ظهور شبكة الكاميرا Nikon_WU2.",
                    "وصّل هاتف Android بشبكة الكاميرا من إعدادات الواي فاي.",
                    "استخدم العنوان 192.168.1.1 والمنفذ 15740 ثم اضغط اختبار الاتصال."
                ),
                imageResType = SetupIllustrationType.WIFI_DSLR
            )
        )
    )

    val canonEos90d = CameraModelSpec(
        id = CameraModelId.CANON_EOS_90D,
        brand = CameraBrand.CANON,
        modelName = "Canon EOS 90D",
        usbVendorId = 0x04A9,
        sensorSummary = "32.5 MP APS-C CMOS • DIGIC 8 • 4K30 / 1080p60 Clean HDMI",
        hdmiOutputSpec = "1080p @ 60fps & 4K @ 30fps (Clean HDMI Output)",
        wirelessProtocolName = "Canon Camera Connect / CCAPI (HTTP REST :8080)",
        wirelessLimitationNoteEn = "Canon EOS 90D supports Canon Camera Connect / CCAPI over Wi-Fi (when CCAPI firmware or Camera Connect Wi-Fi session is active) for full exposure control, AF, Live View, and movie start/stop.",
        wirelessLimitationNoteAr = "تدعم Canon EOS 90D واجهة Canon Camera Connect و CCAPI عبر Wi-Fi للتحكم الكامل بالتعريض والتركيز والمعاينة وتشغيل تسجيل الفيديو، بالإضافة إلى إخراج HDMI نظيف حتى 4K.",
        supportedControlSettings = setOf(
            CameraSettingKey.ISO,
            CameraSettingKey.SHUTTER_SPEED,
            CameraSettingKey.APERTURE,
            CameraSettingKey.WHITE_BALANCE,
            CameraSettingKey.EXPOSURE_COMP
        ),
        supportsRemotePhoto = true,
        supportsRemoteVideoToggle = true,
        supportsWifiLiveView = true,
        supportsCleanHdmi = true,
        capabilityTable = listOf(
            CameraCapabilityRow("Clean HDMI (1080p60 / 4K30)", "إخراج HDMI نظيف (1080p60 / 4K30)", CapabilitySupport.CONFIRMED, "Supports clean 1080p60 & 4K30 when capture hardware supports 4K", "يدعم إخراجاً نظيفاً 1080p60 و 4K30 حسب بطاقة الالتقاط"),
            CameraCapabilityRow("Canon CCAPI Wi-Fi Control", "تحكم Canon CCAPI عبر Wi-Fi", CapabilitySupport.CONFIRMED, "ISO, Tv, Av, WB, Exp Comp, AF, Photo & Movie Rec", "ISO والغالق والفتحة وتوازن البياض والتركيز والتسجيل"),
            CameraCapabilityRow("Canon Wi-Fi Live View Stream", "بث المعاينة عبر Wi-Fi", CapabilitySupport.CONFIRMED, "GET /ccapi/ver100/shooting/liveview/flip", "عبر واجهة CCAPI Live View"),
            CameraCapabilityRow("USB PTP Camera Control", "التحكم بالكاميرا عبر USB PTP", CapabilitySupport.CONFIRMED, "Confirmed EOS PTP control over USB-C OTG", "متاح عبر USB-C OTG")
        ),
        setupGuides = listOf(
            CameraSetupGuide(
                mode = ConnectionMode.HDMI_CAPTURE,
                titleEn = "Canon EOS 90D Clean HDMI (1080p / 4K) Setup",
                titleAr = "إعداد HDMI النظيف لكاميرا Canon EOS 90D",
                stepsEn = listOf(
                    "Connect a Mini-HDMI (Type-C) cable to your UVC capture card and Android USB-C OTG.",
                    "In EOS 90D Movie Menu 5 -> HDMI display -> Select 'Clean / 4K output' or 'No info'.",
                    "Set Movie rec quality to 1080p 59.94fps or 4K 29.97fps depending on your UVC dongle.",
                    "In Live Monitor, verify active input resolution and FPS in the top HUD."
                ),
                stepsAr = listOf(
                    "وصّل كابل Mini-HDMI من كاميرا EOS 90D إلى بطاقة التقاط UVC ثم بهاتف Android.",
                    "من قائمة Movie Menu 5 -> HDMI display اختر الإخراج النظيف Clean / No info.",
                    "اختر دقة 1080p 60fps أو 4K 30fps حسب قدرة بطاقة الالتقاط.",
                    "تحقق من الدقة ومعدل الإطارات الفعلي في شريط المراقب العلوي."
                ),
                imageResType = SetupIllustrationType.HDMI_OTG
            ),
            CameraSetupGuide(
                mode = ConnectionMode.WIFI_WIRELESS,
                titleEn = "Canon EOS 90D Camera Connect / CCAPI Wi-Fi Setup",
                titleAr = "إعداد Canon Camera Connect / CCAPI لكاميرا EOS 90D",
                stepsEn = listOf(
                    "On EOS 90D: Wireless communication settings -> Wi-Fi settings -> Enable.",
                    "Select Camera Control API (CCAPI) or Connect to smartphone (Camera Connect).",
                    "Connect Android Wi-Fi to the Canon EOS 90D network (or shared router IP).",
                    "Enter the camera CCAPI IP (e.g., 192.168.1.2, port 8080) and tap Test Connection."
                ),
                stepsAr = listOf(
                    "في كاميرا EOS 90D: افتح إعدادات الاتصال اللاسلكي ثم فعّل Wi-Fi.",
                    "اختر Camera Control API (CCAPI) أو Connect to smartphone.",
                    "وصّل هاتف Android بشبكة الكاميرا أو الشبكة المحلية المشتركة.",
                    "أدخل عنوان IP (مثل 192.168.1.2 المنفذ 8080) ثم اضغط اختبار الاتصال."
                ),
                imageResType = SetupIllustrationType.WIFI_DSLR
            )
        )
    )

    val allModels: List<CameraModelSpec> = listOf(nikonD5600, nikonD5500, canonEos90d)

    fun getSpec(id: CameraModelId): CameraModelSpec = when (id) {
        CameraModelId.NIKON_D5600 -> nikonD5600
        CameraModelId.NIKON_D5500 -> nikonD5500
        CameraModelId.CANON_EOS_90D -> canonEos90d
    }
}
