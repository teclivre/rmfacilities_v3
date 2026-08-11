package com.rmfacilities.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rmfacilities.app.data.model.Tarefa
import com.rmfacilities.app.data.repository.OperationsRepository
import com.rmfacilities.app.ui.state.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TasksViewModel(private val repository: OperationsRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<Tarefa>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Tarefa>>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            repository.getTarefas()
                .onSuccess { _state.value = if (it.isEmpty()) UiState.Empty else UiState.Success(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Erro ao carregar tarefas") }
        }
    }

    fun marcarConcluida(id: String) {
        viewModelScope.launch {
            repository.marcarTarefaConcluida(id)
            load()
        }
    }
}
