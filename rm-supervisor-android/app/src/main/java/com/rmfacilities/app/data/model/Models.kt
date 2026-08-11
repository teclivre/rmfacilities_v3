package com.rmfacilities.app.data.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class EntityStatus { ATIVO, INATIVO }
enum class VisitStatus { PROGRAMADA, EM_ANDAMENTO, CONCLUIDA }
enum class OcorrenciaStatus { ABERTA, EM_ANALISE, RESOLVIDA, CANCELADA }
enum class Prioridade { BAIXA, MEDIA, ALTA, CRITICA }
enum class TarefaStatus { PENDENTE, EM_ANDAMENTO, CONCLUIDA }

data class UserSession(
    val name: String,
    val email: String,
    val token: String
)

data class DashboardMetrics(
    val funcionariosAtivos: Int,
    val postosAtivos: Int,
    val visitasDoDia: Int,
    val ocorrenciasAbertas: Int,
    val tarefasPendentes: Int,
    val funcionariosAusentes: Int
)

data class DashboardResumo(
    val ocorrenciasRecentes: List<Ocorrencia>,
    val proximasVisitas: List<Visita>,
    val tarefasPendentes: List<Tarefa>,
    val alertas: List<String>
)

data class Funcionario(
    val id: String,
    val nome: String,
    val matricula: String,
    val cpf: String,
    val funcao: String,
    val posto: String,
    val status: EntityStatus,
    val telefone: String,
    val cidade: String,
    val dataAdmissao: LocalDate,
    val observacoes: String
) {
    val cpfMascarado: String
        get() = if (cpf.length >= 4) "***.***.***-${cpf.takeLast(2)}" else "***"
}

data class Posto(
    val id: String,
    val nome: String,
    val cliente: String,
    val cidade: String,
    val status: EntityStatus,
    val supervisorResponsavel: String,
    val endereco: String,
    val funcionariosVinculados: Int,
    val horario: String,
    val observacoes: String,
    val ocorrencias: Int,
    val visitasRealizadas: Int
)

data class Visita(
    val id: String,
    val postoId: String,
    val postoNome: String,
    val supervisor: String,
    val dataHora: LocalDateTime,
    val latitude: Double?,
    val longitude: Double?,
    val observacoes: String,
    val problemas: String,
    val status: VisitStatus,
    val fotos: List<String>
)

data class Ocorrencia(
    val id: String,
    val posto: String,
    val funcionario: String,
    val dataHora: LocalDateTime,
    val tipo: String,
    val descricao: String,
    val prioridade: Prioridade,
    val status: OcorrenciaStatus,
    val fotos: List<String>,
    val observacoes: String
)

data class Tarefa(
    val id: String,
    val titulo: String,
    val descricao: String,
    val responsavel: String,
    val posto: String,
    val prazo: LocalDate,
    val prioridade: Prioridade,
    val status: TarefaStatus
)

data class ReportFilter(
    val periodoInicio: LocalDate?,
    val periodoFim: LocalDate?,
    val posto: String?,
    val funcionario: String?,
    val supervisor: String?,
    val status: String?
)
