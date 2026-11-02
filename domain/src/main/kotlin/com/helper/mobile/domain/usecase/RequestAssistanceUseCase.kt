package com.helper.mobile.domain.usecase

import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.repository.AssistanceRepository
import com.helper.mobile.domain.repository.IdGenerator
import com.helper.mobile.domain.repository.ProblemCatalogRepository
import com.helper.mobile.domain.repository.TimeSource
import com.helper.mobile.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * RF02: pedir asistencia desde donde estoy.
 *
 * El pedido se guarda primero en el teléfono como [AssistanceStatus.NOT_SENT] y después se intenta enviar.
 * Si no hay conexión, el resultado lo dice explícitamente: el repartidor todavía no pidió ayuda.
 */
class RequestAssistanceUseCase @Inject constructor(
    private val assistances: AssistanceRepository,
    private val vehicles: VehicleRepository,
    private val problems: ProblemCatalogRepository,
    private val sendPending: SendPendingAssistanceUseCase,
    private val ids: IdGenerator,
    private val time: TimeSource,
) {
    sealed interface Result {
        /** Llegó al servidor: ya se está buscando mecánico. */
        data class Requested(val assistance: Assistance) : Result
        /** Quedó guardado sin enviar; se reintenta solo hasta que vence. */
        data class NotSentYet(val assistance: Assistance) : Result
        data class Rejected(val reason: String) : Result
        data class Invalid(val reason: InvalidReason) : Result
    }

    enum class InvalidReason { UNKNOWN_VEHICLE, PROBLEM_NOT_FOR_VEHICLE, ALREADY_HAS_ACTIVE_REQUEST }

    suspend operator fun invoke(
        vehicleId: String,
        problemCode: String,
        description: String?,
        location: GeoPoint,
    ): Result {
        val vehicle = vehicles.findById(vehicleId) ?: return Result.Invalid(InvalidReason.UNKNOWN_VEHICLE)
        val problem = problems.problems().firstOrNull { it.code == problemCode }
        if (problem == null || !problem.appliesTo(vehicle.type)) {
            return Result.Invalid(InvalidReason.PROBLEM_NOT_FOR_VEHICLE)
        }
        val active = assistances.observeActive().first()
        if (active != null && !active.status.isFinal) {
            return Result.Invalid(InvalidReason.ALREADY_HAS_ACTIVE_REQUEST)
        }

        val assistance = Assistance(
            localId = ids.newId(),
            remoteId = null,
            vehicleId = vehicle.id,
            vehicleType = vehicle.type,
            problemCode = problem.code,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            location = location,
            confirmedAt = time.now(),
            status = AssistanceStatus.NOT_SENT,
        )
        assistances.saveLocal(assistance)

        return when (val outcome = sendPending.sendOne(assistance, currentLocation = location)) {
            is SendPendingAssistanceUseCase.Outcome.Sent -> Result.Requested(outcome.assistance)
            is SendPendingAssistanceUseCase.Outcome.WaitingForConnection -> Result.NotSentYet(outcome.assistance)
            is SendPendingAssistanceUseCase.Outcome.Rejected -> Result.Rejected(outcome.reason)
            // Recién confirmado: no puede estar vencido ni necesitar reconfirmar ubicación.
            is SendPendingAssistanceUseCase.Outcome.Expired,
            is SendPendingAssistanceUseCase.Outcome.NeedsLocationConfirmation,
            -> Result.NotSentYet(assistance)
        }
    }
}
