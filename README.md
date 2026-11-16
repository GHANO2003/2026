# ProMonitor 360 — Professional DSLR Field Monitor & Camera Controller

**ProMonitor 360** is a native Kotlin + Jetpack Compose Android external monitor and camera remote controller built for **Nikon D5600**, **Nikon D5500**, and **Canon EOS 90D**, with a modular `CameraAdapter` architecture for adding future Nikon and Canon bodies.

> **Important Note on Build Runtime:** This project is implemented natively in Kotlin + Jetpack Compose (`com.aistudio.promonitor360.kxmpzq`) for direct Android USB Host, Camera2 External UVC, PTP/IP, Canon CCAPI, MediaCodec H.264 recording, and hardware-accelerated Canvas scopes.

---

## Supported Cameras & Compatibility Matrix

| Camera Model | HDMI Capture (UVC) | Wireless Protocol | Remote Photo | Remote Video Rec | Confirmed Remote Settings |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Nikon D5600** | Confirmed (1080p60) | SnapBridge BLE / Wi-Fi AP PTP/IP `:15740` | Confirmed (PTP/IP & USB) | Unsupported by firmware | ISO, Shutter, Aperture, WB, Exp Comp |
| **Nikon D5500** | Confirmed (1080p60) | Built-in Wi-Fi WMU PTP/IP `:15740` | Confirmed (PTP/IP & USB) | Unsupported by firmware | ISO, Shutter, Aperture, WB, Exp Comp |
| **Canon EOS 90D** | Confirmed (1080p60 / 4K30 Clean) | Canon Camera Connect / CCAPI `:8080` | Confirmed (CCAPI & USB) | Confirmed (CCAPI `moviemode`) | ISO, Tv, Av, WB, Exp Comp, AF, Movie |

---

## Architecture

- `com.example.usb` & `com.example.uvc`: Android USB Host discovery, `UvcDescriptorParser` (MJPEG/YUY2 format/frame/FPS parsing), `Camera2 LENS_FACING_EXTERNAL` & USB Host Bulk UVC capture.
- `com.example.nikon` & `com.example.canon`: ISO 15740 `PtpIpClient`, `UsbPtpClient`, and `CanonCcapiClient` (OkHttp REST).
- `com.example.connection`: `UnifiedConnectionManager` supporting independent Video & Control channels (**Hybrid Mode**), thermal/battery monitoring, and USB disconnect recovery.
- `com.example.analysis`: Real-time `ImageAnalysisEngine` (Sobel Focus Peaking, Zebra, False Color, Clipping, RGB/Luma Histogram, Luma Waveform, RGB Parade) and `CubeLutEngine` (`.cube` 3D LUT trilinear interpolation).
- `com.example.recording`: `MediaCaptureManager` (PNG frame grabs, `MediaCodec` H.264 MP4 recording, MediaStore export, `FileProvider` sharing).
- `com.example.data`: Room Database (`SavedCameraProfileEntity`, `LutPresetEntity`, `RecordedMediaEntity`) + DataStore Preferences.

---

## دليل الاستخدام السريع (العربية)

تطبيق **ProMonitor 360** يحوّل هاتف أو جهاز لوحي Android إلى شاشة مراقبة احترافية وتحكم عن بُعد لكاميرات:
1. **Nikon D5600** (مراقبة HDMI 1080p60 + تحكم USB PTP أو Wi-Fi AP PTP/IP)
2. **Nikon D5500** (مراقبة HDMI 1080p60 + واي فاي مدمج WMU PTP/IP)
3. **Canon EOS 90D** (مراقبة HDMI نظيفة حتى 4K30 + تحكم كامل عبر Canon CCAPI)

### خطوات التوصيل عبر HDMI (الأفضل للمراقبة الفورية):
1. وصّل كابل **Mini-HDMI** من الكاميرا إلى بطاقة التقاط **UVC HDMI Capture Card**.
2. وصّل بطاقة الالتقاط بالهاتف عبر **USB-C OTG**.
3. افتح **معالج الاتصال** أو اضغط **فحص USB / HDMI** في الشاشة الرئيسية وامنح صلاحية USB.
4. ملاحظة: تسجيل MP4 يتم من إشارة HDMI المضغوطة ولا يستبدل ملفات RAW الداخلية للكاميرا.
