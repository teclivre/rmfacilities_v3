package com.rmfacilities.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rmfacilities.app.viewmodel.AuthUiState
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    state: AuthUiState,
    onLogin: (String, String) -> Unit,
    onDismissError: () -> Unit,
    onSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onDismissError()
        }
    }

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("RM Facilities", style = MaterialTheme.typography.headlineMedium)
        Text("Acesso do Supervisor", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        )

        Button(
            onClick = {
                if (email.contains("@") && senha.length >= 6) {
                    onLogin(email.trim(), senha)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text(if (state.isLoading) "Entrando..." else "Entrar")
        }

        Button(
            onClick = {
                scope.launch {
                    snackbarHostState.showSnackbar("Fluxo de recuperação preparado para integração com backend.")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Esqueci minha senha")
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(top = 12.dp))
    }
}
