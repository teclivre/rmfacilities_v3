package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.os.Bundle
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfiguracoesActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient
    private var internalBiometricChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracoes)

        session = SessionManager(this)
        api = ApiClient(session)

        val switchBiometria = findViewById<SwitchMaterial>(R.id.switchBiometriaConfig)
        val switchNotificacoes = findViewById<SwitchMaterial>(R.id.switchNotificacoesConfig)
        val tvBiometriaStatus = findViewById<TextView>(R.id.tvBiometriaStatusConfig)
        val rgCanal = findViewById<RadioGroup>(R.id.rgCanalOtp)
        val tvCanalStatus = findViewById<TextView>(R.id.tvCanalOtpStatus)

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { finish() }

        internalBiometricChange = true
        switchBiometria.isChecked = session.biometricEnabled
        switchNotificacoes.isChecked = session.notificationsEnabled
        internalBiometricChange = false

        tvBiometriaStatus.text = if (session.biometricEnabled) "Biometria ativa" else "Biometria desativada"

        // Selecionar canal atual
        when (session.canalOtp) {
            "email" -> rgCanal.check(R.id.rbCanalEmail)
            else -> rgCanal.check(R.id.rbCanalWhatsapp)
        }

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

        rgCanal.setOnCheckedChangeListener { _, checkedId ->
            val canal = when (checkedId) {
                R.id.rbCanalEmail -> "email"
                else -> "whatsapp"
            }
            session.canalOtp = canal
            tvCanalStatus.text = "Salvando..."
            CoroutineScope(Dispatchers.IO).launch {
                val resp = try {
                    api.salvarPreferenciaCanalOtp(canal)
                } catch (e: Exception) {
                    null
                }
                withContext(Dispatchers.Main) {
                    if (resp?.ok == true) {
                        val label = mapOf("whatsapp" to "WhatsApp", "email" to "E-mail")
                        tvCanalStatus.text = "Preferência salva: ${label[canal]}"
                    } else {
                        tvCanalStatus.text = resp?.erro ?: "Salvo localmente (sincronize ao entrar)"
                    }
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnPoliticaPrivacidade).setOnClickListener {
            val base = (session.apiBaseUrl.ifBlank { BuildConfig.DEFAULT_API_BASE_URL }).trimEnd('/')
            startActivity(Intent(this, PrivacyPolicyActivity::class.java).apply {
                putExtra(PrivacyPolicyActivity.EXTRA_URL, "$base/politica-de-privacidade")
            })
        }

        findViewById<MaterialButton>(R.id.btnLimparOffline).setOnClickListener {
            OfflineDocsStore(this).clearAll()
            Toast.makeText(this, "Documentos offline limpos.", Toast.LENGTH_SHORT).show()
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
