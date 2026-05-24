package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PontoEspelhoActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private lateinit var tvStatus: TextView
    private lateinit var containerCompetencias: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ponto_espelho)

        api = ApiClient(SessionManager(this))
        tvStatus = findViewById(R.id.tvStatus)
        containerCompetencias = findViewById(R.id.containerCompetencias)

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { finish() }

        carregarStatus()
    }

    private fun carregarStatus() {
        tvStatus.text = "Carregando competências..."
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_info))
        tvStatus.visibility = View.VISIBLE

        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) {
                try { api.getPontoEspelhoStatus() }
                catch (e: Exception) { PontoEspelhoStatusResponse(ok = false, erro = e.message) }
            }
            withContext(Dispatchers.Main) {
                if (resp.ok) {
                    tvStatus.visibility = View.GONE
                    renderCompetencias(resp.competencias)
                } else {
                    tvStatus.text = resp.erro ?: "Erro ao carregar folhas de ponto."
                    tvStatus.setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.mobile_semantic_pending))
                }
            }
        }
    }

    private fun renderCompetencias(competencias: List<PontoEspelhoCompetencia>) {
        containerCompetencias.removeAllViews()
        val dp = resources.displayMetrics.density

        if (competencias.isEmpty()) {
            val tvVazio = TextView(this).apply {
                text = "Nenhuma competência encontrada."
                setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.mobile_text_secondary))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, (32 * dp).toInt(), 0, 0)
            }
            containerCompetencias.addView(tvVazio)
            return
        }

        // Aviso sobre download
        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ContextCompat.getDrawable(this@PontoEspelhoActivity, R.drawable.bg_glass_widget)
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * dp).toInt() }
        }
        val tvInfo = TextView(this).apply {
            text = "ℹ️  O download do PDF fica disponível após o fechamento pelo gestor."
            setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.mobile_text_secondary))
            textSize = 11.5f
        }
        infoCard.addView(tvInfo)
        containerCompetencias.addView(infoCard)

        for (comp in competencias) {
            // Card principal com borda colorida à esquerda
            val cardWrapper = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (12 * dp).toInt() }
                background = ContextCompat.getDrawable(this@PontoEspelhoActivity, R.drawable.bg_glass_widget)
                clipToOutline = true
            }

            // Barra colorida à esquerda
            val accentBar = View(this).apply {
                val barColor = if (comp.pode_baixar)
                    ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_badge_download)
                else
                    ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_accent_btn)
                setBackgroundColor(barColor)
                layoutParams = FrameLayout.LayoutParams((4 * dp).toInt(), FrameLayout.LayoutParams.MATCH_PARENT)
            }
            cardWrapper.addView(accentBar)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((18 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
            }
            cardWrapper.addView(card)

            // Linha superior: label + badge de status
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * dp).toInt() }
            }

            val tvLabel = TextView(this).apply {
                text = comp.label
                setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.mobile_text_primary))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            topRow.addView(tvLabel)

            // Contador de dias registrados
            if (comp.fechamentos_dias > 0) {
                val tvDias = TextView(this).apply {
                    text = "${comp.fechamentos_dias} dias"
                    setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.mobile_text_secondary))
                    textSize = 11f
                    setPadding(0, 0, (8 * dp).toInt(), 0)
                }
                topRow.addView(tvDias, 1)
            }

            // Badge de status
            val badgeBg = if (comp.pode_baixar)
                ContextCompat.getColor(this, R.color.espelho_badge_download)
            else
                ContextCompat.getColor(this, R.color.espelho_accent_btn)
            val badgeText = if (comp.pode_baixar)
                "✓ Fechada"
            else
                "⏳ Aberta"
            val tvBadge = TextView(this).apply {
                text = badgeText
                setTextColor(Color.WHITE)
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setPadding((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (3 * dp).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(badgeBg)
                    cornerRadius = 20 * dp
                }
            }
            topRow.addView(tvBadge)
            card.addView(topRow)

            // Botões lado a lado
            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val btnVisualizar = MaterialButton(this).apply {
                text = "👁  Visualizar"
                textSize = 12.5f
                letterSpacing = 0.01f
                cornerRadius = (10 * dp).toInt()
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_accent_btn))
                setTextColor(Color.WHITE)
                elevation = 2f
                stateListAnimator = null
                minWidth = 0
                minimumWidth = 0
                insetTop = 0
                insetBottom = 0
                setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * dp).toInt() }
            }
            btnVisualizar.setOnClickListener { visualizarFolha(comp.competencia, comp.label) }
            btnRow.addView(btnVisualizar)

            if (comp.pode_baixar) {
                val btnBaixar = MaterialButton(this).apply {
                    text = "⬇  Baixar PDF"
                    textSize = 12.5f
                    letterSpacing = 0.01f
                    cornerRadius = (10 * dp).toInt()
                    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_badge_download))
                    setTextColor(Color.WHITE)
                    elevation = 2f
                    stateListAnimator = null
                    minWidth = 0
                    minimumWidth = 0
                    insetTop = 0
                    insetBottom = 0
                    setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                btnBaixar.setOnClickListener {
                    btnBaixar.isEnabled = false
                    btnBaixar.text = "Baixando..."
                    baixarPdf(comp.competencia, comp.label) {
                        btnBaixar.isEnabled = true
                        btnBaixar.text = "⬇  Baixar PDF"
                    }
                }
                btnRow.addView(btnBaixar)
            }
            card.addView(btnRow)

            containerCompetencias.addView(cardWrapper)
        }
    }

    private fun visualizarFolha(competencia: String, label: String) {
        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) {
                try { api.getPontoEspelhoDados(competencia) }
                catch (e: Exception) { PontoEspelhoDadosResponse(ok = false, erro = e.message) }
            }
            withContext(Dispatchers.Main) {
                if (!resp.ok || resp.dias.isEmpty()) {
                    Toast.makeText(
                        this@PontoEspelhoActivity,
                        resp.erro ?: "Sem dados para esta competência.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@withContext
                }

                val dp = resources.displayMetrics.density
                val colorHeader = ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_header_bg)
                val colorRowEven = ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_row_even)
                val colorRowOdd = ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_row_odd)
                val colorSeparator = ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_separator)
                val colorTextPrimary = ContextCompat.getColor(this@PontoEspelhoActivity, R.color.text_primary)
                val colorTextMuted = ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_text_muted)
                val colorAccent = ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_accent_btn)
                val colorSuccess = ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_ok)

                val root = LinearLayout(this@PontoEspelhoActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_row_highlight))
                }

                // ── Cabeçalho do dialog ─────────────────────────────────
                val header = LinearLayout(this@PontoEspelhoActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(colorHeader)
                    setPadding((20 * dp).toInt(), (18 * dp).toInt(), (20 * dp).toInt(), (18 * dp).toInt())
                }
                val tvTitulo = TextView(this@PontoEspelhoActivity).apply {
                    text = "📋  Folha de Ponto"
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                }
                val tvSubtitulo = TextView(this@PontoEspelhoActivity).apply {
                    text = label
                    setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_text_muted))
                    textSize = 13f
                    setPadding(0, (2 * dp).toInt(), 0, (8 * dp).toInt())
                }
                val tvTotalHoras = TextView(this@PontoEspelhoActivity).apply {
                    text = "Total trabalhado: ${resp.total_horas ?: "--:--"}"
                    setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_ok))
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                }
                header.addView(tvTitulo)
                header.addView(tvSubtitulo)
                header.addView(tvTotalHoras)

                // ── Painel de totais: HE 50%, HE 100%, Noturno, Intrajornada ─
                val tot = resp.totais
                if (tot != null) {
                    val kpiRow = LinearLayout(this@PontoEspelhoActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, (10 * dp).toInt(), 0, 0)
                    }
                    fun kpiChip(label: String, value: String?, mins: Int, bgColor: Int) {
                        val v = value ?: "00:00"
                        val chip = LinearLayout(this@PontoEspelhoActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(if (mins > 0) bgColor else Color.parseColor("#33FFFFFF"))
                                cornerRadius = 8 * dp
                            }
                            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { marginEnd = (6 * dp).toInt() }
                        }
                        chip.addView(TextView(this@PontoEspelhoActivity).apply {
                            text = v
                            setTextColor(Color.WHITE)
                            textSize = 12f
                            setTypeface(null, Typeface.BOLD)
                            gravity = Gravity.CENTER
                        })
                        chip.addView(TextView(this@PontoEspelhoActivity).apply {
                            text = label
                            setTextColor(Color.parseColor("#CCFFFFFF"))
                            textSize = 9f
                            gravity = Gravity.CENTER
                        })
                        kpiRow.addView(chip)
                    }
                    kpiChip("HE 50%",  tot.he_50_fmt,  tot.he_50_min,  Color.parseColor("#E07C00"))
                    kpiChip("HE 100%", tot.he_100_fmt, tot.he_100_min, Color.parseColor("#C0392B"))
                    kpiChip("Noturno", tot.noturno_fmt, tot.noturno_min, Color.parseColor("#1A73E8"))
                    kpiChip("Intrajornada", tot.intrajornada_fmt, tot.intrajornada_min, Color.parseColor("#2E7D32"))
                    header.addView(kpiRow)
                }
                root.addView(header)

                // ── Mini-gráfico de barras horizontais ───────────────────
                val diasComHoras = resp.dias.filter { it.horas_trabalhadas_min > 0 || it.horas_esperadas_min > 0 }
                if (diasComHoras.isNotEmpty()) {
                    val maxMin = diasComHoras.maxOf { maxOf(it.horas_trabalhadas_min, it.horas_esperadas_min, 1) }
                    val graficoScroll = android.widget.HorizontalScrollView(this@PontoEspelhoActivity).apply {
                        isHorizontalScrollBarEnabled = false
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    val graficoContainer = LinearLayout(this@PontoEspelhoActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setBackgroundColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.background))
                        setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
                        gravity = Gravity.BOTTOM
                    }
                    val barW = (16 * dp).toInt()
                    val barMaxH = (60 * dp).toInt()
                    val margin = (3 * dp).toInt()
                    for (dia in diasComHoras) {
                        val col = LinearLayout(this@PontoEspelhoActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { marginEnd = margin }
                        }
                        val propTrab = dia.horas_trabalhadas_min.toFloat() / maxMin
                        val propEsp = if (dia.horas_esperadas_min > 0) dia.horas_esperadas_min.toFloat() / maxMin else 1f
                        val barColor = when {
                            dia.horas_esperadas_min > 0 && dia.horas_trabalhadas_min >= dia.horas_esperadas_min ->
                                ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_ok)
                            dia.horas_trabalhadas_min >= 240 ->
                                ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_parcial)
                            else ->
                                ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_falta)
                        }
                        // Barra de fundo (esperado)
                        val barWrapper = FrameLayout(this@PontoEspelhoActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(barW, (barMaxH * propEsp).toInt().coerceAtLeast((4 * dp).toInt()))
                        }
                        val barBg = View(this@PontoEspelhoActivity).apply {
                            setBackgroundColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_separator))
                            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        }
                        val barFill = View(this@PontoEspelhoActivity).apply {
                            setBackgroundColor(barColor)
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                ((barMaxH * propEsp) * propTrab / propEsp).toInt().coerceAtLeast(0)
                            ).apply { gravity = Gravity.BOTTOM }
                        }
                        barWrapper.addView(barBg)
                        barWrapper.addView(barFill)
                        col.addView(barWrapper)
                        col.addView(TextView(this@PontoEspelhoActivity).apply {
                            text = (dia.data_fmt ?: "").take(5)
                            textSize = 7f
                            setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_text_muted))
                            gravity = Gravity.CENTER
                            layoutParams = LinearLayout.LayoutParams(barW + (4 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                        })
                        graficoContainer.addView(col)
                    }
                    graficoScroll.addView(graficoContainer)
                    root.addView(graficoScroll)
                }

                // ── Tabela com scroll ────────────────────────────────────
                val scroll = ScrollView(this@PontoEspelhoActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (420 * dp).toInt()
                    )
                }
                val table = LinearLayout(this@PontoEspelhoActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(colorRowOdd)
                }
                scroll.addView(table)

                // Cabeçalho da tabela
                fun makeCell(txt: String, weight: Float, isHeader: Boolean, alignEnd: Boolean = false) =
                    TextView(this@PontoEspelhoActivity).apply {
                        text = txt
                        textSize = if (isHeader) 10f else 11.5f
                        setTypeface(null, if (isHeader) Typeface.BOLD else Typeface.NORMAL)
                        setTextColor(if (isHeader) ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_header_text) else colorTextPrimary)
                        gravity = if (alignEnd) Gravity.END or Gravity.CENTER_VERTICAL
                                  else if (weight > 2f) Gravity.START or Gravity.CENTER_VERTICAL
                                  else Gravity.CENTER
                        setPadding(
                            (if (weight > 2f) 12 else 6).let { (it * dp).toInt() }, (7 * dp).toInt(),
                            (if (alignEnd) 12 else 6).let { (it * dp).toInt() }, (7 * dp).toInt()
                        )
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                    }

                val headerRow = LinearLayout(this@PontoEspelhoActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundColor(colorHeader)
                    addView(makeCell("DIA", 1.6f, true))
                    addView(makeCell("BATIDAS", 4.5f, true))
                    addView(makeCell("TOTAL", 1.5f, true, alignEnd = true))
                }
                table.addView(headerRow)

                // Separador abaixo do header
                table.addView(View(this@PontoEspelhoActivity).apply {
                    setBackgroundColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_accent_btn))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (2 * dp).toInt())
                })

                val diasComMarcacoes = resp.dias.filter { it.tem_marcacoes }
                diasComMarcacoes.forEachIndexed { idx, dia ->
                    val rowBg = if (idx % 2 == 0) colorRowEven else colorRowOdd

                    val row = LinearLayout(this@PontoEspelhoActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setBackgroundColor(rowBg)
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // Célula data
                    val tvData = TextView(this@PontoEspelhoActivity).apply {
                        text = dia.data_fmt ?: ""
                        textSize = 11f
                        setTextColor(colorTextPrimary)
                        setTypeface(null, Typeface.BOLD)
                        setPadding((12 * dp).toInt(), (9 * dp).toInt(), (4 * dp).toInt(), (9 * dp).toInt())
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f)
                    }
                    row.addView(tvData)

                    // Célula batidas — cada batida é um chipzinho inline
                    val batidasCell = LinearLayout(this@PontoEspelhoActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setPadding((4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt())
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 4.5f)
                    }

                    if (dia.marcacoes.isNotEmpty()) {
                        dia.marcacoes.forEach { m ->
                            val chipColor = when (m.tipo) {
                                "entrada" -> ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_entrada)
                                "saida_intervalo" -> ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_saida_int)
                                "retorno_intervalo" -> ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_retorno_int)
                                "saida" -> ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_saida)
                                else -> ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_outro)
                            }
                            val chip = TextView(this@PontoEspelhoActivity).apply {
                                text = "${m.hora_fmt ?: "-"}"
                                setTextColor(Color.WHITE)
                                textSize = 9.5f
                                setTypeface(null, Typeface.BOLD)
                                setPadding((5 * dp).toInt(), (2 * dp).toInt(), (5 * dp).toInt(), (2 * dp).toInt())
                                background = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(chipColor)
                                    cornerRadius = 6 * dp
                                }
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { marginEnd = (3 * dp).toInt() }
                            }
                            batidasCell.addView(chip)
                        }
                    } else {
                        val tvVazio = TextView(this@PontoEspelhoActivity).apply {
                            text = "—"
                            setTextColor(colorTextMuted)
                            textSize = 11f
                        }
                        batidasCell.addView(tvVazio)
                    }
                    row.addView(batidasCell)

                    // Célula total horas (coluna vertical: horas + extras)
                    val horasCell = LinearLayout(this@PontoEspelhoActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.END
                        setPadding((4 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
                    }
                    horasCell.addView(TextView(this@PontoEspelhoActivity).apply {
                        text = dia.horas_trabalhadas_fmt ?: "-"
                        textSize = 11f
                        setTextColor(colorSuccess)
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.END
                    })
                    // Indicadores de extras se houver
                    val extras = buildList {
                        if ((dia.he_50_min) > 0) add("HE50 ${dia.he_50_fmt}")
                        if ((dia.he_100_min) > 0) add("HE100 ${dia.he_100_fmt}")
                        if ((dia.noturno_min) > 0) add("Not ${dia.noturno_fmt}")
                    }
                    if (extras.isNotEmpty()) {
                        horasCell.addView(TextView(this@PontoEspelhoActivity).apply {
                            text = extras.joinToString(" ")
                            textSize = 8f
                            setTextColor(Color.parseColor("#E07C00"))
                            gravity = Gravity.END
                            setTypeface(null, Typeface.BOLD)
                        })
                    }
                    // Alerta de inconsistência
                    if (dia.status == "inconsistente") {
                        horasCell.addView(TextView(this@PontoEspelhoActivity).apply {
                            text = "⚠ verificar"
                            textSize = 8f
                            setTextColor(Color.parseColor("#C0392B"))
                            gravity = Gravity.END
                        })
                    }
                    row.addView(horasCell)

                    table.addView(row)

                    // Linha separadora fina
                    table.addView(View(this@PontoEspelhoActivity).apply {
                        setBackgroundColor(colorSeparator)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    })
                }
                root.addView(scroll)

                // ── Legenda de cores ─────────────────────────────────────
                val legenda = LinearLayout(this@PontoEspelhoActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setBackgroundColor(colorHeader)
                    setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
                }
                listOf(
                    "E" to ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_entrada),
                    "SI" to ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_saida_int),
                    "RI" to ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_retorno_int),
                    "S" to ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_saida)
                ).forEach { (lbl, cor) ->
                    val chip = TextView(this@PontoEspelhoActivity).apply {
                        text = lbl
                        setTextColor(Color.WHITE)
                        textSize = 9f
                        setTypeface(null, Typeface.BOLD)
                        setPadding((6 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt())
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(cor); cornerRadius = 6 * dp
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { marginEnd = (4 * dp).toInt() }
                    }
                    legenda.addView(chip)
                    legenda.addView(TextView(this@PontoEspelhoActivity).apply {
                        text = when (lbl) {
                            "E" -> "Entrada"; "SI" -> "Saída Int."; "RI" -> "Retorno"; else -> "Saída"
                        } + "   "
                        setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_text_muted))
                        textSize = 9f
                    })
                }
                root.addView(legenda)

                // ── Botão Compartilhar ───────────────────────────────────
                val shareText = buildString {
                    appendLine("📋 FOLHA DE PONTO — $label")
                    appendLine("Funcionário: ${resp.funcionario ?: "-"}")
                    appendLine("Total trabalhado: ${resp.total_horas ?: "--:--"}")
                    val t = resp.totais
                    if (t != null) {
                        if (t.he_50_min > 0) appendLine("HE 50%: ${t.he_50_fmt}")
                        if (t.he_100_min > 0) appendLine("HE 100%: ${t.he_100_fmt}")
                        if (t.noturno_min > 0) appendLine("Adicional noturno: ${t.noturno_fmt}")
                        if (t.intrajornada_min > 0) appendLine("Intrajornada: ${t.intrajornada_fmt}")
                    }
                    appendLine("─".repeat(36))
                    resp.dias.filter { it.tem_marcacoes }.forEach { dia ->
                        val batidas = dia.marcacoes.joinToString("  ") { it.hora_fmt ?: "-" }
                        val extras = buildList {
                            if (dia.he_50_min > 0) add("HE50:${dia.he_50_fmt}")
                            if (dia.he_100_min > 0) add("HE100:${dia.he_100_fmt}")
                            if (dia.noturno_min > 0) add("Not:${dia.noturno_fmt}")
                            if (dia.status == "inconsistente") add("⚠")
                        }.joinToString(" ")
                        val extrasStr = if (extras.isNotEmpty()) "  [$extras]" else ""
                        appendLine("${dia.data_fmt ?: ""}  |  $batidas  |  ${dia.horas_trabalhadas_fmt ?: "-"}$extrasStr")
                    }
                    appendLine("─".repeat(36))
                    append("Exportado pelo RMFacilities App")
                }
                val shareRow = LinearLayout(this@PontoEspelhoActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    setBackgroundColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_header_bg))
                    setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
                }
                val btnShare = MaterialButton(this@PontoEspelhoActivity).apply {
                    text = "📤 Compartilhar"
                    textSize = 12f
                    cornerRadius = (10 * dp).toInt()
                    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.espelho_accent_btn))
                    setTextColor(Color.WHITE)
                    stateListAnimator = null
                    minWidth = 0; minimumWidth = 0; insetTop = 0; insetBottom = 0
                    setPadding((10 * dp).toInt(), (5 * dp).toInt(), (10 * dp).toInt(), (5 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                btnShare.setOnClickListener {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Folha de Ponto — $label")
                    }
                    startActivity(android.content.Intent.createChooser(intent, "Compartilhar folha"))
                }
                shareRow.addView(btnShare)
                root.addView(shareRow)

                MaterialAlertDialogBuilder(this@PontoEspelhoActivity)
                    .setView(root)
                    .setPositiveButton("Fechar", null)
                    .show()
            }
        }
    }

    private fun baixarPdf(competencia: String, label: String, onDone: () -> Unit) {
        lifecycleScope.launch {
            val (bytes, erro) = withContext(Dispatchers.IO) {
                try { api.baixarEspelhoPdf(competencia) }
                catch (e: Exception) { Pair(null, e.message) }
            }
            withContext(Dispatchers.Main) {
                onDone()
                if (bytes == null || erro != null) {
                    Toast.makeText(this@PontoEspelhoActivity, erro ?: "Falha ao baixar PDF.", Toast.LENGTH_LONG).show()
                    return@withContext
                }
                try {
                    val nomeFuncSeguro = "folha_ponto_${competencia}.pdf"
                    val cacheDir = File(cacheDir, "espelhos").also { it.mkdirs() }
                    val file = File(cacheDir, nomeFuncSeguro)
                    file.writeBytes(bytes)
                    val uri: Uri = FileProvider.getUriForFile(
                        this@PontoEspelhoActivity,
                        "$packageName.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        startActivity(Intent.createChooser(intent, "Abrir folha de ponto"))
                    } catch (_: Exception) {
                        Toast.makeText(
                            this@PontoEspelhoActivity,
                            "Nenhum app disponível para abrir PDF.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@PontoEspelhoActivity, "Erro ao salvar PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
