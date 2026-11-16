package com.helper.mobile.data.local

import com.helper.mobile.domain.model.MechanicKind
import com.helper.mobile.domain.model.AssignedMechanic
import com.helper.mobile.domain.model.Assistance
import com.helper.mobile.domain.model.AssistanceStatus
import com.helper.mobile.domain.model.GeoPoint
import com.helper.mobile.domain.model.Offer
import com.helper.mobile.domain.model.Vehicle
import com.helper.mobile.domain.model.VehicleType
import java.time.Instant

fun VehicleEntity.toDomain() = Vehicle(
    id = id,
    type = VehicleType.valueOf(type),
    brand = brand,
    model = model,
    color = color,
    plate = plate,
)

fun Vehicle.toEntity(remoteId: String?) = VehicleEntity(
    id = id,
    remoteId = remoteId,
    type = type.name,
    brand = brand,
    model = model,
    color = color,
    plate = plate,
)

fun AssistanceEntity.toDomain() = Assistance(
    localId = localId,
    remoteId = remoteId,
    vehicleId = vehicleId,
    vehicleType = VehicleType.valueOf(vehicleType),
    problemCode = problemCode,
    description = description,
    location = GeoPoint(latitude, longitude),
    confirmedAt = Instant.ofEpochMilli(confirmedAtMillis),
    status = AssistanceStatus.valueOf(status),
    mechanic = mechanicId?.let {
        AssignedMechanic(
            id = it,
            name = mechanicName ?: "",
            rating = mechanicRating,
            distanceMeters = mechanicDistanceMeters,
            etaMinutes = mechanicEtaMinutes,
            location = if (mechanicLatitude != null && mechanicLongitude != null) {
                GeoPoint(mechanicLatitude, mechanicLongitude)
            } else {
                null
            },
            phone = mechanicPhone,
            kind = MechanicKind.from(mechanicKind),
            address = mechanicAddress,
        )
    },
    lastSyncedAt = lastSyncedAtMillis?.let(Instant::ofEpochMilli),
    needsLocationConfirmation = needsLocationConfirmation,
    rating = rating,
)

fun Assistance.toEntity() = AssistanceEntity(
    localId = localId,
    remoteId = remoteId,
    vehicleId = vehicleId,
    vehicleType = vehicleType.name,
    problemCode = problemCode,
    description = description,
    latitude = location.latitude,
    longitude = location.longitude,
    confirmedAtMillis = confirmedAt.toEpochMilli(),
    status = status.name,
    mechanicId = mechanic?.id,
    mechanicName = mechanic?.name,
    mechanicRating = mechanic?.rating,
    mechanicDistanceMeters = mechanic?.distanceMeters,
    mechanicEtaMinutes = mechanic?.etaMinutes,
    mechanicLatitude = mechanic?.location?.latitude,
    mechanicLongitude = mechanic?.location?.longitude,
    mechanicPhone = mechanic?.phone,
    mechanicKind = mechanic?.kind?.name,
    mechanicAddress = mechanic?.address,
    lastSyncedAtMillis = lastSyncedAt?.toEpochMilli(),
    needsLocationConfirmation = needsLocationConfirmation,
    rating = rating,
)

fun OfferEntity.toDomain() = Offer(
    assistanceId = assistanceId,
    problemLabel = problemLabel,
    vehicleType = VehicleType.valueOf(vehicleType),
    distanceMeters = distanceMeters,
    expiresAt = Instant.ofEpochMilli(expiresAtMillis),
    location = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null,
    etaMinutes = etaMinutes,
    distanceByRoad = distanceByRoad,
)
