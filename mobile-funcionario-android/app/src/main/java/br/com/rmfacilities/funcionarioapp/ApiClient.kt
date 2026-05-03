package br.com.rmfacilities.funcionarioapp

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ApiClient(private val session: SessionManager) {
    private val gson = Gson()
    private val http = OkHttpClient.Builder().build()

    private fun url(path: String): String {
        val base = session.apiBaseUrl.trim().trimEnd('/')
        return if (path.startsWith("http")) path else "$base$path"
    }

    fun login(cpf: String, senha: String): LoginResponse {
        val payload = gson.toJson(mapOf("cpf" to cpf, "senha" to senha))
        val req = Request.Builder()
            .url(url("/api/app/funcionario/login"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try {
                gson.fromJson(raw, LoginResponse::class.java)
            } catch (_: Exception) {
                LoginResponse(ok = false, erro = "Falha ao interpretar resposta do servidor.")
            }
        }
    }

    fun me(): MeResponse {
        val req = Request.Builder()
            .url(url("/api/app/funcionario/me"))
            .get()
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try {
                gson.fromJson(raw, MeResponse::class.java)
            } catch (_: Exception) {
                MeResponse(ok = false, erro = "Falha ao interpretar perfil.")
            }
        }
    }

    fun documentos(): DocsResponse {
        val req = Request.Builder()
            .url(url("/api/app/funcionario/me/documentos?formato=lista&page=1&per_page=200"))
            .get()
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try {
                gson.fromJson(raw, DocsResponse::class.java)
            } catch (_: Exception) {
                DocsResponse(ok = false, erro = "Falha ao interpretar documentos.")
            }
        }
    }

    fun downloadFile(downloadPath: String): ByteArray {
        val req = Request.Builder()
            .url(url(downloadPath))
            .get()
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Falha no download: HTTP ${resp.code}")
            }
            return resp.body?.bytes() ?: throw IllegalStateException("Arquivo vazio")
        }
    }
}
