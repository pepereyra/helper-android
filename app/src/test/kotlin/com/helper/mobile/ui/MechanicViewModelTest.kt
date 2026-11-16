package com.helper.mobile.ui

import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.MechanicKind
import com.helper.mobile.domain.model.Offer
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.repository.AcceptResult
import com.helper.mobile.domain.repository.MechanicState
import com.helper.mobile.domain.usecase.AdvanceJobUseCase
import com.helper.mobile.domain.usecase.TakeOfferUseCase
import com.helper.mobile.ui.mechanic.MechanicViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** El ViewModel del mecánico con los casos de uso reales y repositorios de prueba. */
class MechanicViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val rider = GeoPoint(-34.6037, -58.3816)
    private val workshopAt = GeoPoint(-34.6036, -58.3902)
    private val offer = Offer("a-1", "Pinchadura", VehicleType.BICYCLE, 430.0, Instant.parse("2026-11-16T05:00:00Z"), rider)

    private val offers = FakeOffers(listOf(offer))
    private val status = FakeMechanicStatus(null)
    private val routes = RecordingRoutes()

    private fun viewModel(): MechanicViewModel {
        val jobs = NoActiveJob()
        return MechanicViewModel(
            offerRepository = offers,
            takeOffer = TakeOfferUseCase(offers, ApprovedMechanicSession()),
            auth = NoAuth(),
            status = status,
            location = FixedLocation(GeoPoint(-34.6083, -58.3712)),
            routes = routes,
            jobs = jobs,
            advanceJob = AdvanceJobUseCase(jobs),
            connectivity = Online(),
        )
    }

    @Test
    fun theSwitchStartsAsTheServerHasIt() {
        status.state = MechanicState(available = true, location = workshopAt, kind = MechanicKind.WORKSHOP)

        val vm = viewModel()
        main.runCurrent()

        assertTrue(vm.available.value)
        assertEquals(MechanicKind.WORKSHOP, vm.kind.value)
    }

    @Test
    fun aMobileMechanicThatTakesAnOfferGetsTheRouteToTheRider() {
        status.state = MechanicState(available = true, location = rider, kind = MechanicKind.MOBILE)
        val vm = viewModel()
        main.runCurrent()

        vm.take(offer)
        main.runCurrent()

        assertEquals(AssistanceStatus.MATCHED, vm.job.value?.status)
        assertEquals(rider, routes.asked.single().second)
    }

    @Test
    fun aWorkshopThatTakesAnOfferDoesNotNavigate() {
        status.state = MechanicState(available = true, location = workshopAt, kind = MechanicKind.WORKSHOP)
        val vm = viewModel()
        main.runCurrent()

        vm.take(offer)
        main.runCurrent()
        assertEquals(AssistanceStatus.MATCHED, vm.job.value?.status)

        // Aunque pase el tiempo, un taller nunca pide la ruta: espera al repartidor.
        main.advanceTimeBy(20_000)
        assertTrue("un taller no pide ruta", routes.asked.isEmpty())
    }

    @Test
    fun ifAnotherMechanicWonItSaysSo() {
        offers.acceptResult = AcceptResult.AlreadyTaken
        val vm = viewModel()
        main.runCurrent()

        vm.take(offer)
        main.runCurrent()

        assertNull(vm.job.value)
        assertEquals("Otro mecánico ya lo tomó.", vm.message.value)
    }
}
