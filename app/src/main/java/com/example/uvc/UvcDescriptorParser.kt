package com.example.uvc

import com.example.core.models.VideoFormatSpec

data class UvcStreamConfig(
    val formatIndex: Int,
    val frameIndex: Int,
    val pixelFormat: String,
    val width: Int,
    val height: Int,
    val defaultInterval100ns: Int,
    val supportedFps: List<Int>
)

data class ParsedUvcDescriptors(
    val uvcVersion: String,
    val streamingInterfaceNumber: Int,
    val alternateSetting: Int,
    val bulkEndpointAddress: Int?,
    val bulkMaxPacketSize: Int,
    val hasIsochronousEndpoint: Boolean,
    val configs: List<UvcStreamConfig>,
    val formatSummaries: List<VideoFormatSpec>
)

object UvcDescriptorParser {
    private const val DESC_INTERFACE = 0x04
    private const val DESC_ENDPOINT = 0x05
    private const val DESC_CS_INTERFACE = 0x24

    private const val USB_CLASS_VIDEO = 0x0E
    private const val SUBCLASS_VIDEO_CONTROL = 0x01
    private const val SUBCLASS_VIDEO_STREAMING = 0x02

    private const val VC_HEADER = 0x01
    private const val VS_FORMAT_UNCOMPRESSED = 0x04
    private const val VS_FRAME_UNCOMPRESSED = 0x05
    private const val VS_FORMAT_MJPEG = 0x06
    private const val VS_FRAME_MJPEG = 0x07

    fun parse(rawDescriptors: ByteArray?): ParsedUvcDescriptors {
        if (rawDescriptors == null || rawDescriptors.isEmpty()) {
            return ParsedUvcDescriptors("Unknown", -1, 0, null, 0, false, emptyList(), emptyList())
        }

        var uvcVersion = "1.0"
        var currentInterfaceClass = -1
        var currentInterfaceSubClass = -1
        var currentInterfaceNumber = -1
        var currentAltSetting = 0
        var streamingInterfaceNumber = -1
        var bestAltSetting = 0
        var bulkEndpointAddress: Int? = null
        var bulkMaxPacketSize = 0
        var hasIsochronous = false

        var activeFormatIndex = 1
        var activePixelFormat = "MJPEG"

        val configs = mutableListOf<UvcStreamConfig>()

        var offset = 0
        while (offset + 2 <= rawDescriptors.size) {
            val length = rawDescriptors[offset].toInt() and 0xFF
            if (length < 2 || offset + length > rawDescriptors.size) break
            val type = rawDescriptors[offset + 1].toInt() and 0xFF

            when (type) {
                DESC_INTERFACE -> if (length >= 9) {
                    currentInterfaceNumber = rawDescriptors[offset + 2].toInt() and 0xFF
                    currentAltSetting = rawDescriptors[offset + 3].toInt() and 0xFF
                    currentInterfaceClass = rawDescriptors[offset + 5].toInt() and 0xFF
                    currentInterfaceSubClass = rawDescriptors[offset + 6].toInt() and 0xFF
                    if (currentInterfaceClass == USB_CLASS_VIDEO && currentInterfaceSubClass == SUBCLASS_VIDEO_STREAMING) {
                        if (streamingInterfaceNumber == -1) streamingInterfaceNumber = currentInterfaceNumber
                    }
                }

                DESC_ENDPOINT -> if (length >= 7 && currentInterfaceClass == USB_CLASS_VIDEO && currentInterfaceSubClass == SUBCLASS_VIDEO_STREAMING) {
                    val epAddr = rawDescriptors[offset + 2].toInt() and 0xFF
                    val attributes = rawDescriptors[offset + 3].toInt() and 0x03
                    val maxPacket = ((rawDescriptors[offset + 4].toInt() and 0xFF) or
                        ((rawDescriptors[offset + 5].toInt() and 0xFF) shl 8)) and 0x07FF
                    val isIn = (epAddr and 0x80) != 0
                    if (isIn && attributes == 0x02) { // Bulk IN
                        bulkEndpointAddress = epAddr
                        bulkMaxPacketSize = maxPacket
                        streamingInterfaceNumber = currentInterfaceNumber
                        bestAltSetting = currentAltSetting
                    } else if (isIn && attributes == 0x01) { // Isochronous IN
                        hasIsochronous = true
                    }
                }

                DESC_CS_INTERFACE -> if (length >= 3 && currentInterfaceClass == USB_CLASS_VIDEO) {
                    val subType = rawDescriptors[offset + 2].toInt() and 0xFF
                    if (currentInterfaceSubClass == SUBCLASS_VIDEO_CONTROL && subType == VC_HEADER && length >= 5) {
                        val bcdLow = rawDescriptors[offset + 3].toInt() and 0xFF
                        val bcdHigh = rawDescriptors[offset + 4].toInt() and 0xFF
                        uvcVersion = "${bcdHigh}.${bcdLow.toString(16).padStart(2, '0')}"
                    } else if (currentInterfaceSubClass == SUBCLASS_VIDEO_STREAMING) {
                        when (subType) {
                            VS_FORMAT_MJPEG -> if (length >= 4) {
                                activeFormatIndex = rawDescriptors[offset + 3].toInt() and 0xFF
                                activePixelFormat = "MJPEG"
                            }

                            VS_FORMAT_UNCOMPRESSED -> if (length >= 21) {
                                activeFormatIndex = rawDescriptors[offset + 3].toInt() and 0xFF
                                val fourCc = String(
                                    byteArrayOf(
                                        rawDescriptors[offset + 5],
                                        rawDescriptors[offset + 6],
                                        rawDescriptors[offset + 7],
                                        rawDescriptors[offset + 8]
                                    ),
                                    Charsets.US_ASCII
                                ).trim()
                                activePixelFormat = if (fourCc.isNotBlank()) fourCc else "YUY2"
                            }

                            VS_FRAME_MJPEG, VS_FRAME_UNCOMPRESSED -> if (length >= 26) {
                                val frameIndex = rawDescriptors[offset + 3].toInt() and 0xFF
                                val width = (rawDescriptors[offset + 5].toInt() and 0xFF) or
                                    ((rawDescriptors[offset + 6].toInt() and 0xFF) shl 8)
                                val height = (rawDescriptors[offset + 7].toInt() and 0xFF) or
                                    ((rawDescriptors[offset + 8].toInt() and 0xFF) shl 8)
                                val defaultInterval = readInt32Le(rawDescriptors, offset + 21)
                                val intervalType = rawDescriptors[offset + 25].toInt() and 0xFF

                                val fpsList = mutableListOf<Int>()
                                if (intervalType > 0) {
                                    var idx = offset + 26
                                    for (i in 0 until intervalType) {
                                        if (idx + 4 <= offset + length) {
                                            val interval = readInt32Le(rawDescriptors, idx)
                                            if (interval > 0) {
                                                fpsList.add((10_000_000.0 / interval).toInt().coerceIn(1, 240))
                                            }
                                            idx += 4
                                        }
                                    }
                                } else if (defaultInterval > 0) {
                                    fpsList.add((10_000_000.0 / defaultInterval).toInt().coerceIn(1, 240))
                                }

                                if (width > 0 && height > 0) {
                                    configs.add(
                                        UvcStreamConfig(
                                            formatIndex = activeFormatIndex,
                                            frameIndex = frameIndex,
                                            pixelFormat = activePixelFormat,
                                            width = width,
                                            height = height,
                                            defaultInterval100ns = if (defaultInterval > 0) defaultInterval else 333_333,
                                            supportedFps = fpsList.distinct().sortedDescending()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            offset += length
        }

        val summaries = configs.map {
            VideoFormatSpec(
                pixelFormat = it.pixelFormat,
                width = it.width,
                height = it.height,
                supportedFps = it.supportedFps
            )
        }

        return ParsedUvcDescriptors(
            uvcVersion = uvcVersion,
            streamingInterfaceNumber = streamingInterfaceNumber,
            alternateSetting = bestAltSetting,
            bulkEndpointAddress = bulkEndpointAddress,
            bulkMaxPacketSize = bulkMaxPacketSize,
            hasIsochronousEndpoint = hasIsochronous,
            configs = configs,
            formatSummaries = summaries
        )
    }

    private fun readInt32Le(buf: ByteArray, offset: Int): Int {
        if (offset + 4 > buf.size) return 0
        return (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }
}
