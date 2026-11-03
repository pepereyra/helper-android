package com.helper.mobile.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val role: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class CreateClientRequest(val fullName: String, val phone: String)

/** El id y el email del mecánico salen del token. */
@Serializable
data class CreateMechanicRequest(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val skills: Set<String>,
    val vehicleTypes: Set<String>,
)

@Serializable
data class AvailabilityRequest(val available: Boolean, val latitude: Double, val longitude: Double)

/** Respuesta de login (B7). El rol y la verificación vienen del servidor, nunca de la app. */
@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
    val userId: String,
    val role: String,
    val verificationStatus: String? = null,
)

@Serializable
data class CreateVehicleRequest(
    val type: String,
    val brand: String,
    val model: String,
    val color: String? = null,
    val plate: String? = null,
)

@Serializable
data class VehicleResponse(val id: String)

/** Mismo cuerpo que POST /api/assistances del backend, más clientCreatedAt (B6). */
@Serializable
data class CreateAssistanceRequest(
    val clientId: String,
    val vehicleId: String,
    val description: String,
    val problemType: String,
    val requiredSkill: String,
    val latitude: Double,
    val longitude: Double,
    val clientCreatedAt: String,
)

@Serializable
data class AssistanceResponse(
    val id: String,
    val status: String,
    val assignedMechanicId: String? = null,
    val offersExpireAt: String? = null,
)

/** GET /api/dispatches/offers: trae la habilidad requerida y la distancia en km. */
@Serializable
data class OfferResponse(
    val assistanceId: String,
    val mechanicId: String? = null,
    val requiredSkill: String,
    val vehicleType: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val distanceKm: Double,
    val ranking: Int? = null,
    val expiresAt: String,
)


/** Cuerpo de error Problem Details que ya devuelve el backend. */
@Serializable
data class ProblemDetail(
    val title: String? = null,
    val detail: String? = null,
    val code: String? = null,
)
