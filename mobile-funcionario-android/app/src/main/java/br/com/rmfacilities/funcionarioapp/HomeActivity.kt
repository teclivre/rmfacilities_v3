package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvBoasVindas: TextView
    private lateinit var tvCargo: TextView
    private lateinit var tvAvatar: TextView
    private lateinit var tvUltimoAso: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        session = SessionManager(this)
        api = ApiClient(session)

        if (session.accessToken.isBlank()) {
            goLogin(); return
        }

        tvBoasVindas = findViewById(R.id.tvBoasVindas)
        tvCargo = findViewById(R.id.tvCargo)
        tvAvatar = findViewById(R.id.tvAvatar)
        tvUltimoAso = findViewById(R.id.tvUltimoAso)
        swipeRefresh = findViewById(R.id.swipeRefreshHome)

        swipeRefresh.setColorSchemeResources(R.color.accent)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.surface)

        findViewById<LinearLayout>(R.id.btnPerfil).setOnClickListener {
            startActivity(Intent(this, PerfilActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnDocumentos).setOnClickListener {
            startActivity(Intent(this, DocumentosActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnPonto).setOnClickListener {
            Toast.makeText(this, "Modulo de ponto em implantacao nesta versao.", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            session.clear()
            goLogin()
        }

        swipeRefresh.setOnRefreshListener { carregarDados() }
        swipeRefresh.isRefreshing = true
        carregarDados()
    }

    private fun carregarDados() {
        CoroutineScope(Dispatchers.IO).launch {
            val me = try { api.me() } catch (_: Exception) { MeResponse(ok = false) }
            withContext(Dispatchers.Main) {
                swipeRefresh.isRefreshing = false
                val nome = me.funcionario?.nome ?: "colaborador"
                val primeiroNome = nome.split(" ").firstOrNull() ?: nome
                val inicial = nome.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                tvBoasVindas.text = "Olá, $primeiroNome"
                tvAvatar.text = inicial
                tvCargo.text = listOf(me.funcionario?.cargo, me.funcionario?.setor)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" • ")
                tvUltimoAso.text = formatUltimoAso(me.funcionario?.ultimo_aso_competencia, me.funcionario?.ultimo_aso_enviado_em)
            }
        }
    }

    private fun formatUltimoAso(competencia: String?, enviadoEmIso: String?): String {
        val comp = competencia?.trim().orEmpty()
        if (comp.isNotBlank()) {
            return "Competencia: $comp"
        }
        val enviado = enviadoEmIso?.trim().orEmpty()
        if (enviado.length >= 10) {
            return "Enviado em: ${enviado.substring(0, 10)}"
        }
        return "Nao encontrado"
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}

