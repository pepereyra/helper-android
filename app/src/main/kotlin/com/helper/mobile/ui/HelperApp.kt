@file:OptIn(ExperimentalCoroutinesApi::class)

package com.helper.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.mobile.domain.model.Role
import com.helper.mobile.domain.model.Session
import com.helper.mobile.domain.model.VerificationStatus
import com.helper.mobile.domain.repository.AuthRepository
import com.helper.mobile.domain.repository.ProfileRepository
import com.helper.mobile.domain.repository.SessionRepository
import com.helper.mobile.ui.login.LoginScreen
import com.helper.mobile.ui.mechanic.MechanicScreen
import com.helper.mobile.ui.mechanic.PendingVerificationScreen
import com.helper.mobile.ui.profile.ProfileScreen
import com.helper.mobile.ui.rider.RiderScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppState {
    data object Loading : AppState
    data object LoggedOut : AppState
    /** La cuenta existe pero todavía no tiene perfil de repartidor o de mecánico en el backend. */
    data class NeedsProfile(val session: Session) : AppState
    data class LoggedIn(val session: Session) : AppState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    sessions: SessionRepository,
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val profileChecks = MutableStateFlow(0)

    val state: StateFlow<AppState> = combine(
        // Solo cambia la pantalla si cambia la cuenta, el rol o la verificación, no cada vez que rota el token.
        sessions.observeSession().distinctUntilChanged { a, b ->
            a?.userId == b?.userId && a?.role == b?.role && a?.verification == b?.verification
        },
        profileChecks,
    ) { session, _ -> session }
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(AppState.LoggedOut)
            } else {
                flow {
                    emit(AppState.Loading)
                    emit(
                        when (profiles.check(session)) {
                            ProfileRepository.Check.MISSING -> AppState.NeedsProfile(session)
                            // Sin red se entra igual: offline first.
                            else -> AppState.LoggedIn(session)
                        },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState.Loading)

    fun profileCreated() {
        profileChecks.value++
    }

    /** El token nuevo trae el estado de verificación actual; si ya lo aprobaron, cambia la pantalla sola. */
    fun refreshVerification() {
        viewModelScope.launch { auth.refreshSession() }
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

/** Qué se muestra lo decide la sesión que devolvió el servidor: rol y estado de verificación. */
@Composable
fun HelperApp(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                AppState.Loading -> Unit
                AppState.LoggedOut -> LoginScreen()
                is AppState.NeedsProfile -> ProfileScreen(
                    role = current.session.role,
                    onDone = viewModel::profileCreated,
                    onLogout = viewModel::logout,
                )
                is AppState.LoggedIn -> when (current.session.role) {
                    Role.RIDER -> RiderScreen()
                    Role.MECHANIC -> if (current.session.verification == VerificationStatus.APPROVED) {
                        MechanicScreen()
                    } else {
                        PendingVerificationScreen(
                            rejected = current.session.verification == VerificationStatus.REJECTED,
                            onCheckAgain = viewModel::refreshVerification,
                            onLogout = viewModel::logout,
                        )
                    }
                    // La administración (aprobar mecánicos) se hace desde la consola web, no desde la app.
                    Role.ADMIN -> Column(Modifier.padding(24.dp)) {
                        Text("Las cuentas de administración se usan desde la consola web.")
                        TextButton(onClick = viewModel::logout) { Text("Salir") }
                    }
                }
            }
        }
    }
}
