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
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

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
    }
}
