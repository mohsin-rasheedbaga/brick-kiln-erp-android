package com.brickkiln.erp.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistent app settings stored in DataStore.
 *
 * Fields:
 *   - serverHost: LAN IP of the desktop ERP server (e.g., "192.168.1.10")
 *   - serverPort: TCP port (default 8765)
 *   - accessCode: shared secret for the LAN server (optional)
 *   - cloudUrl: cloud relay URL (optional — enables out-of-LAN usage)
 *
 * The settings screen exposes all four. The mobile app picks LAN if
 * reachable, otherwise falls back to cloud.
 */
data class ServerSettings(
    val serverHost: String = "",
    val serverPort: Int = 8765,
    val accessCode: String = "",
    val cloudUrl: String = "",
)

class SettingsRepository(private val context: Context) {

    private val KEY_HOST = stringPreferencesKey("server_host")
    private val KEY_PORT = stringPreferencesKey("server_port")
    private val KEY_ACCESS = stringPreferencesKey("access_code")
    private val KEY_CLOUD = stringPreferencesKey("cloud_url")

    val settings: Flow<ServerSettings> = context.dataStore.data.map { p ->
        ServerSettings(
            serverHost = p[KEY_HOST] ?: "",
            serverPort = (p[KEY_PORT]?.toIntOrNull() ?: 8765),
            accessCode = p[KEY_ACCESS] ?: "",
            cloudUrl = p[KEY_CLOUD] ?: "",
        )
    }

    suspend fun update(patch: ServerSettings) {
        context.dataStore.edit { p ->
            p[KEY_HOST] = patch.serverHost
            p[KEY_PORT] = patch.serverPort.toString()
            p[KEY_ACCESS] = patch.accessCode
            p[KEY_CLOUD] = patch.cloudUrl
        }
    }

    companion object {
        private val Context.dataStore by preferencesDataStore(name = "settings")
    }
}
