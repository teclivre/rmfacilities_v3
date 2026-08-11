package com.rmfacilities.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rmfacilities.app.ui.components.StateScaffold
import com.rmfacilities.app.ui.components.StatCard
import com.rmfacilities.app.ui.state.UiState
import com.rmfacilities.app.utils.toBrDateTime
import com.rmfacilities.app.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(vm: DashboardViewModel, modifier: Modifier = Modifier) {
    val metricsState by vm.metricsState.collectAsStateWithLifecycle()
    val resumoState by vm.resumoState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Dashboard do Supervisor", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            StateScaffold(state = metricsState, emptyMessage = "Sem indicadores no momento", onRetry = { vm.load() }) { metrics ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        listOf(
                            "Funcionários ativos" to metrics.funcionariosAtivos.toString(),
                            "Postos ativos" to metrics.postosAtivos.toString(),
                            "Visitas do dia" to metrics.visitasDoDia.toString(),
                            "Ocorrências abertas" to metrics.ocorrenciasAbertas.toString(),
                            "Tarefas pendentes" to metrics.tarefasPendentes.toString(),
                            "Funcionários ausentes" to metrics.funcionariosAusentes.toString()
                        )
                    ) { item ->
                        StatCard(item.first, item.second)
                    }
                }
            }
        }

        item {
            Text("Resumo operacional", style = MaterialTheme.typography.titleMedium)
        }

        item {
            StateScaffold(state = resumoState, emptyMessage = "Resumo indisponível", onRetry = { vm.load() }) { resumo ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Ocorrências recentes", style = MaterialTheme.typography.titleSmall)
                            resumo.ocorrenciasRecentes.forEach {
                                Text("• ${it.tipo} - ${it.descricao}")
                            }
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Próximas visitas", style = MaterialTheme.typography.titleSmall)
                            resumo.proximasVisitas.forEach {
                                Text("• ${it.postoNome} - ${it.dataHora.toBrDateTime()}")
                            }
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Tarefas pendentes", style = MaterialTheme.typography.titleSmall)
                            resumo.tarefasPendentes.forEach {
                                Text("• ${it.titulo} (${it.status})")
                            }
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Alertas", style = MaterialTheme.typography.titleSmall)
                            resumo.alertas.forEach { Text("• $it") }
                        }
                    }
                }
            }
        }
    }
}
