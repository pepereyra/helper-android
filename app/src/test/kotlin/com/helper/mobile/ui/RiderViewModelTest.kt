package com.helper.mobile.ui

import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.MechanicKind
import com.helper.mobile.domain.model.ProblemType
import com.helper.mobile.domain.model.Vehicle
import com.helper.mobile.domain.model.VehicleType
import com.helper.mobile.domain.policy.PendingRequestPolicy
import com.helper.mobile.domain.repository.IdGenerator
import com.helper.mobile.domain.repository.ProblemCatalogRepository
import com.helper.mobile.domain.repository.TimeSource
import com.helper.mobile.domain.usecase.CancelAssistanceUseCase
import com.helper.mobile.domain.usecase.ConfirmNewLocationUseCase
import com.helper.mobile.domain.usecase.ObserveAssistanceUseCase
import com.helper.mobile.domain.usecase.RefreshAssistanceUseCase
import com.helper.mobile.domain.usecase.RegisterVehicleUseCase
import com.helper.mobile.domain.usecase.RequestAssistanceUseCase
import com.helper.mobile.domain.usecase.SendPendingAssistanceUseCase
import com.helper.mobile.ui.rider.RiderViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** El ViewModel del repartidor con los casos de uso reales y repositorios de prueba (sin red). */
class RiderViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val now = Instant.parse("2026-11-16T05:00:00Z")
    private val here = GeoPoint(-34.6037, -58.3816)
    private val bike = Vehicle("v-1", VehicleType.BICYCLE, "bmx", "bmx")
    private val flat = ProblemType("FLAT_TIRE", "Pinchadura", "TIRE_REPAIR", setOf(VehicleType.BICYCLE))

    private val time = object : TimeSource {
        override fun now() = now
    }
    private val ids = object : IdGenerator {
        override fun newId() = "l-1"
    }
    private val vehicles = FakeVehicles(bike)
    private val problems = object : ProblemCatalogRepository {
        override suspend fun problems() = listOf(flat)
    }
    private val location = FixedLocation(here)
    private val assistances = FakeAssistances()
    private val policy = PendingRequestPolicy()
    private val sendPending = SendPendingAssistanceUseCase(assistances, location, NoScheduler(), policy, time)

    private fun viewModel() = RiderViewModel(
        vehicleRepository = vehicles,
        connectivity = Online(),
        assistances = assistances,
        problemsCatalog = problems,
        location = location,
        auth = NoAuth(),
        nearbyMechanics = NoNearby(),
        routes = RecordingRoutes(),
        registerVehicle = RegisterVehicleUseCase(vehicles, ids),
        requestAssistance = RequestAssistanceUseCase(assistances, vehicles, problems, sendPending, ids, time),
        sendPending = sendPending,
        confirmLocation = ConfirmNewLocationUseCase(assistances, sendPending, time),
        cancelAssistance = CancelAssistanceUseCase(assistances),
        refreshAssistance = RefreshAssistanceUseCase(assistances),
        observeAssistance = ObserveAssistanceUseCase(assistances, policy, time),
    )

    @Test
    fun withoutSignalTheRequestIsKeptWithWhatTheRiderChose() {
        val vm = viewModel()
        main.runCurrent()

        vm.selectVehicle("v-1")
        vm.selectProblem("FLAT_TIRE")
        vm.selectKind(MechanicKind.WORKSHOP)
        vm.requestHelp()
        main.runCurrent()

        val saved = assistances.stored.value.getValue("l-1")
        assertEquals(AssistanceStatus.NOT_SENT, saved.status)
        assertEquals(MechanicKind.WORKSHOP, saved.preferredKind)
        assertEquals(here, saved.location)
        // La elección queda para el próximo pedido.
        assertEquals(MechanicKind.WORKSHOP, vm.form.value.mechanicKind)
    }

    @Test
    fun aVehicleErrorDoesNotShowUpAsAnOrderNotice() {
        val vm = viewModel()
        main.runCurrent()

        vm.addVehicle(VehicleType.BICYCLE, "", "", null, null) {}
        main.runCurrent()

        assertEquals("Completá marca y modelo.", vm.vehicleError.value)
        assertNull(vm.message.value)
    }

    @Test
    fun cancellingLeavesNoExtraNoticeAndGoingBackStartsClean() {
        val order = Assistance(
            localId = "l-1",
            remoteId = "r-1",
            vehicleId = "v-1",
            vehicleType = VehicleType.BICYCLE,
            problemCode = "FLAT_TIRE",
            description = null,
            location = here,
            confirmedAt = now,
            status = AssistanceStatus.REQUESTED,
        )
        runBlocking { assistances.saveLocal(order) }
        val vm = viewModel()
        // tracking se comparte mientras alguien lo observa, como hace la pantalla.
        CoroutineScope(main.dispatcher).launch { vm.tracking.collect {} }
        main.runCurrent()

        vm.cancel()
        main.runCurrent()
        assertNull(vm.message.value)
        assertEquals(AssistanceStatus.CANCELLED, assistances.stored.value.getValue("l-1").status)

        vm.message.value = "aviso viejo"
        vm.dismissTracking()
        main.runCurrent()
        assertNull(vm.message.value)
    }
}
