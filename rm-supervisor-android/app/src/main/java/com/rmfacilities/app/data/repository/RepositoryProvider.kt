package com.rmfacilities.app.data.repository

import com.rmfacilities.app.data.network.ApiConfig

object RepositoryProvider {
    val operationsRepository: OperationsRepository by lazy {
        if (ApiConfig.useMockData) MockOperationsRepository() else ApiOperationsRepository()
    }
}
