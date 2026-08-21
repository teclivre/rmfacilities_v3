package com.rmfacilities.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.rmfacilities.app.ui.components.RmSectionCard
import com.rmfacilities.app.ui.components.ScreenHeader
import com.rmfacilities.app.ui.components.StateScaffold
import com.rmfacilities.app.ui.components.StatusChip
import com.rmfacilities.app.ui.components.StatusTone
import com.rmfacilities.app.ui.state.UiState
import com.rmfacilities.app.utils.toBrDate
import com.rmfacilities.app.viewmodel.EmployeesViewModel

@Composable
fun EmployeesScreen(
    vm: EmployeesViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader("Equipe", "Colaboradores sincronizados com o cadastro do sistema")
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                vm.load(query)
            },
            label = { Text("Pesquisar por nome, matrícula, posto...") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        StateScaffold(
            state = state,
            emptyMessage = "Não existem funcionários cadastrados.",
            onRetry = { vm.load(query) }
        ) { data ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(data) { funcionario ->
                    RmSectionCard(modifier = Modifier.clickable { onOpenDetail(funcionario.id) }) {
                            Text(funcionario.nome, style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Matrícula: ${funcionario.matricula}")
                                StatusChip(if (funcionario.status == EntityStatus.ATIVO) "Ativo" else "Inativo", if (funcionario.status == EntityStatus.ATIVO) StatusTone.Success else StatusTone.Warning)
                            }
                            Text("Posto: ${funcionario.posto}")
                            Text("Função: ${funcionario.funcao}")
                            Text("Cidade: ${funcionario.cidade}")
                    }
                }
            }
        }
    }
}

@Composable
fun EmployeeDetailScreen(id: String, vm: EmployeesViewModel, onBack: () -> Unit) {
    val state by vm.detailState.collectAsStateWithLifecycle()

    LaunchedEffect(id) {
        vm.loadDetail(id)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Detalhes do funcionário", "Cadastro e vínculo operacional")
        Text("Voltar", modifier = Modifier.clickable { onBack() }, color = MaterialTheme.colorScheme.primary)

        StateScaffold(state = state, emptyMessage = "Funcionário não encontrado.", onRetry = { vm.loadDetail(id) }) { f ->
            RmSectionCard {
                    InfoRow("Nome", f.nome)
                    InfoRow("Matrícula", f.matricula)
                    InfoRow("CPF", f.cpfMascarado)
                    InfoRow("Função", f.funcao)
                    InfoRow("Posto", f.posto)
                    InfoRow("Status", if (f.status == EntityStatus.ATIVO) "Ativo" else "Inativo")
                    InfoRow("Telefone", f.telefone)
                    InfoRow("Admissão", f.dataAdmissao.toBrDate())
                    Text("Observações: ${f.observacoes}")
            }
        }
    }
}
