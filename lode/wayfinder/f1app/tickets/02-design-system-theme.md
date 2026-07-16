---
id: 02
title: Design system → Compose Material3 theme
type: task
status: closed
blocked_by: []
owner: ""
---

## Resolution

Locked decisions, all implemented in `app/src/main/java/com/anpurnama/f1_app/ui/theme/`:

1. **Dark-only.** `F1appTheme(content)` — single-param composable, no
   `isSystemInDarkTheme()`, no light scheme, no dynamic color, no `Build` branch.
2. **Token → M3 mapping.** Core semantic colors assembled into `darkColorScheme()`
   in `Theme.kt`; raw `Color` vals in `Color.kt` are the single hex source. Result
   accents (`FastestLap`, `PolePosition`, `DriverOfDay`, `DriversChampionship`)
   are domain aliases of core vals so a theme change flows through badges.
3. **F1 palettes as `object`s in `Color.kt`, not a `Tokens.kt`.** In-scope only:
   `Circuits` (23 circuits) and `Tyres` (6 compounds × text+bg). Skipped:
   collaborator colors (dropped screens), pit-wall status (out of scope /
   redundant), `mclaren`/`nina`/`formula2` (boxbox-club-specific / feeder scope).
4. **Typography = M3 defaults** (`val Typography = Typography()`). Design's
   12–34sp scale == standard Material sizes on the system font; no per-role
   overrides. Scaffold's `bodyLarge` override deleted as dead code.
5. **`F1Shapes`** with sm 2 / md 8 / lg 14 / xl 16 dp. Design's `full: 28` has no
   M3 `Shapes` slot → use `CircleShape` directly for pills.
6. **`object Spacing`** with the 8-step scale (xs..xxl, 4–32dp), in `Theme.kt`.
   Exposed (not direct dp) because the design mandates consistent scale use.

### Side-effects (recorded, not ticket drift)

- `compileSdk` 36.1 → 37 and `targetSdk` 36 → 37. Required because Compose BOM
  2026.06.01 (Kotlin 2.4.10) transitively pulls `androidx.core:1.19.0` and
  `lifecycle-runtime-compose:2.11.0`, which hard-require SDK 37. Build was red on
  a clean checkout of the scaffold; the theme rewrite surfaced it. Recorded in
  `lode/practices.md` ("Build floor"). `minSdk` 24 untouched.
- `MainActivity.kt`, all other app sources, navigation, and DI are untouched.

### Files

- `app/src/main/java/com/anpurnama/f1_app/ui/theme/{Color,Theme,Type}.kt` — rewritten.
- `app/build.gradle.kts` — `compileSdk` and `targetSdk` bumped.

### Verification run

- `grep -RIn "Purple\|Pink80\|Pink40\|dynamicColor\|isSystemInDarkTheme\|Build.VERSION\|dynamicDark\|dynamicLight" app/src` → empty.
- `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (after the SDK bump).
- `F1appTheme` call sites in `MainActivity.kt` (2) compile against the new
  single-param signature with no edits required.

### Lode

- New: `lode/design-system/theme.md` (current-state description of the theme).
- Updated: `lode/practices.md` (build-floor section + package-layout fix
  removing the obsolete `Tokens.kt` mention).
- Updated: `lode/lode-map.md` (design-system block), `lode/wayfinder/f1app/map.md`
  (this ticket moved to Decisions).

## Question

Transcribe the boxbox-club design tokens (see `~/Downloads/boxbox-club-DESIGN.md`) into a
Compose Material3 theme inside this project. Confirm the answers to:

- **Dark-only?** The doc says "dark-only in practice" but defines light tokens. Ship
  dark-only, or wire a full light/dark pair?
- **Token mapping:** core semantic colors (`primary` #ff3301, `secondary` #125df0,
  `tertiary` #583ff2, `error` #fa1a24, four surface levels #0d0d0d→#191919, outlines) → a
  `darkColorScheme()`. The non-M3 palettes (23 circuit colors, 6 tyre text+bg pairs, 8
  collaborator colors, pit-wall status set) — keep as standalone `Color` constants or
  fold into a custom token object?
- **Type:** system font (Roboto) at the 12–34sp scale — a `Typography` with those sizes, or
  rely on M3 defaults (they already match)?
- **Shape:** 14dp default radius, the sm/md/lg/xl/full scale → `Shapes`.
- **Spacing:** the 4–32px scale — expose as a `Spacing` object or rely on direct dp usage.

Deliverable is the `ui/theme/` rewrite (the scaffold already has Color/Theme/Type.kt
stubs) plus, if a standalone token object is the call, a `Tokens.kt` for the F1-specific
palettes. This ticket does the work, not just the decision — but it's sized to one session.
