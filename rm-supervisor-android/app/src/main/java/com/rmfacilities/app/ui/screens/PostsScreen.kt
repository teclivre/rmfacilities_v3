package com.rmfacilities.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rmfacilities.app.data.model.EntityStatus
import com.rmfacilities.app.ui.components.InfoRow
import com.rmfacilities.app.ui.components.StateScaffold
import com.rmfacilities.app.viewmodel.PostsViewModel

@Composable
fun PostsScreen(vm: PostsViewModel, onOpenDetail: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Text("Postos", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                vm.load(query)
            },
            label = { Text("Pesquisar posto, cliente ou cidade") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        StateScaffold(state = state, emptyMessage = "Não existem postos cadastrados.", onRetry = { vm.load(query) }) { items ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                this.items(items) { posto ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenDetail(posto.id) }) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(posto.nome, style = MaterialTheme.typography.titleMedium)
                            Text("Cliente: ${posto.cliente}")
                            Text("Cidade: ${posto.cidade}")
                            Text("Supervisor: ${posto.supervisorResponsavel}")
                            Text(if (posto.status == EntityStatus.ATIVO) "Status: Ativo" else "Status: Inativo")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostDetailScreen(id: String, vm: PostsViewModel, onBack: () -> Unit) {
    val state by vm.detailState.collectAsStateWithLifecycle()

    LaunchedEffect(id) {
        vm.loadDetail(id)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Detalhes do posto", style = MaterialTheme.typography.headlineSmall)
        Text("Voltar", modifier = Modifier.clickable { onBack() }, color = MaterialTheme.colorScheme.primary)

        StateScaffold(state = state, emptyMessage = "Posto não encontrado.", onRetry = { vm.loadDetail(id) }) { p ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Posto", p.nome)
                    InfoRow("Cliente", p.cliente)
                    InfoRow("Cidade", p.cidade)
                    InfoRow("Endereço", p.endereco)
                    InfoRow("Supervisor", p.supervisorResponsavel)
                    InfoRow("Funcionários", p.funcionariosVinculados.toString())
                    InfoRow("Horário", p.horario)
                    InfoRow("Ocorrências", p.ocorrencias.toString())
                    InfoRow("Visitas", p.visitasRealizadas.toString())
                    Text("Observações: ${p.observacoes}")
                }
            }
        }
    }
}
