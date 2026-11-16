package com.helper.mobile.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser

/** Qué dibuja cada pin: una bicicleta para el repartidor y una llave de tuercas para mecánicos y talleres. */
enum class PinIcon { BIKE, WRENCH }

// Íconos de Material Icons (licencia Apache 2.0), en una grilla de 24 x 24.
private const val BIKE_PATH =
    "M15.5,5.5c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM5,12c-2.8,0 -5,2.2 -5,5s2.2,5 5,5 5,-2.2 5,-5 " +
        "-2.2,-5 -5,-5zM5,20.5c-1.9,0 -3.5,-1.6 -3.5,-3.5s1.6,-3.5 3.5,-3.5 3.5,1.6 3.5,3.5 -1.6,3.5 -3.5,3.5zM10.8,10.5l2.4," +
        "-2.4 0.8,0.8c1.3,1.3 3,2.1 5.1,2.1L19.1,9c-1.5,0 -2.7,-0.6 -3.6,-1.5l-1.9,-1.9c-0.5,-0.4 -1,-0.6 -1.6,-0.6s-1.1,0.2 " +
        "-1.4,0.6L7.8,8.4c-0.4,0.4 -0.6,0.9 -0.6,1.4 0,0.6 0.2,1.1 0.6,1.4L11,14v5h2v-6.2l-2.2,-2.3zM19,12c-2.8,0 -5,2.2 " +
        "-5,5s2.2,5 5,5 5,-2.2 5,-5 -2.2,-5 -5,-5zM19,20.5c-1.9,0 -3.5,-1.6 -3.5,-3.5s1.6,-3.5 3.5,-3.5 3.5,1.6 3.5,3.5 " +
        "-1.6,3.5 -3.5,3.5z"
private const val WRENCH_PATH =
    "M22.7,19l-9.1,-9.1c0.9,-2.3 0.4,-5 -1.5,-6.9 -2,-2 -5,-2.4 -7.4,-1.3L9,6 6,9 1.6,4.7C0.4,7.1 0.9,10.1 2.9,12.1c1.9," +
        "1.9 4.6,2.4 6.9,1.5l9.1,9.1c0.4,0.4 1,0.4 1.4,0l2.3,-2.3c0.5,-0.4 0.5,-1.1 0.1,-1.4z"

private val cache = mutableMapOf<Pair<PinIcon, Int>, Bitmap>()

/**
 * Pin redondo con la punta abajo, del color de la paleta (naranja o gris oscuro), con el ícono en blanco.
 * El ancla va abajo al centro, en la punta.
 */
fun pinDrawable(context: Context, icon: PinIcon, color: Int): Drawable {
    val density = context.resources.displayMetrics.density
    val bitmap = cache.getOrPut(icon to color) { drawPin(icon, color, density) }
    return BitmapDrawable(context.resources, bitmap)
}

private fun drawPin(icon: PinIcon, color: Int, density: Float): Bitmap {
    val width = (40 * density).toInt()
    val height = (50 * density).toInt()
    val radius = width / 2f
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val body = Path().apply {
        addCircle(radius, radius, radius - density, Path.Direction.CW)
        moveTo(radius - radius * 0.45f, radius * 1.6f)
        lineTo(radius, height.toFloat() - density)
        lineTo(radius + radius * 0.45f, radius * 1.6f)
        close()
    }
    canvas.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    canvas.drawPath(
        body,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2 * density
        },
    )
    val glyph = PathParser.createPathFromPathData(if (icon == PinIcon.BIKE) BIKE_PATH else WRENCH_PATH)
    val size = radius * 1.15f
    glyph.transform(
        Matrix().apply {
            setScale(size / 24f, size / 24f)
            postTranslate(radius - size / 2, radius - size / 2)
        },
    )
    canvas.drawPath(glyph, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
    return bitmap
}
