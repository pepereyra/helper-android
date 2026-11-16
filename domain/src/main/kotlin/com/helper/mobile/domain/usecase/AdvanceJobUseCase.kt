package com.helper.mobile.domain.usecase

import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.MechanicJob
import com.helper.mobile.domain.repository.JobActionResult
import com.helper.mobile.domain.repository.JobRepository
import javax.inject.Inject

/**
 * M2: el mecánico avanza el trabajo un paso. Aceptado → en curso (llegó y empieza) → terminado.
 * Quien decide si el paso vale es el servidor; acá solo se elige cuál pedir según el estado actual.
 */
class AdvanceJobUseCase @Inject constructor(
    private val jobs: JobRepository,
) {
    suspend operator fun invoke(job: MechanicJob): JobActionResult = when (job.status) {
        AssistanceStatus.MATCHED -> jobs.start(job.assistanceId)
        AssistanceStatus.IN_PROGRESS -> jobs.complete(job.assistanceId)
        else -> JobActionResult.Conflict("Este trabajo ya no tiene un paso siguiente.")
    }

    companion object {
        /** Texto del botón para el paso siguiente; null si no hay. */
        fun nextStepLabel(status: AssistanceStatus): String? = when (status) {
            AssistanceStatus.MATCHED -> "Llegué, empezar el trabajo"
            AssistanceStatus.IN_PROGRESS -> "Terminar el trabajo"
            else -> null
        }
    }
}
