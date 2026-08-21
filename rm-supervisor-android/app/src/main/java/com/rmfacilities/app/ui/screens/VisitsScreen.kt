package com.rmfacilities.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rmfacilities.app.data.model.VisitStatus
import com.rmfacilities.app.data.model.Visita
import com.rmfacilities.app.ui.components.RmSectionCard
import com.rmfacilities.app.ui.components.ScreenHeader
import com.rmfacilities.app.ui.components.StateScaffold
import com.rmfacilities.app.ui.components.StatusChip
import com.rmfacilities.app.ui.components.StatusTone
import com.rmfacilities.app.utils.toBrDateTime
import com.rmfacilities.app.viewmodel.VisitsViewModel
import java.time.LocalDateTime
import java.util.UUID

@Composable
fun VisitsScreen(vm: VisitsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val postos by vm.postos.collectAsStateWithLifecycle()

    var posto by remember { mutableStateOf("") }
    var postoId by remember { mutableStateOf("") }
    var observacoes by remember { mutableStateOf("") }
    var problemas by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<String?>(null) }
    var longitude by remember { mutableStateOf<String?>(null) }
    val fotos = remember { mutableStateListOf<Bitmap>() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            latitude = "-23.56"
            longitude = "-46.63"
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) fotos.add(bitmap)
    }

    fun ensureLocationPermission() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            latitude = "-23.56"
            longitude = "-46.63"
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(
            title = "Visitas de supervisão",
            subtitle = "Registre presença, evidências e pendências do posto"
        )

        RmSectionCard {
                OutlinedTextField(
                    value = posto,
                    onValueChange = {
                        posto = it
                        postoId = ""
                        vm.buscarPostos(it)
                    },
                    label = { Text("Posto") },
                    modifier = Modifier.fillMaxWidth()
                )
                postos.take(5).forEach { item ->
                    Text(
                        text = "${item.nome} - ${item.cidade}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                posto = item.nome
                                postoId = item.id
                                vm.buscarPostos(item.nome)
                            },
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(value = observacoes, onValueChange = { observacoes = it }, label = { Text("Observações") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = problemas, onValueChange = { problemas = it }, label = { Text("Problemas") }, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { ensureLocationPermission() }) { Text("Registrar localização") }
                    Button(onClick = { cameraLauncher.launch(null) }) { Text("Tirar foto") }
                }

                Text("Localização: ${latitude ?: "não registrada"}, ${longitude ?: "não registrada"}")
                Text("Fotos anexadas: ${fotos.size}")
                fotos.forEach {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Foto da visita",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = {
                        if (posto.isNotBlank()) {
                            vm.salvar(
                                Visita(
                                    id = UUID.randomUUID().toString(),
                                    postoId = postoId.ifBlank { posto },
                                    postoNome = posto,
                                    supervisor = "Supervisor RM",
                                    dataHora = LocalDateTime.now(),
                                    latitude = latitude?.toDoubleOrNull(),
                                    longitude = longitude?.toDoubleOrNull(),
                                    observacoes = observacoes,
                                    problemas = problemas,
                                    status = VisitStatus.CONCLUIDA,
                                    fotos = fotos.mapIndexed { index, _ -> "foto_${index + 1}.jpg" }
                                )
                            )
                            posto = ""
                            postoId = ""
                            observacoes = ""
                            problemas = ""
                            latitude = null
                            longitude = null
                            fotos.clear()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Finalizar visita")
                }
        }

        StateScaffold(state = state, emptyMessage = "Não existem visitas cadastradas.", onRetry = { vm.load() }) { visitas ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visitas) { visita ->
                    RmSectionCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(visita.postoNome, style = MaterialTheme.typography.titleMedium)
                            StatusChip(
                                text = when (visita.status) {
                                    VisitStatus.CONCLUIDA -> "Concluída"
                                    VisitStatus.EM_ANDAMENTO -> "Em andamento"
                                    VisitStatus.PROGRAMADA -> "Programada"
                                },
                                tone = when (visita.status) {
                                    VisitStatus.CONCLUIDA -> StatusTone.Success
                                    VisitStatus.EM_ANDAMENTO -> StatusTone.Warning
                                    VisitStatus.PROGRAMADA -> StatusTone.Neutral
                                }
                            )
                        }
                        Text("${visita.observacoes.ifBlank { "Visita operacional" }} • ${visita.dataHora.toBrDateTime()}")
                        Text("Supervisor: ${visita.supervisor}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
