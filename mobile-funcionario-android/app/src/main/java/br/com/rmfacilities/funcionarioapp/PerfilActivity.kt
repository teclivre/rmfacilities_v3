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
        val btnAlterarFoto = findViewById<MaterialButton>(R.id.btnAlterarFoto)
        val btnTestarNotificacao = findViewById<MaterialButton>(R.id.btnTestarNotificacao)

        btnAlterarFoto.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnTestarNotificacao.setOnClickListener {
            tvFeedback.text = "Enviando notificação de teste..."
            CoroutineScope(Dispatchers.IO).launch {
                val result = try { api.testarPushToken() } catch (e: Exception) { ApiSimpleResponse(ok = false, erro = e.message) }
                withContext(Dispatchers.Main) {
                    tvFeedback.text = if (result.ok) "✅ Notificação enviada! Verifique o celular." else "❌ Falha: ${result.erro ?: "sem token registrado"}"
                }
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
                    tvCargo.text = f?.cargo.orEmpty()
                    val empresa = f?.empresa_nome.orEmpty()
                    tvEmpresaHeader.text = if (empresa.isNotBlank()) empresa else ""
                    tvEmpresa.text = empresa.ifBlank { "—" }
                    tvPosto.text = f?.posto_operacional.orEmpty().ifBlank { "—" }
                    tvSetor.text = f?.setor.orEmpty()
                    tvStatus.text = f?.status.orEmpty()
                    fotoUrlAtual = f?.foto_url
                    if (f?.foto_url != null) carregarFotoUrl(f.foto_url)
                }
            }
        }

        carregarPerfil()
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
