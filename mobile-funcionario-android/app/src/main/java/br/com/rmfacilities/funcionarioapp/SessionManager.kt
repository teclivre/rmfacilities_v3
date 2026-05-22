package br.com.rmfacilities.funcionarioapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlin.math.max

class SessionManager(private val context: Context) {
    /** True quando o Keystore falhou; dados foram apagados e o app deve pedir novo login. */
    val keystoreFailed: Boolean
    private val prefs: SharedPreferences

    init {
        var failed = false
        val resolved: SharedPreferences = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "rm_funcionario_app",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Keystore inacessivel: apaga TODOS os dados sensiveis e usa prefs vazia.
            // Loga o erro como CRITICO via Logcat + telemetria local para diagnostico.
            Log.e(TAG, "Keystore/EncryptedSharedPreferences indisponivel - dados apagados, login forcado", e)
            try {
                TelemetryLogger.e(TAG, "keystore_failed: " + (e.message ?: e.javaClass.simpleName), e)
            } catch (_: Throwable) { /* nao deixar falha de telemetria quebrar init */ }
            context.getSharedPreferences("rm_funcionario_app", Context.MODE_PRIVATE)
                .edit().clear().apply()
            failed = true
            context.getSharedPreferences("rm_funcionario_app", Context.MODE_PRIVATE)
        }
        prefs = resolved
        keystoreFailed = failed
    }

    var apiBaseUrl: String
        get() = prefs.getString("api_base_url", BuildConfig.DEFAULT_API_BASE_URL) ?: BuildConfig.DEFAULT_API_BASE_URL
        set(value) {
            val normalized = value.trim().trimEnd('/')
            // Só aceita HTTPS para evitar transmissão de credenciais por HTTP
            if (normalized.startsWith("https://", ignoreCase = true)) {
                prefs.edit().putString("api_base_url", normalized).apply()
            }
            // URL não-HTTPS é silenciosamente ignorada; o valor anterior é mantido
        }

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

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications_enabled", true)
        set(value) = prefs.edit().putBoolean("notifications_enabled", value).apply()

    // Canal preferido para receber OTP: "whatsapp", "sms" ou "email"
    var canalOtp: String
        get() = prefs.getString("canal_otp", "whatsapp") ?: "whatsapp"
        set(value) = prefs.edit().putString("canal_otp", value).apply()

    var trustedDeviceUntil: Long
        get() = prefs.getLong("trusted_device_until", 0L)
        set(value) = prefs.edit().putLong("trusted_device_until", value).apply()

    var trustedDeviceLabel: String
        get() = prefs.getString("trusted_device_label", "") ?: ""
        set(value) = prefs.edit().putString("trusted_device_label", value).apply()

    var sessionIdleTimeoutMin: Int
        get() = max(1, prefs.getInt("session_idle_timeout_min", 15))
        set(value) = prefs.edit().putInt("session_idle_timeout_min", max(1, value)).apply()

    var lastActivityAt: Long
        get() = prefs.getLong("last_activity_at", 0L)
        set(value) = prefs.edit().putLong("last_activity_at", value).apply()

    fun clear() {
        val base = apiBaseUrl
        val bio = biometricEnabled
        val bioCpf = biometricCpf
        val notif = notificationsEnabled
        val canal = canalOtp
        val refresh = refreshToken
        val timeoutMin = sessionIdleTimeoutMin
        prefs.edit().clear().apply()
        apiBaseUrl = base
        biometricEnabled = bio
        biometricCpf = bioCpf
        notificationsEnabled = notif
        canalOtp = canal
        sessionIdleTimeoutMin = timeoutMin
        if (bio && bioCpf.isNotBlank() && refresh.isNotBlank()) {
            refreshToken = refresh
        }
    }

    fun markLoginSuccess(rememberDays: Int = 30, label: String = "") {
        lastActivityAt = System.currentTimeMillis()
        trustedDeviceUntil = System.currentTimeMillis() + (rememberDays.coerceAtLeast(1) * 86_400_000L)
        trustedDeviceLabel = label
    }

    fun touchActivity() {
        lastActivityAt = System.currentTimeMillis()
    }

    fun isTrustedDeviceValid(now: Long = System.currentTimeMillis()): Boolean {
        return trustedDeviceUntil > now
    }

    fun isIdleSessionExpired(now: Long = System.currentTimeMillis()): Boolean {
        if (accessToken.isBlank()) return false
        val last = lastActivityAt
        if (last <= 0L) return false
        val timeoutMs = sessionIdleTimeoutMin.toLong() * 60_000L
        return (now - last) > timeoutMs
    }

    fun revokeTrustedDevice() {
        trustedDeviceUntil = 0L
        trustedDeviceLabel = ""
    }

    fun logout() {
        clear()
        val intent = android.content.Intent(ACTION_LOGOUT).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "SessionManager"
        const val ACTION_LOGOUT = "br.com.rmfacilities.funcionarioapp.LOGOUT"
    }
}
