package br.com.rmfacilities.funcionarioapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.appcompat.app.AppCompatActivity

/**
 * Classe base para todas as Activities autenticadas.
 * Fornece:
 * - Logout automático via BroadcastReceiver (ACTION_LOGOUT)
 * - Verificação de idle timeout em onResume
 * - goLogin() centralizado
 *
 * Subclasses com sessão autenticada devem sobrescrever provideSession()
 * para ativar a verificação de idle timeout.
 */
abstract class BaseActivity : AppCompatActivity() {

    /**
     * Retorna o SessionManager desta Activity, ou null se a Activity não
     * requer verificação de sessão (ex.: telas sem autenticação).
     */
    open fun provideSession(): SessionManager? = null

    private val logoutReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            goLogin()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(SessionManager.ACTION_LOGOUT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logoutReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logoutReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(logoutReceiver) } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        val sess = provideSession() ?: return
        if (sess.isIdleSessionExpired() && !sess.isTrustedDeviceValid()) {
            sess.clear()
            goLogin()
        }
    }

    fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
