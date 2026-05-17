package br.com.rmfacilities.funcionarioapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.messaging.FirebaseMessaging
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { finish() }

        val session = SessionManager(this)

        // Versão nome
        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE
        findViewById<TextView>(R.id.tvVersao).text = "Versão $versionName"
        findViewById<TextView>(R.id.tvBuild).text = "$versionCode"

        // API URL (ofusca o domínio para segurança)
        val apiUrl = session.apiBaseUrl.let {
            if (it.length > 30) it.take(30) + "…" else it
        }
        findViewById<TextView>(R.id.tvApiUrl).text = apiUrl

        // Última sincronização
        val prefs = getSharedPreferences("rm_funcionario_app", MODE_PRIVATE)
        val lastSync = prefs.getLong("last_sync_ts", 0L)
        val syncText = if (lastSync > 0L) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(lastSync))
        } else {
            "Nunca"
        }
        findViewById<TextView>(R.id.tvUltimaSync).text = syncText
        findViewById<TextView>(R.id.tvFilaPendente).text = ActionRetryQueue(this).pendingCount().toString()
        findViewById<TextView>(R.id.tvUltimoErro).text = TelemetryLogger.lastError(this)

        // Push token status
        val tvPush = findViewById<TextView>(R.id.tvPushStatus)
        tvPush.text = "Verificando…"
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    tvPush.text = if (!token.isNullOrBlank()) "✅ Ativo" else "⚠️ Não registrado"
                }
                .addOnFailureListener {
                    tvPush.text = "❌ Indisponível"
                }
        } catch (_: Exception) {
            tvPush.text = "❌ Firebase não configurado"
        }
    }
}
