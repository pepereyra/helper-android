package com.helper.mobile.domain.usecase

import com.helper.mobile.domain.model.Vehicle
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.repository.IdGenerator
import com.helper.mobile.domain.repository.VehicleRepository
import javax.inject.Inject

class RegisterVehicleUseCase @Inject constructor(
    private val vehicles: VehicleRepository,
    private val ids: IdGenerator,
) {
    sealed interface Result {
        data class Registered(val vehicle: Vehicle) : Result
        data class Invalid(val field: String) : Result
    }

    suspend operator fun invoke(
        type: VehicleType,
        brand: String,
        model: String,
        color: String?,
        plate: String?,
    ): Result {
        if (brand.isBlank()) return Result.Invalid("brand")
        if (model.isBlank()) return Result.Invalid("model")
        // La patente solo tiene sentido para motos, y para motos el backend la exige.
        val normalizedPlate = plate?.trim()?.uppercase()?.takeIf { it.isNotEmpty() && type == VehicleType.MOTORCYCLE }
        if (type == VehicleType.MOTORCYCLE && normalizedPlate == null) return Result.Invalid("plate")
        val vehicle = Vehicle(
            id = ids.newId(),
            type = type,
            brand = brand.trim(),
            model = model.trim(),
            color = color?.trim()?.takeIf { it.isNotEmpty() },
            plate = normalizedPlate,
        )
        vehicles.save(vehicle)
        return Result.Registered(vehicle)
    }
}
