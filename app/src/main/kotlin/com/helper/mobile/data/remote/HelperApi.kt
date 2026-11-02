package com.helper.mobile.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Contrato con el backend (servicios detrás del nginx).
 * Los endpoints de login (B7), ofertas (B5) e Idempotency-Key (B6) son los acordados para la Segunda Parte de DA II.
 */
interface HelperApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<TokenResponse>

    @POST("api/clients/{clientId}/vehicles")
    suspend fun registerVehicle(
        @Path("clientId") clientId: String,
        @Body body: CreateVehicleRequest,
    ): Response<VehicleResponse>

    /** Reintentar con la misma clave devuelve el mismo pedido: nunca se duplica. */
    @POST("api/assistances")
    suspend fun createAssistance(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: CreateAssistanceRequest,
    ): Response<AssistanceResponse>

    @GET("api/assistances/{id}")
    suspend fun assistance(@Path("id") id: String): Response<AssistanceResponse>

    @POST("api/assistances/{id}/cancel")
    suspend fun cancel(@Path("id") id: String): Response<AssistanceResponse>

    /** Cancela por clave cuando el teléfono no sabe si el envío llegó; 404 si nunca llegó. */
    @POST("api/assistances/cancel-by-key")
    suspend fun cancelByKey(@Header("Idempotency-Key") idempotencyKey: String): Response<Unit>

    @GET("api/dispatches/offers")
    suspend fun offers(): Response<List<OfferResponse>>

    /** El primero que acepta gana; los demás reciben 409. */
    @POST("api/dispatches/{assistanceId}/accept")
    suspend fun accept(@Path("assistanceId") assistanceId: String): Response<Unit>

    @POST("api/dispatches/{assistanceId}/decline")
    suspend fun decline(@Path("assistanceId") assistanceId: String): Response<Unit>
}
