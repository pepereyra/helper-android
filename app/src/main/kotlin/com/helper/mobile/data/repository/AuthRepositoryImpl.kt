package com.helper.mobile.data.repository

import com.helper.mobile.data.local.HelperDatabase
import com.helper.mobile.data.remote.ApiResult
import com.helper.mobile.data.remote.HelperApi
import com.helper.mobile.data.remote.LoginRequest
import com.helper.mobile.data.remote.RefreshRequest
import com.helper.mobile.data.remote.RegisterRequest
import com.helper.mobile.data.remote.TokenResponse
import com.helper.mobile.data.remote.apiCall
import com.helper.mobile.data.session.SessionStore
import com.helper.mobile.domain.model.Role
import com.helper.mobile.domain.model.Session
import com.helper.mobile.domain.model.VerificationStatus
import com.helper.mobile.domain.repository.AuthRepository
import com.helper.mobile.domain.repository.LoginResult
import com.helper.mobile.domain.repository.RefreshResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** La sesión (rol, verificación y tokens) es siempre la que devolvió identity-service. */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: HelperApi,
    private val store: SessionStore,
    private val database: HelperDatabase,
) : AuthRepository {

    /** El refresh token rota en cada uso: dos refresh a la vez dejarían al segundo con un token ya usado. */
    private val refreshLock = Mutex()

    override suspend fun login(email: String, password: String): LoginResult =
        when (val result = apiCall { api.login(LoginRequest(email, password)) }) {
            is ApiResult.Success -> LoginResult.LoggedIn(save(result.body))
            ApiResult.NoConnection -> LoginResult.NoConnection
            is ApiResult.HttpError -> if (result.status >= 500) LoginResult.NoConnection else LoginResult.InvalidCredentials
        }

    override suspend fun register(email: String, password: String, role: Role): LoginResult =
        when (val result = apiCall { api.register(RegisterRequest(email, password, role.name)) }) {
            is ApiResult.Success -> LoginResult.LoggedIn(save(result.body))
            ApiResult.NoConnection -> LoginResult.NoConnection
            is ApiResult.HttpError -> when {
                result.status == 409 -> LoginResult.EmailAlreadyUsed
                result.status >= 500 -> LoginResult.NoConnection
                else -> LoginResult.InvalidCredentials
            }
        }

    override suspend fun refreshSession(): RefreshResult = refreshLock.withLock {
        val token = store.refreshToken() ?: return RefreshResult.SESSION_ENDED
        when (val result = apiCall { api.refresh(RefreshRequest(token)) }) {
            is ApiResult.Success -> {
                save(result.body)
                RefreshResult.REFRESHED
            }
            ApiResult.NoConnection -> RefreshResult.NO_CONNECTION
            is ApiResult.HttpError -> if (result.status >= 500) {
                RefreshResult.NO_CONNECTION
            } else {
                // Refresh vencido o revocado: hay que volver a ingresar.
                clearLocal()
                RefreshResult.SESSION_ENDED
            }
        }
    }

    /** Usado por el Authenticator de OkHttp, que corre fuera de una corrutina. */
    suspend fun accessTokenAfterRefresh(staleToken: String?): String? {
        val current = store.currentSession()?.accessToken
        // Otro pedido ya lo renovó mientras este esperaba.
        if (current != null && current != staleToken) return current
        return if (refreshSession() == RefreshResult.REFRESHED) store.currentSession()?.accessToken else null
    }

    override suspend fun logout() {
        store.refreshToken()?.let { token -> apiCall { api.logout(RefreshRequest(token)) } }
        clearLocal()
    }

    private suspend fun clearLocal() {
        store.clear()
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }

    private suspend fun save(body: TokenResponse): Session {
        val session = Session(
            userId = body.userId,
            role = Role.valueOf(body.role),
            verification = body.verificationStatus?.let(VerificationStatus::valueOf),
            accessToken = body.accessToken,
            expiresAt = Instant.parse(body.expiresAt),
        )
        store.save(session, body.refreshToken)
        return session
    }
}
