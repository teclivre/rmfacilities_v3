package br.com.rmfacilities.funcionarioapp

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.os.Handler
import android.os.Looper
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
    // IDs das marcações confirmadas pelo servidor na última sincronização bem-sucedida
    private val idsMarcacoesConfirmadas = mutableSetOf<Int>()
    // Marcações excluídas pelo admin (estavam no cache mas sumiram do servidor)
    data class MarcacaoExcluida(val id: Int, val hora: String, val tipoLabel: String)
    private val marcacoesExcluidas = mutableListOf<MarcacaoExcluida>()

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
    private lateinit var tvRelogio: TextView
    private lateinit var tvTrabalhando: TextView
    private var entradaTimestamp: Long? = null

    private val relogioHandler = Handler(Looper.getMainLooper())
    private val relogioRunnable = object : Runnable {
        override fun run() {
            if (::tvRelogio.isInitialized) {
                tvRelogio.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            }
            entradaTimestamp?.let { ts ->
                if (::tvTrabalhando.isInitialized) {
                    val elapsed = (System.currentTimeMillis() - ts) / 1000
                    val h = elapsed / 3600
                    val m = (elapsed % 3600) / 60
                    val s = elapsed % 60
                    tvTrabalhando.text = "🕐 Trabalhando há %02d:%02d:%02d".format(h, m, s)
                    tvTrabalhando.visibility = View.VISIBLE
                }
            }
            relogioHandler.postDelayed(this, 1000)
        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var primeiraCarregada = false
    private val gson = Gson()

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
        tvRelogio = findViewById(R.id.tvRelogio)
        tvTrabalhando = findViewById(R.id.tvTrabalhando)

        tvData.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        btnMarcarPonto.setOnClickListener { btn ->
            btn.isEnabled = false  // bloqueia duplo toque imediatamente, antes do dialog
            btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val tipo = tvProximoTipo.text?.toString()?.ifBlank { "ponto" } ?: "ponto"
            val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            var confirmado = false
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Confirmar ponto")
                .setMessage("Registrar $tipo às $hora?")
                .setNegativeButton("Cancelar") { _, _ -> btn.isEnabled = true }
                .setPositiveButton("Confirmar") { _, _ ->
                    confirmado = true
                    registrarComLocalizacao()
                }
                .setOnDismissListener { if (!confirmado) btn.isEnabled = true }
                .show()
        }
        btnAtualizarPonto.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            carregarDia()
        }

        // Linha de atalhos: Histórico e Folha de Ponto lado a lado
        val dpF = resources.displayMetrics.density

        val btnHistorico = MaterialButton(this).apply {
            text = "📅  Ver Histórico"
            textSize = 13f
            letterSpacing = 0.01f
            cornerRadius = (14 * dpF).toInt()
            backgroundTintList = ColorStateList.valueOf(0xFF1565C0.toInt())
            setTextColor(Color.WHITE)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            elevation = 4f
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            setPadding((10 * dpF).toInt(), (10 * dpF).toInt(), (10 * dpF).toInt(), (10 * dpF).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = (6 * dpF).toInt() }
        }
        btnHistorico.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this, PontoHistoricoActivity::class.java))
        }

        val btnEspelho = MaterialButton(this).apply {
            text = "📄  Folha de Ponto"
            textSize = 13f
            letterSpacing = 0.01f
            cornerRadius = (14 * dpF).toInt()
            backgroundTintList = ColorStateList.valueOf(0xFF1B5E20.toInt())
            setTextColor(Color.WHITE)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            elevation = 4f
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            setPadding((10 * dpF).toInt(), (10 * dpF).toInt(), (10 * dpF).toInt(), (10 * dpF).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnEspelho.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this, PontoEspelhoActivity::class.java))
        }

        val rowBtns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * dpF).toInt() }
            addView(btnHistorico)
            addView(btnEspelho)
        }

        val scrollContent = (findViewById<android.widget.ScrollView>(R.id.scrollPonto)
            .getChildAt(0) as? LinearLayout)
        scrollContent?.addView(rowBtns, scrollContent.indexOfChild(btnAtualizarPonto) + 1)

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

        // Mostra cache imediatamente antes de carregar do servidor
        restaurarCacheMarcacoes()
        carregarDia()
        primeiraCarregada = true
    }

    override fun onResume() {
        super.onResume()
        relogioHandler.post(relogioRunnable)
        // Atualiza data caso o app ficou aberto após meia-noite
        tvData.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        atualizarBadgePendentes()
        registrarCallbackRede()
        // Mostra cache imediatamente enquanto carrega do servidor
        if (primeiraCarregada) {
            restaurarCacheMarcacoes()
            carregarDia()
        }
    }

    override fun onPause() {
        super.onPause()
        relogioHandler.removeCallbacks(relogioRunnable)
        desregistrarCallbackRede()
    }

    override fun onDestroy() {
        super.onDestroy()
        desregistrarCallbackRede()
    }

    private fun registrarCallbackRede() {
        if (networkCallback != null) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Voltou a internet: processa fila e atualiza marcações
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        retryQueue.process(ApiClient(SessionManager(this@PontoActivity)))
                    } catch (_: Exception) {}
                    withContext(Dispatchers.Main) {
                        if (retryQueue.pendingCount() == 0 && localPendentes.isNotEmpty()) {
                            // Todos sincronizados — recarrega do servidor para mostrar verde
                            carregarDia()
                        } else {
                            atualizarBadgePendentes()
                        }
                    }
                }
            }
        }
        try {
            cm.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback!!)
        } catch (_: Exception) {
            networkCallback = null
        }
    }

    private fun desregistrarCallbackRede() {
        val cb = networkCallback ?: return
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(cb)
        } catch (_: Exception) {}
        networkCallback = null
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
                val marcacoesResp = resp.resumo?.marcacoes
                if (!marcacoesResp.isNullOrEmpty()) salvarCacheMarcacoes(marcacoesResp)
                renderResumo(resp.resumo)
                btnMarcarPonto.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                // Animação de pulso de confirmação
                btnMarcarPonto.animate()
                    .scaleX(1.18f).scaleY(1.18f).setDuration(140)
                    .withEndAction {
                        btnMarcarPonto.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                    }.start()
                // Flash verde no fundo
                val flashView = View(this@PontoActivity).apply {
                    setBackgroundColor(0x4400C853.toInt())
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val root = window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
                root.addView(flashView)
                flashView.animate().alpha(0f).setDuration(500).withEndAction { root.removeView(flashView) }.start()
                updateStatus("Ponto registrado com localização.", R.color.mobile_semantic_success)
            } else {
                val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val tipoLabel = tvProximoTipo.text?.toString()?.removePrefix("Próxima marcação: ") ?: "Marcação"
                localPendentes.add(LocalMarcacao(hora, tipoLabel, LocalStatus.ERROR))
                renderMarcacoesComLocais(null)
                retryQueue.enqueuePonto(loc.latitude, loc.longitude, loc.accuracy, System.currentTimeMillis())
                @Suppress("DEPRECATION")
                val hapticError = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    HapticFeedbackConstants.REJECT
                else
                    HapticFeedbackConstants.LONG_PRESS
                btnMarcarPonto.performHapticFeedback(hapticError)
                updateStatus("Falha ao enviar. Ponto salvo — será sincronizado automaticamente.", R.color.mobile_semantic_pending)
                atualizarBadgePendentes()
            }
        }
    }

    // ── Cache de marcações (SharedPreferences) ──────────────────────────────────

    private fun cacheKeyHoje(): String {
        val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "marcacoes_$hoje"
    }

    private fun salvarCacheMarcacoes(marcacoes: List<PontoMarcacaoItem>) {
        val prefs = getSharedPreferences("ponto_cache", Context.MODE_PRIVATE)
        prefs.edit().putString(cacheKeyHoje(), gson.toJson(marcacoes)).apply()
    }

    private fun carregarCacheMarcacoes(): List<PontoMarcacaoItem> {
        val prefs = getSharedPreferences("ponto_cache", Context.MODE_PRIVATE)
        val json = prefs.getString(cacheKeyHoje(), null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PontoMarcacaoItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun restaurarCacheMarcacoes() {
        val cached = carregarCacheMarcacoes()
        if (cached.isNotEmpty()) {
            containerMarcacoes.removeAllViews()
            val dp = resources.displayMetrics.density
            for (m in cached) {
                adicionarCardMarcacao(
                    hora = m.hora_fmt ?: "--:--",
                    tipoLabel = m.tipo_label ?: m.tipo ?: "Marcação",
                    tipoEmoji = emojiPorTipo(m.tipo),
                    statusColor = ContextCompat.getColor(this, R.color.mobile_text_primary),
                    statusBadge = null,
                    lat = m.lat, lon = m.lon, dp = dp
                )
            }
        }
    }

    private fun emojiPorTipo(tipo: String?) = when (tipo) {
        "entrada" -> "🟢"
        "saida_intervalo" -> "☕"
        "retorno_intervalo" -> "🔵"
        "saida" -> "🔴"
        else -> "🕐"
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private fun carregarDia() {
        updateStatus("Atualizando...", R.color.mobile_semantic_info)
        lifecycleScope.launch {
            val resp = try { api.getPontoDia() } catch (e: Exception) { PontoDiaResponse(ok = false, erro = e.message) }
            withContext(Dispatchers.Main) {
                if (resp.ok) {
                    localPendentes.clear() // servidor confirmou todas as marcações
                    // Detectar marcações excluídas pelo admin
                    val novosIds = resp.resumo?.marcacoes?.map { it.id }?.toSet() ?: emptySet()
                    val excluidas = idsMarcacoesConfirmadas.filter { it !in novosIds }
                    if (excluidas.isNotEmpty()) {
                        val cache = carregarCacheMarcacoes()
                        excluidas.forEach { id ->
                            val mExcl = cache.find { it.id == id }
                            if (mExcl != null && marcacoesExcluidas.none { it.id == id }) {
                                marcacoesExcluidas.add(
                                    MarcacaoExcluida(id, mExcl.hora_fmt ?: "", mExcl.tipo_label ?: mExcl.tipo ?: "Marcação")
                                )
                            }
                        }
                    }
                    idsMarcacoesConfirmadas.clear()
                    resp.resumo?.marcacoes?.forEach { idsMarcacoesConfirmadas.add(it.id) }
                    // Só sobrescreve o cache se o servidor devolveu marcações
                    // Evita apagar cache válido com lista vazia por erro de resposta
                    val marcacoesServidor = resp.resumo?.marcacoes
                    if (!marcacoesServidor.isNullOrEmpty()) {
                        salvarCacheMarcacoes(marcacoesServidor)
                    }
                    renderResumo(resp.resumo)
                    updateStatus("Atualizado agora.", R.color.mobile_semantic_info)
                } else {
                    if (!resp.erro.isNullOrBlank()) {
                        TelemetryLogger.logHandled(this@PontoActivity, "ponto_carregar", IllegalStateException(resp.erro))
                    }
                    // Em caso de falha: restaura do cache para não sumir as marcações
                    restaurarCacheMarcacoes()
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

        // Timer "Trabalhando há..." — ativo quando há entrada e sem saída
        val marcacoesLista = resumo?.marcacoes ?: emptyList()
        val primeiroTipo = marcacoesLista.firstOrNull()?.tipo
        val ultimoTipo = marcacoesLista.lastOrNull()?.tipo
        if (primeiroTipo == "entrada" && ultimoTipo != "saida") {
            val entradaHora = marcacoesLista.first().hora_fmt ?: ""
            if (entradaHora.isNotBlank()) {
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                try {
                    val sdfFull = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    entradaTimestamp = sdfFull.parse("$hoje $entradaHora")?.time
                } catch (_: Exception) { entradaTimestamp = null }
            }
        } else {
            entradaTimestamp = null
            tvTrabalhando.visibility = View.GONE
        }

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
                tipoEmoji = emojiPorTipo(m.tipo),
                statusColor = ContextCompat.getColor(this, R.color.mobile_text_primary),
                statusBadge = null,
                lat = m.lat,
                lon = m.lon,
                dp = dp
            )
        }

        // Marcações excluídas pelo admin (vermelho com aviso)
        for (excl in marcacoesExcluidas) {
            adicionarCardMarcacao(
                hora = excl.hora,
                tipoLabel = excl.tipoLabel,
                tipoEmoji = "🔴",
                statusColor = ContextCompat.getColor(this, R.color.error),
                statusBadge = "❌ Excluída pelo RH",
                lat = null, lon = null, dp = dp
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
