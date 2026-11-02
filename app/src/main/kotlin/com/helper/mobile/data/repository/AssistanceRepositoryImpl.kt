package com.helper.mobile.data.repository

import com.helper.mobile.data.local.AssistanceDao
import com.helper.mobile.data.local.toDomain
import com.helper.mobile.data.local.toEntity
import com.helper.mobile.data.remote.ApiResult
import com.helper.mobile.data.remote.CreateAssistanceRequest
import com.helper.mobile.data.remote.HelperApi
import com.helper.mobile.data.remote.apiCall
import com.helper.mobile.data.remote.reason
import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.repository.AssistanceRepository
import com.helper.mobile.domain.repository.RemoteActionResult
import com.helper.mobile.domain.repository.SendResult
import com.helper.mobile.domain.repository.SessionRepository
import com.helper.mobile.domain.repository.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistanceRepositoryImpl @Inject constructor(
    private val dao: AssistanceDao,
    private val api: HelperApi,
    private val vehicles: VehicleRepositoryImpl,
    private val catalog: ProblemCatalog,
    private val session: SessionRepository,
    private val time: TimeSource,
) : AssistanceRepository {

    override fun observe(localId: String): Flow<Assistance?> = dao.observe(localId).map { it?.toDomain() }

    override fun observeActive(): Flow<Assistance?> = dao.observeActive().map { it?.toDomain() }

    override suspend fun find(localId: String): Assistance? = dao.find(localId)?.toDomain()

    override suspend fun pending(): List<Assistance> = dao.pending().map { it.toDomain() }

    override suspend fun saveLocal(assistance: Assistance) = dao.upsert(assistance.toEntity())

    override suspend fun deleteLocal(localId: String) = dao.delete(localId)

    override suspend fun send(assistance: Assistance): SendResult {
        val clientId = session.currentSession()?.userId ?: return SendResult.Rejected("Sesión vencida")
        val vehicleRemoteId = vehicles.ensureRegistered(assistance.vehicleId) ?: return SendResult.NoConnection
        val problem = catalog.problems().first { it.code == assistance.problemCode }
        val body = CreateAssistanceRequest(
            clientId = clientId,
            vehicleId = vehicleRemoteId,
            // El backend exige descripción: si el repartidor no escribió nada, va el nombre del problema.
            description = assistance.description ?: problem.label,
            problemType = problem.code,
            requiredSkill = problem.requiredSkill,
            latitude = assistance.location.latitude,
            longitude = assistance.location.longitude,
            clientCreatedAt = assistance.confirmedAt.toString(),
        )
        return when (val result = apiCall { api.createAssistance(assistance.localId, body) }) {
            is ApiResult.Success -> SendResult.Accepted(result.body.id, result.body.status.toStatus())
            ApiResult.NoConnection -> SendResult.NoConnection
            is ApiResult.HttpError -> when {
                result.status == 422 && result.problem?.code == "REQUEST_EXPIRED" -> SendResult.ExpiredOnServer
                result.status >= 500 -> SendResult.NoConnection // se reintenta igual que sin red
                else -> SendResult.Rejected(result.reason())
            }
        }
    }

    override suspend fun cancelRemote(assistance: Assistance): RemoteActionResult {
        val remoteId = assistance.remoteId
        val result = if (remoteId != null) {
            apiCall { api.cancel(remoteId) }
        } else {
            val clientId = session.currentSession()?.userId ?: return RemoteActionResult.Rejected("Sesión vencida")
            apiCall { api.cancelByKey(assistance.localId, clientId) }
        }
        return when (result) {
            is ApiResult.Success -> RemoteActionResult.Done
            ApiResult.NoConnection -> RemoteActionResult.NoConnection
            // Nunca llegó al servidor: no hay nada que cancelar allá.
            is ApiResult.HttpError -> if (result.status == 404 && remoteId == null) {
                RemoteActionResult.Done
            } else {
                RemoteActionResult.Rejected(result.reason())
            }
        }
    }

    override suspend fun refresh(localId: String): RemoteActionResult {
        val local = dao.find(localId) ?: return RemoteActionResult.Rejected("No existe")
        val remoteId = local.remoteId ?: return RemoteActionResult.Done
        return when (val result = apiCall { api.assistance(remoteId) }) {
            is ApiResult.Success -> {
                dao.upsert(
                    local.copy(
                        status = result.body.status.toStatus().name,
                        mechanicId = result.body.assignedMechanicId ?: local.mechanicId,
                        lastSyncedAtMillis = time.now().toEpochMilli(),
                    ),
                )
                RemoteActionResult.Done
            }
            ApiResult.NoConnection -> RemoteActionResult.NoConnection
            is ApiResult.HttpError -> RemoteActionResult.Rejected(result.reason())
        }
    }

    private fun String.toStatus(): AssistanceStatus =
        runCatching { AssistanceStatus.valueOf(this) }.getOrDefault(AssistanceStatus.REQUESTED)
}
