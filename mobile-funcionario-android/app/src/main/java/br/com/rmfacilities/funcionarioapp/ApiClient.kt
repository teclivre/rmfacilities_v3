package br.com.rmfacilities.funcionarioapp

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    fun iniciarOtp(cpf: String): OtpStartResponse {
        val payload = gson.toJson(mapOf("cpf" to cpf))
        val req = Request.Builder()
            .url(url("/api/app/funcionario/auth/iniciar"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return OtpStartResponse(ok = false, erro = if (resp.code == 404) "Funcionalidade ainda não disponível neste servidor." else "Erro do servidor (${resp.code}).")
            }
            return try {
                gson.fromJson(raw, OtpStartResponse::class.java)
            } catch (_: Exception) {
                OtpStartResponse(ok = false, erro = "Resposta inesperada do servidor.")
            }
        }
    }

    fun confirmarOtp(cpf: String, codigo: String): LoginResponse {
        val payload = gson.toJson(mapOf("cpf" to cpf, "codigo" to codigo))
        val req = Request.Builder()
            .url(url("/api/app/funcionario/auth/confirmar"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return LoginResponse(ok = false, erro = if (resp.code == 404) "Funcionalidade ainda não disponível neste servidor." else "Erro do servidor (${resp.code}).")
            }
            return try {
                gson.fromJson(raw, LoginResponse::class.java)
            } catch (_: Exception) {
                LoginResponse(ok = false, erro = "Resposta inesperada do servidor.")
            }
        }
    }

    fun atualizarContato(email: String, telefone: String): ContatoUpdateResponse {
        val payload = gson.toJson(mapOf("email" to email, "telefone" to telefone))
        val req = Request.Builder()
            .url(url("/api/app/funcionario/me/contato"))
            .put(payload.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .addHeader("Content-Type", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try {
                gson.fromJson(raw, ContatoUpdateResponse::class.java)
            } catch (_: Exception) {
                ContatoUpdateResponse(ok = false, erro = "Falha ao atualizar contato.")
            }
        }
    }

    fun solicitarAlteracao(campos: Map<String,String>, observacao: String): SolicitacaoResponse {
        val payload = gson.toJson(mapOf("campos" to campos, "observacao" to observacao))
        val req = Request.Builder()
            .url(url("/api/app/funcionario/me/solicitacoes-alteracao"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .addHeader("Content-Type", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try {
                gson.fromJson(raw, SolicitacaoResponse::class.java)
            } catch (_: Exception) {
                SolicitacaoResponse(ok = false, erro = "Falha ao registrar solicitação.")
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

    fun getMensagens(): List<MensagemItem> {
        val req = Request.Builder()
            .url(url("/api/app/funcionario/mensagens"))
            .get()
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try {
                val type = object : TypeToken<List<MensagemItem>>() {}.type
                gson.fromJson(raw, type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
        }
    }

    fun enviarMensagem(conteudo: String): MensagemItem? {
        val payload = gson.toJson(mapOf("conteudo" to conteudo))
        val req = Request.Builder()
            .url(url("/api/app/funcionario/mensagens"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .addHeader("Content-Type", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try { gson.fromJson(raw, MensagemItem::class.java) } catch (_: Exception) { null }
        }
    }

    fun getNaoLidas(): Int {
        val req = Request.Builder()
            .url(url("/api/app/funcionario/mensagens/nao-lidas"))
            .get()
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try { gson.fromJson(raw, NaoLidasResponse::class.java).nao_lidas } catch (_: Exception) { 0 }
        }
    }
}
