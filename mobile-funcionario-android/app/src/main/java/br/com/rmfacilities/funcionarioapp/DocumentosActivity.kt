package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DocumentosActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var adapter: DocumentoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documentos)

        session = SessionManager(this)
        api = ApiClient(session)

        swipe = findViewById(R.id.swipeDocs)
        val rv = findViewById<RecyclerView>(R.id.rvDocs)

        adapter = DocumentoAdapter(mutableListOf()) { item ->
            baixarDocumento(item)
        }
        rv.adapter = adapter

        swipe.setOnRefreshListener { carregar() }
        swipe.isRefreshing = true
        carregar()
    }

    private fun carregar() {
        CoroutineScope(Dispatchers.IO).launch {
            val docs = try {
                api.documentos()
            } catch (e: Exception) {
                DocsResponse(ok = false, erro = e.message)
            }
            withContext(Dispatchers.Main) {
                swipe.isRefreshing = false
                if (docs.ok) {
                    adapter.replaceAll(docs.itens)
                } else {
                    Toast.makeText(this@DocumentosActivity, docs.erro ?: "Falha ao carregar", Toast.LENGTH_LONG).show()
                }
            }
        }
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
}
