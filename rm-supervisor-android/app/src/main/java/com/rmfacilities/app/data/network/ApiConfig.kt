package com.rmfacilities.app.data.network

import com.rmfacilities.app.BuildConfig

object ApiConfig {
    val baseUrl: String = BuildConfig.API_BASE_URL
    val useMockData: Boolean = BuildConfig.USE_MOCK_DATA
}
