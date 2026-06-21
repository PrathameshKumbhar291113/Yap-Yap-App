package com.yapyap.core.network

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val exception: ApiException) : ApiResult<Nothing> {
        val message: String get() = exception.message
        val errorCode: String? get() = exception.errorCode
        val httpStatus: Int? get() = exception.httpStatus
    }
}

suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(block())
    } catch (e: ApiException) {
        ApiResult.Error(e)
    } catch (e: Exception) {
        ApiResult.Error(
            ApiException.Unknown(e.message ?: "Something went wrong"),
        )
    }
}

suspend inline fun <T> safeApiCallResult(crossinline block: suspend () -> T): Result<T> {
    return when (val result = safeApiCall(block)) {
        is ApiResult.Success -> Result.success(result.data)
        is ApiResult.Error -> Result.failure(result.exception)
    }
}

fun <T> ApiResult<T>.getOrThrow(): T = when (this) {
    is ApiResult.Success -> data
    is ApiResult.Error -> throw exception
}

fun <T> ApiResponse<T>.requireData(httpStatus: Int? = null): T {
    if (!success || data == null) {
        throw ApiException.from(
            httpStatus = httpStatus,
            errorCode = errorCode,
            message = message ?: "Request failed",
        )
    }
    return data
}
