package com.rmfacilities.app.data.repository

import com.rmfacilities.app.data.model.*
import com.rmfacilities.app.data.network.ApiConfig
import com.rmfacilities.app.data.session.SecureSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ApiOperationsRepository(private val sessionStore: SecureSessionStore) : OperationsRepository {
    private val baseUrl = ApiConfig.baseUrl.trim().trimEnd('/')

    override suspend fun login(email: String, password: String): Result<UserSession> = withContext(Dispatchers.IO) {
        runCatching {
            val body = formBody("email" to email.trim(), "senha" to password)
            val response = request("/supervisor/login", method = "POST", body = body, contentType = "application/x-www-form-urlencoded")
            val cookie = response.cookie?.takeIf { it.substringAfter("session=", "").isNotBlank() }
            if (response.code !in 300..399 || cookie == null) throw IllegalArgumentException(parseHtmlError(response.body))
            val name = email.substringBefore('@').replaceFirstChar { it.titlecase(Locale.getDefault()) }
            sessionStore.saveToken(cookie)
            sessionStore.saveUserName(name)
            UserSession(name, email, cookie)
        }
    }

    override suspend fun getDashboardMetrics(): Result<DashboardMetrics> = withContext(Dispatchers.IO) {
        runCatching {
            val agenda = agendaItems()
            val funcionarios = funcionariosItems(onlyActive = false)
            val ocorrencias = ocorrenciasItems()
            val visitados = visitadosItems()
            DashboardMetrics(
                funcionariosAtivos = funcionarios.count { it.status == EntityStatus.ATIVO },
                postosAtivos = agenda.distinctBy { it.optString("cliente_id", it.optString("cliente_nome")) }.size,
                visitasDoDia = agenda.size + visitados.count { it.optString("data") == todayBr() },
                ocorrenciasAbertas = ocorrencias.count { parseOcorrenciaStatus(it.optString("status")) != OcorrenciaStatus.RESOLVIDA },
                tarefasPendentes = pendingChecklistCount(),
                funcionariosAusentes = funcionarios.count { it.status == EntityStatus.INATIVO }
            )
        }
    }

    override suspend fun getDashboardResumo(): Result<DashboardResumo> = withContext(Dispatchers.IO) {
        runCatching {
            val ocorrencias = ocorrenciasItems().map { it.toOcorrencia() }.take(5)
            val visitas = agendaItems().map { it.toVisitaProgramada() }.take(5)
            val tarefas = checklistTasks().filter { it.status != TarefaStatus.CONCLUIDA }.take(5)
            val alertas = buildList {
                if (ocorrencias.any { it.prioridade == Prioridade.ALTA || it.prioridade == Prioridade.CRITICA }) add("Ocorrências críticas aguardam acompanhamento.")
                if (visitas.isNotEmpty()) add("${visitas.size} visitas ativas na agenda do sistema.")
                if (tarefas.isNotEmpty()) add("${tarefas.size} itens de checklist pendentes.")
            }
            DashboardResumo(ocorrencias, visitas, tarefas, alertas)
        }
    }

    override suspend fun getFuncionarios(query: String): Result<List<Funcionario>> = withContext(Dispatchers.IO) {
        runCatching { funcionariosItems(query, onlyActive = false) }
    }

    override suspend fun getFuncionarioById(id: String): Result<Funcionario> = withContext(Dispatchers.IO) {
        runCatching {
            funcionariosItems(onlyActive = false).firstOrNull { it.id == id }
                ?: throw NoSuchElementException("Funcionário não encontrado")
        }
    }

    override suspend fun getPostos(query: String): Result<List<Posto>> = withContext(Dispatchers.IO) {
        runCatching {
            val q = query.trim().lowercase()
            val postos = agendaItems().map { it.toPosto() }.distinctBy { it.id }
            if (q.isBlank()) postos else postos.filter {
                it.nome.lowercase().contains(q) || it.cliente.lowercase().contains(q) || it.cidade.lowercase().contains(q)
            }
        }
    }

    override suspend fun getPostoById(id: String): Result<Posto> = withContext(Dispatchers.IO) {
        runCatching {
            agendaItems().map { it.toPosto() }.firstOrNull { it.id == id }
                ?: throw NoSuchElementException("Posto não encontrado")
        }
    }

    override suspend fun getVisitas(): Result<List<Visita>> = withContext(Dispatchers.IO) {
        runCatching {
            val programadas = agendaItems().map { it.toVisitaProgramada() }
            val realizadas = visitadosItems().map { it.toVisitaRealizada() }
            (realizadas + programadas).sortedByDescending { it.dataHora }
        }
    }

    override suspend fun salvarVisita(visita: Visita): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requestJson(
                "/api/supervisor/checkin",
                JSONObject()
                    .put("contrato_id", visita.postoId.toIntOrNull())
                    .put("observacao", visita.observacoes)
                    .put("lat", visita.latitude)
                    .put("lon", visita.longitude)
            )
            requestJson(
                "/api/supervisor/checklist",
                JSONObject()
                    .put("servico", visita.postoNome)
                    .put("items", JSONArray(listOf(
                        JSONObject().put("label", "Local conferido").put("checked", true),
                        JSONObject().put("label", "Pendências registradas").put("checked", visita.problemas.isBlank()),
                        JSONObject().put("label", "Fechamento da visita finalizado").put("checked", true)
                    )))
            )
            if (visita.problemas.isNotBlank()) {
                salvarOcorrencia(
                    Ocorrencia(
                        id = visita.id,
                        posto = visita.postoNome,
                        funcionario = "",
                        dataHora = visita.dataHora,
                        tipo = "Visita",
                        descricao = visita.problemas,
                        prioridade = Prioridade.MEDIA,
                        status = OcorrenciaStatus.ABERTA,
                        fotos = visita.fotos,
                        observacoes = visita.observacoes
                    )
                ).getOrThrow()
            }
            requestJson("/api/supervisor/finalizar", JSONObject().put("observacoes", visita.observacoes))
            Unit
        }
    }

    override suspend fun getOcorrencias(): Result<List<Ocorrencia>> = withContext(Dispatchers.IO) {
        runCatching { ocorrenciasItems().map { it.toOcorrencia() } }
    }

    override suspend fun salvarOcorrencia(ocorrencia: Ocorrencia): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requestJson(
                "/api/supervisor/ocorrencias",
                JSONObject()
                    .put("titulo", ocorrencia.tipo.ifBlank { "Ocorrência" })
                    .put("categoria", ocorrencia.tipo.ifBlank { "Operacional" })
                    .put("prioridade", ocorrencia.prioridade.label())
                    .put("descricao", ocorrencia.descricao)
                    .put("status", ocorrencia.status.label())
                    .put("cliente_nome", ocorrencia.posto.ifBlank { "Cliente" })
            )
                    Unit
        }
    }

    override suspend fun getTarefas(): Result<List<Tarefa>> = withContext(Dispatchers.IO) {
        runCatching { checklistTasks() }
    }

    override suspend fun marcarTarefaConcluida(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val checklist = requestJson("/api/supervisor/checklist", null).json.optJSONObject("checklist") ?: JSONObject()
            val items = checklist.optJSONArray("items") ?: JSONArray()
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                if ("checklist-$index" == id) item.put("checked", true)
            }
            requestJson(
                "/api/supervisor/checklist",
                JSONObject()
                    .put("servico", checklist.optString("servico", "Visita"))
                    .put("items", items)
            )
                    Unit
        }
    }

    private fun funcionariosItems(query: String = "", onlyActive: Boolean): List<Funcionario> {
        val path = buildString {
            append("/api/funcionarios")
            val params = mutableListOf<String>()
            if (query.isNotBlank()) params += "q=${encode(query)}"
            if (onlyActive) params += "status=Ativo"
            if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        }
        return requestJsonArray(path).mapObjects { it.toFuncionario() }
    }

    private fun agendaItems(): List<JSONObject> = requestJson("/api/supervisor/agenda", null)
        .json.optJSONArray("agenda")
        .orEmptyObjects()

    private fun visitadosItems(): List<JSONObject> = requestJson("/api/supervisor/visitados", null)
        .json.optJSONArray("visitados")
        .orEmptyObjects()

    private fun ocorrenciasItems(): List<JSONObject> = requestJson("/api/supervisor/ocorrencias", null)
        .json.optJSONArray("ocorrencias")
        .orEmptyObjects()

    private fun checklistTasks(): List<Tarefa> {
        val payload = requestJson("/api/supervisor/checklist", null).json.optJSONObject("checklist") ?: JSONObject()
        val servico = payload.optString("servico", "Visita")
        return payload.optJSONArray("items").orEmptyObjects().mapIndexed { index, item ->
            Tarefa(
                id = "checklist-$index",
                titulo = item.optString("label", "Item ${index + 1}"),
                descricao = "Checklist operacional de $servico",
                responsavel = sessionStore.getUserName(),
                posto = servico,
                prazo = LocalDate.now(),
                prioridade = Prioridade.MEDIA,
                status = if (item.optBoolean("checked", false)) TarefaStatus.CONCLUIDA else TarefaStatus.PENDENTE
            )
        }
    }

    private fun pendingChecklistCount(): Int = checklistTasks().count { it.status != TarefaStatus.CONCLUIDA }

    private fun requestJson(path: String, body: JSONObject?): HttpPayload {
        val response = request(path, if (body == null) "GET" else "POST", body?.toString(), "application/json")
        if (response.code !in 200..299) throw IllegalStateException(parseApiError(response.body, "Erro HTTP ${response.code}"))
        return HttpPayload(response.body)
    }

    private fun requestJsonArray(path: String): JSONArray {
        val response = request(path, "GET", null, "application/json")
        if (response.code !in 200..299) throw IllegalStateException(parseApiError(response.body, "Erro HTTP ${response.code}"))
        return JSONArray(response.body)
    }

    private fun request(path: String, method: String, body: String?, contentType: String): HttpResponse {
        val connection = (URL(resolveUrl(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json, text/html")
            sessionStore.getToken()?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
            }
        }
        if (body != null) {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
        }
        val raw = readResponse(connection)
        val cookie = connection.headerFields.entries
            .firstOrNull { it.key.equals("Set-Cookie", ignoreCase = true) }
            ?.value
            ?.firstOrNull { it.startsWith("session=") }
            ?.substringBefore(';')
        return HttpResponse(connection.responseCode, raw, cookie)
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..399) connection.inputStream else connection.errorStream
        return stream?.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText() }.orEmpty()
    }

    private fun resolveUrl(path: String): String = if (path.startsWith("http")) path else "$baseUrl$path"

    private fun formBody(vararg values: Pair<String, String>): String = values.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun parseApiError(raw: String, fallback: String): String = runCatching {
        JSONObject(raw).optString("erro", fallback).ifBlank { fallback }
    }.getOrDefault(fallback)

    private fun parseHtmlError(raw: String): String {
        return when {
            raw.contains("Credenciais inválidas", ignoreCase = true) -> "Credenciais inválidas para o supervisor."
            raw.contains("restrito ao perfil", ignoreCase = true) -> "Este acesso é restrito ao perfil de supervisor."
            raw.contains("Informe e-mail", ignoreCase = true) -> "Informe e-mail e senha do supervisor."
            else -> "Não foi possível autenticar no sistema."
        }
    }

    private fun JSONObject.toFuncionario(): Funcionario {
        val statusRaw = optString("status", "Ativo")
        return Funcionario(
            id = optString("id"),
            nome = optString("nome", "Funcionário"),
            matricula = optString("matricula", optString("re")),
            cpf = optString("cpf"),
            funcao = optString("funcao", optString("cargo")),
            posto = optString("posto_operacional", "Reserva técnica"),
            status = if (statusRaw.equals("Ativo", true) || statusRaw.equals("Férias", true)) EntityStatus.ATIVO else EntityStatus.INATIVO,
            telefone = optString("telefone"),
            cidade = optString("cidade"),
            dataAdmissao = parseDate(optString("data_admissao")),
            observacoes = optString("obs")
        )
    }

    private fun JSONObject.toPosto(): Posto {
        val cliente = optString("cliente_nome", optString("nome", "Cliente"))
        return Posto(
            id = optString("id", optString("cliente_id")),
            nome = cliente,
            cliente = cliente,
            cidade = optString("cidade"),
            status = EntityStatus.ATIVO,
            supervisorResponsavel = sessionStore.getUserName(),
            endereco = optString("endereco", optString("end_fmt")),
            funcionariosVinculados = optInt("qtd_funcionarios_posto", 0),
            horario = optString("data", "Hoje"),
            observacoes = optString("servico", optString("obs")),
            ocorrencias = 0,
            visitasRealizadas = 0
        )
    }

    private fun JSONObject.toVisitaProgramada(): Visita = Visita(
        id = optString("id", optString("contrato_id", optString("cliente_id"))),
        postoId = optString("id", optString("contrato_id", optString("cliente_id"))),
        postoNome = optString("cliente_nome", "Cliente"),
        supervisor = sessionStore.getUserName(),
        dataHora = LocalDateTime.now(),
        latitude = null,
        longitude = null,
        observacoes = optString("servico", "Visita programada"),
        problemas = "",
        status = VisitStatus.PROGRAMADA,
        fotos = emptyList()
    )

    private fun JSONObject.toVisitaRealizada(): Visita = Visita(
        id = optString("contrato_id", optString("cliente_id", optString("numero"))),
        postoId = optString("contrato_id", optString("cliente_id")),
        postoNome = optString("cliente_nome", "Cliente"),
        supervisor = sessionStore.getUserName(),
        dataHora = parseDateTime(optString("horario")),
        latitude = null,
        longitude = null,
        observacoes = optString("observacoes"),
        problemas = "",
        status = VisitStatus.CONCLUIDA,
        fotos = emptyList()
    )

    private fun JSONObject.toOcorrencia(): Ocorrencia = Ocorrencia(
        id = optString("id"),
        posto = optString("cliente_nome", optString("posto", "Cliente")),
        funcionario = optString("funcionario"),
        dataHora = parseDateTime("${optString("data")} ${optString("horario")}".trim()),
        tipo = optString("categoria", optString("titulo", "Ocorrência")),
        descricao = optString("descricao", optString("titulo")),
        prioridade = parsePrioridade(optString("prioridade")),
        status = parseOcorrenciaStatus(optString("status")),
        fotos = emptyList(),
        observacoes = optString("observacoes")
    )

    private fun parseDate(raw: String): LocalDate = runCatching {
        if (raw.contains('/')) LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy")) else LocalDate.parse(raw.take(10))
    }.getOrDefault(LocalDate.now())

    private fun parseDateTime(raw: String): LocalDateTime = runCatching {
        LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }.getOrDefault(LocalDateTime.now())

    private fun parsePrioridade(raw: String): Prioridade = when (raw.lowercase()) {
        "baixa" -> Prioridade.BAIXA
        "alta" -> Prioridade.ALTA
        "critica", "crítica" -> Prioridade.CRITICA
        else -> Prioridade.MEDIA
    }

    private fun parseOcorrenciaStatus(raw: String): OcorrenciaStatus = when (raw.lowercase()) {
        "em análise", "em_analise", "em analise" -> OcorrenciaStatus.EM_ANALISE
        "resolvida" -> OcorrenciaStatus.RESOLVIDA
        "cancelada" -> OcorrenciaStatus.CANCELADA
        else -> OcorrenciaStatus.ABERTA
    }

    private fun Prioridade.label(): String = when (this) {
        Prioridade.BAIXA -> "Baixa"
        Prioridade.MEDIA -> "Média"
        Prioridade.ALTA -> "Alta"
        Prioridade.CRITICA -> "Crítica"
    }

    private fun OcorrenciaStatus.label(): String = when (this) {
        OcorrenciaStatus.ABERTA -> "Aberta"
        OcorrenciaStatus.EM_ANALISE -> "Em análise"
        OcorrenciaStatus.RESOLVIDA -> "Resolvida"
        OcorrenciaStatus.CANCELADA -> "Cancelada"
    }

    private fun todayBr(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    private fun JSONArray?.orEmptyObjects(): List<JSONObject> = this?.mapObjects { it } ?: emptyList()

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
        val result = mutableListOf<T>()
        for (index in 0 until length()) {
            optJSONObject(index)?.let { result += transform(it) }
        }
        return result
    }

    private data class HttpResponse(val code: Int, val body: String, val cookie: String?)
    private data class HttpPayload(val raw: String) {
        val json: JSONObject get() = JSONObject(raw)
    }
}
