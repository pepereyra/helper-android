package com.helper.mobile.domain

import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.Vehicle
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.policy.PendingRequestPolicy
import com.helper.mobile.domain.repository.SendResult
import com.helper.mobile.domain.usecase.CancelAssistanceUseCase
import com.helper.mobile.domain.usecase.ConfirmNewLocationUseCase
import com.helper.mobile.domain.usecase.RequestAssistanceUseCase
import com.helper.mobile.domain.usecase.RequestAssistanceUseCase.InvalidReason
import com.helper.mobile.domain.usecase.RequestAssistanceUseCase.Result
import com.helper.mobile.domain.usecase.SendPendingAssistanceUseCase
import com.helper.mobile.domain.usecase.SendPendingAssistanceUseCase.Outcome
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.repository.RemoteActionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class RequestAssistanceUseCaseTest {

    private val time = FakeTime()
    private val assistances = FakeAssistances()
    private val location = FakeLocation()
    private val scheduler = CountingScheduler()
    private val bike = Vehicle("bike", VehicleType.BICYCLE, "Trek", "Marlin 5")
    private val moto = Vehicle("moto", VehicleType.MOTORCYCLE, "Honda", "Wave 110", plate = "A123BCD")
    private val sendPending = SendPendingAssistanceUseCase(assistances, location, scheduler, PendingRequestPolicy(), time)
    private val request = RequestAssistanceUseCase(
        assistances, FakeVehicles(bike, moto), FakeProblems(), sendPending, SequentialIds(), time,
    )

    @Test
    fun `con conexion el pedido llega al servidor`() = runTest {
        val result = request("bike", "FLAT_TIRE", "Rueda de atrás", OBELISCO)

        assertTrue(result is Result.Requested)
        val sent = (result as Result.Requested).assistance
        assertEquals(AssistanceStatus.REQUESTED, sent.status)
        assertEquals("remote-100", sent.remoteId)
        assertEquals(time.now(), sent.lastSyncedAt)
    }

    @Test
    fun `sin conexion queda sin enviar, se programa el reintento y no se dice que se pidio ayuda`() = runTest {
        assistances.sendResults += SendResult.NoConnection

        val result = request("bike", "FLAT_TIRE", null, OBELISCO)

        assertTrue(result is Result.NotSentYet)
        val pending = (result as Result.NotSentYet).assistance
        assertEquals(AssistanceStatus.NOT_SENT, pending.status)
        assertTrue(pending.status.helpNotRequestedYet)
        assertEquals(1, scheduler.scheduled)
    }

    @Test
    fun `el problema tiene que corresponder al tipo de vehiculo`() = runTest {
        assertEquals(Result.Invalid(InvalidReason.PROBLEM_NOT_FOR_VEHICLE), request("bike", "WONT_START", null, OBELISCO))
        assertTrue(request("moto", "WONT_START", null, OBELISCO) is Result.Requested)
    }

    @Test
    fun `no se puede tener dos pedidos activos a la vez`() = runTest {
        request("bike", "FLAT_TIRE", null, OBELISCO)
        assertEquals(
            Result.Invalid(InvalidReason.ALREADY_HAS_ACTIVE_REQUEST),
            request("moto", "BATTERY", null, OBELISCO),
        )
    }

    @Test
    fun `los reintentos usan siempre el mismo localId como clave de idempotencia`() = runTest {
        assistances.sendResults += listOf(SendResult.NoConnection, SendResult.NoConnection)
        val pending = (request("bike", "FLAT_TIRE", null, OBELISCO) as Result.NotSentYet).assistance

        time.advance(Duration.ofMinutes(1))
        sendPending.sendAll()
        time.advance(Duration.ofMinutes(1))
        val outcomes = sendPending.sendAll()

        assertEquals(listOf(pending.localId, pending.localId, pending.localId), assistances.sentKeys)
        assertTrue(outcomes.single() is Outcome.Sent)
        assertEquals(1, assistances.all.size)
    }

    @Test
    fun `un pendiente vencido no se envia y queda como vencido`() = runTest {
        assistances.sendResults += SendResult.NoConnection
        val pending = (request("bike", "FLAT_TIRE", null, OBELISCO) as Result.NotSentYet).assistance

        time.advance(Duration.ofMinutes(16))
        val outcome = sendPending.sendAll().single()

        assertTrue(outcome is Outcome.Expired)
        assertEquals(listOf(pending.localId), assistances.sentKeys) // solo el primer intento
        assertEquals(AssistanceStatus.EXPIRED, assistances.find(pending.localId)!!.status)
    }

    @Test
    fun `si el servidor lo rechaza por viejo tambien queda vencido`() = runTest {
        assistances.sendResults += listOf(SendResult.NoConnection, SendResult.ExpiredOnServer)
        val pending = (request("bike", "FLAT_TIRE", null, OBELISCO) as Result.NotSentYet).assistance

        time.advance(Duration.ofMinutes(2))
        assertTrue(sendPending.sendAll().single() is Outcome.Expired)
        assertEquals(AssistanceStatus.EXPIRED, assistances.find(pending.localId)!!.status)
    }

    @Test
    fun `si se movio mucho espera a que reconfirme la ubicacion y despues se envia`() = runTest {
        assistances.sendResults += SendResult.NoConnection
        val pending = (request("bike", "FLAT_TIRE", null, OBELISCO) as Result.NotSentYet).assistance

        time.advance(Duration.ofMinutes(7))
        location.current = GeoPoint(-34.5992, -58.3816)
        assertTrue(sendPending.sendAll().single() is Outcome.NeedsLocationConfirmation)
        assertTrue(sendPending.sendAll().isEmpty()) // el worker no lo vuelve a tocar

        val confirm = ConfirmNewLocationUseCase(assistances, sendPending, time)
        val outcome = confirm(pending.localId, GeoPoint(-34.5992, -58.3816))

        assertTrue(outcome is Outcome.Sent)
        assertEquals(GeoPoint(-34.5992, -58.3816), outcome!!.assistance.location)
    }

    @Test
    fun `cancelar un pedido sin enviar sin conexion lo borra y el worker ya no lo manda`() = runTest {
        assistances.sendResults += SendResult.NoConnection
        val pending = (request("bike", "FLAT_TIRE", null, OBELISCO) as Result.NotSentYet).assistance
        assistances.cancelResult = RemoteActionResult.NoConnection

        assertEquals(CancelAssistanceUseCase.Result.CANCELLED, CancelAssistanceUseCase(assistances)(pending.localId))

        assertNull(assistances.find(pending.localId))
        assertTrue(sendPending.sendAll().isEmpty())
    }

    @Test
    fun `cancelar un pedido ya enviado sin conexion pide conexion`() = runTest {
        val sent = (request("bike", "FLAT_TIRE", null, OBELISCO) as Result.Requested).assistance
        assistances.cancelResult = RemoteActionResult.NoConnection

        assertEquals(CancelAssistanceUseCase.Result.NEEDS_CONNECTION, CancelAssistanceUseCase(assistances)(sent.localId))
        assertEquals(AssistanceStatus.REQUESTED, assistances.find(sent.localId)!!.status)
    }
}
