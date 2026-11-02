package com.helper.mobile.data.remote

import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/** Resultado de una llamada HTTP, sin excepciones: los repositorios lo traducen a resultados del dominio. */
sealed interface ApiResult<out T> {
    data class Success<T>(val body: T) : ApiResult<T>
    data class HttpError(val status: Int, val problem: ProblemDetail?) : ApiResult<Nothing>
    data object NoConnection : ApiResult<Nothing>
}

private val errorJson = Json { ignoreUnknownKeys = true }

suspend fun <T> apiCall(call: suspend () -> Response<T>): ApiResult<T> =
    try {
        val response = call()
        if (response.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            ApiResult.Success(response.body() ?: Unit as T)
        } else {
            val problem = response.errorBody()?.string()?.let {
                runCatching { errorJson.decodeFromString(ProblemDetail.serializer(), it) }.getOrNull()
            }
            ApiResult.HttpError(response.code(), problem)
        }
    } catch (_: IOException) {
        ApiResult.NoConnection
    }

fun ApiResult.HttpError.reason(): String =
    problem?.detail ?: problem?.title ?: "Error del servidor ($status)"
