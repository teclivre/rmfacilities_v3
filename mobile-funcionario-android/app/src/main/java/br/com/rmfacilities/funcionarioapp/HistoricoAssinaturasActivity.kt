package br.com.rmfacilities.funcionarioapp

import android.content.ContentValues
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoricoAssinaturasActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var adapter: HistoricoAssinaturasAdapter
    private var ultimoHistorico: List<AssinaturaHistoricoItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historico_assinaturas)

        session = SessionManager(this)
        api = ApiClient(session)

        swipe = findViewById(R.id.swipeHistorico)
        val rv = findViewById<RecyclerView>(R.id.rvHistorico)

        findViewById<android.widget.TextView>(R.id.btnVoltarHistorico).setOnClickListener { finish() }

        adapter = HistoricoAssinaturasAdapter { item ->
            abrirComprovante(item)
        }
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(this)

        findViewById<MaterialButton>(R.id.btnExportarHistorico).setOnClickListener {
            exportarHistoricoPdf()
        }

        swipe.setOnRefreshListener { carregar() }
        swipe.isRefreshing = true
        carregar()
    }

    private fun carregar() {
        CoroutineScope(Dispatchers.IO).launch {
            val resp = try { api.historicoAssinaturas() }
                       catch (e: Exception) { HistoricoAssinaturasResponse(ok = false, erro = e.message) }
            withContext(Dispatchers.Main) {
                swipe.isRefreshing = false
                if (resp.ok) {
                    ultimoHistorico = resp.itens
                    adapter.replaceAll(resp.itens)
                } else {
                    Toast.makeText(this@HistoricoAssinaturasActivity, resp.erro ?: "Falha ao carregar histórico", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun abrirComprovante(item: AssinaturaHistoricoItem) {
        val linhas = listOf(
            "Documento: ${item.nome_arquivo ?: "Documento"}",
            "Categoria: ${item.categoria_label ?: item.categoria ?: "-"}",
            "Competência: ${item.competencia ?: "-"}",
            "Assinado em: ${item.ass_em_fmt ?: "-"}",
            "IP: ${item.ass_ip_mask ?: "-"}",
            "Código de validação: ${item.ass_codigo ?: "-"}"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Comprovante de assinatura")
            .setMessage(linhas.joinToString("\n"))
            .setNegativeButton("Fechar", null)
            .setPositiveButton("Exportar PDF") { _, _ ->
                exportarHistoricoPdf(listOf(item))
            }
            .show()
    }

    private fun exportarHistoricoPdf(lista: List<AssinaturaHistoricoItem> = ultimoHistorico) {
        if (lista.isEmpty()) {
            Toast.makeText(this, "Nada para exportar.", Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val doc = PdfDocument()
                val pageWidth = 595
                val pageHeight = 842
                val margin = 36
                val lineHeight = 18
                val textPaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 12f
                    isAntiAlias = true
                }
                val titlePaint = Paint(textPaint).apply { textSize = 15f; isFakeBoldText = true }

                var pageNumber = 1
                var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                var canvas = page.canvas
                var y = margin + 10

                fun newPage() {
                    doc.finishPage(page)
                    pageNumber += 1
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    y = margin + 10
                }

                canvas.drawText("Histórico de assinaturas", margin.toFloat(), y.toFloat(), titlePaint)
                y += 26
                canvas.drawText("Gerado em: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}", margin.toFloat(), y.toFloat(), textPaint)
                y += 22

                lista.forEachIndexed { idx, item ->
                    val bloco = listOf(
                        "${idx + 1}. ${item.nome_arquivo ?: "Documento"}",
                        "Categoria: ${item.categoria_label ?: item.categoria ?: "-"}",
                        "Competência: ${item.competencia ?: "-"}",
                        "Assinado em: ${item.ass_em_fmt ?: "-"}",
                        "IP: ${item.ass_ip_mask ?: "-"}",
                        "Código: ${item.ass_codigo ?: "-"}",
                        ""
                    )
                    for (linha in bloco) {
                        if (y > pageHeight - margin) {
                            newPage()
                        }
                        canvas.drawText(linha, margin.toFloat(), y.toFloat(), textPaint)
                        y += lineHeight
                    }
                }
                doc.finishPage(page)

                val fileName = "historico_assinaturas_${System.currentTimeMillis()}.pdf"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Falha ao criar arquivo")
                resolver.openOutputStream(uri).use { out ->
                    if (out == null) throw IllegalStateException("Falha ao abrir saída")
                    doc.writeTo(out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(uri, ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }, null, null)
                }
                doc.close()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HistoricoAssinaturasActivity, "Comprovante exportado para Downloads.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HistoricoAssinaturasActivity, "Falha ao exportar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
