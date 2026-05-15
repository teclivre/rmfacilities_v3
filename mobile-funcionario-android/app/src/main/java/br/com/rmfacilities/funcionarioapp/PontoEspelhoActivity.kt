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
                val barColor = if (comp.pode_baixar) 0xFF2E7D32.toInt() else 0xFF1565C0.toInt()
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
            val badgeBg = if (comp.pode_baixar) 0xFF2E7D32.toInt() else 0xFF1565C0.toInt()
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
                backgroundTintList = ColorStateList.valueOf(0xFF1565C0.toInt())
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
                    backgroundTintList = ColorStateList.valueOf(0xFF2E7D32.toInt())
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
                val colorHeader = 0xFF0D2137.toInt()
                val colorRowEven = 0xFFF5F8FF.toInt()
                val colorRowOdd = 0xFFFFFFFF.toInt()
                val colorSeparator = 0xFFDDE3F0.toInt()
                val colorTextPrimary = 0xFF1A1A2E.toInt()
                val colorTextMuted = 0xFF6B7280.toInt()
                val colorAccent = 0xFF1565C0.toInt()
                val colorSuccess = 0xFF2E7D32.toInt()

                val root = LinearLayout(this@PontoEspelhoActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(0xFFF0F4FF.toInt())
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
                    setTextColor(0xFFB0C4DE.toInt())
                    textSize = 13f
                    setPadding(0, (2 * dp).toInt(), 0, (8 * dp).toInt())
                }
                val tvTotalHoras = TextView(this@PontoEspelhoActivity).apply {
                    text = "Total trabalhado: ${resp.total_horas ?: "--:--"}"
                    setTextColor(0xFF7FFF7F.toInt())
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                }
                header.addView(tvTitulo)
                header.addView(tvSubtitulo)
                header.addView(tvTotalHoras)
                root.addView(header)

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
                        setTextColor(if (isHeader) 0xFFCFD8DC.toInt() else colorTextPrimary)
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
                    setBackgroundColor(0xFF1565C0.toInt())
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
                                "entrada" -> 0xFF1B5E20.toInt()
                                "saida_intervalo" -> 0xFFE65100.toInt()
                                "retorno_intervalo" -> 0xFF0D47A1.toInt()
                                "saida" -> 0xFFB71C1C.toInt()
                                else -> 0xFF37474F.toInt()
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

                    // Célula total horas
                    val tvHoras = TextView(this@PontoEspelhoActivity).apply {
                        text = dia.horas_trabalhadas_fmt ?: "-"
                        textSize = 11f
                        setTextColor(colorSuccess)
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.END or Gravity.CENTER_VERTICAL
                        setPadding((4 * dp).toInt(), (9 * dp).toInt(), (12 * dp).toInt(), (9 * dp).toInt())
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
                    }
                    row.addView(tvHoras)

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
                    "E" to 0xFF1B5E20.toInt(),
                    "SI" to 0xFFE65100.toInt(),
                    "RI" to 0xFF0D47A1.toInt(),
                    "S" to 0xFFB71C1C.toInt()
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
                        setTextColor(0xFFB0C4DE.toInt())
                        textSize = 9f
                    })
                }
                root.addView(legenda)

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
