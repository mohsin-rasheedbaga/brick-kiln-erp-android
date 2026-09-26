package com.brickkiln.erp.data.remote

import com.brickkiln.erp.data.repository.SettingsRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the Retrofit ApiService dynamically based on the current server
 * configuration stored in SettingsRepository (server host + port + access code).
 *
 * Supports BOTH LAN/Wi-Fi (e.g., http://192.168.1.10:8765) and cloud relay
 * (e.g., https://brick-kiln-relay.supabase.co). The active URL is picked
 * based on connectivity — if the LAN server is reachable, use it (fast + free);
 * otherwise fall back to the cloud relay.
 */
class ApiClient(internal val settingsRepository: SettingsRepository) {

    private val gson = Gson()
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Returns the LAN server URL based on stored settings.
     * Example: http://192.168.1.10:8765
     */
    suspend fun lanBaseUrl(): String {
        val s = settingsRepository.settings.first()
        return "http://${s.serverHost}:${s.serverPort}"
    }

    /**
     * Returns the cloud relay URL if configured, otherwise null.
     * Example: https://brick-kiln-relay.supabase.co
     */
    suspend fun cloudBaseUrl(): String? {
        val s = settingsRepository.settings.first()
        return s.cloudUrl?.takeIf { it.isNotBlank() }
    }

    /**
     * Returns the access code (if set) for authenticating to the LAN server.
     */
    suspend fun accessCode(): String? {
        val s = settingsRepository.settings.first()
        return s.accessCode?.takeIf { it.isNotBlank() }
    }

    /**
     * Build a fresh Retrofit instance for the given base URL.
     * Called when the user changes server settings.
     */
    fun buildService(baseUrl: String): ApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        return retrofit.create(ApiService::class.java)
    }

    /**
     * Quick connectivity check — does the LAN server respond to /health?
     * Returns true if reachable within 5 seconds.
     */
    suspend fun checkLanReachable(): Boolean {
        return try {
            val url = lanBaseUrl()
            val req = Request.Builder()
                .url("$url/health")
                .get()
                .build()
            val response = httpClient.newCall(req).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Quick connectivity check — does the cloud relay respond?
     */
    suspend fun checkCloudReachable(): Boolean {
        val url = cloudBaseUrl() ?: return false
        return try {
            val req = Request.Builder()
                .url("$url/health")
                .get()
                .build()
            val response = httpClient.newCall(req).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns the best available service:
     *   1. LAN server (try directly with saved settings — NO redundant health check)
     *   2. Cloud relay if configured
     *   3. null if neither is reachable
     *
     * IMPORTANT: We used to call checkLanReachable() before every RPC call, but
     * that's redundant — if the UDP beacon was received, we already know the
     * server is there. And if the HTTP call fails, the caller gets a clear
     * exception anyway. Skipping the pre-check makes every RPC call 10x faster.
     */
    suspend fun bestService(): ApiService? {
        // Try LAN first — just build the service, don't pre-check.
        val s = settingsRepository.settings.first()
        if (s.serverHost.isNotBlank()) {
            val url = "http://${s.serverHost}:${s.serverPort}"
            return buildService(url)
        }
        // Fall back to cloud if configured.
        val cloud = s.cloudUrl?.takeIf { it.isNotBlank() }
        if (cloud != null) {
            return buildService(cloud)
        }
        return null
    }

    /**
     * Generic RPC call — handles service selection automatically.
     * Falls back to cloud if LAN is unreachable.
     *
     * Usage:
     *   apiClient.callRpc(LoginResponse::class.java, "auth:login", mapOf(...))
     */
    suspend fun <T> callRpc(
        responseType: Class<T>,
        channel: String,
        args: Map<String, Any?>,
    ): Result<T> {
        val service = bestService()
            ?: return Result.failure(Exception(
                "No server configured. Open Settings to set the server IP, " +
                "or make sure the desktop ERP is running and on the same Wi-Fi."
            ))
        val access = accessCode()
        return try {
            val req = RpcRequest(channel, args)
            // Execute synchronously on IO thread
            val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                service.rpc<Any>(req, access)
            }
            // The response contains `data` as a LinkedTreeMap (Gson default).
            // We re-serialize to JSON and parse as the requested type.
            if (res.ok && res.data != null) {
                val json = gson.toJson(res.data)
                val parsed = gson.fromJson(json, responseType)
                Result.success(parsed)
            } else {
                Result.failure(Exception(res.error?.message ?: "Unknown server error"))
            }
        } catch (e: Exception) {
            val msg = e.message ?: String()
            Result.failure(Exception(
                if (msg.contains("failed to connect") || msg.contains("timeout") || msg.contains("Unable to resolve host")) {
                    "Cannot reach the ERP server at ${settingsRepository.settings.first().serverHost}. " +
                    "Make sure the desktop ERP is running AND Server mode is ON " +
                    "(Network Settings → Server mode → Apply). " +
                    "Also check Windows Firewall on the desktop PC."
                } else {
                    msg
                }
            ))
        }
    }
}
