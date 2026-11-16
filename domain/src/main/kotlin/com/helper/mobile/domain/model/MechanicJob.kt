package com.helper.mobile.domain.model

/** Pedido que tomó el mecánico (M2): lo que necesita para ir hasta el repartidor, empezar y terminar el trabajo. */
data class MechanicJob(
    val assistanceId: String,
    val problemLabel: String,
    val vehicleType: VehicleType,
    /** Dónde está el repartidor; null si el servidor no la mandó. */
    val location: GeoPoint?,
    /** MATCHED (va en camino), IN_PROGRESS (trabajando) o COMPLETED. */
    val status: AssistanceStatus,
)
