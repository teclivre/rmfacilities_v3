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

    data class ProcessResult(val enviados: Int, val pendentes: Int)

    fun process(api: ApiClient): ProcessResult {
        val items = load()
        if (items.isEmpty()) return ProcessResult(0, 0)

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
