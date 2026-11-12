package com.helper.mobile.data.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.repository.LocationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Una sola lectura, sin ubicación en segundo plano. Si el GPS no responde en 10 segundos se usa la última
 * ubicación conocida, para que el botón no quede esperando indefinidamente.
 */
class FusedLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationProvider {

    // El permiso se pide en la UI; si no está, la llamada falla y devolvemos null.
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GeoPoint? = runCatching {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancel = CancellationTokenSource()
        val fresh = withTimeoutOrNull(TIMEOUT_MS) {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token).awaitOrNull()
        }
        cancel.cancel()
        (fresh ?: client.lastLocation.awaitOrNull())?.let { GeoPoint(it.latitude, it.longitude) }
    }.getOrNull()

    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
