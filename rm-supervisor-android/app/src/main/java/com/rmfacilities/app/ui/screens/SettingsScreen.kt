package com.rmfacilities.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rmfacilities.app.data.network.ApiConfig
import com.rmfacilities.app.ui.components.RmSectionCard
import com.rmfacilities.app.ui.components.ScreenHeader
import com.rmfacilities.app.ui.components.StatusChip
import com.rmfacilities.app.ui.components.StatusTone
import com.rmfacilities.app.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val notificationsEnabled by vm.notificationEnabled.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.toggleNotifications()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            title = "Configurações",
            subtitle = "Ambiente conectado ao portal operacional"
        )

        RmSectionCard {
            Text("Integrações", style = MaterialTheme.typography.titleMedium)
            Text(ApiConfig.baseUrl)
            StatusChip(if (ApiConfig.useMockData) "Dados de teste" else "Sistema real", if (ApiConfig.useMockData) StatusTone.Warning else StatusTone.Success)
            Text("Notificações preparadas para FCM")
        }

        RmSectionCard {
            Text("Permissões", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.toggleNotifications()
                    }
                }
            )
        }

        Button(onClick = {
            vm.clearSession()
            onLogout()
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Sair")
        }
    }
}
