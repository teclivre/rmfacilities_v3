package com.rmfacilities.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rmfacilities.app.data.model.Ocorrencia
import com.rmfacilities.app.data.model.OcorrenciaStatus
import com.rmfacilities.app.data.model.Prioridade
import com.rmfacilities.app.ui.components.RmSectionCard
import com.rmfacilities.app.ui.components.ScreenHeader
import com.rmfacilities.app.ui.components.StateScaffold
import com.rmfacilities.app.ui.components.StatusChip
import com.rmfacilities.app.ui.components.StatusTone
import com.rmfacilities.app.utils.toBrDateTime
import com.rmfacilities.app.viewmodel.OccurrencesViewModel
import java.time.LocalDateTime
import java.util.UUID

@Composable
fun OccurrencesScreen(vm: OccurrencesViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val statusFilter by vm.statusFilter.collectAsStateWithLifecycle()
    val priorityFilter by vm.priorityFilter.collectAsStateWithLifecycle()

    var posto by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Ocorrências", "Registros abertos no atendimento operacional")

        RmSectionCard {
                OutlinedTextField(value = posto, onValueChange = { posto = it }, label = { Text("Posto") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tipo, onValueChange = { tipo = it }, label = { Text("Tipo") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = descricao, onValueChange = { descricao = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        if (posto.isNotBlank() && tipo.isNotBlank() && descricao.isNotBlank()) {
                            vm.save(
                                Ocorrencia(
                                    id = UUID.randomUUID().toString(),
                                    posto = posto,
                                    funcionario = "",
                                    dataHora = LocalDateTime.now(),
                                    tipo = tipo,
                                    descricao = descricao,
                                    prioridade = Prioridade.MEDIA,
                                    status = OcorrenciaStatus.ABERTA,
                                    fotos = emptyList(),
                                    observacoes = ""
                                )
                            )
                            posto = ""
                            tipo = ""
                            descricao = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cadastrar ocorrência")
                }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.setStatusFilter(null) }) { Text("Status: Todos") }
            Button(onClick = { vm.setStatusFilter(OcorrenciaStatus.ABERTA) }) { Text("Abertas") }
            Button(onClick = { vm.setStatusFilter(OcorrenciaStatus.RESOLVIDA) }) { Text("Resolvidas") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.setPriorityFilter(null) }) { Text("Prioridade: Todas") }
            Button(onClick = { vm.setPriorityFilter(Prioridade.ALTA) }) { Text("Alta") }
            Button(onClick = { vm.setPriorityFilter(Prioridade.MEDIA) }) { Text("Média") }
        }

        Text("Filtro atual: status=${statusFilter ?: "todos"}, prioridade=${priorityFilter ?: "todas"}")

        StateScaffold(state = state, emptyMessage = "Não existem ocorrências cadastradas.", onRetry = { vm.load() }) { ocorrencias ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ocorrencias) { o ->
                    RmSectionCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${o.tipo} - ${o.posto}", style = MaterialTheme.typography.titleMedium)
                            StatusChip(o.prioridade.name, if (o.prioridade == Prioridade.ALTA || o.prioridade == Prioridade.CRITICA) StatusTone.Danger else StatusTone.Neutral)
                        }
                            Text(o.descricao)
                            Text("Data: ${o.dataHora.toBrDateTime()}")
                        Text("Status: ${o.status}")
                    }
                }
            }
        }
    }
}
