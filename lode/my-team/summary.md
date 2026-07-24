# My Team favorites management

The `Route.MyTeam` tab is the management surface for the same three-slot
`FavoritesCache` read by Homepage §3. It renders Driver 1, Driver 2, and
Constructor cards from the cache, resolves their display data from current
standings, and opens a `ModalBottomSheet` when any slot is tapped.

## Runtime flow

```mermaid
flowchart LR
    Tab["Route.MyTeam"] --> Screen["MyTeamScreen"]
    Screen --> VM["MyTeamViewModel"]
    VM --> Drivers["GetDriversStandingsUseCase"]
    VM --> Constructors["GetConstructorsStandingsUseCase"]
    VM <--> Cache["FavoritesCache"]
    Cache --> Home["HomepageViewModel §3"]
```

`MyTeamViewModel` is init-less: its `StateFlow` starts on the first collector
with `SharingStarted.Lazily`. Favorites remain reactive for the ViewModel's
lifetime; driver and constructor standings load independently and retry with
`forceRefresh = true`.

```kotlin
MyTeamViewModel(
    favoritesFlow = favoritesCache.read(),
    setDriver1 = favoritesCache::setDriver1,
    setDriver2 = favoritesCache::setDriver2,
    setTeam = favoritesCache::setTeam,
    // standings use-case function references omitted
)
```

## Contracts and invariants

- The slots are two independent favorite drivers plus one favorite
  constructor. Driver choices do not follow the chosen constructor.
- Tapping a slot explicitly chooses which value will be replaced; there is no
  oldest-pin eviction and no separate picker route.
- A driver ID cannot occupy both driver slots. The sheet disables the driver
  used by the other slot, while `FavoritesCache` enforces the hard invariant
  inside the same atomic `DataStore.edit` that performs the write. This keeps
  rapid or non-UI writes race-safe.
- Constructor replacement writes only `FAV_TEAM`; driver replacement writes
  only the selected driver key.
- Homepage and My Team receive the same `FavoritesCache` instance from
  `Wiring`, so successful writes propagate to Homepage §3 through its Flow.
- First-launch defaults remain owned by `HomepageViewModel` and
  `seedIfEmpty`; the seed only fills empty slots and never overwrites a pick.
- Standings failures use the shared `SectionUiState` / `OutcomeContent` UX.

## Lessons learned

Checking uniqueness only against ViewModel state is insufficient: two rapid
cross-slot writes can both observe the old snapshot. Persistent invariants must
be checked inside the serialized `DataStore.edit` transaction.

Related: [terminology](../terminology.md), [project practices](../practices.md),
[favorites decision](../wayfinder/f1app/tickets/12-design-favorites-picker-ux-storage.md),
and [build ticket](../plans/f1app-build/tickets/05-my-team-tab-and-favorites-picker.md).
