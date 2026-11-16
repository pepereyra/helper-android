@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.helper.mobile.ui.mechanic

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.Job
import com.helper.mobile.domain.repository.RouteRepository
import com.helper.mobile.domain.model.progress
import com.helper.mobile.domain.model.isOffRoute
import com.helper.mobile.domain.model.Route
import com.helper.mobile.domain.model.NavigationProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.helper.mobile.ui.common.asEta
import com.helper.mobile.ui.common.PinsMap
import com.helper.mobile.ui.common.MapPin
import com.helper.mobile.domain.model.GeoPoint
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.mobile.data.network.ConnectivityObserver
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.MechanicJob
import com.helper.mobile.domain.model.Offer
import com.helper.mobile.domain.repository.ActiveJobResult
import com.helper.mobile.domain.repository.JobActionResult
import com.helper.mobile.domain.repository.JobRepository
import com.helper.mobile.domain.usecase.AdvanceJobUseCase
import com.helper.mobile.domain.repository.AcceptResult
import com.helper.mobile.domain.repository.AuthRepository
import com.helper.mobile.domain.repository.AvailabilityResult
import com.helper.mobile.domain.repository.LocationProvider
import com.helper.mobile.domain.repository.MechanicStatusRepository
import com.helper.mobile.domain.repository.OfferRepository
import com.helper.mobile.domain.usecase.TakeOfferUseCase
import com.helper.mobile.ui.common.OfflineBanner
import com.helper.mobile.ui.common.asDistance
import com.helper.mobile.ui.common.asHour
import com.helper.mobile.ui.common.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MechanicViewModel @Inject constructor(
    private val offerRepository: OfferRepository,
    private val takeOffer: TakeOfferUseCase,
    private val auth: AuthRepository,
    private val status: MechanicStatusRepository,
    private val location: LocationProvider,
    private val routes: RouteRepository,
    private val jobs: JobRepository,
    private val advanceJob: AdvanceJobUseCase,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    val available = MutableStateFlow(false)
    val changingAvailability = MutableStateFlow(false)

    val offers: StateFlow<List<Offer>> =
        offerRepository.observeOffers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val online: StateFlow<Boolean> =
        connectivity.isOnline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val message = MutableStateFlow<String?>(null)
    /** El pedido que tomó: se recupera del servidor al abrir la app, así no se pierde si se cierra. */
    val job = MutableStateFlow<MechanicJob?>(null)
    val advancing = MutableStateFlow(false)

    /** Dónde estaba el mecánico al ponerse disponible; es el otro pin del mapa de cada pedido. */
    val myLocation = MutableStateFlow<GeoPoint?>(null)

    /** Ruta hasta el repartidor del pedido tomado y cómo viene el mecánico en ella. */
    val route = MutableStateFlow<Route?>(null)
    val progress = MutableStateFlow<NavigationProgress?>(null)
    private var navigation: Job? = null

    init {
        // Las ofertas se refrescan cada 10 segundos mientras la pantalla está abierta. Si hay un trabajo abierto,
        // se revisa que siga activo: el repartidor pudo haberlo cancelado.
        viewModelScope.launch {
            while (isActive) {
                if (online.value) {
                    offerRepository.refresh()
                    syncJob()
                }
                delay(10_000)
            }
        }
    }

    private suspend fun syncJob() {
        val shown = job.value
        if (shown?.status == AssistanceStatus.COMPLETED) return
        val result = jobs.active()
        if (result !is ActiveJobResult.Known) return
        val current = result.job
        when {
            current == null && shown != null -> {
                stopNavigation()
                job.value = null
                message.value = "El pedido ya no está activo: puede que el repartidor lo haya cancelado."
            }
            current != null && (shown == null || shown.assistanceId != current.assistanceId || shown.status != current.status) -> {
                job.value = current
                if (current.status == AssistanceStatus.MATCHED) startNavigation(current) else stopNavigation()
            }
        }
    }

    fun take(offer: Offer) {
        viewModelScope.launch {
            message.value = when (takeOffer(offer.assistanceId)) {
                AcceptResult.Accepted -> {
                    val taken = MechanicJob(
                        assistanceId = offer.assistanceId,
                        problemLabel = offer.problemLabel,
                        vehicleType = offer.vehicleType,
                        location = offer.location,
                        status = AssistanceStatus.MATCHED,
                    )
                    job.value = taken
                    startNavigation(taken)
                    null
                }
                AcceptResult.AlreadyTaken -> "Otro mecánico ya lo tomó."
                AcceptResult.NotAllowed -> {
                    // El servidor no lo ve aprobado: un token nuevo trae el estado real y, si hace falta,
                    // la app vuelve a la pantalla de revisión.
                    auth.refreshSession()
                    "Tu cuenta todavía no está aprobada para tomar pedidos."
                }
                AcceptResult.NoConnection -> "Para tomar un pedido necesitás conexión."
                is AcceptResult.Failed -> "No se pudo tomar el pedido. Probá de nuevo."
            }
        }
    }

    /** Ponerse disponible manda la ubicación actual; el servidor responde 403 si la cuenta no está aprobada. */
    fun setAvailable(wanted: Boolean) {
        changingAvailability.value = true
        viewModelScope.launch {
            val here = location.currentLocation()
            message.value = if (here == null) {
                "Necesitamos tu ubicación para mostrarte pedidos cercanos."
            } else {
                when (val result = status.setAvailable(wanted, here)) {
                    AvailabilityResult.Done -> {
                        myLocation.value = here
                        available.value = wanted
                        null
                    }
                    AvailabilityResult.NotApproved -> {
                        auth.refreshSession()
                        "Tu cuenta todavía no está aprobada."
                    }
                    AvailabilityResult.NoConnection -> "Sin conexión. Probá de nuevo cuando vuelva la señal."
                    is AvailabilityResult.Rejected -> result.reason
                }
            }
            changingAvailability.value = false
        }
    }

    /**
     * Mientras va hacia el repartidor: cada 5 segundos lee el GPS y actualiza el próximo paso; cada 15 manda su
     * ubicación al servidor (así el repartidor lo ve moverse) y pide la ruta de nuevo si se desvió.
     */
    private fun startNavigation(job: MechanicJob) {
        val destination = job.location ?: return
        navigation?.cancel()
        navigation = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                val here = location.currentLocation()
                if (here != null) {
                    myLocation.value = here
                    val current = route.value
                    if (online.value && (current == null || current.isOffRoute(here) || (!current.byRoad && tick % 6 == 0))) {
                        routes.route(here, destination, job.vehicleType)?.let { route.value = it }
                    }
                    if (online.value && tick % 3 == 0) status.setAvailable(available.value, here)
                    progress.value = route.value?.progress(here, destination)
                    if (progress.value?.arrived == true) break
                }
                tick++
                delay(5_000)
            }
        }
    }

    private fun stopNavigation() {
        navigation?.cancel()
        navigation = null
        route.value = null
        progress.value = null
    }

    /** M2: "Llegué, empezar el trabajo" y después "Terminar el trabajo". Necesita conexión. */
    fun advance() {
        val current = job.value ?: return
        advancing.value = true
        viewModelScope.launch {
            message.value = when (val result = advanceJob(current)) {
                is JobActionResult.Done -> {
                    job.value = current.copy(status = result.status)
                    if (result.status != AssistanceStatus.MATCHED) stopNavigation()
                    null
                }
                JobActionResult.NoConnection -> "Sin conexión. Probá de nuevo cuando vuelva la señal."
                is JobActionResult.Conflict -> {
                    syncJob()
                    "El pedido cambió mientras tanto. ${result.reason}"
                }
                is JobActionResult.Failed -> result.reason
            }
            advancing.value = false
        }
    }

    /** Cierra el trabajo terminado y vuelve a la lista de pedidos. */
    fun closeJob() {
        stopNavigation()
        job.value = null
    }

    fun pass(offer: Offer) {
        viewModelScope.launch { offerRepository.decline(offer.assistanceId) }
    }

    fun dismissMessage() {
        message.value = null
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

/** RF04: pedidos cercanos. Tomar requiere conexión: gana el primero que acepta en el servidor. */
@Composable
fun MechanicScreen(viewModel: MechanicViewModel = hiltViewModel()) {
    val offers by viewModel.offers.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val job by viewModel.job.collectAsStateWithLifecycle()
    val available by viewModel.available.collectAsStateWithLifecycle()
    val changing by viewModel.changingAvailability.collectAsStateWithLifecycle()
    val me by viewModel.myLocation.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Pedidos cerca", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = viewModel::logout) { Text("Salir") }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (available) "Estás disponible" else "No estás disponible", style = MaterialTheme.typography.titleMedium)
            Switch(checked = available, onCheckedChange = viewModel::setAvailable, enabled = online && !changing)
        }
        if (!online) OfflineBanner("Sin conexión. No podés tomar pedidos hasta que vuelva la señal.")
        message?.let { Card(onClick = viewModel::dismissMessage) { Text(it, Modifier.padding(12.dp)) } }
        job?.let {
            // La lista de pasos puede ser larga: el viaje se desplaza solo (la lista de ofertas no se muestra).
            Column(Modifier.verticalScroll(rememberScrollState())) { TripCard(it, me, online, viewModel) }
        }
        if (job != null) return@Column
        if (offers.isEmpty()) {
            Text(
                if (available) {
                    "No hay pedidos cerca por ahora. Te mostramos los nuevos apenas aparezcan."
                } else {
                    "Ponete disponible para recibir pedidos cercanos."
                },
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(offers, key = { it.assistanceId }) { offer ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(offer.problemLabel, style = MaterialTheme.typography.titleMedium)
                        Text(offer.vehicleType.label())
                        Text(offer.howFar())
                        OfferMap(offer, me)
                        Text("Disponible hasta las ${offer.expiresAt.asHour()}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.pass(offer) }) { Text("Pasar") }
                            Button(onClick = { viewModel.take(offer) }, enabled = online) { Text("Tomar") }
                        }
                    }
                }
            }
        }
    }
}

/** Mientras un administrador no lo apruebe, el servidor no le deja ponerse disponible ni aceptar. */
@Composable
fun PendingVerificationScreen(rejected: Boolean, onCheckAgain: () -> Unit, onLogout: () -> Unit) {
    // Mientras está en revisión se renueva el token cada 30 segundos: cuando lo aprueban, la pantalla cambia sola.
    if (!rejected) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000)
                onCheckAgain()
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (rejected) "No pudimos aprobar tu cuenta" else "Tu cuenta está en revisión",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            if (rejected) {
                "Revisá la documentación que cargaste o escribinos para volver a intentarlo."
            } else {
                "Estamos validando tu DNI y tu CUIT. Cuando te aprobemos vas a poder ver y tomar pedidos."
            },
        )
        if (!rejected) OutlinedButton(onClick = onCheckAgain) { Text("Revisar ahora") }
        TextButton(onClick = onLogout) { Text("Salir") }
    }
}

/** "A 430 m por calle · unos 3 min". */
private fun Offer.howFar(): String {
    val distance = "A ${distanceMeters.asDistance()} " + if (distanceByRoad) "por calle" else "en línea recta"
    return listOfNotNull(distance, etaMinutes?.let { "unos ${it.asEta()}" }).joinToString(" · ")
}

/** Mapa con el repartidor (naranja) y el mecánico (azul). Sin la ubicación del pedido no se muestra. */
@Composable
private fun OfferMap(offer: Offer, me: GeoPoint?, height: Dp = 180.dp) {
    val rider = offer.location ?: return
    val pins = listOfNotNull(MapPin(rider, "Repartidor", isMain = true), me?.let { MapPin(it, "Vos") })
    PinsMap(pins, description = "Mapa: dónde está el repartidor", height = height)
}

/**
 * M2 · Trabajo en curso. Aceptado: la indicación que sigue, el mapa con la ruta y "Llegué, empezar el trabajo".
 * En curso: "Terminar el trabajo". Terminado: volver a los pedidos. Cada paso lo ve el repartidor en su seguimiento.
 */
@Composable
private fun TripCard(job: MechanicJob, me: GeoPoint?, online: Boolean, viewModel: MechanicViewModel) {
    val advancing by viewModel.advancing.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${job.problemLabel} · ${job.vehicleType.label()}", style = MaterialTheme.typography.titleMedium)
            JobStepper(job.status)
            when (job.status) {
                AssistanceStatus.MATCHED -> RouteToRider(job, me, viewModel)
                AssistanceStatus.IN_PROGRESS -> {
                    Text("Trabajando en el pedido", style = MaterialTheme.typography.headlineSmall)
                    Text("Cuando termines, avisá desde acá: el repartidor lo ve en su seguimiento.")
                }
                AssistanceStatus.COMPLETED -> {
                    Text("Trabajo terminado", style = MaterialTheme.typography.headlineSmall)
                    Text("¡Gracias! Ya podés volver a ver pedidos cerca.")
                }
                else -> Text(job.status.label(), style = MaterialTheme.typography.headlineSmall)
            }
            val next = AdvanceJobUseCase.nextStepLabel(job.status)
            if (next != null) {
                Button(
                    onClick = viewModel::advance,
                    enabled = online && !advancing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (advancing) "Un momento…" else next) }
                if (!online) Text("Necesitás conexión para avisar el avance.", style = MaterialTheme.typography.bodySmall)
            } else {
                Button(onClick = viewModel::closeJob, modifier = Modifier.fillMaxWidth()) { Text("Volver a los pedidos") }
            }
        }
    }
}

/** Aceptado → En curso → Terminado, el mismo recorrido que ve el repartidor. */
@Composable
private fun JobStepper(status: AssistanceStatus) {
    val steps = listOf("Aceptado", "En curso", "Terminado")
    val reached = when (status) {
        AssistanceStatus.MATCHED -> 0
        AssistanceStatus.IN_PROGRESS -> 1
        AssistanceStatus.COMPLETED -> 2
        else -> -1
    }
    Text(
        steps.mapIndexed { index, step -> if (index <= reached) "● $step" else "○ $step" }.joinToString("   "),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { contentDescription = "Estado del trabajo: ${steps.getOrElse(reached) { "" }}" },
    )
}

/** Mientras va en camino: la indicación que sigue, el mapa con la ruta y la lista de pasos. Todo sin salir de la app. */
@Composable
private fun RouteToRider(job: MechanicJob, me: GeoPoint?, viewModel: MechanicViewModel) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var showSteps by remember { mutableStateOf(false) }
    val current = route
    val now = progress

    when {
        now?.arrived == true -> Text("Llegaste. El repartidor está acá.", style = MaterialTheme.typography.headlineSmall)
        now?.nextStep != null -> {
            val inMeters = now.metersToNextStep?.let { "En ${it.asDistance()}: " } ?: ""
            Text(
                inMeters + now.nextStep?.instruction.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        current != null && !current.byRoad -> Text("Andá hacia el pin naranja. No pudimos calcular la ruta por calle.")
        job.location == null -> Text("Andá hasta el repartidor.")
        else -> Text("Calculando la ruta…")
    }
    current?.takeIf { now?.arrived != true }?.let {
        val left = now?.metersToDestination ?: it.distanceMeters
        val minutes = now?.minutesLeft?.takeIf { m -> m > 0 } ?: it.durationMinutes
        Text("Faltan ${left.asDistance()} · unos ${minutes.asEta()}", style = MaterialTheme.typography.bodyMedium)
    }
    val rider = job.location
    if (rider != null) {
        val pins = listOfNotNull(MapPin(rider, "Repartidor", isMain = true), me?.let { MapPin(it, "Vos") })
        PinsMap(pins, description = "Mapa con la ruta hasta el repartidor", height = 300.dp, route = current?.points.orEmpty())
    }
    if (current != null && current.steps.isNotEmpty()) {
        TextButton(onClick = { showSteps = !showSteps }) {
            Text(if (showSteps) "Ocultar los pasos" else "Ver todos los pasos (${current.steps.size})")
        }
        if (showSteps) {
            current.steps.forEachIndexed { index, step ->
                val isNext = step == now?.nextStep
                Text(
                    "${index + 1}. ${step.instruction}" + if (step.distanceMeters > 0) " (${step.distanceMeters.asDistance()})" else "",
                    style = if (isNext) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
