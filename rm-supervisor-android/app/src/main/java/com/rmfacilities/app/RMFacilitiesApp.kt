package com.rmfacilities.app

import android.app.Application
import com.rmfacilities.app.data.repository.RepositoryProvider
import com.rmfacilities.app.data.session.SecureSessionStore

class RMFacilitiesApp : Application() {
    val sessionStore by lazy { SecureSessionStore(this) }
    val repository by lazy { RepositoryProvider.create(sessionStore) }
}
