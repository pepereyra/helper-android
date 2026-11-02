package com.helper.mobile.domain

import app.cash.turbine.test
import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.Role
import com.helper.mobile.domain.model.Session
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.model.VerificationStatus
import com.helper.mobile.domain.repository.AcceptResult
import com.helper.mobile.domain.usecase.ObserveAssistanceUseCase
import com.helper.mobile.domain.usecase.RegisterVehicleUseCase
import com.helper.mobile.domain.usecase.TakeOfferUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class MechanicAndVehicleUseCasesTest {

    private fun mechanic(verification: VerificationStatus) =
        Session("m1", Role.MECHANIC, verification, "token", Instant.parse("2026-11-02T16:00:00Z"))

    @Test
    fun `un mecanico sin aprobar no puede tomar pedidos y ni siquiera se llama al servidor`() = runTest {
        val offers = FakeOffers()
        val take = TakeOfferUseCase(offers, FakeSession(mechanic(VerificationStatus.PENDING_VERIFICATION)))

        assertEquals(AcceptResult.NotAllowed, take("a1"))
        assertEquals(0, offers.acceptCalls)
    }

    @Test
    fun `un repartidor no puede tomar pedidos`() = runTest {
        val rider = Session("r1", Role.RIDER, null, "token", Instant.parse("2026-11-02T16:00:00Z"))
        assertEquals(AcceptResult.NotAllowed, TakeOfferUseCase(FakeOffers(), FakeSession(rider))("a1"))
    }

    @Test
    fun `si otro mecanico lo tomo antes, se informa`() = runTest {
        val offers = FakeOffers(acceptResult = AcceptResult.AlreadyTaken)
        val take = TakeOfferUseCase(offers, FakeSession(mechanic(VerificationStatus.APPROVED)))

        assertEquals(AcceptResult.AlreadyTaken, take("a1"))
        assertEquals(1, offers.acceptCalls)
    }

    @Test
    fun `la patente solo se guarda para motos`() = runTest {
        val vehicles = FakeVehicles()
        val register = RegisterVehicleUseCase(vehicles, SequentialIds())

        val bike = register(VehicleType.BICYCLE, "Trek", "Marlin 5", "Negra", "abc123") as RegisterVehicleUseCase.Result.Registered
        val moto = register(VehicleType.MOTORCYCLE, "Honda", "Wave", null, " a123bcd ") as RegisterVehicleUseCase.Result.Registered

        assertNull(bike.vehicle.plate)
        assertEquals("A123BCD", moto.vehicle.plate)
        assertEquals(RegisterVehicleUseCase.Result.Invalid("brand"), register(VehicleType.E_BIKE, " ", "C20", null, null))
    }

    @Test
    fun `el seguimiento marca como desactualizado un estado que no se confirmo hace rato`() = runTest {
        val time = FakeTime()
        val assistances = FakeAssistances()
        assistances.saveLocal(
            Assistance(
                localId = "a1", remoteId = "r1", vehicleId = "v1", vehicleType = VehicleType.BICYCLE,
                problemCode = "FLAT_TIRE", description = null, location = OBELISCO,
                confirmedAt = time.now(), status = AssistanceStatus.MATCHED,
                lastSyncedAt = time.now().minus(Duration.ofMinutes(2)),
            ),
        )

        ObserveAssistanceUseCase(assistances, time)("a1").test {
            val tracking = awaitItem()!!
            assertTrue(tracking.isStale)
            assertEquals(AssistanceStatus.MATCHED, tracking.assistance.status)
        }

        assistances.saveLocal(assistances.find("a1")!!.copy(lastSyncedAt = time.now()))
        ObserveAssistanceUseCase(assistances, time)("a1").test {
            assertFalse(awaitItem()!!.isStale)
        }
    }
}
