package com.example.recording

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.core.models.RecordingStatus
import com.example.data.local.RecordedMediaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaCaptureManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _recordingStatus = MutableStateFlow(
        RecordingStatus(availableStorageMb = queryAvailableStorageMb())
    )
    val recordingStatus: StateFlow<RecordingStatus> = _recordingStatus.asStateFlow()

    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var muxerStarted = false
    private var activeFile: File? = null
    private var timerJob: Job? = null
    private var encodeWidth = 1280
    private var encodeHeight = 720
    private var startTimeNs = 0L
    private var pausedAccumNs = 0L
    private var pauseStartNs = 0L

    private val screenshotsDir: File
        get() = File(context.filesDir, "screenshots").apply { mkdirs() }

    private val recordingsDir: File
        get() = File(context.filesDir, "recordings").apply { mkdirs() }

    fun queryAvailableStorageMb(): Long {
        return runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            (stat.availableBlocksLong * stat.blockSizeLong) / (1024L * 1024L)
        }.getOrDefault(0L)
    }

    suspend fun saveScreenshot(
        bitmap: Bitmap,
        cameraModel: String
    ): Result<RecordedMediaEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(screenshotsDir, "PM360_FRAME_$stamp.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            exportImageToMediaStore(file, "PM360_FRAME_$stamp.png")
            RecordedMediaEntity(
                title = "Frame Grab $stamp",
                filePath = file.absolutePath,
                mediaType = "SCREENSHOT_PNG",
                cameraModel = cameraModel,
                resolution = "${bitmap.width}x${bitmap.height}",
                durationSeconds = 0L,
                fileSizeBytes = file.length(),
                createdAtMs = System.currentTimeMillis()
            )
        }
    }

    fun startMp4Recording(width: Int, height: Int, withAudio: Boolean): Boolean {
        if (_recordingStatus.value.isRecording) return false
        val w = (width / 16 * 16).coerceIn(640, 1920)
        val h = (height / 16 * 16).coerceIn(360, 1080)
        encodeWidth = w
        encodeHeight = h

        return runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(recordingsDir, "PM360_HDMI_$stamp.mp4")
            activeFile = outFile

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            encoder = codec
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            videoTrackIndex = -1
            muxerStarted = false
            startTimeNs = System.nanoTime()
            pausedAccumNs = 0L

            _recordingStatus.value = RecordingStatus(
                isRecording = true,
                isPaused = false,
                elapsedSeconds = 0L,
                audioEnabled = withAudio,
                outputPath = outFile.absolutePath,
                availableStorageMb = queryAvailableStorageMb(),
                lastError = null
            )

            timerJob?.cancel()
            timerJob = scope.launch {
                while (isActive && _recordingStatus.value.isRecording) {
                    if (!_recordingStatus.value.isPaused) {
                        _recordingStatus.value = _recordingStatus.value.copy(
                            elapsedSeconds = _recordingStatus.value.elapsedSeconds + 1L,
                            availableStorageMb = queryAvailableStorageMb()
                        )
                    }
                    delay(1000L)
                }
            }
            true
        }.getOrElse { err ->
            _recordingStatus.value = _recordingStatus.value.copy(
                isRecording = false,
                lastError = err.message ?: "Hardware MP4 encoder unavailable"
            )
            cleanupCodec()
            false
        }
    }

    fun togglePause() {
        val cur = _recordingStatus.value
        if (!cur.isRecording) return
        if (!cur.isPaused) {
            pauseStartNs = System.nanoTime()
            _recordingStatus.value = cur.copy(isPaused = true)
        } else {
            pausedAccumNs += System.nanoTime() - pauseStartNs
            _recordingStatus.value = cur.copy(isPaused = false)
        }
    }

    fun feedVideoFrame(bitmap: Bitmap) {
        val status = _recordingStatus.value
        val codec = encoder ?: return
        if (!status.isRecording || status.isPaused) return

        runCatching {
            val inIndex = codec.dequeueInputBuffer(0)
            if (inIndex >= 0) {
                val inputBuf = codec.getInputBuffer(inIndex) ?: return
                inputBuf.clear()
                val yuv = bitmapToNv12(bitmap, encodeWidth, encodeHeight)
                val bytesToWrite = minOf(inputBuf.remaining(), yuv.size)
                inputBuf.put(yuv, 0, bytesToWrite)
                val ptsUs = ((System.nanoTime() - startTimeNs - pausedAccumNs) / 1000L).coerceAtLeast(0L)
                codec.queueInputBuffer(inIndex, 0, bytesToWrite, ptsUs, 0)
            }
            drainEncoder(codec, endOfStream = false)
        }
    }

    suspend fun stopMp4Recording(cameraModel: String): RecordedMediaEntity? = withContext(Dispatchers.IO) {
        val status = _recordingStatus.value
        if (!status.isRecording) return@withContext null
        timerJob?.cancel()

        val codec = encoder
        if (codec != null) {
            runCatching {
                val inIndex = codec.dequeueInputBuffer(5000)
                if (inIndex >= 0) {
                    val ptsUs = ((System.nanoTime() - startTimeNs - pausedAccumNs) / 1000L).coerceAtLeast(0L)
                    codec.queueInputBuffer(inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                drainEncoder(codec, endOfStream = true)
            }
        }

        val outFile = activeFile
        val duration = status.elapsedSeconds
        cleanupCodec()
        _recordingStatus.value = RecordingStatus(
            isRecording = false,
            availableStorageMb = queryAvailableStorageMb()
        )

        if (outFile != null && outFile.exists() && outFile.length() > 0L) {
            exportVideoToMediaStore(outFile, outFile.name)
            RecordedMediaEntity(
                title = outFile.nameWithoutExtension,
                filePath = outFile.absolutePath,
                mediaType = "VIDEO_MP4",
                cameraModel = cameraModel,
                resolution = "${encodeWidth}x${encodeHeight}",
                durationSeconds = duration,
                fileSizeBytes = outFile.length(),
                createdAtMs = System.currentTimeMillis()
            )
        } else null
    }

    fun buildShareIntent(media: RecordedMediaEntity): Intent? {
        val file = File(media.filePath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = if (media.mediaType == "VIDEO_MP4") "video/mp4" else "image/png"
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun drainEncoder(codec: MediaCodec, endOfStream: Boolean) {
        val info = MediaCodec.BufferInfo()
        val m = muxer ?: return
        var tries = 0
        while (tries < 20) {
            tries++
            val outIdx = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) break
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        videoTrackIndex = m.addTrack(codec.outputFormat)
                        m.start()
                        muxerStarted = true
                    }
                }
                outIdx >= 0 -> {
                    val encoded = codec.getOutputBuffer(outIdx)
                    if (encoded != null && info.size > 0 && muxerStarted && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        encoded.position(info.offset)
                        encoded.limit(info.offset + info.size)
                        m.writeSampleData(videoTrackIndex, encoded, info)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
    }

    private fun cleanupCodec() {
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        encoder = null
        if (muxerStarted) {
            runCatching { muxer?.stop() }
        }
        runCatching { muxer?.release() }
        muxer = null
        muxerStarted = false
        videoTrackIndex = -1
    }

    private fun exportImageToMediaStore(file: File, displayName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ProMonitor360")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { inp -> inp.copyTo(out) }
            }
        }
    }

    private fun exportVideoToMediaStore(file: File, displayName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ProMonitor360")
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { inp -> inp.copyTo(out) }
            }
        }
    }

    private fun bitmapToNv12(src: Bitmap, targetW: Int, targetH: Int): ByteArray {
        val scaled = if (src.width != targetW || src.height != targetH) {
            Bitmap.createScaledBitmap(src, targetW, targetH, false)
        } else src
        val argb = IntArray(targetW * targetH)
        scaled.getPixels(argb, 0, targetW, 0, 0, targetW, targetH)
        if (scaled !== src) scaled.recycle()

        val frameSize = targetW * targetH
        val yuv = ByteArray(frameSize + frameSize / 2)
        var yIdx = 0
        var uvIdx = frameSize

        for (j in 0 until targetH) {
            for (i in 0 until targetW) {
                val px = argb[j * targetW + i]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIdx++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0 && uvIdx + 1 < yuv.size) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIdx++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIdx++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}
