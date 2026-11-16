package com.helper.mobile.data.repository

import com.helper.mobile.data.remote.ApiResult
import com.helper.mobile.data.remote.AssistanceResponse
import com.helper.mobile.data.remote.HelperApi
import com.helper.mobile.data.remote.apiCall
import com.helper.mobile.data.remote.reason
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.MechanicJob
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.repository.ActiveJobResult
import com.helper.mobile.domain.repository.JobActionResult
import com.helper.mobile.domain.repository.JobRepository
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** El trabajo del mecánico vive en el servidor: empezar y terminar siempre necesitan conexión. */
@Singleton
class JobRepositoryImpl @Inject constructor(
    private val api: HelperApi,
    private val catalog: ProblemCatalog,
) : JobRepository {

    override suspend fun active(): ActiveJobResult =
        when (val result = apiCall { api.myAssistances() }) {
            is ApiResult.Success -> {
                val open = result.body.firstOrNull { it.status == "MATCHED" || it.status == "IN_PROGRESS" }
                ActiveJobResult.Known(open?.toJob())
            }
            else -> ActiveJobResult.Unknown
        }

    override suspend fun start(assistanceId: String): JobActionResult = advance { api.start(assistanceId) }

    override suspend fun complete(assistanceId: String): JobActionResult = advance { api.complete(assistanceId) }

    private suspend fun advance(call: suspend () -> Response<AssistanceResponse>): JobActionResult =
        when (val result = apiCall(call)) {
            is ApiResult.Success -> JobActionResult.Done(result.body.status.toStatus())
            ApiResult.NoConnection -> JobActionResult.NoConnection
            is ApiResult.HttpError -> when (result.status) {
                409 -> JobActionResult.Conflict(result.reason())
                else -> JobActionResult.Failed(result.reason())
            }
        }

    private suspend fun AssistanceResponse.toJob(): MechanicJob {
        val label = catalog.problems().firstOrNull { it.code == problemType }?.label ?: problemType ?: "Pedido"
        val lat = latitude
        val lng = longitude
        return MechanicJob(
            assistanceId = id,
            problemLabel = label,
            vehicleType = vehicleType?.let { runCatching { VehicleType.valueOf(it) }.getOrNull() } ?: VehicleType.BICYCLE,
            location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            status = status.toStatus(),
        )
    }

    private fun String.toStatus(): AssistanceStatus =
        runCatching { AssistanceStatus.valueOf(this) }.getOrDefault(AssistanceStatus.MATCHED)
}
