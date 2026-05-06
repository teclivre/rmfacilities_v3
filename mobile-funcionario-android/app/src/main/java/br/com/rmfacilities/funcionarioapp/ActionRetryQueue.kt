package br.com.rmfacilities.funcionarioapp

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class PendingAction(
    val id: String,
    val type: String,
    val payload: Map<String, String>,
    val createdAt: Long
)

class ActionRetryQueue(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("rm_funcionario_retry_queue", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "pending_actions"

    private fun load(): MutableList<PendingAction> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<MutableList<PendingAction>>() {}.type
            gson.fromJson(raw, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun save(items: List<PendingAction>) {
        prefs.edit().putString(key, gson.toJson(items)).apply()
    }

    fun pendingCount(): Int = load().size

    fun enqueueMensagem(texto: String) {
        val items = load()
        items.add(
            PendingAction(
                id = UUID.randomUUID().toString(),
                type = "mensagem",
                payload = mapOf("texto" to texto),
                createdAt = System.currentTimeMillis()
            )
        )
        save(items)
    }

    fun enqueuePonto(lat: Double, lon: Double, precisao: Float?) {
        val items = load()
        val payload = mutableMapOf(
            "lat" to lat.toString(),
            "lon" to lon.toString()
        )
        if (precisao != null) payload["precisao"] = precisao.toString()
        items.add(
            PendingAction(
                id = UUID.randomUUID().toString(),
                type = "ponto",
                payload = payload,
                createdAt = System.currentTimeMillis()
            )
        )
        save(items)
    }

    fun enqueueFoto(base64Data: String, mimeType: String) {
        val items = load()
        items.add(
            PendingAction(
                id = UUID.randomUUID().toString(),
                type = "foto",
                payload = mapOf("data" to base64Data, "mime" to mimeType),
                createdAt = System.currentTimeMillis()
            )
        )
        save(items)
    }

    fun enqueueDocumentoDownload(item: DocumentoItem) {
        val path = item.app_download_url?.trim().orEmpty()
        if (path.isBlank() || path.startsWith("offline://")) return
        val items = load()
        val alreadyQueued = items.any {
            it.type == "documento_download" && it.payload["download_path"] == path
        }
        if (alreadyQueued) return
        items.add(
            PendingAction(
                id = UUID.randomUUID().toString(),
                type = "documento_download",
                payload = mapOf(
                    "documento_id" to item.id.toString(),
                    "download_path" to path,
                    "nome" to (item.nome_arquivo ?: "Documento ${item.id}"),
                    "categoria" to (item.categoria_label ?: item.categoria.orEmpty()),
                    "ano" to item.ano.orEmpty(),
                    "competencia" to item.competencia.orEmpty(),
                    "criado_fmt" to item.criado_fmt.orEmpty()
                ),
                createdAt = System.currentTimeMillis()
            )
        )
        save(items)
    }

    data class ProcessResult(val enviados: Int, val pendentes: Int)

    fun process(api: ApiClient): ProcessResult {
        val items = load()
        if (items.isEmpty()) return ProcessResult(0, 0)
        val offlineStore = OfflineDocsStore(appContext)

        val remaining = mutableListOf<PendingAction>()
        var sent = 0

        for (action in items) {
            val ok = try {
                when (action.type) {
                    "mensagem" -> {
                        val texto = action.payload["texto"].orEmpty()
                        texto.isNotBlank() && api.enviarMensagem(texto) != null
                    }
                    "ponto" -> {
                        val lat = action.payload["lat"]?.toDoubleOrNull()
                        val lon = action.payload["lon"]?.toDoubleOrNull()
                        val precisao = action.payload["precisao"]?.toFloatOrNull()
                        if (lat == null || lon == null) false else api.marcarPonto(lat = lat, lon = lon, precisao = precisao).ok
                    }
                    "foto" -> {
                        val b64 = action.payload["data"].orEmpty()
                        val mime = action.payload["mime"].orEmpty().ifBlank { "image/jpeg" }
                        if (b64.isBlank()) false
                        else {
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            api.uploadFoto(bytes, mime).ok
                        }
                    }
                    "documento_download" -> {
                        val path = action.payload["download_path"].orEmpty()
                        val docId = action.payload["documento_id"]?.toIntOrNull()
                        if (path.isBlank() || docId == null) {
                            false
                        } else {
                            val bytes = api.downloadFile(path)
                            val item = DocumentoItem(
                                id = docId,
                                nome_arquivo = action.payload["nome"],
                                categoria = action.payload["categoria"],
                                categoria_label = action.payload["categoria"],
                                ano = action.payload["ano"],
                                competencia = action.payload["competencia"],
                                criado_fmt = action.payload["criado_fmt"],
                                app_download_url = path,
                                can_assinar = false
                            )
                            offlineStore.saveDownloaded(item, bytes)
                            true
                        }
                    }
                    else -> false
                }
            } catch (_: Exception) {
                false
            }

            if (ok) sent++ else remaining.add(action)
        }

        save(remaining)
        return ProcessResult(sent, remaining.size)
    }
}
