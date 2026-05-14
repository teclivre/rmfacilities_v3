package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
            }
            containerCompetencias.addView(tvVazio)
            return
        }

        // Aviso sobre download
        val tvInfo = TextView(this).apply {
            text = "ℹ️ O download do PDF só é liberado após o fechamento pelo gestor."
            setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.mobile_text_secondary))
            textSize = 11f
            setPadding(0, 0, 0, (10 * dp).toInt())
        }
        containerCompetencias.addView(tvInfo)

        for (comp in competencias) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@PontoEspelhoActivity, R.drawable.bg_glass_widget)
                setPadding((14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
            }
            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * dp).toInt() }
            card.layoutParams = cardParams

            // Linha superior: label + status
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvLabel = TextView(this).apply {
                text = comp.label
                setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.mobile_text_primary))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            topRow.addView(tvLabel)

            val statusLabel = if (comp.pode_baixar)
                "✅ Fechada (${comp.fechamentos_dias} dia${if (comp.fechamentos_dias != 1) "s" else ""})"
            else
                "⏳ Aguardando gestor"
            val statusColor = if (comp.pode_baixar) R.color.mobile_semantic_success else R.color.mobile_semantic_pending

            val tvStatusComp = TextView(this).apply {
                text = statusLabel
                setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, statusColor))
                textSize = 11f
            }
            topRow.addView(tvStatusComp)
            card.addView(topRow)

            // Botão Visualizar (sempre disponível)
            val btnVisualizar = MaterialButton(this).apply {
                text = "👁 Visualizar"
                textSize = 13f
                setPadding(12, 0, 12, 0)
                minWidth = 0
                minimumWidth = 0
                strokeWidth = 1
                setStrokeColorResource(R.color.accent)
                setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.accent))
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (10 * dp).toInt() }
            }
            btnVisualizar.setOnClickListener { visualizarFolha(comp.competencia, comp.label) }
            card.addView(btnVisualizar)

            // Botão baixar PDF (somente quando fechada)
            if (comp.pode_baixar) {
                val btnBaixar = MaterialButton(this).apply {
                    text = "⬇ Baixar PDF"
                    textSize = 13f
                    setPadding(12, 0, 12, 0)
                    minWidth = 0
                    minimumWidth = 0
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (6 * dp).toInt() }
                }
                btnBaixar.setOnClickListener {
                    btnBaixar.isEnabled = false
                    btnBaixar.text = "Baixando..."
                    baixarPdf(comp.competencia, comp.label) {
                        btnBaixar.isEnabled = true
                        btnBaixar.text = "⬇ Baixar PDF"
                    }
                }
                card.addView(btnBaixar)
            } else {
                val tvAguarda = TextView(this).apply {
                    text = "Download disponível após fechamento pelo gestor."
                    setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.mobile_text_secondary))
                    textSize = 11f
                    setPadding(0, (6 * dp).toInt(), 0, 0)
                }
                card.addView(tvAguarda)
            }

            containerCompetencias.addView(card)
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
                val scroll = ScrollView(this@PontoEspelhoActivity)
                val container = LinearLayout(this@PontoEspelhoActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
                }
                scroll.addView(container)

                // Cabeçalho
                val tvTotal = TextView(this@PontoEspelhoActivity).apply {
                    text = "Total trabalhado: ${resp.total_horas ?: "--:--"}"
                    setTextColor(ContextCompat.getColor(this@PontoEspelhoActivity, R.color.mobile_semantic_success))
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, (8 * dp).toInt())
                }
                container.addView(tvTotal)

                // Linha de cabeçalho da tabela
                fun makeRow(cols: List<String>, isHeader: Boolean) = LinearLayout(this@PontoEspelhoActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val weights = listOf(2.0f, 5.0f, 1.8f)
                    val bg = if (isHeader) 0xFF1D3A5C.toInt() else Color.TRANSPARENT
                    setBackgroundColor(bg)
                    setPadding(0, (2 * dp).toInt(), 0, (2 * dp).toInt())
                    cols.forEachIndexed { i, text ->
                        addView(TextView(this@PontoEspelhoActivity).apply {
                            this.text = text
                            textSize = if (isHeader) 10f else 11f
                            setTypeface(null, if (isHeader) Typeface.BOLD else Typeface.NORMAL)
                            setTextColor(if (isHeader) Color.WHITE else
                                ContextCompat.getColor(this@PontoEspelhoActivity, R.color.mobile_text_primary))
                            gravity = if (i == 1) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
                            setPadding(if (i == 1) (4 * dp).toInt() else 0, 0, 0, 0)
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,
                                weights.getOrElse(i) { 1f })
                        })
                    }
                }

                container.addView(makeRow(listOf("Data", "Batidas", "Horas"), true))

                for (dia in resp.dias) {
                    if (!dia.tem_marcacoes) continue
                    val marcStr = if (dia.marcacoes.isNotEmpty())
                        dia.marcacoes.joinToString("  ·  ") { m ->
                            val lbl = when (m.tipo) {
                                "entrada" -> "E"
                                "saida_intervalo" -> "SI"
                                "retorno_intervalo" -> "RI"
                                "saida" -> "S"
                                else -> m.tipo_label ?: m.tipo ?: "?"
                            }
                            "${m.hora_fmt ?: "-"} $lbl"
                        }
                    else "-"
                    val row = makeRow(listOf(
                        dia.data_fmt ?: "",
                        marcStr,
                        dia.horas_trabalhadas_fmt ?: "-"
                    ), false)
                    container.addView(row)
                    // Separador
                    container.addView(View(this@PontoEspelhoActivity).apply {
                        setBackgroundColor(0x22FFFFFF)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    })
                }

                AlertDialog.Builder(this@PontoEspelhoActivity)
                    .setTitle("📋 $label")
                    .setView(scroll)
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
