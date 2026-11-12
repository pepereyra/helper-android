package com.helper.mobile.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.helper.mobile.domain.model.Role
import com.helper.mobile.domain.repository.LoginResult
import com.helper.mobile.domain.usecase.LoginUseCase
import com.helper.mobile.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val creatingAccount: Boolean = false,
    val email: String = "",
    val password: String = "",
    val role: Role = Role.RIDER,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase,
    private val register: RegisterUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onRole(role: Role) = _state.update { it.copy(role = role) }
    fun toggleMode() = _state.update { it.copy(creatingAccount = !it.creatingAccount, error = null) }

    fun submit() {
        val current = _state.value
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val error = if (current.creatingAccount) {
                when (val result = register(current.email, current.password, current.role)) {
                    RegisterUseCase.Result.InvalidEmail -> "Revisá el email."
                    RegisterUseCase.Result.WeakPassword -> "La contraseña tiene que tener entre 8 y 72 caracteres."
                    RegisterUseCase.Result.RoleNotAllowed -> "Ese tipo de cuenta no se puede crear desde la app."
                    is RegisterUseCase.Result.Done -> result.login.message()
                }
            } else {
                when (val result = login(current.email, current.password)) {
                    LoginUseCase.Result.InvalidEmail -> "Revisá el email."
                    LoginUseCase.Result.EmptyPassword -> "Ingresá tu contraseña."
                    is LoginUseCase.Result.Done -> result.login.message()
                }
            }
            _state.update { it.copy(loading = false, error = error) }
        }
    }

    private fun LoginResult.message(): String? = when (this) {
        is LoginResult.LoggedIn -> null
        LoginResult.InvalidCredentials -> "Email o contraseña incorrectos."
        LoginResult.EmailAlreadyUsed -> "Ya hay una cuenta con ese email. Ingresá con tu contraseña."
        LoginResult.NoConnection -> "No hay conexión. Para ingresar la primera vez necesitás internet."
    }
}

/** El rol se elige al crear la cuenta y después lo manda siempre el servidor en el token. */
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("helper", style = MaterialTheme.typography.headlineLarge)
        Text("Un mecánico hasta donde te quedaste", style = MaterialTheme.typography.bodyLarge)
        if (state.creatingAccount) {
            Text("¿Cómo vas a usar Helper?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.role == Role.RIDER,
                    onClick = { viewModel.onRole(Role.RIDER) },
                    label = { Text("Necesito ayuda (repartidor)") },
                )
                FilterChip(
                    selected = state.role == Role.MECHANIC,
                    onClick = { viewModel.onRole(Role.MECHANIC) },
                    label = { Text("Soy mecánico") },
                )
            }
            if (state.role == Role.MECHANIC) {
                Text(
                    "Tu cuenta queda en revisión hasta que la aprobemos. Recién ahí vas a ver pedidos.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
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
            label = { Text(if (state.creatingAccount) "Contraseña (8 caracteres o más)" else "Contraseña") },
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
        ) {
            Text(
                when {
                    state.loading -> "Un momento…"
                    state.creatingAccount -> "Crear cuenta"
                    else -> "Ingresar"
                },
            )
        }
        TextButton(onClick = viewModel::toggleMode, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.creatingAccount) "Ya tengo cuenta" else "Crear una cuenta")
        }
    }
}
