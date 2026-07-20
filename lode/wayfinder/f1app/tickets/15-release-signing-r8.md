---
id: 15
title: "Release, signing & R8"
type: task
status: closed
blocked_by: []
owner: "pi"
---

## Resolution (closed 2026-07-18)

User signaled a release target: **sideload to a personal Android device**.
That locks output shape to APK and keeps Play Console / AAB / `bundletool`
out of scope. The flip was executed and verified end-to-end:

- **Keystore:** generated fresh via `keytool` into
  `~/.android/f1app-release.jks` (PKCS12, RSA-2048, alias `f1app-release`,
  validity 10000 days, self-signed cert for `CN=anpurnama`). User had no
  prior keystore they could recover, so a new one is the source of truth.
  Cert SHA-256: `66ee22de9426c9c2ac1b35030e74fa862481eb8669da5a038f7f993632dcec1b`.
- **Signing config:** `signingConfigs.register("release")` reads
  `storeFile` / `storePassword` / `keyAlias` / `keyPassword` from a
  git-ignored `keystore.properties` at the repo root (added to
  `.gitignore`). PKCS12 ignores the separate `keyPassword`, so it equals
  `storePassword`. The release buildType binds via
  `signingConfig = signingConfigs.getByName("release")`.
- **R8 / minification:** flipped `optimization { enable = true }` in the
  `release` buildType. AGP 9.2.1 uses the new `optimization {}` DSL (one
  flag enables R8 code shrinking + optimized resource shrinking +
  bundled default Android keep rules). Required `gradle.properties`:
  `android.r8.gradual.support=true` (no-op gate flag AGP 9 raises if it
  is absent). No app-level `proguard-rules.pro` / `src/.../keepRules/*.keep`
  needed: the scaffold has no reflection (Compose + theme only), and
  Compose ships consumer rules transitively. `minifyReleaseWithR8` +
  `optimizeReleaseResources` tasks run; release APK = 812K.
- **Versioning:** `versionCode = 1`, `versionName = "1.0.0"`. Manual
  per-release bumps; no auto-versioning plugin. Bump both on every
  signed release.
- **Output:** APK at `app/build/outputs/apk/release/app-release.apk`.
  AAB + Play Console / `bundletool` reopen as a fresh effort when a
  Play target materializes; stay out of scope until then.
- **Verify (smoke):** installed the release-signed APK on `emulator-5554`
  after uninstalling the stale debug-signed build (signature mismatch is
  expected — debug and release keys differ), launched `.MainActivity`,
  process stayed up with no `FATAL`/`AndroidRuntime` crash. Scaffold R8
  smoke passes; real-screen re-verify happens as each feature lands.

### Macrobenchmark folded in (from ticket 14)

Ticket 14 deferred baseline-profile generation to here as a fifth step.
Folded, not split: with R8 now on, the **only remaining gate** for rung 6
(macrobenchmark + baseline profile) is *real feature screens existing*
(the app is still the `Greeting` scaffold). The flip-flow already supplies
the hard part (a minified, release-signed, R8-on build). When Homepage +
Schedule + Leaderboard + My Team land and stabilize, generate a baseline
profile via the `compose-baseline-profiles` flow against this release
build type, then commit it so subsequent release builds ship pre-compiled
hotpaths. No separate ticket — it is a tracked follow-up in
`lode/testing/scope.md` (rung 6) and `lode/release/build-and-signing.md`.

### CountdownWidget R8 note (ticket 06) — deferred, not resolved here

No widget code exists yet (ticket 06/07 are design-locked, not built), so
there is nothing to strip. When `CountdownWidget` lands, verify its
Glance `@Composable`-to-RemoteViews content renders in a release build
before declaring ticket 06 shipped. Tracked in
`lode/release/build-and-signing.md` and `lode/wayfinder/f1app/tickets/06-widget-technology.md`.

## Out of scope

- **CI/CD pipeline** — separate from the first local signed build;
  revisit after a release exists.
- **Play Console upload / store listing** — per the `android-play-cicd`
  skill, Console-side steps are human; this ticket ends at "release APK
  produced and installs on a device." AAB + upload reopen as a fresh
  effort when a Play target materializes.
- **App bundling splits / dynamic feature delivery** — single-module
  app, no splits needed.
- **Runtime permissions at release** — no runtime permissions in the
  app (network is declared, widgets need none) — no release-time
  permission surface.

## Cross-references

- Ticket 01: `lode/architecture/architecture.md` —
  `Wiring` Application + Ktor `HttpClient`; no runtime reflection.
- Ticket 02: `lode/design-system/theme.md` — SDK bump 36 → 37.
- Ticket 06: `lode/wayfinder/f1app/tickets/06-widget-technology.md`
  — Glance widget; release-build verify target.
- `r8-analyzer` skill — keep-rule audit if a release build strips
  something Compose/Glance needs.
- `android-play-cicd` skill — Console upload steps (out of scope for
  this ticket; useful when a Play target materializes).
