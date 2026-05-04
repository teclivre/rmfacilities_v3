package br.com.rmfacilities.funcionarioapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoricoAssinaturasActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var adapter: HistoricoAssinaturasAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historico_assinaturas)

        session = SessionManager(this)
        api = ApiClient(session)

        swipe = findViewById(R.id.swipeHistorico)
        val rv = findViewById<RecyclerView>(R.id.rvHistorico)

        findViewById<android.widget.TextView>(R.id.btnVoltarHistorico).setOnClickListener { finish() }

        adapter = HistoricoAssinaturasAdapter()
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(this)

        swipe.setOnRefreshListener { carregar() }
        swipe.isRefreshing = true
        carregar()
    }

    private fun carregar() {
        CoroutineScope(Dispatchers.IO).launch {
            val resp = try { api.historicoAssinaturas() }
                       catch (e: Exception) { HistoricoAssinaturasResponse(ok = false, erro = e.message) }
            withContext(Dispatchers.Main) {
                swipe.isRefreshing = false
                if (resp.ok) {
                    adapter.replaceAll(resp.itens)
                } else {
                    Toast.makeText(this@HistoricoAssinaturasActivity, resp.erro ?: "Falha ao carregar histórico", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
