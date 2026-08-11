package com.rmfacilities.app

import android.app.Application
import com.rmfacilities.app.data.repository.RepositoryProvider
import com.rmfacilities.app.data.session.SecureSessionStore

class RMFacilitiesApp : Application() {
    val repository by lazy { RepositoryProvider.operationsRepository }
    val sessionStore by lazy { SecureSessionStore(this) }
}
