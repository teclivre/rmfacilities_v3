package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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

class CorrecoesPontoActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private lateinit var tvStatus: TextView
    private lateinit var container: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var chipContainer: LinearLayout

    private var todosItens: List<CorrecaoPontoItem> = emptyList()
    private var filtroAtual: String = "todos" // todos | pendente | resolvido | rejeitado

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_correcoes_ponto)

        api = ApiClient(SessionManager(this))
        tvStatus = findViewById(R.id.tvStatusCorrecoes)
        container = findViewById(R.id.containerCorrecoes)
        swipeRefresh = findViewById(R.id.swipeRefreshCorrecoes)
        chipContainer = findViewById(R.id.chipFiltrosCorrecoes)

        swipeRefresh.setColorSchemeResources(R.color.accent)
        swipeRefresh.setOnRefreshListener { carregar() }
        findViewById<TextView>(R.id.btnVoltarCorrecoes).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnNovaCorrecao).setOnClickListener {
            startActivity(Intent(this, SolicitacaoCorrecaoPontoActivity::class.java))
        }

        montarChips()
        carregar()
    }

    override fun onResume() {
        super.onResume()
        carregar()
    }

    private fun montarChips() {
        val dp = resources.displayMetrics.density
        val opcoes = listOf("todos" to "Todas", "pendente" to "Pendentes", "resolvido" to "Resolvidas", "rejeitado" to "Rejeitadas")
        chipContainer.removeAllViews()
        for ((key, label) in opcoes) {
            val chip = TextView(this).apply {
                text = label
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setPadding((12 * dp).toInt(), (5 * dp).toInt(), (12 * dp).toInt(), (5 * dp).toInt())
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * dp).toInt() }
                tag = key
            }
            atualizarChipVisual(chip, key == filtroAtual)
            chip.setOnClickListener {
                filtroAtual = key
                for (i in 0 until chipContainer.childCount) {
                    val c = chipContainer.getChildAt(i) as? TextView ?: continue
                    atualizarChipVisual(c, c.tag == filtroAtual)
                }
                aplicarFiltro()
            }
            chipContainer.addView(chip)
        }
    }

    private fun atualizarChipVisual(chip: TextView, ativo: Boolean) {
        val dp = resources.displayMetrics.density
        chip.background = GradientDrawable().apply {
            cornerRadius = 20 * dp
            setColor(if (ativo) ContextCompat.getColor(this@CorrecoesPontoActivity, R.color.accent) else 0x22FFFFFF)
            setStroke(1, if (ativo) ContextCompat.getColor(this@CorrecoesPontoActivity, R.color.accent) else 0x44FFFFFF)
        }
        chip.setTextColor(if (ativo) android.graphics.Color.WHITE else ContextCompat.getColor(this, R.color.mobile_text_secondary))
    }

    private fun aplicarFiltro() {
        val filtrado = when (filtroAtual) {
            "todos" -> todosItens
            else -> todosItens.filter { it.status == filtroAtual }
        }
        render(filtrado)
    }

    private fun carregar() {
        tvStatus.text = "Carregando solicitações..."
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_info))
        tvStatus.visibility = View.VISIBLE

        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) {
                try { api.getCorrecoesPonto() }
                catch (e: Exception) { CorrecaoPontoListResponse(ok = false, erro = e.message) }
            }
            withContext(Dispatchers.Main) {
                swipeRefresh.isRefreshing = false
                if (resp.ok) {
                    tvStatus.visibility = View.GONE
                    todosItens = resp.itens
                    aplicarFiltro()
                } else {
                    tvStatus.text = resp.erro ?: "Erro ao carregar solicitações."
                    tvStatus.setTextColor(ContextCompat.getColor(this@CorrecoesPontoActivity, R.color.mobile_semantic_pending))
                }
            }
        }
    }

    private fun render(itens: List<CorrecaoPontoItem>) {
        container.removeAllViews()
        val dp = resources.displayMetrics.density

        if (itens.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Nenhuma solicitação enviada ainda."
                setTextColor(ContextCompat.getColor(this@CorrecoesPontoActivity, R.color.mobile_text_secondary))
                textSize = 14f
                setPadding(0, (24 * dp).toInt(), 0, 0)
            })
            return
        }

        for (item in itens) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@CorrecoesPontoActivity, R.drawable.bg_glass_widget)
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * dp).toInt() }
            }

            // Header: data + chip de status
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val dataFmt = try {
                item.data_ref?.split("-")?.let { if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else item.data_ref } ?: "--"
            } catch (_: Exception) { item.data_ref ?: "--" }

            topRow.addView(TextView(this).apply {
                text = "📅 $dataFmt"
                setTextColor(ContextCompat.getColor(this@CorrecoesPontoActivity, R.color.mobile_text_primary))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            val (chipLabel, chipColor) = when (item.status) {
                "resolvido" -> "✅ Resolvido" to 0xFF2E7D32.toInt()
                "rejeitado" -> "❌ Rejeitado" to 0xFFB71C1C.toInt()
                else -> "⏳ Pendente" to 0xFFE65100.toInt()
            }
            topRow.addView(TextView(this).apply {
                text = chipLabel
                setTextColor(android.graphics.Color.WHITE)
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setPadding((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (3 * dp).toInt())
                background = GradientDrawable().apply { setColor(chipColor); cornerRadius = 20 * dp }
            })
            card.addView(topRow)

            // Tipo do problema
            val tipoLabel = when (item.tipo_problema) {
                "horario_errado" -> "Horário errado"
                "marcacao_faltando" -> "Marcação faltando"
                "marcacao_extra" -> "Marcação extra"
                else -> "Outro"
            }
            card.addView(TextView(this).apply {
                text = "Tipo: $tipoLabel"
                setTextColor(ContextCompat.getColor(this@CorrecoesPontoActivity, R.color.mobile_text_secondary))
                textSize = 12f
                setPadding(0, (6 * dp).toInt(), 0, 0)
            })

            // Horário esperado se informado
            if (!item.horario_esperado.isNullOrBlank()) {
                card.addView(TextView(this).apply {
                    text = "Horário correto: ${item.horario_esperado}"
                    setTextColor(ContextCompat.getColor(this@CorrecoesPontoActivity, R.color.mobile_text_secondary))
                    textSize = 12f
                })
            }

            // Observação
            if (!item.observacao.isNullOrBlank()) {
                card.addView(TextView(this).apply {
                    text = item.observacao
                    setTextColor(ContextCompat.getColor(this@CorrecoesPontoActivity, R.color.mobile_text_primary))
                    textSize = 13f
                    setPadding(0, (6 * dp).toInt(), 0, 0)
                })
            }

            // Resposta do RH se houver
            if (!item.motivo_admin.isNullOrBlank()) {
                card.addView(View(this).apply {
                    setBackgroundColor(ContextCompat.getColor(this@CorrecoesPontoActivity, R.color.border))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                        topMargin = (8 * dp).toInt(); bottomMargin = (6 * dp).toInt()
                    }
                })
                card.addView(TextView(this).apply {
                    text = "Resposta RH: ${item.motivo_admin}"
                    setTextColor(ContextCompat.getColor(this@CorrecoesPontoActivity,
                        if (item.status == "resolvido") R.color.mobile_semantic_success else R.color.mobile_semantic_pending))
                    textSize = 12f
                    setTypeface(null, Typeface.ITALIC)
                })
            }

            // Data/hora da solicitação
            if (!item.criado_fmt.isNullOrBlank()) {
                card.addView(TextView(this).apply {
                    text = "Enviada em ${item.criado_fmt}"
                    setTextColor(ContextCompat.getColor(this@CorrecoesPontoActivity, R.color.mobile_text_secondary))
                    textSize = 10f
                    setPadding(0, (6 * dp).toInt(), 0, 0)
                })
            }

            container.addView(card)
        }
    }
}
