package com.helper.mobile.domain.usecase

import com.helper.mobile.domain.model.Assistance
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
    private val time: TimeSource,
) {
    data class Tracking(
        val assistance: Assistance,
        val lastSyncedAt: Instant?,
        val isStale: Boolean,
    )

    operator fun invoke(localId: String): Flow<Tracking?> =
        assistances.observe(localId).map { assistance ->
            assistance?.let {
                val synced = it.lastSyncedAt
                val stale = !it.status.isLocalOnly &&
                    (synced == null || Duration.between(synced, time.now()) > STALE_AFTER)
                Tracking(it, synced, stale)
            }
        }

    companion object {
        val STALE_AFTER: Duration = Duration.ofSeconds(30)
    }
}
