package br.com.rmfacilities.funcionarioapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Classe base para todas as Activities autenticadas.
 * Fornece:
 * - Logout automático via BroadcastReceiver (ACTION_LOGOUT)
 * - Verificação de idle timeout em onResume
 * - Verificação de versão mínima do app em todas as Activities
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

    private var appUpdateDialogShown = false
    private var appVersionCheckInProgress = false
    private var appVersionChecked = false
    private var updateDialog: AlertDialog? = null

    override fun onResume() {
        super.onResume()
        val sess = provideSession() ?: return
        if (sess.isIdleSessionExpired() && !sess.isTrustedDeviceValid()) {
            sess.clear()
            goLogin()
            return
        }
        checkAppVersionIfNeeded(sess)
    }

    private fun checkAppVersionIfNeeded(session: SessionManager) {
        if (appUpdateDialogShown || appVersionCheckInProgress || appVersionChecked) return
        appVersionCheckInProgress = true
        lifecycleScope.launch {
            try {
                val versao = withContext(Dispatchers.IO) {
                    ApiClient(session).getVersaoApp()
                }
                if (versao.versao_minima > 0 && BuildConfig.VERSION_CODE < versao.versao_minima) {
                    appUpdateDialogShown = true
                    appVersionChecked = false
                    if (!isFinishing && !isDestroyed) {
                        showAppUpdateDialog(versao.download_url)
                    }
                } else {
                    appVersionChecked = true
                }
            } catch (_: Exception) {
                // Falha na verificação de versão: revalida em próximo onResume.
            } finally {
                appVersionCheckInProgress = false
            }
        }
    }

    protected fun showAppUpdateDialog(downloadUrl: String?) {
        if (updateDialog?.isShowing == true) return
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Atualização necessária")
            .setMessage("Há uma versão mais nova do app disponível. Por favor, atualize para continuar usando.")
            .setCancelable(false)
            .setPositiveButton("Atualizar") { _, _ ->
                val url = downloadUrl?.takeIf { it.isNotBlank() }
                    ?: "${provideSession()?.apiBaseUrl?.trimEnd('/')}/app/download"
                try {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                } catch (_: Exception) {
                }
            }
            .create()
        dialog.setOnDismissListener {
            appUpdateDialogShown = false
            updateDialog = null
        }
        updateDialog = dialog
        if (!isFinishing && !isDestroyed) dialog.show()
    }

    fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
