# Design system → Compose Material3 theme

Current state of the F1app dark-only theme. Source of the design is
`~/Downloads/boxbox-club-DESIGN.md` (lives outside the repo); its transcribed
conclusions now live here and in `ui/theme/`.

## Invariant: dark-only

- `F1appTheme(content)` builds a single `darkColorScheme()` and passes it to
  `MaterialTheme`. There is **no** light scheme, no `isSystemInDarkTheme()`,
  no `Build.VERSION` dynamic-color branch, no `dynamicColor` parameter.
- Any future light theme is a fresh decision, not an untouched scaffold branch
  waiting to be re-enabled.

## Token → `darkColorScheme()` mapping

| Design token (hex)              | `darkColorScheme` param      | Color.kt val       |
|---------------------------------|------------------------------|--------------------|
| primary #ff3301                 | `primary`                    | `F1Primary`        |
| secondary #125df0               | `secondary`                  | `F1Secondary`      |
| tertiary #583ff2                | `tertiary`                   | `F1Tertiary`       |
| error #fa1a24                   | `error`                      | `FLError`          |
| surface #0d0d0d                 | `surface`, `background`       | `Surface`          |
| surface-container #111111       | `surfaceContainer`, `surfaceVariant` | `SurfaceContainer` |
| surface-container-high #191919  | `surfaceContainerHigh`       | `SurfaceContainerHigh` |
| on-surface #ffffff              | `onSurface`, `onBackground`  | `OnSurface`        |
| on-surface-variant #e1e1e1      | `onSurfaceVariant`           | `OnSurfaceVariant` |
| outline #404040                 | `outline`                    | `Outline`          |
| outline-variant #212121         | `outlineVariant`             | `OutlineVariant`   |

`onPrimary/onSecondary/onTertiary/onError` are `#ffffff` (`On*` vals).

## F1 result-highlight accents

Domain aliases over the core palette so a theme change flows through badges:
- `DriversChampionship` = #2267dd
- `FastestLap` = `F1Tertiary` (#583ff2)
- `PolePosition` = `DriversChampionship`
- `DriverOfDay` = `FLError` (#fa1a24)

## F1-specific palettes (`object` singletons in `Color.kt`)

Kept as standalone `Color` constants, **never folded into a custom token object
or `Tokens.kt`** — fewest files (BSSN). Two objects cover the in-scope screens
(Dashboard / Driver / Team / Round detail + Countdown):

- **`Circuits`** — 23 per-track brand colors (AbuDhabi..UsaMiami). One `val`
  per circuit, kebab-case keys flattened to CamelCase.
- **`Tyres`** — six Pirelli compounds as text+background pairs
  (`Soft`/`SoftBg` ... `Wet`/`WetBg`, plus `Unknown`/`UnknownBg`).

### Contract

- **Circuits:** use as accent **backgrounds** on dark surfaces; **never as
  text** on dark (too saturated for readability, per the design's do's/don'ts).
- **Tyres:** always pair text color with its `-Bg` background — a tyre pill is
  `Tyres.Soft` text on `Tyres.SoftBg` background, never one without the other.

### Out-of-scope palettes (deliberately NOT transcribed)

- **Collaborator colors** (8) — only the dropped Firebase-backed content
  screens consumed them ([map](../wayfinder/f1app/map.md) "Out of scope").
- **Pit-wall status set** — redundant with core `secondary`/`error` and out of
  scope for the 4 in-app screens / Countdown widget.
- **`mclaren` / `nina` / `formula2`** — boxbox-club-specific or feeder-scope,
  no consumer in F1app.

## `F1Shapes`

```kotlin
private val F1Shapes = Shapes(
    small = RoundedCornerShape(2.dp),   // design sm
    medium = RoundedCornerShape(8.dp),  // design md
    large = RoundedCornerShape(14.dp),   // design lg — default radius
    extraLarge = RoundedCornerShape(16.dp), // design xl
)
```

The design's `full: 28px` slot has **no M3 `Shapes` role**. Pills/circles use
`CircleShape` directly at call sites — not a token, not built pre-emptively.

## `Spacing`

```kotlin
object Spacing { val xs..xxl = 4.dp..32.dp /* 8 rungs */ }
```

Exposed (not just direct dp usage) because the design mandates consistent
scale use; tiny object, justified. Lives in `Theme.kt` (theming concern, no
4th file).

## Typography

```kotlin
val Typography = Typography()   // M3 defaults
```

The design's 12–34sp scale == standard Material sizes on the system font
(Roboto). No per-role overrides; add one **only** when a screen needs a size M3
doesn't provide. The scaffold's `bodyLarge` override was deleted as dead code.

## Files

- `app/src/main/java/com/anpurnama/f1_app/ui/theme/Color.kt` — palette + objects.
- `.../ui/theme/Theme.kt` — `F1ColorScheme` + `F1Shapes` + `Spacing` + `F1appTheme`.
- `.../ui/theme/Type.kt` — `Typography` (M3 defaults).

## Related
- [architecture/architecture.md](../architecture/architecture.md) — module/DI/layers.
- [practices.md](../practices.md) — "F1 palettes as `object`s, no app logic in theme files."
- [wayfinder/f1app/tickets/02-design-system-theme.md](../wayfinder/f1app/tickets/02-design-system-theme.md) — the closed ticket.
