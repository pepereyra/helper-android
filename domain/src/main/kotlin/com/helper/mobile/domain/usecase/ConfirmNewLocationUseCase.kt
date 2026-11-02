package com.helper.mobile.domain.usecase

import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.repository.AssistanceRepository
import com.helper.mobile.domain.repository.TimeSource
import javax.inject.Inject

/**
 * El repartidor confirma dónde está ahora para un pedido que esperaba sin enviar.
 * Confirmar de nuevo cuenta como una confirmación nueva: el vencimiento vuelve a correr.
 * Se conserva el localId, así que si el envío anterior sí había llegado, el servidor no lo duplica.
 */
class ConfirmNewLocationUseCase @Inject constructor(
    private val assistances: AssistanceRepository,
    private val sendPending: SendPendingAssistanceUseCase,
    private val time: TimeSource,
) {
    suspend operator fun invoke(localId: String, location: GeoPoint): SendPendingAssistanceUseCase.Outcome? {
        val pending = assistances.find(localId) ?: return null
        if (pending.status != AssistanceStatus.NOT_SENT) return null
        val confirmed = pending.copy(location = location, confirmedAt = time.now(), needsLocationConfirmation = false)
        assistances.saveLocal(confirmed)
        return sendPending.sendOne(confirmed, currentLocation = location)
    }
}
