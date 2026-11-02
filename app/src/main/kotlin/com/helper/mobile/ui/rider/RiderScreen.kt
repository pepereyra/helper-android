@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.helper.mobile.ui.rider

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.policy.PendingRequestPolicy
import com.helper.mobile.domain.usecase.ObserveAssistanceUseCase.Tracking
import com.helper.mobile.ui.common.OfflineBanner
import com.helper.mobile.ui.common.asDistance
import com.helper.mobile.ui.common.asHour
import com.helper.mobile.ui.common.label

@Composable
fun RiderScreen(viewModel: RiderViewModel = hiltViewModel()) {
    val tracking by viewModel.tracking.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Pedir ayuda", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = viewModel::logout) { Text("Salir") }
        }
        if (!online && tracking == null) {
            OfflineBanner("Sin conexión. Si pedís ayuda ahora, el pedido queda guardado pero todavía no se envía.")
        }
        message?.let {
            Card(onClick = viewModel::dismissMessage) { Text(it, modifier = Modifier.padding(12.dp)) }
        }
        val current = tracking
        if (current == null) RequestForm(viewModel) else TrackingContent(current, online, viewModel)
    }
}

@Composable
private fun RequestForm(viewModel: RiderViewModel) {
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val problems by viewModel.problems.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    var addingVehicle by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.requestHelp()
    }

    Text("¿Con qué vehículo?", style = MaterialTheme.typography.titleMedium)
    if (vehicles.isEmpty()) Text("Registrá tu vehículo para pedir ayuda.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        vehicles.forEach { vehicle ->
            FilterChip(
                selected = form.vehicleId == vehicle.id,
                onClick = { viewModel.selectVehicle(vehicle.id) },
                label = { Text("${vehicle.type.label()} · ${vehicle.brand} ${vehicle.model}") },
            )
        }
    }
    OutlinedButton(onClick = { addingVehicle = true }) { Text("+ Agregar vehículo") }

    if (problems.isNotEmpty()) {
        Text("¿Qué pasó?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            problems.forEach { problem ->
                FilterChip(
                    selected = form.problemCode == problem.code,
                    onClick = { viewModel.selectProblem(problem.code) },
                    label = { Text(problem.label) },
                )
            }
        }
        OutlinedTextField(
            value = form.description,
            onValueChange = viewModel::describe,
            label = { Text("Contanos un poco más (opcional)") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Button(
        onClick = {
            val wanted = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissions.launch(wanted.toTypedArray())
        },
        enabled = form.vehicleId != null && form.problemCode != null && !form.sending,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (form.sending) "Pidiendo…" else "Pedir mecánico") }

    if (addingVehicle) {
        AddVehicleDialog(
            onDismiss = { addingVehicle = false },
            onSave = { type, brand, model, color, plate ->
                viewModel.addVehicle(type, brand, model, color, plate) { addingVehicle = false }
            },
        )
    }
}

@Composable
private fun TrackingContent(tracking: Tracking, online: Boolean, viewModel: RiderViewModel) {
    val assistance = tracking.assistance
    val context = LocalContext.current
    when {
        tracking.isExpired -> {
            Text("Tu pedido venció sin enviarse", style = MaterialTheme.typography.headlineSmall)
            Text("Pasaron 15 minutos sin conexión, así que no lo mandamos. ¿Todavía necesitás ayuda?")
            Button(onClick = viewModel::dismissTracking, modifier = Modifier.fillMaxWidth()) { Text("Pedir de nuevo") }
        }
        assistance.status == AssistanceStatus.NOT_SENT && assistance.needsLocationConfirmation -> {
            Text("Confirmá dónde estás", style = MaterialTheme.typography.headlineSmall)
            Text("Te moviste desde que pediste ayuda. Antes de enviar el pedido confirmá tu ubicación actual.")
            Button(onClick = viewModel::confirmCurrentLocation, modifier = Modifier.fillMaxWidth()) {
                Text("Estoy acá, enviar")
            }
            TextButton(onClick = viewModel::cancel) { Text("Cancelar pedido") }
        }
        assistance.status == AssistanceStatus.NOT_SENT -> {
            // Lo más importante: que el repartidor sepa que todavía NO pidió ayuda.
            Text("Todavía no pediste ayuda", style = MaterialTheme.typography.headlineSmall)
            val deadline = assistance.confirmedAt.plus(PendingRequestPolicy.EXPIRY).asHour()
            Text("No hay conexión. Guardamos el pedido y lo enviamos solo cuando vuelva la señal, hasta las $deadline.")
            Button(onClick = viewModel::retryNow, modifier = Modifier.fillMaxWidth()) { Text("Reintentar ahora") }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Llamar por teléfono") }
            TextButton(onClick = viewModel::cancel) { Text("Cancelar pedido") }
        }
        else -> RemoteTracking(tracking, online, viewModel)
    }
}

@Composable
private fun RemoteTracking(tracking: Tracking, online: Boolean, viewModel: RiderViewModel) {
    val assistance = tracking.assistance
    Text(assistance.status.label(), style = MaterialTheme.typography.headlineSmall)
    if (!online || tracking.isStale) {
        val since = tracking.lastSyncedAt?.asHour() ?: "—"
        OfflineBanner("Sin conexión. Este es el último estado conocido, de las $since.")
    } else {
        tracking.lastSyncedAt?.let { Text("Actualizado a las ${it.asHour()}", style = MaterialTheme.typography.bodySmall) }
    }
    when (assistance.status) {
        AssistanceStatus.REQUESTED -> Text("Les avisamos a los mecánicos cercanos. El primero que acepte va hacia vos.")
        AssistanceStatus.MATCHED, AssistanceStatus.IN_PROGRESS -> assistance.mechanic?.let { mechanic ->
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(mechanic.name.ifBlank { "Tu mecánico" }, style = MaterialTheme.typography.titleMedium)
                    val details = listOfNotNull(
                        mechanic.rating?.let { "%.1f de 5".format(it) },
                        mechanic.distanceMeters?.let { "a ${it.asDistance()}" },
                    ).joinToString(" · ")
                    if (details.isNotEmpty()) Text(details)
                }
            }
        }
        AssistanceStatus.UNMATCHED -> Text("Ningún mecánico cercano aceptó a tiempo. Podés volver a pedir.")
        AssistanceStatus.COMPLETED -> Text("¡Listo! El trabajo quedó terminado.")
        AssistanceStatus.CANCELLED -> Text("El pedido quedó cancelado.")
        else -> Unit
    }
    if (assistance.status.isFinal) {
        Button(onClick = viewModel::dismissTracking, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
    } else if (assistance.status == AssistanceStatus.REQUESTED || assistance.status == AssistanceStatus.MATCHED) {
        TextButton(onClick = viewModel::cancel, enabled = online) {
            Text(if (online) "Cancelar pedido" else "Para cancelar necesitás conexión")
        }
    }
}

@Composable
private fun AddVehicleDialog(
    onDismiss: () -> Unit,
    onSave: (VehicleType, String, String, String?, String?) -> Unit,
) {
    var type by remember { mutableStateOf(VehicleType.BICYCLE) }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo vehículo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VehicleType.entries.forEach {
                        FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it.label()) })
                    }
                }
                OutlinedTextField(brand, { brand = it }, label = { Text("Marca") }, singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text("Modelo") }, singleLine = true)
                OutlinedTextField(color, { color = it }, label = { Text("Color (opcional)") }, singleLine = true)
                if (type == VehicleType.MOTORCYCLE) {
                    OutlinedTextField(plate, { plate = it }, label = { Text("Patente") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(type, brand, model, color, plate) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
