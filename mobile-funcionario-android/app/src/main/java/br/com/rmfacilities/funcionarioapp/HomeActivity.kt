package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        session = SessionManager(this)
        api = ApiClient(session)

        if (session.accessToken.isBlank()) {
            goLogin(); return
        }

        val tvBoasVindas = findViewById<TextView>(R.id.tvBoasVindas)
        val tvCargo = findViewById<TextView>(R.id.tvCargo)
        val tvAvatar = findViewById<TextView>(R.id.tvAvatar)

        findViewById<LinearLayout>(R.id.btnPerfil).setOnClickListener {
            startActivity(Intent(this, PerfilActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnDocumentos).setOnClickListener {
            startActivity(Intent(this, DocumentosActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            session.clear()
            goLogin()
        }

        CoroutineScope(Dispatchers.IO).launch {
            val me = try { api.me() } catch (_: Exception) { MeResponse(ok = false) }
            withContext(Dispatchers.Main) {
                val nome = me.funcionario?.nome ?: "colaborador"
                val primeiroNome = nome.split(" ").firstOrNull() ?: nome
                val inicial = nome.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                tvBoasVindas.text = "Olá, $primeiroNome"
                tvAvatar.text = inicial
                tvCargo.text = listOf(me.funcionario?.cargo, me.funcionario?.setor)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" • ")
            }
        }
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}

