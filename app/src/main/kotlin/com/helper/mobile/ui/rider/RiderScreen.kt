@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.helper.mobile.ui.rider

import com.helper.mobile.ui.common.asEta
import com.helper.mobile.ui.common.PinsMap
import com.helper.mobile.ui.common.MapPin
import com.helper.mobile.domain.model.GeoPoint
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.Context
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.helper.mobile.domain.model.StatusChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.helper.mobile.domain.policy.PendingRequestPolicy
import com.helper.mobile.domain.usecase.ObserveAssistanceUseCase.Tracking
import com.helper.mobile.ui.common.ErrorCard
import com.helper.mobile.ui.common.OfflineBanner
import com.helper.mobile.ui.common.PickLocationMap
import com.helper.mobile.ui.common.asDistance
import com.helper.mobile.ui.common.asHour
import com.helper.mobile.ui.common.label

@Composable
fun RiderScreen(onAddVehicle: () -> Unit, viewModel: RiderViewModel = hiltViewModel()) {
    val tracking by viewModel.tracking.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val showingHistory by viewModel.showingHistory.collectAsStateWithLifecycle()
    val canRetry by viewModel.canRetry.collectAsStateWithLifecycle()

    // Solo el contenido se desplaza: la acción principal queda fija abajo, al alcance del pulgar.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            val title = when {
                tracking == null -> "Pedir ayuda"
                showingHistory -> "Historial"
                else -> "Tu pedido"
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        if (!online && tracking == null) {
            OfflineBanner("Sin conexión. Si pedís ayuda ahora, el pedido queda guardado pero todavía no se envía.")
        }
        message?.let {
            ErrorCard(it, onDismiss = viewModel::dismissMessage, onRetry = if (canRetry) viewModel::retryLast else null)
        }
        val current = tracking
        when {
            current == null -> RequestForm(viewModel, onAddVehicle)
            showingHistory -> HistoryContent(current, viewModel)
            else -> TrackingContent(current, online, viewModel)
        }
    }
}

@Composable
private fun ColumnScope.RequestForm(viewModel: RiderViewModel, onAddVehicle: () -> Unit) {
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val problems by viewModel.problems.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val picking by viewModel.pickingLocation.collectAsStateWithLifecycle()
    val picked by viewModel.pickedLocation.collectAsStateWithLifecycle()

    // E2: sin vehículo no se puede pedir ayuda; la pantalla vacía lleva a registrarlo.
    if (vehicles.isEmpty()) {
        ScrollingBody { EmptyVehicles(onAddVehicle) }
        return
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.requestHelp()
        viewModel.startNearbyUpdates()
    }

    ScrollingBody {
    if (picking) {
        Text("Marcá dónde estás", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tocá el mapa en el lugar donde quedaste. El mecánico va a ir hasta ahí.",
            style = MaterialTheme.typography.bodyMedium,
        )
        PickLocationMap(selected = picked, onPick = viewModel::pickLocation)
        TextButton(onClick = viewModel::cancelPickingLocation) { Text("Usar el GPS del teléfono") }
    } else {
        NearbyMap(viewModel)
    }

    Text("¿Con qué vehículo?", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        vehicles.forEach { vehicle ->
            FilterChip(
                selected = form.vehicleId == vehicle.id,
                onClick = { viewModel.selectVehicle(vehicle.id) },
                label = { Text("${vehicle.type.label()} · ${vehicle.brand} ${vehicle.model}") },
            )
        }
    }
    OutlinedButton(onClick = onAddVehicle) { Text("+ Agregar vehículo") }

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
    }
    Button(
        onClick = {
            val manual = picked
            if (picking && manual != null) {
                viewModel.requestHelpAt(manual)
            } else {
                val wanted = buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissions.launch(wanted.toTypedArray())
            }
        },
        enabled = form.vehicleId != null && form.problemCode != null && !form.sending && (!picking || picked != null),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            when {
                form.sending -> "Pidiendo…"
                picking && picked == null -> "Marcá tu ubicación en el mapa"
                picking -> "Pedir mecánico acá"
                else -> "Pedir mecánico"
            },
        )
    }
}

/** Mapa con el repartidor y los mecánicos disponibles cerca. Pide el permiso de ubicación si todavía no lo tiene. */
@Composable
private fun NearbyMap(viewModel: RiderViewModel, center: GeoPoint? = null) {
    val context = LocalContext.current
    val here by viewModel.myLocation.collectAsStateWithLifecycle()
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasLocationPermission(context)
    }
    LaunchedEffect(granted) { if (granted) viewModel.startNearbyUpdates() }

    if (!granted) {
        OutlinedButton(
            onClick = {
                askLocation.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Ver mecánicos cerca en el mapa") }
        if (center == null) {
            TextButton(onClick = viewModel::startPickingLocation, modifier = Modifier.fillMaxWidth()) {
                Text("No quiero compartir mi ubicación: marcarla en el mapa")
            }
        }
        return
    }
    val me = center ?: here
    if (me == null) {
        Text("Buscando tu ubicación…", style = MaterialTheme.typography.bodySmall)
        return
    }
    val pins = listOf(MapPin(me, "Vos", isMain = true)) +
        nearby.map { MapPin(it.location, "${it.name} · a ${it.distanceMeters.asDistance()}") }
    val summary = when (nearby.size) {
        0 -> "No hay mecánicos disponibles cerca por ahora."
        1 -> "1 mecánico disponible cerca."
        else -> "${nearby.size} mecánicos disponibles cerca."
    }
    PinsMap(pins, description = "Mapa: vos y $summary")
    Text(summary, style = MaterialTheme.typography.bodySmall)
}

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun ColumnScope.TrackingContent(tracking: Tracking, online: Boolean, viewModel: RiderViewModel) {
    val assistance = tracking.assistance
    val context = LocalContext.current
    when {
        tracking.isExpired -> {
            ScrollingBody {
                Text("Tu pedido venció sin enviarse", style = MaterialTheme.typography.headlineSmall)
                Text("Pasaron 15 minutos sin conexión, así que no lo mandamos. ¿Todavía necesitás ayuda?")
            }
            Button(onClick = viewModel::dismissTracking, modifier = Modifier.fillMaxWidth()) { Text("Pedir de nuevo") }
        }
        assistance.status == AssistanceStatus.NOT_SENT && assistance.needsLocationConfirmation -> {
            ScrollingBody {
                Text("Confirmá dónde estás", style = MaterialTheme.typography.headlineSmall)
                Text("Te moviste desde que pediste ayuda. Antes de enviar el pedido confirmá tu ubicación actual.")
            }
            Button(onClick = viewModel::confirmCurrentLocation, modifier = Modifier.fillMaxWidth()) {
                Text("Estoy acá, enviar")
            }
            CancelButton(onClick = viewModel::cancel)
        }
        assistance.status == AssistanceStatus.NOT_SENT -> {
            ScrollingBody {
                // Lo más importante: que el repartidor sepa que todavía NO pidió ayuda.
                Text("Todavía no pediste ayuda", style = MaterialTheme.typography.headlineSmall)
                val deadline = assistance.confirmedAt.plus(PendingRequestPolicy.EXPIRY).asHour()
                Text("No hay conexión. Guardamos el pedido y lo enviamos solo cuando vuelva la señal, hasta las $deadline.")
            }
            Button(onClick = viewModel::retryNow, modifier = Modifier.fillMaxWidth()) { Text("Reintentar ahora") }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Llamar por teléfono") }
            CancelButton(onClick = viewModel::cancel)
        }
        else -> RemoteTracking(tracking, online, viewModel)
    }
}

@Composable
private fun ColumnScope.RemoteTracking(tracking: Tracking, online: Boolean, viewModel: RiderViewModel) {
    val assistance = tracking.assistance
    ScrollingBody {
    AssistanceStepper(assistance.status)
    Text(assistance.status.headline(), style = MaterialTheme.typography.headlineSmall)
    if (!online || tracking.isStale) {
        val since = tracking.lastSyncedAt?.asHour() ?: "—"
        OfflineBanner("Sin conexión. Este es el último estado conocido, de las $since.")
    } else {
        tracking.lastSyncedAt?.let { Text("Actualizado a las ${it.asHour()}", style = MaterialTheme.typography.bodySmall) }
    }
    when (assistance.status) {
        AssistanceStatus.REQUESTED -> {
            Text("Les avisamos a los mecánicos cercanos. El primero que acepte va hacia vos.")
            NearbyMap(viewModel, center = assistance.location)
        }
        AssistanceStatus.MATCHED, AssistanceStatus.IN_PROGRESS -> assistance.mechanic?.let { mechanic ->
            val name = mechanic.name.ifBlank { "Tu mecánico" }
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Initials(name)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        val route by viewModel.mechanicRoute.collectAsStateWithLifecycle()
                        // La ruta se recalcula mientras viene, así que su tiempo es más actual que el de la asignación.
                        (route?.durationMinutes ?: mechanic.etaMinutes)?.let {
                            Text("Llega en unos ${it.asEta()}", style = MaterialTheme.typography.bodyLarge)
                        }
                        val details = listOfNotNull(
                            (route?.distanceMeters ?: mechanic.distanceMeters)?.let { "a ${it.asDistance()}" },
                            mechanic.rating?.let { "%.1f de 5".format(it) },
                        ).joinToString(" · ")
                        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            val route by viewModel.mechanicRoute.collectAsStateWithLifecycle()
            val pins = listOfNotNull(
                MapPin(assistance.location, "Vos", isMain = true),
                mechanic.location?.let { MapPin(it, name) },
            )
            PinsMap(pins, description = "Mapa: vos, $name y el camino", route = route?.points.orEmpty())
        }
        AssistanceStatus.UNMATCHED -> Text("Ningún mecánico cercano aceptó a tiempo. Podés volver a pedir.")
        AssistanceStatus.COMPLETED -> Text("¡Listo! El trabajo quedó terminado.")
        AssistanceStatus.CANCELLED -> Text("El pedido quedó cancelado.")
        else -> Unit
    }
    }
    val onTheWay = assistance.status == AssistanceStatus.MATCHED || assistance.status == AssistanceStatus.IN_PROGRESS
    val mechanic = assistance.mechanic
    val phone = mechanic?.phone
    if (onTheWay && mechanic != null && phone != null) {
        val context = LocalContext.current
        val firstName = mechanic.name.ifBlank { "tu mecánico" }.substringBefore(' ')
        OutlinedButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Llamar a $firstName") }
    }
    if (assistance.status != AssistanceStatus.REQUESTED) {
        TextButton(onClick = viewModel::openHistory, modifier = Modifier.fillMaxWidth()) { Text("Ver historial del pedido") }
    }
    if (assistance.status.isFinal) {
        Button(onClick = viewModel::dismissTracking, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
    } else if (assistance.status == AssistanceStatus.REQUESTED || assistance.status == AssistanceStatus.MATCHED) {
        CancelButton(
            onClick = viewModel::cancel,
            enabled = online,
            text = if (online) "Cancelar pedido" else "Para cancelar necesitás conexión",
        )
    }
}

/** Título grande del seguimiento, como en los bocetos 05 y 06. */
private fun AssistanceStatus.headline(): String = when (this) {
    AssistanceStatus.REQUESTED -> "Esperando que un mecánico acepte"
    AssistanceStatus.MATCHED -> "Un mecánico aceptó y va en camino"
    AssistanceStatus.IN_PROGRESS -> "El mecánico está trabajando"
    else -> label()
}

/** Acción destructiva: texto rojo, separada de la acción principal. */
@Composable
private fun CancelButton(onClick: () -> Unit, enabled: Boolean = true, text: String = "Cancelar pedido") {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text) }
}

/** Pedido → Aceptado → En curso → Terminado: el stepper de los bocetos 05 y 06. */
@Composable
private fun AssistanceStepper(status: AssistanceStatus) {
    val steps = listOf("Pedido", "Aceptado", "En curso", "Terminado")
    val reached = when (status) {
        AssistanceStatus.REQUESTED, AssistanceStatus.UNMATCHED -> 0
        AssistanceStatus.MATCHED -> 1
        AssistanceStatus.IN_PROGRESS -> 2
        AssistanceStatus.COMPLETED -> 3
        else -> -1
    }
    if (reached < 0) return
    val primary = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Paso actual: ${steps[reached]}" },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        steps.forEachIndexed { index, step ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (index <= reached) primary else idle),
                )
                Text(
                    step,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == reached) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Círculo con las iniciales del mecánico, como en el boceto 06. */
@Composable
private fun Initials(name: String) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials.ifEmpty { "M" }, style = MaterialTheme.typography.titleSmall)
    }
}

/** 07 · Historial del pedido: resumen y la hora de cada cambio de estado. Se ve también sin conexión. */
@Composable
private fun ColumnScope.HistoryContent(tracking: Tracking, viewModel: RiderViewModel) {
    val assistance = tracking.assistance
    val history by viewModel.history.collectAsStateWithLifecycle()
    ScrollingBody {
    TextButton(onClick = viewModel::closeHistory) { Text("← Volver al pedido") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(viewModel.problemLabel(assistance.problemCode), style = MaterialTheme.typography.titleMedium)
                Text(
                    assistance.status.label(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            val summary = listOfNotNull(
                viewModel.vehicleName(assistance.vehicleId),
                assistance.mechanic?.name?.takeIf { it.isNotBlank() },
                assistance.mechanic?.distanceMeters?.asDistance(),
            ).joinToString(" · ")
            if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodySmall)
        }
    }
    Text("Estados", style = MaterialTheme.typography.titleSmall)
    if (history.isEmpty()) {
        Text(
            "Pedido creado a las ${assistance.confirmedAt.asHour()}. El resto de los cambios aparece cuando hay conexión.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    val mechanicName = assistance.mechanic?.name?.takeIf { it.isNotBlank() }
    history.forEach { change ->
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (change == history.last()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        ),
                )
                Text(change.description(mechanicName), style = MaterialTheme.typography.bodyMedium)
            }
            Text(change.at.asHour(), style = MaterialTheme.typography.bodyMedium)
        }
    }
    }
    if (assistance.status.isFinal) {
        OutlinedButton(onClick = viewModel::dismissTracking, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
    }
}

/**
 * Lo que se desplaza de cada pantalla. Ocupa todo el alto libre, así los botones que van después quedan fijos
 * abajo, al alcance del pulgar (decisión de la preentrega).
 */
@Composable
private fun ColumnScope.ScrollingBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

private fun StatusChange.description(mechanicName: String?): String = when (to) {
    AssistanceStatus.REQUESTED -> if (from == null) "Pedido creado" else "Pedido publicado de nuevo"
    AssistanceStatus.MATCHED -> "${mechanicName ?: "Un mecánico"} aceptó el pedido"
    AssistanceStatus.IN_PROGRESS -> "Trabajo iniciado"
    AssistanceStatus.COMPLETED -> "Trabajo terminado"
    AssistanceStatus.CANCELLED -> "Pedido cancelado"
    AssistanceStatus.UNMATCHED -> "Ningún mecánico lo tomó a tiempo"
    else -> to.label()
}
