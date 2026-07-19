package com.ocrstudio.core.common

/** Generic success/failure wrapper used across module boundaries instead of throwing. */
sealed class AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>()
    data class Failure(val error: Throwable, val message: String? = null) : AppResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(value)
        return this
    }

    inline fun onFailure(action: (Throwable) -> Unit): AppResult<T> {
        if (this is Failure) action(error)
        return this
    }

    fun getOrNull(): T? = (this as? Success)?.value

    companion object {
        inline fun <T> runCatching(block: () -> T): AppResult<T> = try {
            Success(block())
        } catch (t: Throwable) {
            Failure(t, t.message)
        }
    }
}
