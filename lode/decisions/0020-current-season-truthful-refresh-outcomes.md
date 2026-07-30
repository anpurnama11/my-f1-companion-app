# 0020 — Current-season refreshes report truthful outcomes

Status: accepted

## Context

The cache previously returned `Success` both after persisting a network payload
and after skipping a fresh snapshot. WorkManager therefore could not distinguish
network evidence from a TTL decision, and a successful sibling write could hide
a retryable current-season failure. Non-season migration remains separately
owned by GitHub issue #69.

## Decision

Current-season schedule, next-session, standings, catalogs, session results,
and pitstops return five truthful outcomes. One classifier maps HTTP
408/429/5xx, timeout, connectivity, storage I/O, and malformed successful
payloads to `RetryableFailure`; other HTTP 4xx responses are
`PermanentFailure`. Unknown programmer failures and coroutine cancellation
propagate. Future or plausibly unpublished sessions return `Deferred` without
changing attempt metadata. The worker retries if any current-season entry is
retryable, while neutral and permanent entries do not request immediate retry.

```kotlin
sealed interface RefreshResult {
    data object Refreshed : RefreshResult
    data object SkippedFresh : RefreshResult
    data object Deferred : RefreshResult
    data class RetryableFailure(val message: String) : RefreshResult
    data class PermanentFailure(val message: String) : RefreshResult
    data object Success : RefreshResult // temporary non-season: #69
    data class Failure(val message: String) : RefreshResult // temporary non-season: #69
}
```

```mermaid
flowchart TD
    Refresh[Current-season refresh] --> Write[Refreshed]
    Refresh --> Fresh[SkippedFresh]
    Refresh --> Deferred[Deferred]
    Refresh --> Retryable[RetryableFailure]
    Refresh --> Permanent[PermanentFailure]
    Retryable --> Retry[WorkManager retry]
    Write --> Keep[Committed snapshot remains]
    Fresh --> Next[Next fixed tick]
    Deferred --> Next
    Permanent --> Next
```

## Why

This is the smallest staged contract that makes current-season orchestration
honest without pre-implementing later tickets. Keeping one `requiresRetry`
aggregate avoids parallel old/new bundle APIs. Legacy `Success`/`Failure`
remain only for non-season resources; issue #69 migrates those resources and
removes both variants.

## Consequences

- Existing compatible content stays in `SectionUiState.Content` with
  `RefreshFailed` after either failure class.
- Fresh skips and deferred work are neutral for worker retry; deferred work
  preserves cached content, while an uncached consumer may show unavailable.
- A retry can coexist with successful writes; TTL gates prevent rewriting those
  fresh siblings during backoff.
- Exhaustive legacy UI branches remain only as compatibility handling for the
  non-season repositories owned by issue #69.

Related: [../offline-data-cache/refresh-coordination.md](../offline-data-cache/refresh-coordination.md), [../offline-data-cache/summary.md](../offline-data-cache/summary.md), [../specs/cache-correctness-hardening.md](../specs/cache-correctness-hardening.md).
