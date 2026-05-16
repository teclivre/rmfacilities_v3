package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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

class AlteracoesCadastraisActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private lateinit var tvStatus: TextView
    private lateinit var container: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val campoLabels = mapOf(
        "nome" to "Nome",
        "cargo" to "Cargo",
        "funcao" to "Função",
        "setor" to "Setor",
        "endereco" to "Endereço",
        "endereco_numero" to "Número",
        "endereco_complemento" to "Complemento",
        "endereco_bairro" to "Bairro",
        "cidade" to "Cidade",
        "estado" to "UF",
        "cep" to "CEP",
        "banco_nome" to "Banco",
        "banco_agencia" to "Agência",
        "banco_conta" to "Conta",
        "banco_tipo_conta" to "Tipo conta",
        "banco_pix" to "PIX",
        "banco_codigo" to "Cód. banco"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alteracoes_cadastrais)

        api = ApiClient(SessionManager(this))
        tvStatus = findViewById(R.id.tvStatusAlteracoes)
        container = findViewById(R.id.containerAlteracoes)
        swipeRefresh = findViewById(R.id.swipeRefreshAlteracoes)

        swipeRefresh.setColorSchemeResources(R.color.accent)
        swipeRefresh.setOnRefreshListener { carregar() }
        findViewById<TextView>(R.id.btnVoltarAlteracoes).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnNovaSolicitacao).setOnClickListener {
            // retorna para PerfilActivity com intent para abrir dialog
            finish()
        }

        carregar()
    }

    override fun onResume() {
        super.onResume()
        carregar()
    }

    private fun carregar() {
        tvStatus.text = "Carregando histórico..."
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_info))
        tvStatus.visibility = View.VISIBLE

        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) {
                try { api.getAlteracoesCadastrais() }
                catch (e: Exception) { AlteracaoListResponse(ok = false, erro = e.message) }
            }
            withContext(Dispatchers.Main) {
                swipeRefresh.isRefreshing = false
                if (resp.ok) {
                    tvStatus.visibility = View.GONE
                    render(resp.items)
                } else {
                    tvStatus.text = resp.erro ?: "Erro ao carregar histórico."
                    tvStatus.setTextColor(ContextCompat.getColor(this@AlteracoesCadastraisActivity, R.color.mobile_semantic_pending))
                }
            }
        }
    }

    private fun render(itens: List<AlteracaoSolicitacaoItem>) {
        container.removeAllViews()
        val dp = resources.displayMetrics.density

        if (itens.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Nenhuma solicitação enviada ainda.\nUse o Perfil para solicitar alterações nos seus dados."
                setTextColor(ContextCompat.getColor(this@AlteracoesCadastraisActivity, R.color.mobile_text_secondary))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, (24 * dp).toInt(), 0, 0)
            })
            return
        }

        for (item in itens) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@AlteracoesCadastraisActivity, R.drawable.bg_glass_widget)
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * dp).toInt() }
            }

            // Header: data + chip status
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            topRow.addView(TextView(this).apply {
                text = "📝 ${item.solicitado_fmt ?: "--"}"
                setTextColor(ContextCompat.getColor(this@AlteracoesCadastraisActivity, R.color.mobile_text_primary))
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            val (chipLabel, chipColor) = when (item.status) {
                "aprovado" -> "✅ Aprovado" to 0xFF2E7D32.toInt()
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

            // Campos solicitados
            if (item.payload.isNotEmpty()) {
                card.addView(View(this).apply {
                    setBackgroundColor(ContextCompat.getColor(this@AlteracoesCadastraisActivity, R.color.border))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                        topMargin = (8 * dp).toInt(); bottomMargin = (6 * dp).toInt()
                    }
                })
                card.addView(TextView(this).apply {
                    text = "Campos solicitados:"
                    setTextColor(ContextCompat.getColor(this@AlteracoesCadastraisActivity, R.color.mobile_text_secondary))
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, (4 * dp).toInt())
                })
                for ((campo, valor) in item.payload) {
                    val label = campoLabels[campo] ?: campo.replace("_", " ").replaceFirstChar { it.uppercase() }
                    card.addView(TextView(this).apply {
                        text = "• $label: $valor"
                        setTextColor(ContextCompat.getColor(this@AlteracoesCadastraisActivity, R.color.mobile_text_primary))
                        textSize = 12f
                    })
                }
            }

            // Observação
            if (!item.observacao.isNullOrBlank()) {
                card.addView(TextView(this).apply {
                    text = "Obs: ${item.observacao}"
                    setTextColor(ContextCompat.getColor(this@AlteracoesCadastraisActivity, R.color.mobile_text_secondary))
                    textSize = 12f
                    setPadding(0, (6 * dp).toInt(), 0, 0)
                    setTypeface(null, Typeface.ITALIC)
                })
            }

            // Resposta do RH
            if (!item.motivo_admin.isNullOrBlank()) {
                card.addView(View(this).apply {
                    setBackgroundColor(ContextCompat.getColor(this@AlteracoesCadastraisActivity, R.color.border))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                        topMargin = (8 * dp).toInt(); bottomMargin = (6 * dp).toInt()
                    }
                })
                card.addView(TextView(this).apply {
                    text = "RH: ${item.motivo_admin}"
                    setTextColor(ContextCompat.getColor(this@AlteracoesCadastraisActivity,
                        if (item.status == "aprovado") R.color.mobile_semantic_success else R.color.mobile_semantic_pending))
                    textSize = 12f
                    setTypeface(null, Typeface.ITALIC)
                })
                if (!item.analisado_fmt.isNullOrBlank()) {
                    card.addView(TextView(this).apply {
                        text = "Analisado em ${item.analisado_fmt}"
                        setTextColor(ContextCompat.getColor(this@AlteracoesCadastraisActivity, R.color.mobile_text_secondary))
                        textSize = 10f
                        setPadding(0, (2 * dp).toInt(), 0, 0)
                    })
                }
            }

            container.addView(card)
        }
    }
}
