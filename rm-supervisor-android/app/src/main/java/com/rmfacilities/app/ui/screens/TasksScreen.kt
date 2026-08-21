package com.rmfacilities.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rmfacilities.app.data.model.TarefaStatus
import com.rmfacilities.app.ui.components.RmSectionCard
import com.rmfacilities.app.ui.components.ScreenHeader
import com.rmfacilities.app.ui.components.StateScaffold
import com.rmfacilities.app.ui.components.StatusChip
import com.rmfacilities.app.ui.components.StatusTone
import com.rmfacilities.app.utils.toBrDate
import com.rmfacilities.app.viewmodel.TasksViewModel

@Composable
fun TasksScreen(vm: TasksViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader("Tarefas", "Checklist pendente da supervisão em andamento")

        StateScaffold(state = state, emptyMessage = "Não existem tarefas cadastradas.", onRetry = { vm.load() }) { tarefas ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tarefas) { t ->
                    RmSectionCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t.titulo, style = MaterialTheme.typography.titleMedium)
                            StatusChip(if (t.status == TarefaStatus.CONCLUIDA) "Concluída" else "Pendente", if (t.status == TarefaStatus.CONCLUIDA) StatusTone.Success else StatusTone.Warning)
                        }
                            Text(t.descricao)
                            Text("Responsável: ${t.responsavel}")
                            Text("Posto: ${t.posto}")
                            Text("Prazo: ${t.prazo.toBrDate()}")
                            Text("Prioridade: ${t.prioridade}")
                            Text("Status: ${t.status}")
                            if (t.status != TarefaStatus.CONCLUIDA) {
                                Button(onClick = { vm.marcarConcluida(t.id) }) {
                                    Text("Marcar como concluída")
                                }
                            }
                    }
                }
            }
        }
    }
}
