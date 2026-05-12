package br.com.rmfacilities.funcionarioapp

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PontoActivity : AppCompatActivity() {

    private lateinit var api: ApiClient
    private lateinit var retryQueue: ActionRetryQueue
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

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            registrarComLocalizacao()
        } else {
            updateStatus("Permissão de localização é obrigatória para registrar ponto.", R.color.mobile_semantic_pending)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ponto)

        api = ApiClient(SessionManager(this))
        retryQueue = ActionRetryQueue(this)

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

        tvData.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        btnMarcarPonto.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            registrarComLocalizacao()
        }
        btnAtualizarPonto.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            carregarDia()
        }

        findViewById<BottomNavigationView>(R.id.bottomNavPonto).apply {
            selectedItemId = R.id.nav_ponto
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        startActivity(Intent(this@PontoActivity, HomeActivity::class.java))
                        true
                    }
                    R.id.nav_tarefas -> {
                        startActivity(Intent(this@PontoActivity, DocumentosActivity::class.java))
                        true
                    }
                    R.id.nav_ponto -> true
                    R.id.nav_mensagens -> {
                        startActivity(Intent(this@PontoActivity, MensagensActivity::class.java))
                        true
                    }
                    R.id.nav_perfil -> {
                        startActivity(Intent(this@PontoActivity, PerfilActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
        }

        carregarDia()
    }

    override fun onResume() {
        super.onResume()
        // Atualiza data caso o app ficou aberto após meia-noite
        tvData.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        atualizarBadgePendentes()
    }

    private fun obterLocalizacao(): Location? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }
        return try { lm.getLastKnownLocation(provider) } catch (_: Exception) { null }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        @Suppress("DEPRECATION")
        return cm.activeNetworkInfo?.isConnected == true
    }

    private fun atualizarBadgePendentes() {
        val count = retryQueue.pendingCount()
        val badge = tvPontoStatus
        if (count > 0) {
            badge.text = "⏳ $count ponto(s) offline aguardando sincronização"
            badge.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_pending))
        }
    }

    private fun registrarComLocalizacao() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        btnMarcarPonto.isEnabled = false

        // Verifica conectividade antes de tentar API
        if (!isOnline()) {
            val loc = obterLocalizacao()
            if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                retryQueue.enqueuePonto(loc.latitude, loc.longitude, loc.accuracy, System.currentTimeMillis())
                updateStatus("Sem internet. Ponto salvo offline — será sincronizado automaticamente.", R.color.mobile_semantic_pending)
            } else {
                updateStatus("Sem internet e sem localização disponível. Ative o GPS e tente novamente.", R.color.mobile_semantic_pending)
            }
            btnMarcarPonto.isEnabled = true
            atualizarBadgePendentes()
            return
        }

        updateStatus("Obtendo localização...", R.color.mobile_semantic_info)

        CoroutineScope(Dispatchers.IO).launch {
            val loc = withContext(Dispatchers.Main) { obterLocalizacao() }
            if (loc == null) {
                withContext(Dispatchers.Main) {
                    btnMarcarPonto.isEnabled = true
                    updateStatus("Não foi possível identificar sua localização. Ative GPS/rede e tente novamente.", R.color.mobile_semantic_pending)
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                updateStatus("Localização obtida. Registrando ponto...", R.color.mobile_semantic_info)
            }

            val resp = try {
                api.marcarPonto(lat = loc.latitude, lon = loc.longitude, precisao = loc.accuracy)
            } catch (e: Exception) {
                PontoDiaResponse(ok = false, erro = e.message)
            }

            withContext(Dispatchers.Main) {
                btnMarcarPonto.isEnabled = true
                if (resp.ok) {
                    renderResumo(resp.resumo)
                    btnMarcarPonto.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    updateStatus("Ponto registrado com localização.", R.color.mobile_semantic_success)
                } else {
                    if (loc.latitude != 0.0 || loc.longitude != 0.0) {
                        retryQueue.enqueuePonto(loc.latitude, loc.longitude, loc.accuracy, System.currentTimeMillis())
                    }
                    updateStatus("Sem conexão. Ponto salvo offline — será sincronizado automaticamente.", R.color.mobile_semantic_pending)
                    atualizarBadgePendentes()
                }
            }
        }
    }

    private fun carregarDia() {
        updateStatus("Atualizando...", R.color.mobile_semantic_info)
        CoroutineScope(Dispatchers.IO).launch {
            val resp = try { api.getPontoDia() } catch (e: Exception) { PontoDiaResponse(ok = false, erro = e.message) }
            withContext(Dispatchers.Main) {
                if (resp.ok) {
                    renderResumo(resp.resumo)
                    updateStatus("Atualizado agora.", R.color.mobile_semantic_info)
                } else {
                    if (!resp.erro.isNullOrBlank()) {
                        TelemetryLogger.logHandled(this@PontoActivity, "ponto_carregar", IllegalStateException(resp.erro))
                    }
                    updateStatus(resp.erro ?: "Falha ao carregar ponto.", R.color.mobile_semantic_pending)
                }
            }
        }
    }

    private fun renderResumo(resumo: PontoResumo?) {
        tvHorasTrabalhadas.text = resumo?.horas_trabalhadas_fmt ?: "00:00"
        tvHorasEsperadas.text = resumo?.horas_esperadas_fmt ?: "00:00"
        tvSaldo.text = resumo?.saldo_fmt ?: "00:00"
        tvProximoTipo.text = "Próxima marcação: ${resumo?.proximo_tipo_label ?: "Entrada"}"
        tvProximoTipo.setTextColor(ContextCompat.getColor(this, R.color.mobile_semantic_info))

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
                setTextColor(ContextCompat.getColor(this@PontoActivity, R.color.mobile_text_secondary))
                textSize = 12f
                setPadding(0, 8, 0, 0)
            }
            containerMarcacoes.addView(empty)
            return
        }

        val dp = resources.displayMetrics.density

        for (m in items) {
            // Card row
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(this@PontoActivity, R.drawable.bg_home_card_soft)
                setPadding((14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
            }
            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
            card.layoutParams = cardParams

            // Ícone tipo (emoji por tipo)
            val tipoEmoji = when (m.tipo) {
                "entrada" -> "🟢"
                "saida_intervalo" -> "☕"
                "retorno_intervalo" -> "🔵"
                "saida" -> "🔴"
                else -> "🕐"
            }
            val tvEmoji = TextView(this).apply {
                text = tipoEmoji
                textSize = 20f
                setPadding(0, 0, (10 * dp).toInt(), 0)
            }
            card.addView(tvEmoji)

            // Hora + label
            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvHora = TextView(this).apply {
                text = m.hora_fmt ?: "--:--"
                setTextColor(ContextCompat.getColor(this@PontoActivity, R.color.mobile_text_primary))
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
            }
            val tvLabel = TextView(this).apply {
                text = m.tipo_label ?: m.tipo ?: "Marcação"
                setTextColor(ContextCompat.getColor(this@PontoActivity, R.color.mobile_text_secondary))
                textSize = 11f
            }
            infoCol.addView(tvHora)
            infoCol.addView(tvLabel)
            card.addView(infoCol)

            // Botão mapa (se tiver localização)
            if (m.lat != null && m.lon != null && (m.lat != 0.0 || m.lon != 0.0)) {
                val btnMapa = TextView(this).apply {
                    text = "📍"
                    textSize = 20f
                    setPadding((8 * dp).toInt(), 0, 0, 0)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        startActivity(Intent(this@PontoActivity, PontoMapaActivity::class.java).apply {
                            putExtra(PontoMapaActivity.EXTRA_LAT, m.lat)
                            putExtra(PontoMapaActivity.EXTRA_LON, m.lon)
                            putExtra(PontoMapaActivity.EXTRA_HORA, m.hora_fmt ?: "--:--")
                            putExtra(PontoMapaActivity.EXTRA_TIPO, m.tipo_label ?: "Marcação")
                        })
                    }
                }
                card.addView(btnMapa)
            }

            containerMarcacoes.addView(card)
        }
    }

    private fun updateStatus(message: String, colorRes: Int) {
        tvPontoStatus.text = message
        tvPontoStatus.setTextColor(ContextCompat.getColor(this, colorRes))
    }
}
