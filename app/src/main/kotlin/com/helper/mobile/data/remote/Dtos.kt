package com.helper.mobile.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

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

/** Identidad del mecánico en accept y decline hasta que llegue el login (B7). */
@Serializable
data class MechanicRef(val mechanicId: String)

/** Cuerpo de error Problem Details que ya devuelve el backend. */
@Serializable
data class ProblemDetail(
    val title: String? = null,
    val detail: String? = null,
    val code: String? = null,
)
