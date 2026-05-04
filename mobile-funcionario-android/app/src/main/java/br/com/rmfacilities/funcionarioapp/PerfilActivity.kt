package br.com.rmfacilities.funcionarioapp

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PerfilActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private lateinit var tvAvatar: TextView
    private lateinit var ivFoto: ImageView
    private lateinit var tvFeedback: TextView
    private var fotoUrlAtual: String? = null

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

        val session = SessionManager(this)
        api = ApiClient(session)

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { finish() }

        tvAvatar = findViewById(R.id.tvAvatar)
        ivFoto = findViewById(R.id.ivFoto)
        tvFeedback = findViewById(R.id.tvFeedbackPerfil)
        val tvNome = findViewById<TextView>(R.id.tvNome)
        val tvCpf = findViewById<TextView>(R.id.tvCpf)
        val tvCargo = findViewById<TextView>(R.id.tvCargo)
        val tvEmpresaHeader = findViewById<TextView>(R.id.tvEmpresaHeader)
        val tvEmpresa = findViewById<TextView>(R.id.tvEmpresa)
        val tvPosto = findViewById<TextView>(R.id.tvPosto)
        val tvSetor = findViewById<TextView>(R.id.tvSetor)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etTelefone = findViewById<TextInputEditText>(R.id.etTelefone)
        val etNovoCargo = findViewById<TextInputEditText>(R.id.etNovoCargo)
        val etNovoSetor = findViewById<TextInputEditText>(R.id.etNovoSetor)
        val etObsSolicitacao = findViewById<TextInputEditText>(R.id.etObsSolicitacao)
        val btnSalvarContato = findViewById<MaterialButton>(R.id.btnSalvarContato)
        val btnSolicitar = findViewById<MaterialButton>(R.id.btnSolicitarAlteracao)
        val btnAlterarFoto = findViewById<MaterialButton>(R.id.btnAlterarFoto)

        btnAlterarFoto.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
                    tvCargo.text = f?.cargo.orEmpty()
                    val empresa = f?.empresa_nome.orEmpty()
                    tvEmpresaHeader.text = if (empresa.isNotBlank()) empresa else ""
                    tvEmpresa.text = empresa.ifBlank { "—" }
                    tvPosto.text = f?.posto_operacional.orEmpty().ifBlank { "—" }
                    tvSetor.text = f?.setor.orEmpty()
                    tvStatus.text = f?.status.orEmpty()
                    etEmail.setText(f?.email.orEmpty())
                    etTelefone.setText(telefoneSemPais(f?.telefone))
                    fotoUrlAtual = f?.foto_url
                    if (f?.foto_url != null) carregarFotoUrl(f.foto_url)
                }
            }
        }

        btnSalvarContato.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val telefone = telefoneSemPais(etTelefone.text?.toString())
            tvFeedback.text = "Salvando contato..."
            CoroutineScope(Dispatchers.IO).launch {
                val r = try {
                    api.atualizarContato(email, telefone)
                } catch (e: Exception) {
                    ContatoUpdateResponse(ok = false, erro = e.message)
                }
                withContext(Dispatchers.Main) {
                    tvFeedback.text = if (r.ok) "Contato atualizado com sucesso." else (r.erro ?: "Falha ao atualizar contato.")
                    if (r.ok) carregarPerfil()
                }
            }
        }

        btnSolicitar.setOnClickListener {
            val campos = mutableMapOf<String, String>()
            val novoCargo = etNovoCargo.text?.toString()?.trim().orEmpty()
            val novoSetor = etNovoSetor.text?.toString()?.trim().orEmpty()
            if (novoCargo.isNotBlank()) campos["cargo"] = novoCargo
            if (novoSetor.isNotBlank()) campos["setor"] = novoSetor
            if (campos.isEmpty()) {
                tvFeedback.text = "Informe ao menos cargo ou setor para solicitar alteracao."
                return@setOnClickListener
            }
            val obs = etObsSolicitacao.text?.toString()?.trim().orEmpty()
            tvFeedback.text = "Enviando solicitacao para aprovacao..."
            CoroutineScope(Dispatchers.IO).launch {
                val r = try {
                    api.solicitarAlteracao(campos, obs)
                } catch (e: Exception) {
                    SolicitacaoResponse(ok = false, erro = e.message)
                }
                withContext(Dispatchers.Main) {
                    tvFeedback.text = if (r.ok) "Solicitacao enviada ao administrador." else (r.erro ?: "Falha ao registrar solicitacao.")
                    if (r.ok) {
                        etNovoCargo.setText("")
                        etNovoSetor.setText("")
                        etObsSolicitacao.setText("")
                    }
                }
            }
        }

        carregarPerfil()
    }

    private fun telefoneSemPais(v: String?): String {
        val digits = (v ?: "").filter { it.isDigit() }
        return if (digits.startsWith("55") && digits.length in 12..13) digits.substring(2) else digits
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
