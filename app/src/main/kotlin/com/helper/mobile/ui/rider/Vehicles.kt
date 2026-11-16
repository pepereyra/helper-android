@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.helper.mobile.ui.rider

import com.helper.mobile.ui.common.ScreenTitle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helper.mobile.domain.model.Vehicle
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.ui.common.ErrorCard
import com.helper.mobile.ui.common.label

/** 02 · Mis vehículos. Se lee de Room: se ve también sin conexión. */
@Composable
fun VehiclesScreen(onAddVehicle: () -> Unit, viewModel: RiderViewModel = hiltViewModel()) {
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Mis vehículos")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vehicles.isEmpty()) {
                EmptyVehicles(onAddVehicle)
            } else {
                vehicles.forEach { VehicleRow(it) }
            }
        }
        if (vehicles.isNotEmpty()) {
            Button(onClick = onAddVehicle, modifier = Modifier.fillMaxWidth()) { Text("+ Agregar vehículo") }
        }
    }
}

/** E2 · Vacío: sin vehículos no se puede pedir ayuda, así que la acción lleva a registrar uno. */
@Composable
fun EmptyVehicles(onAddVehicle: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Todavía no registraste ningún vehículo", style = MaterialTheme.typography.titleMedium)
            Text(
                "Registrá tu bici, bici eléctrica o moto una sola vez. Después pedís ayuda con un toque.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAddVehicle, modifier = Modifier.fillMaxWidth()) { Text("Registrar mi vehículo") }
        }
    }
}

@Composable
private fun VehicleRow(vehicle: Vehicle) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Text(vehicle.type.label().take(1), style = MaterialTheme.typography.titleSmall) }
            Column {
                Text("${vehicle.brand} ${vehicle.model}", style = MaterialTheme.typography.titleMedium)
                val details = listOfNotNull(vehicle.type.label(), vehicle.color, vehicle.plate).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 03 · Agregar vehículo, en pantalla completa con flecha atrás y la acción principal abajo. */
@Composable
fun AddVehicleScreen(onDone: () -> Unit, onBack: () -> Unit, viewModel: RiderViewModel = hiltViewModel()) {
    var type by rememberSaveable { mutableStateOf(VehicleType.BICYCLE) }
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("") }
    var plate by rememberSaveable { mutableStateOf("") }
    val error by viewModel.vehicleError.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Nuevo vehículo", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Tipo", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleType.entries.forEach {
                    FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it.label()) })
                }
            }
            OutlinedTextField(brand, { brand = it }, label = { Text("Marca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text("Modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                color,
                { color = it },
                label = { Text("Color (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (type == VehicleType.MOTORCYCLE) {
                OutlinedTextField(plate, { plate = it }, label = { Text("Patente") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            error?.let { ErrorCard(it, onDismiss = viewModel::dismissVehicleError) }
            Spacer(Modifier.size(4.dp))
        }
        Button(
            onClick = { viewModel.addVehicle(type, brand, model, color, plate, onDone) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar vehículo") }
    }
}
