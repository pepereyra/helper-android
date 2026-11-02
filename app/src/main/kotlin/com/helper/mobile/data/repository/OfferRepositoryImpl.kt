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
) : OfferRepository {

    override fun observeOffers(): Flow<List<Offer>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun refresh(): RemoteActionResult =
        when (val result = apiCall { api.offers() }) {
            is ApiResult.Success -> {
                val labels = catalog.problems().associate { it.code to it.label }
                dao.replaceAll(
                    result.body.map {
                        OfferEntity(
                            assistanceId = it.assistanceId,
                            problemLabel = labels[it.problemType] ?: it.problemType,
                            vehicleType = it.vehicleType,
                            distanceMeters = it.distanceMeters,
                            expiresAtMillis = Instant.parse(it.expiresAt).toEpochMilli(),
                        )
                    },
                )
                RemoteActionResult.Done
            }
            ApiResult.NoConnection -> RemoteActionResult.NoConnection
            is ApiResult.HttpError -> RemoteActionResult.Rejected(result.reason())
        }

    override suspend fun accept(assistanceId: String): AcceptResult =
        when (val result = apiCall { api.accept(assistanceId) }) {
            is ApiResult.Success -> {
                dao.delete(assistanceId)
                AcceptResult.Accepted
            }
            ApiResult.NoConnection -> AcceptResult.NoConnection
            is ApiResult.HttpError -> when (result.status) {
                409 -> {
                    dao.delete(assistanceId)
                    AcceptResult.AlreadyTaken
                }
                403 -> AcceptResult.NotAllowed
                else -> AcceptResult.Failed(result.reason())
            }
        }

    override suspend fun decline(assistanceId: String): RemoteActionResult {
        dao.delete(assistanceId)
        return when (val result = apiCall { api.decline(assistanceId) }) {
            is ApiResult.Success -> RemoteActionResult.Done
            ApiResult.NoConnection -> RemoteActionResult.NoConnection
            is ApiResult.HttpError -> RemoteActionResult.Rejected(result.reason())
        }
    }
}
