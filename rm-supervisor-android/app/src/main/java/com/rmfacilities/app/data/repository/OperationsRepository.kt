package com.rmfacilities.app.data.repository

import com.rmfacilities.app.data.model.*

interface OperationsRepository {
    suspend fun login(email: String, password: String): Result<UserSession>
    suspend fun getDashboardMetrics(): Result<DashboardMetrics>
    suspend fun getDashboardResumo(): Result<DashboardResumo>
    suspend fun getFuncionarios(query: String = ""): Result<List<Funcionario>>
    suspend fun getFuncionarioById(id: String): Result<Funcionario>
    suspend fun getPostos(query: String = ""): Result<List<Posto>>
    suspend fun getPostoById(id: String): Result<Posto>
    suspend fun getVisitas(): Result<List<Visita>>
    suspend fun salvarVisita(visita: Visita): Result<Unit>
    suspend fun getOcorrencias(): Result<List<Ocorrencia>>
    suspend fun salvarOcorrencia(ocorrencia: Ocorrencia): Result<Unit>
    suspend fun getTarefas(): Result<List<Tarefa>>
    suspend fun marcarTarefaConcluida(id: String): Result<Unit>
}
