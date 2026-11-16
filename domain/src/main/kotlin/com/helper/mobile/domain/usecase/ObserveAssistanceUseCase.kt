package com.helper.mobile.domain.usecase

import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.policy.PendingRequestPolicy
import com.helper.mobile.domain.repository.AssistanceRepository
import com.helper.mobile.domain.repository.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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

    /**
     * Se vuelve a evaluar cada [RECHECK_EVERY] aunque la base no cambie: sin red el worker no corre, y sin esto
     * un pedido sin enviar seguiría diciendo "Todavía no pediste ayuda" después de vencido.
     */
    operator fun invoke(localId: String): Flow<Tracking?> =
        combine(assistances.observe(localId), ticks()) { assistance, _ ->
            assistance?.let {
                val now = time.now()
                val synced = it.lastSyncedAt
                val stale = !it.status.isLocalOnly &&
                    (synced == null || Duration.between(synced, now) > STALE_AFTER)
                val expired = it.status == AssistanceStatus.EXPIRED ||
                    (it.status == AssistanceStatus.NOT_SENT && policy.isExpired(it, now))
                Tracking(it, synced, stale, expired)
            }
        }.distinctUntilChanged()

    private fun ticks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(RECHECK_EVERY.toMillis())
        }
    }

    companion object {
        val STALE_AFTER: Duration = Duration.ofSeconds(30)
        val RECHECK_EVERY: Duration = Duration.ofSeconds(15)
    }
}
