package com.helper.mobile.ui

import com.helper.mobile.data.network.Connectivity
import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.NearbyMechanic
import com.helper.mobile.domain.model.Offer
import com.helper.mobile.domain.model.Role
import com.helper.mobile.domain.model.Route
import com.helper.mobile.domain.model.Session
import com.helper.mobile.domain.model.StatusChange
import com.helper.mobile.domain.model.Vehicle
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.model.VerificationStatus
import com.helper.mobile.domain.repository.AcceptResult
import com.helper.mobile.domain.repository.ActiveJobResult
import com.helper.mobile.domain.repository.AssistanceRepository
import com.helper.mobile.domain.repository.AuthRepository
import com.helper.mobile.domain.repository.AvailabilityResult
import com.helper.mobile.domain.repository.JobActionResult
import com.helper.mobile.domain.repository.JobRepository
import com.helper.mobile.domain.repository.LocationProvider
import com.helper.mobile.domain.repository.LoginResult
import com.helper.mobile.domain.repository.MechanicState
import com.helper.mobile.domain.repository.MechanicStatusRepository
import com.helper.mobile.domain.repository.NearbyMechanicsRepository
import com.helper.mobile.domain.repository.OfferRepository
import com.helper.mobile.domain.repository.PendingSendScheduler
import com.helper.mobile.domain.repository.RefreshResult
import com.helper.mobile.domain.repository.RemoteActionResult
import com.helper.mobile.domain.repository.RouteRepository
import com.helper.mobile.domain.repository.SendResult
import com.helper.mobile.domain.repository.SessionRepository
import com.helper.mobile.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant

// Dobles de prueba escritos a mano: más simples de leer que los mocks y sin reflexión.

class Online(online: Boolean = true) : Connectivity {
    override val isOnline: Flow<Boolean> = flowOf(online)
}

class FixedLocation(var here: GeoPoint?) : LocationProvider {
    override suspend fun currentLocation(): GeoPoint? = here
}

class NoAuth : AuthRepository {
    override suspend fun login(email: String, password: String) = LoginResult.NoConnection
    override suspend fun register(email: String, password: String, role: Role) = LoginResult.NoConnection
    override suspend fun refreshSession() = RefreshResult.NO_CONNECTION
    override suspend fun logout() = Unit
}

class ApprovedMechanicSession : SessionRepository {
    private val session = Session("m-1", Role.MECHANIC, VerificationStatus.APPROVED, "token", Instant.parse("2030-01-01T00:00:00Z"))
    override fun observeSession(): Flow<Session?> = flowOf(session)
    override suspend fun currentSession(): Session = session
}

class FakeOffers(offers: List<Offer>, var acceptResult: AcceptResult = AcceptResult.Accepted) : OfferRepository {
    private val shown = MutableStateFlow(offers)
    override fun observeOffers(): Flow<List<Offer>> = shown
    override suspend fun refresh() = RemoteActionResult.Done
    override suspend fun accept(assistanceId: String) = acceptResult
    override suspend fun decline(assistanceId: String) = RemoteActionResult.Done
}

class FakeMechanicStatus(var state: MechanicState?) : MechanicStatusRepository {
    override suspend fun setAvailable(available: Boolean, location: GeoPoint) = AvailabilityResult.Done
    override suspend fun current(): MechanicState? = state
}

class RecordingRoutes : RouteRepository {
    val asked = mutableListOf<Pair<GeoPoint, GeoPoint>>()
    override suspend fun route(from: GeoPoint, to: GeoPoint): Route? {
        asked += from to to
        return Route(listOf(from, to), emptyList(), from.distanceTo(to), 5, byRoad = false)
    }
}

class NoActiveJob : JobRepository {
    override suspend fun active() = ActiveJobResult.Known(null)
    override suspend fun start(assistanceId: String) = JobActionResult.NoConnection
    override suspend fun complete(assistanceId: String) = JobActionResult.NoConnection
}

class FakeVehicles(vararg vehicles: Vehicle) : VehicleRepository {
    private val all = MutableStateFlow(vehicles.toList())
    override fun observeVehicles(): Flow<List<Vehicle>> = all
    override suspend fun findById(id: String) = all.value.firstOrNull { it.id == id }
    override suspend fun save(vehicle: Vehicle) {
        all.value = all.value + vehicle
    }
}

class NoNearby : NearbyMechanicsRepository {
    override suspend fun near(point: GeoPoint, vehicleType: VehicleType?): List<NearbyMechanic>? = emptyList()
}

class NoScheduler : PendingSendScheduler {
    override fun schedule() = Unit
}

/** Pedidos en memoria. Sin red: [send] responde NoConnection, como en el modo avión. */
class FakeAssistances(var cancelResult: RemoteActionResult = RemoteActionResult.Done) : AssistanceRepository {
    val stored = MutableStateFlow<Map<String, Assistance>>(emptyMap())
    override fun observe(localId: String): Flow<Assistance?> = stored.map { it[localId] }
    override fun observeActive(): Flow<Assistance?> = stored.map { all -> all.values.lastOrNull { !it.status.isFinal } }
    override suspend fun find(localId: String) = stored.value[localId]
    override suspend fun pending() = stored.value.values.filter { it.status.isLocalOnly }
    override suspend fun saveLocal(assistance: Assistance) {
        stored.value = stored.value + (assistance.localId to assistance)
    }
    override suspend fun deleteLocal(localId: String) {
        stored.value = stored.value - localId
    }
    override suspend fun send(assistance: Assistance) = SendResult.NoConnection
    override suspend fun cancelRemote(assistance: Assistance) = cancelResult
    override suspend fun refresh(localId: String) = RemoteActionResult.Done
    override suspend fun rate(localId: String, score: Int, comment: String?) = RemoteActionResult.Done
    override fun observeHistory(localId: String): Flow<List<StatusChange>> = flowOf(emptyList())
    override suspend fun refreshHistory(localId: String) = RemoteActionResult.Done
}
