package com.helper.mobile.domain.usecase

import com.helper.mobile.domain.repository.AuthRepository
import com.helper.mobile.domain.repository.LoginResult
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val auth: AuthRepository,
) {
    sealed interface Result {
        data class Done(val login: LoginResult) : Result
        data object InvalidEmail : Result
        data object EmptyPassword : Result
    }

    suspend operator fun invoke(email: String, password: String): Result {
        val normalized = email.trim().lowercase()
        if (!isValidEmail(normalized)) return Result.InvalidEmail
        if (password.isEmpty()) return Result.EmptyPassword
        return Result.Done(auth.login(normalized, password))
    }

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun isValidEmail(email: String): Boolean = EMAIL.matches(email)
    }
}
