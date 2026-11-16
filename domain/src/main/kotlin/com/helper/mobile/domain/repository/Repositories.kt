package com.helper.mobile.domain.repository

import com.helper.mobile.domain.model.MechanicKind
import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.MechanicJob
import com.helper.mobile.domain.model.NearbyMechanic
import com.helper.mobile.domain.model.Route
import com.helper.mobile.domain.model.Offer
import com.helper.mobile.domain.model.ProblemType
import com.helper.mobile.domain.model.Role
import com.helper.mobile.domain.model.Session
import com.helper.mobile.domain.model.StatusChange
import com.helper.mobile.domain.model.Vehicle
import com.helper.mobile.domain.model.VehicleType
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
    /** Califica al mecánico de un pedido terminado (1 a 5). Requiere conexión. */
    suspend fun rate(localId: String, score: Int, comment: String?): RemoteActionResult

    /** Historial guardado en el teléfono: se ve también sin conexión. */
    fun observeHistory(localId: String): Flow<List<StatusChange>>
    /** Trae el historial del servidor y lo guarda; Done sin hacer nada si el pedido nunca llegó. */
    suspend fun refreshHistory(localId: String): RemoteActionResult
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

interface NearbyMechanicsRepository {
    /** Mecánicos disponibles cerca de [point], del más cercano al más lejano; null si no hay conexión o falló. */
    suspend fun near(point: GeoPoint, vehicleType: VehicleType?): List<NearbyMechanic>?
}

interface RouteRepository {
    /** Ruta para ir de [from] a [to], con el mismo perfil que el tiempo de las ofertas; null si no hay conexión. */
    suspend fun route(from: GeoPoint, to: GeoPoint): Route?
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

sealed interface LoginResult {
    data class LoggedIn(val session: Session) : LoginResult
    data object InvalidCredentials : LoginResult
    /** Al crear la cuenta: ya hay una cuenta con ese email. */
    data object EmailAlreadyUsed : LoginResult
    data object NoConnection : LoginResult
}

enum class RefreshResult { REFRESHED, SESSION_ENDED, NO_CONNECTION }

interface AuthRepository {
    suspend fun login(email: String, password: String): LoginResult
    /** Solo RIDER o MECHANIC: los admins no se registran desde la app. */
    suspend fun register(email: String, password: String, role: Role): LoginResult
    /** Pide un token nuevo; trae el estado de verificación actual del mecánico. */
    suspend fun refreshSession(): RefreshResult
    /** Cierra sesión y borra los datos locales del usuario. */
    suspend fun logout()
}

/** El perfil de repartidor o de mecánico en el backend; su id es el userId de la cuenta. */
interface ProfileRepository {
    enum class Check { EXISTS, MISSING, UNKNOWN }

    suspend fun check(session: Session): Check

    /** Datos de la cuenta para la pestaña Perfil; null si no hay conexión. */
    suspend fun summary(session: Session): ProfileSummary?
    suspend fun createRider(fullName: String, phone: String): RemoteActionResult
    suspend fun createMechanic(
        name: String,
        phone: String,
        location: GeoPoint,
        skills: Set<String>,
        vehicleTypes: Set<VehicleType>,
        kind: MechanicKind,
        address: String?,
    ): RemoteActionResult
}

sealed interface AvailabilityResult {
    data object Done : AvailabilityResult
    /** 403: el servidor todavía no lo ve aprobado. */
    data object NotApproved : AvailabilityResult
    data object NoConnection : AvailabilityResult
    data class Rejected(val reason: String) : AvailabilityResult
}

/** Cómo está el mecánico en el servidor; sirve para que el interruptor arranque como corresponde. */
data class MechanicState(val available: Boolean, val location: GeoPoint, val kind: MechanicKind = MechanicKind.MOBILE)

interface MechanicStatusRepository {
    suspend fun setAvailable(available: Boolean, location: GeoPoint): AvailabilityResult
    /** null si no hay conexión o falló. */
    suspend fun current(): MechanicState?
}

sealed interface JobActionResult {
    data class Done(val status: AssistanceStatus) : JobActionResult
    data object NoConnection : JobActionResult
    /** El pedido ya no está en el estado esperado, por ejemplo porque el repartidor lo canceló (409). */
    data class Conflict(val reason: String) : JobActionResult
    data class Failed(val reason: String) : JobActionResult
}

sealed interface ActiveJobResult {
    /** [job] es null si el mecánico no tiene ningún trabajo abierto. */
    data class Known(val job: MechanicJob?) : ActiveJobResult
    /** Sin conexión o error: no se sabe, así que no se cambia lo que se muestra. */
    data object Unknown : ActiveJobResult
}

/** Trabajo del mecánico después de tomar un pedido: empezarlo al llegar y terminarlo. */
interface JobRepository {
    /** El trabajo abierto (aceptado o en curso) según el servidor; sirve para recuperarlo al abrir la app. */
    suspend fun active(): ActiveJobResult
    suspend fun start(assistanceId: String): JobActionResult
    suspend fun complete(assistanceId: String): JobActionResult
}

/** Lo que muestra la pestaña Perfil. */
data class ProfileSummary(
    val name: String,
    val email: String?,
    val phone: String?,
)
