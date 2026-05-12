package br.com.rmfacilities.funcionarioapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {
    companion object {
        private const val PREF_SHORTCUTS = "home_shortcuts"
        private const val KEY_ENABLED = "enabled"
    }

    private lateinit var session: SessionManager
    private lateinit var api: ApiClient
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvBoasVindas: TextView
    private lateinit var tvCargo: TextView
    private lateinit var tvAvatar: TextView
    private lateinit var tvResumoPonto: TextView
    private lateinit var tvResumoTarefas: TextView
    private lateinit var tvResumoAvisos: TextView
    private lateinit var tvMsgBadge: TextView
    private lateinit var tvDocsBadge: TextView
    private lateinit var tvUltimoPagamento: TextView
    private lateinit var retryQueue: ActionRetryQueue
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var btnDocumentos: View
    private lateinit var btnPerfil: View
    private lateinit var btnPonto: View
    private lateinit var btnMensagens: View
    private lateinit var btnOfflineHome: View
    private lateinit var btnConfiguracoesHome: View
    private lateinit var btnSalarioHome: View
    private lateinit var btnBeneficiosHome: View

    private val logoutReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            goLogin()
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) enviarLocalizacao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        session = SessionManager(this)
        api = ApiClient(session)
        retryQueue = ActionRetryQueue(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        TelemetryLogger.init(this)

        if (session.accessToken.isBlank()) {
            goLogin(); return
        }

        tvBoasVindas = findViewById(R.id.tvBoasVindas)
        tvCargo = findViewById(R.id.tvCargo)
        tvAvatar = findViewById(R.id.tvAvatar)
        tvResumoPonto = findViewById(R.id.tvResumoPonto)
        tvResumoTarefas = findViewById(R.id.tvResumoTarefas)
        tvResumoAvisos = findViewById(R.id.tvResumoAvisos)
        tvMsgBadge = findViewById(R.id.tvMsgBadge)
        tvDocsBadge = findViewById(R.id.tvDocsBadge)
        tvUltimoPagamento = findViewById(R.id.tvUltimoPagamento)
        swipeRefresh = findViewById(R.id.swipeRefreshHome)

        swipeRefresh.setColorSchemeResources(R.color.accent)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.surface)

        btnPerfil = findViewById(R.id.btnPerfil)
        btnDocumentos = findViewById(R.id.btnDocumentos)
        btnPonto = findViewById(R.id.btnPonto)
        btnMensagens = findViewById(R.id.btnMensagens)
        btnOfflineHome = findViewById(R.id.btnOfflineHome)
        btnConfiguracoesHome = findViewById(R.id.btnConfiguracoesHome)
        btnSalarioHome = findViewById(R.id.btnSalarioHome)
        btnBeneficiosHome = findViewById(R.id.btnBeneficiosHome)

        btnSalarioHome.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            abrirHistoricoPagamentos()
        }

        btnBeneficiosHome.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            abrirHistoricoBeneficios()
        }

        btnPerfil.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this, PerfilActivity::class.java))
        }

        btnDocumentos.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this, DocumentosActivity::class.java))
        }

        btnPonto.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this, PontoActivity::class.java))
        }

        btnMensagens.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this, MensagensActivity::class.java))
        }

        btnOfflineHome.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this, DocumentosActivity::class.java).apply {
                putExtra("open_offline_list", true)
            })
        }

        btnConfiguracoesHome.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this, ConfiguracoesActivity::class.java))
        }

        // click duplicado removido — tratado acima com abrirHistoricoPagamentos()

        findViewById<BottomNavigationView>(R.id.bottomNavHome).apply {
            selectedItemId = R.id.nav_home
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> true
                    R.id.nav_tarefas -> {
                        startActivity(Intent(this@HomeActivity, DocumentosActivity::class.java))
                        true
                    }
                    R.id.nav_ponto -> {
                        startActivity(Intent(this@HomeActivity, PontoActivity::class.java))
                        true
                    }
                    R.id.nav_mensagens -> {
                        startActivity(Intent(this@HomeActivity, MensagensActivity::class.java))
                        true
                    }
                    R.id.nav_perfil -> {
                        startActivity(Intent(this@HomeActivity, PerfilActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnAtalhos).setOnClickListener {
            abrirPersonalizacaoAtalhos()
        }
        aplicarVisibilidadeAtalhos()

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Sair do aplicativo")
                .setMessage("Deseja realmente sair da sua conta?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sair") { _dialog, _ ->
                    session.clear()
                    goLogin()
                }
                .show()
        }

        swipeRefresh.setOnRefreshListener { carregarDados() }
        swipeRefresh.isRefreshing = true
        carregarDados()
        ensureNotificationPermission()
        registrarPushToken()
        ensureLocationAndSend()
        handleDeepLink()
        processarFilaPendente()
        registrarCallbackRede()
    }

    override fun onResume() {
        super.onResume()
        if (session.isIdleSessionExpired() && !session.isTrustedDeviceValid()) {
            session.clear()
            android.widget.Toast.makeText(this, "Sessão expirada por inatividade.", android.widget.Toast.LENGTH_LONG).show()
            goLogin()
            return
        }
        session.touchActivity()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        session.touchActivity()
    }

    private fun ensureLocationAndSend() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enviarLocalizacao()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @Suppress("MissingPermission")
    private fun enviarLocalizacao() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        val loc = lm.getLastKnownLocation(provider) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try { api.enviarLocalizacao(loc.latitude, loc.longitude, loc.accuracy) } catch (_: Exception) {}
        }
    }

    private fun handleDeepLink() {
        if (intent?.getBooleanExtra("notif_later", false) == true) {
            intent?.removeExtra("notif_later")
            android.widget.Toast.makeText(this, "Notificação marcada para depois.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val tipo = intent?.getStringExtra("tipo") ?: return
        val arquivoId = intent.getStringExtra("arquivo_id")?.toIntOrNull() ?: -1
        intent.removeExtra("tipo")
        when {
            tipo == "documento_assinar" && arquivoId > 0 ->
                startActivity(Intent(this, DocumentosActivity::class.java).apply {
                    putExtra(FcmService.EXTRA_ARQUIVO_ID, arquivoId)
                })
            tipo == "chat" || tipo == "chat_broadcast" ->
                startActivity(Intent(this, MensagensActivity::class.java))
            tipo == "novo_documento" ->
                startActivity(Intent(this, DocumentosActivity::class.java))
        }
    }

    private fun ensureNotificationPermission() {
        if (!session.notificationsEnabled) return
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun registrarPushToken() {
        if (!session.notificationsEnabled) return
        // Se o Firebase nao estiver configurado (sem google-services.json), ignora push sem derrubar o app.
        val firebaseApp = try {
            FirebaseApp.initializeApp(this) ?: FirebaseApp.getInstance()
        } catch (_: Exception) {
            null
        }
        if (firebaseApp == null) return

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isNullOrBlank()) return@addOnSuccessListener
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        api.registrarPushToken(token)
                    } catch (_: Exception) {
                        // Silencioso: não deve impactar uso do app.
                    }
                }
            }
            .addOnFailureListener {
                // Silencioso: ausência de push não bloqueia app.
            }
    }

    private fun carregarDados() {
        CoroutineScope(Dispatchers.IO).launch {
            val me = try { api.me() } catch (_: Exception) { MeResponse(ok = false) }
            val naoLidas = try { api.getNaoLidas() } catch (_: Exception) { 0 }
            val pontoDia = try { api.getPontoDia() } catch (_: Exception) { PontoDiaResponse(ok = false) }
            val versao = try { api.getVersaoApp() } catch (_: Exception) { null }
            val pendentesCount = try { api.pendentesAssinatura().itens.size } catch (_: Exception) { 0 }
            val ultimoPagamento = try { api.ultimoPagamento() } catch (_: Exception) { null }
            withContext(Dispatchers.Main) {
                swipeRefresh.isRefreshing = false
                val nome = me.funcionario?.nome ?: "colaborador"
                val primeiroNome = nome.split(" ").firstOrNull() ?: nome
                val inicial = nome.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                tvBoasVindas.text = "Olá, $primeiroNome"
                tvAvatar.text = inicial
                tvCargo.text = listOf(me.funcionario?.cargo, me.funcionario?.setor)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" • ")
                if (naoLidas > 0) {
                    tvMsgBadge.text = if (naoLidas > 9) "9+" else naoLidas.toString()
                    tvMsgBadge.visibility = View.VISIBLE
                } else {
                    tvMsgBadge.visibility = View.GONE
                }
                if (pendentesCount > 0) {
                    tvDocsBadge.text = if (pendentesCount > 9) "9+" else pendentesCount.toString()
                    tvDocsBadge.visibility = View.VISIBLE
                } else {
                    tvDocsBadge.visibility = View.GONE
                }

                tvResumoPonto.text = pontoDia.resumo?.horas_trabalhadas_fmt ?: "--:--"
                tvResumoTarefas.text = if (pendentesCount > 0) "$pendentesCount pendente(s)" else "Sem pendências"
                tvResumoAvisos.text = if (naoLidas > 0) "$naoLidas aviso(s)" else "Sem alertas"
                tvResumoAvisos.setTextColor(
                    ContextCompat.getColor(
                        this@HomeActivity,
                        if (naoLidas > 0) R.color.mobile_semantic_pending else R.color.mobile_semantic_success
                    )
                )

                // Atualiza o valor do último pagamento
                if (ultimoPagamento != null && ultimoPagamento.ok && ultimoPagamento.valor_liquido != null && ultimoPagamento.competencia != null) {
                    val valorFmt = "R$ %.2f".format(ultimoPagamento.valor_liquido)
                    tvUltimoPagamento.text = "Último pagamento: $valorFmt (${ultimoPagamento.competencia})"
                } else {
                    tvUltimoPagamento.text = "Último pagamento: --"
                }

                // Salva timestamp de última sincronização para a tela Sobre
                getSharedPreferences("rm_funcionario_app", MODE_PRIVATE)
                    .edit().putLong("last_sync_ts", System.currentTimeMillis()).apply()
                if (versao != null && versao.versao_minima > 0 && BuildConfig.VERSION_CODE < versao.versao_minima) {
                    mostrarDialogAtualizar(versao.download_url)
                }
            }
        }
    }

    private fun processarFilaPendente() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = retryQueue.process(api)
                if (result.enviados > 0) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            this@HomeActivity,
                            "${result.enviados} item(ns) da fila offline sincronizado(s).",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                TelemetryLogger.logHandled(this@HomeActivity, "fila_retry_processar", e)
            }
        }
    }

    private fun registrarCallbackRede() {
        if (networkCallback != null) return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                processarFilaPendente()
            }
        }
        try {
            connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback!!)
        } catch (_: Exception) {
            networkCallback = null
        }
    }

    private fun abrirPersonalizacaoAtalhos() {
        val labels = arrayOf("Documentos", "Perfil", "Ponto", "Mensagens", "Offline", "Configurações", "Pagamento", "Benefícios")
        val keys = listOf("documentos", "perfil", "ponto", "mensagens", "offline", "config", "pagamento", "beneficios")
        val enabled = carregarAtalhosHabilitados().toMutableSet()
        val checks = keys.map { enabled.contains(it) }.toBooleanArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Personalizar atalhos")
            .setMultiChoiceItems(labels, checks) { _, which, isChecked ->
                if (isChecked) enabled.add(keys[which]) else enabled.remove(keys[which])
            }
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                if (enabled.isEmpty()) enabled.add("documentos")
                salvarAtalhosHabilitados(enabled)
                aplicarVisibilidadeAtalhos()
            }
            .show()
    }

    private fun carregarAtalhosHabilitados(): Set<String> {
        val prefs = getSharedPreferences(PREF_SHORTCUTS, MODE_PRIVATE)
        val raw = prefs.getString(KEY_ENABLED, "") ?: ""
        if (raw.isBlank()) {
            return setOf("documentos", "perfil", "ponto", "mensagens", "pagamento", "beneficios")
        }
        return raw.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    private fun salvarAtalhosHabilitados(enabled: Set<String>) {
        getSharedPreferences(PREF_SHORTCUTS, MODE_PRIVATE)
            .edit()
            .putString(KEY_ENABLED, enabled.joinToString(","))
            .apply()
    }

    private fun aplicarVisibilidadeAtalhos() {
        val enabled = carregarAtalhosHabilitados()
        btnDocumentos.visibility = if (enabled.contains("documentos")) View.VISIBLE else View.GONE
        btnPerfil.visibility = if (enabled.contains("perfil")) View.VISIBLE else View.GONE
        btnPonto.visibility = if (enabled.contains("ponto")) View.VISIBLE else View.GONE
        btnMensagens.visibility = if (enabled.contains("mensagens")) View.VISIBLE else View.GONE
        btnOfflineHome.visibility = if (enabled.contains("offline")) View.VISIBLE else View.GONE
        btnConfiguracoesHome.visibility = if (enabled.contains("config")) View.VISIBLE else View.GONE
        btnSalarioHome.visibility = if (enabled.contains("pagamento")) View.VISIBLE else View.GONE
        btnBeneficiosHome.visibility = if (enabled.contains("beneficios")) View.VISIBLE else View.GONE
    }

    private fun mostrarDialogAtualizar(downloadUrl: String?) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Atualização necessária")
            .setMessage("Há uma versão mais nova do app disponível. Por favor, atualize para continuar usando.")
            .setCancelable(false)
            .setPositiveButton("Atualizar") { _, _ ->
                val url = downloadUrl?.takeIf { it.isNotBlank() }
                    ?: "${session.apiBaseUrl.trimEnd('/')}/app/download"
                try {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                } catch (_: Exception) {}
            }
            .create()
        if (!isFinishing && !isDestroyed) dialog.show()
    }

    private fun compFmt(comp: String): String {
        if (comp.length != 7) return comp
        val mes = comp.substring(5).toIntOrNull() ?: 0
        val ano = comp.substring(0, 4)
        return listOf("","Jan","Fev","Mar","Abr","Mai","Jun","Jul","Ago","Set","Out","Nov","Dez").getOrElse(mes) { comp } + "/$ano"
    }

    private fun abrirHistoricoPagamentos() {
        val loading = MaterialAlertDialogBuilder(this)
            .setTitle("Histórico de pagamento")
            .setMessage("Buscando...")
            .setCancelable(false)
            .create()
        loading.show()
        CoroutineScope(Dispatchers.IO).launch {
            val resp = try { api.historicoPagamentos() } catch (_: Exception) { null }
            withContext(Dispatchers.Main) {
                loading.dismiss()
                val historico = resp?.historico ?: emptyList()
                val texto = if (historico.isEmpty()) "Nenhum pagamento registrado."
                else historico.joinToString("\n") { p ->
                    val valor = "R$ %,.2f".format(p.valor_liquido)
                    if (p.obs.isNotBlank()) "• ${compFmt(p.competencia)}  →  $valor\n  (${p.obs})"
                    else "• ${compFmt(p.competencia)}  →  $valor"
                }
                MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle("💰 Histórico de Salário")
                    .setMessage(texto)
                    .setPositiveButton("Fechar", null)
                    .setNeutralButton("Holerites") { _, _ ->
                        startActivity(Intent(this@HomeActivity, DocumentosActivity::class.java).apply {
                            putExtra("preset_categoria", "holerite")
                        })
                    }
                    .show()
            }
        }
    }

    private fun abrirHistoricoBeneficios() {
        val loading = MaterialAlertDialogBuilder(this)
            .setTitle("Histórico de benefícios")
            .setMessage("Buscando...")
            .setCancelable(false)
            .create()
        loading.show()
        CoroutineScope(Dispatchers.IO).launch {
            val resp = try { api.historicoBeneficios() } catch (_: Exception) { null }
            withContext(Dispatchers.Main) {
                loading.dismiss()
                val historico = resp?.historico ?: emptyList()
                val texto = if (historico.isEmpty()) "Nenhum benefício registrado."
                else historico.joinToString("\n") { b ->
                    val total = "R$ %,.2f".format(b.total)
                    buildString {
                        append("• ${compFmt(b.competencia)}  →  $total")
                        if (b.detalhes.isNotBlank()) append("\n  ${b.detalhes}")
                        if (b.obs.isNotBlank()) append("\n  (${b.obs})")
                    }
                }
                MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle("🎁 Histórico de Benefícios")
                    .setMessage(texto)
                    .setPositiveButton("Fechar", null)
                    .show()
            }
        }
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(SessionManager.ACTION_LOGOUT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logoutReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logoutReceiver, filter)
        }
        registrarCallbackRede()
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(logoutReceiver) } catch (_: Exception) {}
        val cb = networkCallback
        if (cb != null) {
            try { connectivityManager.unregisterNetworkCallback(cb) } catch (_: Exception) {}
            networkCallback = null
        }
    }
}

