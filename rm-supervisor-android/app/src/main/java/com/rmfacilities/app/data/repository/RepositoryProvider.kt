package com.rmfacilities.app.data.repository

import com.rmfacilities.app.data.network.ApiConfig
import com.rmfacilities.app.data.session.SecureSessionStore

object RepositoryProvider {
    fun create(sessionStore: SecureSessionStore): OperationsRepository {
        return if (ApiConfig.useMockData) MockOperationsRepository() else ApiOperationsRepository(sessionStore)
    }
}
