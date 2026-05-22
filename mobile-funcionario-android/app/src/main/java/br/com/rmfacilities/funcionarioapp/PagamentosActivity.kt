package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PagamentosActivity : BaseActivity() {

    private lateinit var api: ApiClient
    private lateinit var tvStatus: TextView
    private lateinit var containerPagamentos: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_pagamentos)

        api = ApiClient(SessionManager(this))
        tvStatus = findViewById(R.id.tvStatusPagamentos)
        containerPagamentos = findViewById(R.id.containerPagamentos)
        swipeRefresh = findViewById(R.id.swipeRefreshPagamentos)

        swipeRefresh.setColorSchemeResources(R.color.accent)
        swipeRefresh.setOnRefreshListener { carregar() }

        findViewById<TextView>(R.id.btnVoltarPagamentos).setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnHoleritesPagamentos).setOnClickListener {
            startActivity(Intent(this, DocumentosActivity::class.java).apply {
                putExtra("preset_categoria", "holerite")
            })
        }

        carregar()
    }

    private fun carregar() {
        tvStatus.text = "Carregando pagamentos..."
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_info))
        tvStatus.visibility = View.VISIBLE
        containerPagamentos.removeAllViews()

        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) {
                try { api.historicoPagamentos() }
                catch (e: Exception) { ApiClient.HistoricoPagamentosResponse(ok = false) }
            }
            withContext(Dispatchers.Main) {
                swipeRefresh.isRefreshing = false
                if (resp.ok) {
                    tvStatus.visibility = View.GONE
                    renderPagamentos(resp.historico)
                } else {
                    tvStatus.text = "Erro ao carregar pagamentos. Puxe para tentar novamente."
                    tvStatus.setTextColor(ContextCompat.getColor(this@PagamentosActivity, R.color.mobile_semantic_pending))
                }
            }
        }
    }

    private fun renderPagamentos(historico: List<ApiClient.PagamentoItem>) {
        containerPagamentos.removeAllViews()
        val dp = resources.displayMetrics.density

        if (historico.isEmpty()) {
            tvStatus.text = "Nenhum pagamento registrado ainda."
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.mobile_text_secondary))
            tvStatus.visibility = View.VISIBLE
            return
        }

        historico.forEach { p ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@PagamentosActivity, R.drawable.bg_glass_widget)
                setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (12 * dp).toInt()
                layoutParams = lp
            }

            // Linha do topo: competência + total a pagar
            val rowTop = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (8 * dp).toInt()
                layoutParams = lp
            }

            val tvComp = TextView(this).apply {
                text = "💰 ${compFmt(p.competencia)}"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@PagamentosActivity, R.color.mobile_text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val totalPagar = if (p.total_pagar > 0) p.total_pagar else p.valor_liquido
            val tvTotal = TextView(this).apply {
                text = "R$ %,.2f".format(totalPagar)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@PagamentosActivity, R.color.accent))
            }

            rowTop.addView(tvComp)
            rowTop.addView(tvTotal)
            card.addView(rowTop)

            // Salário base
            addDetalheRow(card, dp, "Salário líquido", "R$ %,.2f".format(p.valor_liquido),
                R.color.mobile_text_secondary)

            // Adicionais
            if (p.total_adicional > 0) {
                addDetalheRow(card, dp, "+ Adicionais", "+ R$ %,.2f".format(p.total_adicional),
                    R.color.mobile_semantic_success)
            }

            // Descontos
            if (p.total_desconto > 0) {
                addDetalheRow(card, dp, "− Descontos", "− R$ %,.2f".format(p.total_desconto),
                    R.color.mobile_semantic_pending)
            }

            // Lancamentos detalhados
            if (p.lancamentos.isNotEmpty()) {
                val divider = View(this).apply {
                    setBackgroundColor(ContextCompat.getColor(this@PagamentosActivity, R.color.mobile_card_border))
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    lp.topMargin = (6 * dp).toInt()
                    lp.bottomMargin = (6 * dp).toInt()
                    layoutParams = lp
                }
                card.addView(divider)

                p.lancamentos.forEach { lan ->
                    val sinal = if (lan.natureza == "adicional") "+" else "−"
                    val label = when (lan.tipo) {
                        "adiantamento" -> "Adiantamento"
                        "decimo_terceiro" -> "13º Salário"
                        "ferias" -> "Férias"
                        "hora_extra" -> "Hora Extra"
                        "bonus" -> "Bônus"
                        "rescisao" -> "Rescisão"
                        "desconto_avulso" -> "Desconto"
                        else -> lan.tipo
                    }
                    val desc = if (lan.descricao.isNotBlank() && lan.descricao != lan.tipo) " — ${lan.descricao}" else ""
                    val cor = if (lan.natureza == "adicional") R.color.mobile_semantic_success else R.color.mobile_semantic_pending
                    addDetalheRow(card, dp, "  $label$desc", "$sinal R$ %,.2f".format(lan.valor), cor,
                        textSize = 12f)
                }
            }

            // Observação
            if (p.obs.isNotBlank()) {
                val tvObs = TextView(this).apply {
                    text = "Obs: ${p.obs}"
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(this@PagamentosActivity, R.color.mobile_text_secondary))
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = (6 * dp).toInt()
                    layoutParams = lp
                }
                card.addView(tvObs)
            }

            containerPagamentos.addView(card)
        }
    }

    private fun addDetalheRow(
        parent: LinearLayout, dp: Float,
        label: String, value: String,
        colorRes: Int, textSize: Float = 13f
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (2 * dp).toInt()
            layoutParams = lp
        }
        val tvLabel = TextView(this).apply {
            text = label
            this.textSize = textSize
            setTextColor(ContextCompat.getColor(this@PagamentosActivity, R.color.mobile_text_secondary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvValue = TextView(this).apply {
            text = value
            this.textSize = textSize
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@PagamentosActivity, colorRes))
        }
        row.addView(tvLabel)
        row.addView(tvValue)
        parent.addView(row)
    }

    private fun compFmt(comp: String): String {
        if (comp.length < 7 || comp[4] != '-') return comp
        val ano = comp.substring(0, 4)
        val mes = comp.substring(5, 7).toIntOrNull() ?: return comp
        val meses = listOf("", "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
            "Jul", "Ago", "Set", "Out", "Nov", "Dez")
        return "${meses.getOrElse(mes) { comp }}/$ano"
    }
}
