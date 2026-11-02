package com.helper.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Vehículo del repartidor. [remoteId] queda null hasta que el backend lo registra. */
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val remoteId: String?,
    val type: String,
    val brand: String,
    val model: String,
    val color: String?,
    val plate: String?,
)

/**
 * Pedido de asistencia. [localId] se genera en el teléfono y es la clave de idempotencia;
 * [remoteId] lo asigna el servidor. Las fechas se guardan en milisegundos epoch.
 */
@Entity(tableName = "assistances")
data class AssistanceEntity(
    @PrimaryKey val localId: String,
    val remoteId: String?,
    val vehicleId: String,
    val vehicleType: String,
    val problemCode: String,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val confirmedAtMillis: Long,
    val status: String,
    val mechanicId: String?,
    val mechanicName: String?,
    val mechanicRating: Double?,
    val mechanicDistanceMeters: Double?,
    val lastSyncedAtMillis: Long?,
    val needsLocationConfirmation: Boolean,
)

@Entity(tableName = "offers")
data class OfferEntity(
    @PrimaryKey val assistanceId: String,
    val problemLabel: String,
    val vehicleType: String,
    val distanceMeters: Double,
    val expiresAtMillis: Long,
)
