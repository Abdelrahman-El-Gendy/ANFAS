package com.anfas.core.common

/**
 * Result type used across module boundaries. Deliberately not kotlin.Result: this one is
 * covariant, exhaustively matchable in `when`, and carries a domain error rather than a
 * Throwable.
 */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    val message: String

    data class Network(override val message: String) : AppError
    data class Storage(override val message: String) : AppError
    data class Validation(override val message: String, val field: String? = null) : AppError
    data class Unauthorized(override val message: String) : AppError
    data class Unexpected(override val message: String) : AppError
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

fun <T> AppResult<T>.getOrNull(): T? = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> null
}
