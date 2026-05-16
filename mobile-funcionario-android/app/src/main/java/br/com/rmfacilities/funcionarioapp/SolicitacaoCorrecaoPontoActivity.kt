package br.com.rmfacilities.funcionarioapp

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SolicitacaoCorrecaoPontoActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private lateinit var etData: EditText
    private lateinit var etHorario: EditText
    private lateinit var etObservacao: EditText
    private lateinit var spinnerTipo: Spinner
    private lateinit var btnEnviar: MaterialButton
    private lateinit var tvStatusCorrecao: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_solicitacao_correcao_ponto)

        api = ApiClient(SessionManager(this))
        etData = findViewById(R.id.etDataCorrecao)
        etHorario = findViewById(R.id.etHorarioEsperado)
        etObservacao = findViewById(R.id.etObservacaoCorrecao)
        spinnerTipo = findViewById(R.id.spinnerTipoProblema)
        btnEnviar = findViewById(R.id.btnEnviarCorrecao)
        tvStatusCorrecao = findViewById(R.id.tvStatusCorrecao)

        // Preencher data se passada pelo intent
        val dataIntent = intent.getStringExtra("data_ref")
        if (!dataIntent.isNullOrBlank()) {
            // Converter yyyy-MM-dd para dd/MM/yyyy
            try {
                val parts = dataIntent.split("-")
                if (parts.size == 3) etData.setText("${parts[2]}/${parts[1]}/${parts[0]}")
                else etData.setText(dataIntent)
            } catch (_: Exception) {
                etData.setText(dataIntent)
            }
        }

        // Spinner de tipo
        val tipos = arrayOf("Horário errado", "Marcação faltando", "Marcação extra", "Outro")
        spinnerTipo.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tipos)

        findViewById<TextView>(R.id.btnVoltarCorrecao).setOnClickListener { finish() }

        btnEnviar.setOnClickListener { enviarSolicitacao() }
    }

    private fun enviarSolicitacao() {
        val dataRaw = etData.text.toString().trim()
        val horario = etHorario.text.toString().trim()
        val obs = etObservacao.text.toString().trim()

        if (dataRaw.isBlank()) {
            etData.error = "Informe a data"
            return
        }
        if (obs.isBlank()) {
            etObservacao.error = "Descreva o problema"
            return
        }

        // Converter dd/MM/yyyy para yyyy-MM-dd
        val dataRef = try {
            val parts = dataRaw.split("/")
            if (parts.size == 3) "${parts[2]}-${parts[1].padStart(2,'0')}-${parts[0].padStart(2,'0')}"
            else dataRaw
        } catch (_: Exception) { dataRaw }

        val tipoMap = mapOf(
            0 to "horario_errado",
            1 to "marcacao_faltando",
            2 to "marcacao_extra",
            3 to "outro"
        )
        val tipoProblema = tipoMap[spinnerTipo.selectedItemPosition] ?: "outro"

        btnEnviar.isEnabled = false
        tvStatusCorrecao.text = "Enviando solicitação..."
        tvStatusCorrecao.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_info))
        tvStatusCorrecao.visibility = View.VISIBLE

        lifecycleScope.launch {
            val resp = withContext(Dispatchers.IO) {
                try { api.solicitarCorrecaoPonto(dataRef, tipoProblema, horario, obs) }
                catch (e: Exception) { CorrecaoPontoResponse(ok = false, erro = e.message) }
            }
            withContext(Dispatchers.Main) {
                btnEnviar.isEnabled = true
                if (resp.ok) {
                    tvStatusCorrecao.text = "✅ ${resp.mensagem ?: "Solicitação enviada com sucesso!"}"
                    tvStatusCorrecao.setTextColor(ContextCompat.getColor(this@SolicitacaoCorrecaoPontoActivity, R.color.mobile_semantic_success))
                    etData.text.clear()
                    etHorario.text.clear()
                    etObservacao.text.clear()
                    Toast.makeText(this@SolicitacaoCorrecaoPontoActivity, "Solicitação enviada!", Toast.LENGTH_SHORT).show()
                } else {
                    tvStatusCorrecao.text = "❌ ${resp.erro ?: "Erro ao enviar solicitação."}"
                    tvStatusCorrecao.setTextColor(ContextCompat.getColor(this@SolicitacaoCorrecaoPontoActivity, R.color.mobile_semantic_pending))
                }
            }
        }
    }
}
