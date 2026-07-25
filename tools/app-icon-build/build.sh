#!/usr/bin/env bash
#
# Regenerate the F1app launcher icon assets from source-app-icon.png.
#
# Reads:   source-app-icon.png
# Writes:  app-icon-{48,72,96,144,192}.png  (legacy mipmap densities)
#          app-icon-foreground-432.png       (adaptive-icon foreground)
#          *.webp                            (cwebp -q 95)
#
# Then copy the .webp files into:
#   app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp
#   app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.webp
#   app/src/main/res/drawable/ic_launcher_foreground.webp
#
# See lode/design-system/icons.md for the full pipeline + density table.
set -euo pipefail

cd "$(dirname "$0")"

declare -A SIZES=(
  [mdpi]=48
  [hdpi]=72
  [xhdpi]=96
  [xxhdpi]=144
  [xxxhdpi]=192
)

for density in "${!SIZES[@]}"; do
  size=${SIZES[$density]}
  sips -Z "$size" source-app-icon.png --out "app-icon-${size}.png" > /dev/null
  cwebp -q 95 "app-icon-${size}.png" -o "app-icon-${size}.webp" > /dev/null
  echo "Built $density: app-icon-${size}.{png,webp}"
done

# Adaptive-icon foreground at 108dp @ xxxhdpi = 432px (highest quality).
sips -Z 432 source-app-icon.png --out app-icon-foreground-432.png > /dev/null
cwebp -q 95 app-icon-foreground-432.png -o app-icon-foreground-432.webp > /dev/null
echo "Built adaptive-icon foreground: app-icon-foreground-432.webp"
