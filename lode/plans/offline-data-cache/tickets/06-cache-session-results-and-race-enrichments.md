---
id: 06
title: Cache session results and race enrichments
type: task
status: built
blocked_by: [03, 04]
owner: ""
spec: ../../../specs/offline-data-cache.md
---

# 06 — Cache session results and race enrichments

**What to build:** Make selected current-season session results, past-list podium snippets, and race enrichments durable so Round and Session Result surfaces remain useful offline.

**Blocked by:** 03 — Cache current-season schedule end to end; 04 — Cache standings and catalogs end to end.

**Status:** built

```kotlin
sealed interface CacheResourceKey {
    data class SessionResult(val season: Int, val round: Int, val session: SessionType) : CacheResourceKey
    data class Pitstops(val season: Int, val round: Int) : CacheResourceKey
}
```

```mermaid
flowchart TD
    Schedule[Active schedule] --> Keys[Session keys near now]
    Keys --> Results[Race/Quali/Sprint/FP snapshots]
    Results --> Round[Round detail + podium]
    Results --> Session[Session Result screen]
    Pitstops[Pitstop snapshot] --> Session
```

- [x] Session Result pages render cached Race, Quali, Sprint, Sprint Quali, and Practice results when available.
- [x] Past-list podium content can be derived from cached race results without a separate durable truth.
- [x] Pit-stop enrichment preserves cached content and treats empty enrichment as valid when upstream has no data.
- [x] Result refreshes only run when a session is plausibly complete according to schedule-derived rules.
- [x] Future-session cache misses surface `Session not yet complete` instead of falling through to direct network fetches or permanent loading.
- [x] Refresh failure for one session/enrichment never blanks other session resources.
- [x] Cached alpha-session reads translate car numbers through the cached current-driver catalog so FP/Sprint/Sprint Quali ids match the online path.

Related: [spec](../../../specs/offline-data-cache.md), [session result source ADR](../../../decisions/0005-session-results-use-two-apis.md), [ADR 0017](../../../decisions/0017-offline-refresh-coordination.md).
