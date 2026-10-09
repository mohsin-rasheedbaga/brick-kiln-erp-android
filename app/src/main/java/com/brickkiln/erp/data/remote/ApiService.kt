package com.brickkiln.erp.data.remote

/**
 * NOTE: This interface is kept for reference only. We no longer use Retrofit
 * for RPC calls because generic type erasure causes issues with nested
 * generic types (List<WorkerDto> etc.). All RPC calls go through
 * ApiClient.callRpc() which uses raw OkHttp HTTP POST + manual Gson parsing.
 */
interface ApiService
