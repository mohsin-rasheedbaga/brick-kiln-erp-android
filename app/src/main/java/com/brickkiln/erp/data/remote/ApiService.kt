package com.brickkiln.erp.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {
    /**
     * Generic RPC endpoint — wraps every desktop IPC channel.
     * The body is { channel, args } and the response is { ok, data?, error? }.
     *
     * Use `rpc("auth:login", mapOf("username" to u, "password" to p), null)`.
     */
    @POST("rpc")
    suspend fun <T> rpc(
        @Body request: RpcRequest,
        @Header("X-Access-Code") accessCode: String? = null,
    ): RpcResponse<T>

    /**
     * Health check — used by the network monitor to verify the server is reachable.
     * No auth required.
     */
    @POST("health")
    suspend fun health(): HealthResponse

    data class HealthResponse(
        val ok: Boolean,
        val version: String? = null,
        val machineName: String? = null,
        val uptime: Double = 0.0,
    )
}
