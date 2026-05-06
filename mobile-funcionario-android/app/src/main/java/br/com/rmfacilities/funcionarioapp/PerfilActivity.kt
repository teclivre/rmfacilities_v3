package br.com.rmfacilities.funcionarioapp

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PerfilActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private lateinit var api: ApiClient
    private lateinit var tvAvatar: TextView
    private lateinit var ivFoto: ImageView
    private lateinit var tvFeedback: TextView
    private lateinit var switchBiometria: SwitchMaterial
    private lateinit var tvBiometriaStatus: TextView
    private var fotoUrlAtual: String? = null
    private var cpfPerfilAtual: String = ""
    private var atualizandoBiometriaUi = false

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        tvFeedback.text = "Enviando foto..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val bytes = inputStream.readBytes()
                inputStream.close()
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val r = api.uploadFoto(bytes, mimeType)
                withContext(Dispatchers.Main) {
                    if (r.ok) {
                        tvFeedback.text = "Foto atualizada com sucesso."
                        fotoUrlAtual = r.foto_url
                        exibirFotoDosBytes(bytes)
                    } else {
                        tvFeedback.text = r.erro ?: "Falha ao enviar foto."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvFeedback.text = "Erro ao processar foto."
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        session = SessionManager(this)
        api = ApiClient(session)

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { finish() }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSobre).setOnClickListener {
            startActivity(android.content.Intent(this, AboutActivity::class.java))
        }

        tvAvatar = findViewById(R.id.tvAvatar)
        ivFoto = findViewById(R.id.ivFoto)
        tvFeedback = findViewById(R.id.tvFeedbackPerfil)
        switchBiometria = findViewById(R.id.switchBiometria)
        tvBiometriaStatus = findViewById(R.id.tvBiometriaStatus)
        val tvNome = findViewById<TextView>(R.id.tvNome)
        val tvCpf = findViewById<TextView>(R.id.tvCpf)
        val tvCargo = findViewById<TextView>(R.id.tvCargo)
        val tvEmpresaHeader = findViewById<TextView>(R.id.tvEmpresaHeader)
        val tvEmpresa = findViewById<TextView>(R.id.tvEmpresa)
        val tvPosto = findViewById<TextView>(R.id.tvPosto)
        val tvSetor = findViewById<TextView>(R.id.tvSetor)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnAlterarFoto = findViewById<MaterialButton>(R.id.btnAlterarFoto)
        val btnTestarNotificacao = findViewById<MaterialButton>(R.id.btnTestarNotificacao)

        atualizarBiometriaUi()
        switchBiometria.setOnCheckedChangeListener { _, checked ->
            if (atualizandoBiometriaUi) return@setOnCheckedChangeListener
            if (checked) {
                ativarBiometriaComValidacao()
            } else {
                session.biometricEnabled = false
                atualizarBiometriaUi("Biometria desativada para este aparelho.")
            }
        }

        btnAlterarFoto.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnTestarNotificacao.setOnClickListener {
            tvFeedback.text = "Registrando token do app..."
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { fcmToken ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val reg = try {
                            api.registrarPushToken(fcmToken)
                        } catch (e: Exception) {
                            ApiSimpleResponse(ok = false, erro = e.message)
                        }
                        if (!reg.ok) {
                            withContext(Dispatchers.Main) {
                                tvFeedback.text = "❌ Falha ao registrar token: ${reg.erro ?: "erro desconhecido"}"
                            }
                            return@launch
                        }

                        val result = try {
                            api.testarPushToken()
                        } catch (e: Exception) {
                            ApiSimpleResponse(ok = false, erro = e.message)
                        }
                        withContext(Dispatchers.Main) {
                            tvFeedback.text = if (result.ok) {
                                "✅ Notificação enviada! Verifique o celular."
                            } else {
                                "❌ Falha: ${result.erro ?: "sem token registrado"}"
                            }
                        }
                    }
                }
                .addOnFailureListener { ex ->
                    tvFeedback.text = "❌ Não foi possível obter token FCM: ${ex.message ?: "erro desconhecido"}"
                }
        }

        fun carregarPerfil() {
            CoroutineScope(Dispatchers.IO).launch {
                val me = try { api.me() } catch (_: Exception) { MeResponse(ok = false) }
                withContext(Dispatchers.Main) {
                    val f = me.funcionario
                    val nome = f?.nome.orEmpty()
                    tvNome.text = nome
                    tvAvatar.text = nome.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    tvCpf.text = f?.cpf.orEmpty()
                    cpfPerfilAtual = f?.cpf.orEmpty().replace("\\D".toRegex(), "")
                    tvCargo.text = f?.cargo.orEmpty()
                    val empresa = f?.empresa_nome.orEmpty()
                    tvEmpresaHeader.text = if (empresa.isNotBlank()) empresa else ""
                    tvEmpresa.text = empresa.ifBlank { "—" }
                    tvPosto.text = f?.posto_operacional.orEmpty().ifBlank { "—" }
                    tvSetor.text = f?.setor.orEmpty()
                    tvStatus.text = f?.status.orEmpty()
                    fotoUrlAtual = f?.foto_url
                    if (f?.foto_url != null) carregarFotoUrl(f.foto_url)
                    atualizarBiometriaUi()
                }
            }
        }

        carregarPerfil()
    }

    private fun atualizarBiometriaUi(msgExtra: String? = null) {
        atualizandoBiometriaUi = true
        switchBiometria.isChecked = session.biometricEnabled
        atualizandoBiometriaUi = false

        val status = when {
            !canUseBiometric() -> "Biometria indisponível neste aparelho."
            session.biometricEnabled && session.biometricCpf.isNotBlank() -> "Biometria ativa para o CPF final ${session.biometricCpf.takeLast(3)}."
            session.biometricEnabled -> "Biometria ativa."
            else -> "Biometria desativada."
        }
        tvBiometriaStatus.text = msgExtra ?: status
    }

    private fun canUseBiometric(): Boolean {
        val manager = BiometricManager.from(this)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun ativarBiometriaComValidacao() {
        if (!canUseBiometric()) {
            atualizandoBiometriaUi = true
            switchBiometria.isChecked = false
            atualizandoBiometriaUi = false
            atualizarBiometriaUi("Biometria não disponível neste aparelho.")
            return
        }

        val cpfBase = (cpfPerfilAtual.ifBlank { session.biometricCpf }).replace("\\D".toRegex(), "")
        if (cpfBase.length != 11) {
            atualizandoBiometriaUi = true
            switchBiometria.isChecked = false
            atualizandoBiometriaUi = false
            atualizarBiometriaUi("Faça login com CPF/código para habilitar a biometria.")
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                session.biometricEnabled = true
                session.biometricCpf = cpfBase
                atualizarBiometriaUi("Biometria habilitada com sucesso.")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                atualizandoBiometriaUi = true
                switchBiometria.isChecked = false
                atualizandoBiometriaUi = false
                atualizarBiometriaUi(errString.toString())
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Ativar biometria")
            .setSubtitle("Confirme sua digital para habilitar login biométrico")
            .setNegativeButtonText("Cancelar")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun exibirFotoDosBytes(bytes: ByteArray) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap != null) {
            ivFoto.setImageBitmap(bitmap)
            ivFoto.visibility = View.VISIBLE
            tvAvatar.visibility = View.GONE
        }
    }

    private fun carregarFotoUrl(fotoUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bytes = api.downloadFile(fotoUrl)
                withContext(Dispatchers.Main) { exibirFotoDosBytes(bytes) }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    ivFoto.visibility = View.GONE
                    tvAvatar.visibility = View.VISIBLE
                }
            }
        }
    }
}
