package com.helper.mobile.data.repository

import com.helper.mobile.data.remote.ApiResult
import com.helper.mobile.data.remote.HelperApi
import com.helper.mobile.data.remote.apiCall
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.Route
import com.helper.mobile.domain.model.RouteStep
import com.helper.mobile.domain.repository.RouteRepository
import javax.inject.Inject
import javax.inject.Singleton

/** No se guarda: la ruta depende de dónde está el mecánico en este momento. */
@Singleton
class RouteRepositoryImpl @Inject constructor(
    private val api: HelperApi,
) : RouteRepository {

    override suspend fun route(from: GeoPoint, to: GeoPoint): Route? =
        when (
            val result = apiCall {
                api.route(from.latitude, from.longitude, to.latitude, to.longitude)
            }
        ) {
            is ApiResult.Success -> result.body.let { body ->
                Route(
                    points = body.points.filter { it.size >= 2 }.map { GeoPoint(it[0], it[1]) },
                    steps = body.steps.map {
                        RouteStep(it.instruction, it.distanceMeters.toDouble(), GeoPoint(it.latitude, it.longitude))
                    },
                    distanceMeters = body.distanceKm * 1000,
                    durationMinutes = body.durationMinutes,
                    byRoad = body.source == "ROUTE",
                )
            }
            else -> null
        }
}
