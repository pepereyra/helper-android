package com.helper.mobile.data

import com.helper.mobile.data.remote.HelperApi
import com.helper.mobile.data.remote.NearbyMechanicResponse
import com.helper.mobile.data.remote.RouteResponse
import com.helper.mobile.data.remote.RouteStepResponse
import com.helper.mobile.data.repository.NearbyMechanicsRepositoryImpl
import com.helper.mobile.data.repository.RouteRepositoryImpl
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.MechanicKind
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/** Los repositorios traducen lo que manda el backend al modelo de la app. */
class MapRepositoriesTest {

    private val api = mockk<HelperApi>()
    private val from = GeoPoint(-34.6083, -58.3712)
    private val to = GeoPoint(-34.6037, -58.3816)

    @Test
    fun aRouteByStreetKeepsItsLineAndSteps() = runTest {
        coEvery { api.route(any(), any(), any(), any()) } returns Response.success(
            RouteResponse(
                source = "ROUTE",
                distanceKm = 1.6,
                durationMinutes = 9,
                points = listOf(listOf(-34.6083, -58.3712), listOf(-34.6061, -58.3808), listOf(-34.6037, -58.3816)),
                steps = listOf(RouteStepResponse("Gire a la derecha", "Carlos Pellegrini", 162, 40, -34.6061, -58.3808)),
            ),
        )

        val route = RouteRepositoryImpl(api).route(from, to)

        assertTrue(route!!.byRoad)
        assertEquals(1600.0, route.distanceMeters, 0.1)
        assertEquals(3, route.points.size)
        assertEquals("Gire a la derecha", route.steps.single().instruction)
        assertEquals(GeoPoint(-34.6061, -58.3808), route.steps.single().location)
    }

    @Test
    fun aStraightLineIsNotByRoad() = runTest {
        coEvery { api.route(any(), any(), any(), any()) } returns
            Response.success(RouteResponse("ESTIMATE", 0.9, 4, listOf(listOf(-34.6083, -58.3712), listOf(-34.6037, -58.3816))))

        assertFalse(RouteRepositoryImpl(api).route(from, to)!!.byRoad)
    }

    @Test
    fun withoutConnectionThereIsNoRoute() = runTest {
        coEvery { api.route(any(), any(), any(), any()) } throws IOException("sin red")

        assertNull(RouteRepositoryImpl(api).route(from, to))
    }

    @Test
    fun nearbyMechanicsCarryTheirKind() = runTest {
        coEvery { api.nearbyMechanics(any(), any(), any()) } returns Response.success(
            listOf(
                NearbyMechanicResponse("m-1", "Bicicleteria Demo", "MOBILE", -34.6037, -58.3816, 0.0),
                NearbyMechanicResponse("m-2", "Taller Sur", "WORKSHOP", -34.6036, -58.3902, 0.79),
                NearbyMechanicResponse("m-3", "Perfil viejo", null, -34.6040, -58.3820, 0.05),
            ),
        )

        val nearby = NearbyMechanicsRepositoryImpl(api).near(to, null)!!

        assertEquals(listOf(MechanicKind.MOBILE, MechanicKind.WORKSHOP, MechanicKind.MOBILE), nearby.map { it.kind })
        assertEquals(790.0, nearby[1].distanceMeters, 0.1)
    }
}
