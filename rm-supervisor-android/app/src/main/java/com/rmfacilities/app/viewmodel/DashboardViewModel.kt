package com.rmfacilities.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rmfacilities.app.data.model.DashboardMetrics
import com.rmfacilities.app.data.model.DashboardResumo
import com.rmfacilities.app.data.repository.OperationsRepository
import com.rmfacilities.app.ui.state.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(private val repository: OperationsRepository) : ViewModel() {
    private val _metricsState = MutableStateFlow<UiState<DashboardMetrics>>(UiState.Loading)
    val metricsState: StateFlow<UiState<DashboardMetrics>> = _metricsState.asStateFlow()

    private val _resumoState = MutableStateFlow<UiState<DashboardResumo>>(UiState.Loading)
    val resumoState: StateFlow<UiState<DashboardResumo>> = _resumoState.asStateFlow()

    init {
        load()
    }

    fun load() {
        loadMetrics()
        loadResumo()
    }

    private fun loadMetrics() {
        _metricsState.value = UiState.Loading
        viewModelScope.launch {
            repository.getDashboardMetrics()
                .onSuccess { _metricsState.value = UiState.Success(it) }
                .onFailure { _metricsState.value = UiState.Error(it.message ?: "Erro ao carregar indicadores") }
        }
    }

    private fun loadResumo() {
        _resumoState.value = UiState.Loading
        viewModelScope.launch {
            repository.getDashboardResumo()
                .onSuccess { _resumoState.value = UiState.Success(it) }
                .onFailure { _resumoState.value = UiState.Error(it.message ?: "Erro ao carregar resumo") }
        }
    }
}
