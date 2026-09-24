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
class ApiClient(private val settingsRepository: SettingsRepository) {

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
     *   1. LAN server if reachable
     *   2. Cloud relay if configured and reachable
     *   3. null if neither
     */
    suspend fun bestService(): ApiService? {
        if (checkLanReachable()) {
            return buildService(lanBaseUrl())
        }
        val cloud = cloudBaseUrl()
        if (cloud != null && checkCloudReachable()) {
            return buildService(cloud)
        }
        return null
    }

    /**
     * Generic RPC call — handles service selection automatically.
     * Falls back to cloud if LAN is unreachable.
     */
    suspend inline fun <reified T> callRpc(
        channel: String,
        args: Map<String, Any?>,
    ): Result<T> {
        val service = bestService()
            ?: return Result.failure(Exception("No server reachable. Check Wi-Fi connection or cloud relay settings."))
        val access = accessCode()
        return try {
            val req = RpcRequest(channel, args)
            val res = service.rpc<T>(req, access)
            if (res.ok && res.data != null) {
                Result.success(res.data)
            } else {
                Result.failure(Exception(res.error?.message ?: "Unknown server error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
