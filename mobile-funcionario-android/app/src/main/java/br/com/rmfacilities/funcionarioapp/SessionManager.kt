package br.com.rmfacilities.funcionarioapp

import android.content.Context

class SessionManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("rm_funcionario_app", Context.MODE_PRIVATE)

    var apiBaseUrl: String
        get() = prefs.getString("api_base_url", BuildConfig.DEFAULT_API_BASE_URL) ?: BuildConfig.DEFAULT_API_BASE_URL
        set(value) = prefs.edit().putString("api_base_url", value.trim().trimEnd('/')).apply()

    var accessToken: String
        get() = prefs.getString("access_token", "") ?: ""
        set(value) = prefs.edit().putString("access_token", value).apply()

    var refreshToken: String
        get() = prefs.getString("refresh_token", "") ?: ""
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean("biometric_enabled", false)
        set(value) = prefs.edit().putBoolean("biometric_enabled", value).apply()

    var biometricCpf: String
        get() = prefs.getString("biometric_cpf", "") ?: ""
        set(value) = prefs.edit().putString("biometric_cpf", value).apply()

    fun clear() {
        val base = apiBaseUrl
        val bio = biometricEnabled
        val bioCpf = biometricCpf
        val refresh = refreshToken
        prefs.edit().clear().apply()
        apiBaseUrl = base
        biometricEnabled = bio
        biometricCpf = bioCpf
        if (bio && bioCpf.isNotBlank() && refresh.isNotBlank()) {
            refreshToken = refresh
        }
    }

    fun logout() {
        clear()
        val intent = android.content.Intent(ACTION_LOGOUT).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    companion object {
        const val ACTION_LOGOUT = "br.com.rmfacilities.funcionarioapp.LOGOUT"
    }
}
