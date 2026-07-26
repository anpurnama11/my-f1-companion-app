# Release build, signing & R8

> **STATUS: BUILT** — release buildType flips on R8 + signing; produces a
> sideload-able APK. Decided in ticket 15 ([wayfinder](../wayfinder/f1app/tickets/15-release-signing-r8.md)).

The Scaffold ships release-ready. Sideload target only (personal device) — no
Play Console / AAB / `bundletool` in scope.

## Keystore

- Fresh self-signed PKCS12 keystore at `~/.android/f1app-release.jks`.
  RSA-2048, alias `f1app-release`, validity 10000 days.
  Cert SHA-256: `66ee22de9426c9c2ac1b35030e74fa862481eb8669da5a038f7f993632dcec1b`.
- Credentials live in a git-ignored `keystore.properties` at the repo root
  (added to `.gitignore`). Keep concrete values only in that local file:

```properties
storeFile=<absolute path to local release keystore>
storePassword=<local secret>
keyAlias=<local alias>
keyPassword=<local secret>   # PKCS12 usually ignores this; often equals storePassword
```

**Invariant:** never commit the keystore, `keystore.properties`, or the password
into the repo, chat, or lode. If the user rotates credentials, update
`keystore.properties` locally and re-run `keytool` — do not store the new
password anywhere tracked.

## signingConfig (built)

`app/build.gradle.kts` loads `keystore.properties` at the top, registers a
`release` signing config, and binds it to the `release` buildType:

```kotlin
import java.util.Properties

val keystoreProperties = Properties().apply {
    load(rootProject.file("keystore.properties").inputStream())
}

android {
    signingConfigs {
        register("release") {
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization { enable = true }
        }
    }
}
```

## R8 / optimization (AGP 9.x DSL)

AGP 9.2.1 uses the `optimization {}` block — one flag enables R8 code
shrinking + optimized resource shrinking + bundled default Android keep rules.
No legacy `isMinifyEnabled` / `isShrinkResources` / `proguardFiles` trio.

`gradle.properties` must carry the gate flag or AGP refuses to configure:

```properties
# R8 new optimization pipeline (AGP 9.x) gate for optimization { enable = true }
android.r8.gradual.support=true
```

### Keep rules

One app-level rule is required today because WorkManager initializes its
internal Room database reflectively during AndroidX Startup. In release/R8,
`androidx.work.impl.WorkDatabase_Impl` must keep its generated no-arg
constructor or launch fails before `MainActivity` with
`NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init>[]`.

Rule location: `app/src/main/keepRules/rules.keep` (AGP 9 source-set
convention, not top-level `proguard-rules.pro`):

```proguard
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}
```

Validation contract for release/R8 edits:

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -c
adb shell monkey -p com.anpurnama.f1_app 1
adb shell pidof com.anpurnama.f1_app
adb shell dumpsys activity activities | grep -E 'topResumedActivity|ResumedActivity'
adb logcat -b crash -d -t 100 | grep -i 'com.anpurnama.f1_app\|FATAL EXCEPTION' || true
```

The keep rule must appear in
`app/build/outputs/mapping/release/configuration.txt`, and the seeded
constructor should appear in `seeds.txt`.

## Versioning

- `versionCode = 1`, `versionName = "1.0.0"` (bumped from the `"1.0"` default).
- Manual per-release bumps. No auto-versioning plugin. Bump both on every
  signed release.

## Output

APK at `app/build/outputs/apk/release/app-release.apk` (~812K for the scaffold).
Install via `adb install <apk>`. AAB + Play upload reopen as a fresh effort
when a Play Console target materializes — out of scope here.

**Signature-mismatch install note:** a debug-signed build already on a device
will reject the release-signed install with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
Uninstall the debug build first (`adb uninstall com.anpurnama.f1_app`), then
install the release APK. This is expected — debug and release keys differ.

## Countdown widget R8 verify (not yet actionable)

Ticket 06/07's `CountdownWidget` is design-locked, not built. When it lands,
verify its Glance `@Composable`-to-RemoteViews content renders in a release
build before declaring ticket 06 shipped — Glance has had historical R8 edge
cases with its generated receivers. No keep rule to add preemptively.

## Macrobenchmark / baseline profile (folded from ticket 14)

Ticket 14 deferred rung 6 (macrobenchmark + baseline profile) to here. The flip
above supplies the hard precondition — a minified, release-signed, R8-on build.
The **only remaining gate** is real feature screens existing (the app is still
the scaffold). When Homepage + Schedule + Leaderboard + My Team land and
stabilize, generate a baseline profile via the `compose-baseline-profiles` skill
flow against this release build type, commit it, and let subsequent release
builds ship pre-compiled hotpaths. Tracked alongside rung 6 in
[../testing/scope.md](../testing/scope.md).

## Cross-references

- [../wayfinder/f1app/tickets/15-release-signing-r8.md](../wayfinder/f1app/tickets/15-release-signing-r8.md)
  — the grilling/task ticket, closed.
- [../practices.md](../practices.md) — release-build ADR (signing + R8 DSL).
- [../testing/scope.md](../testing/scope.md) — rung 6 macrobenchmark, now
  blocked only on screens.
- [../architecture/architecture.md](../architecture/architecture.md) —
  no runtime reflection in `f1/`; keeps R8 surface minimal.
