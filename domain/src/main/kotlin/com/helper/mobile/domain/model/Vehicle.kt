package com.helper.mobile.domain.model

/** Mismos valores que usa el backend. Sumar un tipo nuevo es agregar un valor acá y sus problemas al catálogo. */
enum class VehicleType { BICYCLE, E_BIKE, MOTORCYCLE }

data class Vehicle(
    val id: String,
    val type: VehicleType,
    val brand: String,
    val model: String,
    val color: String? = null,
    val plate: String? = null,
)

/** Problema que el repartidor puede elegir; cada uno aplica a ciertos tipos de vehículo. */
data class ProblemType(
    val code: String,
    val label: String,
    /** Habilidad que tiene que tener el mecánico; el backend la usa para elegir a quién ofrecer el pedido. */
    val requiredSkill: String,
    val vehicleTypes: Set<VehicleType>,
) {
    fun appliesTo(type: VehicleType): Boolean = type in vehicleTypes
}
