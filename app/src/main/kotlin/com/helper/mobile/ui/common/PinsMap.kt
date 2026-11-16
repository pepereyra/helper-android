package com.helper.mobile.ui.common

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.helper.mobile.BuildConfig
import com.helper.mobile.domain.model.GeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.util.GeoPoint as OsmPoint

/** Un punto en el mapa. [isMain] es el del usuario (o el pedido, para el mecánico): va en naranja. */
data class MapPin(val point: GeoPoint, val title: String, val isMain: Boolean = false)

internal const val ORANGE = 0xFFE2661D.toInt()
private const val BLUE = 0xFF2F5BD3.toInt()
private const val SINGLE_PIN_ZOOM = 16.0

/**
 * Mapa de OpenStreetMap (osmdroid): no pide clave de API. Encuadra todos los pines cada vez que cambian.
 * [description] es lo que lee TalkBack, porque el mapa en sí no es accesible.
 */
@Composable
fun PinsMap(
    pins: List<MapPin>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    route: List<GeoPoint> = emptyList(),
) {
    val context = LocalContext.current
    val map = remember { createMap(context) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, map) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }

    AndroidView(
        factory = { map },
        update = { view -> show(view, pins, route) },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = description },
    )
}

internal fun createMap(context: Context): MapView {
    Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        controller.setZoom(SINGLE_PIN_ZOOM)
    }
}

private fun show(map: MapView, pins: List<MapPin>, route: List<GeoPoint>) {
    map.overlays.removeAll { it is Marker || it is Polyline }
    if (route.size >= 2) {
        map.overlays.add(
            Polyline(map).apply {
                setPoints(route.map { it.toOsm() })
                outlinePaint.color = BLUE
                outlinePaint.strokeWidth = 12f
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            },
        )
    }
    // El pin principal va último para que quede arriba si se superpone con otro.
    pins.sortedBy { it.isMain }.forEach { pin ->
        map.overlays.add(
            Marker(map).apply {
                position = pin.point.toOsm()
                title = pin.title
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = tinted(map.context, if (pin.isMain) ORANGE else BLUE)
            },
        )
    }
    map.invalidate()
    val framed = pins.map { it.point } + route
    if (framed.isEmpty()) return
    // El encuadre necesita el tamaño de la vista: si todavía no se midió, se hace después del primer layout.
    if (map.width == 0 || map.height == 0) {
        map.addOnFirstLayoutListener { _, _, _, _, _ -> frame(map, framed) }
    } else {
        frame(map, framed)
    }
}

private fun frame(map: MapView, points: List<GeoPoint>) {
    if (points.size == 1) {
        map.controller.setZoom(SINGLE_PIN_ZOOM)
        map.controller.setCenter(points.first().toOsm())
        return
    }
    val box = BoundingBox.fromGeoPoints(points.map { it.toOsm() })
    // Margen para que los pines no queden pegados al borde; con pines muy juntos no se acerca de más.
    map.zoomToBoundingBox(box.increaseByScale(1.6f), false, 48)
    if (map.zoomLevelDouble > 17.0) map.controller.setZoom(17.0)
}

internal fun tinted(context: Context, color: Int): Drawable? =
    ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.also {
        DrawableCompat.setTint(it, color)
    }

internal fun GeoPoint.toOsm() = OsmPoint(latitude, longitude)

private val BUENOS_AIRES = GeoPoint(-34.6037, -58.3816)

/**
 * E3 · Sin permiso de ubicación: el repartidor toca el mapa para marcar dónde está. Arranca en el centro de
 * Buenos Aires, o en [selected] si ya marcó un lugar.
 */
@Composable
fun PickLocationMap(
    selected: GeoPoint?,
    onPick: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
) {
    val context = LocalContext.current
    val map = remember {
        createMap(context).apply {
            controller.setZoom(14.0)
            controller.setCenter((selected ?: BUENOS_AIRES).toOsm())
        }
    }
    val pick by rememberUpdatedState(onPick)

    DisposableEffect(map) {
        val taps = MapEventsOverlay(
            object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: OsmPoint): Boolean {
                    pick(GeoPoint(p.latitude, p.longitude))
                    return true
                }

                override fun longPressHelper(p: OsmPoint): Boolean = false
            },
        )
        map.overlays.add(0, taps)
        onDispose { map.overlays.remove(taps) }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, map) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }

    AndroidView(
        factory = { map },
        update = { view ->
            view.overlays.removeAll { it is Marker }
            if (selected != null) {
                view.overlays.add(
                    Marker(view).apply {
                        position = selected.toOsm()
                        title = "Estás acá"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = tinted(view.context, ORANGE)
                    },
                )
            }
            view.invalidate()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = if (selected == null) "Mapa: tocá el lugar donde estás" else "Mapa: lugar marcado"
            },
    )
}
