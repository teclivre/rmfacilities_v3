package com.rmfacilities.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rmfacilities.app.data.model.Posto
import com.rmfacilities.app.data.repository.OperationsRepository
import com.rmfacilities.app.ui.state.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PostsViewModel(private val repository: OperationsRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<Posto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Posto>>> = _state.asStateFlow()

    private val _detailState = MutableStateFlow<UiState<Posto>>(UiState.Loading)
    val detailState: StateFlow<UiState<Posto>> = _detailState.asStateFlow()

    init {
        load()
    }

    fun load(query: String = "") {
        _state.value = UiState.Loading
        viewModelScope.launch {
            repository.getPostos(query)
                .onSuccess { _state.value = if (it.isEmpty()) UiState.Empty else UiState.Success(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Erro ao carregar postos") }
        }
    }

    fun loadDetail(id: String) {
        _detailState.value = UiState.Loading
        viewModelScope.launch {
            repository.getPostoById(id)
                .onSuccess { _detailState.value = UiState.Success(it) }
                .onFailure { _detailState.value = UiState.Error(it.message ?: "Erro ao carregar posto") }
        }
    }
}
