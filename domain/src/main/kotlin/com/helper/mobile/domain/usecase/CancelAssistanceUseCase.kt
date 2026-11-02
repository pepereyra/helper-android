package com.helper.mobile.domain.usecase

import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.repository.AssistanceRepository
import com.helper.mobile.domain.repository.RemoteActionResult
import javax.inject.Inject

class CancelAssistanceUseCase @Inject constructor(
    private val assistances: AssistanceRepository,
) {
    enum class Result { CANCELLED, NEEDS_CONNECTION, NOT_CANCELLABLE, NOT_FOUND }

    suspend operator fun invoke(localId: String): Result {
        val assistance = assistances.find(localId) ?: return Result.NOT_FOUND
        if (assistance.status.isFinal && !assistance.status.isLocalOnly) return Result.NOT_CANCELLABLE
        if (assistance.status == AssistanceStatus.IN_PROGRESS) return Result.NOT_CANCELLABLE

        // Nunca llegó al servidor (o eso creemos): se intenta cancelar igual con el mismo localId,
        // por si el envío había salido justo antes de perder la conexión.
        val result = assistances.cancelRemote(assistance)
        return when (result) {
            RemoteActionResult.Done -> {
                if (assistance.status.isLocalOnly) {
                    assistances.deleteLocal(localId)
                } else {
                    assistances.saveLocal(assistance.copy(status = AssistanceStatus.CANCELLED))
                }
                Result.CANCELLED
            }
            RemoteActionResult.NoConnection -> {
                if (assistance.remoteId == null && assistance.status.isLocalOnly) {
                    // Sin conexión y nunca enviado: borrarlo localmente basta, el worker ya no lo va a mandar.
                    assistances.deleteLocal(localId)
                    Result.CANCELLED
                } else {
                    Result.NEEDS_CONNECTION
                }
            }
            is RemoteActionResult.Rejected -> Result.NOT_CANCELLABLE
        }
    }
}
