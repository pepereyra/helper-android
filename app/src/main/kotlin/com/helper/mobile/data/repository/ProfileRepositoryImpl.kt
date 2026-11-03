package com.helper.mobile.data.repository

import com.helper.mobile.data.remote.ApiResult
import com.helper.mobile.data.remote.AvailabilityRequest
import com.helper.mobile.data.remote.CreateClientRequest
import com.helper.mobile.data.remote.CreateMechanicRequest
import com.helper.mobile.data.remote.HelperApi
import com.helper.mobile.data.remote.apiCall
import com.helper.mobile.data.remote.reason
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.Role
import com.helper.mobile.domain.model.Session
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.repository.AvailabilityResult
import com.helper.mobile.domain.repository.MechanicStatusRepository
import com.helper.mobile.domain.repository.ProfileRepository
import com.helper.mobile.domain.repository.RemoteActionResult
import com.helper.mobile.domain.repository.SessionRepository
import javax.inject.Inject

/** Perfil de repartidor (assistance-service) o de mecánico (mechanic-service); el id es el userId del token. */
class ProfileRepositoryImpl @Inject constructor(
    private val api: HelperApi,
    private val sessions: SessionRepository,
) : ProfileRepository, MechanicStatusRepository {

    override suspend fun check(session: Session): ProfileRepository.Check {
        val result = when (session.role) {
            Role.RIDER -> apiCall { api.client(session.userId) }
            Role.MECHANIC -> apiCall { api.mechanic(session.userId) }
            Role.ADMIN -> return ProfileRepository.Check.EXISTS
        }
        return when (result) {
            is ApiResult.Success -> ProfileRepository.Check.EXISTS
            is ApiResult.HttpError -> if (result.status == 404) ProfileRepository.Check.MISSING else ProfileRepository.Check.UNKNOWN
            // Sin red no se bloquea al usuario: se vuelve a revisar cuando vuelva.
            ApiResult.NoConnection -> ProfileRepository.Check.UNKNOWN
        }
    }

    override suspend fun createRider(fullName: String, phone: String): RemoteActionResult =
        apiCall { api.createClient(CreateClientRequest(fullName.trim(), phone.trim())) }.toAction()

    override suspend fun createMechanic(
        name: String,
        location: GeoPoint,
        skills: Set<String>,
        vehicleTypes: Set<VehicleType>,
    ): RemoteActionResult = apiCall {
        api.createMechanic(
            CreateMechanicRequest(
                name = name.trim(),
                latitude = location.latitude,
                longitude = location.longitude,
                skills = skills,
                vehicleTypes = vehicleTypes.map { it.name }.toSet(),
            ),
        )
    }.toAction()

    override suspend fun setAvailable(available: Boolean, location: GeoPoint): AvailabilityResult {
        val id = sessions.currentSession()?.userId ?: return AvailabilityResult.Rejected("Sesión vencida")
        return when (val result = apiCall { api.availability(id, AvailabilityRequest(available, location.latitude, location.longitude)) }) {
            is ApiResult.Success -> AvailabilityResult.Done
            ApiResult.NoConnection -> AvailabilityResult.NoConnection
            is ApiResult.HttpError -> if (result.status == 403) AvailabilityResult.NotApproved else AvailabilityResult.Rejected(result.reason())
        }
    }

    private fun ApiResult<Unit>.toAction(): RemoteActionResult = when (this) {
        is ApiResult.Success -> RemoteActionResult.Done
        ApiResult.NoConnection -> RemoteActionResult.NoConnection
        // 409: el perfil ya existía (por ejemplo, un reintento después de un corte).
        is ApiResult.HttpError -> if (status == 409) RemoteActionResult.Done else RemoteActionResult.Rejected(reason())
    }
}
