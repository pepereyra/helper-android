package com.helper.mobile.domain.model

import java.time.Instant

/**
 * Estados de un pedido de asistencia.
 *
 * [NOT_SENT] y [EXPIRED] existen solo en el teléfono: el pedido todavía no llegó al servidor.
 * El resto los decide siempre el servidor.
 */
enum class AssistanceStatus(val isLocalOnly: Boolean, val isFinal: Boolean) {
    NOT_SENT(isLocalOnly = true, isFinal = false),
    EXPIRED(isLocalOnly = true, isFinal = true),
    REQUESTED(isLocalOnly = false, isFinal = false),
    MATCHED(isLocalOnly = false, isFinal = false),
    IN_PROGRESS(isLocalOnly = false, isFinal = false),
    COMPLETED(isLocalOnly = false, isFinal = true),
    CANCELLED(isLocalOnly = false, isFinal = true),
    UNMATCHED(isLocalOnly = false, isFinal = true),
    ;

    /** True cuando el repartidor todavía no pidió ayuda de verdad. */
    val helpNotRequestedYet: Boolean
        get() = isLocalOnly
}

data class AssignedMechanic(
    val id: String,
    val name: String,
    val rating: Double?,
    val distanceMeters: Double?,
)

data class Assistance(
    /** Generado en el teléfono; viaja como Idempotency-Key, así un reintento nunca crea otro pedido. */
    val localId: String,
    val remoteId: String?,
    val vehicleId: String,
    val vehicleType: VehicleType,
    val problemCode: String,
    val description: String?,
    val location: GeoPoint,
    /** Momento en que el repartidor confirmó el pedido; desde acá corre el vencimiento. */
    val confirmedAt: Instant,
    val status: AssistanceStatus,
    val mechanic: AssignedMechanic? = null,
    /** Última vez que el estado se confirmó con el servidor; null si nunca llegó. */
    val lastSyncedAt: Instant? = null,
    /** El repartidor se movió mientras el pedido esperaba: hay que reconfirmar la ubicación antes de enviar. */
    val needsLocationConfirmation: Boolean = false,
)

/** Pedido ofrecido a un mecánico cercano. Lo toma el primero que acepta. */
data class Offer(
    val assistanceId: String,
    val problemLabel: String,
    val vehicleType: VehicleType,
    val distanceMeters: Double,
    val expiresAt: Instant,
)
