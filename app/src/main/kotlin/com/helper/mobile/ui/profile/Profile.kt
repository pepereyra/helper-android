@file:OptIn(ExperimentalLayoutApi::class)

package com.helper.mobile.ui.profile

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.mobile.domain.model.Role
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.repository.LocationProvider
import com.helper.mobile.domain.repository.ProblemCatalogRepository
import com.helper.mobile.domain.repository.ProfileRepository
import com.helper.mobile.domain.repository.RemoteActionResult
import com.helper.mobile.ui.common.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SkillOption(val code: String, val label: String)

data class ProfileUiState(
    val name: String = "",
    val phone: String = "",
    val skillOptions: List<SkillOption> = emptyList(),
    val skills: Set<String> = emptySet(),
    val vehicleTypes: Set<VehicleType> = setOf(VehicleType.BICYCLE),
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val location: LocationProvider,
    catalog: ProblemCatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    init {
        viewModelScope.launch {
            // Cada problema del catálogo pide una habilidad; el mecánico marca las que tiene.
            val options = catalog.problems().distinctBy { it.requiredSkill }.map { SkillOption(it.requiredSkill, it.label) }
            _state.update { it.copy(skillOptions = options) }
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun onPhone(value: String) = _state.update { it.copy(phone = value, error = null) }
    fun toggleSkill(code: String) = _state.update { it.copy(skills = it.skills.toggle(code), error = null) }
    fun toggleVehicle(type: VehicleType) = _state.update { it.copy(vehicleTypes = it.vehicleTypes.toggle(type), error = null) }

    fun save(role: Role, onDone: () -> Unit) {
        val current = _state.value
        val error = when {
            current.name.isBlank() -> "Ingresá tu nombre."
            role == Role.RIDER && current.phone.isBlank() -> "Ingresá un teléfono para que el mecánico te pueda llamar."
            role == Role.MECHANIC && current.skills.isEmpty() -> "Elegí al menos un arreglo que hagas."
            role == Role.MECHANIC && current.vehicleTypes.isEmpty() -> "Elegí al menos un tipo de vehículo."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = if (role == Role.RIDER) {
                profiles.createRider(current.name, current.phone)
            } else {
                // La ubicación del taller sirve para ofrecerle pedidos cercanos.
                val here = location.currentLocation()
                if (here == null) {
                    RemoteActionResult.Rejected("Necesitamos tu ubicación para mostrarte pedidos cercanos.")
                } else {
                    profiles.createMechanic(current.name, here, current.skills, current.vehicleTypes)
                }
            }
            when (result) {
                RemoteActionResult.Done -> onDone()
                RemoteActionResult.NoConnection -> _state.update { it.copy(error = "No hay conexión. Probá de nuevo.") }
                is RemoteActionResult.Rejected -> _state.update { it.copy(error = result.reason) }
            }
            _state.update { it.copy(saving = false) }
        }
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
}

/** Después de crear la cuenta: el perfil de repartidor o de mecánico en el backend. */
@Composable
fun ProfileScreen(role: Role, onDone: () -> Unit, onLogout: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.save(role, onDone)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Completá tu perfil", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onName,
            label = { Text(if (role == Role.MECHANIC) "Nombre o nombre del taller" else "Nombre y apellido") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (role == Role.RIDER) {
            OutlinedTextField(
                value = state.phone,
                onValueChange = viewModel::onPhone,
                label = { Text("Teléfono") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("¿Qué arreglos hacés?", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.skillOptions.forEach { option ->
                    FilterChip(
                        selected = option.code in state.skills,
                        onClick = { viewModel.toggleSkill(option.code) },
                        label = { Text(option.label) },
                    )
                }
            }
            Text("¿Qué vehículos atendés?", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleType.entries.forEach { type ->
                    FilterChip(
                        selected = type in state.vehicleTypes,
                        onClick = { viewModel.toggleVehicle(type) },
                        label = { Text(type.label()) },
                    )
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                if (role == Role.MECHANIC) {
                    permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else {
                    viewModel.save(role, onDone)
                }
            },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.saving) "Guardando…" else "Guardar") }
        TextButton(onClick = onLogout) { Text("Salir") }
    }
}
