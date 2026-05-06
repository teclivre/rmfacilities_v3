package br.com.rmfacilities.funcionarioapp

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfPreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "pdf_file_path"
        const val EXTRA_FILE_PATHS = "pdf_file_paths"
        const val EXTRA_TITLE = "pdf_title"
        const val EXTRA_TITLES = "pdf_titles"
    }

    private lateinit var rvPages: RecyclerView
    private lateinit var tvLoading: TextView
    private var pdfRenderer: PdfRenderer? = null
    private var currentPdfFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bloqueia captura e gravação de tela nesta tela sensível.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_pdf_preview)

        rvPages = findViewById(R.id.rvPages)
        tvLoading = findViewById(R.id.tvLoading)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "PDF"
        supportActionBar?.title = title

        findViewById<MaterialButton>(R.id.btnFechar).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvTitle).text = title

        val filePaths = intent.getStringArrayListExtra(EXTRA_FILE_PATHS)
        val titleList = intent.getStringArrayListExtra(EXTRA_TITLES)
        val filesToOpen = mutableListOf<Pair<File, String>>()
        if (!filePaths.isNullOrEmpty()) {
            filePaths.forEachIndexed { index, p ->
                val f = File(p)
                if (f.exists()) {
                    filesToOpen.add(f to (titleList?.getOrNull(index) ?: f.name))
                }
            }
        } else {
            val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
            if (filePath != null) {
                val f = File(filePath)
                if (f.exists()) {
                    filesToOpen.add(f to (intent.getStringExtra(EXTRA_TITLE) ?: f.name))
                }
            }
        }
        if (filesToOpen.isEmpty()) {
            Toast.makeText(this, "Arquivo não encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        currentPdfFile = filesToOpen.first().first

        findViewById<MaterialButton>(R.id.btnBaixarPdf).setOnClickListener {
            val f = currentPdfFile
            if (f == null || !f.exists()) {
                Toast.makeText(this, "Arquivo não encontrado", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            salvarNoDownloads(f, title)
        }

        renderPdfs(filesToOpen)
    }

    private fun renderPdfs(files: List<Pair<File, String>>) {
        tvLoading.visibility = View.VISIBLE
        rvPages.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val items = mutableListOf<PreviewItem>()
                val displayWidth = resources.displayMetrics.widthPixels
                val targetWidth = minOf(displayWidth, 1280)

                files.forEach { (file, title) ->
                    items.add(PreviewItem.Header(title))
                    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(fd)
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val scale = targetWidth.toFloat() / page.width
                        val bmpHeight = (page.height * scale).toInt()
                        val bmp = Bitmap.createBitmap(targetWidth, bmpHeight, Bitmap.Config.RGB_565)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        items.add(PreviewItem.Page(bmp))
                    }
                    renderer.close()
                    fd.close()
                }
                pdfRenderer = null

                withContext(Dispatchers.Main) {
                    tvLoading.visibility = View.GONE
                    rvPages.visibility = View.VISIBLE
                    rvPages.layoutManager = LinearLayoutManager(this@PdfPreviewActivity)
                    rvPages.setHasFixedSize(true)
                    (rvPages.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
                    rvPages.adapter = PdfPageAdapter(items)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvLoading.text = "Erro ao carregar PDF: ${e.message}"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfRenderer?.close()
    }

    private fun salvarNoDownloads(file: File, title: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rawName = title.ifBlank { file.name }
                val finalName = if (rawName.lowercase().endsWith(".pdf")) rawName else "$rawName.pdf"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, finalName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                }

                val resolver = contentResolver
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val uri = resolver.insert(collection, values)
                    ?: throw IllegalStateException("Não foi possível criar o arquivo no Downloads")

                resolver.openOutputStream(uri).use { out ->
                    if (out == null) throw IllegalStateException("Falha ao abrir destino no Downloads")
                    file.inputStream().use { input -> input.copyTo(out) }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PdfPreviewActivity, "Arquivo salvo em Downloads", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PdfPreviewActivity, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

sealed class PreviewItem {
    data class Header(val title: String) : PreviewItem()
    data class Page(val bitmap: Bitmap) : PreviewItem()
}

class PdfPageAdapter(private val items: List<PreviewItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val VT_HEADER = 1
        private const val VT_PAGE = 2
    }

    inner class PH(v: View) : RecyclerView.ViewHolder(v) {
        val ivPage: ImageView = v.findViewById(R.id.ivPage)
    }

    inner class HH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(android.R.id.text1)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is PreviewItem.Header -> VT_HEADER
            is PreviewItem.Page -> VT_PAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VT_HEADER) {
            val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            HH(v)
        } else {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
            PH(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is PreviewItem.Header -> {
                val h = holder as HH
                h.tv.text = "Documento: ${item.title}"
                h.tv.setTextColor(android.graphics.Color.parseColor("#9EC5F7"))
                h.tv.setBackgroundColor(android.graphics.Color.parseColor("#1A2235"))
                h.tv.setPadding(20, 18, 20, 14)
                h.tv.textSize = 14f
            }
            is PreviewItem.Page -> {
                val p = holder as PH
                p.ivPage.setImageBitmap(item.bitmap)
            }
        }
    }

    override fun getItemCount() = items.size
}
