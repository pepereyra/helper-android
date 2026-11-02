package com.helper.mobile.data.repository

import com.helper.mobile.data.local.VehicleDao
import com.helper.mobile.data.local.toDomain
import com.helper.mobile.data.local.toEntity
import com.helper.mobile.data.remote.ApiResult
import com.helper.mobile.data.remote.CreateVehicleRequest
import com.helper.mobile.data.remote.HelperApi
import com.helper.mobile.data.remote.apiCall
import com.helper.mobile.domain.model.Vehicle
import com.helper.mobile.domain.repository.SessionRepository
import com.helper.mobile.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Los vehículos se guardan primero en Room; el registro en el backend se hace ahora o antes del primer pedido. */
@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val dao: VehicleDao,
    private val api: HelperApi,
    private val session: SessionRepository,
) : VehicleRepository {

    override fun observeVehicles(): Flow<List<Vehicle>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: String): Vehicle? = dao.find(id)?.toDomain()

    override suspend fun save(vehicle: Vehicle) {
        dao.upsert(vehicle.toEntity(remoteId = dao.find(vehicle.id)?.remoteId))
        ensureRegistered(vehicle.id)
    }

    /** Devuelve el id del backend, registrándolo si hace falta; null si no hay conexión. */
    suspend fun ensureRegistered(vehicleId: String): String? {
        val entity = dao.find(vehicleId) ?: return null
        entity.remoteId?.let { return it }
        val clientId = session.currentSession()?.userId ?: return null
        val result = apiCall {
            api.registerVehicle(
                clientId,
                CreateVehicleRequest(entity.type, entity.brand, entity.model, entity.color, entity.plate),
            )
        }
        return when (result) {
            is ApiResult.Success -> result.body.id.also { dao.upsert(entity.copy(remoteId = it)) }
            else -> null
        }
    }
}
