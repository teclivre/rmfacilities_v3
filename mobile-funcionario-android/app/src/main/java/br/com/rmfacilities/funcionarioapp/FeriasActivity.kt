package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeriasActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private lateinit var tvStatus: TextView
    private lateinit var containerFerias: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ferias)

        api = ApiClient(SessionManager(this))
        tvStatus = findViewById(R.id.tvStatusFerias)
        containerFerias = findViewById(R.id.containerFerias)
        swipeRefresh = findViewById(R.id.swipeRefreshFerias)

        swipeRefresh.setColorSchemeResources(R.color.accent)
        swipeRefresh.setOnRefreshListener { carregar() }

        findViewById<TextView>(R.id.btnVoltarFerias).setOnClickListener { finish() }

        carregar()
    }

    private fun carregar() {
        tvStatus.text = "Carregando..."
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_info))
        tvStatus.visibility = View.VISIBLE

        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) {
                try { api.getFeriasFuncionario() }
                catch (e: Exception) { FeriasResponse(ok = false, erro = e.message) }
            }
            withContext(Dispatchers.Main) {
                swipeRefresh.isRefreshing = false
                if (resp.ok) {
                    tvStatus.visibility = View.GONE
                    renderFerias(resp)
                } else {
                    tvStatus.text = resp.erro ?: "Erro ao carregar férias."
                    tvStatus.setTextColor(ContextCompat.getColor(this@FeriasActivity, R.color.mobile_semantic_pending))
                }
            }
        }
    }

    private fun renderFerias(resp: FeriasResponse) {
        containerFerias.removeAllViews()
        val dp = resources.displayMetrics.density

        fun addCard(block: LinearLayout.() -> Unit) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@FeriasActivity, R.drawable.bg_glass_widget)
                setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
                (layoutParams ?: LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (12 * dp).toInt() }).let { layoutParams = it }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * dp).toInt() }
            card.layoutParams = params
            card.block()
            containerFerias.addView(card)
        }

        fun cardLabel(label: String, valor: String, cor: Int = R.color.mobile_text_primary) {
            addCard {
                addView(TextView(this@FeriasActivity).apply {
                    text = label
                    setTextColor(ContextCompat.getColor(this@FeriasActivity, R.color.mobile_text_secondary))
                    textSize = 12f
                })
                addView(TextView(this@FeriasActivity).apply {
                    text = valor
                    setTextColor(ContextCompat.getColor(this@FeriasActivity, cor))
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, (4 * dp).toInt(), 0, 0)
                })
            }
        }

        // Status atual
        if (resp.em_ferias) {
            addCard {
                addView(TextView(this@FeriasActivity).apply {
                    text = "🏖️ Você está em férias!"
                    setTextColor(ContextCompat.getColor(this@FeriasActivity, R.color.mobile_semantic_success))
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                })
                resp.dias_restantes?.let {
                    addView(TextView(this@FeriasActivity).apply {
                        text = "Faltam $it dia${if (it != 1) "s" else ""} para o retorno"
                        setTextColor(ContextCompat.getColor(this@FeriasActivity, R.color.mobile_text_secondary))
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setPadding(0, (6 * dp).toInt(), 0, 0)
                    })
                }
            }
        } else {
            val proximas = resp.proximas
            if (proximas != null) {
                addCard {
                    addView(TextView(this@FeriasActivity).apply {
                        text = "📅 Férias programadas"
                        setTextColor(ContextCompat.getColor(this@FeriasActivity, R.color.mobile_semantic_info))
                        textSize = 16f
                        setTypeface(null, Typeface.BOLD)
                    })
                    proximas.dias_para_inicio?.let {
                        addView(TextView(this@FeriasActivity).apply {
                            text = "Faltam $it dia${if (it != 1) "s" else ""} para o início"
                            setTextColor(ContextCompat.getColor(this@FeriasActivity, R.color.mobile_text_secondary))
                            textSize = 13f
                            setPadding(0, (4 * dp).toInt(), 0, 0)
                        })
                    }
                }
            } else if (resp.ferias_inicio.isNullOrBlank()) {
                addCard {
                    addView(TextView(this@FeriasActivity).apply {
                        text = "Nenhuma férias programada no momento."
                        setTextColor(ContextCompat.getColor(this@FeriasActivity, R.color.mobile_text_secondary))
                        textSize = 14f
                        gravity = Gravity.CENTER
                    })
                }
            }
        }

        // Período
        if (!resp.ferias_inicio.isNullOrBlank() || !resp.ferias_fim.isNullOrBlank()) {
            cardLabel("Período de férias",
                "${formatarData(resp.ferias_inicio)} — ${formatarData(resp.ferias_fim)}")
        }

        // Duração
        cardLabel("Duração", "${resp.ferias_dias} dias")

        // Observações
        if (!resp.ferias_obs.isNullOrBlank()) {
            cardLabel("Observações do RH", resp.ferias_obs)
        }

        // Botão solicitar correção de ponto
        val btnCorrecao = MaterialButton(this).apply {
            text = "📋 Solicitar correção de ponto"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@FeriasActivity, R.color.mobile_text_primary))
            backgroundTintList = ContextCompat.getColorStateList(this@FeriasActivity, R.color.mobile_surface)
            strokeColor = ContextCompat.getColorStateList(this@FeriasActivity, R.color.border)
            strokeWidth = (1 * dp).toInt()
            cornerRadius = (12 * dp).toInt()
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * dp).toInt() }
            layoutParams = params
        }
        btnCorrecao.setOnClickListener {
            startActivity(Intent(this, SolicitacaoCorrecaoPontoActivity::class.java))
        }
        containerFerias.addView(btnCorrecao)
    }

    private fun formatarData(iso: String?): String {
        if (iso.isNullOrBlank()) return "--"
        return try {
            val parts = iso.split("-")
            if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
        } catch (_: Exception) { iso }
    }
}
