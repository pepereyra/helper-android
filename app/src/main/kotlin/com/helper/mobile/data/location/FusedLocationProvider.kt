package com.helper.mobile.data.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.repository.LocationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/** Una sola lectura con precisión balanceada: cuida la batería y no usa ubicación en segundo plano. */
class FusedLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationProvider {

    // El permiso se pide en la UI; si no está, la llamada falla y devolvemos null.
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GeoPoint? = runCatching {
        suspendCancellableCoroutine { continuation ->
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    continuation.resume(location?.let { GeoPoint(it.latitude, it.longitude) })
                }
                .addOnFailureListener { continuation.resume(null) }
        }
    }.getOrNull()
}
