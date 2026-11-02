package com.helper.mobile.domain.policy

import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.GeoPoint
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Decide qué hacer con un pedido que quedó sin enviar por falta de conexión.
 *
 * - Vence a los [EXPIRY] desde que se confirmó: ya no se envía solo.
 * - Pasados [RELOCATION_CHECK_AFTER], si el repartidor se movió más de [MAX_DRIFT_METERS],
 *   hay que reconfirmar la ubicación antes de enviar.
 */
class PendingRequestPolicy @Inject constructor() {

    sealed interface Decision {
        data object Send : Decision
        data object Expire : Decision
        data class ReconfirmLocation(val current: GeoPoint) : Decision
    }

    fun isExpired(pending: Assistance, now: Instant): Boolean =
        !now.isBefore(pending.confirmedAt.plus(EXPIRY))

    fun needsLocationCheck(pending: Assistance, now: Instant): Boolean =
        !now.isBefore(pending.confirmedAt.plus(RELOCATION_CHECK_AFTER))

    fun decide(pending: Assistance, now: Instant, currentLocation: GeoPoint?): Decision {
        if (isExpired(pending, now)) return Decision.Expire
        if (needsLocationCheck(pending, now) && currentLocation != null &&
            pending.location.distanceTo(currentLocation) > MAX_DRIFT_METERS
        ) {
            return Decision.ReconfirmLocation(currentLocation)
        }
        return Decision.Send
    }

    companion object {
        val EXPIRY: Duration = Duration.ofMinutes(15)
        val RELOCATION_CHECK_AFTER: Duration = Duration.ofMinutes(5)
        const val MAX_DRIFT_METERS = 300.0
    }
}
