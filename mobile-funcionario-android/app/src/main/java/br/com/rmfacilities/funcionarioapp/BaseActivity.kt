package br.com.rmfacilities.funcionarioapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
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
 * - Verificação de versão mínima do app em todas as Activities
 * - goLogin() centralizado
 *
 * Subclasses com sessão autenticada devem sobrescrever provideSession()
 * para fornecer a sessão autenticada.
 */
abstract class BaseActivity : AppCompatActivity() {

    private val baseSession by lazy { SessionManager(this) }
    private var loginRedirectInProgress = false

    /**
     * Retorna o SessionManager desta Activity, ou null se a Activity não
     * requer verificação de sessão (ex.: telas sem autenticação). Por padrão,
     * Activities que herdam desta classe usam a sessão do app.
     */
    open fun provideSession(): SessionManager? = baseSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeUtils.apply(this)
    }

    private val logoutReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            goLogin(i?.getBooleanExtra(SessionManager.EXTRA_SESSION_EXPIRED, false) == true)
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
        if (sess.accessToken.isBlank()) {
            goLogin()
            return
        }
        if (sess.isIdleSessionExpired()) {
            sess.logout(sessionExpired = true)
            goLogin(sessionExpired = true)
            return
        }
        sess.touchActivity()
        checkAppVersionIfNeeded(sess)
        // Envia logs pendentes assim que houver uma tela autenticada em primeiro plano,
        // em vez de esperar o app reiniciar do zero (evita logs presos na fila em memoria).
        TelemetryLogger.flush()
    }

    override fun onPause() {
        super.onPause()
        // Tenta esvaziar a fila antes de ir para segundo plano (o processo pode ser
        // encerrado pelo sistema enquanto em background, perdendo logs em memoria).
        TelemetryLogger.flush()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        provideSession()?.touchActivity()
    }

    private fun checkAppVersionIfNeeded(session: SessionManager) {
        if (appUpdateDialogShown || appVersionCheckInProgress || appVersionChecked) return
        appVersionCheckInProgress = true
        lifecycleScope.launch {
            try {
                val versao = withContext(Dispatchers.IO) {
                    ApiClient(session).getVersaoApp()
                }
                val atualizacaoDisponivel = versao.versao_atual > BuildConfig.VERSION_CODE
                val atualizacaoObrigatoria = versao.versao_minima > 0 && BuildConfig.VERSION_CODE < versao.versao_minima
                if (atualizacaoDisponivel || atualizacaoObrigatoria) {
                    appUpdateDialogShown = true
                    appVersionChecked = false
                    if (!isFinishing && !isDestroyed) {
                        showAppUpdateDialog(versao.download_url, atualizacaoObrigatoria)
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

    protected fun showAppUpdateDialog(downloadUrl: String?, obrigatoria: Boolean = false) {
        if (updateDialog?.isShowing == true) return
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Nova versão disponível")
            .setMessage("Há uma versão mais nova do app disponível. Deseja atualizar agora?")
            .setCancelable(!obrigatoria)
            .setPositiveButton("Atualizar") { _, _ ->
                val url = downloadUrl?.takeIf { it.isNotBlank() }
                    ?: "${provideSession()?.apiBaseUrl?.trimEnd('/')}/app/download"
                try {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                } catch (_: Exception) {
                }
            }
            .create()
        if (!obrigatoria) dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Agora não", null as android.content.DialogInterface.OnClickListener?)
        dialog.setOnDismissListener {
            appUpdateDialogShown = false
            updateDialog = null
        }
        updateDialog = dialog
        if (!isFinishing && !isDestroyed) dialog.show()
    }

    fun goLogin(sessionExpired: Boolean = false) {
        if (loginRedirectInProgress || isFinishing) return
        loginRedirectInProgress = true
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(SessionManager.EXTRA_SESSION_EXPIRED, sessionExpired)
        })
        finish()
    }
}
