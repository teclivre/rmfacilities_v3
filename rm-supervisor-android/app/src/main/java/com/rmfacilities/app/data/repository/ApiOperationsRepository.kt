package com.rmfacilities.app.data.repository

import com.rmfacilities.app.data.model.*

class ApiOperationsRepository : OperationsRepository {
    override suspend fun login(email: String, password: String): Result<UserSession> =
        Result.failure(UnsupportedOperationException("API real ainda não integrada"))

    override suspend fun getDashboardMetrics(): Result<DashboardMetrics> =
        Result.failure(UnsupportedOperationException("API real ainda não integrada"))

    override suspend fun getDashboardResumo(): Result<DashboardResumo> =
        Result.failure(UnsupportedOperationException("API real ainda não integrada"))

    override suspend fun getFuncionarios(query: String): Result<List<Funcionario>> =
        Result.failure(UnsupportedOperationException("API real ainda não integrada"))

    override suspend fun getFuncionarioById(id: String): Result<Funcionario> =
        Result.failure(UnsupportedOperationException("API real ainda não integrada"))

    override suspend fun getPostos(query: String): Result<List<Posto>> =
        Result.failure(UnsupportedOperationException("API real ainda não integrada"))

    override suspend fun getPostoById(id: String): Result<Posto> =
        Result.failure(UnsupportedOperationException("API real ainda não integrada"))

    override suspend fun getVisitas(): Result<List<Visita>> =
        Result.failure(UnsupportedOperationException("API real ainda não integrada"))

    override suspend fun salvarVisita(visita: Visita): Result<Unit> =
        Result.failure(UnsupportedOperationException("API real ainda não integrada"))

    override suspend fun getOcorrencias(): Result<List<Ocorrencia>> =
        Result.failure(UnsupportedOperationException("API real ainda não integrada"))

    override suspend fun salvarOcorrencia(ocorrencia: Ocorrencia): Result<Unit> =
        Result.failure(UnsupportedOperationException("API real ainda não integrada"))

    override suspend fun getTarefas(): Result<List<Tarefa>> =
        Result.failure(UnsupportedOperationException("API real ainda não integrada"))

    override suspend fun marcarTarefaConcluida(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("API real ainda não integrada"))
}
