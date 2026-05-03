package br.com.rmfacilities.funcionarioapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PerfilActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        val session = SessionManager(this)
        val api = ApiClient(session)

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { finish() }

        val tvAvatar = findViewById<TextView>(R.id.tvAvatar)
        val tvNome = findViewById<TextView>(R.id.tvNome)
        val tvCpf = findViewById<TextView>(R.id.tvCpf)
        val tvCargo = findViewById<TextView>(R.id.tvCargo)
        val tvSetor = findViewById<TextView>(R.id.tvSetor)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etTelefone = findViewById<TextInputEditText>(R.id.etTelefone)
        val etNovoCargo = findViewById<TextInputEditText>(R.id.etNovoCargo)
        val etNovoSetor = findViewById<TextInputEditText>(R.id.etNovoSetor)
        val etObsSolicitacao = findViewById<TextInputEditText>(R.id.etObsSolicitacao)
        val tvFeedback = findViewById<TextView>(R.id.tvFeedbackPerfil)
        val btnSalvarContato = findViewById<MaterialButton>(R.id.btnSalvarContato)
        val btnSolicitar = findViewById<MaterialButton>(R.id.btnSolicitarAlteracao)

        fun carregarPerfil() {
            CoroutineScope(Dispatchers.IO).launch {
                val me = try {
                    api.me()
                } catch (_: Exception) {
                    MeResponse(ok = false)
                }

                withContext(Dispatchers.Main) {
                    val f = me.funcionario
                    val nome = f?.nome.orEmpty()
                    tvNome.text = nome
                    tvAvatar.text = nome.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    tvCpf.text = f?.cpf.orEmpty()
                    tvCargo.text = f?.cargo.orEmpty()
                    tvSetor.text = f?.setor.orEmpty()
                    tvStatus.text = f?.status.orEmpty()
                    etEmail.setText(f?.email.orEmpty())
                    etTelefone.setText(f?.telefone.orEmpty())
                }
            }
        }

        btnSalvarContato.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val telefone = etTelefone.text?.toString()?.trim().orEmpty()
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
                tvFeedback.text = "Informe ao menos cargo ou setor para solicitar alteração."
                return@setOnClickListener
            }
            val obs = etObsSolicitacao.text?.toString()?.trim().orEmpty()
            tvFeedback.text = "Enviando solicitação para aprovação..."
            CoroutineScope(Dispatchers.IO).launch {
                val r = try {
                    api.solicitarAlteracao(campos, obs)
                } catch (e: Exception) {
                    SolicitacaoResponse(ok = false, erro = e.message)
                }
                withContext(Dispatchers.Main) {
                    tvFeedback.text = if (r.ok) "Solicitação enviada ao administrador." else (r.erro ?: "Falha ao registrar solicitação.")
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
}
