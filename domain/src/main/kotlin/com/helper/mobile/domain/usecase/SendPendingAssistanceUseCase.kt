package com.helper.mobile.domain.usecase

import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.policy.PendingRequestPolicy
import com.helper.mobile.domain.repository.AssistanceRepository
import com.helper.mobile.domain.repository.LocationProvider
import com.helper.mobile.domain.repository.PendingSendScheduler
import com.helper.mobile.domain.repository.SendResult
import com.helper.mobile.domain.repository.TimeSource
import javax.inject.Inject

/**
 * Envía los pedidos que quedaron sin enviar. Lo usa el worker de WorkManager cuando vuelve la conexión
 * y también el envío inmediato al confirmar un pedido.
 *
 * Nunca envía un pedido vencido ni uno cuya ubicación quedó vieja: esos casos vuelven al repartidor.
 */
class SendPendingAssistanceUseCase @Inject constructor(
    private val assistances: AssistanceRepository,
    private val location: LocationProvider,
    private val scheduler: PendingSendScheduler,
    private val policy: PendingRequestPolicy,
    private val time: TimeSource,
) {
    sealed interface Outcome {
        val assistance: Assistance

        data class Sent(override val assistance: Assistance) : Outcome
        data class WaitingForConnection(override val assistance: Assistance) : Outcome
        data class Expired(override val assistance: Assistance) : Outcome
        data class NeedsLocationConfirmation(override val assistance: Assistance) : Outcome
        data class Rejected(override val assistance: Assistance, val reason: String) : Outcome
    }

    /** Procesa todos los pendientes y devuelve qué pasó con cada uno. */
    suspend fun sendAll(): List<Outcome> =
        assistances.pending()
            .filter { it.status == AssistanceStatus.NOT_SENT && !it.needsLocationConfirmation }
            .map { pending ->
                val now = time.now()
                val current = if (policy.needsLocationCheck(pending, now)) location.currentLocation() else null
                sendOne(pending, current)
            }

    suspend fun sendOne(pending: Assistance, currentLocation: GeoPoint?): Outcome {
        val now = time.now()
        when (val decision = policy.decide(pending, now, currentLocation)) {
            PendingRequestPolicy.Decision.Expire -> {
                val expired = pending.copy(status = AssistanceStatus.EXPIRED)
                assistances.saveLocal(expired)
                return Outcome.Expired(expired)
            }
            is PendingRequestPolicy.Decision.ReconfirmLocation -> {
                val flagged = pending.copy(needsLocationConfirmation = true)
                assistances.saveLocal(flagged)
                return Outcome.NeedsLocationConfirmation(flagged)
            }
            PendingRequestPolicy.Decision.Send -> Unit
        }

        return when (val result = assistances.send(pending)) {
            is SendResult.Accepted -> {
                val sent = pending.copy(remoteId = result.remoteId, status = result.status, lastSyncedAt = now)
                assistances.saveLocal(sent)
                Outcome.Sent(sent)
            }
            SendResult.NoConnection -> {
                scheduler.schedule()
                Outcome.WaitingForConnection(pending)
            }
            SendResult.ExpiredOnServer -> {
                val expired = pending.copy(status = AssistanceStatus.EXPIRED)
                assistances.saveLocal(expired)
                Outcome.Expired(expired)
            }
            is SendResult.Rejected -> Outcome.Rejected(pending, result.reason)
        }
    }
}
