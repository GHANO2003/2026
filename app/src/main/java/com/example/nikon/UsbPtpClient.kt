package com.example.nikon

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Direct USB PTP (Still Image Class 6) bulk transfer client for Nikon & Canon cameras over USB-C OTG.
 */
class UsbPtpClient {
    companion object {
        private const val PTP_TYPE_COMMAND = 1
        private const val PTP_TYPE_DATA = 2
        private const val PTP_TYPE_RESPONSE = 3

        const val OP_OPEN_SESSION = 0x1002
        const val OP_CLOSE_SESSION = 0x1003
        const val OP_INITIATE_CAPTURE = 0x100E
        const val OP_GET_DEVICE_PROP_VALUE = 0x1015
        const val OP_SET_DEVICE_PROP_VALUE = 0x1016

        const val PROP_WHITE_BALANCE = 0x5005
        const val PROP_F_NUMBER = 0x5007
        const val PROP_EXPOSURE_TIME = 0x500D
        const val PROP_EXPOSURE_INDEX_ISO = 0x500F
        const val PROP_EXPOSURE_BIAS_COMP = 0x5010

        const val RESP_OK = 0x2001
    }

    private var connection: UsbDeviceConnection? = null
    private var claimedIntf: UsbInterface? = null
    private var bulkIn: UsbEndpoint? = null
    private var bulkOut: UsbEndpoint? = null
    private var transactionId = 1

    val isConnected: Boolean get() = connection != null && bulkIn != null && bulkOut != null

    suspend fun open(device: UsbDevice, conn: UsbDeviceConnection): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            close()
            val intf = (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE }
                ?: error("No USB Still Image (PTP) interface on ${device.productName ?: device.deviceName}")

            if (!conn.claimInterface(intf, true)) {
                error("Could not claim USB PTP interface")
            }

            var inEp: UsbEndpoint? = null
            var outEp: UsbEndpoint? = null
            for (i in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep
                    else if (ep.direction == UsbConstants.USB_DIR_OUT) outEp = ep
                }
            }
            if (inEp == null || outEp == null) {
                conn.releaseInterface(intf)
                error("Missing PTP bulk endpoints")
            }

            connection = conn
            claimedIntf = intf
            bulkIn = inEp
            bulkOut = outEp

            val openResp = sendCommand(OP_OPEN_SESSION, intArrayOf(1))
            if (openResp.first != RESP_OK && openResp.first != 0x201E) {
                error("USB PTP OpenSession failed: 0x${openResp.first.toString(16)}")
            }
        }.onFailure { close() }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        if (isConnected) {
            runCatching { sendCommand(OP_CLOSE_SESSION, intArrayOf(1)) }
        }
        runCatching {
            claimedIntf?.let { connection?.releaseInterface(it) }
            connection?.close()
        }
        claimedIntf = null
        connection = null
        bulkIn = null
        bulkOut = null
    }

    suspend fun sendCommand(
        opCode: Int,
        params: IntArray = IntArray(0),
        dataOut: ByteArray? = null
    ): Pair<Int, ByteArray> = withContext(Dispatchers.IO) {
        val conn = connection ?: error("USB PTP not connected")
        val epOut = bulkOut ?: error("No bulk OUT")
        val epIn = bulkIn ?: error("No bulk IN")
        val tx = transactionId++

        val cmdLen = 12 + params.size * 4
        val cmdBuf = ByteBuffer.allocate(cmdLen).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(cmdLen)
            putShort(PTP_TYPE_COMMAND.toShort())
            putShort(opCode.toShort())
            putInt(tx)
            params.forEach { putInt(it) }
        }.array()
        val sent = conn.bulkTransfer(epOut, cmdBuf, cmdBuf.size, 2500)
        if (sent < 0) error("USB PTP command write failed")

        if (dataOut != null) {
            val dataPktLen = 12 + dataOut.size
            val dataBuf = ByteBuffer.allocate(dataPktLen).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(dataPktLen)
                putShort(PTP_TYPE_DATA.toShort())
                putShort(opCode.toShort())
                putInt(tx)
                put(dataOut)
            }.array()
            val dSent = conn.bulkTransfer(epOut, dataBuf, dataBuf.size, 2500)
            if (dSent < 0) error("USB PTP data write failed")
        }

        val readBuf = ByteArray(maxOf(epIn.maxPacketSize * 8, 4096))
        val payloadOut = ByteArrayOutputStream()
        while (true) {
            val read = conn.bulkTransfer(epIn, readBuf, readBuf.size, 3500)
            if (read < 12) error("USB PTP read timeout or disconnect")
            val bb = ByteBuffer.wrap(readBuf, 0, read).order(ByteOrder.LITTLE_ENDIAN)
            val containerLen = bb.int
            val containerType = bb.short.toInt() and 0xFFFF
            val code = bb.short.toInt() and 0xFFFF
            bb.int // transactionId
            if (containerType == PTP_TYPE_DATA) {
                payloadOut.write(readBuf, 12, read - 12)
                var remaining = containerLen - read
                while (remaining > 0) {
                    val chunk = conn.bulkTransfer(epIn, readBuf, readBuf.size, 3500)
                    if (chunk <= 0) break
                    payloadOut.write(readBuf, 0, chunk)
                    remaining -= chunk
                }
            } else if (containerType == PTP_TYPE_RESPONSE) {
                return@withContext code to payloadOut.toByteArray()
            }
        }
        @Suppress("UNREACHABLE_CODE")
        0 to ByteArray(0)
    }
}
