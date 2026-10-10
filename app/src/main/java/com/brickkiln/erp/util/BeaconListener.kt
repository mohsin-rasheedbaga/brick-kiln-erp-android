package com.brickkiln.erp.util

import android.util.Log
import com.brickkiln.erp.data.remote.ApiClient
import com.brickkiln.erp.data.repository.SettingsRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Listens for UDP broadcast announcements from the desktop ERP server
 * and auto-saves the discovered server IP to settings.
 *
 * The desktop ERP (v2.9.2+) broadcasts a JSON packet every 2 seconds on
 * UDP port 8766 with this shape:
 *   {
 *     "app": "brick-kiln-erp",
 *     "type": "server-announcement",
 *     "version": "2.9.2",
 *     "machineName": "Office-PC",
 *     "rpcPort": 8765,
 *     "rpcHost": "192.168.1.10",
 *     "allIps": ["192.168.1.10", "192.168.1.11"],
 *     "accessCodeRequired": false,
 *     "timestamp": 1234567890
 *   }
 *
 * We pick rpcHost (the primary IP) and save it via SettingsRepository.
 * The LoginViewModel will then automatically connect — no manual entry needed.
 *
 * The listener runs in a background coroutine. It will keep listening
 * for ~10 seconds (or until a server is found), then stop. This avoids
 * draining battery when there's no server on the network.
 */
class BeaconListener(
    private val settingsRepository: SettingsRepository,
    private val apiClient: ApiClient,
) {

    private val gson = Gson()
    private val _status = MutableStateFlow<BeaconStatus>(BeaconStatus.Idle)
    val status: StateFlow<BeaconStatus> = _status.asStateFlow()

    sealed class BeaconStatus {
        object Idle : BeaconStatus()
        data class Listening(val message: String = "Listening for ERP server...") : BeaconStatus()
        data class Found(val serverIp: String, val machineName: String, val rpcPort: Int) : BeaconStatus()
        data class Failed(val reason: String) : BeaconStatus()
    }

    /**
     * Listen for UDP broadcast from the desktop ERP.
     * Returns the discovered server IP, or null if no server found within timeout.
     *
     * @param timeoutMs How long to listen (default 15 seconds = ~7 beacon cycles)
     */
    suspend fun listenForServer(timeoutMs: Long = 15_000L): String? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            _status.value = BeaconStatus.Listening()

            socket = DatagramSocket(BEACON_PORT)
            socket.soTimeout = 2000 // 2 second read timeout per attempt
            socket.broadcast = true

            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)

            val startTime = System.currentTimeMillis()
            Log.i(TAG, "Listening for ERP server beacon on UDP port $BEACON_PORT (timeout=${timeoutMs}ms)")

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    socket.receive(packet)
                    val json = String(packet.data, 0, packet.length)
                    Log.d(TAG, "Received UDP packet: $json")

                    val payload = parseAnnouncement(json)
                    if (payload != null) {
                        val serverIp = payload.rpcHost ?: payload.allIps.firstOrNull()
                        if (serverIp != null) {
                            Log.i(TAG, "Found ERP server: $serverIp:${payload.rpcPort} (${payload.machineName})")
                            // Save to settings
                            val current = settingsRepository.settings.first()
                            settingsRepository.update(current.copy(
                                serverHost = serverIp,
                                serverPort = payload.rpcPort,
                            ))
                            _status.value = BeaconStatus.Found(
                                serverIp = serverIp,
                                machineName = payload.machineName,
                                rpcPort = payload.rpcPort,
                            )
                            return@withContext serverIp
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Normal — no packet received in 2 seconds, loop and try again
                    continue
                }
            }

            // Timed out
            Log.w(TAG, "No ERP beacon received within ${timeoutMs}ms")
            _status.value = BeaconStatus.Failed("No ERP server found on this Wi-Fi within ${timeoutMs / 1000} seconds")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to listen for beacon", e)
            _status.value = BeaconStatus.Failed(e.message ?: "Listener error")
            null
        } finally {
            socket?.close()
        }
    }

    private fun parseAnnouncement(json: String): Announcement? {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java) ?: return null
            val app = obj.get("app")?.asString ?: return null
            if (app != "brick-kiln-erp") return null
            val type = obj.get("type")?.asString ?: return null
            if (type != "server-announcement") return null

            Announcement(
                version = obj.get("version")?.asString ?: "",
                machineName = obj.get("machineName")?.asString ?: "Server",
                rpcPort = obj.get("rpcPort")?.asInt ?: 8765,
                rpcHost = obj.get("rpcHost")?.takeIf { !it.isJsonNull }?.asString,
                allIps = obj.get("allIps")?.takeIf { it.isJsonArray }
                    ?.asJsonArray?.map { it.asString } ?: emptyList(),
                accessCodeRequired = obj.get("accessCodeRequired")?.asBoolean ?: false,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse announcement: $json", e)
            null
        }
    }

    private data class Announcement(
        val version: String,
        val machineName: String,
        val rpcPort: Int,
        val rpcHost: String?,
        val allIps: List<String>,
        val accessCodeRequired: Boolean,
    )

    companion object {
        private const val TAG = "BeaconListener"
        const val BEACON_PORT = 8766
    }
}
