package com.brickkiln.erp.util

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
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

class BeaconListener(
    private val settingsRepository: SettingsRepository,
    private val context: Context,
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

    suspend fun listenForServer(timeoutMs: Long = 15_000L): String? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        var multicastLock: WifiManager.MulticastLock? = null
        try {
            _status.value = BeaconStatus.Listening()

            // CRITICAL: Acquire MulticastLock so Android doesn't filter out UDP broadcast packets
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("brick-kiln-beacon")
            multicastLock.acquire()
            Log.i(TAG, "MulticastLock acquired")

            socket = DatagramSocket(BEACON_PORT)
            socket.soTimeout = 2000
            socket.broadcast = true

            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)

            val startTime = System.currentTimeMillis()
            Log.i(TAG, "Listening for beacon on UDP $BEACON_PORT (timeout=${timeoutMs}ms)")

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    socket.receive(packet)
                    val json = String(packet.data, 0, packet.length)
                    Log.d(TAG, "Received UDP: $json")

                    val payload = parseAnnouncement(json)
                    if (payload != null) {
                        val serverIp = payload.rpcHost ?: payload.allIps.firstOrNull()
                        if (serverIp != null) {
                            Log.i(TAG, "Found ERP server: $serverIp:${payload.rpcPort}")
                            val current = settingsRepository.settings.first()
                            settingsRepository.update(current.copy(
                                serverHost = serverIp,
                                serverPort = payload.rpcPort,
                            ))
                            _status.value = BeaconStatus.Found(serverIp, payload.machineName, payload.rpcPort)
                            return@withContext serverIp
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                }
            }

            Log.w(TAG, "No beacon received within ${timeoutMs}ms")
            _status.value = BeaconStatus.Failed("No ERP server found on Wi-Fi")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Beacon listen failed", e)
            _status.value = BeaconStatus.Failed(e.message ?: "Listener error")
            null
        } finally {
            socket?.close()
            if (multicastLock?.isHeld == true) {
                multicastLock.release()
                Log.i(TAG, "MulticastLock released")
            }
        }
    }

    private fun parseAnnouncement(json: String): Announcement? {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java) ?: return null
            if (obj.get("app")?.asString != "brick-kiln-erp") return null
            Announcement(
                version = obj.get("version")?.asString ?: "",
                machineName = obj.get("machineName")?.asString ?: "Server",
                rpcPort = obj.get("rpcPort")?.asInt ?: 8765,
                rpcHost = obj.get("rpcHost")?.takeIf { !it.isJsonNull }?.asString,
                allIps = obj.get("allIps")?.takeIf { it.isJsonArray }
                    ?.asJsonArray?.map { it.asString } ?: emptyList(),
            )
        } catch (e: Exception) {
            null
        }
    }

    private data class Announcement(
        val version: String,
        val machineName: String,
        val rpcPort: Int,
        val rpcHost: String?,
        val allIps: List<String>,
    )

    companion object {
        private const val TAG = "BeaconListener"
        const val BEACON_PORT = 8766
    }
}
