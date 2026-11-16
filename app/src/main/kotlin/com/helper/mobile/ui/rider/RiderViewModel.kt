package com.helper.mobile.ui.rider

import com.helper.mobile.domain.model.MechanicKind
import com.helper.mobile.domain.repository.RemoteActionResult
import com.helper.mobile.domain.repository.RouteRepository
import com.helper.mobile.domain.model.Route
import kotlinx.coroutines.Job
import com.helper.mobile.domain.repository.NearbyMechanicsRepository
import com.helper.mobile.domain.model.NearbyMechanic
import com.helper.mobile.domain.model.GeoPoint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helper.mobile.data.network.ConnectivityObserver
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.ProblemType
import com.helper.mobile.domain.model.StatusChange
import com.helper.mobile.domain.model.Vehicle
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.repository.AssistanceRepository
import com.helper.mobile.domain.repository.AuthRepository
import com.helper.mobile.domain.repository.LocationProvider
import com.helper.mobile.domain.repository.ProblemCatalogRepository
import com.helper.mobile.domain.repository.VehicleRepository
import com.helper.mobile.domain.usecase.CancelAssistanceUseCase
import com.helper.mobile.domain.usecase.ConfirmNewLocationUseCase
import com.helper.mobile.domain.usecase.ObserveAssistanceUseCase
import com.helper.mobile.domain.usecase.RefreshAssistanceUseCase
import com.helper.mobile.domain.usecase.RegisterVehicleUseCase
import com.helper.mobile.domain.usecase.RequestAssistanceUseCase
import com.helper.mobile.domain.usecase.SendPendingAssistanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RequestForm(
    val vehicleId: String? = null,
    val problemCode: String? = null,
    val description: String = "",
    /** A domicilio (que vaya un mecánico) o ir a un taller, como en las apps de viajes. */
    val mechanicKind: MechanicKind = MechanicKind.MOBILE,
    val sending: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RiderViewModel @Inject constructor(
    vehicleRepository: VehicleRepository,
    connectivity: ConnectivityObserver,
    private val assistances: AssistanceRepository,
    private val problemsCatalog: ProblemCatalogRepository,
    private val location: LocationProvider,
    private val auth: AuthRepository,
    private val nearbyMechanics: NearbyMechanicsRepository,
    private val routes: RouteRepository,
    private val registerVehicle: RegisterVehicleUseCase,
    private val requestAssistance: RequestAssistanceUseCase,
    private val sendPending: SendPendingAssistanceUseCase,
    private val confirmLocation: ConfirmNewLocationUseCase,
    private val cancelAssistance: CancelAssistanceUseCase,
    private val refreshAssistance: RefreshAssistanceUseCase,
    observeAssistance: ObserveAssistanceUseCase,
) : ViewModel() {

    val vehicles: StateFlow<List<Vehicle>> =
        vehicleRepository.observeVehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val online: StateFlow<Boolean> =
        connectivity.isOnline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _problems = MutableStateFlow<List<ProblemType>>(emptyList())
    val form = MutableStateFlow(RequestForm())
    val message = MutableStateFlow<String?>(null)

    /** El último error se puede reintentar (E6): el botón Reintentar vuelve a mandar el pedido. */
    val canRetry = MutableStateFlow(false)

    /** E3: no hay permiso o no se pudo leer el GPS, así que el repartidor marca el lugar en el mapa. */
    val pickingLocation = MutableStateFlow(false)
    val pickedLocation = MutableStateFlow<GeoPoint?>(null)

    /** Dónde está el repartidor, para centrar el mapa. Null hasta que haya permiso y una lectura. */
    val myLocation = MutableStateFlow<GeoPoint?>(null)

    /** Mecánicos disponibles cerca, para el mapa del formulario y mientras se busca mecánico. */
    val nearby = MutableStateFlow<List<NearbyMechanic>>(emptyList())
    private var nearbyJob: Job? = null

    /** Ruta del mecánico asignado hasta el repartidor; se vuelve a pedir cuando el mecánico se mueve. */
    val mechanicRoute = MutableStateFlow<Route?>(null)
    private var routeFrom: GeoPoint? = null

    /** Pedido que se está mostrando. Se conserva aunque termine, hasta que el repartidor lo cierre. */
    private val shownLocalId = MutableStateFlow<String?>(null)

    val tracking: StateFlow<ObserveAssistanceUseCase.Tracking?> = shownLocalId
        .flatMapLatest { id -> if (id == null) flowOf(null) else observeAssistance(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 07 · Historial del pedido que se está mostrando, guardado en Room (se ve sin conexión). */
    val history: StateFlow<List<StatusChange>> = shownLocalId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else assistances.observeHistory(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val showingHistory = MutableStateFlow(false)

    /** Problemas que aplican al vehículo elegido. */
    val problems: StateFlow<List<ProblemType>> = combine(_problems, form, vehicles) { all, f, list ->
        val type = list.firstOrNull { it.id == f.vehicleId }?.type ?: return@combine emptyList()
        all.filter { it.appliesTo(type) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _problems.value = problemsCatalog.problems() }
        viewModelScope.launch {
            assistances.observeActive().filterNotNull().collect { shownLocalId.value = it.localId }
        }
        // Mientras el pedido está en el servidor y no terminó, se trae el estado real cada 5 segundos.
        viewModelScope.launch {
            while (isActive) {
                val current = tracking.value?.assistance
                if (current != null && current.remoteId != null && !current.status.isFinal && online.value) {
                    refreshAssistance(current.localId)
                    updateMechanicRoute(current.localId)
                }
                delay(5_000)
            }
        }
    }

    /**
     * Se llama cuando la pantalla tiene permiso de ubicación. Cada 15 segundos trae los mecánicos cercanos
     * mientras el repartidor no tiene un mecánico asignado; la ubicación se relee cada minuto.
     */
    fun startNearbyUpdates() {
        if (nearbyJob?.isActive == true) return
        nearbyJob = viewModelScope.launch {
            var round = 0
            while (isActive) {
                val status = tracking.value?.assistance?.status
                val searching = status == null || status == AssistanceStatus.REQUESTED
                if (searching && online.value) {
                    if (round % 4 == 0 || myLocation.value == null) {
                        location.currentLocation()?.let { myLocation.value = it }
                    }
                    myLocation.value?.let { here ->
                        nearbyMechanics.near(here, selectedVehicleType())?.let { nearby.value = it }
                    }
                    round++
                }
                delay(15_000)
            }
        }
    }

    private suspend fun updateMechanicRoute(localId: String) {
        // Se lee de la base recién actualizada: tracking todavía puede tener la posición anterior del mecánico.
        val assistance = assistances.find(localId) ?: return
        val onTheWay = assistance.status == AssistanceStatus.MATCHED
        val mechanicAt = assistance.mechanic?.location
        if (!onTheWay || mechanicAt == null) {
            mechanicRoute.value = null
            routeFrom = null
            return
        }
        if (assistance.mechanic?.kind == MechanicKind.WORKSHOP) {
            // Al taller va el repartidor: la ruta es desde donde pidió ayuda hasta el taller, y no cambia.
            if (mechanicRoute.value == null) {
                routes.route(assistance.location, mechanicAt)?.let { mechanicRoute.value = it }
            }
            return
        }
        val lastFrom = routeFrom
        if (lastFrom != null && lastFrom.distanceTo(mechanicAt) < ROUTE_REFRESH_METERS && mechanicRoute.value != null) return
        routes.route(mechanicAt, assistance.location)?.let {
            mechanicRoute.value = it
            routeFrom = mechanicAt
        }
    }

    private fun selectedVehicleType(): VehicleType? =
        vehicles.value.firstOrNull { it.id == form.value.vehicleId }?.type

    /** True mientras se envía la calificación. */
    val rating = MutableStateFlow(false)

    fun rate(score: Int, comment: String) {
        val localId = tracking.value?.assistance?.localId ?: return
        rating.value = true
        viewModelScope.launch {
            message.value = when (val result = assistances.rate(localId, score, comment)) {
                RemoteActionResult.Done -> null
                RemoteActionResult.NoConnection -> "Para calificar necesitás conexión."
                is RemoteActionResult.Rejected -> result.reason
            }
            rating.value = false
        }
    }

    fun selectVehicle(id: String) = form.update { it.copy(vehicleId = id, problemCode = null) }
    fun selectProblem(code: String) = form.update { it.copy(problemCode = code) }
    fun selectKind(kind: MechanicKind) = form.update { it.copy(mechanicKind = kind) }
    fun describe(text: String) = form.update { it.copy(description = text) }
    fun dismissMessage() {
        message.value = null
        canRetry.value = false
    }

    fun startPickingLocation() {
        pickingLocation.value = true
    }

    fun pickLocation(point: GeoPoint) {
        pickedLocation.value = point
    }

    fun cancelPickingLocation() {
        pickingLocation.value = false
        pickedLocation.value = null
    }

    fun retryLast() {
        canRetry.value = false
        requestHelp()
    }

    /** Error del alta de vehículo: va aparte para que no se mezcle con los avisos del pedido. */
    val vehicleError = MutableStateFlow<String?>(null)
    fun dismissVehicleError() { vehicleError.value = null }

    fun addVehicle(type: VehicleType, brand: String, model: String, color: String?, plate: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = registerVehicle(type, brand, model, color, plate)) {
                is RegisterVehicleUseCase.Result.Registered -> {
                    vehicleError.value = null
                    form.update { it.copy(vehicleId = result.vehicle.id, problemCode = null) }
                    onDone()
                }
                is RegisterVehicleUseCase.Result.Invalid -> vehicleError.value = when (result.field) {
                    "plate" -> "Para una moto, la patente es obligatoria."
                    else -> "Completá marca y modelo."
                }
            }
        }
    }

    /** Se llama después de que el usuario otorgó (o negó) el permiso de ubicación. */
    fun requestHelp() {
        // Si el repartidor ya marcó el lugar en el mapa, se usa ese.
        pickedLocation.value?.let { return requestHelpAt(it) }
        submit(null)
    }

    /** E3: pedir ayuda en el lugar que el repartidor marcó en el mapa. */
    fun requestHelpAt(point: GeoPoint) = submit(point)

    private fun submit(manual: GeoPoint?) {
        val current = form.value
        val vehicleId = current.vehicleId ?: return
        val problemCode = current.problemCode ?: return
        message.value = null
        canRetry.value = false
        form.update { it.copy(sending = true) }
        viewModelScope.launch {
            val here = manual ?: location.currentLocation()
            if (here == null) {
                message.value = "No pudimos leer tu ubicación. Marcá en el mapa dónde estás."
                pickingLocation.value = true
                form.update { it.copy(sending = false) }
                return@launch
            }
            val result = requestAssistance(vehicleId, problemCode, current.description, here, current.mechanicKind)
            message.value = when (result) {
                is RequestAssistanceUseCase.Result.Requested -> null
                is RequestAssistanceUseCase.Result.NotSentYet -> null // la pantalla lo muestra en grande
                is RequestAssistanceUseCase.Result.Rejected -> {
                    canRetry.value = true
                    "No pudimos crear el pedido: ${result.reason}"
                }
                is RequestAssistanceUseCase.Result.Invalid -> when (result.reason) {
                    RequestAssistanceUseCase.InvalidReason.ALREADY_HAS_ACTIVE_REQUEST -> "Ya tenés un pedido activo."
                    else -> "Revisá el vehículo y el problema."
                }
            }
            when (result) {
                is RequestAssistanceUseCase.Result.Requested -> shownLocalId.value = result.assistance.localId
                is RequestAssistanceUseCase.Result.NotSentYet -> shownLocalId.value = result.assistance.localId
                else -> Unit
            }
            if (result is RequestAssistanceUseCase.Result.Requested || result is RequestAssistanceUseCase.Result.NotSentYet) {
                form.update { RequestForm(vehicleId = it.vehicleId, mechanicKind = it.mechanicKind) }
                cancelPickingLocation()
            } else {
                // Lo escrito se conserva ante un error (RNF07).
                form.update { it.copy(sending = false) }
            }
        }
    }

    fun openHistory() {
        showingHistory.value = true
        val localId = tracking.value?.assistance?.localId ?: return
        viewModelScope.launch { assistances.refreshHistory(localId) }
    }

    fun closeHistory() {
        showingHistory.value = false
    }

    /** Nombre del problema para mostrar, por ejemplo "Pinchadura". */
    fun problemLabel(code: String): String = _problems.value.firstOrNull { it.code == code }?.label ?: code

    fun vehicleName(id: String): String? =
        vehicles.value.firstOrNull { it.id == id }?.let { "${it.brand} ${it.model}" }

    fun retryNow() {
        val pending = tracking.value?.assistance ?: return
        viewModelScope.launch {
            val outcome = sendPending.sendOne(pending, location.currentLocation())
            if (outcome is SendPendingAssistanceUseCase.Outcome.WaitingForConnection) {
                message.value = "Sigue sin haber conexión. Lo vamos a reintentar solos."
            }
        }
    }

    fun confirmCurrentLocation() {
        val pending = tracking.value?.assistance ?: return
        viewModelScope.launch {
            val here = location.currentLocation()
            if (here == null) {
                message.value = "No pudimos leer tu ubicación."
            } else {
                confirmLocation(pending.localId, here)
            }
        }
    }

    fun cancel() {
        val localId = tracking.value?.assistance?.localId ?: return
        viewModelScope.launch {
            message.value = when (cancelAssistance(localId)) {
                // La pantalla ya lo dice en grande ("Cancelado"): no hace falta un aviso aparte.
                CancelAssistanceUseCase.Result.CANCELLED -> null
                CancelAssistanceUseCase.Result.NEEDS_CONNECTION -> "Para cancelar un pedido ya enviado necesitás conexión."
                CancelAssistanceUseCase.Result.NOT_CANCELLABLE -> "Este pedido ya no se puede cancelar."
                CancelAssistanceUseCase.Result.NOT_FOUND -> null
            }
            if (tracking.value == null) shownLocalId.value = null
        }
    }

    /** Cierra un pedido terminado o vencido y vuelve al formulario. */
    fun dismissTracking() {
        val current = tracking.value
        // Los avisos eran de ese pedido: el formulario arranca limpio.
        message.value = null
        canRetry.value = false
        viewModelScope.launch {
            // Un pendiente vencido que el worker todavía no marcó: la política lo marca como vencido.
            if (current != null && current.isExpired && current.assistance.status == AssistanceStatus.NOT_SENT) {
                sendPending.sendOne(current.assistance, currentLocation = null)
            }
            shownLocalId.value = null
            showingHistory.value = false
        }
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

private const val ROUTE_REFRESH_METERS = 40.0

