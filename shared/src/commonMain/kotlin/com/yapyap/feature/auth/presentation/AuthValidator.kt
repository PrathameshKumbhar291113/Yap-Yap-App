package com.yapyap.feature.auth.presentation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.error_email_invalid
import yapyap.shared.generated.resources.error_email_required
import yapyap.shared.generated.resources.error_login_identifier_required
import yapyap.shared.generated.resources.error_name_required
import yapyap.shared.generated.resources.error_password_required
import yapyap.shared.generated.resources.error_password_short
import yapyap.shared.generated.resources.error_phone_invalid
import yapyap.shared.generated.resources.error_phone_required

sealed class AuthFieldError {
    data object LoginIdentifierRequired : AuthFieldError()
    data object EmailRequired : AuthFieldError()
    data object EmailInvalid : AuthFieldError()
    data object NameRequired : AuthFieldError()
    data object PhoneRequired : AuthFieldError()
    data object PhoneInvalid : AuthFieldError()
    data object PasswordRequired : AuthFieldError()
    data object PasswordTooShort : AuthFieldError()
}

@Composable
fun AuthFieldError.toMessage(): String = when (this) {
    AuthFieldError.LoginIdentifierRequired -> stringResource(Res.string.error_login_identifier_required)
    AuthFieldError.EmailRequired -> stringResource(Res.string.error_email_required)
    AuthFieldError.EmailInvalid -> stringResource(Res.string.error_email_invalid)
    AuthFieldError.NameRequired -> stringResource(Res.string.error_name_required)
    AuthFieldError.PhoneRequired -> stringResource(Res.string.error_phone_required)
    AuthFieldError.PhoneInvalid -> stringResource(Res.string.error_phone_invalid)
    AuthFieldError.PasswordRequired -> stringResource(Res.string.error_password_required)
    AuthFieldError.PasswordTooShort -> stringResource(Res.string.error_password_short)
}

object AuthValidator {
    private val emailRegex = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
    private val phoneRegex = Regex("""^\+?[0-9]{10,20}$""")

    fun validateLoginIdentifier(identifier: String): AuthFieldError? {
        val trimmed = identifier.trim()
        return when {
            trimmed.isBlank() -> AuthFieldError.LoginIdentifierRequired
            '@' in trimmed -> validateEmail(trimmed)
            else -> validatePhone(trimmed)
        }
    }

    fun validateEmail(email: String): AuthFieldError? = when {
        email.isBlank() -> AuthFieldError.EmailRequired
        !emailRegex.matches(email.trim()) -> AuthFieldError.EmailInvalid
        else -> null
    }

    fun validateName(name: String): AuthFieldError? = when {
        name.isBlank() -> AuthFieldError.NameRequired
        else -> null
    }

    fun validatePhone(phone: String): AuthFieldError? = when {
        phone.isBlank() -> AuthFieldError.PhoneRequired
        !phoneRegex.matches(phone.trim()) -> AuthFieldError.PhoneInvalid
        else -> null
    }

    fun validatePassword(password: String): AuthFieldError? = when {
        password.isBlank() -> AuthFieldError.PasswordRequired
        password.length < 8 -> AuthFieldError.PasswordTooShort
        else -> null
    }
}
