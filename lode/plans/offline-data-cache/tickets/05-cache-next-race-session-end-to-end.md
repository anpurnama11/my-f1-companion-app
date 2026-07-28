---
id: 05
title: Cache next race and session end to end
type: task
status: shipped
blocked_by: [03]
owner: ""
spec: ../../../specs/offline-data-cache.md
---

# 05 — Cache next race and session end to end

**What to build:** Make Homepage's next race/session data a durable structured cache resource while leaving the existing Countdown widget cache separate.

**Blocked by:** 03 — Cache current-season schedule end to end.

**Status:** shipped

```kotlin
sealed interface CacheResourceKey {
    data class NextRace(val season: Int) : CacheResourceKey
}
```

```mermaid
flowchart LR
    API[/current/next + cached schedule choice] --> Repo[Refresh coordinator]
    Repo --> Store[(Next race/session snapshot)]
    Store --> Home[Homepage countdown card]
    Widget[Countdown widget cache] -. separate .- Store
```

- [x] Homepage next race/session content renders from durable cache when offline.
- [x] Off-season `null` next-race responses are valid cached payloads.
- [x] Refresh preserves visible next-session content and surfaces sync status.
- [x] The Countdown widget's existing typed-key cache remains separate and unchanged unless explicitly bridged later.
- [x] Next race/session refresh uses the current active season and does not promote season rollover by itself.

Related: [spec](../../../specs/offline-data-cache.md), [widget cache](../../../widget/countdown.md), [ADR 0014](../../../decisions/0014-countdown-widget-shows-next-session.md).
