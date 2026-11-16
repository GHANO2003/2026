package com.example.nikon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Spec-compliant PTP/IP (ISO 15740 over TCP/IP port 15740) client for Nikon DSLR cameras
 * (Nikon D5600 Wi-Fi AP & Nikon D5500 WMU Wi-Fi AP).
 */
class PtpIpClient {
    companion object {
        const val DEFAULT_PORT = 15740
        private const val PKT_INIT_CMD_REQ = 0x00000001
        private const val PKT_INIT_CMD_ACK = 0x00000002
        private const val PKT_INIT_EVENT_REQ = 0x00000003
        private const val PKT_INIT_EVENT_ACK = 0x00000004
        private const val PKT_CMD_REQ = 0x00000006
        private const val PKT_CMD_RESP = 0x00000007
        private const val PKT_START_DATA = 0x00000009
        private const val PKT_DATA = 0x0000000A
        private const val PKT_END_DATA = 0x0000000C

        const val OP_GET_DEVICE_INFO = 0x1001
        const val OP_OPEN_SESSION = 0x1002
        const val OP_CLOSE_SESSION = 0x1003
        const val OP_INITIATE_CAPTURE = 0x100E
        const val OP_GET_DEVICE_PROP_VALUE = 0x1015
        const val OP_SET_DEVICE_PROP_VALUE = 0x1016
        const val OP_NIKON_START_LIVE_VIEW = 0x9201
        const val OP_NIKON_END_LIVE_VIEW = 0x9202
        const val OP_NIKON_GET_LIVE_VIEW_IMAGE = 0x9203

        const val RESP_OK = 0x2001
    }

    private var cmdSocket: Socket? = null
    private var eventSocket: Socket? = null
    private var cmdOut: DataOutputStream? = null
    private var cmdIn: DataInputStream? = null
    private var transactionId = 1

    val isConnected: Boolean
        get() = cmdSocket?.isConnected == true && cmdSocket?.isClosed == false

    suspend fun connect(hostIp: String, port: Int = DEFAULT_PORT, clientName: String = "ProMonitor360"): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                disconnect()
                val cmd = Socket()
                cmd.connect(InetSocketAddress(hostIp, port), 3500)
                cmd.soTimeout = 4000
                cmdSocket = cmd
                cmdOut = DataOutputStream(cmd.getOutputStream())
                cmdIn = DataInputStream(cmd.getInputStream())

                val guid = UUID.randomUUID()
                sendInitCommandRequest(guid, clientName)
                val (pktType, payload) = readPacket(cmdIn!!)
                if (pktType != PKT_INIT_CMD_ACK || payload.size < 4) {
                    error("PTP/IP InitCommandRequest rejected (packet=0x${pktType.toString(16)})")
                }
                val connNumber = ByteBuffer.wrap(payload, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int

                val evt = Socket()
                evt.connect(InetSocketAddress(hostIp, port), 3500)
                evt.soTimeout = 4000
                eventSocket = evt
                val evtOut = DataOutputStream(evt.getOutputStream())
                val evtIn = DataInputStream(evt.getInputStream())
                sendInitEventRequest(evtOut, connNumber)
                val (evtType, _) = readPacket(evtIn)
                if (evtType != PKT_INIT_EVENT_ACK) {
                    error("PTP/IP InitEventRequest rejected")
                }

                // Open Session (sessionId = 1)
                val openResp = executeCommand(OP_OPEN_SESSION, intArrayOf(1))
                if (openResp.responseCode != RESP_OK && openResp.responseCode != 0x201E) {
                    error("PTP OpenSession failed with 0x${openResp.responseCode.toString(16)}")
                }
                connNumber
            }.onFailure {
                disconnect()
            }
        }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        if (isConnected) {
            runCatching { executeCommand(OP_CLOSE_SESSION, intArrayOf(1)) }
        }
        runCatching { cmdSocket?.close() }
        runCatching { eventSocket?.close() }
        cmdSocket = null
        eventSocket = null
        cmdOut = null
        cmdIn = null
    }

    data class PtpResponse(val responseCode: Int, val data: ByteArray = ByteArray(0))

    suspend fun executeCommand(
        opCode: Int,
        params: IntArray = IntArray(0),
        outgoingData: ByteArray? = null
    ): PtpResponse = withContext(Dispatchers.IO) {
        val out = cmdOut ?: error("PTP/IP not connected")
        val inp = cmdIn ?: error("PTP/IP not connected")
        val tx = transactionId++

        val dataPhaseInfo = if (outgoingData != null) 2 else 1
        val body = ByteBuffer.allocate(10 + params.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(dataPhaseInfo)
            putShort(opCode.toShort())
            putInt(tx)
            params.forEach { putInt(it) }
        }.array()
        writePacket(out, PKT_CMD_REQ, body)

        if (outgoingData != null) {
            val startBuf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(tx)
                putLong(outgoingData.size.toLong())
            }.array()
            writePacket(out, PKT_START_DATA, startBuf)
            val endBuf = ByteBuffer.allocate(4 + outgoingData.size).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(tx)
                put(outgoingData)
            }.array()
            writePacket(out, PKT_END_DATA, endBuf)
        }

        val dataAccumulator = java.io.ByteArrayOutputStream()
        while (true) {
            val (type, payload) = readPacket(inp)
            when (type) {
                PKT_START_DATA -> { /* length header */ }
                PKT_DATA, PKT_END_DATA -> {
                    if (payload.size > 4) {
                        dataAccumulator.write(payload, 4, payload.size - 4)
                    }
                }
                PKT_CMD_RESP -> {
                    val respCode = if (payload.size >= 2) {
                        ByteBuffer.wrap(payload, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                    } else 0
                    return@withContext PtpResponse(respCode, dataAccumulator.toByteArray())
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        PtpResponse(0)
    }

    suspend fun fetchLiveViewFrame(): Bitmap? {
        val resp = executeCommand(OP_NIKON_GET_LIVE_VIEW_IMAGE)
        if (resp.responseCode != RESP_OK || resp.data.size < 384) return null
        // Nikon Live View header is 384 bytes (D5500/D5600 EXPEED 4) followed by JPEG SOI 0xFFD8
        val jpegStart = findJpegSoi(resp.data)
        if (jpegStart < 0) return null
        return BitmapFactory.decodeByteArray(resp.data, jpegStart, resp.data.size - jpegStart)
    }

    private fun findJpegSoi(data: ByteArray): Int {
        for (i in 0 until data.size - 1) {
            if ((data[i].toInt() and 0xFF) == 0xFF && (data[i + 1].toInt() and 0xFF) == 0xD8) {
                return i
            }
        }
        return -1
    }

    private fun sendInitCommandRequest(guid: UUID, clientName: String) {
        val utf16Name = (clientName + "\u0000").toByteArray(Charsets.UTF_16LE)
        val payload = ByteBuffer.allocate(16 + utf16Name.size + 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(guid.mostSignificantBits)
            putLong(guid.leastSignificantBits)
            put(utf16Name)
            putInt(0x00010000) // protocol version 1.0
        }.array()
        writePacket(cmdOut!!, PKT_INIT_CMD_REQ, payload)
    }

    private fun sendInitEventRequest(out: DataOutputStream, connNumber: Int) {
        val payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(connNumber)
        }.array()
        writePacket(out, PKT_INIT_EVENT_REQ, payload)
    }

    private fun writePacket(out: DataOutputStream, type: Int, payload: ByteArray) {
        val packet = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(8 + payload.size)
            putInt(type)
            put(payload)
        }.array()
        out.write(packet)
        out.flush()
    }

    private fun readPacket(inp: DataInputStream): Pair<Int, ByteArray> {
        val header = ByteArray(8)
        inp.readFully(header)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val totalLen = bb.int
        val pktType = bb.int
        val payloadLen = (totalLen - 8).coerceAtLeast(0).coerceAtMost(8 * 1024 * 1024)
        val payload = ByteArray(payloadLen)
        if (payloadLen > 0) inp.readFully(payload)
        return pktType to payload
    }
}
