package com.helper.mobile.domain

import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.Offer
import com.helper.mobile.domain.model.ProblemType
import com.helper.mobile.domain.model.Session
import com.helper.mobile.domain.model.Vehicle
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.repository.AcceptResult
import com.helper.mobile.domain.repository.AssistanceRepository
import com.helper.mobile.domain.repository.IdGenerator
import com.helper.mobile.domain.repository.LocationProvider
import com.helper.mobile.domain.repository.OfferRepository
import com.helper.mobile.domain.repository.PendingSendScheduler
import com.helper.mobile.domain.repository.ProblemCatalogRepository
import com.helper.mobile.domain.repository.RemoteActionResult
import com.helper.mobile.domain.repository.SendResult
import com.helper.mobile.domain.repository.SessionRepository
import com.helper.mobile.domain.repository.TimeSource
import com.helper.mobile.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

val OBELISCO = GeoPoint(-34.6037, -58.3816)

class FakeTime(var current: Instant = Instant.parse("2026-11-02T15:00:00Z")) : TimeSource {
    override fun now(): Instant = current
    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}

class SequentialIds : IdGenerator {
    private var next = 1
    override fun newId(): String = "id-${next++}"
}

class FakeVehicles(vararg initial: Vehicle) : VehicleRepository {
    private val state = MutableStateFlow(initial.toList())
    override fun observeVehicles(): Flow<List<Vehicle>> = state
    override suspend fun findById(id: String): Vehicle? = state.value.firstOrNull { it.id == id }
    override suspend fun save(vehicle: Vehicle) {
        state.value = state.value.filterNot { it.id == vehicle.id } + vehicle
    }
}

class FakeProblems : ProblemCatalogRepository {
    override suspend fun problems(): List<ProblemType> = listOf(
        ProblemType("FLAT_TIRE", "Pinchadura", "TIRE_REPAIR", VehicleType.entries.toSet()),
        ProblemType("CHAIN", "Cadena", "CHAIN_REPAIR", setOf(VehicleType.BICYCLE, VehicleType.E_BIKE, VehicleType.MOTORCYCLE)),
        ProblemType("BATTERY", "Batería", "ELECTRICAL", setOf(VehicleType.E_BIKE, VehicleType.MOTORCYCLE)),
        ProblemType("WONT_START", "No arranca", "ENGINE", setOf(VehicleType.MOTORCYCLE)),
    )
}

class FakeAssistances : AssistanceRepository {
    private val state = MutableStateFlow<Map<String, Assistance>>(emptyMap())

    /** Respuestas del servidor en orden; si se acaban, responde Accepted. */
    val sendResults = ArrayDeque<SendResult>()
    var cancelResult: RemoteActionResult = RemoteActionResult.Done
    val sentKeys = mutableListOf<String>()
    private var remoteSeq = 100

    val all: Collection<Assistance> get() = state.value.values

    override fun observe(localId: String): Flow<Assistance?> = state.map { it[localId] }
    override fun observeActive(): Flow<Assistance?> =
        state.map { map -> map.values.firstOrNull { !it.status.isFinal } }

    override suspend fun find(localId: String): Assistance? = state.value[localId]
    override suspend fun pending(): List<Assistance> =
        state.value.values.filter { it.status == AssistanceStatus.NOT_SENT }

    override suspend fun saveLocal(assistance: Assistance) {
        state.value = state.value + (assistance.localId to assistance)
    }

    override suspend fun deleteLocal(localId: String) {
        state.value = state.value - localId
    }

    override suspend fun send(assistance: Assistance): SendResult {
        sentKeys += assistance.localId
        return sendResults.removeFirstOrNull()
            ?: SendResult.Accepted("remote-${remoteSeq++}", AssistanceStatus.REQUESTED)
    }

    override suspend fun cancelRemote(assistance: Assistance): RemoteActionResult = cancelResult
    override suspend fun refresh(localId: String): RemoteActionResult = RemoteActionResult.Done
}

class FakeLocation(var current: GeoPoint? = OBELISCO) : LocationProvider {
    override suspend fun currentLocation(): GeoPoint? = current
}

class CountingScheduler : PendingSendScheduler {
    var scheduled = 0
    override fun schedule() {
        scheduled++
    }
}

class FakeSession(var session: Session?) : SessionRepository {
    override fun observeSession(): Flow<Session?> = MutableStateFlow(session)
    override suspend fun currentSession(): Session? = session
}

class FakeOffers(var acceptResult: AcceptResult = AcceptResult.Accepted) : OfferRepository {
    var acceptCalls = 0
    override fun observeOffers(): Flow<List<Offer>> = MutableStateFlow(emptyList())
    override suspend fun refresh(): RemoteActionResult = RemoteActionResult.Done
    override suspend fun accept(assistanceId: String): AcceptResult {
        acceptCalls++
        return acceptResult
    }
    override suspend fun decline(assistanceId: String): RemoteActionResult = RemoteActionResult.Done
}
