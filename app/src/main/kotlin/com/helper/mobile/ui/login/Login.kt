package com.helper.mobile.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import com.helper.mobile.BuildConfig
import com.helper.mobile.data.repository.AuthRepositoryImpl
import com.helper.mobile.domain.model.Role
import com.helper.mobile.domain.repository.LoginResult
import com.helper.mobile.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val devRole: Role = Role.RIDER,
    val devUserId: String = "",
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase,
    private val auth: AuthRepositoryImpl,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onDevRole(role: Role) = _state.update { it.copy(devRole = role) }
    fun onDevUserId(value: String) = _state.update { it.copy(devUserId = value.trim(), error = null) }

    /** Entrada de prueba hasta que el backend tenga login: usa el id de un cliente o mecánico existente. */
    fun devLogin() {
        val current = _state.value
        if (current.devUserId.isBlank()) {
            _state.update { it.copy(error = "Ingresá el id del cliente o del mecánico.") }
            return
        }
        viewModelScope.launch { auth.devLogin(current.devRole, current.devUserId) }
    }

    fun submit() {
        val current = _state.value
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val error = when (val result = login(current.email, current.password)) {
                LoginUseCase.Result.InvalidEmail -> "Revisá el email."
                LoginUseCase.Result.EmptyPassword -> "Ingresá tu contraseña."
                is LoginUseCase.Result.Done -> when (result.login) {
                    is LoginResult.LoggedIn -> null
                    LoginResult.InvalidCredentials -> "Email o contraseña incorrectos."
                    LoginResult.NoConnection -> "No hay conexión. Para ingresar la primera vez necesitás internet."
                }
            }
            _state.update { it.copy(loading = false, error = error) }
        }
    }
}

/** El rol (repartidor o mecánico) lo define la cuenta en el servidor, no se elige acá. */
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("helper", style = MaterialTheme.typography.headlineLarge)
        Text("Un mecánico hasta donde te quedaste", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmail,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPassword,
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = viewModel::submit,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.loading) "Ingresando…" else "Ingresar") }

        if (BuildConfig.DEBUG) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Probar sin cuenta", style = MaterialTheme.typography.titleMedium)
            Text(
                "Hasta que el backend tenga login, entrá con el id de un cliente o un mecánico que ya exista.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.devRole == Role.RIDER,
                    onClick = { viewModel.onDevRole(Role.RIDER) },
                    label = { Text("Repartidor") },
                )
                FilterChip(
                    selected = state.devRole == Role.MECHANIC,
                    onClick = { viewModel.onDevRole(Role.MECHANIC) },
                    label = { Text("Mecánico") },
                )
            }
            OutlinedTextField(
                value = state.devUserId,
                onValueChange = viewModel::onDevUserId,
                label = { Text(if (state.devRole == Role.RIDER) "Id del cliente" else "Id del mecánico") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = viewModel::devLogin, modifier = Modifier.fillMaxWidth()) {
                Text("Entrar para probar")
            }
        }
    }
}
