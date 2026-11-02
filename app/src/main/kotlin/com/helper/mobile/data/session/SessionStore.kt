package com.helper.mobile.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.helper.mobile.domain.model.Role
import com.helper.mobile.domain.model.Session
import com.helper.mobile.domain.model.VerificationStatus
import com.helper.mobile.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarda la sesión que devolvió el servidor. El rol y la verificación se copian del login;
 * la app no los puede cambiar por su cuenta.
 */
@Singleton
class SessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SessionRepository {

    override fun observeSession(): Flow<Session?> = dataStore.data.map { it.toSession() }

    override suspend fun currentSession(): Session? = observeSession().first()

    suspend fun save(session: Session, refreshToken: String) {
        dataStore.edit {
            it[USER_ID] = session.userId
            it[ROLE] = session.role.name
            it[VERIFICATION] = session.verification?.name ?: ""
            it[ACCESS_TOKEN] = session.accessToken
            it[REFRESH_TOKEN] = refreshToken
            it[EXPIRES_AT] = session.expiresAt.toString()
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toSession(): Session? {
        val userId = this[USER_ID] ?: return null
        val token = this[ACCESS_TOKEN] ?: return null
        return Session(
            userId = userId,
            role = Role.valueOf(this[ROLE] ?: return null),
            verification = this[VERIFICATION]?.takeIf { it.isNotEmpty() }?.let(VerificationStatus::valueOf),
            accessToken = token,
            expiresAt = Instant.parse(this[EXPIRES_AT] ?: return null),
        )
    }

    private companion object {
        val USER_ID = stringPreferencesKey("user_id")
        val ROLE = stringPreferencesKey("role")
        val VERIFICATION = stringPreferencesKey("verification")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = stringPreferencesKey("expires_at")
    }
}
