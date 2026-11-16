@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.helper.mobile.ui.home

import androidx.compose.foundation.layout.isImeVisible
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.mobile.domain.model.Role
import com.helper.mobile.domain.repository.AuthRepository
import com.helper.mobile.domain.repository.ProfileRepository
import com.helper.mobile.domain.repository.ProfileSummary
import com.helper.mobile.domain.repository.SessionRepository
import com.helper.mobile.ui.mechanic.MechanicJobScreen
import com.helper.mobile.ui.mechanic.MechanicOffersScreen
import com.helper.mobile.ui.mechanic.MechanicViewModel
import com.helper.mobile.ui.rider.AddVehicleScreen
import com.helper.mobile.ui.rider.RiderScreen
import com.helper.mobile.ui.rider.VehiclesScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class Tab(val label: String, val glyph: String)

/** Barra inferior de Material 3 con tres destinos por rol, como en la preentrega. */
@Composable
private fun HomeBar(tabs: List<Tab>, selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
        tabs.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                // El ícono es decorativo: TalkBack lee la etiqueta.
                icon = { Text(tab.glyph, modifier = Modifier.clearAndSetSemantics { }) },
                label = { Text(tab.label) },
            )
        }
    }
}

private val RIDER_TABS = listOf(Tab("Ayuda", "🛟"), Tab("Mis vehículos", "🛵"), Tab("Perfil", "👤"))
private val MECHANIC_TABS = listOf(Tab("Pedidos", "📍"), Tab("Trabajo", "🔧"), Tab("Perfil", "👤"))

/** Repartidor: Ayuda, Mis vehículos y Perfil. "Agregar vehículo" se abre en pantalla completa, sin la barra. */
@Composable
fun RiderHome() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addingVehicle by rememberSaveable { mutableStateOf(false) }
    if (addingVehicle) {
        BackHandler { addingVehicle = false }
        AddVehicleScreen(onDone = { addingVehicle = false }, onBack = { addingVehicle = false })
        return
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // Con el teclado abierto se oculta la barra: el espacio queda para el campo y el botón principal.
        bottomBar = { if (!WindowInsets.isImeVisible) HomeBar(RIDER_TABS, tab) { tab = it } },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> RiderScreen(onAddVehicle = { addingVehicle = true })
                1 -> VehiclesScreen(onAddVehicle = { addingVehicle = true })
                else -> AccountScreen()
            }
        }
    }
}

/** Mecánico: Pedidos, Trabajo y Perfil. Al tomar un pedido pasa solo a la pestaña Trabajo. */
@Composable
fun MechanicHome(viewModel: MechanicViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val job by viewModel.job.collectAsStateWithLifecycle()
    LaunchedEffect(job?.assistanceId) {
        if (job != null) tab = 1
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (!WindowInsets.isImeVisible) HomeBar(MECHANIC_TABS, tab) { tab = it } },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> MechanicOffersScreen(onOpenJob = { tab = 1 }, viewModel = viewModel)
                1 -> MechanicJobScreen(onBackToOffers = { tab = 0 }, viewModel = viewModel)
                else -> AccountScreen()
            }
        }
    }
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val profiles: ProfileRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    val role = MutableStateFlow<Role?>(null)
    val summary = MutableStateFlow<ProfileSummary?>(null)
    val loading = MutableStateFlow(true)

    /** Se llama cada vez que se abre la pestaña: la cuenta pudo cambiar desde la última vez. */
    fun load() {
        viewModelScope.launch {
            val session = sessions.currentSession()
            role.value = session?.role
            summary.value = session?.let { profiles.summary(it) }
            loading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

/** Perfil: los datos de la cuenta y cerrar sesión (que borra los datos guardados en el teléfono, RNF06). */
@Composable
fun AccountScreen(viewModel: AccountViewModel = hiltViewModel()) {
    val role by viewModel.role.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Perfil", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val current = summary
                when {
                    current != null -> {
                        Text(current.name, style = MaterialTheme.typography.titleMedium)
                        current.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        current.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    loading -> Text("Cargando tus datos…")
                    else -> Text("No pudimos traer tus datos. Revisá la conexión.")
                }
                Text(
                    when (role) {
                        Role.RIDER -> "Cuenta de repartidor"
                        Role.MECHANIC -> "Cuenta de mecánico"
                        Role.ADMIN -> "Cuenta de administración"
                        null -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
        Text(
            "Al cerrar sesión se borran los datos guardados en este teléfono.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
