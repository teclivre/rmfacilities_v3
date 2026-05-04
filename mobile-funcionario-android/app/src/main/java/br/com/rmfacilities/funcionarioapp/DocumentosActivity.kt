package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.view.View
import android.widget.HorizontalScrollView
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class DocumentosActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var adapter: DocumentoAdapter
    private lateinit var rv: RecyclerView

    private var filtroQ = ""
    private var filtroCategoria = ""
    private var filtroAno = ""
    private lateinit var shimmerDocs: ShimmerFrameLayout
    private lateinit var scrollChips: HorizontalScrollView
    private lateinit var chipGroupAnos: ChipGroup
    private var primeiroLoad = true
    private var anosDisponiveis: List<String> = emptyList()

    private val debounceHandler = Handler(Looper.getMainLooper())
    private val debounceRunnable = Runnable { carregarComFiltros() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documentos)

        session = SessionManager(this)
        api = ApiClient(session)

        swipe = findViewById(R.id.swipeDocs)
        rv = findViewById(R.id.rvDocs)
        shimmerDocs = findViewById(R.id.shimmerDocs)
        scrollChips = findViewById(R.id.scrollChips)
        chipGroupAnos = findViewById(R.id.chipGroupAnos)

        // Botão voltar
        findViewById<android.widget.TextView>(R.id.btnVoltar).setOnClickListener { finish() }

        // Botão histórico de assinaturas
        findViewById<android.widget.ImageButton>(R.id.btnHistoricoAss).setOnClickListener {
            startActivity(Intent(this, HistoricoAssinaturasActivity::class.java))
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

        adapter = DocumentoAdapter(
            onBaixar = { item -> baixarDocumento(item) },
            onAssinar = { item -> confirmarAssinatura(item) }
        )
        rv.adapter = adapter
        if (rv.layoutManager == null) {
            rv.layoutManager = LinearLayoutManager(this)
        }

        swipe.setOnRefreshListener { carregarComFiltros() }
        swipe.isRefreshing = false
        shimmerDocs.startShimmer()
        carregar()
    }

    override fun onResume() {
        super.onResume()
        // Handle deep link from push notification
        val arquivoId = intent.getIntExtra(FcmService.EXTRA_ARQUIVO_ID, -1)
        if (arquivoId > 0) {
            intent.removeExtra(FcmService.EXTRA_ARQUIVO_ID)
            carregarEScrollar(arquivoId)
        }
    }

    private fun carregar() {
        carregarComFiltros()
    }

    private fun carregarComFiltros(scrollToArquivoId: Int = -1) {
        CoroutineScope(Dispatchers.IO).launch {
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
                    adapter.replaceAll(pendentes.itens, docs.itens)
                    atualizarChips((pendentes.itens ?: emptyList()) + (docs.itens ?: emptyList()))
                    if (scrollToArquivoId > 0) {
                        scrollToArquivo(scrollToArquivoId)
                    }
                } else {
                    Toast.makeText(this@DocumentosActivity, docs.erro ?: "Falha ao carregar", Toast.LENGTH_LONG).show()
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
            .setMessage("${item.nome_arquivo ?: "Documento"}\n\n$detalhes")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("✍ Confirmar assinatura") { _, _ ->
                assinarDocumento(item)
            }
            .show()
    }

    private fun baixarDocumento(item: DocumentoItem) {
        val path = item.app_download_url
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "Link de download indisponível", Toast.LENGTH_SHORT).show()
            return
        }

        swipe.isRefreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bytes = api.downloadFile(path)
                val fileName = item.nome_arquivo ?: "documento_${item.id}.pdf"
                val file = File(cacheDir, fileName)
                file.writeBytes(bytes)

                withContext(Dispatchers.Main) {
                    swipe.isRefreshing = false
                    abrirArquivo(file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipe.isRefreshing = false
                    Toast.makeText(this@DocumentosActivity, e.message ?: "Erro no download", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun assinarDocumento(item: DocumentoItem) {
        swipe.isRefreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            val resp = try {
                api.assinarDocumento(item.id)
            } catch (e: Exception) {
                ApiSimpleResponse(ok = false, erro = e.message)
            }
            withContext(Dispatchers.Main) {
                swipe.isRefreshing = false
                if (resp.ok) {
                    Toast.makeText(this@DocumentosActivity, "Documento assinado com sucesso.", Toast.LENGTH_SHORT).show()
                    carregarComFiltros()
                } else {
                    Toast.makeText(this@DocumentosActivity, resp.erro ?: "Falha ao assinar documento", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun abrirArquivo(file: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"

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

        if (anosNovos.isNotEmpty()) {
            anosDisponiveis = (anosNovos + anosDisponiveis)
                .toSortedSet(compareByDescending { it }).toList()
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
    }
}
