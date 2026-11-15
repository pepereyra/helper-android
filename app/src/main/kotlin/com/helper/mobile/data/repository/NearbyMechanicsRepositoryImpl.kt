package com.helper.mobile.data.repository

import com.helper.mobile.data.remote.ApiResult
import com.helper.mobile.data.remote.HelperApi
import com.helper.mobile.data.remote.apiCall
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.NearbyMechanic
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.repository.NearbyMechanicsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** No se guarda en Room: el mapa solo tiene sentido con datos del momento. */
@Singleton
class NearbyMechanicsRepositoryImpl @Inject constructor(
    private val api: HelperApi,
) : NearbyMechanicsRepository {

    override suspend fun near(point: GeoPoint, vehicleType: VehicleType?): List<NearbyMechanic>? =
        when (val result = apiCall { api.nearbyMechanics(point.latitude, point.longitude, vehicleType?.name) }) {
            is ApiResult.Success -> result.body.map {
                NearbyMechanic(
                    id = it.id,
                    name = it.name,
                    location = GeoPoint(it.latitude, it.longitude),
                    distanceMeters = it.distanceKm * 1000,
                )
            }
            else -> null
        }
}
