package com.helper.mobile.data.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Avisa qué pasó con un pedido que estaba sin enviar, para que el repartidor no tenga que adivinar. */
class PendingRequestNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun helpRequested() = notify(1, "Listo, ya pedimos ayuda", "Estamos buscando un mecánico cerca tuyo.")

    fun requestExpired() = notify(
        2,
        "Tu pedido no se envió",
        "Pasaron 15 minutos sin conexión. Abrí Helper si todavía necesitás ayuda.",
    )

    fun locationNeedsConfirmation() = notify(
        3,
        "Confirmá dónde estás",
        "Te moviste desde que pediste ayuda. Confirmá la ubicación para enviar el pedido.",
    )

    private fun notify(id: Int, title: String, text: String) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Estado del pedido", NotificationManager.IMPORTANCE_HIGH),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private companion object {
        const val CHANNEL_ID = "assistance-status"
    }
}
