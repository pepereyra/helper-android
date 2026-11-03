package com.helper.mobile.data.remote

import com.helper.mobile.data.repository.AuthRepositoryImpl
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

/**
 * Ante un 401 (access token vencido o identity-service reiniciado) pide un token nuevo con el refresh
 * token y repite el pedido una sola vez. Si el refresh tampoco sirve, la sesión se cierra.
 */
class TokenAuthenticator @Inject constructor(
    private val auth: Lazy<AuthRepositoryImpl>,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.startsWith("/api/auth/")) return null
        if (response.priorResponse != null) return null
        val stale = response.request.header("Authorization")?.removePrefix("Bearer ")
        val fresh = runBlocking { auth.get().accessTokenAfterRefresh(stale) } ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
    }
}
