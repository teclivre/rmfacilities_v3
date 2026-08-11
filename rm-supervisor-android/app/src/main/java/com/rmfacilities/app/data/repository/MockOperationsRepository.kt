package com.rmfacilities.app.data.repository

import com.rmfacilities.app.data.model.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

class MockOperationsRepository : OperationsRepository {

    private val funcionarios = mutableListOf(
        Funcionario("1", "Aline Rodrigues", "RM001", "12345678900", "Supervisora", "NOVA MOTORS", EntityStatus.ATIVO, "(11) 98888-1111", "São Paulo", LocalDate.parse("2022-03-11"), "Responsável por visitas de rotina."),
        Funcionario("2", "Bruno Alves", "RM045", "98765432100", "Porteiro", "TORK TOOLS", EntityStatus.ATIVO, "(11) 97777-2222", "Guarulhos", LocalDate.parse("2021-09-02"), "Escala noturna."),
        Funcionario("3", "Clara Mendes", "RM078", "55544433322", "Auxiliar de Limpeza", "NOVA MOTORS", EntityStatus.INATIVO, "(11) 96666-3333", "São Paulo", LocalDate.parse("2020-01-25"), "Afastamento médico.")
    )

    private val postos = mutableListOf(
        Posto("1", "NOVA MOTORS COMERCIO", "NOVA MOTORS", "São Paulo", EntityStatus.ATIVO, "Aline Rodrigues", "Av. Paulista, 1000", 9, "06:00 - 18:00", "Posto com alta circulação.", 2, 14),
        Posto("2", "TORK TOOLS COMERCIAL", "TORK TOOLS", "Guarulhos", EntityStatus.ATIVO, "Aline Rodrigues", "Rua Industrial, 255", 6, "24h", "Ponto crítico em portaria.", 1, 10)
    )

    private val visitas = mutableListOf(
        Visita("1", "1", "NOVA MOTORS COMERCIO", "Aline Rodrigues", LocalDateTime.now().minusHours(2), -23.56, -46.63, "Checklist iniciado", "Sem problemas graves", VisitStatus.EM_ANDAMENTO, emptyList()),
        Visita("2", "2", "TORK TOOLS COMERCIAL", "Aline Rodrigues", LocalDateTime.now().plusHours(1), null, null, "Visita programada", "", VisitStatus.PROGRAMADA, emptyList())
    )

    private val ocorrencias = mutableListOf(
        Ocorrencia("1", "NOVA MOTORS COMERCIO", "Bruno Alves", LocalDateTime.now().minusHours(3), "Operacional", "Falta de insumo de limpeza", Prioridade.MEDIA, OcorrenciaStatus.ABERTA, emptyList(), "Aguardando reposição"),
        Ocorrencia("2", "TORK TOOLS COMERCIAL", "", LocalDateTime.now().minusDays(1), "Segurança", "Portão lateral com falha", Prioridade.ALTA, OcorrenciaStatus.EM_ANALISE, emptyList(), "Manutenção acionada")
    )

    private val tarefas = mutableListOf(
        Tarefa("1", "Conferir EPIs", "Validar uso de EPI no turno manhã", "Aline Rodrigues", "NOVA MOTORS", LocalDate.now().plusDays(1), Prioridade.ALTA, TarefaStatus.PENDENTE),
        Tarefa("2", "Atualizar escala", "Revisar escala do posto TORK", "Aline Rodrigues", "TORK TOOLS", LocalDate.now().plusDays(2), Prioridade.MEDIA, TarefaStatus.EM_ANDAMENTO)
    )

    override suspend fun login(email: String, password: String): Result<UserSession> {
        delay(400)
        if (email.isBlank() || password.length < 6) return Result.failure(IllegalArgumentException("Credenciais inválidas"))
        return Result.success(UserSession("Supervisor RM", email, "mock-token-${System.currentTimeMillis()}"))
    }

    override suspend fun getDashboardMetrics(): Result<DashboardMetrics> {
        delay(300)
        return Result.success(
            DashboardMetrics(
                funcionariosAtivos = funcionarios.count { it.status == EntityStatus.ATIVO },
                postosAtivos = postos.count { it.status == EntityStatus.ATIVO },
                visitasDoDia = visitas.size,
                ocorrenciasAbertas = ocorrencias.count { it.status == OcorrenciaStatus.ABERTA || it.status == OcorrenciaStatus.EM_ANALISE },
                tarefasPendentes = tarefas.count { it.status != TarefaStatus.CONCLUIDA },
                funcionariosAusentes = funcionarios.count { it.status == EntityStatus.INATIVO }
            )
        )
    }

    override suspend fun getDashboardResumo(): Result<DashboardResumo> {
        delay(300)
        return Result.success(
            DashboardResumo(
                ocorrenciasRecentes = ocorrencias.take(3),
                proximasVisitas = visitas.sortedBy { it.dataHora }.take(3),
                tarefasPendentes = tarefas.filter { it.status != TarefaStatus.CONCLUIDA }.take(3),
                alertas = listOf("1 ocorrência com prioridade ALTA.", "2 visitas previstas para hoje.")
            )
        )
    }

    override suspend fun getFuncionarios(query: String): Result<List<Funcionario>> {
        delay(250)
        val q = query.trim().lowercase()
        val filtered = if (q.isBlank()) funcionarios else funcionarios.filter {
            it.nome.lowercase().contains(q) || it.matricula.lowercase().contains(q) || it.posto.lowercase().contains(q) || it.funcao.lowercase().contains(q) || it.cidade.lowercase().contains(q)
        }
        return Result.success(filtered)
    }

    override suspend fun getFuncionarioById(id: String): Result<Funcionario> {
        delay(150)
        return funcionarios.find { it.id == id }?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("Funcionário não encontrado"))
    }

    override suspend fun getPostos(query: String): Result<List<Posto>> {
        delay(250)
        val q = query.trim().lowercase()
        val filtered = if (q.isBlank()) postos else postos.filter {
            it.nome.lowercase().contains(q) || it.cliente.lowercase().contains(q) || it.cidade.lowercase().contains(q)
        }
        return Result.success(filtered)
    }

    override suspend fun getPostoById(id: String): Result<Posto> {
        delay(150)
        return postos.find { it.id == id }?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("Posto não encontrado"))
    }

    override suspend fun getVisitas(): Result<List<Visita>> {
        delay(250)
        return Result.success(visitas.sortedByDescending { it.dataHora })
    }

    override suspend fun salvarVisita(visita: Visita): Result<Unit> {
        delay(350)
        visitas.removeAll { it.id == visita.id }
        visitas.add(visita)
        return Result.success(Unit)
    }

    override suspend fun getOcorrencias(): Result<List<Ocorrencia>> {
        delay(250)
        return Result.success(ocorrencias.sortedByDescending { it.dataHora })
    }

    override suspend fun salvarOcorrencia(ocorrencia: Ocorrencia): Result<Unit> {
        delay(350)
        ocorrencias.removeAll { it.id == ocorrencia.id }
        ocorrencias.add(0, ocorrencia)
        return Result.success(Unit)
    }

    override suspend fun getTarefas(): Result<List<Tarefa>> {
        delay(250)
        return Result.success(tarefas.toList())
    }

    override suspend fun marcarTarefaConcluida(id: String): Result<Unit> {
        delay(200)
        val idx = tarefas.indexOfFirst { it.id == id }
        if (idx == -1) return Result.failure(NoSuchElementException("Tarefa não encontrada"))
        val task = tarefas[idx]
        tarefas[idx] = task.copy(status = TarefaStatus.CONCLUIDA)
        return Result.success(Unit)
    }
}
