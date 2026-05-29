package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.Locale
import androidx.lifecycle.lifecycleScope

class DocumentosActivity : BaseActivity() {
    private lateinit var session: SessionManager
    override fun provideSession() = session
    private lateinit var api: ApiClient
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var adapter: DocumentoAdapter
    private lateinit var rv: RecyclerView

    private var filtroQ = ""
    private var filtroCategoria = ""
    private var filtroAno = ""
    private var filtroStatus = "todos"
    private lateinit var shimmerDocs: ShimmerFrameLayout
    private lateinit var scrollChips: HorizontalScrollView
    private lateinit var chipGroupAnos: ChipGroup
    private lateinit var chipGroupCategorias: ChipGroup
    private lateinit var chipGroupStatus: ChipGroup
    private var primeiroLoad = true
    private var anosDisponiveis: List<String> = emptyList()
    private var categoriasDisponiveis: List<String> = emptyList()
    private lateinit var tvUltimoAsoDoc: android.widget.TextView
    private lateinit var layoutEmptyDocs: android.view.View
    private lateinit var tvEmptyDocs: android.widget.TextView
    private lateinit var offlineStore: OfflineDocsStore
    private lateinit var retryQueue: ActionRetryQueue
    private var pendentesAssinatura: List<DocumentoItem> = emptyList()

    private val debounceHandler = Handler(Looper.getMainLooper())
    private val debounceRunnable = Runnable { carregarComFiltros() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_documentos)

        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavApp)
            ?.let { setupAppBottomNav(it, R.id.nav_tarefas) }

        session = SessionManager(this)
        api = ApiClient(session)
        offlineStore = OfflineDocsStore(this)
        retryQueue = ActionRetryQueue(this)

        swipe = findViewById(R.id.swipeDocs)
        rv = findViewById(R.id.rvDocs)
        shimmerDocs = findViewById(R.id.shimmerDocs)
        scrollChips = findViewById(R.id.scrollChips)
        chipGroupAnos = findViewById(R.id.chipGroupAnos)
        chipGroupCategorias = findViewById(R.id.chipGroupCategorias)
        chipGroupStatus = findViewById(R.id.chipGroupStatus)
        tvUltimoAsoDoc = findViewById(R.id.tvUltimoAsoDoc)
        layoutEmptyDocs = findViewById(R.id.layoutEmptyDocs)
        tvEmptyDocs = findViewById(R.id.tvEmptyDocs)

        // Botão voltar
        findViewById<android.widget.TextView>(R.id.btnVoltar).setOnClickListener { finish() }

        // Botão histórico de assinaturas
        findViewById<MaterialButton>(R.id.btnHistoricoAss).setOnClickListener {
            startActivity(Intent(this, HistoricoAssinaturasActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnOfflineDocs).setOnClickListener {
            abrirListaOffline()
        }
        findViewById<MaterialButton>(R.id.btnLerPendentes).setOnClickListener {
            abrirPendentesEmSequencia()
        }
        findViewById<MaterialButton>(R.id.btnBaixarPendentes).setOnClickListener {
            baixarPendentesParaOffline()
        }

        // Busca por nome
        findViewById<EditText>(R.id.etBuscaDoc).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filtroQ = s?.toString() ?: ""
                debounceHandler.removeCallbacks(debounceRunnable)
                debounceHandler.postDelayed(debounceRunnable, 400)
            }
        })

        val presetCategoria = intent.getStringExtra("preset_categoria")?.trim().orEmpty()
        if (presetCategoria.isNotBlank()) {
            filtroCategoria = when (presetCategoria.lowercase(Locale.getDefault())) {
                "holerite", "holerites", "pagamento", "salario", "salário" -> "Holerites"
                else -> presetCategoria
            }
            // Atualiza o título da tela de acordo com o filtro aplicado
            val titulo = when (filtroCategoria) {
                "Holerites" -> "Holerites"
                else -> filtroCategoria.replaceFirstChar { it.uppercase() }
            }
            findViewById<android.widget.TextView>(R.id.tvTituloDocumentos).text = titulo
        }
        val presetBusca = intent.getStringExtra("preset_busca")?.trim().orEmpty()
        if (presetBusca.isNotBlank()) {
            filtroQ = presetBusca
            findViewById<EditText>(R.id.etBuscaDoc).setText(presetBusca)
        }

        adapter = DocumentoAdapter(
            onBaixar = { item -> baixarDocumento(item) },
            onAssinar = { item -> confirmarAssinatura(item) }
        )
        rv.adapter = adapter
        if (rv.layoutManager == null) {
            rv.layoutManager = LinearLayoutManager(this)
        }
        rv.setHasFixedSize(true)
        rv.setItemViewCacheSize(24)
        (rv.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        swipe.setOnRefreshListener { carregarComFiltros() }
        swipe.isRefreshing = false
        shimmerDocs.startShimmer()
        carregarUltimoAso()
        carregar()
    }

    private fun carregarUltimoAso() {
        lifecycleScope.launch(Dispatchers.IO) {
            val me = try { api.me() } catch (_: Exception) { MeResponse(ok = false) }
            withContext(Dispatchers.Main) {
                val comp = me.funcionario?.ultimo_aso_competencia?.trim().orEmpty()
                val enviado = me.funcionario?.ultimo_aso_enviado_em?.trim().orEmpty()
                tvUltimoAsoDoc.text = when {
                    comp.isNotBlank() -> "Competência: $comp"
                    enviado.length >= 10 -> "Enviado em: ${enviado.substring(0, 10)}"
                    else -> "Não informado"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        session.touchActivity()
        // Handle deep link from push notification
        val arquivoId = intent.getIntExtra(FcmService.EXTRA_ARQUIVO_ID, -1)
        if (arquivoId > 0) {
            intent.removeExtra(FcmService.EXTRA_ARQUIVO_ID)
            carregarEScrollar(arquivoId)
        }
        val openOffline = intent.getBooleanExtra("open_offline_list", false)
        if (openOffline) {
            intent.removeExtra("open_offline_list")
            abrirListaOffline()
        }
    }

    override fun onDestroy() {
        // Evita vazamento de Activity via Runnable pendente do debounce
        debounceHandler.removeCallbacks(debounceRunnable)
        super.onDestroy()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        session.touchActivity()
    }

    private fun carregar() {
        carregarComFiltros()
    }

    private fun carregarComFiltros(scrollToArquivoId: Int = -1) {
        lifecycleScope.launch(Dispatchers.IO) {
            val docs = try { api.documentos(q = filtroQ, categoria = filtroCategoria, ano = filtroAno) }
                       catch (e: Exception) { DocsResponse(ok = false, erro = e.message) }
            val pendentes = try { api.pendentesAssinatura() }
                            catch (e: Exception) { DocsResponse(ok = false) }
            withContext(Dispatchers.Main) {
                swipe.isRefreshing = false
                if (primeiroLoad) {
                    shimmerDocs.stopShimmer()
                    shimmerDocs.visibility = View.GONE
                    rv.visibility = View.VISIBLE
                    primeiroLoad = false
                }
                if (docs.ok) {
                    pendentesAssinatura = pendentes.itens ?: emptyList()
                    val pendentesFiltrados = aplicarFiltrosLocais(pendentes.itens ?: emptyList())
                    val docsFiltrados = aplicarFiltrosLocais(docs.itens ?: emptyList())
                    adapter.replaceAll(pendentesFiltrados, docsFiltrados)
                    atualizarChips((pendentes.itens ?: emptyList()) + (docs.itens ?: emptyList()))
                    val isEmpty = pendentesFiltrados.isEmpty() && docsFiltrados.isEmpty()
                    layoutEmptyDocs.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    rv.visibility = if (isEmpty) View.GONE else View.VISIBLE
                    if (isEmpty) {
                        tvEmptyDocs.text = if (filtroCategoria.isNotBlank())
                            "Nenhum documento encontrado\npara a categoria \"$filtroCategoria\"."
                        else "Nenhum documento encontrado."
                    }
                    if (scrollToArquivoId > 0) {
                        scrollToArquivo(scrollToArquivoId)
                    }
                } else {
                    val offline = offlineStore.toDocumentoItems()
                    if (offline.isNotEmpty()) {
                        adapter.replaceAll(emptyList(), offline)
                        layoutEmptyDocs.visibility = View.GONE
                        rv.visibility = View.VISIBLE
                        Toast.makeText(this@DocumentosActivity, "Sem conexão. Exibindo documentos offline.", Toast.LENGTH_LONG).show()
                    } else {
                        layoutEmptyDocs.visibility = View.VISIBLE
                        rv.visibility = View.GONE
                        tvEmptyDocs.text = docs.erro ?: "Falha ao carregar documentos."
                        Toast.makeText(this@DocumentosActivity, docs.erro ?: "Falha ao carregar", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun carregarEScrollar(arquivoId: Int) {
        swipe.isRefreshing = true
        carregarComFiltros(scrollToArquivoId = arquivoId)
    }

    private fun scrollToArquivo(arquivoId: Int) {
        val pos = adapter.indexOfArquivoId(arquivoId)
        if (pos >= 0) rv.smoothScrollToPosition(pos)
    }

    private fun confirmarAssinatura(item: DocumentoItem) {
        val detalhes = listOf(
            item.categoria_label,
            item.competencia,
            item.criado_fmt
        ).filter { !it.isNullOrBlank() }.joinToString("\n")

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirmar assinatura")
            .setMessage("${item.nome_arquivo ?: "Documento"}\n\n$detalhes\n\nSua identidade será confirmada antes de assinar.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("✍ Prosseguir") { _, _ ->
                iniciarStepUp(item)
            }
            .show()
    }

    private fun canUseBiometric(): Boolean {
        val bm = BiometricManager.from(this)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun iniciarStepUp(item: DocumentoItem) {
        if (canUseBiometric() && session.biometricEnabled && session.biometricCpf.isNotBlank()) {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        assinarDocumento(item, stepupBiometria = true)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            Toast.makeText(this@DocumentosActivity, "Biometria: $errString", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Confirmar identidade")
                    .setSubtitle("Autentique para assinar: ${item.nome_arquivo ?: "documento"}")
                    .setNegativeButtonText("Usar código")
                    .build()
            )
        } else {
            solicitarOtpEAssinar(item)
        }
    }

    private fun solicitarOtpEAssinar(item: DocumentoItem) {
        swipe.isRefreshing = true
        lifecycleScope.launch(Dispatchers.IO) {
            val resp = try {
                api.solicitarStepupOtp(item.id)
            } catch (e: Exception) {
                ApiSimpleResponse(ok = false, erro = e.message)
            }
            withContext(Dispatchers.Main) {
                swipe.isRefreshing = false
                if (resp.ok) {
                    mostrarDialogOtpAssinatura(item, resp.mensagem ?: "Código enviado.")
                } else {
                    Toast.makeText(this@DocumentosActivity, resp.erro ?: "Falha ao enviar código", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun mostrarDialogOtpAssinatura(item: DocumentoItem, infoEnvio: String) {
        val etOtp = EditText(this).apply {
            hint = "Código de 6 dígitos"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            maxLines = 1
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val dp16 = (16 * resources.displayMetrics.density).toInt()
            setPadding(dp16 * 2, dp16, dp16 * 2, dp16 / 2)
            addView(android.widget.TextView(this@DocumentosActivity).apply {
                text = infoEnvio
                setTextColor(androidx.core.content.ContextCompat.getColor(this@DocumentosActivity, R.color.text_hint))
                textSize = 13f
                setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
            })
            addView(etOtp)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Código de confirmação")
            .setMessage("Para assinar \"${item.nome_arquivo ?: "documento"}\" insira o código enviado.")
            .setView(layout)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Reenviar") { _, _ -> solicitarOtpEAssinar(item) }
            .setPositiveButton("✍ Assinar") { _, _ ->
                val codigo = etOtp.text.toString().trim()
                if (!codigo.matches(Regex("\\d{6}"))) {
                    Toast.makeText(this, "O código deve ter exatamente 6 dígitos.", Toast.LENGTH_SHORT).show()
                } else {
                    assinarDocumento(item, stepupOtp = codigo)
                }
            }
            .show()
    }

    private fun baixarDocumento(item: DocumentoItem) {
        val path = item.app_download_url
        if (!path.isNullOrBlank() && path.startsWith("offline://")) {
            val id = path.removePrefix("offline://").toIntOrNull()
            val offline = id?.let { offlineStore.findById(it) }
            if (offline != null) {
                val file = offlineStore.openDecrypted(offline)
                if (file != null && file.exists()) {
                    abrirArquivo(file)
                    return
                }
            }
            Toast.makeText(this, "Arquivo offline não encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "Link de download indisponível", Toast.LENGTH_SHORT).show()
            return
        }

        swipe.isRefreshing = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bytes = api.downloadFile(path)
                val file = offlineStore.saveDownloaded(item, bytes)

                withContext(Dispatchers.Main) {
                    swipe.isRefreshing = false
                    abrirArquivo(file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipe.isRefreshing = false
                    TelemetryLogger.logHandled(this@DocumentosActivity, "documentos_download", e)
                    retryQueue.enqueueDocumentoDownload(item)
                    Toast.makeText(this@DocumentosActivity, "Sem conexão agora. O download entrou na fila offline.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun baixarPendentesParaOffline() {
        if (pendentesAssinatura.isEmpty()) {
            Toast.makeText(this, "Não há pendentes para baixar.", Toast.LENGTH_SHORT).show()
            return
        }
        var enfileirados = 0
        for (item in pendentesAssinatura) {
            val path = item.app_download_url?.trim().orEmpty()
            if (path.isNotBlank() && !path.startsWith("offline://")) {
                retryQueue.enqueueDocumentoDownload(item)
                enfileirados += 1
            }
        }
        if (enfileirados == 0) {
            Toast.makeText(this, "Nenhum item válido para fila.", Toast.LENGTH_SHORT).show()
            return
        }
        swipe.isRefreshing = true
        lifecycleScope.launch(Dispatchers.IO) {
            val result = try { retryQueue.process(api) } catch (_: Exception) { null }
            withContext(Dispatchers.Main) {
                swipe.isRefreshing = false
                if (result == null) {
                    Toast.makeText(this@DocumentosActivity, "$enfileirados download(s) foram salvos na fila offline.", Toast.LENGTH_LONG).show()
                } else {
                    val msg = if (result.pendentes > 0) {
                        "${result.enviados} baixado(s), ${result.pendentes} pendente(s) para retomada automática."
                    } else {
                        "Todos os downloads pendentes foram concluídos: ${result.enviados}."
                    }
                    Toast.makeText(this@DocumentosActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun abrirListaOffline() {
        val offline = offlineStore.list()
        if (offline.isEmpty()) {
            Toast.makeText(this, "Nenhum documento offline salvo.", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = offline.map { "${it.nome} (${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it.salvoEm))})" }
        MaterialAlertDialogBuilder(this)
            .setTitle("Documentos offline")
            .setItems(labels.toTypedArray()) { _, which ->
                val sel = offline[which]
                val file = offlineStore.openDecrypted(sel)
                if (file != null && file.exists()) abrirArquivo(file)
                else Toast.makeText(this, "Arquivo não encontrado", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun abrirPendentesEmSequencia() {
        if (pendentesAssinatura.isEmpty()) {
            Toast.makeText(this, "Nenhum documento pendente para leitura.", Toast.LENGTH_SHORT).show()
            return
        }
        val opcoes = arrayOf("Mais recente primeiro", "Mais antigo primeiro", "Nome A-Z")
        var escolha = 0
        MaterialAlertDialogBuilder(this)
            .setTitle("Ordem de leitura")
            .setSingleChoiceItems(opcoes, 0) { _, which -> escolha = which }
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Abrir") { _, _ ->
                abrirPendentesOrdenados(escolha)
            }
            .show()
    }

    private fun abrirPendentesOrdenados(ordem: Int) {
        swipe.isRefreshing = true
        lifecycleScope.launch(Dispatchers.IO) {
            val sorted = when (ordem) {
                1 -> pendentesAssinatura.sortedBy { (it.competencia ?: "").trim() }
                2 -> pendentesAssinatura.sortedBy { (it.nome_arquivo ?: "").lowercase(Locale.getDefault()) }
                else -> pendentesAssinatura.sortedByDescending { (it.competencia ?: "").trim() }
            }
            val files = arrayListOf<String>()
            val titles = arrayListOf<String>()
            for (item in sorted) {
                val path = item.app_download_url
                if (path.isNullOrBlank()) continue
                try {
                    val file = if (path.startsWith("offline://")) {
                        val id = path.removePrefix("offline://").toIntOrNull()
                        val offline = id?.let { offlineStore.findById(it) }
                        offline?.let { offlineStore.openDecrypted(it) }
                    } else {
                        val bytes = api.downloadFile(path)
                        offlineStore.saveDownloaded(item, bytes)
                    }
                    if (file != null && file.exists() && file.extension.equals("pdf", ignoreCase = true)) {
                        files.add(file.absolutePath)
                        titles.add(item.nome_arquivo ?: file.name)
                    }
                } catch (_: Exception) {
                    // Ignora falhas pontuais para abrir o máximo de arquivos possível.
                }
            }
            withContext(Dispatchers.Main) {
                swipe.isRefreshing = false
                if (files.isEmpty()) {
                    Toast.makeText(this@DocumentosActivity, "Nenhum PDF pendente disponível para abrir.", Toast.LENGTH_LONG).show()
                    return@withContext
                }
                startActivity(Intent(this@DocumentosActivity, PdfPreviewActivity::class.java).apply {
                    putExtra(PdfPreviewActivity.EXTRA_TITLE, "Pendentes para assinatura")
                    putStringArrayListExtra(PdfPreviewActivity.EXTRA_FILE_PATHS, files)
                    putStringArrayListExtra(PdfPreviewActivity.EXTRA_TITLES, titles)
                })
            }
        }
    }

    private fun assinarDocumento(item: DocumentoItem, stepupOtp: String? = null, stepupBiometria: Boolean = false) {
        swipe.isRefreshing = true
        lifecycleScope.launch(Dispatchers.IO) {
            val resp = try {
                api.assinarDocumento(item.id, stepupOtp = stepupOtp, stepupBiometria = stepupBiometria)
            } catch (e: Exception) {
                ApiSimpleResponse(ok = false, erro = e.message)
            }
            withContext(Dispatchers.Main) {
                swipe.isRefreshing = false
                if (resp.ok) {
                    Toast.makeText(this@DocumentosActivity, "Documento assinado com sucesso.", Toast.LENGTH_SHORT).show()
                    // Invalida cópia offline antiga (sem carimbo) para forçar novo download do PDF assinado.
                    try { offlineStore.removeById(item.id) } catch (_: Exception) {}
                    carregarComFiltros()
                } else {
                    val rawErro = resp.erro ?: ""
                    val mensagem = when {
                        rawErro.contains("expirado", ignoreCase = true) ->
                            "Código expirado. Solicite um novo código e tente novamente."
                        rawErro.contains("invalido", ignoreCase = true) || rawErro.contains("inválido", ignoreCase = true) ->
                            "Código incorreto. Verifique e tente novamente."
                        rawErro.contains("muitas tentativas", ignoreCase = true) || rawErro.contains("429", ignoreCase = true) ->
                            "Muitas tentativas. Aguarde alguns minutos e tente novamente."
                        rawErro.contains("ja assinado", ignoreCase = true) || rawErro.contains("já assinado", ignoreCase = true) ->
                            "Este documento já foi assinado anteriormente."
                        rawErro.contains("solicite", ignoreCase = true) ->
                            "É necessário solicitar um código antes de assinar."
                        rawErro.contains("ja utilizado", ignoreCase = true) || rawErro.contains("já utilizado", ignoreCase = true) ->
                            "Este código já foi utilizado. Solicite um novo."
                        rawErro.isNotBlank() -> rawErro
                        else -> "Falha ao assinar documento. Tente novamente."
                    }
                    Toast.makeText(this@DocumentosActivity, mensagem, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun abrirArquivo(file: File) {
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"

        if (ext == "pdf") {
            val intent = Intent(this, PdfPreviewActivity::class.java).apply {
                putExtra(PdfPreviewActivity.EXTRA_FILE_PATH, file.absolutePath)
                putExtra(PdfPreviewActivity.EXTRA_TITLE, file.name)
            }
            startActivity(intent)
            return
        }

        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(intent, "Abrir documento"))
        } catch (e: Exception) {
            Toast.makeText(this, "Nenhum app disponível para abrir este arquivo", Toast.LENGTH_LONG).show()
        }
    }

    private fun atualizarChips(itens: List<DocumentoItem>) {
        val anosNovos = itens.mapNotNull { item ->
            item.competencia?.take(4)?.takeIf { it.matches(Regex("\\d{4}")) }
                ?: item.criado_fmt?.takeLast(4)?.takeIf { it.matches(Regex("\\d{4}")) }
        }.toSortedSet(compareByDescending { it }).toList()

        val categoriasNovas = itens
            .mapNotNull { it.categoria_label?.trim()?.takeIf { c -> c.isNotBlank() } }
            .toSortedSet(compareBy { it.lowercase(Locale.getDefault()) })
            .toList()

        if (anosNovos.isNotEmpty()) {
            anosDisponiveis = (anosNovos + anosDisponiveis)
                .toSortedSet(compareByDescending { it }).toList()
        }
        if (categoriasNovas.isNotEmpty()) {
            categoriasDisponiveis = (categoriasNovas + categoriasDisponiveis)
                .toSortedSet(compareBy { it.lowercase(Locale.getDefault()) }).toList()
        }

        if (anosDisponiveis.isEmpty()) { scrollChips.visibility = View.GONE; return }
        scrollChips.visibility = View.VISIBLE

        val labels = listOf("Todos") + anosDisponiveis
        val currentLabels = (0 until chipGroupAnos.childCount)
            .mapNotNull { (chipGroupAnos.getChildAt(it) as? Chip)?.text?.toString() }

        if (labels != currentLabels) {
            chipGroupAnos.setOnCheckedChangeListener(null)
            chipGroupAnos.removeAllViews()
            for ((i, label) in labels.withIndex()) {
                chipGroupAnos.addView(Chip(this).apply {
                    id = i + 1
                    text = label
                    isCheckable = true
                    isChecked = (label == "Todos" && filtroAno.isEmpty()) || label == filtroAno
                })
            }
            chipGroupAnos.setOnCheckedChangeListener { group, checkedId ->
                if (checkedId == View.NO_ID) return@setOnCheckedChangeListener
                val chip = group.findViewById<Chip>(checkedId) ?: return@setOnCheckedChangeListener
                val sel = chip.text.toString()
                val novoFiltro = if (sel == "Todos") "" else sel
                if (novoFiltro == filtroAno) return@setOnCheckedChangeListener
                filtroAno = novoFiltro
                carregarComFiltros()
            }
        } else {
            for (i in 0 until chipGroupAnos.childCount) {
                val chip = chipGroupAnos.getChildAt(i) as? Chip ?: continue
                val label = chip.text.toString()
                chip.isChecked = (label == "Todos" && filtroAno.isEmpty()) || label == filtroAno
            }
        }

        val catLabels = listOf("Todas") + categoriasDisponiveis
        val currentCatLabels = (0 until chipGroupCategorias.childCount)
            .mapNotNull { (chipGroupCategorias.getChildAt(it) as? Chip)?.text?.toString() }
        if (catLabels != currentCatLabels) {
            chipGroupCategorias.setOnCheckedChangeListener(null)
            chipGroupCategorias.removeAllViews()
            for ((i, label) in catLabels.withIndex()) {
                chipGroupCategorias.addView(Chip(this).apply {
                    id = 2000 + i
                    text = label
                    isCheckable = true
                    isChecked = (label == "Todas" && filtroCategoria.isEmpty()) || label == filtroCategoria
                })
            }
            chipGroupCategorias.setOnCheckedChangeListener { group, checkedId ->
                if (checkedId == View.NO_ID) return@setOnCheckedChangeListener
                val chip = group.findViewById<Chip>(checkedId) ?: return@setOnCheckedChangeListener
                val sel = chip.text.toString()
                filtroCategoria = if (sel == "Todas") "" else sel
                carregarComFiltros()
            }
        }

        val statusLabels = listOf(
            "Todos" to "todos",
            "Pendentes" to "pendente",
            "Assinados" to "assinado"
        )
        val currentStatusLabels = (0 until chipGroupStatus.childCount)
            .mapNotNull { (chipGroupStatus.getChildAt(it) as? Chip)?.text?.toString() }
        val targetStatusLabels = statusLabels.map { it.first }
        if (currentStatusLabels != targetStatusLabels) {
            chipGroupStatus.setOnCheckedChangeListener(null)
            chipGroupStatus.removeAllViews()
            for ((idx, pair) in statusLabels.withIndex()) {
                chipGroupStatus.addView(Chip(this).apply {
                    id = 3000 + idx
                    text = pair.first
                    isCheckable = true
                    isChecked = pair.second == filtroStatus
                })
            }
            chipGroupStatus.setOnCheckedChangeListener { group, checkedId ->
                if (checkedId == View.NO_ID) return@setOnCheckedChangeListener
                val idx = checkedId - 3000
                filtroStatus = statusLabels.getOrNull(idx)?.second ?: "todos"
                carregarComFiltros()
            }
        }
    }

    private fun aplicarFiltrosLocais(origem: List<DocumentoItem>): List<DocumentoItem> {
        val q = filtroQ.trim().lowercase(Locale.getDefault())
        return origem.filter { item ->
            val nome = (item.nome_arquivo ?: "").lowercase(Locale.getDefault())
            val categoria = (item.categoria_label ?: item.categoria ?: "")
            val competencia = (item.competencia ?: "")
            val criado = (item.criado_fmt ?: "")
            val ano = item.ano ?: ""
            val statusNorm = (item.ass_status ?: "").trim().lowercase(Locale.getDefault())
            val isAssinado = statusNorm == "assinado" || statusNorm == "concluida"
            val isPendente = !isAssinado && (item.can_assinar || statusNorm == "pendente")
            val statusOk = when (filtroStatus) {
                "pendente" -> isPendente
                "assinado" -> isAssinado
                else -> true
            }
            val categoriaOk = filtroCategoria.isBlank() || categoria.equals(filtroCategoria, ignoreCase = true)
            val anoOk = filtroAno.isBlank() || ano == filtroAno || competencia.startsWith(filtroAno) || criado.endsWith(filtroAno)
            val textoOk = q.isBlank() || nome.contains(q) || categoria.lowercase(Locale.getDefault()).contains(q)
            statusOk && categoriaOk && anoOk && textoOk
        }
    }
}
