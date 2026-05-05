package br.com.rmfacilities.funcionarioapp

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ApiClient(private val session: SessionManager) {
    private val gson = Gson()
    private val http = OkHttpClient.Builder().build()

    private fun parseErro(raw: String, fallback: String): String {
        return try {
            val map = gson.fromJson(raw, Map::class.java)
            (map["erro"] as? String)?.takeIf { it.isNotBlank() } ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    private fun handleUnauthorized() {
        session.logout()
    }

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
                val fallback = if (resp.code == 404) "Funcionalidade ainda não disponível neste servidor." else "Erro do servidor (${resp.code})."
                return OtpStartResponse(ok = false, erro = parseErro(raw, fallback))
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
                val fallback = if (resp.code == 404) "Funcionalidade ainda não disponível neste servidor." else "Erro do servidor (${resp.code})."
                return LoginResponse(ok = false, erro = parseErro(raw, fallback))
            }
            return try {
                gson.fromJson(raw, LoginResponse::class.java)
            } catch (_: Exception) {
                LoginResponse(ok = false, erro = "Resposta inesperada do servidor.")
            }
        }
    }

    fun renovarSessao(refreshToken: String): LoginResponse {
        val payload = gson.toJson(mapOf("refresh_token" to refreshToken))
        val req = Request.Builder()
            .url(url("/api/app/funcionario/refresh"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return LoginResponse(ok = false, erro = parseErro(raw, "Não foi possível renovar a sessão."))
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
            if (resp.code == 401) { handleUnauthorized(); return MeResponse(ok = false, erro = "Sessão expirada.") }
            val raw = resp.body?.string().orEmpty()
            return try {
                gson.fromJson(raw, MeResponse::class.java)
            } catch (_: Exception) {
                MeResponse(ok = false, erro = "Falha ao interpretar perfil.")
            }
        }
    }

    fun documentos(q: String = "", categoria: String = "", ano: String = ""): DocsResponse {
        val params = buildList {
            add("formato=lista&page=1&per_page=200")
            if (q.isNotBlank()) add("q=${android.net.Uri.encode(q)}")
            if (categoria.isNotBlank()) add("categoria=${android.net.Uri.encode(categoria)}")
            if (ano.isNotBlank()) add("ano=${android.net.Uri.encode(ano)}")
        }.joinToString("&")
        val req = Request.Builder()
            .url(url("/api/app/funcionario/me/documentos?$params"))
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

    fun pendentesAssinatura(): DocsResponse {
        val req = Request.Builder()
            .url(url("/api/app/funcionario/pendentes-assinatura"))
            .get()
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try {
                gson.fromJson(raw, DocsResponse::class.java)
            } catch (_: Exception) {
                DocsResponse(ok = false, erro = "Falha ao carregar pendentes.")
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

    fun assinarDocumento(documentoId: Int): ApiSimpleResponse {
        val req = Request.Builder()
            .url(url("/api/app/funcionario/arquivos/$documentoId/assinar"))
            .post("{}".toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .addHeader("Content-Type", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try {
                gson.fromJson(raw, ApiSimpleResponse::class.java)
            } catch (_: Exception) {
                ApiSimpleResponse(ok = resp.isSuccessful, erro = if (resp.isSuccessful) null else parseErro(raw, "Falha ao assinar documento."))
            }
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

    fun enviarArquivoMensagem(bytes: ByteArray, mimeType: String, fileName: String, legenda: String = ""): MensagemItem? {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("arquivo", fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .apply { if (legenda.isNotBlank()) addFormDataPart("conteudo", legenda) }
            .build()
        val req = Request.Builder()
            .url(url("/api/app/funcionario/mensagens/arquivo"))
            .post(body)
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try { gson.fromJson(raw, MensagemItem::class.java) } catch (_: Exception) { null }
        }
    }

    fun downloadMensagemArquivo(arquivoUrl: String): ByteArray {
        val req = Request.Builder()
            .url(url(arquivoUrl))
            .get()
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Falha no download: HTTP ${resp.code}")
            return resp.body?.bytes() ?: throw IllegalStateException("Arquivo vazio")
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

    fun uploadFoto(bytes: ByteArray, mimeType: String): FotoUploadResponse {
        val ext = when {
            mimeType.contains("png") -> "foto.png"
            mimeType.contains("webp") -> "foto.webp"
            else -> "foto.jpg"
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("foto", ext, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val req = Request.Builder()
            .url(url("/api/app/funcionario/me/foto"))
            .post(body)
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try { gson.fromJson(raw, FotoUploadResponse::class.java) }
            catch (_: Exception) { FotoUploadResponse(ok = false, erro = "Falha ao enviar foto.") }
        }
    }

    fun registrarPushToken(token: String): ApiSimpleResponse {
        val payload = gson.toJson(mapOf("token" to token))
        val req = Request.Builder()
            .url(url("/api/app/funcionario/me/push-token"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .addHeader("Content-Type", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try {
                gson.fromJson(raw, ApiSimpleResponse::class.java)
            } catch (_: Exception) {
                ApiSimpleResponse(ok = resp.isSuccessful, erro = if (resp.isSuccessful) null else "Falha ao registrar notificações")
            }
        }
    }

    fun enviarLocalizacao(lat: Double, lon: Double, precisao: Float?): ApiSimpleResponse {
        val payload = gson.toJson(buildMap {
            put("lat", lat); put("lon", lon)
            if (precisao != null) put("precisao", precisao)
        })
        val req = Request.Builder()
            .url(url("/api/app/funcionario/me/localizacao"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .addHeader("Content-Type", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 401) { handleUnauthorized(); return ApiSimpleResponse(ok = false, erro = "Sessão expirada.") }
            val raw = resp.body?.string().orEmpty()
            return try { gson.fromJson(raw, ApiSimpleResponse::class.java) }
            catch (_: Exception) { ApiSimpleResponse(ok = resp.isSuccessful) }
        }
    }

    fun testarPushToken(): ApiSimpleResponse {
        val req = Request.Builder()
            .url(url("/api/app/funcionario/me/push-token/teste"))
            .post("{}".toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try {
                gson.fromJson(raw, ApiSimpleResponse::class.java)
            } catch (_: Exception) {
                ApiSimpleResponse(ok = resp.isSuccessful, erro = if (resp.isSuccessful) null else "Falha no teste de push")
            }
        }
    }

    fun getVersaoApp(): VersaoAppResponse {
        val req = Request.Builder()
            .url(url("/api/app/versao"))
            .get()
            .build()
        return try {
            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                gson.fromJson(raw, VersaoAppResponse::class.java)
            }
        } catch (_: Exception) { VersaoAppResponse(versao_minima = 0, versao_atual = 0) }
    }

    fun historicoAssinaturas(): HistoricoAssinaturasResponse {
        val req = Request.Builder()
            .url(url("/api/app/funcionario/historico-assinaturas"))
            .get()
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            return try {
                gson.fromJson(raw, HistoricoAssinaturasResponse::class.java)
            } catch (_: Exception) {
                HistoricoAssinaturasResponse(ok = false, erro = "Falha ao carregar histórico.")
            }
        }
    }

    fun getPontoDia(data: String = ""): PontoDiaResponse {
        val q = if (data.isNotBlank()) "?data=${android.net.Uri.encode(data)}" else ""
        val req = Request.Builder()
            .url(url("/api/app/funcionario/me/ponto/dia$q"))
            .get()
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 401) { handleUnauthorized(); return PontoDiaResponse(ok = false, erro = "Sessão expirada.") }
            val raw = resp.body?.string().orEmpty()
            return try { gson.fromJson(raw, PontoDiaResponse::class.java) }
            catch (_: Exception) { PontoDiaResponse(ok = false, erro = "Falha ao carregar ponto do dia.") }
        }
    }

    fun marcarPonto(tipo: String = "", observacao: String = "", lat: Double? = null, lon: Double? = null, precisao: Float? = null): PontoDiaResponse {
        val payload = gson.toJson(buildMap {
            if (tipo.isNotBlank()) put("tipo", tipo)
            if (observacao.isNotBlank()) put("observacao", observacao)
            if (lat != null) put("lat", lat)
            if (lon != null) put("lon", lon)
            if (precisao != null) put("precisao", precisao)
        })
        val req = Request.Builder()
            .url(url("/api/app/funcionario/me/ponto/marcar"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .addHeader("Content-Type", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 401) { handleUnauthorized(); return PontoDiaResponse(ok = false, erro = "Sessão expirada.") }
            val raw = resp.body?.string().orEmpty()
            return try { gson.fromJson(raw, PontoDiaResponse::class.java) }
            catch (_: Exception) { PontoDiaResponse(ok = false, erro = parseErro(raw, "Falha ao registrar ponto.")) }
        }
    }
}
