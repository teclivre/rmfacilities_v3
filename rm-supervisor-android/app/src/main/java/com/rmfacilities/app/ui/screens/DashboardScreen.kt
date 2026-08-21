package com.rmfacilities.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rmfacilities.app.data.model.Prioridade
import com.rmfacilities.app.ui.components.ProgressSummary
import com.rmfacilities.app.ui.components.RmSectionCard
import com.rmfacilities.app.ui.components.ScreenHeader
import com.rmfacilities.app.ui.components.StateScaffold
import com.rmfacilities.app.ui.components.StatusChip
import com.rmfacilities.app.ui.components.StatusTone
import com.rmfacilities.app.ui.components.StatCard
import com.rmfacilities.app.utils.toBrDateTime
import com.rmfacilities.app.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(vm: DashboardViewModel, modifier: Modifier = Modifier) {
    val metricsState by vm.metricsState.collectAsStateWithLifecycle()
    val resumoState by vm.resumoState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "Dashboard do Supervisor",
                subtitle = "Dados operacionais carregados diretamente do sistema RM"
            )
        }

        item {
            StateScaffold(
                state = metricsState,
                emptyMessage = "Sem indicadores no momento",
                onRetry = { vm.load() },
                fullScreen = false
            ) { metrics ->
                val metricItems = listOf(
                    "Funcionários ativos" to metrics.funcionariosAtivos.toString(),
                    "Postos ativos" to metrics.postosAtivos.toString(),
                    "Visitas do dia" to metrics.visitasDoDia.toString(),
                    "Ocorrências abertas" to metrics.ocorrenciasAbertas.toString(),
                    "Tarefas pendentes" to metrics.tarefasPendentes.toString(),
                    "Funcionários ausentes" to metrics.funcionariosAusentes.toString()
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    metricItems.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { item ->
                                Column(modifier = Modifier.weight(1f)) {
                                    StatCard(item.first, item.second)
                                }
                            }
                            if (rowItems.size == 1) {
                                Column(modifier = Modifier.weight(1f)) {}
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Resumo operacional", style = MaterialTheme.typography.titleLarge)
        }

        item {
            StateScaffold(
                state = resumoState,
                emptyMessage = "Resumo indisponível",
                onRetry = { vm.load() },
                fullScreen = false
            ) { resumo ->
                val abertas = resumo.ocorrenciasRecentes.count { it.prioridade == Prioridade.ALTA || it.prioridade == Prioridade.CRITICA }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RmSectionCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ocorrências recentes", style = MaterialTheme.typography.titleMedium)
                            StatusChip("${resumo.ocorrenciasRecentes.size}", if (abertas > 0) StatusTone.Danger else StatusTone.Neutral)
                        }
                        if (resumo.ocorrenciasRecentes.isEmpty()) {
                            Text("Sem ocorrências recentes.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            resumo.ocorrenciasRecentes.forEach {
                                Text("${it.posto} • ${it.tipo}: ${it.descricao}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    RmSectionCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Próximas visitas", style = MaterialTheme.typography.titleMedium)
                            StatusChip("Agenda", StatusTone.Warm)
                        }
                        resumo.proximasVisitas.forEach {
                            Text("${it.postoNome} • ${it.observacoes} • ${it.dataHora.toBrDateTime()}")
                        }
                    }
                    RmSectionCard {
                        ProgressSummary(
                            label = "Checklist da visita",
                            value = if (resumo.tarefasPendentes.isEmpty()) 1f else 0.35f,
                            detail = "${resumo.tarefasPendentes.size} pendentes"
                        )
                        resumo.tarefasPendentes.forEach {
                            Text("${it.titulo} • ${it.posto}")
                        }
                    }
                    RmSectionCard {
                        Text("Alertas", style = MaterialTheme.typography.titleMedium)
                        if (resumo.alertas.isEmpty()) Text("Nenhum alerta operacional no momento.")
                        resumo.alertas.forEach { Text(it) }
                    }
                }
            }
        }
    }
}
