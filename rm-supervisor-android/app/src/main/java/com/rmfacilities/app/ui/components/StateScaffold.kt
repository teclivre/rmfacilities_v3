package com.rmfacilities.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rmfacilities.app.ui.state.UiState

@Composable
fun <T> StateScaffold(
    state: UiState<T>,
    emptyMessage: String,
    onRetry: () -> Unit,
    success: @Composable (T) -> Unit
) {
    when (state) {
        is UiState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        }

        is UiState.Empty -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(emptyMessage, textAlign = TextAlign.Center)
                Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Tentar novamente")
                }
            }
        }

        is UiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Tentar novamente")
                }
            }
        }

        is UiState.Success -> success(state.data)
    }
}
