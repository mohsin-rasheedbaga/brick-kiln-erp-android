package com.brickkiln.erp.util

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import com.brickkiln.erp.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetAddress

/**
 * Auto-discovery: scans the local Wi-Fi subnet for the desktop ERP server.
 *
 * Strategy:
 *   1. Get the phone's Wi-Fi IP address (e.g., 192.168.1.42)
 *   2. Compute the subnet (192.168.1.0/24)
 *   3. Try http://<subnet>.1:8765/health, .2, .3, ..., .254 in parallel
 *   4. First server that responds with { ok: true } wins
 *
 * This means the user doesn't need to manually enter the server IP —
 * the app finds it automatically on first launch.
 *
 * Takes ~5-15 seconds on a typical home network (parallel pings).
 */
class ServerDiscovery(private val apiClient: ApiClient) {

    /**
     * Returns the IP address of the desktop ERP server if found, otherwise null.
     */
    suspend fun discover(): String? = withContext(Dispatchers.IO) {
        // First check the common router-issued IPs (1, 10, 100, 101, 102)
        // — these are often where the main PC is on home/small-office networks.
        val commonLastOctets = listOf(1, 2, 10, 50, 100, 101, 102, 103, 104, 105)

        // Get the phone's IP to determine the subnet
        val phoneIp = getPhoneIpAddress() ?: return@withContext null
        val parts = phoneIp.split(".")
        if (parts.size != 4) return@withContext null

        val subnet = "${parts[0]}.${parts[1]}.${parts[2]}"

        // Phase 1: try common IPs first (fast — usually the server is at .1 or .100)
        for (last in commonLastOctets) {
            val candidate = "$subnet.$last"
            if (checkHealth(candidate)) return@withContext candidate
        }

        // Phase 2: parallel scan of remaining IPs in subnet
        val allIps = (1..254).map { last -> "$subnet.$last" } - commonLastOctets.map { "$subnet.$it" }
        coroutineScope {
            val deferred = allIps.map { ip ->
                async(Dispatchers.IO) {
                    if (checkHealth(ip)) ip else null
                }
            }
            // Wait for all and return first non-null
            for (d in deferred) {
                val ip = d.await()
                if (ip != null) return@coroutineScope ip
            }
            null
        }
    }

    /**
     * Check if a given IP has the ERP server running on port 8765.
     * Uses OkHttp with a 3-second timeout. Returns true if the server
     * responds with {"ok":true,...} on /health.
     */
    private suspend fun checkHealth(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "http://$ip:8765/health"
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
            val req = okhttp3.Request.Builder()
                .url(url)
                .get()
                .header("Connection", "close")
                .build()
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: ""
            res.close()
            body.contains("\"ok\":true") || body.contains("\"ok\": true")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns an IP address of this phone on the Wi-Fi network, or null.
     */
    @Suppress("DEPRECATION")
    private fun getPhoneIpAddress(): String? {
        return try {
            // Try InetAddress first (works on newer Android)
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is InetAddress && addr.hostAddress?.contains(":") == false) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") ||
                            ip.startsWith("172.16.") || ip.startsWith("172.17.") ||
                            ip.startsWith("172.18.") || ip.startsWith("172.19.") ||
                            ip.startsWith("172.20.") || ip.startsWith("172.21.") ||
                            ip.startsWith("172.22.") || ip.startsWith("172.23.") ||
                            ip.startsWith("172.24.") || ip.startsWith("172.25.") ||
                            ip.startsWith("172.26.") || ip.startsWith("172.27.") ||
                            ip.startsWith("172.28.") || ip.startsWith("172.29.") ||
                            ip.startsWith("172.30.") || ip.startsWith("172.31.")) {
                            return ip
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
