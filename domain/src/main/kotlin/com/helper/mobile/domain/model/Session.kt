package com.helper.mobile.domain.model

import java.time.Instant

/** El rol lo define el servidor dentro del token; la app solo lo usa para elegir pantallas. */
enum class Role { RIDER, MECHANIC, ADMIN }

/** Un mecánico solo puede trabajar cuando un administrador lo aprobó. */
enum class VerificationStatus { PENDING_VERIFICATION, APPROVED, REJECTED }

data class Session(
    val userId: String,
    val role: Role,
    val verification: VerificationStatus?,
    val accessToken: String,
    val expiresAt: Instant,
) {
    val canWorkAsMechanic: Boolean
        get() = role == Role.MECHANIC && verification == VerificationStatus.APPROVED
}
