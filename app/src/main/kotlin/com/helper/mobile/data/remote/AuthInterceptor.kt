package com.helper.mobile.data.remote

import com.helper.mobile.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Agrega el token de acceso a cada llamada. El servidor decide qué puede hacer cada rol. */
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // Login, alta y refresh son públicos: un token vencido ahí haría que Spring rechace el pedido con 401.
        if (chain.request().url.encodedPath.startsWith("/api/auth/")) return chain.proceed(chain.request())
        val token = runBlocking { sessionStore.currentSession()?.accessToken }
        val request = if (token.isNullOrEmpty()) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(request)
    }
}
