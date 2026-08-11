package com.rmfacilities.app.viewmodel

import androidx.lifecycle.ViewModel
import com.rmfacilities.app.data.model.ReportFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

class ReportsViewModel : ViewModel() {
    private val _filter = MutableStateFlow(
        ReportFilter(
            periodoInicio = LocalDate.now().minusDays(7),
            periodoFim = LocalDate.now(),
            posto = null,
            funcionario = null,
            supervisor = null,
            status = null
        )
    )
    val filter: StateFlow<ReportFilter> = _filter.asStateFlow()

    fun updateFilter(newFilter: ReportFilter) {
        _filter.value = newFilter
    }
}
