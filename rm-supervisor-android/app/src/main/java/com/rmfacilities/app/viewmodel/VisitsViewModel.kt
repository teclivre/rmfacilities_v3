package com.rmfacilities.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rmfacilities.app.data.model.Visita
import com.rmfacilities.app.data.repository.OperationsRepository
import com.rmfacilities.app.ui.state.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VisitsViewModel(private val repository: OperationsRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<Visita>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Visita>>> = _state.asStateFlow()

    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val saveState: StateFlow<UiState<Unit>> = _saveState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            repository.getVisitas()
                .onSuccess { _state.value = if (it.isEmpty()) UiState.Empty else UiState.Success(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Erro ao carregar visitas") }
        }
    }

    fun salvar(visita: Visita) {
        _saveState.value = UiState.Loading
        viewModelScope.launch {
            repository.salvarVisita(visita)
                .onSuccess {
                    _saveState.value = UiState.Success(Unit)
                    load()
                }
                .onFailure { _saveState.value = UiState.Error(it.message ?: "Erro ao salvar visita") }
        }
    }
}
