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
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import androidx.lifecycle.lifecycleScope

class PontoActivity : AppCompatActivity() {

    // Marcações registradas localmente (offline ou com erro) ainda não confirmadas pelo servidor
    enum class LocalStatus { PENDING, ERROR }
    data class LocalMarcacao(
        val hora: String,
        val tipoLabel: String,
        val status: LocalStatus,
        val timestamp: Long = System.currentTimeMillis()
    )
    private val localPendentes = mutableListOf<LocalMarcacao>()

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
            val tipo = tvProximoTipo.text?.toString()?.ifBlank { "ponto" } ?: "ponto"
            val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Confirmar ponto")
                .setMessage("Registrar $tipo às $hora?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Confirmar") { _, _ -> registrarComLocalizacao() }
                .show()
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
                        startActivity(Intent(this@PontoActivity, HomeActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
                        true
                    }
                    R.id.nav_tarefas -> {
                        startActivity(Intent(this@PontoActivity, DocumentosActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
                        true
                    }
                    R.id.nav_ponto -> true
                    R.id.nav_mensagens -> {
                        startActivity(Intent(this@PontoActivity, MensagensActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
                        true
                    }
                    R.id.nav_perfil -> {
                        startActivity(Intent(this@PontoActivity, PerfilActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
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

    /** Solicita localização atual via FusedLocationProviderClient (alta precisão, até 15s). */
    private suspend fun obterLocalizacaoAtual(): Location? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine { cont ->
                val client = LocationServices.getFusedLocationProviderClient(this@PontoActivity)
                val cts = com.google.android.gms.tasks.CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc -> cont.resume(loc) }
                    .addOnFailureListener { cont.resume(null) }
            }
        }
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

        updateStatus("Obtendo localização atual...", R.color.mobile_semantic_info)

        lifecycleScope.launch {
            val loc = obterLocalizacaoAtual()
            if (loc == null) {
                btnMarcarPonto.isEnabled = true
                updateStatus("Não foi possível obter localização. Ative o GPS e tente novamente.", R.color.mobile_semantic_pending)
                return@launch
            }
            updateStatus("Localização obtida. Registrando ponto...", R.color.mobile_semantic_info)

            // Verifica conectividade após obter localização
            if (!isOnline()) {
                retryQueue.enqueuePonto(loc.latitude, loc.longitude, loc.accuracy, System.currentTimeMillis())
                updateStatus("Sem internet. Ponto salvo offline — será sincronizado automaticamente.", R.color.mobile_semantic_pending)
                btnMarcarPonto.isEnabled = true
                val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val tipoLabel = tvProximoTipo.text?.toString()?.removePrefix("Próxima marcação: ") ?: "Marcação"
                localPendentes.add(LocalMarcacao(hora, tipoLabel, LocalStatus.PENDING))
                renderMarcacoesComLocais(null)
                atualizarBadgePendentes()
                return@launch
            }

            val resp = withContext(Dispatchers.IO) {
                try {
                    api.marcarPonto(lat = loc.latitude, lon = loc.longitude, precisao = loc.accuracy)
                } catch (e: Exception) {
                    PontoDiaResponse(ok = false, erro = e.message)
                }
            }

            btnMarcarPonto.isEnabled = true
            if (resp.ok) {
                localPendentes.clear() // servidor confirmou, limpa locais
                renderResumo(resp.resumo)
                btnMarcarPonto.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                updateStatus("Ponto registrado com localização.", R.color.mobile_semantic_success)
            } else {
                val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val tipoLabel = tvProximoTipo.text?.toString()?.removePrefix("Próxima marcação: ") ?: "Marcação"
                localPendentes.add(LocalMarcacao(hora, tipoLabel, LocalStatus.ERROR))
                renderMarcacoesComLocais(null)
                retryQueue.enqueuePonto(loc.latitude, loc.longitude, loc.accuracy, System.currentTimeMillis())
                updateStatus("Falha ao enviar. Ponto salvo — será sincronizado automaticamente.", R.color.mobile_semantic_pending)
                atualizarBadgePendentes()
            }
        }
    }

    private fun carregarDia() {
        updateStatus("Atualizando...", R.color.mobile_semantic_info)
        lifecycleScope.launch {
            val resp = try { api.getPontoDia() } catch (e: Exception) { PontoDiaResponse(ok = false, erro = e.message) }
            withContext(Dispatchers.Main) {
                if (resp.ok) {
                    localPendentes.clear() // servidor confirmou todas as marcações
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

        renderMarcacoesComLocais(resumo)
    }

    private fun renderMarcacoesComLocais(resumo: PontoResumo?) {
        containerMarcacoes.removeAllViews()
        val dp = resources.displayMetrics.density
        val items = resumo?.marcacoes ?: emptyList()

        if (items.isEmpty() && localPendentes.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Nenhuma marcação hoje."
                setTextColor(ContextCompat.getColor(this@PontoActivity, R.color.mobile_text_secondary))
                textSize = 12f
                setPadding(0, 8, 0, 0)
            }
            containerMarcacoes.addView(empty)
            return
        }

        // Marcações confirmadas pelo servidor (verde via emoji)
        for (m in items) {
            adicionarCardMarcacao(
                hora = m.hora_fmt ?: "--:--",
                tipoLabel = m.tipo_label ?: m.tipo ?: "Marcação",
                tipoEmoji = when (m.tipo) {
                    "entrada" -> "🟢"
                    "saida_intervalo" -> "☕"
                    "retorno_intervalo" -> "🔵"
                    "saida" -> "🔴"
                    else -> "🕐"
                },
                statusColor = ContextCompat.getColor(this, R.color.mobile_text_primary),
                statusBadge = null,
                lat = m.lat,
                lon = m.lon,
                dp = dp
            )
        }

        // Marcações locais pendentes (azul = offline, vermelho = erro)
        for (local in localPendentes) {
            val (badgeText, badgeColor) = when (local.status) {
                LocalStatus.PENDING -> Pair("⏳ offline", ContextCompat.getColor(this, R.color.mobile_semantic_info))
                LocalStatus.ERROR   -> Pair("❌ erro ao enviar", ContextCompat.getColor(this, R.color.error))
            }
            adicionarCardMarcacao(
                hora = local.hora,
                tipoLabel = local.tipoLabel,
                tipoEmoji = when (local.status) {
                    LocalStatus.PENDING -> "🔵"
                    LocalStatus.ERROR   -> "🔴"
                },
                statusColor = badgeColor,
                statusBadge = badgeText,
                lat = null,
                lon = null,
                dp = dp
            )
        }
    }

    private fun adicionarCardMarcacao(
        hora: String,
        tipoLabel: String,
        tipoEmoji: String,
        statusColor: Int,
        statusBadge: String?,
        lat: Double?,
        lon: Double?,
        dp: Float
    ) {
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

        val tvEmoji = TextView(this).apply {
            text = tipoEmoji
            textSize = 20f
            setPadding(0, 0, (10 * dp).toInt(), 0)
        }
        card.addView(tvEmoji)

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvHora = TextView(this).apply {
            text = hora
            setTextColor(statusColor)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
        }
        val tvLabel = TextView(this).apply {
            text = tipoLabel
            setTextColor(ContextCompat.getColor(this@PontoActivity, R.color.mobile_text_secondary))
            textSize = 11f
        }
        infoCol.addView(tvHora)
        infoCol.addView(tvLabel)

        if (statusBadge != null) {
            val tvBadge = TextView(this).apply {
                text = statusBadge
                setTextColor(statusColor)
                textSize = 10f
                setPadding(0, 2, 0, 0)
            }
            infoCol.addView(tvBadge)
        }

        card.addView(infoCol)

        if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) {
            val btnMapa = TextView(this).apply {
                text = "📍"
                textSize = 20f
                setPadding((8 * dp).toInt(), 0, 0, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    startActivity(Intent(this@PontoActivity, PontoMapaActivity::class.java).apply {
                        putExtra(PontoMapaActivity.EXTRA_LAT, lat)
                        putExtra(PontoMapaActivity.EXTRA_LON, lon)
                        putExtra(PontoMapaActivity.EXTRA_HORA, hora)
                        putExtra(PontoMapaActivity.EXTRA_TIPO, tipoLabel)
                    })
                }
            }
            card.addView(btnMapa)
        }

        containerMarcacoes.addView(card)
    }

    private fun updateStatus(message: String, colorRes: Int) {
        tvPontoStatus.text = message
        tvPontoStatus.setTextColor(ContextCompat.getColor(this, colorRes))
    }
}
