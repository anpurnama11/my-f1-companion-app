package com.anpurnama.f1_app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.ui.theme.Spacing

/**
 * Shared [Outcome] → composable renderer. Pinned for ticket 01 open
 * question #2 (the error / empty / loading UX family): every later
 * screen reuses this shape, no per-screen ad-hoc rendering.
 *
 *  - [Outcome.Loading] → centered [CircularProgressIndicator].
 *  - [Outcome.Failure] → error message + optional retry [Button].
 *  - [Outcome.Success] → caller-provided [content] lambda.
 *
 * The retry button only renders when [onRetry] is non-null; screens
 * without a refresh affordance (read-only lists) pass `null`.
 */
@Composable
fun <T> OutcomeContent(
    outcome: Outcome<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when (outcome) {
        is Outcome.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        is Outcome.Failure -> Box(
            modifier = modifier
                .fillMaxSize()
                .padding(Spacing.normal),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = outcome.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (onRetry != null) {
                    Spacer(Modifier.height(Spacing.md))
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
        is Outcome.Success -> content(outcome.data)
    }
}
