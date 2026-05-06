package br.com.rmfacilities.funcionarioapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class OfflineDocEntry(
    val id: Int,
    val nome: String,
    val path: String,
    val salvoEm: Long,
    val categoria: String? = null,
    val ano: String? = null
)

class OfflineDocsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("rm_funcionario_offline_docs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "docs"

    private fun baseDir(): File {
        val dir = File(context.filesDir, "offline_docs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun load(): MutableList<OfflineDocEntry> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<MutableList<OfflineDocEntry>>() {}.type
            gson.fromJson(raw, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun save(list: List<OfflineDocEntry>) {
        prefs.edit().putString(key, gson.toJson(list)).apply()
    }

    fun list(): List<OfflineDocEntry> = load().sortedByDescending { it.salvoEm }

    fun saveDownloaded(item: DocumentoItem, bytes: ByteArray): File {
        val dir = baseDir()
        val safeName = (item.nome_arquivo ?: "documento_${item.id}.pdf")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(dir, "${item.id}_${System.currentTimeMillis()}_$safeName")
        file.writeBytes(bytes)

        val all = load().filterNot { it.id == item.id }.toMutableList()
        all.add(
            OfflineDocEntry(
                id = item.id,
                nome = item.nome_arquivo ?: "Documento ${item.id}",
                path = file.absolutePath,
                salvoEm = System.currentTimeMillis(),
                categoria = item.categoria_label ?: item.categoria,
                ano = item.ano
            )
        )
        save(all)
        return file
    }

    fun findById(id: Int): OfflineDocEntry? = load().firstOrNull { it.id == id }

    fun clearAll() {
        val all = load()
        all.forEach { entry ->
            try { File(entry.path).delete() } catch (_: Exception) {}
        }
        save(emptyList())
    }

    fun toDocumentoItems(): List<DocumentoItem> {
        return list().map {
            DocumentoItem(
                id = it.id,
                categoria = it.categoria,
                categoria_label = it.categoria,
                ano = it.ano,
                nome_arquivo = it.nome,
                criado_fmt = "Offline",
                app_download_url = "offline://${it.id}",
                can_assinar = false
            )
        }
    }
}
