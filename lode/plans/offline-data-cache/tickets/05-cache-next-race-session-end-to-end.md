---
id: 05
title: Cache next race and session end to end
type: task
status: ready-for-agent
blocked_by: [03]
owner: ""
spec: ../../../specs/offline-data-cache.md
---

# 05 — Cache next race and session end to end

**What to build:** Make Homepage's next race/session data a durable structured cache resource while leaving the existing Countdown widget cache separate.

**Blocked by:** 03 — Cache current-season schedule end to end.

**Status:** ready-for-agent

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

- [ ] Homepage next race/session content renders from durable cache when offline.
- [ ] Off-season `null` next-race responses are valid cached payloads.
- [ ] Refresh preserves visible next-session content and surfaces sync status.
- [ ] The Countdown widget's existing typed-key cache remains separate and unchanged unless explicitly bridged later.
- [ ] Next race/session refresh uses the current active season and does not promote season rollover by itself.

Related: [spec](../../../specs/offline-data-cache.md), [widget cache](../../../widget/countdown.md), [ADR 0014](../../../decisions/0014-countdown-widget-shows-next-session.md).
