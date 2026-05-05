package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        btnEnviarCodigo.setOnClickListener { enviarCodigo() }
        btnBiometria.setOnClickListener { autenticarComBiometria() }
        btnEntrar.setOnClickListener { confirmarOtp() }
        tvReenviar.setOnClickListener { enviarCodigo() }

        inicializarBiometriaUI()
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
                    hideErro()
                } else {
                    showErro(resp.erro ?: "Erro ao enviar código.")
                }
            }
        }
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
        btnEnviarCodigo.isEnabled = !loading
        btnBiometria.isEnabled = !loading
        btnEntrar.isEnabled = !loading
        tvReenviar.isEnabled = !loading
    }

    private fun inicializarBiometriaUI() {
        val biometricManager = BiometricManager.from(this)
        val canBio = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        btnBiometria.visibility = if (canBio && session.biometricEnabled && session.biometricCpf.isNotBlank()) View.VISIBLE else View.GONE
        if (session.biometricCpf.isNotBlank() && etCpf.text.isNullOrBlank()) {
            etCpf.setText(session.biometricCpf)
        }
    }

    private fun autenticarComBiometria() {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            showErro("Biometria não disponível neste aparelho.")
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val cpf = session.biometricCpf
                    if (cpf.length == 11) {
                        etCpf.setText(cpf)
                        enviarCodigo()
                    } else {
                        showErro("CPF biométrico não configurado. Faça login com código uma vez.")
                    }
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

    private fun perguntarAtivarBiometria(cpf: String) {
        val biometricManager = BiometricManager.from(this)
        val canBio = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
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
}
