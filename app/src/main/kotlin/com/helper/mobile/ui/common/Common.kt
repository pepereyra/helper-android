package com.helper.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.VehicleType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Warning = Color(0xFFFFE8A3)
private val HOUR = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

fun Instant.asHour(): String = HOUR.format(this)

/** Banner amarillo de "sin conexión"; TalkBack lo anuncia cuando aparece. */
@Composable
fun OfflineBanner(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF3A2E00),
        modifier = modifier
            .fillMaxWidth()
            .background(Warning, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

fun VehicleType.label(): String = when (this) {
    VehicleType.BICYCLE -> "Bici"
    VehicleType.E_BIKE -> "Bici eléctrica"
    VehicleType.MOTORCYCLE -> "Moto"
}

fun AssistanceStatus.label(): String = when (this) {
    AssistanceStatus.NOT_SENT -> "Sin enviar"
    AssistanceStatus.EXPIRED -> "Vencido sin enviar"
    AssistanceStatus.REQUESTED -> "Buscando mecánico"
    AssistanceStatus.MATCHED -> "Un mecánico aceptó"
    AssistanceStatus.IN_PROGRESS -> "En curso"
    AssistanceStatus.COMPLETED -> "Terminado"
    AssistanceStatus.CANCELLED -> "Cancelado"
    AssistanceStatus.UNMATCHED -> "Nadie lo tomó"
}

fun Double.asDistance(): String = if (this < 1000) "${toInt()} m" else "%.1f km".format(this / 1000)

/** Minutos estimados de llegada, como "3 min". */
fun Int.asEta(): String = "${coerceAtLeast(1)} min"

/**
 * E6 · Error con una acción para resolverlo. [onRetry] null cuando no hay nada que reintentar
 * (por ejemplo, un dato que el usuario tiene que corregir).
 */
@Composable
fun ErrorCard(text: String, onDismiss: () -> Unit, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onRetry != null) Button(onClick = onRetry) { Text("Reintentar") }
                TextButton(onClick = onDismiss) { Text(if (onRetry != null) "Cerrar" else "Entendido") }
            }
        }
    }
}

/** E1 · Cargando: bloques grises con la forma de la pantalla, en vez de una pantalla en blanco. */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier
            .fillMaxSize()
            .padding(20.dp)
            .semantics { contentDescription = "Cargando" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkeletonBlock(block, 32.dp, 0.5f)
        SkeletonBlock(block, 180.dp)
        SkeletonBlock(block, 56.dp)
        SkeletonBlock(block, 56.dp, 0.7f)
        SkeletonBlock(block, 56.dp)
    }
}

@Composable
private fun SkeletonBlock(color: Color, height: Dp, widthFraction: Float = 1f) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(color, RoundedCornerShape(10.dp)),
    )
}
