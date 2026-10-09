package com.brickkiln.erp.data.remote

import android.util.Log
import com.brickkiln.erp.data.repository.SettingsRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * API Client — connects to the desktop ERP server via HTTP.
 *
 * Uses raw OkHttp HTTP POST (NOT Retrofit) for all RPC calls.
 * This avoids generic type erasure issues that caused workers data
 * to come through as null in previous versions.
 */
class ApiClient(val settingsRepository: SettingsRepository) {

    private val gson = Gson()
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun accessCode(): String? {
        val s = settingsRepository.settings.first()
        return s.accessCode?.takeIf { it.isNotBlank() }
    }

    /**
     * Generic RPC call — uses raw OkHttp HTTP POST.
     *
     * Flow:
     *   1. Build JSON body: { "channel": "...", "args": {...} }
     *   2. POST to http://<serverHost>:<serverPort>/rpc
     *   3. Parse full response as RpcResponse (data = JsonElement)
     *   4. Re-serialize data field and parse as requested type
     *
     * This properly handles @SerializedName on nested objects like
     * List<WorkerDto> because we're doing full Gson deserialization.
     */
    suspend fun <T> callRpc(
        responseType: Class<T>,
        channel: String,
        args: Map<String, Any?>,
    ): Result<T> {
        val s = settingsRepository.settings.first()
        if (s.serverHost.isBlank()) {
            return Result.failure(Exception(
                "No server configured. Make sure the desktop ERP is running " +
                "and on the same Wi-Fi. Tap 'Re-scan' to find it."
            ))
        }

        val baseUrl = "http://${s.serverHost}:${s.serverPort}"
        val TAG = "ApiClient"

        return try {
            withContext(Dispatchers.IO) {
                // Build the RPC request body
                val requestBody = gson.toJson(mapOf(
                    "channel" to channel,
                    "args" to args,
                ))

                Log.d(TAG, "POST $baseUrl/rpc — channel=$channel")

                // Build the HTTP request
                val reqBuilder = Request.Builder()
                    .url("$baseUrl/rpc")
                    .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                if (s.accessCode.isNotBlank()) {
                    reqBuilder.addHeader("X-Access-Code", s.accessCode)
                }

                val response = httpClient.newCall(reqBuilder.build()).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP ${response.code}: ${responseBody?.take(200)}")
                    Result.failure(Exception("Server returned ${response.code}: ${responseBody?.take(200)}"))
                } else if (responseBody.isNullOrBlank()) {
                    Log.e(TAG, "Empty response body")
                    Result.failure(Exception("Empty response from server"))
                } else {
                    // Parse the full RPC response envelope
                    val rpcResponse = gson.fromJson(responseBody, RpcResponse::class.java)
                    if (rpcResponse.ok && rpcResponse.data != null) {
                        // Re-serialize just the `data` field and parse as the requested type
                        val dataJson = gson.toJson(rpcResponse.data)
                        Log.d(TAG, "Response data: ${dataJson.take(300)}")
                        val parsed = gson.fromJson(dataJson, responseType)
                        if (parsed != null) {
                            Result.success(parsed)
                        } else {
                            Log.e(TAG, "Failed to parse as ${responseType.simpleName}: ${dataJson.take(200)}")
                            Result.failure(Exception("Failed to parse server response as ${responseType.simpleName}"))
                        }
                    } else {
                        Log.e(TAG, "RPC error: ${rpcResponse.error?.message}")
                        Result.failure(Exception(rpcResponse.error?.message ?: "Unknown server error"))
                    }
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            Log.e(TAG, "callRpc exception: $msg")
            Result.failure(Exception(
                if (msg.contains("failed to connect") || msg.contains("timeout") || msg.contains("Unable to resolve host")) {
                    "Cannot reach ERP server at ${s.serverHost}:${s.serverPort}. " +
                    "Make sure desktop ERP is running with Server mode ON."
                } else {
                    msg
                }
            ))
        }
    }
}
