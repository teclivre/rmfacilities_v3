package br.com.rmfacilities.funcionarioapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvBoasVindas: TextView
    private lateinit var tvCargo: TextView
    private lateinit var tvAvatar: TextView
    private lateinit var tvMsgBadge: TextView

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

        if (session.accessToken.isBlank()) {
            goLogin(); return
        }

        tvBoasVindas = findViewById(R.id.tvBoasVindas)
        tvCargo = findViewById(R.id.tvCargo)
        tvAvatar = findViewById(R.id.tvAvatar)
        tvMsgBadge = findViewById(R.id.tvMsgBadge)
        swipeRefresh = findViewById(R.id.swipeRefreshHome)

        swipeRefresh.setColorSchemeResources(R.color.accent)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.surface)

        findViewById<LinearLayout>(R.id.btnPerfil).setOnClickListener {
            startActivity(Intent(this, PerfilActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnDocumentos).setOnClickListener {
            startActivity(Intent(this, DocumentosActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnPonto).setOnClickListener {
            Toast.makeText(this, "Modulo de ponto em implantacao nesta versao.", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.btnMensagens).setOnClickListener {
            startActivity(Intent(this, MensagensActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            session.clear()
            goLogin()
        }

        swipeRefresh.setOnRefreshListener { carregarDados() }
        swipeRefresh.isRefreshing = true
        carregarDados()
        ensureNotificationPermission()
        registrarPushToken()
        ensureLocationAndSend()
        handleDeepLink()
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
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun registrarPushToken() {
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
            val versao = try { api.getVersaoApp() } catch (_: Exception) { null }
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
                if (versao != null && versao.versao_minima > 0 && BuildConfig.VERSION_CODE < versao.versao_minima) {
                    mostrarDialogAtualizar(versao.download_url)
                }
            }
        }
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
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(logoutReceiver) } catch (_: Exception) {}
    }
}

