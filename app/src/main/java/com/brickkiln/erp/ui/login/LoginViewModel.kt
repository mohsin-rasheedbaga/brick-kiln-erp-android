package com.brickkiln.erp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brickkiln.erp.data.remote.ApiClient
import com.brickkiln.erp.data.repository.AuthRepository
import com.brickkiln.erp.data.repository.ServerSettings
import com.brickkiln.erp.data.repository.SettingsRepository
import com.brickkiln.erp.util.BeaconListener
import com.brickkiln.erp.util.ServerDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val discovering: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val serverIp: String? = null,
    val discoveryMessage: String? = null,
    val serverMachineName: String? = null,
)

class LoginViewModel(
    private val authRepo: AuthRepository,
    private val settingsRepo: SettingsRepository,
    private val apiClient: ApiClient,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        // On first launch, if no server is configured, try auto-discovery.
        // Two-stage discovery:
        //   1. UDP beacon listener (fast — 2-15 seconds, server finds us)
        //   2. TCP subnet scan fallback (slower — 15-30 seconds, we find server)
        viewModelScope.launch {
            try {
                val s = settingsRepo.settings.first()
                if (s.serverHost.isBlank()) {
                    discoverServer()
                } else {
                    // Server IP already saved — show it
                    _state.update {
                        it.copy(
                            serverIp = s.serverHost,
                            discoveryMessage = "✓ Will connect to ${s.serverHost}:${s.serverPort}",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(discoveryMessage = "Open Settings → enter server IP manually.")
                }
            }
        }
    }

    fun updateUsername(v: String) { _state.update { it.copy(username = v, error = null) } }
    fun updatePassword(v: String) { _state.update { it.copy(password = v, error = null) } }

    /**
     * Two-stage auto-discovery:
     *   Stage 1: Listen for UDP beacon broadcast from desktop ERP (fast path, ~2-15s)
     *   Stage 2: If beacon not received, fall back to TCP subnet scan (~15-30s)
     */
    fun discoverServer() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    discovering = true,
                    discoveryMessage = "Searching for ERP server on Wi-Fi...",
                    serverIp = null,
                )
            }

            // Stage 1: UDP beacon listener (preferred — fast and reliable)
            try {
                val beaconListener = BeaconListener(settingsRepo, apiClient)
                val beaconIp = withContext(Dispatchers.IO) {
                    beaconListener.listenForServer(timeoutMs = 15_000L)
                }
                if (beaconIp != null) {
                    val s = settingsRepo.settings.first()
                    _state.update {
                        it.copy(
                            discovering = false,
                            serverIp = beaconIp,
                            serverMachineName = (beaconListener.status.value as? BeaconListener.BeaconStatus.Found)?.machineName,
                            discoveryMessage = "✓ Found ERP server at $beaconIp — ready to login",
                        )
                    }
                    return@launch
                }
            } catch (e: Exception) {
                // Fall through to TCP scan
            }

            // Stage 2: TCP subnet scan fallback
            _state.update { it.copy(discoveryMessage = "Trying subnet scan (slower)...") }
            try {
                val discovery = ServerDiscovery(apiClient)
                val ip = withContext(Dispatchers.IO) { discovery.discover() }
                if (ip != null) {
                    val current = settingsRepo.settings.first()
                    settingsRepo.update(current.copy(serverHost = ip))
                    _state.update {
                        it.copy(
                            discovering = false,
                            serverIp = ip,
                            discoveryMessage = "✓ Found server at $ip",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            discovering = false,
                            discoveryMessage = "✗ No ERP server found on Wi-Fi.\nMake sure desktop ERP is running in Server mode.",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        discovering = false,
                        discoveryMessage = "✗ Discovery failed: ${e.message}",
                    )
                }
            }
        }
    }

    fun login() {
        val s = _state.value
        if (s.username.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "Username and password required.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = authRepo.login(s.username.trim(), s.password)
            result.onSuccess {
                authRepo.fetchMobileContext()
                _state.update { it.copy(loading = false, success = true) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Login failed") }
            }
        }
    }

    companion object {
        fun factory(
            authRepo: AuthRepository,
            settingsRepo: SettingsRepository,
            apiClient: ApiClient,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LoginViewModel(authRepo, settingsRepo, apiClient) as T
        }
    }
}
