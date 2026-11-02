package com.helper.mobile.domain.usecase

import com.helper.mobile.domain.repository.AssistanceRepository
import com.helper.mobile.domain.repository.RemoteActionResult
import javax.inject.Inject

/** Trae el estado real del pedido desde el servidor y actualiza Room; la pantalla se entera sola. */
class RefreshAssistanceUseCase @Inject constructor(
    private val assistances: AssistanceRepository,
) {
    suspend operator fun invoke(localId: String): RemoteActionResult = assistances.refresh(localId)
}
