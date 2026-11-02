package com.helper.mobile.data.repository

import com.helper.mobile.data.local.OfferDao
import com.helper.mobile.data.local.OfferEntity
import com.helper.mobile.data.local.toDomain
import com.helper.mobile.data.remote.ApiResult
import com.helper.mobile.data.remote.HelperApi
import com.helper.mobile.data.remote.apiCall
import com.helper.mobile.data.remote.reason
import com.helper.mobile.domain.model.Offer
import com.helper.mobile.domain.repository.AcceptResult
import com.helper.mobile.domain.repository.OfferRepository
import com.helper.mobile.domain.repository.RemoteActionResult
import com.helper.mobile.domain.repository.SessionRepository
import com.helper.mobile.data.remote.MechanicRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Las ofertas se guardan en Room para mostrarlas, pero aceptar siempre requiere conexión. */
@Singleton
class OfferRepositoryImpl @Inject constructor(
    private val dao: OfferDao,
    private val api: HelperApi,
    private val catalog: ProblemCatalog,
    private val session: SessionRepository,
) : OfferRepository {

    private suspend fun mechanicId(): String? = session.currentSession()?.userId

    override fun observeOffers(): Flow<List<Offer>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun refresh(): RemoteActionResult {
        val id = mechanicId() ?: return RemoteActionResult.Rejected("Sesión vencida")
        return when (val result = apiCall { api.offers(id) }) {
            is ApiResult.Success -> {
                // La oferta trae la habilidad requerida; se muestra el nombre del primer problema que la usa.
                val labels = catalog.problems().reversed().associate { it.requiredSkill to it.label }
                dao.replaceAll(
                    result.body.map {
                        OfferEntity(
                            assistanceId = it.assistanceId,
                            problemLabel = labels[it.requiredSkill] ?: it.requiredSkill,
                            vehicleType = it.vehicleType,
                            distanceMeters = it.distanceKm * 1000,
                            expiresAtMillis = Instant.parse(it.expiresAt).toEpochMilli(),
                        )
                    },
                )
                RemoteActionResult.Done
            }
            ApiResult.NoConnection -> RemoteActionResult.NoConnection
            is ApiResult.HttpError -> RemoteActionResult.Rejected(result.reason())
        }
    }

    override suspend fun accept(assistanceId: String): AcceptResult {
        val id = mechanicId() ?: return AcceptResult.NotAllowed
        return when (val result = apiCall { api.accept(assistanceId, MechanicRef(id)) }) {
            is ApiResult.Success -> {
                dao.delete(assistanceId)
                AcceptResult.Accepted
            }
            ApiResult.NoConnection -> AcceptResult.NoConnection
            is ApiResult.HttpError -> when {
                // ALREADY_TAKEN: otro ganó. OFFER_EXPIRED y ASSISTANCE_CLOSED: la oferta ya no sirve.
                result.status == 409 && result.problem?.code in setOf(null, "ALREADY_TAKEN") -> {
                    dao.delete(assistanceId)
                    AcceptResult.AlreadyTaken
                }
                result.status == 409 || result.status == 404 -> {
                    dao.delete(assistanceId)
                    AcceptResult.Failed(result.reason())
                }
                result.status == 403 -> AcceptResult.NotAllowed
                else -> AcceptResult.Failed(result.reason())
            }
        }
    }

    override suspend fun decline(assistanceId: String): RemoteActionResult {
        dao.delete(assistanceId)
        val id = mechanicId() ?: return RemoteActionResult.Rejected("Sesión vencida")
        return when (val result = apiCall { api.decline(assistanceId, MechanicRef(id)) }) {
            is ApiResult.Success -> RemoteActionResult.Done
            ApiResult.NoConnection -> RemoteActionResult.NoConnection
            is ApiResult.HttpError -> RemoteActionResult.Rejected(result.reason())
        }
    }
}
