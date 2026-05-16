package br.com.rmfacilities.funcionarioapp

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class PontoHistoricoActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private lateinit var tvStatus: TextView
    private lateinit var containerHistorico: LinearLayout
    private lateinit var swipeRefreshHistorico: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ponto_historico)

        api = ApiClient(SessionManager(this))
        tvStatus = findViewById(R.id.tvStatus)
        containerHistorico = findViewById(R.id.containerHistorico)
        swipeRefreshHistorico = findViewById(R.id.swipeRefreshHistorico)

        swipeRefreshHistorico.setColorSchemeResources(R.color.accent)
        swipeRefreshHistorico.setOnRefreshListener { carregarHistorico() }

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { finish() }

        carregarHistorico()
    }

    private fun carregarHistorico() {
        tvStatus.text = "Carregando histórico..."
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_info))
        tvStatus.visibility = View.VISIBLE

        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) {
                try { api.getPontoHistorico(7) }
                catch (e: Exception) { PontoHistoricoResponse(ok = false, erro = e.message) }
            }
            withContext(Dispatchers.Main) {
                swipeRefreshHistorico.isRefreshing = false
                if (resp.ok) {
                    tvStatus.visibility = View.GONE
                    renderHistorico(resp.dias)
                } else {
                    tvStatus.text = resp.erro ?: "Erro ao carregar histórico."
                    tvStatus.setTextColor(ContextCompat.getColor(this@PontoHistoricoActivity, R.color.mobile_semantic_pending))
                }
            }
        }
    }

    private fun renderHistorico(dias: List<PontoResumo>) {
        containerHistorico.removeAllViews()
        val dp = resources.displayMetrics.density

        if (dias.isEmpty()) {
            val tvVazio = TextView(this).apply {
                text = "Nenhuma marcação encontrada."
                setTextColor(ContextCompat.getColor(this@PontoHistoricoActivity, R.color.mobile_text_secondary))
                textSize = 13f
                setPadding(0, (8 * dp).toInt(), 0, 0)
            }
            containerHistorico.addView(tvVazio)
            return
        }

        val diasSemana = mapOf(
            "monday" to "Segunda", "tuesday" to "Terça", "wednesday" to "Quarta",
            "thursday" to "Quinta", "friday" to "Sexta", "saturday" to "Sábado", "sunday" to "Domingo"
        )

        for (dia in dias) {
            val dataRef = dia.data_ref ?: continue
            // Header do dia
            val headerCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@PontoHistoricoActivity, R.drawable.bg_glass_widget)
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            }
            val headerParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * dp).toInt() }
            headerCard.layoutParams = headerParams

            // Formata data legível
            val dataFmt = try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR"))
                val d = sdf.parse(dataRef)
                val out = SimpleDateFormat("EEE, dd/MM/yyyy", Locale("pt", "BR"))
                d?.let { out.format(it) } ?: dataRef
            } catch (_: Exception) { dataRef }

            val tvDia = TextView(this).apply {
                text = dataFmt
                setTextColor(ContextCompat.getColor(this@PontoHistoricoActivity, R.color.mobile_text_primary))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            }
            headerCard.addView(tvDia)

            // Resumo do dia
            val resumoRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (6 * dp).toInt(), 0, 0)
            }
            listOf(
                "Trabalhadas" to (dia.horas_trabalhadas_fmt ?: "00:00"),
                "Esperadas" to (dia.horas_esperadas_fmt ?: "00:00"),
                "Saldo" to (dia.saldo_fmt ?: "00:00")
            ).forEach { (label, valor) ->
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                col.addView(TextView(this).apply {
                    text = label
                    setTextColor(ContextCompat.getColor(this@PontoHistoricoActivity, R.color.mobile_text_secondary))
                    textSize = 10f
                })
                col.addView(TextView(this).apply {
                    text = valor
                    setTextColor(ContextCompat.getColor(this@PontoHistoricoActivity, R.color.mobile_text_primary))
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                })
                resumoRow.addView(col)
            }

            // Indicador de fechamento
            if (dia.fechado) {
                val tvFechado = TextView(this).apply {
                    text = "✅ Fechado pelo gestor${if (!dia.fechado_por.isNullOrBlank()) " (${dia.fechado_por})" else ""}"
                    setTextColor(ContextCompat.getColor(this@PontoHistoricoActivity, R.color.mobile_semantic_success))
                    textSize = 10f
                    setPadding(0, (4 * dp).toInt(), 0, 0)
                }
                headerCard.addView(resumoRow)
                headerCard.addView(tvFechado)
            } else {
                headerCard.addView(resumoRow)
            }

            // Inconsistências
            val incons = dia.inconsistencias
            if (incons.isNotEmpty()) {
                val tvIncons = TextView(this).apply {
                    text = "⚠ " + incons.joinToString(" | ")
                    setTextColor(ContextCompat.getColor(this@PontoHistoricoActivity, R.color.mobile_semantic_pending))
                    textSize = 10f
                    setPadding(0, (4 * dp).toInt(), 0, 0)
                }
                headerCard.addView(tvIncons)
            }

            containerHistorico.addView(headerCard)

            // Marcações do dia
            val marcacoes = dia.marcacoes
            if (marcacoes.isEmpty()) {
                val tvSem = TextView(this).apply {
                    text = "  Nenhuma marcação neste dia."
                    setTextColor(ContextCompat.getColor(this@PontoHistoricoActivity, R.color.mobile_text_secondary))
                    textSize = 11f
                    setPadding((6 * dp).toInt(), (2 * dp).toInt(), 0, (10 * dp).toInt())
                }
                containerHistorico.addView(tvSem)
            } else {
                for (m in marcacoes) {
                    val emoji = when (m.tipo) {
                        "entrada" -> "🟢"
                        "saida_intervalo" -> "☕"
                        "retorno_intervalo" -> "🔵"
                        "saida" -> "🔴"
                        else -> "🕐"
                    }
                    val rowCard = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = ContextCompat.getDrawable(
                            this@PontoHistoricoActivity, R.drawable.bg_home_card_soft
                        )
                        setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
                    }
                    val rowParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = (8 * dp).toInt()
                        bottomMargin = (4 * dp).toInt()
                    }
                    rowCard.layoutParams = rowParams

                    rowCard.addView(TextView(this).apply {
                        text = emoji
                        textSize = 18f
                        setPadding(0, 0, (10 * dp).toInt(), 0)
                    })
                    val infoCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    infoCol.addView(TextView(this).apply {
                        text = m.hora_fmt ?: "--:--"
                        setTextColor(ContextCompat.getColor(this@PontoHistoricoActivity, R.color.mobile_text_primary))
                        textSize = 16f
                        setTypeface(null, Typeface.BOLD)
                    })
                    infoCol.addView(TextView(this).apply {
                        text = m.tipo_label ?: m.tipo ?: "Marcação"
                        setTextColor(ContextCompat.getColor(this@PontoHistoricoActivity, R.color.mobile_text_secondary))
                        textSize = 10f
                    })
                    if (!m.observacao.isNullOrBlank()) {
                        infoCol.addView(TextView(this).apply {
                            text = m.observacao
                            setTextColor(ContextCompat.getColor(this@PontoHistoricoActivity, R.color.mobile_text_secondary))
                            textSize = 10f
                        })
                    }
                    rowCard.addView(infoCol)
                    containerHistorico.addView(rowCard)
                }
            }

            // Espaço entre dias + botão solicitar correção
            val bottomRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (8 * dp).toInt()
                    bottomMargin = (12 * dp).toInt()
                }
            }
            val btnCorrecao = android.widget.Button(this).apply {
                text = "📋 Solicitar correção"
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@PontoHistoricoActivity, R.color.mobile_semantic_info))
                background = null
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val dataRefFinal = dataRef
            btnCorrecao.setOnClickListener {
                val intent = Intent(this, SolicitacaoCorrecaoPontoActivity::class.java)
                intent.putExtra("data_ref", dataRefFinal)
                startActivity(intent)
            }
            bottomRow.addView(btnCorrecao)
            containerHistorico.addView(bottomRow)
        }
    }
}
