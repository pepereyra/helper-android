package com.helper.mobile.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Contrato con el backend. Todo pasa por el nginx del docker compose (puerto 8080), que reparte por prefijo:
 * /identity, /mechanic, /assistance y /dispatch, cada uno hacia su servicio.
 * Ofertas (B5) e Idempotency-Key (B6) siguen lo implementado en mechanic-helper (docs/OFERTAS_Y_ACEPTACION.md).
 * Login con JWT (B7): el id del repartidor o del mecánico sale del token, nunca de un parámetro.
 */
interface HelperApi {

    @POST("identity/api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<TokenResponse>

    @POST("identity/api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<TokenResponse>

    /** Rota el refresh token: el usado deja de servir. */
    @POST("identity/api/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<TokenResponse>

    @POST("identity/api/auth/logout")
    suspend fun logout(@Body body: RefreshRequest): Response<Unit>

    @POST("assistance/api/clients")
    suspend fun createClient(@Body body: CreateClientRequest): Response<Unit>

    @GET("assistance/api/clients/{id}")
    suspend fun client(@Path("id") id: String): Response<Unit>

    @POST("mechanic/api/mechanics")
    suspend fun createMechanic(@Body body: CreateMechanicRequest): Response<Unit>

    @GET("mechanic/api/mechanics/{id}")
    suspend fun mechanic(@Path("id") id: String): Response<Unit>

    /** 403 mientras el mecánico está pendiente de validación. */
    @PATCH("mechanic/api/mechanics/{id}/availability")
    suspend fun availability(@Path("id") id: String, @Body body: AvailabilityRequest): Response<Unit>

    @POST("assistance/api/clients/{clientId}/vehicles")
    suspend fun registerVehicle(
        @Path("clientId") clientId: String,
        @Body body: CreateVehicleRequest,
    ): Response<VehicleResponse>

    /** Reintentar con la misma clave devuelve el mismo pedido: nunca se duplica. */
    @POST("assistance/api/assistances")
    suspend fun createAssistance(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: CreateAssistanceRequest,
    ): Response<AssistanceResponse>

    @GET("assistance/api/assistances/{id}")
    suspend fun assistance(@Path("id") id: String): Response<AssistanceResponse>

    @POST("assistance/api/assistances/{id}/cancel")
    suspend fun cancel(@Path("id") id: String): Response<AssistanceResponse>

    /** Cancela por clave cuando el teléfono no sabe si el envío llegó; 404 si nunca llegó. */
    @POST("assistance/api/assistances/cancel-by-key")
    suspend fun cancelByKey(@Header("Idempotency-Key") idempotencyKey: String): Response<AssistanceResponse>

    @GET("dispatch/api/dispatches/offers")
    suspend fun offers(): Response<List<OfferResponse>>

    /** El primero que acepta gana; los demás reciben 409. */
    @POST("dispatch/api/dispatches/{assistanceId}/accept")
    suspend fun accept(@Path("assistanceId") assistanceId: String): Response<Unit>

    @POST("dispatch/api/dispatches/{assistanceId}/decline")
    /** Responde 204. */
    suspend fun decline(@Path("assistanceId") assistanceId: String): Response<Unit>
}
