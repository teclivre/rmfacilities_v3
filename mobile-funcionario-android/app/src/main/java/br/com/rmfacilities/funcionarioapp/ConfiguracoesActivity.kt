package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class ConfiguracoesActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private var internalBiometricChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracoes)

        session = SessionManager(this)
        val switchBiometria = findViewById<SwitchMaterial>(R.id.switchBiometriaConfig)
        val switchNotificacoes = findViewById<SwitchMaterial>(R.id.switchNotificacoesConfig)
        val tvBiometriaStatus = findViewById<TextView>(R.id.tvBiometriaStatusConfig)

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { finish() }

        internalBiometricChange = true
        switchBiometria.isChecked = session.biometricEnabled
        switchNotificacoes.isChecked = session.notificationsEnabled
        internalBiometricChange = false

        tvBiometriaStatus.text = if (session.biometricEnabled) "Biometria ativa" else "Biometria desativada"

        switchBiometria.setOnCheckedChangeListener { _, checked ->
            if (internalBiometricChange) return@setOnCheckedChangeListener
            if (!checked) {
                session.biometricEnabled = false
                tvBiometriaStatus.text = "Biometria desativada"
                return@setOnCheckedChangeListener
            }
            ativarBiometriaComValidacao(switchBiometria, tvBiometriaStatus)
        }

        switchNotificacoes.setOnCheckedChangeListener { _, checked ->
            session.notificationsEnabled = checked
        }

        findViewById<MaterialButton>(R.id.btnPoliticaPrivacidade).setOnClickListener {
            val base = (session.apiBaseUrl.ifBlank { BuildConfig.DEFAULT_API_BASE_URL }).trimEnd('/')
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$base/politica-de-privacidade")))
        }

        findViewById<MaterialButton>(R.id.btnLimparOffline).setOnClickListener {
            OfflineDocsStore(this).clearAll()
        }
    }

    private fun canUseBiometric(): Boolean {
        val manager = BiometricManager.from(this)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun ativarBiometriaComValidacao(switchBiometria: SwitchMaterial, tvStatus: TextView) {
        if (!canUseBiometric()) {
            internalBiometricChange = true
            switchBiometria.isChecked = false
            internalBiometricChange = false
            tvStatus.text = "Biometria indisponível neste aparelho"
            return
        }

        val cpf = session.biometricCpf.trim()
        if (cpf.length != 11) {
            internalBiometricChange = true
            switchBiometria.isChecked = false
            internalBiometricChange = false
            tvStatus.text = "Faça login com CPF/código antes de ativar"
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                session.biometricEnabled = true
                tvStatus.text = "Biometria ativa"
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                internalBiometricChange = true
                switchBiometria.isChecked = false
                internalBiometricChange = false
                tvStatus.text = errString.toString()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Ativar biometria")
            .setSubtitle("Confirme sua digital para habilitar")
            .setNegativeButtonText("Cancelar")
            .build()
        prompt.authenticate(promptInfo)
    }
}
