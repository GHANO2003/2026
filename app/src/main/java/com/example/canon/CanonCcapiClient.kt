package com.example.canon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Official Canon Camera Control API (CCAPI) HTTP/REST client for Canon EOS 90D.
 */
class CanonCcapiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(4500, TimeUnit.MILLISECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private var baseUrl: String? = null
    private var apiVersionPath: String = "ver100"

    val isConnected: Boolean get() = baseUrl != null

    suspend fun connect(hostIp: String, port: Int = 8080): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val candidateBase = "http://$hostIp:$port"
            val req = Request.Builder().url("$candidateBase/ccapi").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("CCAPI HTTP ${resp.code} at $candidateBase/ccapi")
                }
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                apiVersionPath = when {
                    json.has("ver110") -> "ver110"
                    json.has("ver100") -> "ver100"
                    else -> "ver100"
                }
                baseUrl = candidateBase
                // Fetch device info to verify real Canon EOS 90D identity
                val infoReq = Request.Builder().url("$candidateBase/ccapi/$apiVersionPath/deviceinformation").get().build()
                client.newCall(infoReq).execute().use { infoResp ->
                    if (infoResp.isSuccessful) {
                        val infoJson = JSONObject(infoResp.body?.string().orEmpty())
                        infoJson.optString("productname", "Canon EOS Camera")
                    } else {
                        "Canon CCAPI Device"
                    }
                }
            }
        }
    }

    fun disconnect() {
        baseUrl = null
    }

    suspend fun getShootingSettings(): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val base = baseUrl ?: error("CCAPI not connected")
            val req = Request.Builder().url("$base/ccapi/$apiVersionPath/shooting/settings").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("CCAPI settings error HTTP ${resp.code}")
                JSONObject(resp.body?.string().orEmpty())
            }
        }
    }

    suspend fun putSetting(endpoint: String, value: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val base = baseUrl ?: error("CCAPI not connected")
            val payload = JSONObject().put("value", value).toString()
            val req = Request.Builder()
                .url("$base/ccapi/$apiVersionPath/shooting/settings/$endpoint")
                .put(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string().orEmpty()
                    error("CCAPI $endpoint rejected (${resp.code}): $errBody")
                }
            }
        }
    }

    suspend fun triggerShutter(af: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val base = baseUrl ?: error("CCAPI not connected")
            val payload = JSONObject().put("af", af).toString()
            val req = Request.Builder()
                .url("$base/ccapi/$apiVersionPath/shooting/control/shutterbutton")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Shutter command failed HTTP ${resp.code}")
            }
        }
    }

    suspend fun setMovieRecording(action: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val base = baseUrl ?: error("CCAPI not connected")
            val payload = JSONObject().put("action", action).toString()
            val req = Request.Builder()
                .url("$base/ccapi/$apiVersionPath/shooting/control/moviemode")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Movie mode '$action' failed HTTP ${resp.code}")
            }
        }
    }

    suspend fun startLiveView(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val base = baseUrl ?: error("CCAPI not connected")
            val payload = JSONObject()
                .put("liveviewsize", "medium")
                .put("cameradisplay", "on")
                .toString()
            val req = Request.Builder()
                .url("$base/ccapi/$apiVersionPath/shooting/liveview")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("CCAPI Live View start failed HTTP ${resp.code}")
            }
        }
    }

    suspend fun fetchLiveViewFrame(): Bitmap? = withContext(Dispatchers.IO) {
        val base = baseUrl ?: return@withContext null
        val req = Request.Builder().url("$base/ccapi/$apiVersionPath/shooting/liveview/flip").get().build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }
}
