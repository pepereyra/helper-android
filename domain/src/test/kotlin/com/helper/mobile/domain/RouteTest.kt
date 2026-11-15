package com.helper.mobile.domain

import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.Route
import com.helper.mobile.domain.model.RouteStep
import com.helper.mobile.domain.model.isOffRoute
import com.helper.mobile.domain.model.progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {

    private val start = GeoPoint(-34.6045, -58.3830)
    private val corner = GeoPoint(-34.6037, -58.3830)
    private val rider = GeoPoint(-34.6037, -58.3816)
    private val turnRight = RouteStep("Girá a la derecha por Corrientes", 130.0, corner)
    private val arrive = RouteStep("Llegaste a tu destino", 0.0, rider)
    private val route = Route(
        points = listOf(start, GeoPoint(-34.6041, -58.3830), corner, GeoPoint(-34.6037, -58.3823), rider),
        steps = listOf(RouteStep("Dirigite al norte por Sarmiento", 90.0, start), turnRight, arrive),
        distanceMeters = 220.0,
        durationMinutes = 1,
        byRoad = true,
    )

    @Test
    fun atTheStartTheNextStepIsTheFirstTurn() {
        val progress = route.progress(start, rider)

        assertEquals(turnRight, progress.nextStep)
        assertEquals(89.0, progress.metersToNextStep ?: 0.0, 2.0)
        assertFalse(progress.arrived)
    }

    @Test
    fun afterTheCornerTheNextStepIsTheArrival() {
        assertEquals(arrive, route.progress(GeoPoint(-34.6037, -58.3824), rider).nextStep)
    }

    @Test
    fun whatIsLeftIsMeasuredAlongTheRoute() {
        val atTheCorner = route.progress(corner, rider)

        // Del recodo al repartidor quedan ~128 m por la línea, la mitad larga de la ruta.
        assertEquals(128.0, atTheCorner.metersToDestination, 5.0)
        assertEquals(1, atTheCorner.minutesLeft)
    }

    @Test
    fun closeToTheRiderItHasArrived() {
        assertTrue(route.progress(GeoPoint(-34.6037, -58.3818), rider).arrived)
    }

    @Test
    fun farFromTheLineItIsOffRoute() {
        assertFalse(route.isOffRoute(GeoPoint(-34.6040, -58.3831)))
        assertTrue(route.isOffRoute(GeoPoint(-34.6060, -58.3800)))
    }
}
