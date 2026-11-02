package com.helper.mobile.domain.usecase

import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.policy.PendingRequestPolicy
import com.helper.mobile.domain.repository.AssistanceRepository
import com.helper.mobile.domain.repository.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * RF03: seguir mi pedido. La UI observa Room; el estado viene del servidor salvo [Assistance.status]
 * locales (sin enviar, vencido). [Tracking.isStale] indica que el dato puede no ser el estado real.
 */
class ObserveAssistanceUseCase @Inject constructor(
    private val assistances: AssistanceRepository,
    private val policy: PendingRequestPolicy,
    private val time: TimeSource,
) {
    data class Tracking(
        val assistance: Assistance,
        val lastSyncedAt: Instant?,
        val isStale: Boolean,
        /** Sin enviar y ya pasaron 15 minutos: aunque el worker no haya corrido, se muestra como vencido. */
        val isExpired: Boolean,
    )

    operator fun invoke(localId: String): Flow<Tracking?> =
        assistances.observe(localId).map { assistance ->
            assistance?.let {
                val now = time.now()
                val synced = it.lastSyncedAt
                val stale = !it.status.isLocalOnly &&
                    (synced == null || Duration.between(synced, now) > STALE_AFTER)
                val expired = it.status == AssistanceStatus.EXPIRED ||
                    (it.status == AssistanceStatus.NOT_SENT && policy.isExpired(it, now))
                Tracking(it, synced, stale, expired)
            }
        }

    companion object {
        val STALE_AFTER: Duration = Duration.ofSeconds(30)
    }
}
