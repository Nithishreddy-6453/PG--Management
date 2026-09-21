package com.example.core.common

import java.io.Serializable

/**
 * Domain error interface representing safe failure categories.
 */
sealed interface PgError : Serializable {
    val message: String

    data class DatabaseError(override val message: String) : PgError
    data class SecurityError(override val message: String) : PgError
    data class ValidationError(override val message: String) : PgError
    data class NetworkError(override val message: String) : PgError
    data class UnknownError(override val message: String, val throwable: Throwable? = null) : PgError
}

/**
 * Enterprise monadic Result wrapper enclosing successful values of [T] or domain [PgError].
 */
sealed interface PgResult<out T> {
    data class Success<out T>(val data: T) : PgResult<T>
    data class Failure(val error: PgError) : PgResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun errorOrNull(): PgError? = when (this) {
        is Success -> null
        is Failure -> error
    }

    companion object {
        fun <T> success(data: T): PgResult<T> = Success(data)
        fun failure(error: PgError): PgResult<Nothing> = Failure(error)
        
        inline fun <T> runCatching(block: () -> T): PgResult<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Failure(PgError.UnknownError(e.localizedMessage ?: "An unexpected error occurred", e))
            }
        }
    }
}
