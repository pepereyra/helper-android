package com.helper.mobile.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.helper.mobile.domain.model.GeoPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val INK = 0xFF2B2B2B.toInt()
private const val OVERVIEW_MAX_ZOOM = 18.0
private const val TRAVEL_ZOOM = 18.5

/**
 * Mapa de navegación del mecánico, en dos colores: las calles en gris y la ruta en naranja.
 *
 * Sin [traveling] encuadra el camino completo, del mecánico al repartidor. Con [traveling] pasa a posición de
 * viaje: sigue al mecánico de cerca, con su flecha en el tercio de abajo, y gira el mapa para que hacia donde va
 * quede siempre arriba.
 */
@Composable
fun NavigationMap(
    rider: GeoPoint,
    me: GeoPoint?,
    route: List<GeoPoint>,
    traveling: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = remember { NavigationMapState(createMap(context).apply { twoColors() }) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.map.onResume()
                Lifecycle.Event.ON_PAUSE -> state.map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            state.map.onDetach()
        }
    }

    AndroidView(
        factory = { state.map },
        update = { state.show(rider, me, route, traveling) },
        modifier = modifier.semantics {
            contentDescription = if (traveling) "Mapa en modo viaje, siguiendo tu posición" else "Mapa con el camino hasta el repartidor"
        },
    )
}

private class NavigationMapState(val map: MapView) {
    private var framedRoute: List<GeoPoint>? = null
    private var wasTraveling = false

    fun show(rider: GeoPoint, me: GeoPoint?, route: List<GeoPoint>, traveling: Boolean) {
        map.overlays.removeAll { it is Marker || it is Polyline }
        val line = route.takeIf { it.size >= 2 } ?: listOfNotNull(me, rider)
        if (line.size >= 2) {
            // Borde blanco abajo y naranja arriba: la ruta se distingue sobre el gris sin sumar otro color.
            map.overlays.add(line(map, line, android.graphics.Color.WHITE, 20f))
            map.overlays.add(line(map, line, ORANGE, 12f))
        }
        map.overlays.add(
            Marker(map).apply {
                position = rider.toOsm()
                title = "Repartidor"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = tinted(map.context, ORANGE)
            },
        )
        val heading = me?.let { headingAlong(route, it) }
        if (me != null) {
            map.overlays.add(
                Marker(map).apply {
                    position = me.toOsm()
                    title = "Vos"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = arrow(map.context)
                    isFlat = true
                    rotation = -(heading ?: 0f)
                },
            )
        }
        map.invalidate()
        whenLaidOut {
            if (traveling && me != null) {
                follow(me, heading ?: 0f)
            } else if (!traveling && (wasTraveling || framedRoute != line)) {
                // Se encuadra solo cuando cambia la ruta: si no, el mapa saltaría con cada lectura del GPS.
                overview(line + listOfNotNull(me, rider))
                framedRoute = line
            }
            wasTraveling = traveling
        }
    }

    private fun follow(me: GeoPoint, heading: Float) {
        // La flecha queda en el tercio de abajo, así se ve más camino por delante.
        map.setMapCenterOffset(0, map.height / 4)
        map.mapOrientation = -heading
        if (!wasTraveling) map.controller.setZoom(TRAVEL_ZOOM)
        map.controller.animateTo(me.toOsm())
    }

    private fun overview(points: List<GeoPoint>) {
        map.setMapCenterOffset(0, 0)
        map.mapOrientation = 0f
        if (points.size == 1) {
            map.controller.setZoom(OVERVIEW_MAX_ZOOM - 1)
            map.controller.setCenter(points.first().toOsm())
            return
        }
        val box = BoundingBox.fromGeoPoints(points.map { it.toOsm() })
        map.zoomToBoundingBox(box.increaseByScale(1.15f), false, 64)
        if (map.zoomLevelDouble > OVERVIEW_MAX_ZOOM) map.controller.setZoom(OVERVIEW_MAX_ZOOM)
    }

    private fun whenLaidOut(block: () -> Unit) {
        if (map.width == 0 || map.height == 0) {
            map.addOnFirstLayoutListener { _, _, _, _, _ -> block() }
        } else {
            block()
        }
    }
}

/** Calles en gris con más contraste: el único color fuerte del mapa es el naranja de la ruta. */
private fun MapView.twoColors() {
    val gray = ColorMatrix().apply { setSaturation(0f) }
    val contrast = 1.25f
    val shift = -0.06f * 255
    gray.postConcat(
        ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, shift,
                0f, contrast, 0f, 0f, shift,
                0f, 0f, contrast, 0f, shift,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(gray))
    maxZoomLevel = 19.0
}

private fun line(map: MapView, points: List<GeoPoint>, color: Int, width: Float) = Polyline(map).apply {
    setPoints(points.map { it.toOsm() })
    outlinePaint.color = color
    outlinePaint.strokeWidth = width
    outlinePaint.strokeCap = Paint.Cap.ROUND
    outlinePaint.strokeJoin = Paint.Join.ROUND
    isGeodesic = false
    infoWindow = null
}

/**
 * Hacia dónde va el mecánico, en grados desde el norte: del punto de la ruta más cercano a uno un poco más
 * adelante. Sin ruta, null.
 */
private fun headingAlong(route: List<GeoPoint>, me: GeoPoint): Float? {
    if (route.size < 2) return null
    val nearest = route.indices.minBy { route[it].distanceTo(me) }
    val ahead = (nearest + 1 until route.size).firstOrNull { route[it].distanceTo(me) > 25 } ?: route.lastIndex
    if (ahead == nearest) return null
    return bearing(me, route[ahead])
}

private fun bearing(from: GeoPoint, to: GeoPoint): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
}

/** Flecha oscura con borde blanco, apuntando hacia arriba (el norte, o hacia donde va en modo viaje). */
private fun arrow(context: Context): Drawable {
    val size = (36 * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val path = Path().apply {
        moveTo(size / 2f, size * 0.08f)
        lineTo(size * 0.88f, size * 0.9f)
        lineTo(size / 2f, size * 0.7f)
        lineTo(size * 0.12f, size * 0.9f)
        close()
    }
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = size * 0.1f; strokeJoin = Paint.Join.ROUND })
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; style = Paint.Style.FILL })
    return BitmapDrawable(context.resources, bitmap)
}
