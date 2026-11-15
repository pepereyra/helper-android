package com.helper.mobile.ui.common

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint as OsmPoint

/** Un punto en el mapa. [isMain] es el del usuario (o el pedido, para el mecánico): va en naranja. */
data class MapPin(val point: GeoPoint, val title: String, val isMain: Boolean = false)

private const val ORANGE = 0xFFE2661D.toInt()
private const val BLUE = 0xFF2F5BD3.toInt()
private const val SINGLE_PIN_ZOOM = 16.0

/**
 * Mapa de OpenStreetMap (osmdroid): no pide clave de API. Encuadra todos los pines cada vez que cambian.
 * [description] es lo que lee TalkBack, porque el mapa en sí no es accesible.
 */
@Composable
fun PinsMap(pins: List<MapPin>, description: String, modifier: Modifier = Modifier, height: Dp = 220.dp) {
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
        update = { view -> show(view, pins) },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = description },
    )
}

private fun createMap(context: Context): MapView {
    Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        controller.setZoom(SINGLE_PIN_ZOOM)
    }
}

private fun show(map: MapView, pins: List<MapPin>) {
    map.overlays.removeAll { it is Marker }
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
    if (pins.isEmpty()) return
    // El encuadre necesita el tamaño de la vista: si todavía no se midió, se hace después del primer layout.
    if (map.width == 0 || map.height == 0) {
        map.addOnFirstLayoutListener { _, _, _, _, _ -> frame(map, pins) }
    } else {
        frame(map, pins)
    }
}

private fun frame(map: MapView, pins: List<MapPin>) {
    if (pins.size == 1) {
        map.controller.setZoom(SINGLE_PIN_ZOOM)
        map.controller.setCenter(pins.first().point.toOsm())
        return
    }
    val box = BoundingBox.fromGeoPoints(pins.map { it.point.toOsm() })
    // Margen para que los pines no queden pegados al borde; con pines muy juntos no se acerca de más.
    map.zoomToBoundingBox(box.increaseByScale(1.6f), false, 48)
    if (map.zoomLevelDouble > 17.0) map.controller.setZoom(17.0)
}

private fun tinted(context: Context, color: Int): Drawable? =
    ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.also {
        DrawableCompat.setTint(it, color)
    }

private fun GeoPoint.toOsm() = OsmPoint(latitude, longitude)
