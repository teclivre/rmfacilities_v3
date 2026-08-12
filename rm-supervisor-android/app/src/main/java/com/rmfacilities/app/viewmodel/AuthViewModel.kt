package com.rmfacilities.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rmfacilities.app.data.repository.OperationsRepository
import com.rmfacilities.app.data.session.SecureSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(
    private val repository: OperationsRepository,
    private val sessionStore: SecureSessionStore
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(isAuthenticated = !sessionStore.getToken().isNullOrBlank())
    }

    fun login(email: String, senha: String) {
        _state.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            repository.login(email, senha)
                .onSuccess {
                    sessionStore.saveToken(it.token)
                    _state.value = AuthUiState(isAuthenticated = true)
                }
                .onFailure {
                    _state.value = AuthUiState(errorMessage = it.message ?: "Falha no login")
                }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun logout() {
        sessionStore.clear()
        _state.value = AuthUiState(isAuthenticated = false)
    }
}
