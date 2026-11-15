package com.helper.mobile.domain.model

/**
 * Ruta del mecánico hasta el repartidor. [byRoad] es false cuando el servidor no pudo calcularla por calle:
 * entonces es una línea recta y no trae pasos.
 */
data class Route(
    val points: List<GeoPoint>,
    val steps: List<RouteStep>,
    val distanceMeters: Double,
    val durationMinutes: Int,
    val byRoad: Boolean,
)

/** Un paso de la ruta: la indicación empieza en [location]. */
data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val location: GeoPoint,
)

/** Cómo va el mecánico respecto de la ruta: el próximo paso y cuánto falta. */
data class NavigationProgress(
    val nextStep: RouteStep?,
    val metersToNextStep: Double?,
    /** Por la ruta (o en línea recta si no hay ruta por calle). */
    val metersToDestination: Double,
    val arrived: Boolean,
    /** Minutos que faltan, en proporción a lo que queda de la ruta. */
    val minutesLeft: Int = 0,
)

/**
 * Calcula el próximo paso con la posición actual: el primer paso que empieza después del punto de la ruta más
 * cercano al mecánico. Se considera que llegó a menos de [ARRIVAL_METERS] del destino.
 */
fun Route.progress(here: GeoPoint, destination: GeoPoint): NavigationProgress {
    val toDestination = here.distanceTo(destination)
    if (toDestination <= ARRIVAL_METERS) {
        return NavigationProgress(nextStep = null, metersToNextStep = null, metersToDestination = toDestination, arrived = true)
    }
    val nearest = points.indices.minByOrNull { points[it].distanceTo(here) } ?: 0
    val next = steps.firstOrNull { step -> indexOf(step.location) > nearest }
    // Lo que falta se mide sobre la línea, desde el punto más cercano: en línea recta engañaría en una ruta con vueltas.
    val alongRoute = if (points.size >= 2) {
        here.distanceTo(points[nearest]) + (nearest until points.lastIndex).sumOf { points[it].distanceTo(points[it + 1]) }
    } else {
        toDestination
    }
    val left = maxOf(alongRoute, toDestination)
    return NavigationProgress(
        nextStep = next,
        metersToNextStep = next?.location?.distanceTo(here),
        metersToDestination = left,
        arrived = false,
        minutesLeft = if (distanceMeters > 0) {
            kotlin.math.ceil(durationMinutes * minOf(1.0, left / distanceMeters)).toInt().coerceAtLeast(1)
        } else {
            durationMinutes
        },
    )
}

/** True si el mecánico se alejó tanto de la línea que conviene pedir la ruta de nuevo. */
fun Route.isOffRoute(here: GeoPoint): Boolean =
    byRoad && points.isNotEmpty() && points.minOf { it.distanceTo(here) } > OFF_ROUTE_METERS

private fun Route.indexOf(location: GeoPoint): Int =
    points.indices.minByOrNull { points[it].distanceTo(location) } ?: 0

const val ARRIVAL_METERS = 30.0
private const val OFF_ROUTE_METERS = 60.0
