package com.helper.mobile.ui.common

import androidx.compose.foundation.background
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
