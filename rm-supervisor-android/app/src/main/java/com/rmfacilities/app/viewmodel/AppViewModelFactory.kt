package com.rmfacilities.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rmfacilities.app.RMFacilitiesApp

class AppViewModelFactory(private val app: RMFacilitiesApp) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(app.repository, app.sessionStore) as T
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(app.repository) as T
            modelClass.isAssignableFrom(EmployeesViewModel::class.java) -> EmployeesViewModel(app.repository) as T
            modelClass.isAssignableFrom(PostsViewModel::class.java) -> PostsViewModel(app.repository) as T
            modelClass.isAssignableFrom(VisitsViewModel::class.java) -> VisitsViewModel(app.repository) as T
            modelClass.isAssignableFrom(OccurrencesViewModel::class.java) -> OccurrencesViewModel(app.repository) as T
            modelClass.isAssignableFrom(TasksViewModel::class.java) -> TasksViewModel(app.repository) as T
            modelClass.isAssignableFrom(ReportsViewModel::class.java) -> ReportsViewModel() as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(app.sessionStore) as T
            else -> throw IllegalArgumentException("ViewModel não suportado: ${modelClass.name}")
        }
    }
}
