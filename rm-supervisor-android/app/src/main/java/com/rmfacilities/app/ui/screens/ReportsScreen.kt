package com.rmfacilities.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.rmfacilities.app.data.model.ReportFilter
import com.rmfacilities.app.utils.toBrDate
import com.rmfacilities.app.viewmodel.ReportsViewModel
import java.time.LocalDate

@Composable
fun ReportsScreen(vm: ReportsViewModel, modifier: Modifier = Modifier) {
    val filter by vm.filter.collectAsStateWithLifecycle()

    var posto by remember { mutableStateOf(filter.posto.orEmpty()) }
    var funcionario by remember { mutableStateOf(filter.funcionario.orEmpty()) }
    var supervisor by remember { mutableStateOf(filter.supervisor.orEmpty()) }
    var status by remember { mutableStateOf(filter.status.orEmpty()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Relatórios", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Filtros", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = posto, onValueChange = { posto = it }, label = { Text("Posto") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = funcionario, onValueChange = { funcionario = it }, label = { Text("Funcionário") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = supervisor, onValueChange = { supervisor = it }, label = { Text("Supervisor") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = status, onValueChange = { status = it }, label = { Text("Status") }, modifier = Modifier.fillMaxWidth())

                Button(
                    onClick = {
                        vm.updateFilter(
                            ReportFilter(
                                periodoInicio = filter.periodoInicio ?: LocalDate.now().minusDays(7),
                                periodoFim = filter.periodoFim ?: LocalDate.now(),
                                posto = posto.ifBlank { null },
                                funcionario = funcionario.ifBlank { null },
                                supervisor = supervisor.ifBlank { null },
                                status = status.ifBlank { null }
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aplicar filtros")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Módulos disponíveis", style = MaterialTheme.typography.titleMedium)
                Text("• Visitas")
                Text("• Ocorrências")
                Text("• Funcionários")
                Text("• Tarefas")
                Text("• Postos")
                Text("Período: ${(filter.periodoInicio ?: LocalDate.now()).toBrDate()} até ${(filter.periodoFim ?: LocalDate.now()).toBrDate()}")
                Text("Geração de PDF: MOCK (arquitetura preparada para integração)")
            }
        }
    }
}
