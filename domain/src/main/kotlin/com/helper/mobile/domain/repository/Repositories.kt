package com.helper.mobile.domain.repository

import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.Offer
import com.helper.mobile.domain.model.ProblemType
import com.helper.mobile.domain.model.Session
import com.helper.mobile.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SessionRepository {
    fun observeSession(): Flow<Session?>
    suspend fun currentSession(): Session?
}

interface VehicleRepository {
    fun observeVehicles(): Flow<List<Vehicle>>
    suspend fun findById(id: String): Vehicle?
    suspend fun save(vehicle: Vehicle)
}

interface ProblemCatalogRepository {
    suspend fun problems(): List<ProblemType>
}

/** Resultado de intentar enviar un pedido al servidor. */
sealed interface SendResult {
    data class Accepted(val remoteId: String, val status: AssistanceStatus) : SendResult
    /** El servidor lo rechazó por viejo (422 REQUEST_EXPIRED). */
    data object ExpiredOnServer : SendResult
    data object NoConnection : SendResult
    data class Rejected(val reason: String) : SendResult
}

sealed interface RemoteActionResult {
    data object Done : RemoteActionResult
    data object NoConnection : RemoteActionResult
    data class Rejected(val reason: String) : RemoteActionResult
}

interface AssistanceRepository {
    fun observe(localId: String): Flow<Assistance?>
    fun observeActive(): Flow<Assistance?>
    suspend fun find(localId: String): Assistance?
    suspend fun pending(): List<Assistance>
    suspend fun saveLocal(assistance: Assistance)
    suspend fun deleteLocal(localId: String)

    /** Envía con el localId como Idempotency-Key: reintentar es seguro. */
    suspend fun send(assistance: Assistance): SendResult
    suspend fun cancelRemote(assistance: Assistance): RemoteActionResult
    suspend fun refresh(localId: String): RemoteActionResult
}

sealed interface AcceptResult {
    data object Accepted : AcceptResult
    /** Otro mecánico lo tomó antes (409 ALREADY_TAKEN). */
    data object AlreadyTaken : AcceptResult
    /** El servidor no lo deja trabajar todavía (403, cuenta sin aprobar). */
    data object NotAllowed : AcceptResult
    data object NoConnection : AcceptResult
    data class Failed(val reason: String) : AcceptResult
}

interface OfferRepository {
    fun observeOffers(): Flow<List<Offer>>
    suspend fun refresh(): RemoteActionResult
    suspend fun accept(assistanceId: String): AcceptResult
    suspend fun decline(assistanceId: String): RemoteActionResult
}

interface LocationProvider {
    /** Lectura puntual del GPS; null si no hay permiso o no se pudo leer. */
    suspend fun currentLocation(): GeoPoint?
}

/** Programa el envío de pedidos pendientes para cuando vuelva la conexión (WorkManager). */
interface PendingSendScheduler {
    fun schedule()
}

interface TimeSource {
    fun now(): Instant
}

interface IdGenerator {
    fun newId(): String
}
