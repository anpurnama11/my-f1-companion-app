package com.anpurnama.f1_app.core

/**
 * Sealed result type used across data → ViewModel boundaries.
 *
 * - [Success] carries [data] (the model, not the DTO).
 * - [Failure] carries a user-readable [errorMessage]. Network/parse errors
 *   are mapped to a short string in the use case or call site.
 * - [Loading] is the initial state on every load.
 *
 * Pure Kotlin (zero `android.*` imports) — the domain-purity invariant
 * applies here so `core/` moves with `f1/` at a future KMP port.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val errorMessage: String) : Outcome<Nothing>
    data object Loading : Outcome<Nothing>

    fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
        is Loading -> this
    }

    fun dataOrNull(): T? = (this as? Success<T>)?.data
}

inline fun <T, R> Outcome<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (String) -> R,
    onLoading: () -> R,
): R = when (this) {
    is Outcome.Success -> onSuccess(data)
    is Outcome.Failure -> onFailure(errorMessage)
    is Outcome.Loading -> onLoading()
}
