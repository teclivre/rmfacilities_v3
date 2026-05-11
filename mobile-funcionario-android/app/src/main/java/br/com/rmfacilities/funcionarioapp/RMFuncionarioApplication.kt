package br.com.rmfacilities.funcionarioapp

import android.app.Application

class RMFuncionarioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppThemeManager.apply(this)
    }
}
