package com.example.uvc

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import com.example.core.models.VideoFormatSpec
import com.example.core.models.VideoSignalState
import com.example.core.models.VideoSourceType
import com.example.usb.UsbDiscoveredDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class UvcCaptureEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _signalState = MutableStateFlow<VideoSignalState>(
        VideoSignalState.Disconnected("Connect a UVC HDMI capture card via USB-C OTG")
    )
    val signalState: StateFlow<VideoSignalState> = _signalState.asStateFlow()

    private val _latestFrame = MutableStateFlow<Bitmap?>(null)
    val latestFrame: StateFlow<Bitmap?> = _latestFrame.asStateFlow()

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private var usbConnection: UsbDeviceConnection? = null
    private var usbClaimedInterface: UsbInterface? = null
    private var usbStreamJob: Job? = null

    private var frameCounter = 0
    private var fpsWindowStartMs = System.currentTimeMillis()
    private var measuredFps = 0f

    fun findExternalCamera2Devices(): List<String> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
        return runCatching {
            manager.cameraIdList.filter { id ->
                val chars = manager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    facing == CameraCharacteristics.LENS_FACING_EXTERNAL
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    fun startCamera2ExternalCapture(cameraId: String, preferredWidth: Int = 1920, preferredHeight: Int = 1080): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            _signalState.value = VideoSignalState.Error("CAMERA permission required to access Android External UVC Camera2 stream")
            return false
        }
        stopCapture()
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false

        val chars = runCatching { manager.getCameraCharacteristics(cameraId) }.getOrNull()
        val map = chars?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty()
        val chosen = yuvSizes.minByOrNull {
            kotlin.math.abs(it.width - preferredWidth) + kotlin.math.abs(it.height - preferredHeight)
        }
        val width = chosen?.width ?: preferredWidth
        val height = chosen?.height ?: preferredHeight

        val formats = yuvSizes.map {
            VideoFormatSpec("YUV_420_888", it.width, it.height, listOf(30, 60))
        }

        _signalState.value = VideoSignalState.Connecting("External UVC ($cameraId)")
        val thread = HandlerThread("UvcCamera2Thread").apply { start() }
        cameraThread = thread
        val handler = Handler(thread.looper)
        cameraHandler = handler

        val reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 3)
        imageReader = reader
        fpsWindowStartMs = System.currentTimeMillis()
        frameCounter = 0

        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            val startNs = System.nanoTime()
            try {
                val bmp = yuv420ToBitmap(img)
                if (bmp != null) {
                    _latestFrame.value = bmp
                    updateFpsAndSignal(
                        sourceType = VideoSourceType.UVC_CAMERA2,
                        deviceName = "HDMI UVC ($cameraId)",
                        width = bmp.width,
                        height = bmp.height,
                        pixelFormat = "YUV_420_888",
                        latencyMs = (System.nanoTime() - startNs) / 1_000_000L,
                        supportedFormats = formats
                    )
                }
            } finally {
                img.close()
            }
        }, handler)

        runCatching {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(
                        listOf(reader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                    addTarget(reader.surface)
                                }.build()
                                session.setRepeatingRequest(req, null, handler)
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                _signalState.value = VideoSignalState.Error("Failed to configure External UVC capture session")
                            }
                        },
                        handler
                    )
                }

                override fun onDisconnected(camera: CameraDevice) {
                    stopCapture()
                    _signalState.value = VideoSignalState.Disconnected("External UVC device disconnected")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    stopCapture()
                    _signalState.value = VideoSignalState.Error("Camera2 UVC error code $error")
                }
            }, handler)
        }.onFailure { err ->
            _signalState.value = VideoSignalState.Error(err.message ?: "Failed to open Camera2 external device")
            return false
        }
        return true
    }

    fun startUsbHostBulkCapture(
        discovered: UsbDiscoveredDevice,
        connection: UsbDeviceConnection,
        preferredConfig: UvcStreamConfig? = null
    ) {
        stopCapture()
        val parsed = discovered.parsedUvc ?: UvcDescriptorParser.parse(connection.rawDescriptors)
        val bulkAddr = parsed.bulkEndpointAddress
        if (bulkAddr == null || parsed.streamingInterfaceNumber < 0) {
            connection.close()
            val detail = if (parsed.hasIsochronousEndpoint) {
                "Detected ${discovered.displayName} (UVC ${parsed.uvcVersion}) with isochronous-only USB endpoints. Android kernel UVC Camera2 driver is required unless the capture card exposes a Bulk UVC endpoint."
            } else {
                "Unsupported UVC descriptor layout on ${discovered.displayName}."
            }
            _signalState.value = VideoSignalState.Error(detail, recoverable = true)
            return
        }

        val device: UsbDevice = discovered.device
        val streamIntf = (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull { it.id == parsed.streamingInterfaceNumber }
        if (streamIntf == null || !connection.claimInterface(streamIntf, true)) {
            connection.close()
            _signalState.value = VideoSignalState.Error("Failed to claim USB Video Streaming interface")
            return
        }

        val endpoint = (0 until streamIntf.endpointCount)
            .map { streamIntf.getEndpoint(it) }
            .firstOrNull { it.address == bulkAddr && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }

        if (endpoint == null) {
            connection.releaseInterface(streamIntf)
            connection.close()
            _signalState.value = VideoSignalState.Error("Bulk IN video endpoint not found on ${discovered.displayName}")
            return
        }

        val targetConfig = preferredConfig
            ?: parsed.configs.firstOrNull { it.pixelFormat == "MJPEG" && it.width == 1920 }
            ?: parsed.configs.firstOrNull { it.pixelFormat == "MJPEG" }
            ?: parsed.configs.firstOrNull()

        if (targetConfig == null) {
            connection.releaseInterface(streamIntf)
            connection.close()
            _signalState.value = VideoSignalState.Error("No UVC frame descriptors advertised by ${discovered.displayName}")
            return
        }

        usbConnection = connection
        usbClaimedInterface = streamIntf
        _signalState.value = VideoSignalState.Connecting(discovered.displayName)

        usbStreamJob = scope.launch(Dispatchers.IO) {
            val negotiated = negotiateProbeAndCommit(connection, parsed.streamingInterfaceNumber, targetConfig)
            if (!negotiated) {
                _signalState.value = VideoSignalState.Error("UVC Probe/Commit negotiation rejected by ${discovered.displayName}")
                stopCapture()
                return@launch
            }

            val packetSize = (parsed.bulkMaxPacketSize.coerceAtLeast(512) * 16).coerceAtMost(65536)
            val transferBuffer = ByteArray(packetSize)
            val frameAssembly = ByteArrayOutputStream(512 * 1024)
            var lastFid = -1
            fpsWindowStartMs = System.currentTimeMillis()
            frameCounter = 0

            while (isActive) {
                val read = connection.bulkTransfer(endpoint, transferBuffer, transferBuffer.size, 500)
                if (read > 2) {
                    val headerLen = transferBuffer[0].toInt() and 0xFF
                    val headerInfo = transferBuffer[1].toInt() and 0xFF
                    val fid = headerInfo and 0x01
                    val eof = (headerInfo and 0x02) != 0
                    val hasError = (headerInfo and 0x40) != 0

                    if (lastFid != -1 && fid != lastFid && frameAssembly.size() > 0) {
                        val completed = frameAssembly.toByteArray()
                        frameAssembly.reset()
                        decodeAndPublishUvcFrame(completed, targetConfig, discovered.displayName, parsed.formatSummaries)
                    }
                    lastFid = fid

                    if (!hasError && headerLen in 2..read) {
                        frameAssembly.write(transferBuffer, headerLen, read - headerLen)
                    }

                    if (eof && frameAssembly.size() > 0) {
                        val completed = frameAssembly.toByteArray()
                        frameAssembly.reset()
                        decodeAndPublishUvcFrame(completed, targetConfig, discovered.displayName, parsed.formatSummaries)
                    }
                } else if (read < 0) {
                    // Timeout or disconnect; keep waiting unless disconnected by UsbManager receiver
                }
            }
        }
    }

    private fun negotiateProbeAndCommit(
        connection: UsbDeviceConnection,
        interfaceNum: Int,
        cfg: UvcStreamConfig
    ): Boolean {
        val probe = ByteArray(26)
        probe[2] = cfg.formatIndex.toByte()
        probe[3] = cfg.frameIndex.toByte()
        val interval = cfg.defaultInterval100ns
        probe[4] = (interval and 0xFF).toByte()
        probe[5] = ((interval shr 8) and 0xFF).toByte()
        probe[6] = ((interval shr 16) and 0xFF).toByte()
        probe[7] = ((interval shr 24) and 0xFF).toByte()

        val setProbe = connection.controlTransfer(0x21, 0x01, 0x0100, interfaceNum, probe, probe.size, 1000)
        if (setProbe < 0) return false
        val getProbe = connection.controlTransfer(0xA1, 0x81, 0x0100, interfaceNum, probe, probe.size, 1000)
        if (getProbe < 0) return false
        val setCommit = connection.controlTransfer(0x21, 0x01, 0x0200, interfaceNum, probe, probe.size, 1000)
        return setCommit >= 0
    }

    private fun decodeAndPublishUvcFrame(
        data: ByteArray,
        cfg: UvcStreamConfig,
        deviceName: String,
        formats: List<VideoFormatSpec>
    ) {
        val t0 = System.nanoTime()
        val bmp = if (cfg.pixelFormat.equals("MJPEG", ignoreCase = true)) {
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } else {
            yuy2ToBitmap(data, cfg.width, cfg.height)
        }
        if (bmp != null) {
            _latestFrame.value = bmp
            updateFpsAndSignal(
                sourceType = VideoSourceType.UVC_USB_BULK,
                deviceName = deviceName,
                width = bmp.width,
                height = bmp.height,
                pixelFormat = cfg.pixelFormat,
                latencyMs = (System.nanoTime() - t0) / 1_000_000L,
                supportedFormats = formats
            )
        }
    }

    private fun updateFpsAndSignal(
        sourceType: VideoSourceType,
        deviceName: String,
        width: Int,
        height: Int,
        pixelFormat: String,
        latencyMs: Long,
        supportedFormats: List<VideoFormatSpec>
    ) {
        frameCounter++
        val now = System.currentTimeMillis()
        val elapsed = now - fpsWindowStartMs
        if (elapsed >= 1000L) {
            measuredFps = (frameCounter * 1000f) / elapsed.coerceAtLeast(1L)
            frameCounter = 0
            fpsWindowStartMs = now
        }
        _signalState.value = VideoSignalState.Active(
            sourceType = sourceType,
            deviceName = deviceName,
            width = width,
            height = height,
            actualFps = if (measuredFps > 0f) measuredFps else 30f,
            pixelFormat = pixelFormat,
            latencyMs = latencyMs,
            supportedFormats = supportedFormats
        )
    }

    fun publishExternalBitmapFrame(
        bitmap: Bitmap,
        sourceType: VideoSourceType,
        deviceName: String,
        pixelFormat: String,
        latencyMs: Long
    ) {
        _latestFrame.value = bitmap
        updateFpsAndSignal(
            sourceType = sourceType,
            deviceName = deviceName,
            width = bitmap.width,
            height = bitmap.height,
            pixelFormat = pixelFormat,
            latencyMs = latencyMs,
            supportedFormats = emptyList()
        )
    }

    fun setSignalDisconnected(reason: String) {
        _signalState.value = VideoSignalState.Disconnected(reason)
    }

    fun stopCapture() {
        usbStreamJob?.cancel()
        usbStreamJob = null
        runCatching {
            usbClaimedInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()
        }
        usbClaimedInterface = null
        usbConnection = null

        runCatching { captureSession?.close() }
        captureSession = null
        runCatching { cameraDevice?.close() }
        cameraDevice = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { cameraThread?.quitSafely() }
        cameraThread = null
        cameraHandler = null
    }

    private fun yuy2ToBitmap(yuy2: ByteArray, width: Int, height: Int): Bitmap? {
        val expected = width * height * 2
        if (yuy2.size < expected || width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        var inIdx = 0
        var outIdx = 0
        while (inIdx + 3 < expected && outIdx + 1 < pixels.size) {
            val y0 = (yuy2[inIdx].toInt() and 0xFF) - 16
            val u = (yuy2[inIdx + 1].toInt() and 0xFF) - 128
            val y1 = (yuy2[inIdx + 2].toInt() and 0xFF) - 16
            val v = (yuy2[inIdx + 3].toInt() and 0xFF) - 128
            pixels[outIdx] = yuvToArgb(y0, u, v)
            pixels[outIdx + 1] = yuvToArgb(y1, u, v)
            inIdx += 4
            outIdx += 2
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun yuv420ToBitmap(image: Image): Bitmap? {
        val width = image.width
        val height = image.height
        val planes = image.planes
        if (planes.size < 3) return null
        val yBuf: ByteBuffer = planes[0].buffer
        val uBuf: ByteBuffer = planes[1].buffer
        val vBuf: ByteBuffer = planes[2].buffer
        val yRowStride = planes[0].rowStride
        val uvRowStride = planes[1].rowStride
        val uvPixelStride = planes[1].pixelStride

        val pixels = IntArray(width * height)
        for (row in 0 until height) {
            val yRowOffset = row * yRowStride
            val uvRowOffset = (row shr 1) * uvRowStride
            for (col in 0 until width) {
                val y = (yBuf.get(yRowOffset + col).toInt() and 0xFF) - 16
                val uvIdx = uvRowOffset + (col shr 1) * uvPixelStride
                val u = (uBuf.get(uvIdx).toInt() and 0xFF) - 128
                val v = (vBuf.get(uvIdx).toInt() and 0xFF) - 128
                pixels[row * width + col] = yuvToArgb(y, u, v)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun yuvToArgb(yIn: Int, u: Int, v: Int): Int {
        val y = if (yIn < 0) 0 else yIn
        val r = ((298 * y + 409 * v + 128) shr 8).coerceIn(0, 255)
        val g = ((298 * y - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
        val b = ((298 * y + 516 * u + 128) shr 8).coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
