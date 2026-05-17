package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MensagensActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient
    private lateinit var adapter: MensagemAdapter
    private lateinit var avisoAdapter: AvisoAdapter
    private lateinit var rvMensagens: RecyclerView
    private lateinit var rvAvisos: RecyclerView
    private lateinit var etMensagem: EditText
    private lateinit var tvBadge: TextView
    private lateinit var tvAvisosVazio: TextView
    private lateinit var tvStatusRh: TextView
    private lateinit var panelChat: LinearLayout
    private lateinit var panelAvisos: LinearLayout
    private lateinit var btnTabChat: MaterialButton
    private lateinit var btnTabAvisos: MaterialButton
    private lateinit var retryQueue: ActionRetryQueue
    private lateinit var swipeChat: SwipeRefreshLayout
    private lateinit var progressEnvio: ProgressBar
    private lateinit var tvCharCount: TextView

    private var cameraPhotoUri: Uri? = null
    private val pollingHandler = Handler(Looper.getMainLooper())
    private var isInChatTab = true
    private val pollingRunnable = object : Runnable {
        override fun run() {
            if (isInChatTab) {
                carregarMensagens(silently = true)
                pollingHandler.postDelayed(this, 5_000)
            }
        }
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) enviarArquivo(uri)
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraPhotoUri?.let { enviarArquivo(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mensagens)

        session = SessionManager(this)
        api = ApiClient(session)
        retryQueue = ActionRetryQueue(this)

        rvMensagens = findViewById(R.id.rvMensagens)
        rvAvisos = findViewById(R.id.rvAvisos)
        etMensagem = findViewById(R.id.etMensagem)
        tvBadge = findViewById(R.id.tvBadge)
        tvAvisosVazio = findViewById(R.id.tvAvisosVazio)
        tvStatusRh = findViewById(R.id.tvStatusRh)
        panelChat = findViewById(R.id.panelChat)
        panelAvisos = findViewById(R.id.panelAvisos)
        btnTabChat = findViewById(R.id.btnTabChat)
        btnTabAvisos = findViewById(R.id.btnTabAvisos)
        swipeChat = findViewById(R.id.swipeChat)
        progressEnvio = findViewById(R.id.progressEnvio)
        tvCharCount = findViewById(R.id.tvCharCount)

        adapter = MensagemAdapter(
            onAbrirArquivo = { item -> abrirArquivoMensagem(item) },
            onApagarMensagem = { item ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val ok = api.deletarMensagem(item.id)
                    withContext(Dispatchers.Main) {
                        if (ok) adapter.removeMensagem(item.id)
                        else android.widget.Toast.makeText(
                            this@MensagensActivity,
                            "Não foi possível apagar a mensagem.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
        rvMensagens.layoutManager = LinearLayoutManager(this).also { it.stackFromEnd = true }
        rvMensagens.adapter = adapter

        avisoAdapter = AvisoAdapter(onLido = { aviso ->
            lifecycleScope.launch(Dispatchers.IO) {
                api.marcarComunicadoLido(aviso.id)
            }
        })
        rvAvisos.layoutManager = LinearLayoutManager(this)
        rvAvisos.adapter = avisoAdapter

        btnTabChat.setOnClickListener { mostrarAba("chat") }
        btnTabAvisos.setOnClickListener { mostrarAba("avisos") }

        // Swipe-to-refresh do chat
        swipeChat.setColorSchemeResources(R.color.mobile_tab_active)
        swipeChat.setOnRefreshListener {
            carregarMensagens(silently = false, aoTerminar = { swipeChat.isRefreshing = false })
        }

        // Contador de caracteres + Enter para enviar
        etMensagem.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val len = s?.length ?: 0
                tvCharCount.visibility = if (len > 0) View.VISIBLE else View.GONE
                tvCharCount.text = "$len/1000"
                tvCharCount.setTextColor(
                    if (len >= 900) 0xFFFF6B6B.toInt() else getColor(R.color.mobile_text_secondary)
                )
            }
        })
        etMensagem.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEND || isEnter) {
                enviar(); true
            } else false
        }

        findViewById<MaterialButton>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnEnviar).setOnClickListener { enviar() }
        findViewById<MaterialButton>(R.id.btnAnexar).setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Enviar arquivo")
                .setItems(arrayOf("📷 Câmera", "📁 Arquivo / Galeria")) { _, which ->
                    if (which == 0) abrirCamera() else pickFile.launch("*/*")
                }
                .show()
        }

        // Abre na aba correta se vier de notificação de aviso
        val openTab = intent.getStringExtra("open_tab")
        if (openTab == "avisos") {
            mostrarAba("avisos")
        } else {
            mostrarAba("chat")
        }

        carregarMensagens()
        carregarAvisos()
    }

    override fun onResume() {
        super.onResume()
        if (isInChatTab) pollingHandler.postDelayed(pollingRunnable, 5_000)
    }

    override fun onPause() {
        super.onPause()
        pollingHandler.removeCallbacks(pollingRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingHandler.removeCallbacks(pollingRunnable)
    }

    private fun mostrarAba(aba: String) {
        isInChatTab = aba == "chat"
        val isChat = isInChatTab
        panelChat.visibility = if (isChat) View.VISIBLE else View.GONE
        panelAvisos.visibility = if (isChat) View.GONE else View.VISIBLE
        tvStatusRh.visibility = if (isChat) View.VISIBLE else View.GONE

        if (isChat) {
            pollingHandler.removeCallbacks(pollingRunnable)
            pollingHandler.postDelayed(pollingRunnable, 5_000)
        } else {
            pollingHandler.removeCallbacks(pollingRunnable)
        }

        val colorAtivo = getColor(R.color.mobile_tab_active)
        val colorInativo = getColor(R.color.mobile_surface_soft)
        val colorTextoAtivo = getColor(R.color.white)
        val colorTextoInativo = getColor(R.color.mobile_text_primary)

        btnTabChat.backgroundTintList = android.content.res.ColorStateList.valueOf(if (isChat) colorAtivo else colorInativo)
        btnTabChat.setTextColor(if (isChat) colorTextoAtivo else colorTextoInativo)
        btnTabAvisos.backgroundTintList = android.content.res.ColorStateList.valueOf(if (!isChat) colorAtivo else colorInativo)
        btnTabAvisos.setTextColor(if (!isChat) colorTextoAtivo else colorTextoInativo)
    }

    private fun abrirCamera() {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imgFile = File(getExternalFilesDir(null), "foto_chat_$ts.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", imgFile)
        cameraPhotoUri = uri
        takePhoto.launch(uri)
    }

    private fun carregarMensagens(silently: Boolean = false, aoTerminar: (() -> Unit)? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            val msgs = try { api.getMensagens() } catch (_: Exception) { emptyList() }
            withContext(Dispatchers.Main) {
                val llm = rvMensagens.layoutManager as? LinearLayoutManager
                val atBottom = llm != null &&
                    llm.findLastCompletelyVisibleItemPosition() >= adapter.itemCount - 2
                adapter.replaceAll(msgs)
                if (msgs.isNotEmpty() && (!silently || atBottom)) {
                    // Se o RH respondeu depois da última mensagem do funcionário,
                    // rola para a primeira mensagem não respondida do RH
                    val ultimaRhIdx = msgs.indexOfLast { it.de_rh == true }
                    val ultimaFuncIdx = msgs.indexOfLast { it.de_rh != true }
                    val scrollTarget = if (!silently && ultimaRhIdx >= 0 && ultimaRhIdx > ultimaFuncIdx) {
                        ultimaRhIdx
                    } else {
                        adapter.itemCount - 1
                    }
                    rvMensagens.scrollToPosition(scrollTarget)
                }
                if (!silently) tvBadge.visibility = View.GONE
                atualizarStatusRh(msgs)
                aoTerminar?.invoke()
            }
        }
    }

    private fun atualizarStatusRh(msgs: List<MensagemItem>) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val ultimaRh = msgs.lastOrNull { it.de_rh == true }
        val ultimaFunc = msgs.lastOrNull { it.de_rh != true }
        when {
            ultimaRh == null -> {
                tvStatusRh.text = "Nenhuma resposta do RH ainda."
                tvStatusRh.visibility = View.VISIBLE
            }
            ultimaFunc != null && (ultimaFunc.id ?: 0) > (ultimaRh.id ?: 0) -> {
                tvStatusRh.text = "⏳ Aguardando resposta do RH…"
                tvStatusRh.visibility = View.VISIBLE
            }
            else -> {
                val hora = ultimaRh.enviado_fmt?.takeLast(5) ?: sdf.format(Date())
                tvStatusRh.text = "✅ RH respondeu às $hora"
                tvStatusRh.visibility = View.VISIBLE
            }
        }
    }

    private fun carregarAvisos() {
        lifecycleScope.launch(Dispatchers.IO) {
            val avisos = try { api.getComunicados() } catch (_: Exception) { emptyList() }
            withContext(Dispatchers.Main) {
                if (avisos.isEmpty()) {
                    rvAvisos.visibility = View.GONE
                    tvAvisosVazio.visibility = View.VISIBLE
                } else {
                    rvAvisos.visibility = View.VISIBLE
                    tvAvisosVazio.visibility = View.GONE
                    avisoAdapter.replaceAll(avisos)
                }
                // Badge na aba "Avisos" com quantidade de comunicados não lidos
                val naoLidos = avisos.count { it.lido != true }
                if (naoLidos > 0) {
                    btnTabAvisos.text = "📢 Avisos ($naoLidos)"
                } else {
                    btnTabAvisos.text = "📢 Avisos"
                }
            }
        }
    }

    private fun enviar() {
        val texto = etMensagem.text.toString().trim()
        if (texto.isBlank()) {
            Toast.makeText(this, "Digite uma mensagem.", Toast.LENGTH_SHORT).show()
            return
        }
        etMensagem.isEnabled = false
        progressEnvio.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val nova = try { api.enviarMensagem(texto) } catch (_: Exception) { null }
            withContext(Dispatchers.Main) {
                etMensagem.isEnabled = true
                progressEnvio.visibility = View.GONE
                if (nova != null) {
                    etMensagem.setText("")
                    adapter.addMensagem(nova)
                    rvMensagens.scrollToPosition(adapter.itemCount - 1)
                } else {
                    retryQueue.enqueueMensagem(texto)
                    Toast.makeText(this@MensagensActivity, "Sem conexão. Mensagem colocada na fila.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun enviarArquivo(uri: Uri) {
        val sizeLimit = 20 * 1024 * 1024L
        val fileSize = contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        if (fileSize != null && fileSize > sizeLimit) {
            Toast.makeText(this, "Arquivo muito grande. Limite: 20 MB.", Toast.LENGTH_LONG).show()
            return
        }

        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val fileName = obterNomeArquivo(uri)
        Toast.makeText(this, "Enviando $fileName...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bytes = contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw IllegalStateException("Não foi possível ler o arquivo")
                val nova = api.enviarArquivoMensagem(bytes, mimeType, fileName)
                withContext(Dispatchers.Main) {
                    if (nova != null) {
                        adapter.addMensagem(nova)
                        rvMensagens.scrollToPosition(adapter.itemCount - 1)
                    } else {
                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        retryQueue.enqueueMensagem("[arquivo pendente] $fileName")
                        TelemetryLogger.logHandled(this@MensagensActivity, "mensagem_arquivo_fila", IllegalStateException("Arquivo enfileirado: ${b64.length}"))
                        Toast.makeText(this@MensagensActivity, "Erro ao enviar arquivo.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    TelemetryLogger.logHandled(this@MensagensActivity, "mensagem_enviar_arquivo", e)
                    Toast.makeText(this@MensagensActivity, e.message ?: "Erro ao enviar arquivo.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun abrirArquivoMensagem(item: MensagemItem) {
        val arquivoUrl = item.arquivo_url ?: return
        Toast.makeText(this, "Baixando ${item.arquivo_nome ?: "arquivo"}...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bytes = api.downloadMensagemArquivo(arquivoUrl)
                val fileName = item.arquivo_nome ?: "arquivo_${item.id}"
                val file = File(cacheDir, fileName)
                file.writeBytes(bytes)
                withContext(Dispatchers.Main) { abrirArquivoLocal(file) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MensagensActivity, e.message ?: "Erro ao baixar arquivo.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun abrirArquivoLocal(file: File) {
        val ext = file.extension.lowercase()

        if (ext == "pdf") {
            val intent = Intent(this, PdfPreviewActivity::class.java).apply {
                putExtra(PdfPreviewActivity.EXTRA_FILE_PATH, file.absolutePath)
                putExtra(PdfPreviewActivity.EXTRA_TITLE, file.name)
            }
            startActivity(intent)
            return
        }

        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Abrir arquivo"))
        } catch (_: Exception) {
            Toast.makeText(this, "Nenhum app disponível para abrir este arquivo", Toast.LENGTH_LONG).show()
        }
    }

    private fun obterNomeArquivo(uri: Uri): String {
        var nome = "arquivo"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) nome = cursor.getString(idx) ?: nome
        }
        return nome
    }
}
