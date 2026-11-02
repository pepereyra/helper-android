package com.helper.mobile.data.repository

import com.helper.mobile.domain.model.ProblemType
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.model.VehicleType.BICYCLE
import com.helper.mobile.domain.model.VehicleType.E_BIKE
import com.helper.mobile.domain.model.VehicleType.MOTORCYCLE
import com.helper.mobile.domain.repository.ProblemCatalogRepository
import javax.inject.Inject

/**
 * Catálogo precargado en la app. Cuando el backend exponga el suyo, este queda como respaldo sin conexión.
 * Sumar un tipo de vehículo nuevo es agregarlo a los problemas que le aplican.
 */
class ProblemCatalog @Inject constructor() : ProblemCatalogRepository {
    override suspend fun problems(): List<ProblemType> = PROBLEMS

    companion object {
        private val ALL: Set<VehicleType> = VehicleType.entries.toSet()

        val PROBLEMS = listOf(
            ProblemType("FLAT_TIRE", "Pinchadura", "TIRE_REPAIR", ALL),
            ProblemType("CHAIN", "Cadena", "CHAIN_REPAIR", ALL),
            ProblemType("BRAKES", "Frenos", "BRAKES", ALL),
            ProblemType("WHEEL", "Rueda", "WHEEL_TRUING", setOf(BICYCLE, E_BIKE)),
            ProblemType("BATTERY", "Batería", "ELECTRICAL", setOf(E_BIKE, MOTORCYCLE)),
            ProblemType("WONT_START", "No arranca", "ENGINE", setOf(MOTORCYCLE)),
            ProblemType("OTHER", "Otro", "GENERAL", ALL),
        )
    }
}
