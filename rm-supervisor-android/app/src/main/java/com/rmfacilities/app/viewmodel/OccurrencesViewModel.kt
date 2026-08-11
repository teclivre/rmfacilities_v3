package com.rmfacilities.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rmfacilities.app.data.model.Ocorrencia
import com.rmfacilities.app.data.model.OcorrenciaStatus
import com.rmfacilities.app.data.model.Prioridade
import com.rmfacilities.app.data.repository.OperationsRepository
import com.rmfacilities.app.ui.state.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OccurrencesViewModel(private val repository: OperationsRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<Ocorrencia>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Ocorrencia>>> = _state.asStateFlow()

    private val _statusFilter = MutableStateFlow<OcorrenciaStatus?>(null)
    val statusFilter: StateFlow<OcorrenciaStatus?> = _statusFilter.asStateFlow()

    private val _priorityFilter = MutableStateFlow<Prioridade?>(null)
    val priorityFilter: StateFlow<Prioridade?> = _priorityFilter.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            repository.getOcorrencias()
                .onSuccess { _state.value = if (it.isEmpty()) UiState.Empty else UiState.Success(applyFilters(it)) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Erro ao carregar ocorrências") }
        }
    }

    fun save(ocorrencia: Ocorrencia) {
        viewModelScope.launch {
            repository.salvarOcorrencia(ocorrencia)
            load()
        }
    }

    fun setStatusFilter(filter: OcorrenciaStatus?) {
        _statusFilter.value = filter
        reloadFiltered()
    }

    fun setPriorityFilter(filter: Prioridade?) {
        _priorityFilter.value = filter
        reloadFiltered()
    }

    private fun reloadFiltered() {
        val current = (_state.value as? UiState.Success)?.data ?: return
        _state.value = UiState.Success(applyFilters(current))
    }

    private fun applyFilters(items: List<Ocorrencia>): List<Ocorrencia> {
        return items.filter { item ->
            (_statusFilter.value == null || item.status == _statusFilter.value) &&
                (_priorityFilter.value == null || item.prioridade == _priorityFilter.value)
        }
    }
}
