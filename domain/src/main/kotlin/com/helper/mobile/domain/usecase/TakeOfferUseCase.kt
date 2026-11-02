package com.helper.mobile.domain.usecase

import com.helper.mobile.domain.repository.AcceptResult
import com.helper.mobile.domain.repository.OfferRepository
import com.helper.mobile.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * RF04: el mecánico toma un pedido cercano. Quién gana lo decide el servidor (el primero que acepta);
 * la app solo evita llamar si ya sabe que la cuenta no está aprobada.
 */
class TakeOfferUseCase @Inject constructor(
    private val offers: OfferRepository,
    private val session: SessionRepository,
) {
    suspend operator fun invoke(assistanceId: String): AcceptResult {
        val current = session.currentSession()
        if (current == null || !current.canWorkAsMechanic) return AcceptResult.NotAllowed
        return offers.accept(assistanceId)
    }
}
