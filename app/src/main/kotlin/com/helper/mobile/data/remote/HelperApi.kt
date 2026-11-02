package com.helper.mobile.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Contrato con el backend (servicios detrás del nginx).
 * Ofertas (B5) e Idempotency-Key (B6) siguen lo implementado en mechanic-helper (docs/OFERTAS_Y_ACEPTACION.md).
 * Hasta que exista el login con JWT (B7), la identidad viaja como clientId o mechanicId; con B7 pasa al token.
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
    suspend fun cancelByKey(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Query("clientId") clientId: String,
    ): Response<AssistanceResponse>

    @GET("api/dispatches/offers")
    suspend fun offers(@Query("mechanicId") mechanicId: String): Response<List<OfferResponse>>

    /** El primero que acepta gana; los demás reciben 409. */
    @POST("api/dispatches/{assistanceId}/accept")
    suspend fun accept(
        @Path("assistanceId") assistanceId: String,
        @Body body: MechanicRef,
    ): Response<Unit>

    @POST("api/dispatches/{assistanceId}/decline")
    /** Responde 204. */
    suspend fun decline(
        @Path("assistanceId") assistanceId: String,
        @Body body: MechanicRef,
    ): Response<Unit>
}
