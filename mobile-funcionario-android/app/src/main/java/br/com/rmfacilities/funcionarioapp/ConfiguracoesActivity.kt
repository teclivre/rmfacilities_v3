package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
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
import androidx.lifecycle.lifecycleScope

class ConfiguracoesActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient
    private var internalBiometricChange = false
    private var internalThemeChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracoes)

        session = SessionManager(this)
        api = ApiClient(session)

        val switchBiometria = findViewById<SwitchMaterial>(R.id.switchBiometriaConfig)
        val switchNotificacoes = findViewById<SwitchMaterial>(R.id.switchNotificacoesConfig)
        val tvBiometriaStatus = findViewById<TextView>(R.id.tvBiometriaStatusConfig)
        val rgCanal = findViewById<RadioGroup>(R.id.rgCanalOtp)
        val rgTimeout = findViewById<RadioGroup>(R.id.rgTimeoutSessao)
        val rgTema = findViewById<RadioGroup>(R.id.rgTemaApp)
        val tvCanalStatus = findViewById<TextView>(R.id.tvCanalOtpStatus)
        val tvFeedback = findViewById<TextView>(R.id.tvFeedbackConfig)

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

        when (session.sessionIdleTimeoutMin) {
            in 1..10 -> rgTimeout.check(R.id.rbTimeout10)
            in 11..15 -> rgTimeout.check(R.id.rbTimeout15)
            else -> rgTimeout.check(R.id.rbTimeout30)
        }

        internalThemeChange = true
        when (AppThemeManager.getMode(this)) {
            AppThemeManager.MODE_DARK -> rgTema.check(R.id.rbTemaEscuro)
            AppThemeManager.MODE_SYSTEM -> rgTema.check(R.id.rbTemaSistema)
            else -> rgTema.check(R.id.rbTemaClaro)
        }
        internalThemeChange = false

        switchBiometria.setOnCheckedChangeListener { _, checked ->
            if (internalBiometricChange) return@setOnCheckedChangeListener
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (!checked) {
                session.biometricEnabled = false
                tvBiometriaStatus.text = "Biometria desativada"
                tvFeedback.text = "Biometria desativada neste aparelho."
                tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_pending))
                return@setOnCheckedChangeListener
            }
            ativarBiometriaComValidacao(switchBiometria, tvBiometriaStatus)
            tvFeedback.text = "Confirme sua digital para ativar a biometria."
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_info))
        }

        switchNotificacoes.setOnCheckedChangeListener { _, checked ->
            session.notificationsEnabled = checked
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            tvFeedback.text = if (checked) "Notificações push ativadas." else "Notificações push desativadas."
            tvFeedback.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (checked) R.color.mobile_semantic_success else R.color.mobile_semantic_pending
                )
            )
        }

        rgCanal.setOnCheckedChangeListener { _, checkedId ->
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val canal = when (checkedId) {
                R.id.rbCanalEmail -> "email"
                else -> "whatsapp"
            }
            session.canalOtp = canal
            tvCanalStatus.text = "Salvando..."
            tvCanalStatus.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_info))
            lifecycleScope.launch(Dispatchers.IO) {
                val resp = try {
                    api.salvarPreferenciaCanalOtp(canal)
                } catch (e: Exception) {
                    null
                }
                withContext(Dispatchers.Main) {
                    if (resp?.ok == true) {
                        val label = mapOf("whatsapp" to "WhatsApp", "email" to "E-mail")
                        tvCanalStatus.text = "Preferência salva: ${label[canal]}"
                        tvCanalStatus.setTextColor(ContextCompat.getColor(this@ConfiguracoesActivity, R.color.mobile_semantic_success))
                        tvFeedback.text = "Canal de código atualizado com sucesso."
                        tvFeedback.setTextColor(ContextCompat.getColor(this@ConfiguracoesActivity, R.color.mobile_semantic_success))
                    } else {
                        tvCanalStatus.text = resp?.erro ?: "Salvo localmente (sincronize ao entrar)"
                        tvCanalStatus.setTextColor(ContextCompat.getColor(this@ConfiguracoesActivity, R.color.mobile_semantic_pending))
                        tvFeedback.text = "Preferência salva localmente e será sincronizada depois."
                        tvFeedback.setTextColor(ContextCompat.getColor(this@ConfiguracoesActivity, R.color.mobile_semantic_pending))
                    }
                }
            }
        }

        rgTimeout.setOnCheckedChangeListener { _, checkedId ->
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val minutes = when (checkedId) {
                R.id.rbTimeout10 -> 10
                R.id.rbTimeout30 -> 30
                else -> 15
            }
            session.sessionIdleTimeoutMin = minutes
            tvFeedback.text = "Bloqueio por inatividade ajustado para $minutes minutos."
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_info))
            Toast.makeText(this, "Bloqueio configurado para $minutes min.", Toast.LENGTH_SHORT).show()
        }

        rgTema.setOnCheckedChangeListener { _, checkedId ->
            if (internalThemeChange) return@setOnCheckedChangeListener
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val mode = when (checkedId) {
                R.id.rbTemaEscuro -> AppThemeManager.MODE_DARK
                R.id.rbTemaSistema -> AppThemeManager.MODE_SYSTEM
                else -> AppThemeManager.MODE_LIGHT
            }
            AppThemeManager.setMode(this, mode)
            val msg = when (mode) {
                AppThemeManager.MODE_DARK -> "Tema escuro ativado."
                AppThemeManager.MODE_SYSTEM -> "Tema seguindo o sistema."
                else -> "Tema claro ativado."
            }
            tvFeedback.text = msg
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_info))
        }

        findViewById<MaterialButton>(R.id.btnRevogarDispositivo).setOnClickListener {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            session.revokeTrustedDevice()
            tvFeedback.text = "Dispositivo confiável revogado."
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_pending))
            Toast.makeText(this, "Dispositivo confiável revogado neste aparelho.", Toast.LENGTH_LONG).show()
        }

        findViewById<MaterialButton>(R.id.btnLogoutRemoto).setOnClickListener {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            session.clear()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

        findViewById<MaterialButton>(R.id.btnPoliticaPrivacidade).setOnClickListener {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val base = (session.apiBaseUrl.ifBlank { BuildConfig.DEFAULT_API_BASE_URL }).trimEnd('/')
            startActivity(Intent(this, PrivacyPolicyActivity::class.java).apply {
                putExtra(PrivacyPolicyActivity.EXTRA_URL, "$base/politica-de-privacidade")
            })
        }

        findViewById<MaterialButton>(R.id.btnLimparOffline).setOnClickListener {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            OfflineDocsStore(this).clearAll()
            tvFeedback.text = "Documentos offline removidos deste aparelho."
            tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_success))
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
                findViewById<TextView>(R.id.tvFeedbackConfig).apply {
                    text = "Biometria ativada com sucesso."
                    setTextColor(ContextCompat.getColor(this@ConfiguracoesActivity, R.color.mobile_semantic_success))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                internalBiometricChange = true
                switchBiometria.isChecked = false
                internalBiometricChange = false
                tvStatus.text = errString.toString()
                findViewById<TextView>(R.id.tvFeedbackConfig).apply {
                    text = "Falha ao ativar biometria: ${errString}"
                    setTextColor(ContextCompat.getColor(this@ConfiguracoesActivity, R.color.mobile_semantic_pending))
                }
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
