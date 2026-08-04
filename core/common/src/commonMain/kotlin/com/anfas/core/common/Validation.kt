package com.anfas.core.common

/**
 * Validation scaffolding. Concrete rules belong with the features that own the fields —
 * this only establishes the shape they plug into.
 */
fun interface Validator<in T> {
    fun validate(value: T): AppResult<Unit>
}

fun notBlank(field: String): Validator<String> = Validator { value ->
    if (value.isNotBlank()) {
        AppResult.Success(Unit)
    } else {
        AppResult.Failure(AppError.Validation("$field must not be blank", field))
    }
}
