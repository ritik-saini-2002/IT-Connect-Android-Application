package com.example.ritik_2.core

/** Runs [block] and wraps any exception in Result.failure */
inline fun <T> safeCall(block: () -> T): Result<T> =
    try { Result.success(block()) }
    catch (e: Exception) { Result.failure(e) }

/** Maps a Result<T> to Result<R> via [transform], catching exceptions */
inline fun <T, R> Result<T>.mapResult(transform: (T) -> R): Result<R> =
    fold(
        onSuccess = { safeCall { transform(it) } },
        onFailure = { Result.failure(it) }
    )
