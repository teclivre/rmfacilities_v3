package br.com.rmfacilities.funcionarioapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfPreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "pdf_file_path"
        const val EXTRA_TITLE = "pdf_title"
    }

    private lateinit var rvPages: RecyclerView
    private lateinit var tvLoading: TextView
    private var pdfRenderer: PdfRenderer? = null
    private lateinit var watermarkText: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bloqueia captura e gravação de tela nesta tela sensível.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_pdf_preview)

        val session = SessionManager(this)
        val cpf = session.biometricCpf.filter { it.isDigit() }
        val cpfMask = if (cpf.length >= 4) "***.***.***-${cpf.takeLast(2)}" else "usuario"
        val ts = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        watermarkText = "RM Facilities - $cpfMask - $ts"

        rvPages = findViewById(R.id.rvPages)
        tvLoading = findViewById(R.id.tvLoading)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "PDF"
        supportActionBar?.title = title

        findViewById<MaterialButton>(R.id.btnFechar).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvTitle).text = title

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        if (filePath == null) {
            Toast.makeText(this, "Arquivo não encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        renderPdf(File(filePath))
    }

    private fun renderPdf(file: File) {
        tvLoading.visibility = View.VISIBLE
        rvPages.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val bitmaps = mutableListOf<Bitmap>()
                val displayWidth = resources.displayMetrics.widthPixels

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val scale = displayWidth.toFloat() / page.width
                    val bmpHeight = (page.height * scale).toInt()
                    val bmp = Bitmap.createBitmap(displayWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    applyWatermark(bmp)
                    page.close()
                    bitmaps.add(bmp)
                }
                renderer.close()
                fd.close()
                pdfRenderer = null

                withContext(Dispatchers.Main) {
                    tvLoading.visibility = View.GONE
                    rvPages.visibility = View.VISIBLE
                    rvPages.layoutManager = LinearLayoutManager(this@PdfPreviewActivity)
                    rvPages.adapter = PdfPageAdapter(bitmaps)
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

    private fun applyWatermark(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(52, 45, 55, 72)
            textSize = (bitmap.width * 0.042f).coerceAtLeast(22f)
            style = Paint.Style.FILL
        }

        canvas.save()
        canvas.rotate(-28f, bitmap.width / 2f, bitmap.height / 2f)
        val xStep = bitmap.width * 0.62f
        val yStep = bitmap.height * 0.28f
        var y = -bitmap.height.toFloat()
        while (y < bitmap.height * 2f) {
            var x = -bitmap.width.toFloat()
            while (x < bitmap.width * 2f) {
                canvas.drawText(watermarkText, x, y, paint)
                x += xStep
            }
            y += yStep
        }
        canvas.restore()
    }
}

class PdfPageAdapter(private val pages: List<Bitmap>) : RecyclerView.Adapter<PdfPageAdapter.PH>() {
    inner class PH(v: View) : RecyclerView.ViewHolder(v) {
        val ivPage: ImageView = v.findViewById(R.id.ivPage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
        return PH(v)
    }

    override fun onBindViewHolder(holder: PH, position: Int) {
        holder.ivPage.setImageBitmap(pages[position])
    }

    override fun getItemCount() = pages.size
}
