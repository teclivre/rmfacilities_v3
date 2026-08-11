package com.rmfacilities.app.viewmodel

import androidx.lifecycle.ViewModel
import com.rmfacilities.app.data.session.SecureSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val sessionStore: SecureSessionStore) : ViewModel() {
    private val _notificationEnabled = MutableStateFlow(true)
    val notificationEnabled: StateFlow<Boolean> = _notificationEnabled.asStateFlow()

    fun toggleNotifications() {
        _notificationEnabled.value = \!_notificationEnabled.value
    }

    fun clearSession() {
        sessionStore.clear()
    }
}
