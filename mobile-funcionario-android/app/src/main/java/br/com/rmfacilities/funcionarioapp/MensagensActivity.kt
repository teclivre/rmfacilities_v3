package br.com.rmfacilities.funcionarioapp

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MensagensActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient
    private lateinit var adapter: MensagemAdapter
    private lateinit var rvMensagens: RecyclerView
    private lateinit var etMensagem: EditText
    private lateinit var tvBadge: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mensagens)

        session = SessionManager(this)
        api = ApiClient(session)

        rvMensagens = findViewById(R.id.rvMensagens)
        etMensagem = findViewById(R.id.etMensagem)
        tvBadge = findViewById(R.id.tvBadge)

        adapter = MensagemAdapter()
        rvMensagens.layoutManager = LinearLayoutManager(this).also { it.stackFromEnd = true }
        rvMensagens.adapter = adapter

        findViewById<MaterialButton>(R.id.btnVoltar).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnEnviar).setOnClickListener { enviar() }

        carregarMensagens()
    }

    private fun carregarMensagens() {
        CoroutineScope(Dispatchers.IO).launch {
            val msgs = try { api.getMensagens() } catch (_: Exception) { emptyList() }
            withContext(Dispatchers.Main) {
                adapter.replaceAll(msgs)
                if (msgs.isNotEmpty()) rvMensagens.scrollToPosition(msgs.size - 1)
                tvBadge.visibility = android.view.View.GONE
            }
        }
    }

    private fun enviar() {
        val texto = etMensagem.text.toString().trim()
        if (texto.isBlank()) {
            Toast.makeText(this, "Digite uma mensagem.", Toast.LENGTH_SHORT).show()
            return
        }
        etMensagem.isEnabled = false
        CoroutineScope(Dispatchers.IO).launch {
            val nova = try { api.enviarMensagem(texto) } catch (_: Exception) { null }
            withContext(Dispatchers.Main) {
                etMensagem.isEnabled = true
                if (nova != null) {
                    etMensagem.setText("")
                    adapter.addMensagem(nova)
                    rvMensagens.scrollToPosition(adapter.itemCount - 1)
                } else {
                    Toast.makeText(this@MensagensActivity, "Erro ao enviar mensagem.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
