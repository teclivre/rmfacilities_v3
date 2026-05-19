package br.com.rmfacilities.funcionarioapp

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SolicitacaoCorrecaoPontoActivity : BaseActivity() {

    private lateinit var api: ApiClient

    // Views
    private lateinit var tvData: TextView
    private lateinit var btnSelecionarData: MaterialButton
    private lateinit var layoutMarcacoes: LinearLayout
    private lateinit var tvMarcacoesHint: TextView
    private lateinit var layoutCorrecao: LinearLayout
    private lateinit var tvMarcacaoSelecionada: TextView
    private lateinit var tvHorarioOriginal: TextView
    private lateinit var tvHorarioNovo: TextView
    private lateinit var btnEscolherHorario: MaterialButton
    private lateinit var etObservacao: EditText
    private lateinit var btnEnviar: MaterialButton
    private lateinit var tvStatus: TextView

    // Estado
    private var dataRef: String = "" // yyyy-MM-dd
    private var marcacaoId: Int? = null
    private var horarioOriginal: String = ""
    private var horarioCorreto: String = ""
    private var tipoMarcacao: String = ""
    // Controle de limite de marcações faltando
    private var maxMarcacoesDia: Int = 4
    private var marcacoesDia: Int = 0          // marcações já existentes no dia
    private var correcoesFaltandoPendentes: Int = 0  // pendentes enviadas nesta sessão

    private val sdfBr = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val sdfIso = SimpleDateFormat("yyyy-MM-dd", Locale("pt", "BR"))
    private val sdfHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_solicitacao_correcao_ponto)

        api = ApiClient(SessionManager(this))

        tvData = findViewById(R.id.tvDataSelecionada)
        btnSelecionarData = findViewById(R.id.btnSelecionarData)
        layoutMarcacoes = findViewById(R.id.layoutMarcacoes)
        tvMarcacoesHint = findViewById(R.id.tvMarcacoesHint)
        layoutCorrecao = findViewById(R.id.layoutCorrecao)
        tvMarcacaoSelecionada = findViewById(R.id.tvMarcacaoSelecionada)
        tvHorarioOriginal = findViewById(R.id.tvHorarioOriginal)
        tvHorarioNovo = findViewById(R.id.tvHorarioNovo)
        btnEscolherHorario = findViewById(R.id.btnEscolherHorario)
        etObservacao = findViewById(R.id.etObservacaoCorrecao)
        btnEnviar = findViewById(R.id.btnEnviarCorrecao)
        tvStatus = findViewById(R.id.tvStatusCorrecao)

        // Data inicial: hoje
        val cal = Calendar.getInstance()
        definirData(sdfIso.format(cal.time))

        // Intent pode passar data pré-selecionada
        val dataIntent = intent.getStringExtra("data_ref")
        if (!dataIntent.isNullOrBlank()) {
            definirData(dataIntent)
            carregarMarcacoes()
        }

        btnSelecionarData.setOnClickListener { abrirDatePicker() }
        btnEscolherHorario.setOnClickListener { abrirTimePicker() }
        btnEnviar.setOnClickListener { enviarSolicitacao() }
        findViewById<View>(R.id.btnVoltarCorrecao).setOnClickListener { finish() }
    }

    private fun definirData(iso: String) {
        dataRef = iso
        try {
            val d = sdfIso.parse(iso)
            tvData.text = if (d != null) sdfBr.format(d) else iso
        } catch (_: Exception) {
            tvData.text = iso
        }
        // Limpar seleção de marcação e contadores ao trocar de data
        marcacaoId = null
        horarioOriginal = ""
        horarioCorreto = ""
        correcoesFaltandoPendentes = 0
        maxMarcacoesDia = 4
        marcacoesDia = 0
        layoutCorrecao.visibility = View.GONE
        layoutMarcacoes.visibility = View.GONE
        tvMarcacoesHint.visibility = View.VISIBLE
    }

    private fun abrirDatePicker() {
        val cal = Calendar.getInstance()
        try { sdfIso.parse(dataRef)?.let { cal.time = it } } catch (_: Exception) {}
        android.app.DatePickerDialog(
            this,
            { _, year, month, day ->
                val iso = "%04d-%02d-%02d".format(year, month + 1, day)
                definirData(iso)
                carregarMarcacoes()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).also { picker ->
            // Não permite datas futuras
            picker.datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun carregarMarcacoes() {
        tvMarcacoesHint.text = "Carregando marcações..."
        tvMarcacoesHint.visibility = View.VISIBLE
        layoutMarcacoes.removeAllViews()
        layoutMarcacoes.visibility = View.GONE
        layoutCorrecao.visibility = View.GONE

        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) {
                try { api.getPontoDia(dataRef) }
                catch (e: Exception) { PontoDiaResponse(ok = false, erro = e.message ?: "Erro de conexão") }
            }
            if (!resp.ok || resp.resumo == null) {
                tvMarcacoesHint.text = "❌ ${resp.erro ?: "Falha ao carregar marcações"}"
                return@launch
            }
            // Salvar limites do servidor
            maxMarcacoesDia = resp.resumo.max_marcacoes_dia
            correcoesFaltandoPendentes = resp.resumo.correcoes_faltando_pendentes
            val marcacoes = resp.resumo.marcacoes
            marcacoesDia = marcacoes.size
            if (marcacoes.isEmpty()) {
                tvMarcacoesHint.text = "Nenhuma marcação neste dia.\nSe precisar adicionar uma marcação, selecione \"Marcação faltando\" abaixo."
                layoutMarcacoes.visibility = View.GONE
                // Mostrar painel para solicitar adição de marcação
                mostrarPainelSemMarcacao()
                return@launch
            }
            tvMarcacoesHint.text = "Toque na marcação que deseja corrigir:"
            layoutMarcacoes.visibility = View.VISIBLE
            marcacoes.forEach { m ->
                val horaFmt = m.hora_fmt ?: m.data_hora?.substringAfter(" ")?.take(5) ?: "—"
                val tipoLabel = m.tipo_label ?: m.tipo ?: "Marcação"
                val btn = MaterialButton(this@SolicitacaoCorrecaoPontoActivity).apply {
                    text = "$tipoLabel  ·  $horaFmt"
                    textSize = 14f
                    isAllCaps = false
                    strokeWidth = 2
                    setStrokeColorResource(R.color.mobile_border)
                    setBackgroundColor(ContextCompat.getColor(context, R.color.mobile_surface))
                    setTextColor(ContextCompat.getColor(context, R.color.mobile_text_primary))
                    val dp8 = (8 * resources.displayMetrics.density).toInt()
                    val dp12 = (12 * resources.displayMetrics.density).toInt()
                    setPadding(dp12, dp8, dp12, dp8)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = dp8 }
                    setOnClickListener {
                        selecionarMarcacao(m.id, horaFmt, tipoLabel)
                        // Destacar botão selecionado
                        layoutMarcacoes.children.forEach { v ->
                            (v as? MaterialButton)?.setBackgroundColor(
                                ContextCompat.getColor(context, R.color.mobile_surface)
                            )
                        }
                        setBackgroundColor(ContextCompat.getColor(context, R.color.mobile_primary_light))
                    }
                }
                layoutMarcacoes.addView(btn)
            }
        }
    }

    private fun mostrarPainelSemMarcacao() {
        val enviadas = marcacoesDia + correcoesFaltandoPendentes
        if (enviadas >= maxMarcacoesDia) {
            tvMarcacoesHint.text = "✅ Todas as $maxMarcacoesDia marcações já foram solicitadas ou existem para este dia. Aguarde a aprovação do RH."
            layoutCorrecao.visibility = View.GONE
            return
        }
        // Solicitar "marcação faltando"
        marcacaoId = null
        horarioOriginal = ""
        horarioCorreto = ""
        tipoMarcacao = "marcacao_faltando"
        val num = enviadas + 1
        tvMarcacaoSelecionada.text = "Marcação $num de $maxMarcacoesDia — adicionar horário faltante"
        tvHorarioOriginal.text = "Horário original: —"
        tvHorarioNovo.text = "Horário correto: não definido"
        layoutCorrecao.visibility = View.VISIBLE
        btnEscolherHorario.text = "Definir horário que deveria ter"
    }

    private fun selecionarMarcacao(id: Int, hora: String, tipo: String) {
        marcacaoId = id
        horarioOriginal = hora
        tipoMarcacao = "horario_errado"
        tvMarcacaoSelecionada.text = "$tipo · $hora"
        tvHorarioOriginal.text = "Horário original: $hora"
        tvHorarioNovo.text = "Horário correto: não definido"
        horarioCorreto = ""
        layoutCorrecao.visibility = View.VISIBLE
        btnEscolherHorario.text = "Escolher horário correto"
    }

    private fun abrirTimePicker() {
        val cal = Calendar.getInstance()
        // Pré-preencher com o horário original se disponível
        if (horarioOriginal.isNotBlank()) {
            try {
                val parts = horarioOriginal.split(":")
                cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                cal.set(Calendar.MINUTE, parts[1].toInt())
            } catch (_: Exception) {}
        }
        TimePickerDialog(
            this,
            { _, hour, minute ->
                horarioCorreto = "%02d:%02d".format(hour, minute)
                tvHorarioNovo.text = "Horário correto: $horarioCorreto"
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun enviarSolicitacao() {
        val obs = etObservacao.text?.toString()?.trim() ?: ""
        if (dataRef.isBlank()) {
            Toast.makeText(this, "Selecione a data", Toast.LENGTH_SHORT).show()
            return
        }
        if (marcacaoId == null && horarioCorreto.isBlank() && tipoMarcacao != "marcacao_faltando") {
            Toast.makeText(this, "Selecione a marcação a corrigir", Toast.LENGTH_SHORT).show()
            return
        }
        if (horarioCorreto.isBlank() && tipoMarcacao != "marcacao_extra") {
            Toast.makeText(this, "Informe o horário correto", Toast.LENGTH_SHORT).show()
            return
        }
        if (obs.isBlank()) {
            etObservacao.error = "Descreva o motivo da correção"
            return
        }

        val tipoFinal = when {
            marcacaoId != null -> "horario_errado"
            tipoMarcacao == "marcacao_faltando" -> "marcacao_faltando"
            else -> "outro"
        }

        btnEnviar.isEnabled = false
        tvStatus.text = "Enviando..."
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_info))
        tvStatus.visibility = View.VISIBLE

        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) {
                try {
                    api.solicitarCorrecaoPonto(
                        dataRef = dataRef,
                        tipoProbema = tipoFinal,
                        horarioEsperado = horarioOriginal,
                        observacao = obs,
                        marcacaoId = marcacaoId,
                        horarioCorreto = horarioCorreto.ifBlank { null }
                    )
                } catch (e: Exception) {
                    CorrecaoPontoResponse(ok = false, erro = e.message ?: "Falha de conexão")
                }
            }
            btnEnviar.isEnabled = true
            if (resp.ok) {
                Toast.makeText(this@SolicitacaoCorrecaoPontoActivity, "Solicitação enviada!", Toast.LENGTH_SHORT).show()
                if (tipoFinal == "marcacao_faltando") {
                    correcoesFaltandoPendentes++
                    val enviadas = marcacoesDia + correcoesFaltandoPendentes
                    val restantes = maxMarcacoesDia - enviadas
                    etObservacao.text?.clear()
                    if (restantes > 0) {
                        tvStatus.text = "✅ Marcação $correcoesFaltandoPendentes/${maxMarcacoesDia - marcacoesDia} solicitada! Você pode adicionar mais $restantes marcação(ões) para este dia abaixo."
                        tvStatus.setTextColor(ContextCompat.getColor(this@SolicitacaoCorrecaoPontoActivity, R.color.mobile_semantic_success))
                        // Reabrir painel para próxima marcação
                        mostrarPainelSemMarcacao()
                    } else {
                        tvStatus.text = "✅ Todas as ${maxMarcacoesDia - marcacoesDia} marcações solicitadas! O RH analisará em breve."
                        tvStatus.setTextColor(ContextCompat.getColor(this@SolicitacaoCorrecaoPontoActivity, R.color.mobile_semantic_success))
                        layoutCorrecao.visibility = View.GONE
                        layoutMarcacoes.visibility = View.GONE
                        marcacaoId = null
                        horarioCorreto = ""
                    }
                } else {
                    tvStatus.text = "✅ Solicitação enviada! O RH analisará em breve."
                    tvStatus.setTextColor(ContextCompat.getColor(this@SolicitacaoCorrecaoPontoActivity, R.color.mobile_semantic_success))
                    etObservacao.text?.clear()
                    layoutCorrecao.visibility = View.GONE
                    layoutMarcacoes.visibility = View.GONE
                    marcacaoId = null
                    horarioCorreto = ""
                }
            } else {
                tvStatus.text = "❌ ${resp.erro ?: "Erro ao enviar"}"
                tvStatus.setTextColor(ContextCompat.getColor(this@SolicitacaoCorrecaoPontoActivity, R.color.mobile_semantic_pending))
            }
        }
    }

    // Extensão para iterar views filhas de LinearLayout
    private val ViewGroup.children: Sequence<View>
        get() = sequence { for (i in 0 until childCount) yield(getChildAt(i)) }
}
