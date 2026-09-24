package com.brickkiln.erp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brickkiln.erp.data.remote.LoginResponse
import com.brickkiln.erp.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

class LoginViewModel(private val authRepo: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun updateUsername(v: String) { _state.update { it.copy(username = v, error = null) } }
    fun updatePassword(v: String) { _state.update { it.copy(password = v, error = null) } }

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
                // Fetch mobile context for caching
                authRepo.fetchMobileContext()
                _state.update { it.copy(loading = false, success = true) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Login failed") }
            }
        }
    }

    companion object {
        fun factory(authRepo: AuthRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LoginViewModel(authRepo) as T
        }
    }
}
