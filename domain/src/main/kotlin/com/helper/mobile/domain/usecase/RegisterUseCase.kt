package com.helper.mobile.domain.usecase

import com.helper.mobile.domain.model.Role
import com.helper.mobile.domain.repository.AuthRepository
import com.helper.mobile.domain.repository.LoginResult
import javax.inject.Inject

/** Alta de cuenta. El rol elegido lo guarda el servidor y vuelve en el token; un mecánico arranca pendiente. */
class RegisterUseCase @Inject constructor(
    private val auth: AuthRepository,
) {
    sealed interface Result {
        data class Done(val login: LoginResult) : Result
        data object InvalidEmail : Result
        data object WeakPassword : Result
        data object RoleNotAllowed : Result
    }

    suspend operator fun invoke(email: String, password: String, role: Role): Result {
        val normalized = email.trim().lowercase()
        if (!LoginUseCase.isValidEmail(normalized)) return Result.InvalidEmail
        // BCrypt en el servidor solo usa 72 bytes; el servidor pide entre 8 y 72.
        if (password.length !in 8..72) return Result.WeakPassword
        if (role == Role.ADMIN) return Result.RoleNotAllowed
        return Result.Done(auth.register(normalized, password, role))
    }
}
