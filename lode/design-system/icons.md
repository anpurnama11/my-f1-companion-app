# Design system → App + tab icons

Asset system for F1app's launcher icon and bottom NavigationBar icons. The
launcher icon is the brand surface; the nav icons are functional — each one
identifies a tab and respects M3's `LocalContentColor` tinting.

## Launcher icon — `drawable/ic_launcher_foreground.webp`

Full-color PNG/WebP of the F1 stopwatch (timer, F1 track, checkered flag,
orange progress ring) on the dark theme's `#0d0d0d` ground. The source is
the user-provided stopwatch PNG; the script that produced every density
variant lives at `tools/app-icon-build/`. The image is reused for both
`ic_launcher` and `ic_launcher_round` because the corner pixels are dark —
the system mask (circle on Pixel, squircle on Samsung, teardrop on Xiaomi)
shapes the icon at the launcher level.

### Density variants (`mipmap-{density}/ic_launcher.webp`)

| Density | Size (px) | File                                |
|---------|-----------|-------------------------------------|
| mdpi    | 48        | `mipmap-mdpi/ic_launcher.webp`      |
| hdpi    | 72        | `mipmap-hdpi/ic_launcher.webp`      |
| xhdpi   | 96        | `mipmap-xhdpi/ic_launcher.webp`     |
| xxhdpi  | 144       | `mipmap-xxhdpi/ic_launcher.webp`    |
| xxxhdpi | 192       | `mipmap-xxxhdpi/ic_launcher.webp`   |

The same set is mirrored at `ic_launcher_round.webp` per density.

### Adaptive icon (Android 8+)

`mipmap-anydpi-v26/ic_launcher.xml` (and `ic_launcher_round.xml`) wire three
layers:

- **background** — `drawable/ic_launcher_background.xml`, a 108dp solid
  `#0d0d0d` (the app's `Surface` token). Putting the dark background in
  the launcher rather than the foreground means the icon's silhouette sits
  on the same surface as the rest of the app, with no visible "card" edge
  when the system mask clips the corners.
- **foreground** — `drawable/ic_launcher_foreground.webp` (the full-color
  stopwatch). The image's own dark ground blends with the background.
- **monochrome** — `drawable/ic_launcher_monochrome.xml`, a stopwatch
  silhouette (outer ring + four tick marks + crown/side buttons) on a
  transparent background. Required for Android 13+ themed icons, which
  render the layer as a single mask color over the user's wallpaper.
  Reusing the colored foreground here would turn the entire image solid
  white, so this is a separate vector.

The foreground's content is well within the 66dp safe area, so the launcher
mask never clips the stopwatch face or the F1 track.

## NavigationBar icons — `drawable/ic_{home,schedule,leaderboard,myteam}_outline.xml`

Four 24dp outlined vector drawables, one per top-level destination. Each
path uses `@android:color/white` so the M3 `NavigationBarItem` can tint
the icon with `LocalContentColor` — `onSurfaceVariant` when unselected,
`primary` (F1 orange) when selected.

| Tab        | Drawable                            | Shape                                         |
|------------|-------------------------------------|-----------------------------------------------|
| Home       | `ic_home_outline.xml`               | House with door (Material `home` shape)       |
| Schedule   | `ic_schedule_outline.xml`           | Calendar with binder rings + date dot         |
| Leaderboard| `ic_leaderboard_outline.xml`        | 1-2-3 podium: 2nd left, 1st center, 3rd right |
| My Team    | `ic_myteam_outline.xml`             | Racing helmet (front, with visor slit)        |

### Selected-state brand color

`NavShell.kt` overrides `NavigationBarItemDefaults.colors` so the selected
tab picks up the F1 brand:

- `selectedIconColor = MaterialTheme.colorScheme.primary` (F1Primary `#ff3301`)
- `selectedTextColor = MaterialTheme.colorScheme.primary`
- `indicatorColor = primary.copy(alpha = 0.16f)` — a subtle orange pill
  behind the icon, sitting on the dark `surfaceContainer` nav bar.

Unselected items keep the M3 default `onSurfaceVariant` so the brand color
only lights up the active tab.

## Build-time rules

- Adaptive-icon **monochrome** must be a single-color silhouette on a
  transparent background. Reusing the colored foreground is wrong — the
  themed-icon renderer treats the layer as a mask.
- Nav-bar icons must not reference `?attr/colorControlNormal` — F1app is
  pure Compose (no AppCompat), and the attribute is unresolved at link
  time. The Compose `Icon` composable applies `tint` via
  `LocalContentColor`; the drawable itself should stay neutral
  (`@android:color/white`).
- Vector drawables go in `drawable/`, density-specific bitmaps in
  `mipmap-{density}/` (launcher) or `drawable-{density}/` (in-app
  raster). The legacy `mipmap-{density}/ic_launcher.webp` is required for
  Android 7 (`minSdk 24`) launchers that do not read `mipmap-anydpi-v26`.

## Regenerating the launcher icon

`tools/app-icon-build/source-app-icon.png` is the source-of-truth stopwatch
PNG. To regenerate every density:

```bash
cd tools/app-icon-build
for size in 48 72 96 144 192; do
  sips -Z $size source-app-icon.png --out app-icon-${size}.png
  cwebp -q 95 app-icon-${size}.png -o app-icon-${size}.webp
done
sips -Z 432 source-app-icon.png --out app-icon-foreground-432.png
cwebp -q 95 app-icon-foreground-432.png -o app-icon-foreground-432.webp
# then copy each .webp into the matching res/ folder (see table above).
```
