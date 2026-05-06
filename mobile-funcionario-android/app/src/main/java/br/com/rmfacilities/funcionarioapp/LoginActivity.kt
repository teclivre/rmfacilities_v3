package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient

    private lateinit var etCpf: TextInputEditText
    private lateinit var etCodigo: TextInputEditText
    private lateinit var btnEnviarCodigo: MaterialButton
    private lateinit var btnBiometria: MaterialButton
    private lateinit var btnEntrar: MaterialButton
    private lateinit var tvReenviar: TextView
    private lateinit var layoutOtp: LinearLayout
    private lateinit var tvOtpMsg: TextView
    private lateinit var progress: ProgressBar
    private lateinit var tvErro: TextView

    private var cpfAtual = ""
    private var biometricPromptShown = false
    private var otpCooldownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TelemetryLogger.init(this)
        setContentView(R.layout.activity_login)

        session = SessionManager(this)
        api = ApiClient(session)

        if (session.accessToken.isNotBlank()) {
            goHomeOrDeepLink()
            return
        }

        etCpf = findViewById(R.id.etCpf)
        etCodigo = findViewById(R.id.etCodigo)
        btnEnviarCodigo = findViewById(R.id.btnEnviarCodigo)
        btnBiometria = findViewById(R.id.btnBiometria)
        btnEntrar = findViewById(R.id.btnEntrar)
        tvReenviar = findViewById(R.id.tvReenviar)
        layoutOtp = findViewById(R.id.layoutOtp)
        tvOtpMsg = findViewById(R.id.tvOtpMsg)
        progress = findViewById(R.id.progressLogin)
        tvErro = findViewById(R.id.tvErro)
        val tvPrivacidade: TextView = findViewById(R.id.tvPrivacidade)

        btnEnviarCodigo.setOnClickListener { enviarCodigo() }
        btnBiometria.setOnClickListener { autenticarComBiometria() }
        btnEntrar.setOnClickListener { confirmarOtp() }
        tvReenviar.setOnClickListener { enviarCodigo() }
        tvPrivacidade.setOnClickListener {
            val base = (session.apiBaseUrl.ifBlank { BuildConfig.DEFAULT_API_BASE_URL }).trimEnd('/')
            val url = "$base/politica-de-privacidade"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                showErro("Não foi possível abrir a Política de Privacidade.")
            }
        }

        inicializarBiometriaUI()
    }

    override fun onResume() {
        super.onResume()
        if (!biometricPromptShown && shouldOfferBiometric()) {
            biometricPromptShown = true
            autenticarComBiometria()
        }
    }

    private fun enviarCodigo() {
        val cpf = etCpf.text?.toString()?.replace("\\D".toRegex(), "").orEmpty()

        if (cpf.length != 11) { showErro("Informe o CPF com 11 dígitos."); return }

        cpfAtual = cpf
        setLoading(true)
        hideErro()

        CoroutineScope(Dispatchers.IO).launch {
            val resp = try {
                api.iniciarOtp(cpf)
            } catch (e: Exception) {
                OtpStartResponse(ok = false, erro = "Erro de conexão: ${e.message}")
            }
            withContext(Dispatchers.Main) {
                setLoading(false)
                if (resp.ok) {
                    tvOtpMsg.text = resp.mensagem ?: "Código enviado! Verifique seu celular."
                    layoutOtp.visibility = View.VISIBLE
                    btnEnviarCodigo.text = "Reenviar código"
                    etCodigo.requestFocus()
                    iniciarCooldownReenvio()
                    hideErro()
                } else {
                    showErro(resp.erro ?: "Erro ao enviar código.")
                }
            }
        }
    }

    private fun iniciarCooldownReenvio() {
        otpCooldownTimer?.cancel()
        tvReenviar.isEnabled = false
        btnEnviarCodigo.isEnabled = false
        otpCooldownTimer = object : CountDownTimer(45_000L, 1_000L) {
            override fun onTick(ms: Long) {
                val s = ms / 1000
                tvReenviar.text = "Reenviar em ${String.format("%02d", s)}s"
            }

            override fun onFinish() {
                tvReenviar.text = "Reenviar código"
                tvReenviar.isEnabled = true
                btnEnviarCodigo.isEnabled = true
                otpCooldownTimer = null
            }
        }.start()
    }

    private fun confirmarOtp() {
        val cpf = cpfAtual.ifBlank {
            etCpf.text?.toString()?.replace("\\D".toRegex(), "").orEmpty()
        }
        val codigo = etCodigo.text?.toString()?.replace("\\D".toRegex(), "").orEmpty()

        if (cpf.length != 11) { showErro("CPF inválido."); return }
        if (codigo.length != 6) { showErro("O código tem 6 dígitos."); return }

        setLoading(true)
        hideErro()

        CoroutineScope(Dispatchers.IO).launch {
            val resp = try {
                api.confirmarOtp(cpf, codigo)
            } catch (e: Exception) {
                LoginResponse(ok = false, erro = "Erro de conexão: ${e.message}")
            }
            withContext(Dispatchers.Main) {
                setLoading(false)
                if (resp.ok && !resp.access_token.isNullOrBlank()) {
                    session.accessToken = resp.access_token
                    session.refreshToken = resp.refresh_token ?: ""
                    if (session.biometricCpf.isBlank()) {
                        session.biometricCpf = cpf
                    }
                    if (!session.biometricEnabled) {
                        perguntarAtivarBiometria(cpf)
                    }
                    // Registrar token FCM imediatamente após login
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
                        CoroutineScope(Dispatchers.IO).launch {
                            try { ApiClient(session).registrarPushToken(fcmToken) } catch (_: Exception) {}
                        }
                    }
                    goHomeOrDeepLink()
                } else {
                    showErro(resp.erro ?: "Código inválido ou expirado.")
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            btnEnviarCodigo.isEnabled = false
            tvReenviar.isEnabled = false
        } else if (otpCooldownTimer == null) {
            btnEnviarCodigo.isEnabled = true
            tvReenviar.isEnabled = true
        }
        btnBiometria.isEnabled = !loading
        btnEntrar.isEnabled = !loading
    }

    private fun inicializarBiometriaUI() {
        val canBio = canUseBiometric()
        btnBiometria.visibility = if (canBio && session.biometricEnabled && session.biometricCpf.isNotBlank()) View.VISIBLE else View.GONE
        if (session.biometricCpf.isNotBlank() && etCpf.text.isNullOrBlank()) {
            etCpf.setText(session.biometricCpf)
        }
    }

    private fun autenticarComBiometria() {
        if (!canUseBiometric()) {
            showErro("Biometria não disponível neste aparelho.")
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    loginComBiometria()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showErro(errString.toString())
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Entrar com biometria")
            .setSubtitle("Confirme sua identidade para continuar")
            .setNegativeButtonText("Cancelar")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun shouldOfferBiometric(): Boolean {
        return canUseBiometric() && session.biometricEnabled && session.biometricCpf.isNotBlank()
    }

    private fun canUseBiometric(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun loginComBiometria() {
        val cpf = session.biometricCpf
        if (cpf.length != 11) {
            showErro("CPF biométrico não configurado. Faça login com código uma vez.")
            return
        }
        etCpf.setText(cpf)

        if (session.refreshToken.isBlank()) {
            enviarCodigo()
            return
        }

        setLoading(true)
        hideErro()
        CoroutineScope(Dispatchers.IO).launch {
            val resp = try {
                api.renovarSessao(session.refreshToken)
            } catch (e: Exception) {
                LoginResponse(ok = false, erro = "Erro de conexão: ${e.message}")
            }
            withContext(Dispatchers.Main) {
                setLoading(false)
                if (resp.ok && !resp.access_token.isNullOrBlank()) {
                    session.accessToken = resp.access_token
                    if (!resp.refresh_token.isNullOrBlank()) {
                        session.refreshToken = resp.refresh_token
                    }
                    goHomeOrDeepLink()
                } else {
                    // Se refresh expirou, mantém biometria, mas volta para o fluxo OTP.
                    session.refreshToken = ""
                    enviarCodigo()
                }
            }
        }
    }

    private fun perguntarAtivarBiometria(cpf: String) {
        val canBio = canUseBiometric()
        if (!canBio) return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Ativar biometria?")
            .setMessage("Deseja usar biometria nos próximos acessos deste colaborador?")
            .setPositiveButton("Ativar") { _, _ ->
                session.biometricEnabled = true
                session.biometricCpf = cpf
                btnBiometria.visibility = View.VISIBLE
            }
            .setNegativeButton("Agora não", null)
            .show()
    }

    private fun showErro(msg: String) {
        tvErro.text = msg
        tvErro.visibility = View.VISIBLE
    }

    private fun hideErro() {
        tvErro.visibility = View.GONE
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun goHomeOrDeepLink() {
        val tipo = intent?.getStringExtra("tipo") ?: intent?.extras?.getString("tipo") ?: ""
        val arquivoId = intent?.getStringExtra("arquivo_id")?.toIntOrNull() ?: -1
        val target: Intent = when {
            tipo == "documento_assinar" && arquivoId > 0 ->
                Intent(this, DocumentosActivity::class.java).apply {
                    putExtra(FcmService.EXTRA_ARQUIVO_ID, arquivoId)
                }
            tipo == "chat" || tipo == "chat_broadcast" ->
                Intent(this, MensagensActivity::class.java)
            else ->
                Intent(this, HomeActivity::class.java)
        }
        startActivity(target)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        otpCooldownTimer?.cancel()
        otpCooldownTimer = null
    }
}
