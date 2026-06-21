package com.yapyap.core.network

sealed class ApiException(
    override val message: String,
    open val httpStatus: Int? = null,
    open val errorCode: String? = null,
) : Exception(message) {

    class AuthInvalid(
        message: String,
        httpStatus: Int? = 401,
        errorCode: String? = ERROR_AUTH_INVALID,
    ) : ApiException(message, httpStatus, errorCode)

    class TokenInvalid(
        message: String,
        httpStatus: Int? = 401,
        errorCode: String? = ERROR_TOKEN_INVALID,
    ) : ApiException(message, httpStatus, errorCode)

    class NotFound(
        message: String,
        httpStatus: Int? = 404,
        errorCode: String? = ERROR_NOT_FOUND,
    ) : ApiException(message, httpStatus, errorCode)

    class Conflict(
        message: String,
        httpStatus: Int? = 409,
        errorCode: String? = ERROR_CONFLICT,
    ) : ApiException(message, httpStatus, errorCode)

    class ValidationError(
        message: String,
        httpStatus: Int? = 400,
        errorCode: String? = ERROR_VALIDATION,
    ) : ApiException(message, httpStatus, errorCode)

    class Unknown(
        message: String,
        httpStatus: Int? = null,
        errorCode: String? = null,
    ) : ApiException(message, httpStatus, errorCode)

    companion object {
        const val ERROR_AUTH_INVALID = "AUTH_INVALID"
        const val ERROR_TOKEN_INVALID = "TOKEN_INVALID"
        const val ERROR_NOT_FOUND = "NOT_FOUND"
        const val ERROR_CONFLICT = "CONFLICT"
        const val ERROR_VALIDATION = "VALIDATION_ERROR"

        fun from(
            httpStatus: Int? = null,
            errorCode: String? = null,
            message: String = "Request failed",
        ): ApiException = when (errorCode) {
            ERROR_AUTH_INVALID -> AuthInvalid(message, httpStatus, errorCode)
            ERROR_TOKEN_INVALID -> TokenInvalid(message, httpStatus, errorCode)
            ERROR_NOT_FOUND -> NotFound(message, httpStatus, errorCode)
            ERROR_CONFLICT -> Conflict(message, httpStatus, errorCode)
            ERROR_VALIDATION -> ValidationError(message, httpStatus, errorCode)
            else -> when (httpStatus) {
                401 -> AuthInvalid(message, httpStatus, errorCode)
                404 -> NotFound(message, httpStatus, errorCode)
                409 -> Conflict(message, httpStatus, errorCode)
                400, 422 -> ValidationError(message, httpStatus, errorCode)
                else -> Unknown(message, httpStatus, errorCode)
            }
        }
    }
}
