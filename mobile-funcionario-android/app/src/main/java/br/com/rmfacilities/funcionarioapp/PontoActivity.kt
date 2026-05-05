package br.com.rmfacilities.funcionarioapp

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PontoActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private lateinit var tvData: TextView
    private lateinit var tvHorasTrabalhadas: TextView
    private lateinit var tvHorasEsperadas: TextView
    private lateinit var tvSaldo: TextView
    private lateinit var tvProximoTipo: TextView
    private lateinit var tvInconsistencia: TextView
    private lateinit var tvPontoStatus: TextView
    private lateinit var containerMarcacoes: LinearLayout
    private lateinit var btnMarcarPonto: MaterialButton
    private lateinit var btnAtualizarPonto: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ponto)

        api = ApiClient(SessionManager(this))

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { finish() }

        tvData = findViewById(R.id.tvData)
        tvHorasTrabalhadas = findViewById(R.id.tvHorasTrabalhadas)
        tvHorasEsperadas = findViewById(R.id.tvHorasEsperadas)
        tvSaldo = findViewById(R.id.tvSaldo)
        tvProximoTipo = findViewById(R.id.tvProximoTipo)
        tvInconsistencia = findViewById(R.id.tvInconsistencia)
        tvPontoStatus = findViewById(R.id.tvPontoStatus)
        containerMarcacoes = findViewById(R.id.containerMarcacoes)
        btnMarcarPonto = findViewById(R.id.btnMarcarPonto)
        btnAtualizarPonto = findViewById(R.id.btnAtualizarPonto)

        tvData.text = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date())

        btnMarcarPonto.setOnClickListener {
            btnMarcarPonto.isEnabled = false
            tvPontoStatus.text = "Registrando ponto..."
            CoroutineScope(Dispatchers.IO).launch {
                val resp = try { api.marcarPonto() } catch (e: Exception) { PontoDiaResponse(ok = false, erro = e.message) }
                withContext(Dispatchers.Main) {
                    btnMarcarPonto.isEnabled = true
                    if (resp.ok) {
                        renderResumo(resp.resumo)
                        tvPontoStatus.text = "Ponto registrado com sucesso."
                    } else {
                        tvPontoStatus.text = resp.erro ?: "Falha ao registrar ponto."
                    }
                }
            }
        }

        btnAtualizarPonto.setOnClickListener { carregarDia() }

        carregarDia()
    }

    private fun carregarDia() {
        tvPontoStatus.text = "Atualizando..."
        CoroutineScope(Dispatchers.IO).launch {
            val resp = try { api.getPontoDia() } catch (e: Exception) { PontoDiaResponse(ok = false, erro = e.message) }
            withContext(Dispatchers.Main) {
                if (resp.ok) {
                    renderResumo(resp.resumo)
                    tvPontoStatus.text = "Atualizado agora"
                } else {
                    tvPontoStatus.text = resp.erro ?: "Falha ao carregar ponto."
                }
            }
        }
    }

    private fun renderResumo(resumo: PontoResumo?) {
        tvHorasTrabalhadas.text = resumo?.horas_trabalhadas_fmt ?: "00:00"
        tvHorasEsperadas.text = resumo?.horas_esperadas_fmt ?: "00:00"
        tvSaldo.text = resumo?.saldo_fmt ?: "00:00"
        tvProximoTipo.text = "Próxima marcação: ${resumo?.proximo_tipo_label ?: "Entrada"}"

        val inconsistencias = resumo?.inconsistencias ?: emptyList()
        if (inconsistencias.isNotEmpty()) {
            tvInconsistencia.visibility = View.VISIBLE
            tvInconsistencia.text = inconsistencias.joinToString(" | ")
        } else {
            tvInconsistencia.visibility = View.GONE
        }

        containerMarcacoes.removeAllViews()
        val items = resumo?.marcacoes ?: emptyList()
        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Nenhuma marcação hoje."
                setTextColor(resources.getColor(R.color.text_hint, theme))
                textSize = 12f
                setPadding(0, 8, 0, 0)
            }
            containerMarcacoes.addView(empty)
            return
        }

        for (m in items) {
            val row = TextView(this).apply {
                text = "${m.hora_fmt ?: "--:--"} • ${m.tipo_label ?: m.tipo ?: "Marcação"}"
                setTextColor(resources.getColor(R.color.text_primary, theme))
                textSize = 14f
                setPadding(0, 8, 0, 8)
                background = resources.getDrawable(R.drawable.bg_card_aso, theme)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 8
            row.layoutParams = params
            row.setPadding(20, 16, 20, 16)
            containerMarcacoes.addView(row)
        }
    }
}
