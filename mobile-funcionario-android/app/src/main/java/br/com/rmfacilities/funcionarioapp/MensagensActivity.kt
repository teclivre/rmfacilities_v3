package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
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
    private lateinit var rvMensagens: RecyclerView
    private lateinit var etMensagem: EditText
    private lateinit var tvBadge: TextView
    private lateinit var retryQueue: ActionRetryQueue

    private var cameraPhotoUri: Uri? = null

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
        etMensagem = findViewById(R.id.etMensagem)
        tvBadge = findViewById(R.id.tvBadge)

        adapter = MensagemAdapter(onAbrirArquivo = { item -> abrirArquivoMensagem(item) })
        rvMensagens.layoutManager = LinearLayoutManager(this).also { it.stackFromEnd = true }
        rvMensagens.adapter = adapter

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

        carregarMensagens()
    }

    private fun abrirCamera() {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imgFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "foto_chat_$ts.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", imgFile)
        cameraPhotoUri = uri
        takePhoto.launch(uri)
    }

    private fun carregarMensagens() {
        CoroutineScope(Dispatchers.IO).launch {
            val msgs = try { api.getMensagens() } catch (_: Exception) { emptyList() }
            withContext(Dispatchers.Main) {
                adapter.replaceAll(msgs)
                if (msgs.isNotEmpty()) rvMensagens.scrollToPosition(msgs.size - 1)
                tvBadge.visibility = android.view.View.GONE
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
        CoroutineScope(Dispatchers.IO).launch {
            val nova = try { api.enviarMensagem(texto) } catch (_: Exception) { null }
            withContext(Dispatchers.Main) {
                etMensagem.isEnabled = true
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
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val fileName = obterNomeArquivo(uri)
        Toast.makeText(this, "Enviando $fileName...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
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
        CoroutineScope(Dispatchers.IO).launch {
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
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val ext = file.extension.lowercase()
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
