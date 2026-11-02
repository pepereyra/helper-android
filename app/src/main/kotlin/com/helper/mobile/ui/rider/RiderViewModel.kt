package com.helper.mobile.ui.rider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helper.mobile.data.network.ConnectivityObserver
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.ProblemType
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
    val sending: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RiderViewModel @Inject constructor(
    vehicles: VehicleRepository,
    connectivity: ConnectivityObserver,
    assistances: AssistanceRepository,
    private val problemsCatalog: ProblemCatalogRepository,
    private val location: LocationProvider,
    private val auth: AuthRepository,
    private val registerVehicle: RegisterVehicleUseCase,
    private val requestAssistance: RequestAssistanceUseCase,
    private val sendPending: SendPendingAssistanceUseCase,
    private val confirmLocation: ConfirmNewLocationUseCase,
    private val cancelAssistance: CancelAssistanceUseCase,
    private val refreshAssistance: RefreshAssistanceUseCase,
    observeAssistance: ObserveAssistanceUseCase,
) : ViewModel() {

    val vehicles: StateFlow<List<Vehicle>> =
        vehicles.observeVehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val online: StateFlow<Boolean> =
        connectivity.isOnline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _problems = MutableStateFlow<List<ProblemType>>(emptyList())
    val form = MutableStateFlow(RequestForm())
    val message = MutableStateFlow<String?>(null)

    /** Pedido que se está mostrando. Se conserva aunque termine, hasta que el repartidor lo cierre. */
    private val shownLocalId = MutableStateFlow<String?>(null)

    val tracking: StateFlow<ObserveAssistanceUseCase.Tracking?> = shownLocalId
        .flatMapLatest { id -> if (id == null) flowOf(null) else observeAssistance(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
                }
                delay(5_000)
            }
        }
    }

    fun selectVehicle(id: String) = form.update { it.copy(vehicleId = id, problemCode = null) }
    fun selectProblem(code: String) = form.update { it.copy(problemCode = code) }
    fun describe(text: String) = form.update { it.copy(description = text) }
    fun dismissMessage() { message.value = null }

    fun addVehicle(type: VehicleType, brand: String, model: String, color: String?, plate: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = registerVehicle(type, brand, model, color, plate)) {
                is RegisterVehicleUseCase.Result.Registered -> {
                    form.update { it.copy(vehicleId = result.vehicle.id, problemCode = null) }
                    onDone()
                }
                is RegisterVehicleUseCase.Result.Invalid -> message.value = when (result.field) {
                    "plate" -> "Para una moto, la patente es obligatoria."
                    else -> "Completá marca y modelo."
                }
            }
        }
    }

    /** Se llama después de que el usuario otorgó (o negó) el permiso de ubicación. */
    fun requestHelp() {
        val current = form.value
        val vehicleId = current.vehicleId ?: return
        val problemCode = current.problemCode ?: return
        form.update { it.copy(sending = true) }
        viewModelScope.launch {
            val here = location.currentLocation()
            if (here == null) {
                message.value = "No pudimos leer tu ubicación. Activá el GPS y el permiso de ubicación."
                form.update { it.copy(sending = false) }
                return@launch
            }
            val result = requestAssistance(vehicleId, problemCode, current.description, here)
            message.value = when (result) {
                is RequestAssistanceUseCase.Result.Requested -> null
                is RequestAssistanceUseCase.Result.NotSentYet -> null // la pantalla lo muestra en grande
                is RequestAssistanceUseCase.Result.Rejected -> result.reason
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
            form.update { RequestForm(vehicleId = it.vehicleId) }
        }
    }

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
                CancelAssistanceUseCase.Result.CANCELLED -> "Pedido cancelado."
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
        viewModelScope.launch {
            // Un pendiente vencido que el worker todavía no marcó: la política lo marca como vencido.
            if (current != null && current.isExpired && current.assistance.status == AssistanceStatus.NOT_SENT) {
                sendPending.sendOne(current.assistance, currentLocation = null)
            }
            shownLocalId.value = null
        }
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}
