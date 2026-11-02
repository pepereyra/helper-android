package com.helper.mobile.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.helper.mobile.domain.usecase.SendPendingAssistanceUseCase
import com.helper.mobile.domain.usecase.SendPendingAssistanceUseCase.Outcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Corre cuando vuelve la conexión, aunque la app esté cerrada o el teléfono se haya reiniciado.
 * Los pedidos vencidos o con ubicación vieja no se envían: quedan para que el repartidor decida.
 */
@HiltWorker
class SendPendingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sendPending: SendPendingAssistanceUseCase,
    private val notifier: PendingRequestNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcomes = sendPending.sendAll()
        outcomes.forEach { outcome ->
            when (outcome) {
                is Outcome.Sent -> notifier.helpRequested()
                is Outcome.Expired -> notifier.requestExpired()
                is Outcome.NeedsLocationConfirmation -> notifier.locationNeedsConfirmation()
                is Outcome.Rejected, is Outcome.WaitingForConnection -> Unit
            }
        }
        return if (outcomes.any { it is Outcome.WaitingForConnection }) Result.retry() else Result.success()
    }
}
