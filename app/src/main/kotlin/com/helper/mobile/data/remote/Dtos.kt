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
    val phone: String? = null,
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
    val assignedEtaMinutes: Int? = null,
    val assignedDistanceKm: Double? = null,
    val offersExpireAt: String? = null,
    val problemType: String? = null,
    val vehicleType: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val rating: Int? = null,
)

/** POST /api/assistances/{id}/rating: de 1 a 5, con un comentario opcional. */
@Serializable
data class RateRequest(val score: Int, val comment: String? = null)

/** GET /api/clients/{id}: el perfil del repartidor. */
@Serializable
data class ClientResponse(
    val id: String,
    val fullName: String,
    val phone: String? = null,
    val email: String? = null,
)

/** GET /api/mechanics/{id}: lo que la app usa del perfil del mecánico asignado. */
@Serializable
data class MechanicResponse(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val latitude: Double,
    val longitude: Double,
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val available: Boolean = false,
)

/** GET /api/mechanics/nearby: mecánicos disponibles cerca, sin datos de contacto. */
@Serializable
data class NearbyMechanicResponse(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double,
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
    val etaMinutes: Int? = null,
    /** ROUTE si la distancia es por calle; si no, es en línea recta. */
    val etaSource: String? = null,
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

/** GET /api/routes: la línea para el mapa ([latitud, longitud]) y los pasos en castellano. */
@Serializable
data class RouteResponse(
    val source: String,
    val distanceKm: Double,
    val durationMinutes: Int,
    val points: List<List<Double>>,
    val steps: List<RouteStepResponse> = emptyList(),
)

@Serializable
data class RouteStepResponse(
    val instruction: String,
    val street: String? = null,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val latitude: Double,
    val longitude: Double,
)

/** GET /api/assistances/{id}/history. */
@Serializable
data class StatusChangeResponse(
    val fromStatus: String? = null,
    val toStatus: String,
    val changedAt: String,
)
