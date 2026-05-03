package br.com.rmfacilities.funcionarioapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

        val tvNome = findViewById<TextView>(R.id.tvNome)
        val tvCpf = findViewById<TextView>(R.id.tvCpf)
        val tvEmail = findViewById<TextView>(R.id.tvEmail)
        val tvTelefone = findViewById<TextView>(R.id.tvTelefone)
        val tvCargo = findViewById<TextView>(R.id.tvCargo)
        val tvSetor = findViewById<TextView>(R.id.tvSetor)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        CoroutineScope(Dispatchers.IO).launch {
            val me = try {
                api.me()
            } catch (_: Exception) {
                MeResponse(ok = false)
            }

            withContext(Dispatchers.Main) {
                val f = me.funcionario
                tvNome.text = "Nome: ${f?.nome.orEmpty()}"
                tvCpf.text = "CPF: ${f?.cpf.orEmpty()}"
                tvEmail.text = "E-mail: ${f?.email.orEmpty()}"
                tvTelefone.text = "Telefone: ${f?.telefone.orEmpty()}"
                tvCargo.text = "Cargo: ${f?.cargo.orEmpty()}"
                tvSetor.text = "Setor: ${f?.setor.orEmpty()}"
                tvStatus.text = "Status: ${f?.status.orEmpty()}"
            }
        }
    }
}
