package com.helper.mobile.data.repository

import com.helper.mobile.data.local.HelperDatabase
import com.helper.mobile.data.remote.ApiResult
import com.helper.mobile.data.remote.HelperApi
import com.helper.mobile.data.remote.LoginRequest
import com.helper.mobile.data.remote.apiCall
import com.helper.mobile.data.session.SessionStore
import com.helper.mobile.domain.model.Role
import com.helper.mobile.domain.model.Session
import com.helper.mobile.domain.model.VerificationStatus
import com.helper.mobile.domain.repository.AuthRepository
import com.helper.mobile.domain.repository.LoginResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: HelperApi,
    private val store: SessionStore,
    private val database: HelperDatabase,
) : AuthRepository {

    override suspend fun login(email: String, password: String): LoginResult =
        when (val result = apiCall { api.login(LoginRequest(email, password)) }) {
            is ApiResult.Success -> {
                val body = result.body
                val session = Session(
                    userId = body.userId,
                    role = Role.valueOf(body.role),
                    verification = body.verificationStatus?.let(VerificationStatus::valueOf),
                    accessToken = body.accessToken,
                    expiresAt = Instant.parse(body.expiresAt),
                )
                store.save(session, body.refreshToken)
                LoginResult.LoggedIn(session)
            }
            ApiResult.NoConnection -> LoginResult.NoConnection
            is ApiResult.HttpError -> if (result.status >= 500) LoginResult.NoConnection else LoginResult.InvalidCredentials
        }

    /**
     * Solo en builds de prueba y hasta que el backend tenga login (B7): entra con el id de un cliente o un
     * mecánico que ya existe en el backend. El mecánico se toma como aprobado porque hoy el servidor no verifica.
     */
    suspend fun devLogin(role: Role, userId: String): Session {
        val session = Session(
            userId = userId,
            role = role,
            verification = if (role == Role.MECHANIC) VerificationStatus.APPROVED else null,
            accessToken = "",
            expiresAt = Instant.now().plusSeconds(7 * 24 * 3600),
        )
        store.save(session, "")
        return session
    }

    override suspend fun logout() {
        store.clear()
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }
}
